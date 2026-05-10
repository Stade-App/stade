package app.stade.notification

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import app.stade.StadeApplication

actual val isNotificationSupported: Boolean = true

private val prefs get() = StadeApplication.instance
    .getSharedPreferences("stade_notifications", Context.MODE_PRIVATE)

private val _notificationsEnabled by lazy {
    mutableStateOf(prefs.getBoolean("messages_enabled", true))
}

actual fun getNotificationsEnabled(): State<Boolean> = _notificationsEnabled

actual fun setNotificationsEnabled(value: Boolean) {
    _notificationsEnabled.value = value
    prefs.edit().putBoolean("messages_enabled", value).apply()
}

private val _notificationPrivacyEnabled by lazy {
    mutableStateOf(prefs.getBoolean("privacy_enabled", false))
}

actual fun getNotificationPrivacyEnabled(): State<Boolean> = _notificationPrivacyEnabled

actual fun setNotificationPrivacyEnabled(value: Boolean) {
    _notificationPrivacyEnabled.value = value
    prefs.edit().putBoolean("privacy_enabled", value).apply()
}

actual fun openNotificationSettings() {
    val ctx = StadeApplication.instance
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { ctx.startActivity(intent) }
}

actual fun cancelMessagesNotification(contactId: String) {
    val mgr = StadeApplication.instance
        .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val notifId = (contactId.hashCode() and 0x7FFFFFFF) + 1000
    mgr.cancel(notifId)
    mgr.cancel(NotificationIds.HIDDEN_MESSAGES)
}

actual fun clearAllMessageNotifications() {
    val ctx = StadeApplication.instance
    val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    runCatching {
        mgr.activeNotifications.forEach { sbn ->
            if (sbn.packageName == ctx.packageName && sbn.notification.channelId == "stade.messages") {
                mgr.cancel(sbn.id)
            }
        }
    }
    mgr.cancel(NotificationIds.HIDDEN_MESSAGES)
}


