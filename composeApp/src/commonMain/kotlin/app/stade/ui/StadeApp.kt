package app.stade.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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

        when (val s = screen) {
            Screen.Lock -> LockScreen(
                container = container,
                onUnlocked = {
                    unlocked = true
                    screen = Screen.Onboarding
                }
            )
            Screen.Onboarding -> OnboardingScreen(
                container = container,
                onReady = {
                    identity = it
                    screen = Screen.Contacts
                }
            )
            Screen.Contacts -> ContactsScreen(
                container = container,
                owner = identity!!,
                onOpenChat = { screen = Screen.Chat(it) },
                onOpenSettings = { screen = Screen.Settings },
                onAddContact = { screen = Screen.AddContact },
                onLongPressVerify = { screen = Screen.Verify(it) }
            )
            is Screen.Chat -> ChatScreen(
                container = container,
                owner = identity!!,
                contactId = s.contactId,
                onBack = { screen = Screen.Contacts },
                onVerify = { screen = Screen.Verify(s.contactId) }
            )
            is Screen.Verify -> VerifyContactScreen(
                container = container,
                owner = identity!!,
                contactId = s.contactId,
                onBack = { screen = Screen.Contacts }
            )
            Screen.Settings -> SettingsScreen(
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
            Screen.Transports -> TransportsScreen(
                container = container,
                onBack = { screen = Screen.Settings }
            )
            Screen.AddContact -> AddContactScreen(
                container = container,
                owner = identity!!,
                onBack = { screen = Screen.Contacts }
            )
        }
    }
}
