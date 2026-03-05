package com.philips.borealis.kmm.profileaward.di

import com.philips.borealis.kmm.profileaward.db.BorealisProfileAwardDb
import com.philips.borealis.kmm.profileaward.platform.AwardJsonProvider
import com.philips.borealis.kmm.profileaward.platform.AwardOverflowNotifier
import com.philips.borealis.kmm.profileaward.platform.DatabaseDriverFactory
import com.philips.borealis.kmm.profileaward.platform.InventoryMergeHandler
import com.philips.borealis.kmm.profileaward.repository.AwardRepository
import com.philips.borealis.kmm.profileaward.repository.ProfileAwardRepository

class ProfileAwardModule(
    driverFactory: DatabaseDriverFactory,
    jsonProvider: AwardJsonProvider,
    overflowNotifier: AwardOverflowNotifier? = null,
    inventoryMergeHandler: InventoryMergeHandler? = null
) {
    private val database = BorealisProfileAwardDb(driverFactory.createDriver())
    val awardRepository = AwardRepository(database, jsonProvider)
    val profileAwardRepository = ProfileAwardRepository(
        database, awardRepository, overflowNotifier, inventoryMergeHandler
    )
}
