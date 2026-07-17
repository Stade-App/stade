package dev.stade.ui

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
import dev.stade.AppContainer
import dev.stade.BootContext
import dev.stade.identity.LocalIdentity
import dev.stade.security.SessionTimeout
import dev.stade.ui.screens.AboutScreen
import dev.stade.ui.screens.AddContactScreen
import dev.stade.ui.screens.ChatScreen
import dev.stade.ui.screens.ContactsScreen
import dev.stade.ui.screens.CreateGroupScreen
import dev.stade.ui.screens.GroupChatScreen
import dev.stade.ui.screens.LockScreen
import dev.stade.ui.screens.OnboardingScreen
import dev.stade.ui.screens.PinSetupScreen
import dev.stade.ui.screens.SecuritySettingsScreen
import dev.stade.ui.screens.SettingsScreen
import dev.stade.ui.screens.TransportsScreen
import dev.stade.ui.screens.VerifyContactScreen
import dev.stade.ui.i18n.LocalStrings
import dev.stade.ui.i18n.getLocalePreference
import dev.stade.ui.i18n.localeToStrings
import dev.stade.ui.theme.StadeTheme
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.rememberLazyListState

sealed interface Screen {
    data object Onboarding : Screen
    data object Contacts : Screen
    data class Chat(val contactId: String) : Screen
    data class GroupChat(val groupId: String) : Screen
    data object CreateGroup : Screen
    data class Verify(val contactId: String, val fromScreen: Screen) : Screen
    data object Settings : Screen
    data object Security : Screen
    data object Transports : Screen
    data object About : Screen
    data object AddContact : Screen
    data class PinSetup(val requireCurrent: Boolean, val returnTo: Screen) : Screen
}

