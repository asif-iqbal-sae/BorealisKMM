package com.philips.borealis.kmm.profileaward.repository

import com.philips.borealis.kmm.profileaward.db.BorealisProfileAwardDb
import com.philips.borealis.kmm.profileaward.model.Award
import com.philips.borealis.kmm.profileaward.platform.AwardJsonProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

class AwardRepository(
    private val database: BorealisProfileAwardDb,
    private val jsonProvider: AwardJsonProvider
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Lazy-loaded UUID→type lookup from awards_uuids.json (the full 363-entry file).
     * Used only for classifying award types during de-duplication, NOT stored in DB.
     */
    val awardTypeLookup: Map<String, String> by lazy {
        val lookupJson = jsonProvider.loadAwardUuidsLookupJson() ?: return@lazy emptyMap()
        try {
            val root = json.parseToJsonElement(lookupJson)
            if (root is JsonObject) {
                root.entries.mapNotNull { (uuid, element) ->
                    val type = element.jsonObject["type"]?.jsonPrimitive?.content
                    if (type != null) uuid to type else null
                }.toMap()
            } else {
                emptyMap()
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun loadAwardsFromJson(): Int {
        val jsonString = jsonProvider.loadAwardsJson() ?: return 0
        val awards = parseAwardsJson(jsonString)
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

    /**
     * The awards_uuids.json file uses a map format:
     *   { "uuid1": { "type": "...", "name": "..." }, "uuid2": { ... } }
     * Convert each entry into an Award with the map key as the uuid.
     */
    private fun parseAwardsJson(jsonString: String): List<Award> {
        val root = json.parseToJsonElement(jsonString)
        return when (root) {
            is JsonObject -> root.entries.map { (uuid, element) ->
                val obj = element.jsonObject
                Award(
                    uuid = uuid,
                    awardDescription = obj["awardDescription"]?.jsonPrimitive?.content,
                    badge = obj["badge"]?.jsonPrimitive?.content,
                    consumable = obj["consumable"]?.jsonPrimitive?.booleanOrNull,
                    criteria = obj["criteria"]?.jsonPrimitive?.content,
                    inventoryAwarded = obj["inventoryAwarded"]?.jsonPrimitive?.intOrNull,
                    name = obj["name"]?.jsonPrimitive?.content,
                    trackingId = obj["trackingId"]?.jsonPrimitive?.content,
                    type = obj["type"]?.jsonPrimitive?.content,
                    value = obj["value"]?.jsonPrimitive?.intOrNull
                )
            }
            else -> json.decodeFromString<List<Award>>(jsonString)
        }
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
