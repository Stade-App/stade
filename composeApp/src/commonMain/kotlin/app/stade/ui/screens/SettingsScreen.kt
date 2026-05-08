package app.stade.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.stade.AppContainer
import app.stade.identity.LocalIdentity
import app.stade.ui.components.Avatar
import app.stade.ui.components.StadeIdCard
import app.stade.ui.theme.getDynamicColorEnabled
import app.stade.ui.theme.isDynamicColorSupported
import app.stade.ui.theme.setDynamicColorEnabled
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    owner: LocalIdentity,
    onBack: () -> Unit,
    onOpenTransports: () -> Unit,
    onLogout: () -> Unit
) {
    val fingerprint = remember(owner.id) { container.fingerprint.fingerprint(owner.publicSigningKey) }
    val dynamicColorEnabled by getDynamicColorEnabled()
    var showLogoutDialog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    var fingerprintCopied by remember { mutableStateOf(false) }

    LaunchedEffect(fingerprintCopied) {
        if (fingerprintCopied) {
            delay(2000)
            fingerprintCopied = false
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Oturumu Kapat") },
            text = {
                Text(
                    "Yerel veriler korunur; bir sonraki açılışta kimliğin hazır olacak.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text("Çıkış Yap") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("İptal") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {

            // ── Profil başlığı ───────────────────────────────────
            item {
                ProfileHeader(
                    owner = owner,
                    fingerprint = fingerprint,
                    copied = fingerprintCopied,
                    onCopyFingerprint = {
                        clipboardManager.setText(AnnotatedString(fingerprint))
                        fingerprintCopied = true
                    }
                )
            }

            // ── Stade ID kartı ───────────────────────────────────
            item {
                SettingsSectionLabel("Kimlik")
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    StadeIdCard(stadeId = owner.stadeId)
                }
            }

            // ── Görünüm (yalnızca Android 12+) ──────────────────
            if (isDynamicColorSupported) {
                item {
                    SettingsSectionLabel("Görünüm")
                    SettingsGroup {
                        SwitchSettingsRow(
                            icon = Icons.Default.Palette,
                            iconTint = MaterialTheme.colorScheme.tertiary,
                            title = "Dinamik renk",
                            subtitle = "Material You duvar kağıdı renklerini kullan",
                            checked = dynamicColorEnabled,
                            onCheckedChange = { setDynamicColorEnabled(it) }
                        )
                    }
                }
            }

            // ── Ağ Bağlantısı ────────────────────────────────────
            item {
                SettingsSectionLabel("Ağ Bağlantısı")
                SettingsGroup {
                    NavigationSettingsRow(
                        icon = Icons.Default.SettingsEthernet,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        title = "Taşıma katmanları",
                        subtitle = "LAN, Tor ve diğer ağ ayarları",
                        onClick = onOpenTransports
                    )
                }
            }

            // ── Hesap ────────────────────────────────────────────
            item {
                SettingsSectionLabel("Hesap")
                SettingsGroup {
                    ActionSettingsRow(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        iconTint = MaterialTheme.colorScheme.error,
                        title = "Oturumu kapat",
                        subtitle = "Yerel veriler korunur",
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = { showLogoutDialog = true }
                    )
                }
            }
        }
    }
}

// ── Profil başlığı bileşeni ──────────────────────────────────────────────────

@Composable
private fun ProfileHeader(
    owner: LocalIdentity,
    fingerprint: String,
    copied: Boolean,
    onCopyFingerprint: () -> Unit
) {
    // 1. Şekli bir değişkene atayalım ki hem Card hem de clip için aynı olsun
    val cardShape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(top = 20.dp, bottom = 28.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Büyük avatar
            Avatar(name = owner.nickname, size = 84.dp)
            Spacer(Modifier.height(14.dp))
            // İsim
            Text(
                owner.nickname,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Yerel kimlik",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f)
            )
            Spacer(Modifier.height(16.dp))

            // 2. Card modifier'larını güncelledik
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape) // ÖNEMLİ: Önce kırp (clip)
                    .clickable { onCopyFingerprint() }, // Sonra tıklanabilir yap
                shape = cardShape, // Kartın kendi şekli
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedContent(
                        targetState = copied,
                        transitionSpec = { fadeIn() togetherWith fadeOut() }
                    ) { isCopied ->
                        Icon(
                            if (isCopied) Icons.Default.Check else Icons.Default.Fingerprint,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                alpha = if (isCopied) 1f else 0.7f
                            )
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (copied) "Kopyalandı!" else "Kimlik parmak izi",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            fingerprint,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = if (copied) 1 else 3
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Kopyala",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

// ── Bölüm başlığı ────────────────────────────────────────────────────────────

@Composable
private fun SettingsSectionLabel(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 6.dp, end = 16.dp)
    )
}

// ── Ayar grubu kartı ─────────────────────────────────────────────────────────

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        content()
    }
}

// ── Satır: Toggle / Switch ────────────────────────────────────────────────────

@Composable
private fun SwitchSettingsRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconBox(icon = icon, tint = iconTint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ── Satır: Navigasyon / Chevron ───────────────────────────────────────────────

@Composable
private fun NavigationSettingsRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconBox(icon = icon, tint = iconTint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Satır: Aksiyon (tıklanabilir, renk özelleştirilebilir) ──────────────────

@Composable
private fun ActionSettingsRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconBox(icon = icon, tint = iconTint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── İkon kutusu (yuvarlak köşeli, renkli arka plan) ──────────────────────────

@Composable
private fun SettingsIconBox(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}
