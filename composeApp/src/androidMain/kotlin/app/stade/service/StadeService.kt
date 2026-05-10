package app.stade.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.stade.MainActivity
import app.stade.StadeApplication
import app.stade.notification.getNotificationPrivacyEnabled
import app.stade.notification.getNotificationsEnabled
import app.stade.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class StadeService : Service() {
    private val channelId = "stade.connectivity"
    private val msgChannelId = "stade.messages"
    private val notificationId = 4242
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        ensureChannels()
        startForeground(notificationId, buildForegroundNotification())
        observeMessages()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ── Kanallar ────────────────────────────────────────────────────────────────

    private fun ensureChannels() {
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // Bağlantı kanalı (öncelik: MIN, sessiz)
        if (mgr.getNotificationChannel(channelId) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(channelId, "Bağlantı", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Stade eşlerle bağlantı durumunu korur"
                    setShowBadge(false)
                }
            )
        }
        // Mesaj kanalı (öncelik: HIGH, ses + titreşim)
        if (mgr.getNotificationChannel(msgChannelId) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(msgChannelId, "Mesajlar", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Gelen şifreli mesajlar için bildirimler"
                    setShowBadge(true)
                }
            )
        }
    }

    // ── Ön plan bildirimi ────────────────────────────────────────────────────────

    private fun buildForegroundNotification(): Notification =
        NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Stade çalışıyor")
            .setContentText("Eşler arası bağlantılar etkin")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

    // Gizlilik modunda biriken mesaj sayısı (servis yeniden başladığında sıfırlanır)
    private var hiddenMsgCount = 0
    private val hiddenNotifId = 9001

    // ── SyncEngine olaylarını dinle ──────────────────────────────────────────────

    private fun observeMessages() {
        val container = (application as StadeApplication).container
        scope.launch {
            container.sync.events.collect { event ->
                when (event) {
                    is SyncEngine.SyncEvent.MessageReceived -> {
                        if (!getNotificationsEnabled().value) return@collect
                        if (getNotificationPrivacyEnabled().value) {
                            // Gizlilik modu: içerik gösterilmez, sayaç güncellenir
                            hiddenMsgCount++
                            showPrivacyNotification()
                        } else {
                            val contact = container.contacts.get(event.contactId)
                            val senderName = contact?.nickname ?: "Bilinmeyen"
                            val preview = container.messages.lastMessage(event.contactId)?.body
                                ?: "Yeni mesaj"
                            showMessageNotification(event.contactId, senderName, preview)
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    // ── Gizlilik bildirimi ───────────────────────────────────────────────────────

    private fun showPrivacyNotification() {
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val openIntent = PendingIntent.getActivity(
            this, hiddenNotifId,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, msgChannelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("Stade")
            .setContentText("$hiddenMsgCount yeni mesajınız var")
            .setNumber(hiddenMsgCount)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        mgr.notify(hiddenNotifId, notif)
    }

    // ── Mesaj bildirimi ──────────────────────────────────────────────────────────

    private fun showMessageNotification(contactId: String, senderName: String, preview: String) {
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val openIntent = PendingIntent.getActivity(
            this,
            contactId.hashCode(),
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, msgChannelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(senderName)
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        // Her kişi için ayrı bildirim id'si
        val notifId = (contactId.hashCode() and 0x7FFFFFFF) + 1000
        mgr.notify(notifId, notif)
    }
}
