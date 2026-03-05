package com.philips.borealis.kmm.profileaward.platform

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.philips.borealis.kmm.profileaward.db.BorealisProfileAwardDb

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(BorealisProfileAwardDb.Schema, context, "borealis_profile_award.db")
}
