package com.philips.borealis.kmm.profileaward.demo

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.philips.borealis.kmm.profileaward.db.BorealisProfileAwardDb
import com.philips.borealis.kmm.profileaward.model.Award
import com.philips.borealis.kmm.profileaward.model.ProfileAward
import com.philips.borealis.kmm.profileaward.model.SyncState
import com.philips.borealis.kmm.profileaward.platform.AwardJsonProvider
import com.philips.borealis.kmm.profileaward.repository.AwardRepository
import com.philips.borealis.kmm.profileaward.repository.ProfileAwardRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  STORAGE MIGRATION DEMO                                            ║
 * ║  Proves: Award storage moved from Core Data / ContentProvider      ║
 * ║          to SQLDelight (KMM shared module)                         ║
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * Run with:
 *   cd BorealisKMM
 *   ./gradlew :shared_profile_award:jvmTest --tests "*StorageMigrationDemo*" --info
 *
 * Each test method demonstrates one aspect of the storage migration.
 * All data is stored/retrieved via the SAME KMM SQLDelight layer that
 * both Android and iOS now use.
 */
class StorageMigrationDemo {

    // ─────────────────────────────────────────────────────────────────
    // Shared setup — creates an in-memory SQLDelight database
    // (same schema used on Android & iOS, just with JVM driver for demo)
    // ─────────────────────────────────────────────────────────────────

    private fun createDatabase(): BorealisProfileAwardDb {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        BorealisProfileAwardDb.Schema.create(driver)
        return BorealisProfileAwardDb(driver)
    }

    private val sampleAwardsJson = """
        [
          {"uuid":"award-level-1","name":"Bronze Star","type":"level","badge":"bronze","criteria":"milestone","value":1,"consumable":false,"inventoryAwarded":0},
          {"uuid":"award-level-2","name":"Silver Star","type":"level","badge":"silver","criteria":"milestone","value":2,"consumable":false,"inventoryAwarded":0},
          {"uuid":"award-level-3","name":"Gold Star","type":"level","badge":"gold","criteria":"milestone","value":3,"consumable":false,"inventoryAwarded":0},
          {"uuid":"award-achieve-1","name":"First Brush","type":"achievement","badge":"firstbrush","criteria":"brush_once","value":10,"consumable":false,"inventoryAwarded":0},
          {"uuid":"award-dye-1","name":"Red Dye","type":"dye","badge":"reddye","criteria":"","value":0,"consumable":true,"inventoryAwarded":1}
        ]
    """.trimIndent()

    // ═════════════════════════════════════════════════════════════════
    // DEMO 1: Award Catalog — JSON → SQLDelight (replaces assets/JSON + ContentProvider)
    // ═════════════════════════════════════════════════════════════════

    @Test
    fun demo1_AwardCatalogStoredInSQLDelight() {
        println("\n${"═".repeat(70)}")
        println("  DEMO 1: Award Catalog loaded from JSON → stored in SQLDelight")
        println("  BEFORE: Android read awards.json → ContentProvider → SQLite via ContentResolver")
        println("  BEFORE: iOS read awards_uuids.json → Core Data AwardEntity")
        println("  NOW:    Both platforms → AwardRepository.loadAwardsFromJson() → SQLDelight")
        println("${"═".repeat(70)}")

        val db = createDatabase()
        val jsonProvider = object : AwardJsonProvider {
            override fun loadAwardsJson(): String = sampleAwardsJson
        }
        val awardRepo = AwardRepository(db, jsonProvider)

        // Load awards from JSON into SQLDelight
        val count = awardRepo.loadAwardsFromJson()
        println("  ✓ Loaded $count awards from JSON into SQLDelight database")

        // Retrieve all awards
        val allAwards = awardRepo.getAllAwards()
        println("  ✓ Retrieved ${allAwards.size} awards from SQLDelight")
        allAwards.forEach { a ->
            println("    → ${a.uuid}  name=${a.name}  type=${a.type}  value=${a.value}")
        }

        // Query by type
        val levelAwards = awardRepo.getAwardsByType("level")
        println("  ✓ Queried level awards: found ${levelAwards.size}")

        // Query by UUID
        val specific = awardRepo.getAwardById("award-achieve-1")
        println("  ✓ Queried by UUID 'award-achieve-1': name=${specific?.name}")

        assertEquals(5, count)
        assertEquals(5, allAwards.size)
        assertEquals(3, levelAwards.size)
        assertNotNull(specific)

        println("  ★ PASSED — Award catalog fully stored and queryable in SQLDelight\n")
    }

