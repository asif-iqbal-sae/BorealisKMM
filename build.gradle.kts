plugins {
    kotlin("multiplatform").version("1.9.23").apply(false)
    kotlin("plugin.serialization").version("1.9.23").apply(false)
    id("app.cash.sqldelight").version("2.0.2").apply(false)
    id("org.jetbrains.kotlin.native.cocoapods").version("1.9.23").apply(false)
    id("com.android.library").version("8.7.2").apply(false)
}
