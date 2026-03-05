package com.philips.borealis.kmm.profileaward.repository

import com.philips.borealis.kmm.profileaward.db.BorealisProfileAwardDb
import com.philips.borealis.kmm.profileaward.model.Award
import com.philips.borealis.kmm.profileaward.model.ProfileAward
import com.philips.borealis.kmm.profileaward.model.SyncState
import com.philips.borealis.kmm.profileaward.platform.AwardJsonProvider
import com.philips.borealis.kmm.profileaward.platform.AwardOverflowNotifier
import com.philips.borealis.kmm.profileaward.util.createTestSqlDriver
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfileAwardRepositoryTest {

    private lateinit var database: BorealisProfileAwardDb
    private lateinit var awardRepository: AwardRepository
    private lateinit var profileAwardRepository: ProfileAwardRepository

    @BeforeTest
    fun setup() {
        val driver = createTestSqlDriver()
        BorealisProfileAwardDb.Schema.create(driver)
        database = BorealisProfileAwardDb(driver)
        awardRepository = AwardRepository(database, FakeJsonProvider())
        profileAwardRepository = ProfileAwardRepository(database, awardRepository)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun makeAward(uuid: String, type: String, criteria: String = "", value: Int = 0): Award {
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
            awardValue = value.toLong()
        )
        return Award(uuid = uuid, type = type, criteria = criteria, value = value)
    }

    private fun makeProfileAward(
        uuid: String,
        awardUuid: String,
        profileUuid: String,
        notified: Boolean = false,
        syncState: SyncState = SyncState.CREATED,
        dateTimeAwarded: String = "2024-01-01T10:00:00Z",
        dateTimeAwardedLocal: String = "2024-01-01T10:00:00Z"
    ): ProfileAward = ProfileAward(
        uuid = uuid,
        award = awardUuid,
        dateTimeAwarded = dateTimeAwarded,
        dateTimeAwardedLocal = dateTimeAwardedLocal,
        notified = notified,
        profileUuid = profileUuid,
        awardLevelValue = 0,
        momentId = null,
        momentVersion = 0,
        momentCreatedDate = null,
        momentLastModifiedDate = null,
        syncState = syncState
    )

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    fun testStoreProfileAwardsList_insertsNew() {
        makeAward("aw1", "level", "milestone", 1)
        val award = makeProfileAward("pa1", "aw1", "profile-A")

        val count = profileAwardRepository.storeProfileAwardsList("profile-A", listOf(award))

        assertEquals(1, count)
        val stored = profileAwardRepository.getProfileAwards("profile-A")
        assertEquals(1, stored.size)
        assertEquals("pa1", stored.first().uuid)
    }

    @Test
    fun testStoreProfileAwardsList_updatesExisting_whenChanged() {
        makeAward("aw1", "level", "milestone", 1)
        val original = makeProfileAward("pa1", "aw1", "profile-A", notified = false)
        profileAwardRepository.storeProfileAwardsList("profile-A", listOf(original))

        // Change notified flag — isSameAward should return false → update
        val updated = original.copy(notified = true)
        val count = profileAwardRepository.storeProfileAwardsList("profile-A", listOf(updated))

        assertEquals(1, count)
        val stored = profileAwardRepository.getProfileAwards("profile-A")
        assertTrue(stored.first().notified)
    }

    @Test
    fun testStoreProfileAwardsList_skipsWhenSame() {
        makeAward("aw1", "achievement")
        val award = makeProfileAward("pa1", "aw1", "profile-A")
        profileAwardRepository.storeProfileAwardsList("profile-A", listOf(award))

        // Store identical award again — isSameAward returns true → skip
        val count = profileAwardRepository.storeProfileAwardsList("profile-A", listOf(award))

        assertEquals(0, count)
    }

    @Test
    fun testGetProfileAwards_returnsOnlyForProfile() {
        makeAward("aw1", "achievement")
        makeAward("aw2", "wearable")
        profileAwardRepository.storeProfileAwardsList(
            "profile-A",
            listOf(makeProfileAward("pa1", "aw1", "profile-A"))
        )
        profileAwardRepository.storeProfileAwardsList(
            "profile-B",
            listOf(makeProfileAward("pa2", "aw2", "profile-B"))
        )

        val profileAwards = profileAwardRepository.getProfileAwards("profile-A")

        assertEquals(1, profileAwards.size)
        assertEquals("pa1", profileAwards.first().uuid)
    }

    @Test
    fun testLevelRankOfUserProfile_returnsMaxLevelValue() {
        makeAward("level-1", "level", "milestone", 1)
        makeAward("level-2", "level", "milestone", 3)
        profileAwardRepository.storeProfileAwardsList(
            "profile-A",
            listOf(
                makeProfileAward("pa1", "level-1", "profile-A"),
                makeProfileAward("pa2", "level-2", "profile-A")
            )
        )

        val rank = profileAwardRepository.levelRankOfUserProfile("profile-A")

        assertEquals(3, rank)
    }

    @Test
    fun testLevelRankOfUserProfile_returnsZeroWhenNoLevelAwards() {
        val rank = profileAwardRepository.levelRankOfUserProfile("profile-empty")
        assertEquals(0, rank)
    }

    @Test
    fun testGetUnsyncedProfileAwards_returnsUnsyncedOnly() {
        makeAward("aw1", "achievement")
        makeAward("aw2", "wearable")
        profileAwardRepository.storeProfileAwardsList(
            "profile-A",
            listOf(
                makeProfileAward("pa1", "aw1", "profile-A", syncState = SyncState.CREATED),
                makeProfileAward("pa2", "aw2", "profile-A", syncState = SyncState.SYNCED)
            )
        )

        val unsynced = profileAwardRepository.getUnsyncedProfileAwards()

        assertEquals(1, unsynced.size)
        assertEquals("pa1", unsynced.first().uuid)
    }

    @Test
    fun testStoreProfileAwardMoment_preservesMomentCreatedDate() {
        makeAward("aw1", "achievement")
        val award = makeProfileAward("pa1", "aw1", "profile-A").copy(
            momentId = "moment-1",
            momentCreatedDate = "2024-01-01T00:00:00Z",
            momentLastModifiedDate = "2024-01-01T00:00:00Z"
        )
        profileAwardRepository.storeProfileAwardMoment(award)

        // Now update moment with a different created date — should be preserved
        val updated = award.copy(
            momentCreatedDate = "2025-06-01T00:00:00Z",
            momentLastModifiedDate = "2025-06-01T12:00:00Z"
        )
        profileAwardRepository.storeProfileAwardMoment(updated)

        val stored = profileAwardRepository.getProfileAwards("profile-A").first()
        // Original momentCreatedDate must be preserved
        assertEquals("2024-01-01T00:00:00Z", stored.momentCreatedDate)
        // LastModifiedDate should be updated
        assertEquals("2025-06-01T12:00:00Z", stored.momentLastModifiedDate)
    }

    @Test
    fun testDeDuplicateAwards_triggersOverflowWhenMoreThan5New() {
        makeAward("aw1", "achievement")
        makeAward("aw2", "achievement")
        makeAward("aw3", "achievement")
        makeAward("aw4", "achievement")
        makeAward("aw5", "achievement")
        makeAward("aw6", "achievement")

        // Pre-store 6 un-notified awards for the profile
        val existingList = (1..6).map { i ->
            makeProfileAward("pa$i", "aw$i", "profile-A", notified = false)
        }
        profileAwardRepository.storeProfileAwardsList("profile-A", existingList)

        val overflowNotifier = FakeOverflowNotifier()
        val repo = ProfileAwardRepository(database, awardRepository, overflowNotifier)

        // Attempt to store one more — unnotified count > 5 should trigger overflow
        val incomingJson = """[{"uuid":"pa7","award":"aw1","dateTimeAwarded":"2024-06-01T10:00:00Z","dateTimeAwardedLocal":"2024-06-01T10:00:00Z","notified":false,"profileUuid":"profile-A","awardLevelValue":0,"momentVersion":0,"syncState":"CREATED"}]"""
        repo.deDuplicateAndStore("profile-A", incomingJson)

        assertTrue(overflowNotifier.wasCalled, "Expected overflowNotifier.onTooManyNewAwards() to be called")
    }
}

// -------------------------------------------------------------------------
// Test fakes
// -------------------------------------------------------------------------

private class FakeJsonProvider : AwardJsonProvider {
    override fun loadAwardsJson(): String? = null
}

private class FakeOverflowNotifier : AwardOverflowNotifier {
    var wasCalled = false
    override fun onTooManyNewAwards() {
        wasCalled = true
    }
}


