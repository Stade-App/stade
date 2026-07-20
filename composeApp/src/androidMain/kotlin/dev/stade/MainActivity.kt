package dev.stade

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import dev.stade.notification.clearAllMessageNotifications
import dev.stade.service.StadeService
import dev.stade.ui.StadeApp

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** Son uygulanan FLAG_SECURE durumu; aynı değerle tekrar setFlags/clearFlags çağrısını önler. */
    private var lastSecureFlagState: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)
        val app = (application as StadeApplication)
        applySecureScreenFlag(app)
        startForegroundService(Intent(this, StadeService::class.java))
        askNotificationPermissionIfNeeded()
        setContent { StadeApp(app.boot) }
    }

    override fun onStop() {
        super.onStop()
        val app = (application as StadeApplication)
        val vault = app.container?.vault ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { vault.flushAndKeep() }
        }
    }

    override fun onResume() {
        super.onResume()
        val app = (application as StadeApplication)
        applySecureScreenFlag(app)
        clearAllMessageNotifications()
    }

    /**
     * FLAG_SECURE etkinken arka plandan dönerken pencere yüzeyi yeniden oluşturulabilir.
     * Odak geri kazanıldığında decor view'ı invalidate ederek Compose'u yeniden çizmeye
     * zorluyoruz; bu, gri ekran sorununu ortadan kaldırır.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.invalidate()
        }
    }

    private fun applySecureScreenFlag(app: StadeApplication) {
        val enabled = app.container?.secrets?.isScreenshotBlockingEnabled() ?: false
        if (enabled == lastSecureFlagState) return
        if (lastSecureFlagState == null && !enabled) {
            lastSecureFlagState = false
            return
        }
        lastSecureFlagState = enabled
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
