import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties
import java.net.URI
import java.security.MessageDigest

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
                runtimeOnly(libs.slf4j.nop)
            }
            resources.srcDir(layout.buildDirectory.dir("torBinaries"))
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

val torBundleVersion: String = providers.gradleProperty("tor.bundle.version").getOrElse("13.5.6")
val torDistBase = "https://archive.torproject.org/tor-package-archive/torbrowser"

data class TorPlatform(val key: String, val triple: String, val shaProp: String)

val torPlatforms = listOf(
    TorPlatform("windows-x86_64", "windows-x86_64", "tor.sha256.windows.x86_64"),
    TorPlatform("linux-x86_64", "linux-x86_64", "tor.sha256.linux.x86_64"),
    TorPlatform("macos-x86_64", "macos-x86_64", "tor.sha256.macos.x86_64"),
    TorPlatform("macos-aarch64", "macos-aarch64", "tor.sha256.macos.aarch64")
)

val torBinariesRoot = layout.buildDirectory.dir("torBinaries/tor")

val downloadTorBinaries by tasks.registering {
    group = "tor"
    description = "Downloads and extracts the Tor Expert Bundle for each desktop platform."
    val outRoot = torBinariesRoot
    outputs.dir(outRoot)
    inputs.property("torVersion", torBundleVersion)
    outputs.upToDateWhen {
        val rootDir = outRoot.get().asFile
        torPlatforms.all { plat ->
            rootDir.resolve("${plat.key}/.ok-$torBundleVersion").exists()
        }
    }
    doLast {
        val rootDir = outRoot.get().asFile
        rootDir.mkdirs()
        torPlatforms.forEach { plat ->
            val targetDir = rootDir.resolve(plat.key)
            val marker = targetDir.resolve(".ok-$torBundleVersion")
            if (marker.exists()) return@forEach
            targetDir.deleteRecursively()
            targetDir.mkdirs()
            val fname = "tor-expert-bundle-${plat.triple}-$torBundleVersion.tar.gz"
            val urlStr = "$torDistBase/$torBundleVersion/$fname"
            logger.lifecycle("Downloading $urlStr")
            val tmp = File.createTempFile("tor-bundle-${plat.key}-", ".tar.gz")
            try {
                URI(urlStr).toURL().openStream().use { input ->
                    tmp.outputStream().use { out -> input.copyTo(out) }
                }
                val actualSha = MessageDigest.getInstance("SHA-256")
                    .digest(tmp.readBytes())
                    .joinToString("") { byte -> "%02x".format(byte) }
                val expected = providers.gradleProperty(plat.shaProp).orNull?.trim()?.takeIf { it.isNotEmpty() }
                if (expected == null) {
                    logger.warn("[tor] ${plat.key} SHA-256 NOT pinned. Actual=$actualSha  -> set ${plat.shaProp} in gradle.properties")
                } else if (!expected.equals(actualSha, ignoreCase = true)) {
                    throw GradleException("Tor binary ${plat.key} hash mismatch. expected=$expected actual=$actualSha")
                }
                copy {
                    from(tarTree(resources.gzip(tmp)))
                    into(targetDir)
                }
                marker.writeText(actualSha)
            } finally {
                tmp.delete()
            }
        }
    }
}

tasks.matching { it.name == "desktopProcessResources" || it.name == "jvmProcessResources" }.configureEach {
    dependsOn(downloadTorBinaries)
}
