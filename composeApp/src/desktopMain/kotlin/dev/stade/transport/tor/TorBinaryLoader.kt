package dev.stade.transport.tor

import java.io.File
import java.util.Locale

internal object TorBinaryLoader {

    fun prepare(appRoot: File): TorLayout {
        val plat = detectPlatformKey()
        val resourcePath = "/tor/$plat"
        val targetRoot = File(appRoot, "tor/$plat")
        if (!targetRoot.exists()) targetRoot.mkdirs()
        val torRunDir = File(targetRoot, "bin")
        if (!torRunDir.exists()) torRunDir.mkdirs()
        val markerVersionFile = File(targetRoot, ".extracted")
        val expectedMarker = bundleVersionMarker()
        val needsExtract = !markerVersionFile.exists() || markerVersionFile.readText().trim() != expectedMarker

        val entries = listResourceEntries(resourcePath)
        if (entries.isEmpty()) {
            error("Embedded Tor binaries missing for platform '$plat' (expected resources at $resourcePath). Did the Gradle task downloadTorBinaries run?")
        }
        if (needsExtract) {
            torRunDir.listFiles()?.forEach { it.deleteRecursively() }
            entries.forEach { entry ->
                val out = File(torRunDir, entry.relativePath)
                out.parentFile?.mkdirs()
                javaClass.getResourceAsStream("$resourcePath/${entry.relativePath}")
                    ?.use { input -> out.outputStream().use { input.copyTo(it) } }
                    ?: error("Resource missing: $resourcePath/${entry.relativePath}")
                if (entry.executable) {
                    runCatching { out.setExecutable(true, false) }
                }
            }
            markerVersionFile.writeText(expectedMarker)
        }

        val torExe = locateTorExecutable(torRunDir) ?: error("Tor executable not found under $torRunDir")
        runCatching { torExe.setExecutable(true, false) }

        val dataDir = File(targetRoot, "data")
        if (!dataDir.exists()) dataDir.mkdirs()
        runCatching {
            val perms = java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")
            java.nio.file.Files.setPosixFilePermissions(dataDir.toPath(), perms)
        }
        val geoip = File(torRunDir, "data/geoip").takeIf { it.exists() }
            ?: File(torRunDir, "geoip").takeIf { it.exists() }
        val geoip6 = File(torRunDir, "data/geoip6").takeIf { it.exists() }
            ?: File(torRunDir, "geoip6").takeIf { it.exists() }
        val obfs4Exe = locateObfs4Executable(torRunDir)
        runCatching { obfs4Exe?.setExecutable(true, false) }
        return TorLayout(torRunDir, torExe, dataDir, geoip, geoip6, obfs4Exe)
    }

    private fun locateObfs4Executable(dir: File): File? {
        val candidates = listOf(
            File(dir, "tor/pluggable_transports/lyrebird.exe"),
            File(dir, "tor/pluggable_transports/lyrebird"),
            File(dir, "pluggable_transports/lyrebird.exe"),
            File(dir, "pluggable_transports/lyrebird")
        )
        return candidates.firstOrNull { it.isFile }
    }

    fun detectPlatformKey(): String {
        val osRaw = System.getProperty("os.name").lowercase(Locale.ROOT)
        val archRaw = System.getProperty("os.arch").lowercase(Locale.ROOT)
        val os = when {
            osRaw.contains("win") -> "windows"
            osRaw.contains("mac") || osRaw.contains("darwin") -> "macos"
            osRaw.contains("nux") || osRaw.contains("nix") -> "linux"
            else -> error("Unsupported OS for embedded Tor: $osRaw")
        }
        val arch = when {
            archRaw.contains("aarch64") || archRaw.contains("arm64") -> "aarch64"
            archRaw.contains("64") -> "x86_64"
            else -> error("Unsupported arch for embedded Tor: $archRaw")
        }
        if (os == "windows" && arch != "x86_64") error("Embedded Tor only ships windows-x86_64")
        if (os == "linux" && arch != "x86_64") error("Embedded Tor only ships linux-x86_64")
        return "$os-$arch"
    }

    private fun bundleVersionMarker(): String = "v2-${detectPlatformKey()}"

    private fun locateTorExecutable(dir: File): File? {
        val candidates = listOf(
            File(dir, "tor/tor.exe"),
            File(dir, "tor/tor"),
            File(dir, "tor.exe"),
            File(dir, "tor")
        )
        return candidates.firstOrNull { it.isFile }
    }

    private data class EntryRef(val relativePath: String, val executable: Boolean)

    private fun listResourceEntries(resourcePath: String): List<EntryRef> {
        val url = javaClass.getResource(resourcePath) ?: return emptyList()
        val proto = url.protocol
        val collected = mutableListOf<EntryRef>()
        if (proto == "file") {
            val rootFile = java.nio.file.Paths.get(url.toURI()).toFile()
            rootFile.walkTopDown().filter { it.isFile }.forEach { f ->
                val rel = f.relativeTo(rootFile).path.replace(File.separatorChar, '/')
                collected += EntryRef(rel, isExecutableName(rel))
            }
        } else if (proto == "jar") {
            val spec = url.toString()
            val bang = spec.indexOf("!/")
            val jarPath = spec.substring("jar:file:".length, bang)
            val inside = spec.substring(bang + 2).trimEnd('/')
            java.util.jar.JarFile(java.io.File(java.net.URLDecoder.decode(jarPath, Charsets.UTF_8))).use { jar ->
                val entries = jar.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    if (e.isDirectory) continue
                    val name = e.name
                    if (!name.startsWith("$inside/")) continue
                    val rel = name.substring(inside.length + 1)
                    collected += EntryRef(rel, isExecutableName(rel))
                }
            }
        }
        return collected
    }

    private fun isExecutableName(rel: String): Boolean {
        val name = rel.substringAfterLast('/')
        if (name.equals("tor", ignoreCase = true)) return true
        if (name.equals("tor.exe", ignoreCase = true)) return true
        if (name.equals("lyrebird", ignoreCase = true)) return true
        if (name.equals("lyrebird.exe", ignoreCase = true)) return true
        if (name.endsWith(".so") || name.contains(".so.") || name.endsWith(".dylib")) return true
        return false
    }
}

