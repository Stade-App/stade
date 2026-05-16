package app.stade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.stade.AppContainer
import app.stade.security.SessionTimeout
import app.stade.ui.components.PlatformVerticalScrollbar
import app.stade.ui.i18n.LocalStrings


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenPinSetup: (requireCurrent: Boolean) -> Unit
) {
    val strings = LocalStrings.current
    var refreshTick by remember { mutableStateOf(0) }
    val scrambleEnabled = remember(refreshTick) { container.secrets.isScrambleKeypadEnabled() }
    val sessionTimeout = remember(refreshTick) { container.secrets.sessionTimeoutSeconds() }
    val screenshotBlockingEnabled = remember(refreshTick) { container.secrets.isScreenshotBlockingEnabled() }
    var timeoutMenuOpen by remember { mutableStateOf(false) }
    var showNeverInfoDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.securitySettingsTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
            ) {
                item {
                    SecuritySectionLabel(strings.pinSection)
                    SecurityGroup {
                        SecurityNavRow(
                            icon = Icons.Default.Fingerprint,
                            tint = MaterialTheme.colorScheme.primary,
                            title = strings.changePinTitle,
                            subtitle = strings.changePinSubtitle,
                            onClick = { onOpenPinSetup(true) },
                            modifier = Modifier
                                .padding(bottom = 2.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                                )
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                        )
                        SecuritySwitchRow(
                            icon = Icons.Default.Grid3x3,
                            tint = MaterialTheme.colorScheme.tertiary,
                            title = strings.scrambleKeypadTitle,
                            subtitle = if (scrambleEnabled) strings.scrambleKeypadOnSubtitle else strings.scrambleKeypadOffSubtitle,
                            checked = scrambleEnabled,
                            onCheckedChange = {
                                container.secrets.setScrambleKeypadEnabled(it)
                                refreshTick++
                            },
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                                )
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                        )
                    }
                }

                // Gizlilik bölümü yalnızca desteklenen platformlarda gösterilir (Android).
                // Masaüstünde FLAG_SECURE ve benzeri özellikler desteklenmediğinden gizlenir.
                // İleride masaüstüne özgü gizlilik ayarları eklendikçe PrivacyFeatures.kt
                // güncellenerek bu blok otomatik olarak görünür hale gelir.
                if (isScreenPrivacySupported) {
                    item {
                        SecuritySectionLabel(strings.privacySection)
                        SecurityGroup {
                            SecuritySwitchRow(
                                icon = Icons.Default.VisibilityOff,
                                tint = MaterialTheme.colorScheme.secondary,
                                title = strings.screenshotBlockingTitle,
                                subtitle = if (screenshotBlockingEnabled) strings.screenshotBlockingOnSubtitle else strings.screenshotBlockingOffSubtitle,
                                checked = screenshotBlockingEnabled,
                                onCheckedChange = {
                                    container.secrets.setScreenshotBlockingEnabled(it)
                                    refreshTick++
                                },
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = MaterialTheme.shapes.large
                                    )
                                    .clip(MaterialTheme.shapes.large)
                            )
                        }
                    }
                }

                item {
                    SecuritySectionLabel(strings.sessionSection)
                    SecurityGroup {
                        Box {
                            SecurityNavRow(
                                icon = Icons.Default.Timer,
                                tint = MaterialTheme.colorScheme.secondary,
                                title = strings.autoLockTitle,
                                subtitle = strings.autoLockSubtitle(strings.sessionTimeoutLabel(sessionTimeout)),
                                onClick = { timeoutMenuOpen = true },
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = MaterialTheme.shapes.large
                                    )
                                    .clip(MaterialTheme.shapes.large),
                                trailingContent = {
                                    IconButton(onClick = { showNeverInfoDialog = true }) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = strings.autoLockNeverInfoTitle,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            )
                            DropdownMenu(
                                expanded = timeoutMenuOpen,
                                onDismissRequest = { timeoutMenuOpen = false }
                            ) {
                                SessionTimeout.OPTIONS.forEach { opt ->
                                    DropdownMenuItem(
                                        text = { Text(strings.sessionTimeoutLabel(opt)) },
                                        trailingIcon = {
                                            if (opt == sessionTimeout) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                            }
                                        },
                                        onClick = {
                                            container.secrets.setSessionTimeoutSeconds(opt)
                                            timeoutMenuOpen = false
                                            refreshTick++
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showNeverInfoDialog) {
                AlertDialog(
                    onDismissRequest = { showNeverInfoDialog = false },
                    icon = {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    title = { Text(strings.autoLockNeverInfoTitle) },
                    text = {
                        Text(
                            strings.autoLockNeverInfoBody,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showNeverInfoDialog = false }) {
                            Text(strings.understood)
                        }
                    }
                )
            }
            PlatformVerticalScrollbar(
                state = listState,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun SecuritySectionLabel(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 6.dp, end = 16.dp)
    )
}

// Eski SecurityGroup fonksiyonunu tamamen bununla değiştir:
@Composable
private fun SecurityGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        content()
    }
}

@Composable
private fun SecurityNavRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SecurityIconBox(icon, tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailingContent != null) {
            trailingContent()
        }
    }
}

@Composable
private fun SecuritySwitchRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        // Düzenlenen kısım: Aynı zincirleme mantığı Switch için de geçerli
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SecurityIconBox(icon, tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}


@Composable
private fun SecurityIconBox(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}
