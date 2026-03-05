package com.philips.borealis.kmm.profileaward.repository

import com.philips.borealis.kmm.profileaward.db.BorealisProfileAwardDb
import com.philips.borealis.kmm.profileaward.model.Award
import com.philips.borealis.kmm.profileaward.model.ProfileAward
import com.philips.borealis.kmm.profileaward.model.SyncState
import com.philips.borealis.kmm.profileaward.platform.AwardJsonProvider
import com.philips.borealis.kmm.profileaward.util.createTestSqlDriver
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfileAwardFilterTest {

    private lateinit var database: BorealisProfileAwardDb
    private lateinit var awardRepository: AwardRepository
    private lateinit var profileAwardRepository: ProfileAwardRepository

    @BeforeTest
    fun setup() {
        val driver = createTestSqlDriver()
        BorealisProfileAwardDb.Schema.create(driver)
        database = BorealisProfileAwardDb(driver)
        awardRepository = AwardRepository(database, NullJsonProvider())
        profileAwardRepository = ProfileAwardRepository(database, awardRepository)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun seedAward(uuid: String, type: String, criteria: String = ""): Award {
        database.awardQueries.insertOrReplace(
            uuid = uuid,
            awardDescription = null,
            badge = null,
            consumable = 0L,
            criteria = criteria,
            inventoryAwarded = 0L,
            name = "Award-$uuid",
            trackingId = null,
            type = type,
            awardValue = 0L
        )
        return Award(uuid = uuid, type = type, criteria = criteria)
    }

    private fun seedProfileAward(
        uuid: String,
        awardUuid: String,
        profileUuid: String,
        notified: Boolean = false,
        syncState: SyncState = SyncState.SYNCED
    ): ProfileAward {
        database.profileAwardQueries.insertOrReplace(
            uuid = uuid,
            award = awardUuid,
            dateTimeAwarded = "2024-01-01T10:00:00Z",
            dateTimeAwardedLocal = "2024-01-01T10:00:00Z",
            notified = if (notified) 1L else 0L,
            profileUuid = profileUuid,
            awardLevelValue = 0L,
            momentId = "",
            momentVersion = 0L,
            momentCreatedDate = "",
            momentLastModifiedDate = "",
            syncState = syncState.value.toLong()
        )
        return ProfileAward(
            uuid = uuid,
            award = awardUuid,
            dateTimeAwarded = "2024-01-01T10:00:00Z",
            dateTimeAwardedLocal = "2024-01-01T10:00:00Z",
            notified = notified,
            profileUuid = profileUuid,
            syncState = syncState
        )
    }

    private fun incomingProfileAward(
        uuid: String,
        awardUuid: String,
        profileUuid: String,
        notified: Boolean = false
    ): ProfileAward = ProfileAward(
        uuid = uuid,
        award = awardUuid,
        dateTimeAwarded = "2024-06-01T10:00:00Z",
        dateTimeAwardedLocal = "2024-06-01T10:00:00Z",
        notified = notified,
        profileUuid = profileUuid,
        syncState = SyncState.CREATED
    )

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    fun testFilterOneTimeAwards_removesAlreadyGivenOnetimeAward() {
        // "level" is in ONE_TIME_AWARD_TYPES — award already given → should be filtered out
        seedAward("aw-level", "level", "milestone")
        seedProfileAward("pa-existing", "aw-level", "profile-A", notified = true)

        val incoming = listOf(
            incomingProfileAward("pa-new", "aw-level", "profile-A", notified = false)
        )

        val filtered = profileAwardRepository.filterOneTimeAwards("profile-A", incoming)

        assertTrue(
            filtered.isEmpty(),
            "One-time award already given to profile should be filtered out, got: $filtered"
        )
    }

    @Test
    fun testFilterOneTimeAwards_allowsRepeatableAward() {
        // "consumable" is not a one-time award type — should pass through freely
        seedAward("aw-consumable", "consumable")

        val incoming = listOf(
            incomingProfileAward("pa-1", "aw-consumable", "profile-A"),
            incomingProfileAward("pa-2", "aw-consumable", "profile-A")
        )

        val filtered = profileAwardRepository.filterOneTimeAwards("profile-A", incoming)

        assertEquals(2, filtered.size)
    }

    @Test
    fun testFilterOneTimeAwards_allowsNotifiedUpdateForExistingAward() {
        // Award already given (one-time type) but incoming is marking it notified=true
        // → should be allowed through as a notified update
        seedAward("aw-achievement", "achievement")
        seedProfileAward("pa-existing", "aw-achievement", "profile-A", notified = false)

        val incoming = listOf(
            incomingProfileAward("pa-new", "aw-achievement", "profile-A", notified = true)
        )

        val filtered = profileAwardRepository.filterOneTimeAwards("profile-A", incoming)

        assertEquals(
            1, filtered.size,
            "Notified=true update for existing one-time award should be allowed through"
        )
        assertTrue(filtered.first().notified)
    }

    @Test
    fun testFilterOneTimeAwards_allowsNewOnetimeAwardNotYetGiven() {
        // "wearable" is one-time type, but profile doesn't have it yet → allow
        seedAward("aw-wearable", "wearable")
        // Profile has no awards at all

        val incoming = listOf(
            incomingProfileAward("pa-new", "aw-wearable", "profile-A", notified = false)
        )

        val filtered = profileAwardRepository.filterOneTimeAwards("profile-A", incoming)

        assertEquals(1, filtered.size)
        assertEquals("pa-new", filtered.first().uuid)
    }

    @Test
    fun testFilterOneTimeAwards_mixedTypesFilteredCorrectly() {
        // profile-A already has a "level" award; incoming has one level (blocked) + one wearable (new, allowed)
        seedAward("aw-level", "level", "milestone")
        seedAward("aw-wearable", "wearable")
        seedProfileAward("pa-level-existing", "aw-level", "profile-A", notified = true)

        val incoming = listOf(
            incomingProfileAward("pa-level-dup", "aw-level", "profile-A"),     // duplicate one-time → blocked
            incomingProfileAward("pa-wearable-new", "aw-wearable", "profile-A") // new one-time → allowed
        )

        val filtered = profileAwardRepository.filterOneTimeAwards("profile-A", incoming)

        assertEquals(1, filtered.size)
        assertEquals("pa-wearable-new", filtered.first().uuid)
    }
}

private class NullJsonProvider : AwardJsonProvider {
    override fun loadAwardsJson(): String? = null
}
