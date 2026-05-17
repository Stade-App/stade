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
import androidx.lifecycle.lifecycleScope
import app.stade.notification.clearAllMessageNotifications
import app.stade.service.StadeService
import app.stade.ui.StadeApp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        val app = (application as StadeApplication)
        if (app.vault.isScreenshotBlockingEnabled()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)
        startForegroundService(Intent(this, StadeService::class.java))
        askNotificationPermissionIfNeeded()
        handleIncomingInvite(intent)
        setContent { StadeApp(app.boot) }

        // Ekran görüntüsü engelleme ayarı değiştiğinde (SecuritySettingsScreen'den)
        // onResume'u beklemeden FLAG_SECURE'ü anında uygula.
        lifecycleScope.launch {
            app.containerFlow.collectLatest { container ->
                container?.screenshotSettingTick?.collect {
                    applySecureScreenFlag(app)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingInvite(intent)
    }

    override fun onResume() {
        super.onResume()
        // Ayar değişmiş olabilir; her öne geliş anında yeniden uygula.
        val app = (application as StadeApplication)
        applySecureScreenFlag(app)
        clearAllMessageNotifications()
    }

    /**
     * Uygulama arka plandan ön plana döndüğünde pencere odağı yeniden kazanılır.
     * FLAG_SECURE etkinken Android, arka plan geçişinde pencere yüzeyini serbest bırakabilir;
     * odak geri geldiğinde Compose'u yeniden çizmeye zorlayarak gri ekranı önlüyoruz.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.invalidate()
        }
    }

    private fun applySecureScreenFlag(app: StadeApplication) {
        // Vault her zaman StadeApplication.onCreate'de başlatılır; container'ın
        // (null olabileceği soğuk başlatma dahil) hazır olmasını beklemeden
        // doğrudan vault'tan okuruz. readMeta şifreli dosyada çalışır,
        // PIN kilidi açma gerektirmez.
        val enabled = app.vault.isScreenshotBlockingEnabled()
        val hasSecure = (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        when {
            enabled && !hasSecure -> {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                // Yeni flag sonrası yüzeyi zorla yenile
                window.decorView.invalidate()
            }
            !enabled && hasSecure -> {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                window.decorView.invalidate()
            }
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
