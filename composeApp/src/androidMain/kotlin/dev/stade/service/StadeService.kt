package dev.stade.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dev.stade.MainActivity
import dev.stade.StadeApplication
import dev.stade.notification.NotificationAvatar
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
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        dev.stade.ui.i18n.I18n.current =
            dev.stade.ui.i18n.localeToStrings(dev.stade.ui.i18n.getLocalePreference().value)
        ensureChannels()
        startForeground(notificationId, buildForegroundNotification())
        observeMessages()
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        networkCallback?.let { cb ->
            runCatching {
                (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(cb)
            }
        }
        networkCallback = null
        scope.cancel()
        super.onDestroy()
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { notifyNetworkChanged() }
            override fun onLost(network: Network) { notifyNetworkChanged() }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }
            .onSuccess { networkCallback = cb }
    }

    private fun notifyNetworkChanged() {
        val app = application as StadeApplication
        app.containerFlow.value?.connections?.onNetworkChanged()
    }


    private fun ensureChannels() {
        val strings = dev.stade.ui.i18n.I18n.current
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(channelId) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(channelId, strings.notifConnectionChannelName, NotificationManager.IMPORTANCE_MIN).apply {
                    description = strings.notifConnectionChannelDesc
                    setShowBadge(false)
                }
            )
        }
        if (mgr.getNotificationChannel(msgChannelId) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(msgChannelId, strings.notifMessagesChannelName, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = strings.notifMessagesChannelDesc
                    setShowBadge(true)
                }
            )
        }
    }


    private fun buildForegroundNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            notificationId,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_GO_HOME, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(dev.stade.ui.i18n.I18n.current.notifRunningTitle)
            .setContentText(dev.stade.ui.i18n.I18n.current.notifRunningText)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .build()
    }



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
                                    val senderName = contact?.nickname ?: dev.stade.ui.i18n.I18n.current.unknownNickname
                                    val preview = container.messages.lastMessage(event.contactId)?.body
                                        ?.let { dev.stade.message.previewBody(it, dev.stade.ui.i18n.I18n.current.photoMessage, dev.stade.ui.i18n.I18n.current.voiceMessage, dev.stade.ui.i18n.I18n.current.videoMessage) }
                                        ?: dev.stade.ui.i18n.I18n.current.notifNewMessageFallback
                                    showMessageNotification(event.contactId, senderName, preview)
                                }
                            }
                            is SyncEngine.SyncEvent.StadiumMessageReceived -> {
                                if (!getNotificationsEnabled().value) return@collect
                                val stadium = container.stadiums.getStadium(event.stadiumId)
                                if (stadium == null || stadium.muted) return@collect
                                if (container.isAppInForeground.value && container.activeContactId == event.stadiumId) return@collect
                                val preview = container.stadiums.lastMessage(event.stadiumId)?.body
                                    ?.let { dev.stade.message.previewBody(it, dev.stade.ui.i18n.I18n.current.photoMessage, dev.stade.ui.i18n.I18n.current.voiceMessage, dev.stade.ui.i18n.I18n.current.videoMessage) }
                                    ?: dev.stade.ui.i18n.I18n.current.notifNewMessageFallback
                                showMessageNotification(event.stadiumId, stadium.name, preview, isStadium = true)
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
            .setContentText(dev.stade.ui.i18n.I18n.current.notifNewMessages(count))
            .setNumber(count)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        mgr.notify(hiddenNotifId, notif)
    }



    private fun showMessageNotification(contactId: String, senderName: String, preview: String, isStadium: Boolean = false) {
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val openIntent = PendingIntent.getActivity(
            this,
            contactId.hashCode(),
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (isStadium) putExtra(MainActivity.EXTRA_OPEN_STADIUM_ID, contactId)
                else putExtra(MainActivity.EXTRA_OPEN_CHAT_ID, contactId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val avatarBitmap = runCatching { NotificationAvatar.bitmapFor(senderName) }.getOrNull()
        val avatarIcon = avatarBitmap?.let { IconCompat.createWithBitmap(it) }
        val senderPerson = Person.Builder()
            .setName(senderName)
            .setKey(contactId)
            .apply { avatarIcon?.let { setIcon(it) } }
            .build()
        val userPerson = Person.Builder().setName("Me").build()

        val shortcutId = "${if (isStadium) "stadium" else "contact"}_$contactId"
        runCatching {
            val shortcutIntent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                if (isStadium) putExtra(MainActivity.EXTRA_OPEN_STADIUM_ID, contactId)
                else putExtra(MainActivity.EXTRA_OPEN_CHAT_ID, contactId)
            }
            val shortcut = ShortcutInfoCompat.Builder(this, shortcutId)
                .setShortLabel(senderName)
                .setLongLived(true)
                .setIntent(shortcutIntent)
                .setPerson(senderPerson)
                .setCategories(setOf("android.shortcut.conversation"))
                .apply { avatarIcon?.let { setIcon(it) } }
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)
        }

        val notif = NotificationCompat.Builder(this, msgChannelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .apply { avatarBitmap?.let { setLargeIcon(it) } }
            .setStyle(
                NotificationCompat.MessagingStyle(userPerson)
                    .addMessage(preview, System.currentTimeMillis(), senderPerson)
            )
            .setContentTitle(senderName)
            .setContentText(preview)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setShortcutId(shortcutId)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        val notifId = (contactId.hashCode() and 0x7FFFFFFF) + 1000
        mgr.notify(notifId, notif)
    }
}
