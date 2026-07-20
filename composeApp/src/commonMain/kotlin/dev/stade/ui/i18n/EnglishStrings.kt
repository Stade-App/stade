package dev.stade.ui.i18n

import dev.stade.security.SessionTimeout

object EnglishStrings : AppStrings() {
    override val back = "Back"
    override val cancel = "Cancel"
    override val delete = "Delete"

    override val loading = "Loading…"
    override val welcomeTitle = "Welcome to Stade"
    override val welcomeDescription =
        "End-to-end encrypted, serverless, post-quantum secure messaging.\n" +
        "Choose a nickname to get started — a permanent Stade ID will be assigned."
    override val nicknamePlaceholder = "Nickname"
    override val createIdentity = "Create identity"

    override val unlockTitle = "Unlock"
    override val unlockSubtitle = "Enter your PIN to continue."
    override val tooManyAttemptsSubtitle = "Too many failed attempts. Please wait."
    override val forgotPin = "Forgot PIN"
    override val resetPinTitle = "Reset PIN"
    override val resetPinBody =
        "PIN cannot be recovered from this device. Continuing will permanently erase all local data and reset the app."
    override val resetAndWipe = "Reset and wipe"
    override val vaultNotInitialized = "Vault not initialized"
    override fun wrongPinRemaining(remaining: Int) = "Wrong PIN ($remaining attempts left)"
    override val wrongPin = "Wrong PIN"
    override val wiping = "Wiping…"
    override fun retryIn(formattedTime: String) = "Retry in $formattedTime"
    override fun formatRemainingTime(seconds: Long) = when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }

    override val enterCurrentPinTitle = "Enter current PIN"
    override val setNewPinTitle = "Set new PIN"
    override val confirmPinTitle = "Confirm PIN"
    override val enterCurrentPinSubtitle = "Enter your current PIN to continue."
    override fun setPinSubtitle(min: Int, max: Int) = "Set a $min–$max digit PIN."
    override val confirmPinSubtitle = "Enter the same PIN again."
    override val wrongCurrentPin = "Current PIN is incorrect"
    override val pinMismatch = "PINs do not match"
    override val pinChangeFailed = "Failed to change PIN"
    override val confirmAction = "Confirm"
    override val backspaceAction = "Backspace"

    override val appTitle = "Stade"
    override val searchContactsPlaceholder = "Search contacts…"
    override val closeSearch = "Close search"
    override val searchAction = "Search"
    override val settingsAction = "Settings"
    override val addContactAction = "Add contact"
    override val noContactsTitle = "No contacts yet"
    override val noContactsHint = "Tap the button in the bottom right to add a new contact."
    override val noSearchResults = "No matching contacts"
    override val showVerificationCode = "Show verification code"
    override val deleteContact = "Delete contact"
    override val noMessages = "No messages yet"
    override fun deleteContactTitle(name: String) = "Delete \"$name\"?"
    override val deleteContactBody =
        "All messages, pending records, and encryption keys will be permanently deleted. " +
        "Both parties must delete and re-add each other to chat again.\n\nThis action cannot be undone."

    override val online = "online"
    override val offline = "offline"
    override val deleteContactDialogTitle = "Delete contact?"
    override fun deleteContactDialogBody(name: String) =
        "\"$name\" contact, all messages, pending queue entries and encryption keys (ratchet) will be " +
        "completely deleted. Both parties must delete and re-add each other to chat again.\n\nThis action cannot be undone."
    override val noMessagesYet = "No messages yet"
    override val sendFirstMessage = "Send the first message."
    override val typeMessagePlaceholder = "Message…"
    override val sendButton = "Send"
    override val verifyAction = "Verify"
    override val deleteContactIconDescription = "Delete contact"
    override val connectionFailed = "Connection failed"
    override val collapseAction = "Collapse"
    override val expandAction = "Expand"
    override val noConnectionInfo =
        "No connection info for this contact. Request a new invite code and paste below."
    override val connectionChannels = "Connection channels"
    override val trying = "trying"
    override val channelReadyVerifying = "channel ready, verifying…"
    override val connectedLabel = "connected"
    override val unreachable = "unreachable"
    override val handshakeFailed = "handshake failed"
    override val notYetTried = "not yet tried"
    override fun channelLabel(index: Int, maskedAddr: String) = "Channel #$index • $maskedAddr"
    override val connectionDelayNote =
        "For remote connections, it may take a few minutes for both parties to be online and network channels to be ready."
    override val newInviteCodeLabel = "New invite code"
    override val applyInviteCode = "Apply invite code"
    override val clearAddresses = "Clear"
    override fun handshakeRejected(reason: String) = "Connection rejected: $reason"
    override val contactConnected = "Connected ✓"
    override val decryptFailed =
        "Message could not be decrypted — delete and re-add the contact on both sides"
    override fun sendFailed(reason: String) = "Failed to send message: $reason"
    override val invalidInvite = "Invalid invite code"
    override val inviteBelongsToDifferent = "This invite belongs to a different contact"
    override val noConnectionInInvite = "No connection info in invite"
    override val connectionInfoUpdated = "Connection info updated"
    override val addressesCleared = "Addresses cleared"
    override fun diagnosticError(msg: String) = "Error: $msg"

    override val dialQueued = "queued…"
    override val dialTransportNotReady = "transport not ready — waiting"
    override fun dialTransportStarting(msg: String) = "starting transport ($msg)…"
    override fun dialConnectingVia(transport: String, attempt: Int) =
        "connecting via $transport (attempt #$attempt)…"
    override fun dialConnectFailedRetry(err: String) = "$err — will retry"
    override val dialHandshaking = "handshaking…"
    override val dialConnectedOk = "connected ✓"
    override val dialHandshakeFailedRetry = "handshake failed — will retry"
    override val dialOwnStaleAddress = "this is your own address (stale) — needs updating"
    override val dialTransportClosed = "transport off"
    override val dialUnreachableTimeout = "unreachable / timed out"
    override val dialHandshakeFailed = "handshake failed"

    override fun hsProtocolMismatch(peer: Int, app: Int) =
        "Protocol mismatch: v$peer (app v$app)"
    override val hsKeySizeBad = "Key sizes are invalid"
    override val hsSelfConnected = "Connected to yourself (stale address)"
    override val hsStadeIdMismatch = "Stade ID does not match the keys"
    override val hsTranscriptMismatch = "Transcript commitment mismatch (downgrade?)"
    override val hsAuthStadeIdMismatch = "AUTH Stade ID does not match HELLO"
    override val hsSignaturesInvalid = "Signatures could not be verified"
    override val hsEdInvalid = "Ed25519 signature invalid"
    override val hsMldsaInvalid = "ML-DSA signature invalid (post-quantum verification failed)"
    override val hsMlkemDecapFailed = "ML-KEM decapsulation failed"
    override val unknownNickname = "Unknown"
    override fun contactNameFallback(last4: String) = "Contact-$last4"

    override val backgroundRunningNotice = "The app keeps running in the background"
    override val trayOpen = "Open"
    override val trayExit = "Exit"

    override val copiedLabel = "Copied"
    override fun addrRemoteNetwork(first: String, last: String) = "Remote network • $first…$last"
    override val addrLocalNetwork = "Local network"
    override val addrNetwork = "Network"
    override fun timeYesterday(time: String) = "yesterday $time"

    override val saveMediaDialogTitle = "Save media"

    override val vaultMetaUnreadable = "Could not read metadata"
    override val vaultKeyDerivationFailed = "Could not derive key"
    override val vaultDekDecryptFailed = "Could not decrypt key"

    override val notifConnectionChannelName = "Connectivity"
    override val notifConnectionChannelDesc = "Stade keeps peer connections alive"
    override val notifMessagesChannelName = "Messages"
    override val notifMessagesChannelDesc = "Notifications for incoming encrypted messages"
    override val notifRunningTitle = "Stade is running"
    override val notifRunningText = "Peer-to-peer connections active"
    override fun notifNewMessages(count: Int) = "You have $count new messages"
    override val notifNewMessageFallback = "New message"

    override val addContactTitle = "Add contact"
    override val step1Title = "Share your own invite"
    override val step1Description =
        "Press the button below and share the invite code to establish a connection. " +
        "Stade ID is only an identity label — it doesn't replace the invite code."
    override fun copyInviteCode(length: Int) = "Copy invite code ($length characters)"
    override fun inviteCodeCopied(length: Int) =
        "Invite code copied ($length characters) — send to the other party"
    override val yourStadeId = "Your Stade ID:"
    override val step2Title = "Enter the other party's invite"
    override val inviteCodeLabel = "Invite code"
    override fun charCount(n: Int) = "$n characters (a valid invite should be ~10500 characters)"
    override val contactNameLabel = "Name for this contact"
    override val acceptInvite = "Accept invite"
    override val pendingInviteOpened =
        "Invite file opened — give this person a name and tap \"Accept invite\""
    override val inviteCodeIsStadeId =
        "This is a Stade ID (identity label), not an invite code. " +
        "The other party should tap 'Copy invite code' and send you the long block (STADE2-… ~10500 characters)."
    override fun inviteMissingPrefix(first: String) =
        "Invite does not start with 'STADE2-' (first: '$first')"
    override fun inviteTooShort(actual: Int, expected: Int) =
        "Invite code incomplete: $actual bytes, $expected required — some characters may have been lost during copy"
    override fun inviteTrailingBytes(extra: Int) =
        "Invite code has $extra extra bytes — may have been pasted twice"
    override val inviteBadMagic = "Invite code not in Stade format (magic mismatch)"
    override fun inviteBadVersion(version: Int) = "Invite code version not supported (v=$version)"
    override fun inviteBadNickname(length: Int) = "Invite code corrupted (nickname length=$length)"
    override fun inviteBadAddressBlob(length: Int) =
        "Invite code corrupted (address blob length=$length)"
    override val inviteEdVerifyFail =
        "Invite code signature invalid (Ed25519) — may have been corrupted during copy"
    override val inviteMlDsaVerifyFail =
        "Invite code PQ signature invalid (ML-DSA) — may have been corrupted during copy"
    override fun inviteDecodeError(cause: String) = "Base32 decode error: $cause"
    override val selfInviteError = "This is your own invite"
    override fun alreadyAdded(stadeId: String) = "This contact is already added ($stadeId)"
    override val inviteAcceptedNoAddr =
        "Invite accepted — waiting for the other party to come online"
    override fun inviteAccepted(name: String, count: Int) =
        "Invite accepted ($name) — connecting… ($count addresses trying; other party must be online)"
    override fun contactAdded(name: String) = "✓ $name added — you can start chatting"
    override val connectionTimeout =
        "Could not connect — the other party may be offline or Tor/network unavailable. " +
        "Leave the app open; the contact will be added automatically when a connection is established."
    override val torStartingInviteHint =
        "Tor is still starting — wait until it is fully ready before sharing this invite code."
    override val inviteLanOnlyWarning =
        "This invite only carries a local-network (Wi-Fi) address — it can only connect on the same network. " +
        "To reach someone remotely, they must share an invite created after Tor is ready."
    override val inviteNotReadyForRemote =
        "Tor is still starting, so your invite is not reachable remotely yet. " +
        "Wait until it is ready, otherwise the other person can only connect on the same Wi-Fi."
    override val addContactDialogTitle = "Add contact?"
    override fun addContactQuestion(name: String) = "Do you want to add \"$name\"?"
    override val incomingInviteMessage = "You are about to add this user."
    override val confirmAddCheckbox = "I confirm I want to add this contact"
    override val addAction = "Add"
    override val notNowAction = "Not now"
    override fun connectingInBackground(name: String) =
        "Connecting to $name… you can close this; the contact appears once connected."
    override fun error(msg: String) = "Error: $msg"

    override val settingsTitle = "Settings"
    override val identitySection = "Identity"
    override val appearanceSection = "Appearance"
    override val dynamicColorTitle = "Dynamic color"
    override val dynamicColorSubtitle = "Use Material You wallpaper colors"
    override val notificationsSection = "Notifications"
    override val messageNotificationsTitle = "Message notifications"
    override val notificationsOnSubtitle = "Send notifications for new messages"
    override val notificationsOffSubtitle = "Notifications are off"
    override val hideNotificationTitle = "Hide notification content"
    override val hiddenNotificationSubtitle = "Shown as \"You have X new messages\""
    override val visibleNotificationSubtitle = "Sender name and message preview visible"
    override val systemNotificationsTitle = "System notification settings"
    override val systemNotificationsSubtitle = "Sound, vibration, and channel settings"
    override val runInBackgroundTitle = "Run in background"
    override val runInBackgroundOnSubtitle = "App stays in the tray when window is closed"
    override val runInBackgroundOffSubtitle = "Closing the window quits the app"
    override val networkSection = "Network"
    override val transportLayersTitle = "Transport layers"
    override val transportLayersSubtitle = "LAN, Tor, and other network settings"
    override val securitySection = "Security"
    override val securitySettingsTitle = "Security settings"
    override val securitySettingsSubtitle = "PIN, auto-lock, and more."
    override val aboutSection = "About"
    override val aboutTitle = "About"
    override val aboutSubtitle = "App info and social media"
    override val aboutAppDescription = "End-to-end encrypted, private messaging."
    override val aboutFollowUs = "Follow us"
    override val aboutLinkComingSoon = "Coming soon"
    override val aboutVersionLabel = "Version"
    override val accountSection = "Account"
    override val logoutTitle = "Sign out"
    override val logoutSubtitle = "Local data is preserved"
    override val localIdentity = "Local identity"
    override val fingerprintLabel = "Identity fingerprint"
    override val fingerprintCopied = "Copied!"
    override val copyButton = "Copy"
    override val logoutDialogTitle = "Sign out"
    override val logoutDialogBody =
        "Your identity, contacts, chat history, and transport settings on this device will be " +
        "permanently deleted. This action cannot be undone."
    override val deleteAndLogout = "Delete and sign out"
    override val languageSection = "Language"
    override val languageTitle = "Language"
    override val languageSubtitle = "English"

    override val pinSection = "PIN"
    override val changePinTitle = "Change PIN"
    override val changePinSubtitle = "Verify your current PIN and set a new one"
    override val scrambleKeypadTitle = "Scramble keypad"
    override val scrambleKeypadOnSubtitle = "Digits are shuffled randomly on each entry"
    override val scrambleKeypadOffSubtitle = "Digits shown in standard order"
    override val sessionSection = "Session"
    override val autoLockTitle = "Auto-lock"
    override fun autoLockSubtitle(label: String) = "After going to background: $label"
    override fun sessionTimeoutLabel(seconds: Int) = when (seconds) {
        SessionTimeout.IMMEDIATE -> "Immediately"
        SessionTimeout.NEVER -> "Never"
        30 -> "30 seconds"
        60 -> "1 minute"
        5 * 60 -> "5 minutes"
        15 * 60 -> "15 minutes"
        60 * 60 -> "1 hour"
        else -> if (seconds < 60) "$seconds seconds" else "${seconds / 60} minutes"
    }

    override val privacySection = "Privacy"
    override val screenshotBlockingTitle = "Disable screenshot capture"
    override val screenshotBlockingOnSubtitle = "App content hidden in recent apps; screenshots disabled"
    override val screenshotBlockingOffSubtitle = "App content visible in recent apps"

    override val autoLockNeverInfoTitle = "About the 'Never' Option"
    override val autoLockNeverInfoBody =
        "When set to 'Never', no PIN is required when returning to the app from the background.\n\n" +
        "However, if the app is fully closed and restarted — even if cleared from memory by the system — you will still need to enter your PIN."
    override val understood = "Got it"

    override val transportsTitle = "Transport layers"
    override val notRegistered = "not registered"
    override fun transportRunning(msg: String) = "running · $msg"
    override val transportReady = "ready"
    override fun transportUnavailable(msg: String) = "unavailable · $msg"
    override fun transportStatus(addr: String) = "Status: $addr ready"
    override fun transportChannelsReady(n: Int) = "$n channels ready"
    override val torBuiltinNote =
        "Tor is automatically installed and started. Wait for the installation to complete, " +
        "then use it seamlessly. This is a one-time setup."
    override val hiddenServiceDescription =
        "Hidden Service (advanced — optional). To establish a connection channel, you must configure " +
        "the appropriate settings in torrc and enter the resulting identity in the field below. " +
        "If left empty, this channel will be disabled."
    override val hiddenServiceId = "Hidden service identity"
    override val onionVirtport = "Port (onion VIRTPORT)"
    override val localPortLabel = "Local port (leave empty to use Port)"
    override val socks5Note =
        "SOCKS5 (for outgoing connections). Standard Tor: 9050. Tor Browser: 9150. Orbot: 9050. " +
        "If this doesn't work, Tor is not installed or listening on a different port."
    override val socks5Host = "SOCKS5 host"
    override val socks5Port = "SOCKS5 port"
    override val saveAndRestart = "Save and restart channel"
    override val lanLabel = "Local network"
    override val torLabel = "Remote network channel"

    override val verifyContactTitle = "Verify contact"
    override val safetyNumber = "Safety number"
    override val safetyNumberNote =
        "Compare this number in person or through another secure channel."
    override val markAsVerified = "Mark as verified"
    override val alreadyVerifiedLabel = "Verified ✓"
    override val verifiedLabel = "Verified"
    override val contactStadeId = "Other party's Stade ID"

    override val selectContactHint = "Select a contact from the left panel to start a new conversation."

    override val attachPhoto = "Attach photo"
    override val selectMediaTitle = "Select Media"
    override val photoMessage = "📷 Photo"
    override val photoSendFailed = "Failed to load photo"
    override val photoTooBig = "Photo is too large (max 3 MB)"
    override val tapToViewPhoto = "Tap to view"
    override val closePhoto = "Close"
    override val removeAttachment = "Remove"
    override fun attachmentCount(count: Int) = if (count == 1) "1 photo attached" else "$count photos attached"

    override val recordVoice = "Record voice message"
    override val stopRecording = "Stop recording"
    override val voiceMessage = "🎤 Voice message"
    override val voiceSendFailed = "Failed to send voice message"
    override val micPermissionDenied = "Microphone permission is required to record voice messages"
    override val voiceMaxDurationReached = "Maximum voice message length reached"

    override val createGroupTitle = "Create Group"
    override val createGroupAction = "Create Group"
    override val groupNameLabel = "Group name"
    override val selectMembersHint = "Select members to add to the group:"
    override val groupInviteTitle = "Group Invite Link"
    override val groupInviteBody = "Share this link with someone to invite them to the group. They must already be your contact."
    override val copyInviteLink = "Copy Link"
    override val deleteGroupTitle = "Delete Group"
    override val deleteGroupBody = "All group messages and member records will be permanently deleted. This action cannot be undone."
    override val groupGenerateInvite = "Generate invite link"
    override fun groupMemberCount(count: Int) = "$count members"

    override val addMembersTitle = "Add people to group"
    override val addMembersAction = "Add"
    override val addMembersHint = "Select contacts to add:"
    override val noContactsToAdd = "No contacts available to add. All of your contacts seem to already be in this group."
    override fun membersAdded(count: Int) = if (count == 1) "1 person invited" else "$count people invited"
    override val leaveGroupAction = "Leave group"
    override val leaveGroupTitle = "Leave group?"
    override val leaveGroupBody = "This group will be removed from your local device. Other members will remain. This action cannot be undone."
    override val leaveAction = "Leave"

    override val copyMessage = "Copy message"
    override val deleteMessageForMe = "Delete for me"
    override val deleteMessagesForMe = "Delete selected messages"
    override fun selectedCount(count: Int) = "$count selected"
    override val messageCopied = "Message copied"
    override val cancelSelection = "Cancel selection"

    override val saveImageAction = "Save"
    override val copyImageAction = "Copy"
    override val imageSaved = "Image saved"
    override val imageSaveFailed = "Failed to save image"
    override val imageCopied = "Image copied"
    override val imageCopyFailed = "Failed to copy image"
}

