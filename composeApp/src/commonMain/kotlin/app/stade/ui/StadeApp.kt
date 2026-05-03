package app.stade.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
}

@Suppress("UnusedBoxWithConstraintsScope")
@Composable
fun StadeApp(container: AppContainer) {
    val scope = rememberCoroutineScope()
    StadeTheme {
        var unlocked by remember { mutableStateOf(!container.secrets.isLockEnabled()) }
        var identity by remember { mutableStateOf<LocalIdentity?>(null) }
        var screen by remember { mutableStateOf<Screen>(if (unlocked) Screen.Onboarding else Screen.Lock) }

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
            // Geniş ekranda Lock/Onboarding dışı her şey iki panelde açılır
            val showTwoPanel = isWideScreen && unlocked && identity != null &&
                screen != Screen.Lock && screen != Screen.Onboarding

            PlatformBackHandler(
                enabled = !showTwoPanel &&
                    screen !is Screen.Lock &&
                    screen !is Screen.Onboarding &&
                    screen !is Screen.Contacts
            ) {
                when (screen) {
                    is Screen.Chat    -> screen = Screen.Contacts
                    is Screen.Verify  -> screen = Screen.Contacts
                    Screen.Settings   -> screen = Screen.Contacts
                    Screen.Transports -> screen = Screen.Settings
                    Screen.AddContact -> screen = Screen.Contacts
                    else -> {}
                }
            }

            when {
                screen == Screen.Lock -> LockScreen(
                    container = container,
                    onUnlocked = { unlocked = true; screen = Screen.Onboarding }
                )
                screen == Screen.Onboarding -> OnboardingScreen(
                    container = container,
                    onReady = { identity = it; screen = Screen.Contacts }
                )
                // ── Geniş ekran: her şey iki panelde açılır ──────────
                showTwoPanel -> TwoPanelLayout(
                    container = container,
                    owner = identity!!,
                    onLogout = {
                        scope.launch { container.connections.stop() }
                        identity = null
                        screen = Screen.Onboarding
                    }
                )
                // ── Dar ekran: tek panel akışı ────────────────────────
                screen == Screen.Settings -> SettingsScreen(
                    container = container,
                    owner = identity!!,
                    onBack = { screen = Screen.Contacts },
                    onOpenTransports = { screen = Screen.Transports },
                    onLogout = {
                        scope.launch { container.connections.stop() }
                        identity = null
                        screen = Screen.Onboarding
                    }
                )
                screen == Screen.Transports -> TransportsScreen(
                    container = container,
                    onBack = { screen = Screen.Settings }
                )
                screen == Screen.AddContact -> AddContactScreen(
                    container = container,
                    owner = identity!!,
                    onBack = { screen = Screen.Contacts }
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
