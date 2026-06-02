package app.stade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.stade.AppContainer
import app.stade.security.SessionTimeout
import app.stade.ui.components.PlatformVerticalScrollbar
import app.stade.ui.i18n.LocalStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenPinSetup: (requireCurrent: Boolean) -> Unit
) {
    val lockEnabled = remember { container.secrets.isLockEnabled() }
    var pinVerified by remember { mutableStateOf(!lockEnabled) }

    if (!pinVerified) {
        SecurityPinGate(
            container = container,
            onVerified = { pinVerified = true },
            onBack = onBack
        )
        return
    }

    SecuritySettingsContent(
        container = container,
        onBack = onBack,
        onOpenPinSetup = onOpenPinSetup
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecuritySettingsContent(
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
                        val changePinShape = if (isKeypadSupported) {
                            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                        } else {
                            MaterialTheme.shapes.large
                        }
                        SecurityNavRow(
                            icon = Icons.Default.Fingerprint,
                            tint = MaterialTheme.colorScheme.primary,
                            title = strings.changePinTitle,
                            subtitle = strings.changePinSubtitle,
                            onClick = { onOpenPinSetup(true) },
                            modifier = Modifier
                                .then(if (isKeypadSupported) Modifier.padding(bottom = 2.dp) else Modifier)
                                .background(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = changePinShape)
                                .clip(changePinShape)
                        )
                        if (isKeypadSupported) {
                            val scrambleShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
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
                                    .background(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = scrambleShape)
                                    .clip(scrambleShape)
                            )
                        }
                    }
                }

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
            .then(modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(
                    start = 16.dp,
                    top = 14.dp,
                    bottom = 14.dp,
                    end = if (trailingContent != null) 4.dp else 16.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SecurityIconBox(icon, tint)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (trailingContent != null) {
            Box(
                modifier = Modifier.padding(end = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                trailingContent()
            }
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecurityPinGate(
    container: AppContainer,
    onVerified: () -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    val shakeOffset = remember { androidx.compose.animation.core.Animatable(0f) }
    val keyFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { keyFocusRequester.requestFocus() }
    }

    fun tryVerify() {
        if (pin.length < 4 || isVerifying || error != null) return
        val snap = pin
        isVerifying = true
        scope.launch {
            val ok = withContext(Dispatchers.Default) { container.secrets.verifyPin(snap) }
            isVerifying = false
            if (ok) {
                onVerified()
            } else {
                error = strings.wrongCurrentPin
                launch {
                    shakeOffset.animateTo(0f, androidx.compose.animation.core.keyframes {
                        durationMillis = 420
                        0f at 0
                        -12f at 55
                        12f at 110
                        -10f at 165
                        10f at 220
                        -6f at 275
                        6f at 330
                        0f at 420
                    })
                }
                delay(700)
                pin = ""
                error = null
            }
        }
    }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .focusRequester(keyFocusRequester)
                .focusable()
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        val digit = when (keyEvent.key) {
                            Key.Zero, Key.NumPad0 -> "0"
                            Key.One, Key.NumPad1 -> "1"
                            Key.Two, Key.NumPad2 -> "2"
                            Key.Three, Key.NumPad3 -> "3"
                            Key.Four, Key.NumPad4 -> "4"
                            Key.Five, Key.NumPad5 -> "5"
                            Key.Six, Key.NumPad6 -> "6"
                            Key.Seven, Key.NumPad7 -> "7"
                            Key.Eight, Key.NumPad8 -> "8"
                            Key.Nine, Key.NumPad9 -> "9"
                            else -> null
                        }
                        when {
                            digit != null -> {
                                if (!isVerifying && pin.length < 16 && error == null) {
                                    pin += digit
                                    if (pin.length >= 16) tryVerify()
                                }
                                true
                            }
                            keyEvent.key == Key.Backspace -> {
                                if (pin.isNotEmpty() && !isVerifying) pin = pin.dropLast(1)
                                true
                            }
                            keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter -> {
                                tryVerify()
                                true
                            }
                            else -> false
                        }
                    } else false
                },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(strings.enterCurrentPinTitle, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                strings.enterCurrentPinSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    PinDots(
                        filled = pin.length,
                        shakeOffset = shakeOffset.value,
                        error = error != null
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(modifier = Modifier.height(20.dp), contentAlignment = Alignment.Center) {
                if (error != null) {
                    Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            if (isKeypadSupported) {
                val digits = (1..9).map { it.toString() }
                val rows = listOf(digits.subList(0, 3), digits.subList(3, 6), digits.subList(6, 9))
                val haptic = LocalHapticFeedback.current
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { d ->
                                GatePadButton(onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (!isVerifying && error == null && pin.length < 16) {
                                        pin += d
                                        if (pin.length >= 16) tryVerify()
                                    }
                                }) {
                                    Text(d, style = MaterialTheme.typography.headlineSmall)
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val canConfirm = pin.length >= 4 && !isVerifying && error == null
                        Surface(
                            modifier = Modifier.size(68.dp).clip(CircleShape).clickable(enabled = canConfirm) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                tryVerify()
                            },
                            color = if (canConfirm) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = CircleShape
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Default.Check, contentDescription = strings.confirmAction,
                                    tint = if (canConfirm) MaterialTheme.colorScheme.onPrimary
                                           else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        GatePadButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (!isVerifying && error == null && pin.length < 16) {
                                pin += "0"
                                if (pin.length >= 16) tryVerify()
                            }
                        }) { Text("0", style = MaterialTheme.typography.headlineSmall) }
                        GatePadButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (pin.isNotEmpty() && !isVerifying) pin = pin.dropLast(1)
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = strings.backspaceAction,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GatePadButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.size(68.dp).clip(CircleShape).clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = CircleShape
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