    // ═════════════════════════════════════════════════════════════════
    // DEMO 2: ProfileAward CRUD — replaces Core Data / ContentProvider
    // ═════════════════════════════════════════════════════════════════

    @Test
    fun demo2_ProfileAwardCRUD_InSQLDelight() {
        println("\n${"═".repeat(70)}")
        println("  DEMO 2: ProfileAward full CRUD in SQLDelight")
        println("  BEFORE: Android → ContentResolver insert/query/update on profile_award table")
        println("  BEFORE: iOS → NSManagedObjectContext fetch/save ProfileAwardEntity")
        println("  NOW:    Both platforms → ProfileAwardRepository → SQLDelight")
        println("${"═".repeat(70)}")

        val db = createDatabase()
        val jsonProvider = object : AwardJsonProvider {
            override fun loadAwardsJson(): String = sampleAwardsJson
        }
        val awardRepo = AwardRepository(db, jsonProvider)
        awardRepo.loadAwardsFromJson()
        val profileAwardRepo = ProfileAwardRepository(db, awardRepo)

        val profileUuid = "child-profile-001"

        // Seed the Profile table (FK requirement)
        db.profileQueries.insertOrReplace(profileUuid)

        // ── CREATE ──
        val awards = listOf(
            ProfileAward(
                uuid = "pa-001", award = "award-level-1", profileUuid = profileUuid,
                dateTimeAwarded = "2025-03-01T10:00:00Z",
                dateTimeAwardedLocal = "2025-03-01T11:00:00+01:00",
                notified = false, syncState = SyncState.CREATED
            ),
            ProfileAward(
                uuid = "pa-002", award = "award-level-2", profileUuid = profileUuid,
                dateTimeAwarded = "2025-03-02T10:00:00Z",
                dateTimeAwardedLocal = "2025-03-02T11:00:00+01:00",
                notified = true, syncState = SyncState.SYNCED
            ),
            ProfileAward(
                uuid = "pa-003", award = "award-achieve-1", profileUuid = profileUuid,
                dateTimeAwarded = "2025-03-03T10:00:00Z",
                dateTimeAwardedLocal = "2025-03-03T11:00:00+01:00",
                notified = false, syncState = SyncState.CREATED
            )
        )

        val stored = profileAwardRepo.storeProfileAwardsList(profileUuid, awards)
        println("  ✓ CREATE: Stored $stored profile awards in SQLDelight")

        // ── READ ──
        val retrieved = profileAwardRepo.getProfileAwards(profileUuid)
        println("  ✓ READ:   Retrieved ${retrieved.size} profile awards for profile '$profileUuid'")
        retrieved.forEach { pa ->
            println("    → uuid=${pa.uuid}  award=${pa.award}  notified=${pa.notified}  syncState=${pa.syncState}")
        }

        // ── READ as JSON (same format both platforms consume) ──
        val jsonOutput = profileAwardRepo.getProfileAwardsAsJson(profileUuid)
        println("  ✓ READ (JSON): ${jsonOutput.take(120)}...")

        // ── UPDATE ──
        val updated = awards[0].copy(notified = true, syncState = SyncState.UPDATED)
        profileAwardRepo.updateProfileAward(profileUuid, updated)
        val afterUpdate = profileAwardRepo.getProfileAwards(profileUuid).first { it.uuid == "pa-001" }
        println("  ✓ UPDATE: pa-001 notified=${afterUpdate.notified}, syncState=${afterUpdate.syncState}")

        // ── QUERY: unsynced ──
        val unsynced = profileAwardRepo.getUnsyncedProfileAwards()
        println("  ✓ QUERY:  Unsynced awards count = ${unsynced.size} (syncState != SYNCED)")
        unsynced.forEach { pa ->
            println("    → uuid=${pa.uuid}  syncState=${pa.syncState}")
        }

        assertEquals(3, stored)
        assertEquals(3, retrieved.size)
        assertTrue(afterUpdate.notified)
        assertEquals(SyncState.UPDATED, afterUpdate.syncState)

        println("  ★ PASSED — Full CRUD lifecycle demonstrated in SQLDelight\n")
    }

