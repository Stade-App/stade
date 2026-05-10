package app.stade.notification

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

actual val isNotificationSupported: Boolean = false

private val _disabled = mutableStateOf(false)

actual fun getNotificationsEnabled(): State<Boolean> = _disabled
actual fun setNotificationsEnabled(value: Boolean) { }

actual fun getNotificationPrivacyEnabled(): State<Boolean> = _disabled
actual fun setNotificationPrivacyEnabled(value: Boolean) { }

actual fun openNotificationSettings() { }

