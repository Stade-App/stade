package app.stade.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import app.stade.BootContext
import app.stade.identity.LocalIdentity
import app.stade.security.SessionTimeout
import app.stade.ui.screens.AddContactScreen
import app.stade.ui.screens.ChatScreen
import app.stade.ui.screens.ContactsScreen
import app.stade.ui.screens.LockScreen
import app.stade.ui.screens.OnboardingScreen
import app.stade.ui.screens.PinSetupScreen
import app.stade.ui.screens.SecuritySettingsScreen
import app.stade.ui.screens.SettingsScreen
import app.stade.ui.screens.TransportsScreen
import app.stade.ui.screens.VerifyContactScreen
import app.stade.ui.theme.StadeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Onboarding : Screen
    data object Contacts : Screen
    data class Chat(val contactId: String) : Screen
    data class Verify(val contactId: String) : Screen
    data object Settings : Screen
    data object Security : Screen
    data object Transports : Screen
    data object AddContact : Screen
    data class PinSetup(val requireCurrent: Boolean, val returnTo: Screen) : Screen
}

@Composable
fun StadeApp(boot: BootContext) {
    val vault = boot.vault
    StadeTheme {
        var initialized by remember { mutableStateOf(vault.isInitialized()) }
        var unlocked by remember { mutableStateOf(vault.isUnlocked()) }
        var autoUnlockTried by remember { mutableStateOf(false) }
        var container by remember { mutableStateOf<AppContainer?>(null) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(initialized) {
            if (initialized && !unlocked && !autoUnlockTried) {
                val ok = withContext(Dispatchers.Default) { vault.tryAutoUnlock() }
                autoUnlockTried = true
                if (ok) unlocked = true
            }
        }

        when {
            !initialized -> PinSetupScreen(
                vault = vault,
                requireCurrent = false,
                onDone = {
                    initialized = true
                    unlocked = true
                },
                onCancel = { }
            )
            !unlocked -> {
                if (autoUnlockTried) {
                    LockScreen(
                        vault = vault,
                        onUnlocked = { unlocked = true },
                        onForgotPin = {
                            initialized = vault.isInitialized()
                            container = null
                            autoUnlockTried = true
                        }
                    )
                }
            }
            else -> {
                val active = container ?: remember { boot.buildContainer() }.also { container = it }
                UnlockedApp(
                    container = active,
                    onLockRequested = {
                        scope.launch {
                            withContext(Dispatchers.Default) { vault.flushAndKeep() }
                            unlocked = false
                        }
                    },
                    onWipeRequested = {
                        scope.launch {
                            active.wipeAllData()
                            container = null
                            initialized = vault.isInitialized()
                            unlocked = false
                        }
                    }
                )
            }
        }
    }
}

@Suppress("UnusedBoxWithConstraintsScope")
@Composable
private fun UnlockedApp(
    container: AppContainer,
    onLockRequested: () -> Unit,
    onWipeRequested: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var identity by remember { mutableStateOf<LocalIdentity?>(null) }
    var screen by remember { mutableStateOf<Screen>(Screen.Onboarding) }

    val isInForeground by container.isAppInForeground.collectAsState()
    var leftForegroundAt by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(isInForeground) {
        if (!isInForeground) {
            leftForegroundAt = container.vault.nowMillis()
        } else {
            val left = leftForegroundAt
            leftForegroundAt = null
            if (left != null) {
                val timeout = container.vault.sessionTimeoutSeconds()
                val elapsedMs = container.vault.nowMillis() - left
                val shouldRelock = when (timeout) {
                    SessionTimeout.NEVER -> false
                    SessionTimeout.IMMEDIATE -> true
                    else -> elapsedMs >= timeout.toLong() * 1000L
                }
                if (shouldRelock) {
                    onLockRequested()
                }
            }
        }
    }

    DisposableEffect(container) {
        onDispose {
            runCatching { container.vault.flushAndKeep() }
        }
    }

    val pendingInvite by container.pendingInvite.collectAsState()
    LaunchedEffect(pendingInvite, identity?.id) {
        if (pendingInvite != null && identity != null &&
            screen !is Screen.AddContact && screen !is Screen.PinSetup) {
            screen = Screen.AddContact
        }
    }

    LaunchedEffect(identity?.id) {
        val current = identity
        if (current != null) {
            container.connections.start(current)
        } else {
            container.connections.stop()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp
        val showTwoPanel = isWideScreen && identity != null &&
            screen != Screen.Onboarding &&
            screen !is Screen.PinSetup

        PlatformBackHandler(
            enabled = !showTwoPanel &&
                screen !is Screen.Onboarding &&
                screen !is Screen.Contacts
        ) {
            when (val s = screen) {
                is Screen.Chat -> screen = Screen.Contacts
                is Screen.Verify -> screen = Screen.Contacts
                Screen.Settings -> screen = Screen.Contacts
                Screen.Security -> screen = Screen.Settings
                Screen.Transports -> screen = Screen.Settings
                Screen.AddContact -> screen = Screen.Contacts
                is Screen.PinSetup -> screen = s.returnTo
                else -> {}
            }
        }

        when {
            screen is Screen.PinSetup -> {
                val s = screen as Screen.PinSetup
                PinSetupScreen(
                    vault = container.vault,
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
                        onWipeRequested()
                    }
                }
            )
            screen == Screen.Settings -> SettingsScreen(
                container = container,
                owner = identity!!,
                onBack = { screen = Screen.Contacts },
                onOpenTransports = { screen = Screen.Transports },
                onOpenSecurity = { screen = Screen.Security },
                onLogout = {
                    scope.launch {
                        container.connections.stop()
                        onWipeRequested()
                    }
                }
            )
            screen == Screen.Security -> SecuritySettingsScreen(
                container = container,
                onBack = { screen = Screen.Settings },
                onOpenPinSetup = { requireCurrent ->
                    screen = Screen.PinSetup(requireCurrent, Screen.Security)
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
