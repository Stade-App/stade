package dev.stade.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.stade.ui.beginAcceptStadiumInvite
import dev.stade.stadium.joinOfficialStadiumIfNeeded
import dev.stade.ui.screens.AboutScreen
import dev.stade.ui.screens.AddContactScreen
import dev.stade.ui.screens.ChatScreen
import dev.stade.ui.screens.ContactsScreen
import dev.stade.ui.screens.CreateGroupScreen
import dev.stade.ui.screens.CreateStadiumScreen
import dev.stade.ui.screens.GroupChatScreen
import dev.stade.ui.screens.GroupMembersScreen
import dev.stade.ui.screens.JoinStadiumScreen
import dev.stade.ui.screens.LockScreen
import dev.stade.ui.screens.ManageStadiumScreen
import dev.stade.ui.screens.OnboardingScreen
import dev.stade.ui.screens.PinSetupMode
import dev.stade.ui.screens.PinSetupScreen
import dev.stade.radar.isRadarSupported
import dev.stade.ui.screens.StadeRadarScreen
import dev.stade.ui.screens.StadeyScreen
import dev.stade.ui.screens.SecuritySettingsScreen
import dev.stade.ui.screens.SettingsScreen
import dev.stade.ui.screens.StadiumScreen
import dev.stade.ui.screens.TransportsScreen
import dev.stade.ui.screens.VerifyContactScreen
import dev.stade.ui.screens.WelcomeUsernameScreen
import dev.stade.ui.i18n.LocalStrings
import dev.stade.ui.i18n.getLocalePreference
import dev.stade.ui.i18n.localeToStrings
import dev.stade.ui.components.fastLongPressConfiguration
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import dev.stade.ui.theme.StadeTheme
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.rememberLazyListState

private const val NAV_SLIDE_MS = 260
private const val NAV_FADE_MS = 180

internal fun screenKey(s: Screen): String = when (s) {
    is Screen.Chat -> "chat:" + s.contactId
    is Screen.GroupChat -> "group:" + s.groupId
    is Screen.Stadium -> "stadium:" + s.stadiumId
    is Screen.GroupMembers -> "members:" + s.groupId
    is Screen.ManageStadium -> "manage:" + s.stadiumId
    is Screen.Verify -> "verify:" + s.contactId
    is Screen.PinSetup -> "pinSetup"
    else -> s.toString()
}

internal fun screenDepth(s: Screen): Int = when (s) {
    Screen.Onboarding -> 0
    Screen.Contacts -> 1
    Screen.Settings, Screen.Stadey, Screen.AddContact, Screen.Radar,
    Screen.CreateGroup, Screen.CreateStadium, Screen.JoinStadium -> 2
    Screen.Security, Screen.Transports, Screen.About -> 3
    is Screen.GroupMembers, is Screen.ManageStadium, is Screen.Verify, is Screen.PinSetup -> 3
    else -> 2
}

