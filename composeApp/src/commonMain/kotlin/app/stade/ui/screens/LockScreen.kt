package app.stade.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.stade.AppContainer
import app.stade.ui.components.BrandMark
import kotlinx.coroutines.delay

private const val PIN_MIN = 4
private const val PIN_MAX = 8

@Composable
fun LockScreen(container: AppContainer, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var shake by remember { mutableStateOf(false) }

    LaunchedEffect(error) {
        if (error != null) {
            shake = true
            delay(400)
            shake = false
        }
    }

    LaunchedEffect(pin) {
        if (pin.length >= PIN_MIN && error == null) {
            if (container.secrets.verifyPin(pin)) {
                onUnlocked()
            } else if (pin.length >= PIN_MAX) {
                error = "PIN hatalı"
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
            PinDots(filled = pin.length, total = PIN_MAX, shake = shake, error = error != null)
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
                onDigit = { d -> if (pin.length < PIN_MAX && error == null) pin += d },
                onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) }
            )
        }
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
    var shake by remember { mutableStateOf(false) }

    LaunchedEffect(error) {
        if (error != null) {
            shake = true
            delay(400)
            shake = false
        }
    }

    val currentInput = when (phase) {
        Phase.Current -> currentPin
        Phase.New -> newPin
        Phase.Confirm -> confirmPin
    }

    LaunchedEffect(currentPin, newPin, confirmPin, phase) {
        when (phase) {
            Phase.Current -> {
                if (currentPin.length >= PIN_MAX) {
                    if (container.secrets.verifyPin(currentPin)) {
                        phase = Phase.New
                        currentPin = ""
                        error = null
                    } else {
                        error = "Mevcut PIN hatalı"
                        delay(700)
                        currentPin = ""
                        error = null
                    }
                }
            }
            Phase.New -> {
                if (newPin.length in PIN_MIN..PIN_MAX) {
                }
            }
            Phase.Confirm -> {
                if (confirmPin.length == newPin.length) {
                    if (confirmPin == newPin) {
                        container.secrets.setupPin(newPin)
                        onDone()
                    } else {
                        error = "PIN'ler eşleşmiyor"
                        delay(700)
                        confirmPin = ""
                        error = null
                    }
                }
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
        Phase.New -> "$PIN_MIN ila $PIN_MAX hane. \"Onayla\" tuşuyla bir sonraki adıma geç."
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
            PinDots(filled = currentInput.length, total = PIN_MAX, shake = shake, error = error != null)
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
                        Phase.Current -> if (currentPin.length < PIN_MAX && error == null) currentPin += d
                        Phase.New -> if (newPin.length < PIN_MAX) newPin += d
                        Phase.Confirm -> if (confirmPin.length < PIN_MAX && error == null) confirmPin += d
                    }
                },
                onBackspace = {
                    when (phase) {
                        Phase.Current -> if (currentPin.isNotEmpty()) currentPin = currentPin.dropLast(1)
                        Phase.New -> if (newPin.isNotEmpty()) newPin = newPin.dropLast(1)
                        Phase.Confirm -> if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                    }
                },
                actionLabel = when (phase) {
                    Phase.New -> if (newPin.length >= PIN_MIN) "Onayla" else null
                    else -> null
                },
                onAction = {
                    if (phase == Phase.New && newPin.length >= PIN_MIN) {
                        phase = Phase.Confirm
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
private fun PinDots(filled: Int, total: Int, shake: Boolean, error: Boolean) {
    val offsetX by animateDpAsState(
        targetValue = if (shake) 6.dp else 0.dp,
        animationSpec = tween(durationMillis = 80),
        label = "shake"
    )
    val visible = (filled.coerceAtMost(total)).coerceAtLeast(PIN_MIN)
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(start = offsetX)
    ) {
        repeat(visible) { i ->
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
    }
}

@Composable
private fun PinKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    cancelLabel: String? = null,
    onCancel: () -> Unit = {}
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9")
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
            if (actionLabel != null) {
                KeyButton(label = actionLabel, isAction = true) { onAction() }
            } else if (cancelLabel != null) {
                KeyButton(label = cancelLabel, isAction = false, small = true) { onCancel() }
            } else {
                Spacer(Modifier.width(72.dp).height(72.dp))
            }
            KeyButton(label = "0") { onDigit("0") }
            KeyButton(icon = true) { onBackspace() }
        }
    }
}

@Composable
private fun KeyButton(
    label: String = "",
    icon: Boolean = false,
    isAction: Boolean = false,
    small: Boolean = false,
    onClick: () -> Unit
) {
    val bg = when {
        isAction -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val fg = when {
        isAction -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        color = bg,
        shape = CircleShape
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (icon) {
                Icon(
                    Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Sil",
                    tint = fg
                )
            } else {
                Text(
                    label,
                    style = if (small) MaterialTheme.typography.labelLarge
                            else MaterialTheme.typography.headlineSmall,
                    color = fg
                )
            }
        }
    }
}
