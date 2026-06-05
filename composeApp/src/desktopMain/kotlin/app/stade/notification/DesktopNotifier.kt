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
    private const val TRAY_TOOLTIP = "Stade"

    @Volatile private var trayIcon: TrayIcon? = null
    @Volatile private var onActivate: (() -> Unit)? = null
    @Volatile private var onQuit: (() -> Unit)? = null

    fun setHandlers(onActivate: (() -> Unit)?, onQuit: (() -> Unit)?) {
        this.onActivate = onActivate
        this.onQuit = onQuit
    }

    fun ensureTray() {
        if (!SystemTray.isSupported()) return
        synchronized(this) {
            val tray = SystemTray.getSystemTray()
            tray.trayIcons.forEach { existing ->
                if (existing === trayIcon) return@forEach
                if (existing.toolTip == TRAY_TOOLTIP) {
                    runCatching { tray.remove(existing) }
                }
            }
            if (trayIcon != null && trayIcon !in tray.trayIcons) {
                trayIcon = null
            }
            if (trayIcon != null) return
            val image = runCatching {
                val stream = javaClass.classLoader.getResourceAsStream("drawable/app_tray_icon.png")
                    ?: javaClass.classLoader.getResourceAsStream("composeResources/stade.composeapp.generated.resources/drawable/app_tray_icon.png")
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
            val icon = TrayIcon(image, TRAY_TOOLTIP, popup).apply {
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
        synchronized(this) {
            val tray = runCatching { SystemTray.getSystemTray() }.getOrNull()
            if (tray != null) {
                tray.trayIcons.forEach { existing ->
                    if (existing === trayIcon || existing.toolTip == TRAY_TOOLTIP) {
                        runCatching { tray.remove(existing) }
                    }
                }
            }
            trayIcon = null
        }
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

