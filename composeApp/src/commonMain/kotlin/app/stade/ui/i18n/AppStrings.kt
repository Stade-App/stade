package app.stade.ui.i18n

import androidx.compose.runtime.compositionLocalOf

abstract class AppStrings {
    abstract val back: String
    abstract val cancel: String
    abstract val delete: String

    abstract val loading: String
    abstract val welcomeTitle: String
    abstract val welcomeDescription: String
    abstract val nicknamePlaceholder: String
    abstract val createIdentity: String

    abstract val unlockTitle: String
    abstract val unlockSubtitle: String
    abstract val tooManyAttemptsSubtitle: String
    abstract val forgotPin: String
    abstract val resetPinTitle: String
    abstract val resetPinBody: String
    abstract val resetAndWipe: String
    abstract val vaultNotInitialized: String
    abstract fun wrongPinRemaining(remaining: Int): String
    abstract val wrongPin: String
    abstract val wiping: String
    abstract fun retryIn(formattedTime: String): String
    abstract fun formatRemainingTime(seconds: Long): String

    abstract val enterCurrentPinTitle: String
    abstract val setNewPinTitle: String
    abstract val confirmPinTitle: String
    abstract val enterCurrentPinSubtitle: String
    abstract fun setPinSubtitle(min: Int, max: Int): String
    abstract val confirmPinSubtitle: String
    abstract val wrongCurrentPin: String
    abstract val pinMismatch: String
    abstract val pinChangeFailed: String
    abstract val confirmAction: String
    abstract val backspaceAction: String

    abstract val appTitle: String
    abstract val searchContactsPlaceholder: String
    abstract val closeSearch: String
    abstract val searchAction: String
    abstract val settingsAction: String
    abstract val addContactAction: String
    abstract val noContactsTitle: String
    abstract val noContactsHint: String
    abstract val noSearchResults: String
    abstract val showVerificationCode: String
    abstract val deleteContact: String
    abstract val noMessages: String
    abstract fun deleteContactTitle(name: String): String
    abstract val deleteContactBody: String

    abstract val online: String
    abstract val offline: String
    abstract val deleteContactDialogTitle: String
    abstract fun deleteContactDialogBody(name: String): String
    abstract val noMessagesYet: String
    abstract val sendFirstMessage: String
    abstract val typeMessagePlaceholder: String
    abstract val sendButton: String
    abstract val verifyAction: String
    abstract val deleteContactIconDescription: String
    abstract val connectionFailed: String
    abstract val collapseAction: String
    abstract val expandAction: String
    abstract val noConnectionInfo: String
    abstract val connectionChannels: String
    abstract val trying: String
    abstract val channelReadyVerifying: String
    abstract val connectedLabel: String
    abstract val unreachable: String
    abstract val handshakeFailed: String
    abstract val notYetTried: String
    abstract fun channelLabel(index: Int, maskedAddr: String): String
    abstract val connectionDelayNote: String
    abstract val newInviteCodeLabel: String
    abstract val applyInviteCode: String
    abstract val clearAddresses: String
    abstract fun handshakeRejected(reason: String): String
    abstract val contactConnected: String
    abstract val decryptFailed: String
    abstract fun sendFailed(reason: String): String
    abstract val invalidInvite: String
    abstract val inviteBelongsToDifferent: String
    abstract val noConnectionInInvite: String
    abstract val connectionInfoUpdated: String
    abstract val addressesCleared: String
    abstract fun diagnosticError(msg: String): String

    abstract val addContactTitle: String
    abstract val step1Title: String
    abstract val step1Description: String
    abstract fun copyInviteCode(length: Int): String
    abstract fun inviteCodeCopied(length: Int): String
    abstract val shareAsFile: String
    abstract val yourStadeId: String
    abstract val step2Title: String
    abstract val inviteCodeLabel: String
    abstract fun charCount(n: Int): String
    abstract val contactNameLabel: String
    abstract val acceptInvite: String
    abstract val pendingInviteOpened: String
    abstract val inviteCodeIsStadeId: String
    abstract fun inviteMissingPrefix(first: String): String
    abstract fun inviteTooShort(actual: Int, expected: Int): String
    abstract fun inviteTrailingBytes(extra: Int): String
    abstract val inviteBadMagic: String
    abstract fun inviteBadVersion(version: Int): String
    abstract fun inviteBadNickname(length: Int): String
    abstract fun inviteBadAddressBlob(length: Int): String
    abstract val inviteEdVerifyFail: String
    abstract val inviteMlDsaVerifyFail: String
    abstract fun inviteDecodeError(cause: String): String
    abstract val selfInviteError: String
    abstract fun alreadyAdded(stadeId: String): String
    abstract val inviteAcceptedNoAddr: String
    abstract fun inviteAccepted(name: String, count: Int): String
    abstract fun contactAdded(name: String): String
    abstract val connectionTimeout: String
    abstract val torStartingInviteHint: String
    abstract fun error(msg: String): String

