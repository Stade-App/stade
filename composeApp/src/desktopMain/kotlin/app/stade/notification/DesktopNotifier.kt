package app.stade.notification

import java.awt.AWTException
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.event.ActionListener
import java.util.prefs.Preferences
import javax.imageio.ImageIO

object DesktopNotifier {

    private val flagPrefs: Preferences = Preferences.userRoot().node("app/stade/notifications")
    private const val KEY_BG_NOTICE_SHOWN = "bg_notice_shown"

    @Volatile private var trayIcon: TrayIcon? = null
    @Volatile private var onActivate: (() -> Unit)? = null
    @Volatile private var onQuit: (() -> Unit)? = null

    fun setHandlers(onActivate: (() -> Unit)?, onQuit: (() -> Unit)?) {
        this.onActivate = onActivate
        this.onQuit = onQuit
    }

    fun ensureTray() {
        if (!SystemTray.isSupported()) return
        if (trayIcon != null) return
        synchronized(this) {
            if (trayIcon != null) return
            val tray = SystemTray.getSystemTray()
            val image = runCatching {
                val stream = javaClass.classLoader.getResourceAsStream("drawable/app_icon.png")
                    ?: javaClass.classLoader.getResourceAsStream("composeResources/stade.composeapp.generated.resources/drawable/app_icon.png")
                if (stream != null) ImageIO.read(stream) else null
            }.getOrNull() ?: Toolkit.getDefaultToolkit().getImage("")
            val popup = PopupMenu().apply {
                add(MenuItem("Aç").apply {
                    addActionListener { onActivate?.invoke() }
                })
                addSeparator()
                add(MenuItem("Çıkış").apply {
                    addActionListener { onQuit?.invoke() }
                })
            }
            val icon = TrayIcon(image, "Stade", popup).apply {
                isImageAutoSize = true
                addActionListener(ActionListener { onActivate?.invoke() })
            }
            try {
                tray.add(icon)
                trayIcon = icon
            } catch (_: AWTException) {
            }
        }
    }

    fun removeTray() {
        val icon = trayIcon ?: return
        runCatching { SystemTray.getSystemTray().remove(icon) }
        trayIcon = null
    }

    fun showMessage(title: String, body: String) {
        if (!getNotificationsEnabled().value) return
        ensureTray()
        val icon = trayIcon ?: return
        runCatching {
            icon.displayMessage(title, body, TrayIcon.MessageType.NONE)
        }
    }

    fun notifyBackgroundIfFirstTime(title: String, body: String) {
        if (flagPrefs.getBoolean(KEY_BG_NOTICE_SHOWN, false)) return
        ensureTray()
        val icon = trayIcon ?: return
        runCatching {
            icon.displayMessage(title, body, TrayIcon.MessageType.NONE)
            flagPrefs.putBoolean(KEY_BG_NOTICE_SHOWN, true)
        }
    }

    fun cancel(@Suppress("UNUSED_PARAMETER") contactId: String) {
    }

    fun cancelAll() {
    }
}

