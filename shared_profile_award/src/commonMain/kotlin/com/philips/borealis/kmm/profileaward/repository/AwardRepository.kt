package com.philips.borealis.kmm.profileaward.repository

import com.philips.borealis.kmm.profileaward.db.BorealisProfileAwardDb
import com.philips.borealis.kmm.profileaward.model.Award
import com.philips.borealis.kmm.profileaward.platform.AwardJsonProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

class AwardRepository(
    private val database: BorealisProfileAwardDb,
    private val jsonProvider: AwardJsonProvider
) {

    private val json = Json { ignoreUnknownKeys = true }

    fun loadAwardsFromJson(): Int {
        val jsonString = jsonProvider.loadAwardsJson() ?: return 0
        val awards = json.decodeFromString<List<Award>>(jsonString)
        awards.forEach { award ->
            database.awardQueries.insertOrReplace(
                uuid = award.uuid,
                awardDescription = award.awardDescription,
                badge = award.badge,
                consumable = award.consumable?.let { if (it) 1L else 0L },
                criteria = award.criteria,
                inventoryAwarded = award.inventoryAwarded?.toLong(),
                name = award.name,
                trackingId = award.trackingId,
                type = award.type,
                awardValue = award.value?.toLong()
            )
        }
        return awards.size
    }

    fun getAllAwards(): List<Award> =
        database.awardQueries.selectAll().executeAsList().map { rowToAward(it) }

    fun getAwardById(uuid: String): Award? =
        database.awardQueries.selectByUuid(uuid).executeAsOneOrNull()?.let { rowToAward(it) }

    fun getAwardsByType(type: String): List<Award> =
        database.awardQueries.selectByType(type).executeAsList().map { rowToAward(it) }

    private fun rowToAward(row: com.philips.borealis.kmm.profileaward.db.Award): Award =
        Award(
            uuid = row.uuid,
            awardDescription = row.awardDescription,
            badge = row.badge,
            consumable = row.consumable?.let { it != 0L },
            criteria = row.criteria,
            inventoryAwarded = row.inventoryAwarded?.toInt(),
            name = row.name,
            trackingId = row.trackingId,
            type = row.type,
            value = row.awardValue?.toInt()
        )
}
