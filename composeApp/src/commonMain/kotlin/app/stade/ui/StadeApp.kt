package app.stade.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.stade.AppContainer
import app.stade.identity.LocalIdentity
import app.stade.ui.screens.AddContactScreen
import app.stade.ui.screens.ChatScreen
import app.stade.ui.screens.ContactsScreen
import app.stade.ui.screens.LockScreen
import app.stade.ui.screens.OnboardingScreen
import app.stade.ui.screens.PinSetupScreen
import app.stade.ui.screens.SettingsScreen
import app.stade.ui.screens.TransportsScreen
import app.stade.ui.screens.VerifyContactScreen
import app.stade.ui.theme.StadeTheme
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Lock : Screen
    data object Onboarding : Screen
    data object Contacts : Screen
    data class Chat(val contactId: String) : Screen
    data class Verify(val contactId: String) : Screen
    data object Settings : Screen
    data object Transports : Screen
    data object AddContact : Screen
    data class PinSetup(val requireCurrent: Boolean, val returnTo: Screen) : Screen
}

@Suppress("UnusedBoxWithConstraintsScope")
@Composable
fun StadeApp(container: AppContainer) {
    val scope = rememberCoroutineScope()
    StadeTheme {
        var unlocked by remember { mutableStateOf(!container.secrets.isLockEnabled()) }
        var identity by remember { mutableStateOf<LocalIdentity?>(null) }
        var screen by remember { mutableStateOf<Screen>(if (unlocked) Screen.Onboarding else Screen.Lock) }

        // ── Arka plan → ön plan geçişinde yeniden kilitleme ─────────────────
        val isInForeground by container.isAppInForeground.collectAsState()
        var wentToBackground by remember { mutableStateOf(false) }
        LaunchedEffect(isInForeground) {
            if (!isInForeground && unlocked && container.secrets.isLockEnabled()) {
                wentToBackground = true
            }
            if (isInForeground && wentToBackground) {
                wentToBackground = false
                unlocked = false
                screen = Screen.Lock
            }
        }

        val pendingInvite by container.pendingInvite.collectAsState()
        LaunchedEffect(pendingInvite, unlocked, identity?.id) {
            if (pendingInvite != null && unlocked && identity != null &&
                screen !is Screen.AddContact && screen !is Screen.PinSetup) {
                screen = Screen.AddContact
            }
        }

        LaunchedEffect(unlocked, identity?.id) {
            val current = identity
            if (unlocked && current != null) {
                container.connections.start(current)
            } else {
                container.connections.stop()
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 600.dp
            val showTwoPanel = isWideScreen && unlocked && identity != null &&
                screen != Screen.Lock && screen != Screen.Onboarding &&
                screen !is Screen.PinSetup

            PlatformBackHandler(
                enabled = !showTwoPanel &&
                    screen !is Screen.Lock &&
                    screen !is Screen.Onboarding &&
                    screen !is Screen.Contacts
            ) {
                when (val s = screen) {
                    is Screen.Chat    -> screen = Screen.Contacts
                    is Screen.Verify  -> screen = Screen.Contacts
                    Screen.Settings   -> screen = Screen.Contacts
                    Screen.Transports -> screen = Screen.Settings
                    Screen.AddContact -> screen = Screen.Contacts
                    is Screen.PinSetup -> screen = s.returnTo
                    else -> {}
                }
            }

            when {
                screen == Screen.Lock -> LockScreen(
                    container = container,
                    onUnlocked = { unlocked = true; screen = Screen.Onboarding },
                    onForgotPin = {
                        identity = null
                        unlocked = true
                        screen = Screen.Onboarding
                    }
                )
                screen is Screen.PinSetup -> {
                    val s = screen as Screen.PinSetup
                    PinSetupScreen(
                        container = container,
                        requireCurrent = s.requireCurrent,
                        onDone = { screen = s.returnTo },
                        onCancel = { screen = s.returnTo }
                    )
                }
                screen == Screen.Onboarding -> OnboardingScreen(
                    container = container,
                    onReady = { identity = it; screen = Screen.Contacts }
                )
                showTwoPanel -> TwoPanelLayout(
                    container = container,
                    owner = identity!!,
                    onLogout = {
                        scope.launch {
                            container.connections.stop()
                            container.wipeAllData()
                            identity = null
                            unlocked = !container.secrets.isLockEnabled()
                            screen = if (unlocked) Screen.Onboarding else Screen.Lock
                        }
                    }
                )
                screen == Screen.Settings -> SettingsScreen(
                    container = container,
                    owner = identity!!,
                    onBack = { screen = Screen.Contacts },
                    onOpenTransports = { screen = Screen.Transports },
                    onOpenPinSetup = { requireCurrent ->
                        screen = Screen.PinSetup(requireCurrent, Screen.Settings)
                    },
                    onLogout = {
                        scope.launch {
                            container.connections.stop()
                            container.wipeAllData()
                            identity = null
                            unlocked = !container.secrets.isLockEnabled()
                            screen = if (unlocked) Screen.Onboarding else Screen.Lock
                        }
                    }
                )
                screen == Screen.Transports -> TransportsScreen(
                    container = container,
                    onBack = { screen = Screen.Settings }
                )
                screen == Screen.AddContact -> AddContactScreen(
                    container = container,
                    owner = identity!!,
                    onBack = {
                        container.pendingInvite.value = null
                        screen = Screen.Contacts
                    }
                )
                screen is Screen.Verify -> VerifyContactScreen(
                    container = container,
                    owner = identity!!,
                    contactId = (screen as Screen.Verify).contactId,
                    onBack = { screen = Screen.Contacts }
                )
                screen is Screen.Chat -> ChatScreen(
                    container = container,
                    owner = identity!!,
                    contactId = (screen as Screen.Chat).contactId,
                    onBack = { screen = Screen.Contacts },
                    onVerify = { screen = Screen.Verify((screen as Screen.Chat).contactId) }
                )
                else -> ContactsScreen(
                    container = container,
                    owner = identity!!,
                    onOpenChat = { screen = Screen.Chat(it) },
                    onOpenSettings = { screen = Screen.Settings },
                    onAddContact = { screen = Screen.AddContact },
                    onLongPressVerify = { screen = Screen.Verify(it) }
                )
            }
        }
    }
}
