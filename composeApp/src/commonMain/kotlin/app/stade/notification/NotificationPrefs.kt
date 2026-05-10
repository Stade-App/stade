package app.stade.notification

import androidx.compose.runtime.State

/** Platformun mesaj bildirimi destekleyip desteklemediği. */
expect val isNotificationSupported: Boolean

/** Kullanıcının mesaj bildirimi tercihini reaktif olarak döner. */
expect fun getNotificationsEnabled(): State<Boolean>

/** Mesaj bildirimi tercihini kalıcı olarak kaydeder. */
expect fun setNotificationsEnabled(value: Boolean)

/**
 * Bildirim gizliliği: `true` olduğunda gönderici adı / mesaj içeriği gösterilmez,
 * bunun yerine "X yeni mesajınız var" tarzı genel bir bildirim gösterilir.
 */
expect fun getNotificationPrivacyEnabled(): State<Boolean>

/** Bildirim gizliliği tercihini kalıcı olarak kaydeder. */
expect fun setNotificationPrivacyEnabled(value: Boolean)

/** Sistemin bildirim ayarları ekranını açar (desteklenmeyen platformda no-op). */
expect fun openNotificationSettings()

/**
 * Belirli bir kişiye ait mesaj bildirimini (varsa) hemen iptal eder.
 * ChatScreen açıldığında çağrılır; böylece zaten görülen bildirimler temizlenir.
 */
expect fun cancelMessagesNotification(contactId: String)

/**
 * Tüm aktif mesaj/gizlilik bildirimlerini temizler. Uygulama ön plana
 * döndüğünde veya bir sohbet ekranı açıldığında çağrılır.
 */
expect fun clearAllMessageNotifications()