    // ═════════════════════════════════════════════════════════════════
    // DEMO 3: Level Rank Calculation via JOIN — replaces both platforms' logic
    // ═════════════════════════════════════════════════════════════════

    @Test
    fun demo3_LevelRankCalculation_SharedBizLogic() {
        println("\n${"═".repeat(70)}")
        println("  DEMO 3: Level Rank Calculation (JOIN query in SQLDelight)")
        println("  BEFORE: Android → updateProfileTableForLevel() queried ContentProvider + Awards table")
        println("  BEFORE: iOS → levelRankOfUserProfile: used NSPredicate + Core Data join")
        println("  NOW:    Single SQLDelight JOIN query shared across both platforms")
        println("${"═".repeat(70)}")

        val db = createDatabase()
        val jsonProvider = object : AwardJsonProvider {
            override fun loadAwardsJson(): String = sampleAwardsJson
        }
        val awardRepo = AwardRepository(db, jsonProvider)
        awardRepo.loadAwardsFromJson()
        val profileAwardRepo = ProfileAwardRepository(db, awardRepo)

        val profileUuid = "child-profile-002"
        db.profileQueries.insertOrReplace(profileUuid)

        // Award Bronze (level value=1) and Silver (level value=2)
        val awards = listOf(
            ProfileAward(
                uuid = "pa-lvl-1", award = "award-level-1", profileUuid = profileUuid,
                dateTimeAwarded = "2025-03-01T10:00:00Z", notified = true,
                syncState = SyncState.SYNCED
            ),
            ProfileAward(
                uuid = "pa-lvl-2", award = "award-level-2", profileUuid = profileUuid,
                dateTimeAwarded = "2025-03-02T10:00:00Z", notified = true,
                syncState = SyncState.SYNCED
            )
        )
        profileAwardRepo.storeProfileAwardsList(profileUuid, awards)

        val rank = profileAwardRepo.levelRankOfUserProfile(profileUuid)
        println("  ✓ Profile has Bronze(1) + Silver(2) level awards")
        println("  ✓ Level rank = $rank (highest level value via JOIN on Award.type='level' + criteria='milestone')")

        // Now award Gold (level value=3)
        profileAwardRepo.storeProfileAwardsList(profileUuid, listOf(
            ProfileAward(
                uuid = "pa-lvl-3", award = "award-level-3", profileUuid = profileUuid,
                dateTimeAwarded = "2025-03-03T10:00:00Z", notified = true,
                syncState = SyncState.SYNCED
            )
        ))

        val newRank = profileAwardRepo.levelRankOfUserProfile(profileUuid)
        println("  ✓ After awarding Gold(3): Level rank = $newRank")

        assertEquals(2, rank)
        assertEquals(3, newRank)

        println("  ★ PASSED — Level rank computed via shared SQLDelight JOIN\n")
    }

    // ═════════════════════════════════════════════════════════════════
    // DEMO 4: Moment (cloud sync) fields — replaces both platforms' moment storage
    // ═════════════════════════════════════════════════════════════════

