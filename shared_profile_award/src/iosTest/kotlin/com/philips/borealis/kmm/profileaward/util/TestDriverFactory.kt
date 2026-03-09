package com.philips.borealis.kmm.profileaward.util

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import kotlin.random.Random

actual fun createTestSqlDriver(): SqlDriver =
    NativeSqliteDriver(
        DatabaseConfiguration(
            name = "test_${Random.nextLong()}.db",
            version = 1,
            inMemory = true,
            create = { },
            upgrade = { _, _, _ -> }
        )
    )
