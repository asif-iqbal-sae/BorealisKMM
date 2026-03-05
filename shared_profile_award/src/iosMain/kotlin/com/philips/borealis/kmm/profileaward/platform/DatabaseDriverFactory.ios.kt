package com.philips.borealis.kmm.profileaward.platform

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.philips.borealis.kmm.profileaward.db.BorealisProfileAwardDb

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(BorealisProfileAwardDb.Schema, "borealis_profile_award.db")
}