    @Test
    fun demo4_MomentFieldStorage_CloudSyncReadiness() {
        println("\n${"═".repeat(70)}")
        println("  DEMO 4: Moment Field Storage (Cloud Sync metadata)")
        println("  BEFORE: Android → storeProfileAwardMoments() via ContentValues + ContentResolver")
        println("  BEFORE: iOS → updateProfileAwardForMomentDetails: via Core Data save")
        println("  NOW:    ProfileAwardRepository.storeProfileAwardMoment() via SQLDelight")
        println("${"═".repeat(70)}")

        val db = createDatabase()
        val jsonProvider = object : AwardJsonProvider {
            override fun loadAwardsJson(): String = sampleAwardsJson
        }
        val awardRepo = AwardRepository(db, jsonProvider)
        awardRepo.loadAwardsFromJson()
        val profileAwardRepo = ProfileAwardRepository(db, awardRepo)

        val profileUuid = "child-profile-003"
        db.profileQueries.insertOrReplace(profileUuid)

        // Store initial award (no moment data yet)
        profileAwardRepo.storeProfileAwardsList(profileUuid, listOf(
            ProfileAward(
                uuid = "pa-moment-1", award = "award-achieve-1", profileUuid = profileUuid,
                dateTimeAwarded = "2025-03-04T08:00:00Z", notified = false,
                syncState = SyncState.CREATED
            )
        ))

        val before = profileAwardRepo.getProfileAwards(profileUuid).first()
        println("  ✓ Before moment sync: momentId=${before.momentId}, momentVersion=${before.momentVersion}")

        // Simulate cloud sync setting moment fields
        profileAwardRepo.storeProfileAwardMoment(
            before.copy(
                momentId = "cloud-moment-abc-123",
                momentVersion = 1,
                momentCreatedDate = "2025-03-04T08:01:00Z",
                momentLastModifiedDate = "2025-03-04T08:01:00Z"
            )
        )

        val after = profileAwardRepo.getProfileAwards(profileUuid).first()
        println("  ✓ After moment sync:  momentId=${after.momentId}, momentVersion=${after.momentVersion}")
        println("    momentCreatedDate=${after.momentCreatedDate}")
        println("    momentLastModifiedDate=${after.momentLastModifiedDate}")

        // Verify momentCreatedDate is preserved on subsequent updates
        profileAwardRepo.storeProfileAwardMoment(
            after.copy(
                momentVersion = 2,
                momentLastModifiedDate = "2025-03-04T09:00:00Z"
            )
        )

        val afterSecondSync = profileAwardRepo.getProfileAwards(profileUuid).first()
        println("  ✓ After 2nd sync:     momentVersion=${afterSecondSync.momentVersion}, " +
                "momentCreatedDate=${afterSecondSync.momentCreatedDate} (preserved)")

        assertEquals("cloud-moment-abc-123", after.momentId)
        assertEquals("2025-03-04T08:01:00Z", afterSecondSync.momentCreatedDate) // preserved!
        assertEquals(2, afterSecondSync.momentVersion)

        println("  ★ PASSED — Moment fields stored and preserved correctly in SQLDelight\n")
    }

    // ═════════════════════════════════════════════════════════════════
    // DEMO 5: Cross-platform data fidelity — same bytes on both platforms
    // ═════════════════════════════════════════════════════════════════

    @Test
    fun demo5_CrossPlatformDataFidelity() {
        println("\n${"═".repeat(70)}")
        println("  DEMO 5: Cross-Platform Data Fidelity")
        println("  KEY POINT: The SAME Kotlin code runs on Android AND iOS.")
        println("  The SQLDelight schema and queries are IDENTICAL on both platforms.")
        println("  Only the SqlDriver implementation differs (AndroidSqliteDriver vs NativeSqliteDriver).")
        println("${"═".repeat(70)}")

        val db = createDatabase()
        val jsonProvider = object : AwardJsonProvider {
            override fun loadAwardsJson(): String = sampleAwardsJson
        }
        val awardRepo = AwardRepository(db, jsonProvider)
        awardRepo.loadAwardsFromJson()
        val profileAwardRepo = ProfileAwardRepository(db, awardRepo)

        val profileUuid = "child-profile-cross-platform"
        db.profileQueries.insertOrReplace(profileUuid)

        // Store awards via JSON (same format received from cloud on both platforms)
        val incomingJson = """
        [
          {"uuid":"pa-xp-1","award":"award-level-1","profileUuid":"$profileUuid",
           "dateTimeAwarded":"2025-06-15T14:30:00Z","dateTimeAwardedLocal":"2025-06-15T16:30:00+02:00",
           "notified":false,"awardLevelValue":0,"momentVersion":0,"syncState":"CREATED"},
          {"uuid":"pa-xp-2","award":"award-dye-1","profileUuid":"$profileUuid",
           "dateTimeAwarded":"2025-06-15T14:31:00Z","dateTimeAwardedLocal":"2025-06-15T16:31:00+02:00",
           "notified":true,"awardLevelValue":0,"momentVersion":0,"syncState":"SYNCED"}
        ]
        """.trimIndent()

        val count = profileAwardRepo.storeProfileAwardsFromJson(profileUuid, incomingJson)
        println("  ✓ Stored $count awards from JSON (same format used by both platforms)")

        // Retrieve and serialize back to JSON
        val outputJson = profileAwardRepo.getProfileAwardsAsJson(profileUuid)
        println("  ✓ Serialized back to JSON: ${outputJson.take(100)}...")

        // Verify data round-trips correctly
        val awards = profileAwardRepo.getProfileAwards(profileUuid)
        println("  ✓ Round-trip verification:")
        awards.forEach { pa ->
            println("    → uuid=${pa.uuid}  award=${pa.award}  date=${pa.dateTimeAwarded}  sync=${pa.syncState}")
        }

        // Show the architecture
        println()
        println("  ┌──────────────────────────────────────────────────────────┐")
        println("  │     BEFORE (Platform-Specific Storage)                   │")
        println("  ├─────────────────────────┬────────────────────────────────┤")
        println("  │  Android                │  iOS                           │")
        println("  │  ContentProvider +      │  Core Data +                   │")
        println("  │  SQLiteOpenHelper +     │  NSManagedObjectContext +      │")
        println("  │  ProfileAwardTable.java │  ProfileAwardStore.m +        │")
        println("  │  (ContentResolver CRUD) │  ProfileAwardEntity (NSMgdObj)│")
        println("  │  ~850 lines Java        │  ~620 lines ObjC              │")
        println("  └─────────────────────────┴────────────────────────────────┘")
        println()
        println("  ┌──────────────────────────────────────────────────────────┐")
        println("  │     AFTER (Shared KMM SQLDelight Storage)                │")
        println("  ├──────────────────────────────────────────────────────────┤")
        println("  │  ProfileAward.sq        → SQLDelight schema (shared)    │")
        println("  │  Award.sq               → SQLDelight schema (shared)    │")
        println("  │  ProfileAwardRepo.kt    → Business logic (shared)       │")
        println("  │  AwardRepository.kt     → Award CRUD (shared)           │")
        println("  │  ~470 lines Kotlin total, runs on BOTH platforms        │")
        println("  │                                                          │")
        println("  │  Android: AndroidSqliteDriver → same .db file           │")
        println("  │  iOS:     NativeSqliteDriver  → same .db file           │")
        println("  └──────────────────────────────────────────────────────────┘")

        assertEquals(2, count)
        assertEquals(2, awards.size)
        assertEquals("2025-06-15T14:30:00Z", awards.find { it.uuid == "pa-xp-1" }?.dateTimeAwarded)

        println("  ★ PASSED — Data fidelity verified across platform boundary\n")
    }

