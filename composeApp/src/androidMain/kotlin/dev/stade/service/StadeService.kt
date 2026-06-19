package dev.stade.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.stade.MainActivity
import dev.stade.StadeApplication
import dev.stade.notification.NotificationIds
import dev.stade.notification.getNotificationPrivacyEnabled
import dev.stade.notification.getNotificationsEnabled
import dev.stade.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class StadeService : Service() {
    private val channelId = "stade.connectivity"
    private val msgChannelId = "stade.messages"
    private val notificationId = NotificationIds.FOREGROUND
    private val hiddenNotifId = NotificationIds.HIDDEN_MESSAGES
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


    private fun ensureChannels() {
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(channelId) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(channelId, "Bağlantı", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Stade eşlerle bağlantı durumunu korur"
                    setShowBadge(false)
                }
            )
        }
        if (mgr.getNotificationChannel(msgChannelId) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(msgChannelId, "Mesajlar", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Gelen şifreli mesajlar için bildirimler"
                    setShowBadge(true)
                }
            )
        }
    }


    private fun buildForegroundNotification(): Notification =
        NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Stade çalışıyor")
            .setContentText("Eşler arası bağlantılar etkin")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()



    private fun observeMessages() {
        val app = (application as StadeApplication)
        scope.launch {
            app.containerFlow.collectLatest { container ->
                if (container == null) return@collectLatest
                launch {
                    container.sync.events.collect { event ->
                        when (event) {
                            is SyncEngine.SyncEvent.MessageReceived -> {
                                if (!getNotificationsEnabled().value) return@collect
                                if (container.isAppInForeground.value && container.activeContactId == event.contactId) return@collect
                                if (getNotificationPrivacyEnabled().value) {
                                    val total = runCatching { container.messages.totalUnread() }.getOrDefault(0L).toInt()
                                    if (total > 0) showPrivacyNotification(total)
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
                launch {
                    val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    container.messages.observeTotalUnread().distinctUntilChanged().collect { count ->
                        if (count == 0L) {
                            mgr.cancel(hiddenNotifId)
                        } else if (getNotificationPrivacyEnabled().value && getNotificationsEnabled().value) {
                            showPrivacyNotification(count.toInt())
                        }
                    }
                }
            }
        }
    }


    private fun showPrivacyNotification(count: Int) {
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
            .setContentText("$count yeni mesajınız var")
            .setNumber(count)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        mgr.notify(hiddenNotifId, notif)
    }



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
        val notifId = (contactId.hashCode() and 0x7FFFFFFF) + 1000
        mgr.notify(notifId, notif)
    }
}
