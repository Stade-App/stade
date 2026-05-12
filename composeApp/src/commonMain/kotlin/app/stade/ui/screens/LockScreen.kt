package app.stade.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.stade.AppContainer
import app.stade.ui.components.BrandMark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PIN_MIN = 4
private const val PIN_MAX = 16
private const val PIN_DOTS_MAX = 8

// Hatalı PIN titreme animasyonu — Animatable ile doğrudan çalıştırılır, state flip yok.
private suspend fun Animatable<Float, *>.shake() {
    animateTo(0f, keyframes {
        durationMillis = 420
        0f   at 0
        -12f at 55
        12f  at 110
        -10f at 165
        10f  at 220
        -6f  at 275
        6f   at 330
        0f   at 420
    })
}

@Composable
fun LockScreen(container: AppContainer, onUnlocked: () -> Unit, onForgotPin: () -> Unit = {}) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showForgotDialog by remember { mutableStateOf(false) }
    var wiping by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    val shakeOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val scrambleEnabled = remember { container.secrets.isScrambleKeypadEnabled() }

    fun tryUnlock() {
        if (pin.length < PIN_MIN || error != null || isVerifying) return
        val currentPin = pin
        isVerifying = true
        scope.launch {
            val correct = withContext(Dispatchers.Default) {
                container.secrets.verifyPin(currentPin)
            }
            isVerifying = false
            if (correct) {
                onUnlocked()
            } else {
                error = "PIN hatalı"
                launch { shakeOffset.shake() }   // titreme — ana akışı bloke etmez
                delay(700)
                pin = ""
                error = null
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandMark(size = 72.dp)
            Spacer(Modifier.height(16.dp))
            Text("Kilidi aç", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "Devam etmek için PIN'ini gir.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))

            // Doğrulama süresince spinner; aksi halde noktalar
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
            Box(modifier = Modifier.height(20.dp)) {
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            PinKeypad(
                onDigit = { d ->
                    if (pin.length < PIN_MAX && error == null && !isVerifying) {
                        pin += d
                        if (pin.length >= PIN_MAX) tryUnlock()
                    }
                },
                onBackspace = {
                    if (pin.isNotEmpty() && !isVerifying) pin = pin.dropLast(1)
                },
                actionIsCheck = pin.length >= PIN_MIN && error == null && !isVerifying,
                onAction = { tryUnlock() },
                scrambled = scrambleEnabled
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { showForgotDialog = true }, enabled = !wiping) {
                Text("Şifremi unuttum")
            }
        }
    }

    if (showForgotDialog) {
        AlertDialog(
            onDismissRequest = { if (!wiping) showForgotDialog = false },
            icon = {
                Icon(
                    Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("PIN'i sıfırla") },
            text = {
                Text(
                    "PIN'in cihazından kurtarılamaz. Devam edersen tüm yerel veriler " +
                        "(kimliğin, kişilerin, sohbet geçmişin ve taşıma ayarların) kalıcı " +
                        "olarak silinir ve uygulama sıfırlanır.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (wiping) return@Button
                        wiping = true
                        scope.launch {
                            container.wipeAllData()
                            showForgotDialog = false
                            wiping = false
                            onForgotPin()
                        }
                    },
                    enabled = !wiping,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text(if (wiping) "Siliniyor…" else "Sıfırla ve sil") }
            },
            dismissButton = {
                TextButton(onClick = { showForgotDialog = false }, enabled = !wiping) {
                    Text("Vazgeç")
                }
            }
        )
    }
}

@Composable
fun PinSetupScreen(
    container: AppContainer,
    title: String = "PIN belirle",
    requireCurrent: Boolean = false,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    var phase by remember { mutableStateOf(if (requireCurrent) Phase.Current else Phase.New) }
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    val shakeOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val currentInput = when (phase) {
        Phase.Current -> currentPin
        Phase.New -> newPin
        Phase.Confirm -> confirmPin
    }

    fun tryVerifyCurrent() {
        if (currentPin.length < PIN_MIN || error != null || isVerifying) return
        val snap = currentPin
        isVerifying = true
        scope.launch {
            val correct = withContext(Dispatchers.Default) {
                container.secrets.verifyPin(snap)
            }
            isVerifying = false
            if (correct) {
                phase = Phase.New
                currentPin = ""
                error = null
            } else {
                error = "Mevcut PIN hatalı"
                launch { shakeOffset.shake() }
                delay(700)
                currentPin = ""
                error = null
            }
        }
    }

    LaunchedEffect(confirmPin, phase) {
        if (phase == Phase.Confirm && confirmPin.length == newPin.length) {
            if (confirmPin == newPin) {
                withContext(Dispatchers.Default) { container.secrets.setupPin(newPin) }
                onDone()
            } else {
                error = "PIN'ler eşleşmiyor"
                launch { shakeOffset.shake() }
                delay(700)
                confirmPin = ""
                error = null
            }
        }
    }

    val heading = when (phase) {
        Phase.Current -> "Mevcut PIN'i gir"
        Phase.New -> "Yeni PIN belirle"
        Phase.Confirm -> "PIN'i doğrula"
    }
    val sub = when (phase) {
        Phase.Current -> "Devam etmek için mevcut PIN'ini gir."
        Phase.New -> "4-16 haneli bir PIN belirle."
        Phase.Confirm -> "Aynı PIN'i tekrar gir."
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandMark(size = 64.dp)
            Spacer(Modifier.height(14.dp))
            Text(heading, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                sub,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 360.dp)
            )
            Spacer(Modifier.height(24.dp))
            Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    PinDots(
                        filled = currentInput.length,
                        shakeOffset = shakeOffset.value,
                        error = error != null
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(modifier = Modifier.height(20.dp)) {
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            PinKeypad(
                onDigit = { d ->
                    when (phase) {
                        Phase.Current -> if (currentPin.length < PIN_MAX && error == null && !isVerifying) {
                            currentPin += d
                            if (currentPin.length >= PIN_MAX) tryVerifyCurrent()
                        }
                        Phase.New -> if (newPin.length < PIN_MAX) {
                            newPin += d
                            if (newPin.length >= PIN_MAX) phase = Phase.Confirm
                        }
                        Phase.Confirm -> if (confirmPin.length < newPin.length && error == null) confirmPin += d
                    }
                },
                onBackspace = {
                    when (phase) {
                        Phase.Current -> if (currentPin.isNotEmpty() && !isVerifying) currentPin = currentPin.dropLast(1)
                        Phase.New -> if (newPin.isNotEmpty()) newPin = newPin.dropLast(1)
                        Phase.Confirm -> if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                    }
                },
                actionLabel = null,
                actionIsCheck = when (phase) {
                    Phase.Current -> currentPin.length >= PIN_MIN && error == null && !isVerifying
                    Phase.New -> newPin.length >= PIN_MIN
                    Phase.Confirm -> false
                },
                onAction = {
                    when (phase) {
                        Phase.Current -> tryVerifyCurrent()
                        Phase.New -> if (newPin.length >= PIN_MIN) phase = Phase.Confirm
                        Phase.Confirm -> {}
                    }
                },
                cancelLabel = "İptal",
                onCancel = onCancel
            )
        }
    }
}

private enum class Phase { Current, New, Confirm }

@Composable
private fun PinDots(filled: Int, shakeOffset: Float, error: Boolean) {
    val visibleDots = filled.coerceIn(PIN_MIN, PIN_DOTS_MAX)
    val overflow = (filled - PIN_DOTS_MAX).coerceAtLeast(0)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.graphicsLayer { translationX = shakeOffset }
    ) {
        repeat(visibleDots) { i ->
            val isFilled = i < filled
            val color = when {
                error -> MaterialTheme.colorScheme.error
                isFilled -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Box(
                modifier = Modifier
                    .size(if (isFilled) 16.dp else 14.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        if (overflow > 0) {
            Text(
                "+$overflow",
                style = MaterialTheme.typography.labelMedium,
                color = if (error) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun PinKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    actionLabel: String? = null,
    actionIsCheck: Boolean = false,
    onAction: () -> Unit = {},
    cancelLabel: String? = null,
    onCancel: () -> Unit = {},
    scrambled: Boolean = false
) {
    val digits: List<String> = remember(scrambled) {
        val all = (1..9).map { it.toString() } + "0"
        if (scrambled) all.shuffled() else all
    }
    val topDigits = digits.take(9)
    val bottomMid = digits[9]
    val rows = listOf(
        topDigits.subList(0, 3),
        topDigits.subList(3, 6),
        topDigits.subList(6, 9)
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { d -> KeyButton(label = d) { onDigit(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            when {
                actionIsCheck -> KeyButton(checkIcon = true, isAction = true) { onAction() }
                actionLabel != null -> KeyButton(label = actionLabel, isAction = true) { onAction() }
                cancelLabel != null -> KeyButton(label = cancelLabel, isAction = false, small = true) { onCancel() }
                else -> Spacer(Modifier.width(72.dp).height(72.dp))
            }
            KeyButton(label = bottomMid) { onDigit(bottomMid) }
            KeyButton(icon = true) { onBackspace() }
        }
    }
}

@Composable
private fun KeyButton(
    label: String = "",
    icon: Boolean = false,
    checkIcon: Boolean = false,
    isAction: Boolean = false,
    small: Boolean = false,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val bg = if (isAction) MaterialTheme.colorScheme.primary
             else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (isAction) MaterialTheme.colorScheme.onPrimary
             else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        color = bg,
        shape = CircleShape
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            when {
                checkIcon -> Icon(Icons.Filled.Check, contentDescription = "Onayla", tint = fg)
                icon -> Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Sil", tint = fg)
                else -> Text(
                    label,
                    style = if (small) MaterialTheme.typography.labelLarge
                            else MaterialTheme.typography.headlineSmall,
                    color = fg
                )
            }
        }
    }
}
