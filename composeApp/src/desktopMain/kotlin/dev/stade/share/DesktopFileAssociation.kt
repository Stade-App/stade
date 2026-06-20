package dev.stade.share

import java.io.File
import java.util.Locale

object DesktopFileAssociation {

    fun ensureRegistered() {
        val exe = currentExecutable() ?: return
        val os = System.getProperty("os.name").lowercase(Locale.ROOT)
        runCatching {
            when {
                os.contains("win") -> registerWindows(exe)
                os.contains("linux") -> registerLinux(exe)
            }
        }
    }

    private fun currentExecutable(): String? {
        val cmd = runCatching {
            ProcessHandle.current().info().command().orElse(null)
        }.getOrNull() ?: return null
        val lower = cmd.lowercase(Locale.ROOT)
        val isRawJvm = lower.endsWith("java.exe") || lower.endsWith("javaw.exe") ||
            lower.endsWith(File.separator + "java") || lower.endsWith("/java")
        if (isRawJvm) return null
        return cmd
    }

    private fun registerWindows(exe: String) {
        val progId = "Stade.Invite"
        val classes = "HKCU\\Software\\Classes"
        val commands = listOf(
            arrayOf("reg", "add", "$classes\\.stadeid", "/ve", "/d", progId, "/f"),
            arrayOf("reg", "add", "$classes\\.stadeid", "/v", "Content Type", "/d", "application/x-stade-invite", "/f"),
            arrayOf("reg", "add", "$classes\\$progId", "/ve", "/d", "Stade Invite", "/f"),
            arrayOf("reg", "add", "$classes\\$progId\\DefaultIcon", "/ve", "/d", "\"$exe\",0", "/f"),
            arrayOf("reg", "add", "$classes\\$progId\\shell\\open\\command", "/ve", "/d", "\"$exe\" \"%1\"", "/f")
        )
        for (c in commands) {
            runCatching {
                ProcessBuilder(*c).redirectErrorStream(true).start().waitFor()
            }
        }
    }

    private fun registerLinux(exe: String) {
        val home = System.getProperty("user.home")?.takeIf { it.isNotBlank() } ?: return
        val appsDir = File("$home/.local/share/applications").apply { mkdirs() }
        val mimeDir = File("$home/.local/share/mime/packages").apply { mkdirs() }
        File(mimeDir, "stade.xml").writeText(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<mime-info xmlns=\"http://www.freedesktop.org/standards/shared-mime-info\">\n" +
                "  <mime-type type=\"application/x-stade-invite\">\n" +
                "    <comment>Stade Invite</comment>\n" +
                "    <glob pattern=\"*.stadeid\"/>\n" +
                "  </mime-type>\n" +
                "</mime-info>\n"
        )
        File(appsDir, "stade.desktop").writeText(
            "[Desktop Entry]\n" +
                "Type=Application\n" +
                "Name=Stade\n" +
                "Exec=\"$exe\" %f\n" +
                "MimeType=application/x-stade-invite;\n" +
                "Terminal=false\n"
        )
        runCatching { ProcessBuilder("update-mime-database", "$home/.local/share/mime").start().waitFor() }
        runCatching { ProcessBuilder("update-desktop-database", appsDir.absolutePath).start().waitFor() }
    }
}