@Composable
fun StadeApp(boot: BootContext) {
    val vault = boot.vault
    val locale by getLocalePreference()
    val activeStrings = localeToStrings(locale)
    LaunchedEffect(activeStrings) {
        dev.stade.ui.i18n.I18n.current = activeStrings
    }
    StadeTheme {
        CompositionLocalProvider(LocalStrings provides activeStrings) {
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
                    LockScreen(
                        vault = vault,
                        onUnlocked = { unlocked = true },
                        onPrepareWipe = {
                            container?.let { runCatching { it.close() } }
                            container = null
                        },
                        onForgotPin = {
                            initialized = vault.isInitialized()
                            autoUnlockTried = true
                        }
                    )
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
                                val toWipe = container
                                container = null
                                unlocked = false
                                kotlinx.coroutines.delay(120)
                                if (toWipe != null) {
                                    runCatching { toWipe.wipeAllData() }
                                }
                                initialized = vault.isInitialized()
                                autoUnlockTried = false
                            }
                        }
                    )
                }
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
    val settingsListState = rememberLazyListState()

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
        }
    }

    val pendingInvite by container.pendingInvite.collectAsState()

    LaunchedEffect(identity?.id) {
        val current = identity
        if (current != null) {
            container.connections.start(current)
            container.groupChat.start(current, this)
        } else {
            container.connections.stop()
        }
    }

    LaunchedEffect(identity?.id, container) {
        if (identity == null) return@LaunchedEffect
        container.sync.events.collect { event ->
            when (event) {
                is dev.stade.sync.SyncEngine.SyncEvent.MessageReceived -> {
                    if (!dev.stade.notification.getNotificationsEnabled().value) return@collect
                    if (container.isAppInForeground.value && container.activeContactId == event.contactId) return@collect
                    val notifStrings = dev.stade.ui.i18n.I18n.current
                    val contact = container.contacts.get(event.contactId)
                    val sender = contact?.nickname ?: "Stade"
                    val preview = runCatching { container.messages.lastMessage(event.contactId)?.body }
                        .getOrNull()
                        ?.let { dev.stade.message.previewBody(it, notifStrings.photoMessage) }
                        ?: notifStrings.notifNewMessageFallback
                    val total = runCatching { container.messages.totalUnread() }.getOrDefault(0L).toInt()
                    val privacy = dev.stade.notification.getNotificationPrivacyEnabled().value
                    dev.stade.notification.showIncomingMessageNotification(
                        contactId = event.contactId,
                        senderName = sender,
                        preview = preview,
                        privacy = privacy,
                        unreadTotal = total
                    )
                }
                is dev.stade.sync.SyncEngine.SyncEvent.GroupMessageReceived -> {
                    if (!dev.stade.notification.getNotificationsEnabled().value) return@collect
                    val notifStrings = dev.stade.ui.i18n.I18n.current
                    val group = container.groups.getGroup(event.groupId)
                    val name = group?.name ?: "Stade"
                    val preview = container.groups.lastMessage(event.groupId)?.body
                        ?.let { dev.stade.message.previewBody(it, notifStrings.photoMessage) }
                        ?: notifStrings.notifNewMessageFallback
                    val privacy = dev.stade.notification.getNotificationPrivacyEnabled().value
                    dev.stade.notification.showIncomingMessageNotification(
                        contactId = event.groupId,
                        senderName = name,
                        preview = preview,
                        privacy = privacy,
                        unreadTotal = 0
                    )
                }
                else -> Unit
            }
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
                is Screen.GroupChat -> screen = Screen.Contacts
                Screen.CreateGroup -> screen = Screen.Contacts

                is Screen.Verify -> screen = s.fromScreen

                Screen.Settings -> screen = Screen.Contacts
                Screen.Security -> screen = Screen.Settings
                Screen.Transports -> screen = Screen.Settings
                Screen.About -> screen = Screen.Settings
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
                onOpenAbout = { screen = Screen.About },
                onLogout = {
                    scope.launch {
                        container.connections.stop()
                        onWipeRequested()
                    }
                },
                listState = settingsListState
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
            screen == Screen.About -> AboutScreen(
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
                onBack = {
                    screen = (screen as Screen.Verify).fromScreen
                }
            )
            screen is Screen.Chat -> {
                val currentChat = screen as Screen.Chat

                ChatScreen(
                    container = container,
                    owner = identity!!,
                    contactId = currentChat.contactId,
                    onBack = { screen = Screen.Contacts },
                    onVerify = {
                        screen = Screen.Verify(contactId = currentChat.contactId, fromScreen = currentChat)
                    },
                    onContactDeleted = { screen = Screen.Contacts }
                )
            }
            screen is Screen.GroupChat -> GroupChatScreen(
                container = container,
                owner = identity!!,
                groupId = (screen as Screen.GroupChat).groupId,
                onBack = { screen = Screen.Contacts }
            )
            screen == Screen.CreateGroup -> CreateGroupScreen(
                container = container,
                owner = identity!!,
                onBack = { screen = Screen.Contacts },
                onGroupCreated = { groupId -> screen = Screen.GroupChat(groupId) }
            )
            else -> {
                val currentContactsScreen = screen

                ContactsScreen(
                    container = container,
                    owner = identity!!,
                    onOpenChat = { screen = Screen.Chat(it) },
                    onOpenGroupChat = { screen = Screen.GroupChat(it) },
                    onOpenSettings = { screen = Screen.Settings },
                    onAddContact = { screen = Screen.AddContact },
                    onCreateGroup = { screen = Screen.CreateGroup },
                    onLongPressVerify = { contactId ->
                        screen = Screen.Verify(contactId = contactId, fromScreen = currentContactsScreen)
                    }
                )
            }
        }

        val pending = pendingInvite
        val owner = identity
        if (pending != null && owner != null &&
            screen != Screen.Onboarding &&
            screen !is Screen.PinSetup &&
            screen !is Screen.AddContact
        ) {
            IncomingInviteDialog(
                container = container,
                owner = owner,
                code = pending,
                onDismiss = { container.pendingInvite.value = null }
            )
        }
    }
}
