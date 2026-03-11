package com.philips.borealis.kmm.profileaward.repository

import com.philips.borealis.kmm.profileaward.db.BorealisProfileAwardDb
import com.philips.borealis.kmm.profileaward.model.Award
import com.philips.borealis.kmm.profileaward.model.AwardInventoryItem
import com.philips.borealis.kmm.profileaward.model.ProfileAward
import com.philips.borealis.kmm.profileaward.model.SyncState
import com.philips.borealis.kmm.profileaward.platform.AwardOverflowNotifier
import com.philips.borealis.kmm.profileaward.platform.InventoryMergeHandler
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val ONE_TIME_AWARD_TYPES = setOf(
    "achievement",
    "wearable",
    "level",
    "habitat",
    "smiggle-achievement",
    "trick",
    "adventure-achievement",
    "durable-earned"
)

private val CONSUMABLE_AWARD_TYPES = setOf("dye", "food", "wearable", "habitat")

class ProfileAwardRepository(
    private val database: BorealisProfileAwardDb,
    private val awardRepository: AwardRepository,
    private val overflowNotifier: AwardOverflowNotifier? = null,
    private val inventoryMergeHandler: InventoryMergeHandler? = null
) {

    private val json = Json { ignoreUnknownKeys = true }

    // -------------------------------------------------------------------------
    // Query methods
    // -------------------------------------------------------------------------

    fun getProfileAwards(profileUuid: String): List<ProfileAward> =
        database.profileAwardQueries
            .selectByProfileUuid(profileUuid)
            .executeAsList()
            .map { rowToProfileAward(it) }

    fun getProfileAwardsAsJson(profileUuid: String): String =
        json.encodeToString(getProfileAwards(profileUuid))

    fun getProfileAwardsForType(profileUuid: String, awardType: String): List<Award> =
        database.profileAwardQueries
            .selectForTypeByProfile(profileUuid, awardType)
            .executeAsList()
            .map { row ->
                Award(
                    uuid = row.award ?: "",
                    awardDescription = row.awardDescription,
                    badge = row.badge,
                    consumable = row.consumable?.let { it != 0L },
                    criteria = row.criteria,
                    inventoryAwarded = row.inventoryAwarded?.toInt(),
                    name = row.awardName,
                    trackingId = row.trackingId,
                    type = row.type,
                    value = row.awardValue?.toInt()
                )
            }

    fun getUnsyncedProfileAwards(): List<ProfileAward> =
        database.profileAwardQueries
            .selectUnsynced()
            .executeAsList()
            .map { rowToProfileAward(it) }

    fun levelRankOfUserProfile(profileUuid: String): Int {
        val levelAwards = database.profileAwardQueries
            .selectLevelAwardsByProfile(profileUuid)
            .executeAsList()
        return levelAwards.lastOrNull()?.awardValue?.toInt() ?: 0
    }

    // -------------------------------------------------------------------------
    // Store / upsert methods
    // -------------------------------------------------------------------------

    fun storeProfileAwardsFromJson(profileUuid: String, jsonString: String): Int {
        val awards = json.decodeFromString<List<ProfileAward>>(jsonString)
        return storeProfileAwardsList(profileUuid, awards)
    }

    fun storeProfileAwardsList(profileUuid: String, awards: List<ProfileAward>): Int {
        var count = 0
        database.profileAwardQueries.transaction {
            awards.forEach { incoming ->
                // Core Data deduplicates by award (award-catalog UUID) + profileUuid,
                // NOT by uuid (profile-award instance UUID). Mirror that behaviour to
                // prevent duplicates when the same award arrives with a different uuid.
                val existingByAward = incoming.award?.let { awardUuid ->
                    database.profileAwardQueries
                        .selectByAwardAndProfileUuid(awardUuid, profileUuid)
                        .executeAsOneOrNull()
                        ?.let { rowToProfileAward(it) }
                }

                if (existingByAward != null) {
                    // If the incoming uuid differs from the existing one, delete the old
                    // row first so the INSERT OR REPLACE below uses the new uuid as PK.
                    if (existingByAward.uuid != incoming.uuid) {
                        database.profileAwardQueries.deleteByUuidAndProfileUuid(
                            existingByAward.uuid, profileUuid
                        )
                    }

                    if (!isSameAward(existingByAward, incoming)) {
                        // Update, preserving any moment fields already set
                        val preservedMomentId = existingByAward.momentId?.takeIf { it.isNotEmpty() }
                            ?: incoming.momentId ?: ""
                        val preservedMomentVersion = if (!existingByAward.momentId.isNullOrEmpty())
                            existingByAward.momentVersion else incoming.momentVersion
                        val preservedMomentCreatedDate = existingByAward.momentCreatedDate
                            ?.takeIf { it.isNotEmpty() }
                            ?: incoming.momentCreatedDate ?: ""
                        val preservedMomentLastModifiedDate =
                            incoming.momentLastModifiedDate ?: existingByAward.momentLastModifiedDate

                        database.profileAwardQueries.insertOrReplace(
                            uuid = incoming.uuid,
                            award = incoming.award,
                            dateTimeAwarded = incoming.dateTimeAwarded,
                            dateTimeAwardedLocal = incoming.dateTimeAwardedLocal,
                            notified = if (incoming.notified) 1L else 0L,
                            profileUuid = profileUuid,
                            awardLevelValue = incoming.awardLevelValue.toLong(),
                            momentId = preservedMomentId,
                            momentVersion = preservedMomentVersion.toLong(),
                            momentCreatedDate = preservedMomentCreatedDate,
                            momentLastModifiedDate = preservedMomentLastModifiedDate ?: "",
                            syncState = incoming.syncState.value.toLong()
                        )
                        count++
                    }
                } else {
                    database.profileAwardQueries.insertOrReplace(
                        uuid = incoming.uuid,
                        award = incoming.award,
                        dateTimeAwarded = incoming.dateTimeAwarded,
                        dateTimeAwardedLocal = incoming.dateTimeAwardedLocal,
                        notified = if (incoming.notified) 1L else 0L,
                        profileUuid = profileUuid,
                        awardLevelValue = incoming.awardLevelValue.toLong(),
                        momentId = incoming.momentId ?: "",
                        momentVersion = incoming.momentVersion.toLong(),
                        momentCreatedDate = incoming.momentCreatedDate ?: "",
                        momentLastModifiedDate = incoming.momentLastModifiedDate ?: "",
                        syncState = incoming.syncState.value.toLong()
                    )
                    count++
                }
            }
        }
        // Update the cached level rank value for all stored awards
        updateLevelRankForProfile(profileUuid)
        return count
    }

    fun storeProfileAwardMoment(award: ProfileAward): Int {
        val existing = database.profileAwardQueries
            .selectByUuidAndProfileUuid(award.uuid, award.profileUuid)
            .executeAsOneOrNull()
            ?.let { rowToProfileAward(it) }

        return if (existing != null) {
            // Preserve the original moment created date if already set
            val preservedCreatedDate = existing.momentCreatedDate
                ?.takeIf { it.isNotEmpty() }
                ?: award.momentCreatedDate ?: ""

            database.profileAwardQueries.updateMomentFields(
                momentId = award.momentId ?: existing.momentId ?: "",
                momentVersion = award.momentVersion.toLong(),
                momentCreatedDate = preservedCreatedDate,
                momentLastModifiedDate = award.momentLastModifiedDate ?: "",
                uuid = award.uuid,
                profileUuid = award.profileUuid
            )
            1
        } else {
            database.profileAwardQueries.insertOrReplace(
                uuid = award.uuid,
                award = award.award,
                dateTimeAwarded = award.dateTimeAwarded,
                dateTimeAwardedLocal = award.dateTimeAwardedLocal,
                notified = if (award.notified) 1L else 0L,
                profileUuid = award.profileUuid,
                awardLevelValue = award.awardLevelValue.toLong(),
                momentId = award.momentId ?: "",
                momentVersion = award.momentVersion.toLong(),
                momentCreatedDate = award.momentCreatedDate ?: "",
                momentLastModifiedDate = award.momentLastModifiedDate ?: "",
                syncState = award.syncState.value.toLong()
            )
            1
        }
    }

    fun updateProfileAward(profileUuid: String, award: ProfileAward): Int {
        database.profileAwardQueries.insertOrReplace(
            uuid = award.uuid,
            award = award.award,
            dateTimeAwarded = award.dateTimeAwarded,
            dateTimeAwardedLocal = award.dateTimeAwardedLocal,
            notified = if (award.notified) 1L else 0L,
            profileUuid = profileUuid,
            awardLevelValue = award.awardLevelValue.toLong(),
            momentId = award.momentId ?: "",
            momentVersion = award.momentVersion.toLong(),
            momentCreatedDate = award.momentCreatedDate ?: "",
            momentLastModifiedDate = award.momentLastModifiedDate ?: "",
            syncState = award.syncState.value.toLong()
        )
        return 1
    }

    // -------------------------------------------------------------------------
    // iOS deDuplicateTimestamps + Android getFilteredList combined
    // -------------------------------------------------------------------------

    fun deDuplicateAndStore(profileUuid: String, awardsJson: String): Int {
        val incoming = json.decodeFromString<List<ProfileAward>>(awardsJson)

        // --- iOS overflow check ---
        val unnotified = database.profileAwardQueries
            .selectUnnotifiedByProfile(profileUuid)
            .executeAsList()
        val markAllNotified = unnotified.size > 5
        if (markAllNotified) {
            overflowNotifier?.onTooManyNewAwards()
        }

        // --- Dedup timestamps (iOS deDuplicateTimestamps logic) ---
        val seenTimestamps = mutableMapOf<String, Int>()
        val chestBoxItems = mutableMapOf<String, AwardInventoryItem>()

        val deduplicatedAwards = incoming.map { award ->
            val ts = award.dateTimeAwarded ?: ""
            val dupCount = seenTimestamps.getOrElse(ts) { 0 }
            val adjustedAward = if (dupCount > 0 && ts.isNotEmpty()) {
                // Subtract incrementing seconds from dateTimeAwarded for duplicates
                award.copy(dateTimeAwarded = subtractSeconds(ts, dupCount))
            } else {
                award
            }
            seenTimestamps[ts] = dupCount + 1

            // Collect consumable types for inventory merge.
            // Use the full UUID→type lookup (awards_uuids.json) since the Award
            // DB table only contains display awards, not inventory items.
            val awardUuid = award.award ?: ""
            val awardType = awardRepository.awardTypeLookup[awardUuid]
                ?: awardRepository.getAwardById(awardUuid)?.type
            if (awardType != null && awardType in CONSUMABLE_AWARD_TYPES) {
                val existing = chestBoxItems[awardUuid]
                if (existing != null) {
                    chestBoxItems[awardUuid] = existing.copy(
                        inventoryAwarded = existing.inventoryAwarded + 1
                    )
                } else {
                    chestBoxItems[awardUuid] = AwardInventoryItem(
                        awardUuid = awardUuid,
                        type = awardType,
                        inventoryAwarded = 1
                    )
                }
            }

            // Mark all as notified when overflow
            if (markAllNotified) adjustedAward.copy(notified = true) else adjustedAward
        }

        // --- Android one-time award filter ---
        val filtered = filterOneTimeAwards(profileUuid, deduplicatedAwards)

        // --- Persist ---
        val count = storeProfileAwardsList(profileUuid, filtered)

        // --- Notify inventory merge handler ---
        if (chestBoxItems.isNotEmpty()) {
            inventoryMergeHandler?.mergeInventoryItems(chestBoxItems)
        }

        return count
    }

    fun filterOneTimeAwards(profileUuid: String, incoming: List<ProfileAward>): List<ProfileAward> {
        // Build set of award UUIDs that are one-time typed and already awarded to this profile
        val existingAwardUuids = getProfileAwards(profileUuid)
            .mapNotNull { it.award }
            .toSet()

        // Use the full UUID→type lookup (awards_uuids.json) to identify one-time awards.
        // The DB catalog (awards.json) only has display awards; inventory-type awards
        // like wearable/habitat/durable-earned are in the lookup but not in the DB.
        val fullLookup = awardRepository.awardTypeLookup
        val oneTimeAwardUuids = buildSet {
            // From the DB catalog
            awardRepository.getAllAwards()
                .filter { it.type in ONE_TIME_AWARD_TYPES }
                .forEach { add(it.uuid) }
            // From the full UUID→type lookup
            fullLookup.entries
                .filter { it.value in ONE_TIME_AWARD_TYPES }
                .forEach { add(it.key) }
        }

        val alreadyOwnedOneTimeUuids = existingAwardUuids.intersect(oneTimeAwardUuids)

        return incoming.filter { award ->
            val awardUuid = award.award ?: return@filter true
            if (awardUuid in alreadyOwnedOneTimeUuids) {
                // Only allow through if it is just updating notified=true on an existing award
                val existing = getProfileAwards(profileUuid).find { it.award == awardUuid }
                existing != null && !existing.notified && award.notified
            } else {
                true
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun updateLevelRankForProfile(profileUuid: String) {
        val rank = levelRankOfUserProfile(profileUuid)
        // Update awardLevelValue on all ProfileAwards for this profile
        val profileAwards = getProfileAwards(profileUuid)
        database.profileAwardQueries.transaction {
            profileAwards.forEach { award ->
                database.profileAwardQueries.insertOrReplace(
                    uuid = award.uuid,
                    award = award.award,
                    dateTimeAwarded = award.dateTimeAwarded,
                    dateTimeAwardedLocal = award.dateTimeAwardedLocal,
                    notified = if (award.notified) 1L else 0L,
                    profileUuid = profileUuid,
                    awardLevelValue = rank.toLong(),
                    momentId = award.momentId ?: "",
                    momentVersion = award.momentVersion.toLong(),
                    momentCreatedDate = award.momentCreatedDate ?: "",
                    momentLastModifiedDate = award.momentLastModifiedDate ?: "",
                    syncState = award.syncState.value.toLong()
                )
            }
        }
    }

    private fun isSameAward(a: ProfileAward, b: ProfileAward): Boolean =
        a.uuid == b.uuid &&
            a.award == b.award &&
            a.dateTimeAwarded == b.dateTimeAwarded &&
            a.dateTimeAwardedLocal == b.dateTimeAwardedLocal &&
            a.notified == b.notified

    private fun rowToProfileAward(row: com.philips.borealis.kmm.profileaward.db.ProfileAward): ProfileAward =
        ProfileAward(
            uuid = row.uuid,
            award = row.award,
            dateTimeAwarded = row.dateTimeAwarded,
            dateTimeAwardedLocal = row.dateTimeAwardedLocal,
            notified = row.notified != 0L,
            profileUuid = row.profileUuid,
            awardLevelValue = row.awardLevelValue.toInt(),
            momentId = row.momentId.takeIf { it.isNotEmpty() },
            momentVersion = row.momentVersion.toInt(),
            momentCreatedDate = row.momentCreatedDate.takeIf { it.isNotEmpty() },
            momentLastModifiedDate = row.momentLastModifiedDate.takeIf { it.isNotEmpty() },
            syncState = SyncState.fromInt(row.syncState.toInt())
        )

    /**
     * Subtracts [seconds] from an ISO-8601 datetime string (e.g. "2024-01-15T10:30:00Z").
     * Falls back to the original string if parsing fails.
     */
    private fun subtractSeconds(iso8601: String, seconds: Int): String {
        return try {
            // Handles the common compact ISO-8601 pattern: yyyy-MM-ddTHH:mm:ssZ
            val regex = Regex("""^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:)(\d{2})(Z|[+-].*)$""")
            val match = regex.matchEntire(iso8601) ?: return iso8601
            val prefix = match.groupValues[1]
            val sec = match.groupValues[2].toInt()
            val suffix = match.groupValues[3]
            val adjusted = (sec - seconds).coerceAtLeast(0)
            "$prefix${adjusted.toString().padStart(2, '0')}$suffix"
        } catch (_: Exception) {
            iso8601
        }
    }
}
