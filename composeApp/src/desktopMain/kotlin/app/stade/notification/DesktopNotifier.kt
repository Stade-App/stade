package app.stade.notification

import java.awt.AWTException
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.event.ActionListener
import javax.imageio.ImageIO

/**
 * Masaüstünde sistem tepsisinden bildirim göstermeyi ve uygulamayı arka planda
 * tutmayı sağlayan singleton. Pencere kapatıldığında uygulama tray'e iner ve
 * yeni mesaj geldiğinde TrayIcon.displayMessage ile balon bildirim atar.
 */
object DesktopNotifier {

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
                // Tray eklenemedi
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

    fun cancel(@Suppress("UNUSED_PARAMETER") contactId: String) {
        // TrayIcon.displayMessage tek seferlik bir balon olduğu için
        // kişi başına özel kapatma desteklemiyor; no-op.
    }

    fun cancelAll() {
        // Aynı sebeple no-op.
    }
}

