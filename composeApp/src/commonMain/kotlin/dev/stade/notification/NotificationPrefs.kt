package dev.stade.notification

import androidx.compose.runtime.State

expect val isNotificationSupported: Boolean

expect val isSystemNotificationSettingsSupported: Boolean

expect fun getNotificationsEnabled(): State<Boolean>

expect fun setNotificationsEnabled(value: Boolean)

expect fun getNotificationPrivacyEnabled(): State<Boolean>

expect fun setNotificationPrivacyEnabled(value: Boolean)

expect fun openNotificationSettings()

expect fun cancelMessagesNotification(contactId: String)

expect fun clearAllMessageNotifications()

expect fun showIncomingMessageNotification(
    contactId: String,
    senderName: String,
    preview: String,
    privacy: Boolean,
    unreadTotal: Int
)

expect val isRunInBackgroundSupported: Boolean

expect fun getRunInBackgroundEnabledCommon(): androidx.compose.runtime.State<Boolean>
expect fun setRunInBackgroundEnabledCommon(value: Boolean)

