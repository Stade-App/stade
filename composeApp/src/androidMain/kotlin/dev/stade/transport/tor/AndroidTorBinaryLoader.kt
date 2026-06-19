package dev.stade.transport.tor

import android.content.Context
import java.io.File
import java.io.FileOutputStream

internal object AndroidTorBinaryLoader {

    fun prepare(context: Context, appRoot: File): TorLayout {
        val torRoot = File(appRoot, "tor").apply { if (!exists()) mkdirs() }
        val dataDir = File(torRoot, "data").apply {
            if (!exists()) mkdirs()
            runCatching {
                val perms = java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")
                java.nio.file.Files.setPosixFilePermissions(toPath(), perms)
            }
        }

        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val torExe = File(nativeDir, "libtor.so").takeIf { it.isFile }
            ?: error("Embedded Tor binary missing: ${nativeDir.absolutePath}/libtor.so")
        runCatching { torExe.setExecutable(true, false) }

        val geoip = copyAsset(context, "tor/geoip", File(torRoot, "geoip"))
        val geoip6 = copyAsset(context, "tor/geoip6", File(torRoot, "geoip6"))

        return TorLayout(
            torDir = torRoot,
            executable = torExe,
            dataDir = dataDir,
            geoipFile = geoip,
            geoip6File = geoip6
        )
    }

    private fun copyAsset(context: Context, assetPath: String, dest: File): File? {
        return runCatching {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(dest).use { out -> input.copyTo(out) }
            }
            dest
        }.getOrNull()
    }
}

