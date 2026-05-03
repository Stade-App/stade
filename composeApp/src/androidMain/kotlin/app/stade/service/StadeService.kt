package app.stade.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.stade.StadeApplication

class StadeService : Service() {
    private val channelId = "stade.connectivity"
    private val notificationId = 4242

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(notificationId, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(channelId) != null) return
        val channel = NotificationChannel(channelId, "Bağlantı", NotificationManager.IMPORTANCE_MIN).apply {
            description = "Stade eşlerle bağlantı durumunu korur"
            setShowBadge(false)
        }
        mgr.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Stade çalışıyor")
            .setContentText("Eşler arası bağlantılar etkin")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }
}
