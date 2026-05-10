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