sealed interface Screen {
    data object Onboarding : Screen
    data object Contacts : Screen
    data class Chat(val contactId: String, val highlightMessageId: String? = null) : Screen
    data class GroupChat(val groupId: String, val highlightMessageId: String? = null) : Screen
    data class GroupMembers(val groupId: String) : Screen
    data object CreateGroup : Screen
    data class Stadium(val stadiumId: String, val highlightMessageId: String? = null) : Screen
    data object CreateStadium : Screen
    data class ManageStadium(val stadiumId: String) : Screen
    data object JoinStadium : Screen
    data class Verify(val contactId: String, val fromScreen: Screen) : Screen
    data object Settings : Screen
    data object Security : Screen
    data object Transports : Screen
    data object About : Screen
    data object Stadey : Screen
    data object AddContact : Screen
    data object Radar : Screen
    data class PinSetup(val requireCurrent: Boolean, val returnTo: Screen, val mode: PinSetupMode = PinSetupMode.Primary) : Screen
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
        CompositionLocalProvider(LocalStrings provides activeStrings, LocalViewConfiguration provides fastLongPressConfiguration()) {
            var initialized by remember { mutableStateOf(vault.isInitialized()) }
            var unlocked by remember { mutableStateOf(boot.resolveUnlocked()) }
            var autoUnlockTried by remember { mutableStateOf(false) }
            var container by remember { mutableStateOf<AppContainer?>(null) }
            var pendingNickname by remember { mutableStateOf<String?>(null) }
            var lockFailure by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(initialized) {
                if (initialized && !unlocked && !autoUnlockTried) {
                    val ok = withContext(Dispatchers.Default) { vault.tryAutoUnlock() }
                    autoUnlockTried = true
                    if (ok && boot.resolveUnlocked()) unlocked = true
                }
            }

            if (lockFailure) {
                AlertDialog(
                    onDismissRequest = { lockFailure = false },
                    title = { Text(activeStrings.vaultLockFailedTitle) },
                    text = { Text(activeStrings.vaultLockFailedBody) },
                    confirmButton = {
                        TextButton(onClick = { lockFailure = false }) {
                            Text(activeStrings.closeAction)
                        }
                    }
                )
            }

            when {
                !initialized -> {
                    val nickname = pendingNickname
                    if (nickname == null) {
                        WelcomeUsernameScreen(onNext = { pendingNickname = it })
                    } else {
                        PinSetupScreen(
                            vault = vault,
                            requireCurrent = false,
                            tip = activeStrings.onboardingPinTip,
                            onDone = {
                                initialized = true
                                boot.markUnlocked()
                                unlocked = true
                            },
                            onCancel = { pendingNickname = null }
                        )
                    }
                }
                !unlocked -> {
                    LockScreen(
                        vault = vault,
                        onUnlocked = {
                            boot.markUnlocked()
                            unlocked = true
                        },
                        onPrepareWipe = {
                            (container ?: boot.activeContainer())?.let { runCatching { it.close() } }
                            container = null
                        },
                        onForgotPin = {
                            initialized = vault.isInitialized()
                            autoUnlockTried = true
                            pendingNickname = null
                        },
                        onDuressTriggered = {
                            (container ?: boot.activeContainer())?.let { runCatching { it.close() } }
                            container = null
                            boot.markLocked()
                            unlocked = false
                            runCatching { vault.wipe() }
                            initialized = vault.isInitialized()
                            autoUnlockTried = true
                            pendingNickname = null
                        }
                    )
                }
                else -> {
                    val active = container ?: boot.buildContainer().also { container = it }
                    UnlockedApp(
                        container = active,
                        boot = boot,
                        presetNickname = pendingNickname,
                        onLockRequested = {
                            scope.launch {
                                val toClose = container ?: boot.activeContainer()
                                val failure = runCatching {
                                    withContext(Dispatchers.Default) {
                                        toClose?.close()
                                        vault.flushAndClose()
                                    }
                                }.exceptionOrNull()
                                if (failure != null) {
                                    container = null
                                    boot.markUnlocked()
                                    unlocked = true
                                    lockFailure = true
                                    return@launch
                                }
                                container = null
                                boot.markLocked()
                                unlocked = false
                            }
                        },
                        onWipeRequested = {
                            scope.launch {
                                val toWipe = container
                                container = null
                                boot.markLocked()
                                unlocked = false
                                kotlinx.coroutines.delay(120)
                                if (toWipe != null) {
                                    runCatching { toWipe.wipeAllData() }
                                }
                                initialized = vault.isInitialized()
                                autoUnlockTried = false
                                pendingNickname = null
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
    boot: BootContext,
    presetNickname: String?,
    onLockRequested: () -> Unit,
    onWipeRequested: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var identity by remember { mutableStateOf<LocalIdentity?>(null) }
    var screen by remember { mutableStateOf<Screen>(Screen.Onboarding) }
    val settingsListState = rememberLazyListState()

    val isInForeground by container.isAppInForeground.collectAsState()
    LaunchedEffect(isInForeground) {
        if (!isInForeground) {
            boot.noteLeftForeground()
        } else if (!boot.resolveUnlocked()) {
            onLockRequested()
        }
    }

    DisposableEffect(container) {
        onDispose {
        }
    }

    LaunchedEffect(container) {
        while (isActive) {
            runCatching { container.vanish.sweepAllActive() }
            delay(30_000L)
        }
    }

    val pendingInvite by container.pendingInvite.collectAsState()

    val pendingOpenChatId by container.pendingOpenChat.collectAsState()
    LaunchedEffect(pendingOpenChatId, identity?.id) {
        val id = pendingOpenChatId
        if (id != null && identity != null) {
            screen = Screen.Chat(id)
            container.pendingOpenChat.value = null
        }
    }

    val pendingOpenGroupId by container.pendingOpenGroup.collectAsState()
    LaunchedEffect(pendingOpenGroupId, identity?.id) {
        val id = pendingOpenGroupId
        if (id != null && identity != null) {
            screen = Screen.GroupChat(id)
            container.pendingOpenGroup.value = null
        }
    }

    val pendingOpenStadiumId by container.pendingOpenStadium.collectAsState()
    LaunchedEffect(pendingOpenStadiumId, identity?.id) {
        val id = pendingOpenStadiumId
        if (id != null && identity != null) {
            screen = Screen.Stadium(id)
            container.pendingOpenStadium.value = null
        }
    }

    val pendingGoHome by container.pendingGoHome.collectAsState()
    LaunchedEffect(pendingGoHome, identity?.id) {
        if (pendingGoHome && identity != null) {
            screen = Screen.Contacts
            container.pendingGoHome.value = false
        }
    }

    LaunchedEffect(identity?.id) {
        val current = identity
        if (current != null) {
            container.connections.start(current)
            container.groupChat.start(current, this)
            container.stadiumChat.start(current, this)
            container.avatars.start(current, this)
            container.typing.start(container.sync, this)
            container.scheduler.start(current, container.appScope)
            launch { runCatching { joinOfficialStadiumIfNeeded(container, current) } }
        } else {
            container.scheduler.stop()
            container.connections.stop()
        }
    }

    LaunchedEffect(identity?.id) {
        val currentId = identity?.id ?: return@LaunchedEffect
        container.identities.observeIdentities().collect { list ->
            list.find { it.id == currentId }?.let { fresh -> identity = fresh }
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
                        ?.let { when (dev.stade.message.padPreviewKind(it)) {
                            dev.stade.message.MessageType.PAD_SOUND -> notifStrings.padSentSound(null, false)
                            dev.stade.message.MessageType.MEME_CLIP -> notifStrings.padSentMeme(null, false)
                            else -> dev.stade.message.previewBody(it, notifStrings.photoMessage, notifStrings.voiceMessage, notifStrings.videoMessage, notifStrings.stickerMessage)
                        } }
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
                is dev.stade.sync.SyncEngine.SyncEvent.RemovedFromGroup -> {
                    if (!dev.stade.notification.getNotificationsEnabled().value) return@collect
                    val privacy = dev.stade.notification.getNotificationPrivacyEnabled().value
                    val notifStrings = dev.stade.ui.i18n.I18n.current
                    dev.stade.notification.showIncomingMessageNotification(
                        contactId = event.groupId,
                        senderName = event.groupName,
                        preview = notifStrings.removedFromGroupNotification(event.groupName),
                        privacy = privacy,
                        unreadTotal = 0
                    )
                }
                is dev.stade.sync.SyncEngine.SyncEvent.GroupMessageReceived -> {
                    if (!dev.stade.notification.getNotificationsEnabled().value) return@collect
                    if (container.isAppInForeground.value && container.activeContactId == event.groupId) return@collect
                    val notifStrings = dev.stade.ui.i18n.I18n.current
                    val group = container.groups.getGroup(event.groupId)
                    val name = group?.name ?: "Stade"
                    val preview = container.groups.lastMessage(event.groupId)?.body
                        ?.let { when (dev.stade.message.padPreviewKind(it)) {
                            dev.stade.message.MessageType.PAD_SOUND -> notifStrings.padSentSound(null, false)
                            dev.stade.message.MessageType.MEME_CLIP -> notifStrings.padSentMeme(null, false)
                            else -> dev.stade.message.previewBody(it, notifStrings.photoMessage, notifStrings.voiceMessage, notifStrings.videoMessage, notifStrings.stickerMessage)
                        } }
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
                is dev.stade.sync.SyncEngine.SyncEvent.StadiumInviteReceived -> {
                    identity?.let { container.beginAcceptStadiumInvite(it, event.code, dev.stade.ui.i18n.I18n.current) }
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
                is Screen.GroupMembers -> screen = Screen.GroupChat(s.groupId)
                Screen.CreateGroup -> screen = Screen.Contacts
                is Screen.Stadium -> screen = Screen.Contacts
                is Screen.ManageStadium -> screen = Screen.Stadium(s.stadiumId)
                Screen.CreateStadium -> screen = Screen.Contacts
                Screen.JoinStadium -> screen = Screen.Contacts

                is Screen.Verify -> screen = s.fromScreen

                Screen.Settings -> screen = Screen.Contacts
                Screen.Security -> screen = Screen.Settings
                Screen.Transports -> screen = Screen.Settings
                Screen.About -> screen = Screen.Settings
                Screen.Stadey -> screen = Screen.Contacts
                Screen.AddContact -> screen = Screen.Contacts
                Screen.Radar -> screen = Screen.Contacts
                is Screen.PinSetup -> screen = s.returnTo
                else -> {}
            }
        }

        AnimatedContent(
            targetState = screen,
            modifier = Modifier.fillMaxSize(),
            contentKey = { screenKey(it) },
            transitionSpec = {
                val forward = screenDepth(targetState) >= screenDepth(initialState)
                if (forward) {
                    (slideInHorizontally(tween(NAV_SLIDE_MS)) { it } + fadeIn(tween(NAV_FADE_MS))) togetherWith
                        (slideOutHorizontally(tween(NAV_SLIDE_MS)) { -it / 4 } + fadeOut(tween(NAV_FADE_MS)))
                } else {
                    (slideInHorizontally(tween(NAV_SLIDE_MS)) { -it / 4 } + fadeIn(tween(NAV_FADE_MS))) togetherWith
                        (slideOutHorizontally(tween(NAV_SLIDE_MS)) { it } + fadeOut(tween(NAV_FADE_MS)))
                }
            },
            label = "screenNav"
        ) { target ->
            val twoPanelNow = isWideScreen && identity != null &&
                    target != Screen.Onboarding &&
                    target !is Screen.PinSetup
            when {
                target is Screen.PinSetup -> {
                    val s = target as Screen.PinSetup
                    PinSetupScreen(
                        vault = container.vault,
                        requireCurrent = s.requireCurrent,
                        mode = s.mode,
                        onDone = { screen = s.returnTo },
                        onCancel = { screen = s.returnTo }
                    )
                }
                target == Screen.Onboarding -> OnboardingScreen(
                    container = container,
                    presetNickname = presetNickname,
                    onReady = { identity = it; screen = Screen.Contacts }
                )
                twoPanelNow -> TwoPanelLayout(
                    container = container,
                    owner = identity!!,
                    onLogout = {
                        scope.launch {
                            container.connections.stop()
                            onWipeRequested()
                        }
                    }
                )
                target == Screen.Settings -> SettingsScreen(
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
                target == Screen.Security -> SecuritySettingsScreen(
                    container = container,
                    onBack = { screen = Screen.Settings },
                    onOpenPinSetup = { requireCurrent ->
                        screen = Screen.PinSetup(requireCurrent, Screen.Security)
                    },
                    onOpenDuressPinSetup = {
                        screen = Screen.PinSetup(true, Screen.Security, PinSetupMode.Duress)
                    }
                )
                target == Screen.Transports -> TransportsScreen(
                    container = container,
                    onBack = { screen = Screen.Settings }
                )
                target == Screen.About -> AboutScreen(
                    onBack = { screen = Screen.Settings }
                )
                target == Screen.Stadey -> StadeyScreen(
                    onBack = { screen = Screen.Contacts }
                )
                target == Screen.AddContact -> AddContactScreen(
                    container = container,
                    owner = identity!!,
                    onBack = {
                        container.pendingInvite.value = null
                        screen = Screen.Contacts
                    }
                )
                target == Screen.Radar -> StadeRadarScreen(
                    container = container,
                    owner = identity!!,
                    onBack = { screen = Screen.Contacts }
                )
                target is Screen.Verify -> VerifyContactScreen(
                    container = container,
                    owner = identity!!,
                    contactId = (target as Screen.Verify).contactId,
                    onBack = {
                        screen = (target as Screen.Verify).fromScreen
                    }
                )
                target is Screen.Chat -> {
                    val currentChat = target as Screen.Chat

                    ChatScreen(
                        container = container,
                        owner = identity!!,
                        contactId = currentChat.contactId,
                        highlightMessageId = currentChat.highlightMessageId,
                        onBack = { screen = Screen.Contacts },
                        onOpenProfile = {
                            screen = Screen.Verify(contactId = currentChat.contactId, fromScreen = currentChat)
                        },
                        onContactDeleted = { screen = Screen.Contacts }
                    )
                }
                target is Screen.GroupChat -> {
                    val currentGroupChat = target as Screen.GroupChat
                    val currentGroupId = currentGroupChat.groupId
                    GroupChatScreen(
                        container = container,
                        owner = identity!!,
                        groupId = currentGroupId,
                        highlightMessageId = currentGroupChat.highlightMessageId,
                        onBack = { screen = Screen.Contacts },
                        onOpenMembers = { screen = Screen.GroupMembers(currentGroupId) }
                    )
                }
                target is Screen.GroupMembers -> {
                    val currentGroupMembersScreen = target as Screen.GroupMembers
                    val currentGroupId = currentGroupMembersScreen.groupId
                    GroupMembersScreen(
                        container = container,
                        owner = identity!!,
                        groupId = currentGroupId,
                        onBack = { screen = Screen.GroupChat(currentGroupId) },
                        onOpenProfile = { memberId ->
                            screen = Screen.Verify(contactId = memberId, fromScreen = currentGroupMembersScreen)
                        }
                    )
                }
                target == Screen.CreateGroup -> CreateGroupScreen(
                    container = container,
                    owner = identity!!,
                    onBack = { screen = Screen.Contacts },
                    onGroupCreated = { groupId -> screen = Screen.GroupChat(groupId) }
                )
                target is Screen.Stadium -> {
                    val currentStadium = target as Screen.Stadium
                    StadiumScreen(
                        container = container,
                        owner = identity!!,
                        stadiumId = currentStadium.stadiumId,
                        onBack = { screen = Screen.Contacts },
                        onManage = { screen = Screen.ManageStadium(currentStadium.stadiumId) }
                    )
                }
                target is Screen.ManageStadium -> {
                    val currentManage = target as Screen.ManageStadium
                    ManageStadiumScreen(
                        container = container,
                        owner = identity!!,
                        stadiumId = currentManage.stadiumId,
                        onBack = { screen = Screen.Stadium(currentManage.stadiumId) },
                        onDeleted = { screen = Screen.Contacts }
                    )
                }
                target == Screen.CreateStadium -> CreateStadiumScreen(
                    container = container,
                    owner = identity!!,
                    onBack = { screen = Screen.Contacts },
                    onStadiumCreated = { stadiumId -> screen = Screen.Stadium(stadiumId) }
                )
                target == Screen.JoinStadium -> JoinStadiumScreen(
                    container = container,
                    owner = identity!!,
                    onBack = { screen = Screen.Contacts },
                    onJoined = { stadiumId -> screen = Screen.Stadium(stadiumId) }
                )
                else -> {
                    val currentContactsScreen = screen

                    ContactsScreen(
                        container = container,
                        owner = identity!!,
                        onOpenChat = { screen = Screen.Chat(it) },
                        onOpenGroupChat = { screen = Screen.GroupChat(it) },
                        onOpenStadium = { screen = Screen.Stadium(it) },
                        onOpenSettings = { screen = Screen.Settings },
                        onOpenStadey = { screen = Screen.Stadey },
                        onAddContact = { screen = Screen.AddContact },
                        onCreateGroup = { screen = Screen.CreateGroup },
                        onCreateStadium = { screen = Screen.CreateStadium },
                        onJoinStadium = { screen = Screen.JoinStadium },
                        onOpenRadar = if (isRadarSupported) ({ screen = Screen.Radar }) else null,
                        onLongPressVerify = { contactId ->
                            screen = Screen.Verify(contactId = contactId, fromScreen = currentContactsScreen)
                        },
                        onOpenChatMessage = { contactId, messageId ->
                            screen = Screen.Chat(contactId, highlightMessageId = messageId)
                        },
                        onOpenGroupMessage = { groupId, messageId ->
                            screen = Screen.GroupChat(groupId, highlightMessageId = messageId)
                        },
                        onOpenStadiumMessage = { stadiumId, messageId ->
                            screen = Screen.Stadium(stadiumId, highlightMessageId = messageId)
                        }
                    )
                }
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
