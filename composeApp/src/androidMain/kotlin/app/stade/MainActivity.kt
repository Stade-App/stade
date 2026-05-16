package app.stade

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import app.stade.notification.clearAllMessageNotifications
import app.stade.service.StadeService
import app.stade.ui.StadeApp

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

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
        handleIncomingInvite(intent)
        setContent { StadeApp(app.boot) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingInvite(intent)
    }

    override fun onResume() {
        super.onResume()
        // Ayar değişmiş olabilir; her öne geliş anında yeniden uygula
        val app = (application as StadeApplication)
        applySecureScreenFlag(app)
        clearAllMessageNotifications()
    }

    private fun applySecureScreenFlag(app: StadeApplication) {
        val enabled = app.container?.secrets?.isScreenshotBlockingEnabled() ?: false
        // Mevcut durum ile istenen durum aynıysa hiçbir şey yapma.
        // clearFlags/setFlags çağrısı, arka plandan öne geçiş animasyonu sırasında
        // pencere yüzeyini geçici olarak temizleyebilir ve gri ekrana yol açabilir.
        val hasSecure = (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        when {
            enabled && !hasSecure -> window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
            !enabled && hasSecure -> window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            // Zaten doğru durumda → dokunma
        }
    }

    private fun handleIncomingInvite(intent: Intent?) {
        if (intent == null) return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            }
            else -> null
        }
        val app = (application as StadeApplication)
        val text = when {
            uri != null -> runCatching {
                contentResolver.openInputStream(uri)?.use { stream ->
                    // Güvenlik: dış kaynaktan (ACTION_VIEW / ACTION_SEND, mimeType=*/*) gelen
                    // dosyayı sınırsız okumak OOM/DoS'a yol açar. Davet kodları base32
                    // olduğundan birkaç MB'tan büyük olamaz; sert üst sınır uyguluyoruz.
                    val limit = MAX_INVITE_BYTES
                    val buffer = ByteArray(8 * 1024)
                    val out = java.io.ByteArrayOutputStream()
                    var total = 0
                    while (true) {
                        val n = stream.read(buffer)
                        if (n <= 0) break
                        total += n
                        if (total > limit) return@use null
                        out.write(buffer, 0, n)
                    }
                    out.toString(Charsets.UTF_8.name())
                }
            }.getOrNull()
            intent.action == Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }?.trim()?.takeIf { it.isNotEmpty() }
        if (text != null && text.length <= MAX_INVITE_CHARS && text.startsWith("STADE2-")) {
            app.container?.pendingInvite?.value = text
        }
    }

    private companion object {
        // Davet kodu maksimum boyutu: ML-DSA imza + ML-KEM açık anahtar + Ed25519 + adresler
        // tipik olarak ~7-8 KB; emniyet için cömert bir tavan.
        const val MAX_INVITE_BYTES = 256 * 1024
        const val MAX_INVITE_CHARS = 512 * 1024
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
