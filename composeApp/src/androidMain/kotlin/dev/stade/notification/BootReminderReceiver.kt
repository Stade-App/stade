package dev.stade.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dev.stade.MainActivity
import dev.stade.ui.i18n.I18n
import dev.stade.ui.i18n.getLocalePreference
import dev.stade.ui.i18n.localeToStrings

/**
 * Sistem açılışında sadece bir hatırlatma bildirimi gösterir; StadeService'i BAŞLATMAZ.
 * Kullanıcı bildirime dokunup uygulamayı açana kadar mesaj alma/arka plan bağlantısı başlamaz.
 */
class BootReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        I18n.current = localeToStrings(getLocalePreference().value)
        val strings = I18n.current

        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(REMINDER_CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(REMINDER_CHANNEL_ID, strings.notifReminderChannelName, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = strings.notifReminderChannelDesc
                }
            )
        }

        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_GO_HOME, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(strings.notifBootReminderTitle)
            .setContentText(strings.notifBootReminderText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        mgr.notify(NotificationIds.BOOT_REMINDER, notif)
    }

    companion object {
        const val REMINDER_CHANNEL_ID = "stade.reminder"
    }
}
