package app.stade.notification

import androidx.compose.runtime.State

expect val isNotificationSupported: Boolean

expect fun getNotificationsEnabled(): State<Boolean>

expect fun setNotificationsEnabled(value: Boolean)

expect fun getNotificationPrivacyEnabled(): State<Boolean>

expect fun setNotificationPrivacyEnabled(value: Boolean)

expect fun openNotificationSettings()

expect fun cancelMessagesNotification(contactId: String)

expect fun clearAllMessageNotifications()

/**
 * Platform tarafına yeni gelen bir mesaj için bildirim talebi.
 * Mobil tarafta uygulama foreground service'inden çağrılır,
 * masaüstünde tray icon balonu olarak gösterilir.
 */
expect fun showIncomingMessageNotification(
    contactId: String,
    senderName: String,
    preview: String,
    privacy: Boolean,
    unreadTotal: Int
)

/** Sadece masaüstünde anlamlı: kapatınca tray'e gizleme desteği. Android'de daima false. */
expect val isRunInBackgroundSupported: Boolean

expect fun getRunInBackgroundEnabledCommon(): androidx.compose.runtime.State<Boolean>
expect fun setRunInBackgroundEnabledCommon(value: Boolean)

