import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(17)

    applyDefaultHierarchyTemplate {
        common {
            group("jvmCommon") {
                withAndroidTarget()
                withJvm()
            }
        }
    }

    androidTarget {
        compilations.all {
            kotlinOptions { jvmTarget = "17" }
        }
    }

    jvm("desktop") {
        compilations.all {
            kotlinOptions { jvmTarget = "17" }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.ktor.network)
                implementation(libs.sqldelight.runtime)
                implementation(libs.sqldelight.coroutines)
            }
        }
        val jvmCommonMain by getting {
            dependencies {
                implementation(libs.bouncycastle)
                implementation(libs.zxing.core)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.core.ktx)
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.sqldelight.android.driver)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.sqldelight.sqlite.driver)
            }
        }
    }
}

android {
    namespace = "app.stade"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.stade"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

compose.desktop {
    application {
        mainClass = "app.stade.MainKt"
        // local.properties'ten java.home okunuyor. eğer bir hata alınırsa local.properties'te java yolunun doğru verildiğini doğrula.
        val localProps = Properties()
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { localProps.load(it) }
        javaHome = localProps.getProperty("java.home") ?: System.getProperty("java.home")
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.AppImage, TargetFormat.Exe, TargetFormat.Dmg)
            modules(
                "jdk.unsupported",       // BouncyCastle, Kotlin coroutines (sun.misc.Unsafe)
                "java.sql",              // SQLDelight JDBC/SQLite driver
                "java.naming",           // JNDI, required by many libs
                "java.net.http",         // Ktor HTTP client
                "java.management",       // JMX, required by some libs
                "java.security.jgss",    // Security extensions
                "jdk.crypto.cryptoki",   // Additional crypto support
                "jdk.security.auth"      // Security auth
            )
            packageName = "Stade"
            packageVersion = "0.1.0"
            windows {
                iconFile.set(project.file("src/desktopMain/resources/app_icon_desktop.ico"))
                menuGroup = "Stade"
                upgradeUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
            }
            macOS {
                dmgPackageVersion = "1.0.0"
            }
        }
    }
}

sqldelight {
    databases {
        create("StadeDb") {
            packageName.set("app.stade.db")
            deriveSchemaFromMigrations.set(false)
        }
    }
}
