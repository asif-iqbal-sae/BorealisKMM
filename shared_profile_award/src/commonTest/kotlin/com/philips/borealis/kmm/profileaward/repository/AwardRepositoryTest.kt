package com.philips.borealis.kmm.profileaward.repository

import com.philips.borealis.kmm.profileaward.db.BorealisProfileAwardDb
import com.philips.borealis.kmm.profileaward.platform.AwardJsonProvider
import com.philips.borealis.kmm.profileaward.util.createTestSqlDriver
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AwardRepositoryTest {

    private lateinit var database: BorealisProfileAwardDb
    private lateinit var awardRepository: AwardRepository

    @BeforeTest
    fun setup() {
        val driver = createTestSqlDriver()
        BorealisProfileAwardDb.Schema.create(driver)
        database = BorealisProfileAwardDb(driver)
        awardRepository = AwardRepository(database, FakeAwardJsonProvider())
    }

    @Test
    fun testLoadAwardsFromJson_storesAllAwards() {
        val jsonProvider = FakeAwardJsonProvider(
            """[{"uuid":"a1","name":"Star","type":"level","badge":"star","criteria":"milestone","value":1,"consumable":false,"inventoryAwarded":0}]"""
        )
        val repo = AwardRepository(database, jsonProvider)

        val count = repo.loadAwardsFromJson()

        assertEquals(1, count)
        val awards = repo.getAllAwards()
        assertEquals(1, awards.size)
        assertEquals("a1", awards.first().uuid)
        assertEquals("Star", awards.first().name)
        assertEquals("level", awards.first().type)
    }

    @Test
    fun testGetAwardById_returnsCorrectAward() {
        val jsonProvider = FakeAwardJsonProvider(
            """[{"uuid":"a1","name":"Star","type":"level","badge":"star","criteria":"milestone","value":1,"consumable":false,"inventoryAwarded":0},
               {"uuid":"a2","name":"Badge","type":"achievement","badge":"badge","criteria":"","value":2,"consumable":false,"inventoryAwarded":0}]"""
        )
        val repo = AwardRepository(database, jsonProvider)
        repo.loadAwardsFromJson()

        val award = repo.getAwardById("a2")

        assertNotNull(award)
        assertEquals("a2", award.uuid)
        assertEquals("Badge", award.name)
        assertEquals("achievement", award.type)
    }

    @Test
    fun testGetAwardById_returnsNullForUnknownUuid() {
        val repo = AwardRepository(database, FakeAwardJsonProvider("""[]"""))
        repo.loadAwardsFromJson()

        assertNull(repo.getAwardById("nonexistent"))
    }

    @Test
    fun testGetAwardsByType_filtersCorrectly() {
        val jsonProvider = FakeAwardJsonProvider(
            """[
                {"uuid":"a1","name":"LevelStar","type":"level","criteria":"milestone","value":1},
                {"uuid":"a2","name":"Achievement","type":"achievement","criteria":"","value":0},
                {"uuid":"a3","name":"LevelTwo","type":"level","criteria":"milestone","value":2}
               ]"""
        )
        val repo = AwardRepository(database, jsonProvider)
        repo.loadAwardsFromJson()

        val levelAwards = repo.getAwardsByType("level")

        assertEquals(2, levelAwards.size)
        assertTrue(levelAwards.all { it.type == "level" })
    }

    @Test
    fun testLoadAwardsFromJson_emptyJson_returnsZero() {
        val jsonProvider = FakeAwardJsonProvider("[]")
        val repo = AwardRepository(database, jsonProvider)

        val count = repo.loadAwardsFromJson()

        assertEquals(0, count)
        assertEquals(0, repo.getAllAwards().size)
    }

    @Test
    fun testLoadAwardsFromJson_nullJson_returnsZero() {
        val jsonProvider = FakeAwardJsonProvider(null)
        val repo = AwardRepository(database, jsonProvider)

        val count = repo.loadAwardsFromJson()

        assertEquals(0, count)
    }
}

private class FakeAwardJsonProvider(private val json: String? = null) : AwardJsonProvider {
    override fun loadAwardsJson(): String? = json
}