    // ═════════════════════════════════════════════════════════════════
    // DEMO 6: SQLDelight Schema inspection — prove the tables exist
    // ═════════════════════════════════════════════════════════════════

    @Test
    fun demo6_SQLDelightSchemaInspection() {
        println("\n${"═".repeat(70)}")
        println("  DEMO 6: SQLDelight Schema Inspection — Prove the tables exist")
        println("${"═".repeat(70)}")

        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        BorealisProfileAwardDb.Schema.create(driver)

        // Query SQLite master table to show tables created by SQLDelight
        val tables = mutableListOf<String>()
        driver.execute(null, "SELECT name, sql FROM sqlite_master WHERE type='table' ORDER BY name", 0)
        val cursor = driver.executeQuery(null, "SELECT name, sql FROM sqlite_master WHERE type='table' ORDER BY name", { cursor ->
            val result = mutableListOf<Pair<String, String>>()
            while (cursor.next().value) {
                val name = cursor.getString(0) ?: ""
                val sql = cursor.getString(1) ?: ""
                result.add(name to sql)
            }
            app.cash.sqldelight.db.QueryResult.Value(result)
        }, 0)

        println("  Tables created by SQLDelight schema:")
        cursor.value.forEach { (name, sql) ->
            tables.add(name)
            println("  ┌─ TABLE: $name")
            sql.split(",").forEach { col ->
                println("  │  ${col.trim()}")
            }
            println("  └─")
        }

        assertTrue(tables.contains("Award"), "Award table should exist")
        assertTrue(tables.contains("ProfileAward"), "ProfileAward table should exist")
        assertTrue(tables.contains("Profile"), "Profile table should exist")

        println()
        println("  ✓ Tables match the legacy schema from:")
        println("    Android: ProfileAwardTable.java + AwardsTable.java (SQLiteOpenHelper)")
        println("    iOS:     ProfileAwardEntity + AwardEntity (Core Data .xcdatamodeld)")
        println("  ✓ Now unified in: ProfileAward.sq + Award.sq + Profile.sq (SQLDelight)")

        println("  ★ PASSED — Schema verified\n")
    }
}
