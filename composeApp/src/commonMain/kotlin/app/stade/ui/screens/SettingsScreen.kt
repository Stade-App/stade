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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import app.stade.notification.getNotificationPrivacyEnabled
import app.stade.notification.getNotificationsEnabled
import app.stade.notification.isNotificationSupported
import app.stade.notification.openNotificationSettings
import app.stade.notification.setNotificationPrivacyEnabled
import app.stade.notification.setNotificationsEnabled
import app.stade.ui.components.Avatar
import app.stade.ui.components.PlatformVerticalScrollbar
import app.stade.ui.components.StadeIdCard
import app.stade.ui.theme.getDynamicColorEnabled
import app.stade.ui.theme.isDynamicColorSupported
import app.stade.ui.theme.setDynamicColorEnabled
import app.stade.ui.i18n.AppLocale
import app.stade.ui.i18n.LocalStrings
import app.stade.ui.i18n.getLocalePreference
import app.stade.ui.i18n.setLocalePreference
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    owner: LocalIdentity,
    onBack: () -> Unit,
    onOpenTransports: () -> Unit,
    onOpenSecurity: () -> Unit = {},
    onLogout: () -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    val strings = LocalStrings.current
    val fingerprint = remember(owner.id) { container.fingerprint.fingerprint(owner.publicSigningKey) }
    val dynamicColorEnabled by getDynamicColorEnabled()
    val notificationsEnabled by getNotificationsEnabled()
    val notificationPrivacyEnabled by getNotificationPrivacyEnabled()
    var showLogoutDialog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    var fingerprintCopied by remember { mutableStateOf(false) }
    val currentLocale by getLocalePreference()
    var showLanguageMenu by remember { mutableStateOf(false) }

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
            title = { Text(strings.logoutDialogTitle) },
            text = {
                Text(
                    strings.logoutDialogBody,
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
                ) { Text(strings.deleteAndLogout) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.settingsTitle) },
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
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {

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

            item {
                SettingsSectionLabel(strings.identitySection)
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    StadeIdCard(stadeId = owner.stadeId)
                }
            }

            if (isDynamicColorSupported) {
                item {
                    SettingsSectionLabel(strings.appearanceSection)
                    SettingsGroup {
                        SwitchSettingsRow(
                            icon = Icons.Default.Palette,
                            iconTint = MaterialTheme.colorScheme.tertiary,
                            title = strings.dynamicColorTitle,
                            subtitle = strings.dynamicColorSubtitle,
                            checked = dynamicColorEnabled,
                            onCheckedChange = { setDynamicColorEnabled(it) }
                        )
                    }
                }
            }

            item {
                SettingsSectionLabel(strings.languageSection)
                SettingsGroup {
                    Box {
                        NavigationSettingsRow(
                            icon = Icons.Default.Grid3x3,
                            iconTint = MaterialTheme.colorScheme.tertiary,
                            title = strings.languageTitle,
                            subtitle = strings.languageSubtitle,
                            onClick = { showLanguageMenu = true }
                        )
                        DropdownMenu(
                            expanded = showLanguageMenu,
                            onDismissRequest = { showLanguageMenu = false }
                        ) {
                            AppLocale.entries.forEach { locale ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when (locale) {
                                                AppLocale.English -> "English"
                                                AppLocale.Turkish -> "Türkçe"
                                            }
                                        )
                                    },
                                    trailingIcon = {
                                        if (locale == currentLocale) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    },
                                    onClick = {
                                        setLocalePreference(locale)
                                        showLanguageMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (isNotificationSupported) {
                item {
                    SettingsSectionLabel(strings.notificationsSection)
                    val bgColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    val shapeTop = RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = 4.dp, bottomEnd = 4.dp
                    )
                    val shapeMid = RoundedCornerShape(4.dp)
                    val shapeBot = RoundedCornerShape(
                        topStart = 4.dp, topEnd = 4.dp,
                        bottomStart = 16.dp, bottomEnd = 16.dp
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        // ── Bildirimler toggle — her zaman en üstte ──────
                        SwitchSettingsRow(
                            icon = if (notificationsEnabled) Icons.Default.Notifications
                                   else Icons.Default.NotificationsOff,
                            iconTint = if (notificationsEnabled) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                            title = strings.messageNotificationsTitle,
                            subtitle = if (notificationsEnabled) strings.notificationsOnSubtitle
                                       else strings.notificationsOffSubtitle,
                            checked = notificationsEnabled,
                            onCheckedChange = { setNotificationsEnabled(it) },
                            modifier = Modifier
                                .padding(bottom = 2.dp)
                                .background(color = bgColor, shape = shapeTop)
                                .clip(shapeTop)
                        )
                        // ── Gizlilik toggle — yalnızca bildirimler açıkken ──
                        if (notificationsEnabled) {
                            SwitchSettingsRow(
                                icon = Icons.Default.VisibilityOff,
                                iconTint = MaterialTheme.colorScheme.tertiary,
                                title = strings.hideNotificationTitle,
                                subtitle = if (notificationPrivacyEnabled)
                                    strings.hiddenNotificationSubtitle
                                else
                                    strings.visibleNotificationSubtitle,
                                checked = notificationPrivacyEnabled,
                                onCheckedChange = { setNotificationPrivacyEnabled(it) },
                                modifier = Modifier
                                    .padding(bottom = 2.dp)
                                    .background(color = bgColor, shape = shapeMid)
                                    .clip(shapeMid)
                            )
                        }
                        // ── Sistem bildirimleri — her zaman en altta ──────
                        NavigationSettingsRow(
                            icon = Icons.Default.OpenInNew,
                            iconTint = MaterialTheme.colorScheme.secondary,
                            title = strings.systemNotificationsTitle,
                            subtitle = strings.systemNotificationsSubtitle,
                            onClick = { openNotificationSettings() },
                            modifier = Modifier
                                .background(color = bgColor, shape = shapeBot)
                                .clip(shapeBot)
                        )
                    }
                }
            }

            item {
                SettingsSectionLabel(strings.networkSection)
                SettingsGroup {
                    NavigationSettingsRow(
                        icon = Icons.Default.SettingsEthernet,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        title = strings.transportLayersTitle,
                        subtitle = strings.transportLayersSubtitle,
                        onClick = onOpenTransports
                    )
                }
            }

            item {
                SettingsSectionLabel(strings.securitySection)
                SettingsGroup {
                    NavigationSettingsRow(
                        icon = Icons.Default.Lock,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = strings.securitySettingsTitle,
                        subtitle = strings.securitySettingsSubtitle,
                        onClick = onOpenSecurity
                    )
                }
            }

            item {
                SettingsSectionLabel(strings.accountSection)
                SettingsGroup {
                    ActionSettingsRow(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        iconTint = MaterialTheme.colorScheme.error,
                        title = strings.logoutTitle,
                        subtitle = strings.logoutSubtitle,
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = { showLogoutDialog = true }
                    )
                }
            }
            }
            PlatformVerticalScrollbar(
                state = listState,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
            )
        }
    }
}


@Composable
private fun ProfileHeader(
    owner: LocalIdentity,
    fingerprint: String,
    copied: Boolean,
    onCopyFingerprint: () -> Unit
) {
    val strings = LocalStrings.current
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
            Avatar(name = owner.nickname, size = 84.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                owner.nickname,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(2.dp))
            Text(
                strings.localIdentity,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f)
            )
            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .clickable { onCopyFingerprint() },
                shape = cardShape,
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
                            if (copied) strings.fingerprintCopied else strings.fingerprintLabel,
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
                        contentDescription = strings.copyButton,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}


@Composable
private fun SettingsSectionLabel(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 6.dp, end = 16.dp)
    )
}


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


@Composable
private fun SwitchSettingsRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconBox(icon = icon, tint = iconTint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}


@Composable
private fun NavigationSettingsRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconBox(icon = icon, tint = iconTint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


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