    abstract val settingsTitle: String
    abstract val identitySection: String
    abstract val appearanceSection: String
    abstract val dynamicColorTitle: String
    abstract val dynamicColorSubtitle: String
    abstract val notificationsSection: String
    abstract val messageNotificationsTitle: String
    abstract val notificationsOnSubtitle: String
    abstract val notificationsOffSubtitle: String
    abstract val hideNotificationTitle: String
    abstract val hiddenNotificationSubtitle: String
    abstract val visibleNotificationSubtitle: String
    abstract val systemNotificationsTitle: String
    abstract val systemNotificationsSubtitle: String
    abstract val runInBackgroundTitle: String
    abstract val runInBackgroundOnSubtitle: String
    abstract val runInBackgroundOffSubtitle: String
    abstract val networkSection: String
    abstract val transportLayersTitle: String
    abstract val transportLayersSubtitle: String
    abstract val securitySection: String
    abstract val securitySettingsTitle: String
    abstract val securitySettingsSubtitle: String
    abstract val accountSection: String
    abstract val logoutTitle: String
    abstract val logoutSubtitle: String
    abstract val localIdentity: String
    abstract val fingerprintLabel: String
    abstract val fingerprintCopied: String
    abstract val copyButton: String
    abstract val logoutDialogTitle: String
    abstract val logoutDialogBody: String
    abstract val deleteAndLogout: String
    abstract val languageSection: String
    abstract val languageTitle: String
    abstract val languageSubtitle: String

    abstract val pinSection: String
    abstract val changePinTitle: String
    abstract val changePinSubtitle: String
    abstract val scrambleKeypadTitle: String
    abstract val scrambleKeypadOnSubtitle: String
    abstract val scrambleKeypadOffSubtitle: String
    abstract val sessionSection: String
    abstract val autoLockTitle: String
    abstract fun autoLockSubtitle(label: String): String
    abstract fun sessionTimeoutLabel(seconds: Int): String
    abstract val autoLockNeverInfoTitle: String
    abstract val autoLockNeverInfoBody: String
    abstract val understood: String

    abstract val privacySection: String
    abstract val screenshotBlockingTitle: String
    abstract val screenshotBlockingOnSubtitle: String
    abstract val screenshotBlockingOffSubtitle: String

    abstract val transportsTitle: String
    abstract val notRegistered: String
    abstract fun transportRunning(msg: String): String
    abstract val transportReady: String
    abstract fun transportUnavailable(msg: String): String
    abstract fun transportStatus(addr: String): String
    abstract fun transportChannelsReady(n: Int): String
    abstract val torBuiltinNote: String
    abstract val hiddenServiceDescription: String
    abstract val hiddenServiceId: String
    abstract val onionVirtport: String
    abstract val localPortLabel: String
    abstract val socks5Note: String
    abstract val socks5Host: String
    abstract val socks5Port: String
    abstract val saveAndRestart: String
    abstract val lanLabel: String
    abstract val torLabel: String
    abstract val bluetoothLabel: String
    abstract val removableLabel: String

    abstract val verifyContactTitle: String
    abstract val safetyNumber: String
    abstract val safetyNumberNote: String
    abstract val markAsVerified: String
    abstract val alreadyVerifiedLabel: String
    abstract val verifiedLabel: String
    abstract val contactStadeId: String

    abstract val selectContactHint: String
    abstract val attachPhoto: String
    abstract val photoMessage: String
    abstract val photoSendFailed: String
    abstract val photoTooBig: String
    abstract val tapToViewPhoto: String
    abstract val closePhoto: String
    abstract val removeAttachment: String
    abstract fun attachmentCount(count: Int): String

    // Group chat
    abstract val createGroupTitle: String
    abstract val createGroupAction: String
    abstract val groupNameLabel: String
    abstract val selectMembersHint: String
    abstract val groupInviteTitle: String
    abstract val groupInviteBody: String
    abstract val copyInviteLink: String
    abstract val deleteGroupTitle: String
    abstract val deleteGroupBody: String
    abstract val groupGenerateInvite: String
    abstract fun groupMemberCount(count: Int): String
}

val LocalStrings = compositionLocalOf<AppStrings> { EnglishStrings }

