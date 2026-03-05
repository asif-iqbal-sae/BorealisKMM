import java.util.Properties

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("app.cash.sqldelight")
    id("org.jetbrains.kotlin.native.cocoapods")
    id("com.android.library")
    `maven-publish`
}

android {
    compileSdk = 34
    defaultConfig {
        minSdk = 26
    }
    namespace = "com.philips.borealis.kmm.profileaward"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    jvm()

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    cocoapods {
        name = "shared_profile_award"
        summary = "KMM ProfileAward business logic"
        version = "1.0.0"
        ios.deploymentTarget = "15.0"
        framework {
            baseName = "shared_profile_award"
            isStatic = true
        }
        podfile = project.file("../../BorealisiOS/orlando-native/Podfile")
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("app.cash.sqldelight:android-driver:2.1.0")
            }
        }

        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain)
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
            dependencies {
                implementation("app.cash.sqldelight:native-driver:2.1.0")
            }
        }

        val jvmMain by getting {
            dependsOn(commonMain)
            dependencies {
                implementation("app.cash.sqldelight:sqlite-driver:2.1.0")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation("app.cash.sqldelight:sqlite-driver:2.1.0")
            }
        }

        val androidUnitTest by getting {
            dependencies {
                implementation("app.cash.sqldelight:sqlite-driver:2.1.0")
            }
        }
    }
}

sqldelight {
    databases {
        create("BorealisProfileAwardDb") {
            packageName.set("com.philips.borealis.kmm.profileaward.db")
        }
    }
}

// ---------------------------------------------------------------------------
// Publishing to GitHub Packages
// Credentials are read from local.properties (local dev) or env vars (CI)
// ---------------------------------------------------------------------------
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { stream -> load(stream) }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/asif-iqbal-sae/BorealisKMM")
            credentials {
                username = localProps.getProperty("github.actor")
                    ?: System.getenv("GITHUB_ACTOR")
                password = localProps.getProperty("github.token")
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
    publications.withType<MavenPublication> {
        groupId = "com.philips.borealis"
        artifactId = "shared-profile-award-${name.lowercase()}"
        version = "1.0.0"
    }
}
