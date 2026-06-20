package dev.stade.ui

import dev.stade.AppContainer
import dev.stade.contact.InviteParseResult
import dev.stade.contact.InvitePayload
import dev.stade.identity.LocalIdentity
import dev.stade.ui.i18n.AppStrings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private val STADE_ID_REGEX = Regex("^STADE-[0-9A-Za-z]{4}-[0-9A-Za-z]{4}-[0-9A-Za-z]{4}$")

fun inviteErrorText(result: InviteParseResult, strings: AppStrings): String? = when (result) {
    is InviteParseResult.Ok -> null
    is InviteParseResult.MissingPrefix -> strings.inviteMissingPrefix(result.firstChars)
    is InviteParseResult.TooShort -> strings.inviteTooShort(result.actual, result.expected)
    is InviteParseResult.TrailingBytes -> strings.inviteTrailingBytes(result.extra)
    InviteParseResult.BadMagic -> strings.inviteBadMagic
    is InviteParseResult.BadVersion -> strings.inviteBadVersion(result.version)
    is InviteParseResult.BadNickname -> strings.inviteBadNickname(result.length)
    is InviteParseResult.BadAddressBlob -> strings.inviteBadAddressBlob(result.length)
    InviteParseResult.EdVerifyFail -> strings.inviteEdVerifyFail
    InviteParseResult.MlDsaVerifyFail -> strings.inviteMlDsaVerifyFail
    is InviteParseResult.DecodeError -> strings.inviteDecodeError(result.cause)
}

sealed interface BeginAcceptResult {
    data class Error(val message: String) : BeginAcceptResult
    data class NoAddress(val payload: InvitePayload) : BeginAcceptResult
    data class Dialing(val payload: InvitePayload, val addressCount: Int, val lanOnly: Boolean) : BeginAcceptResult
}

fun AppContainer.beginAcceptInvite(
    owner: LocalIdentity,
    rawCode: String,
    alias: String,
    strings: AppStrings
): BeginAcceptResult {
    val trimmed = rawCode.trim()
    if (STADE_ID_REGEX.matches(trimmed.uppercase())) {
        return BeginAcceptResult.Error(strings.inviteCodeIsStadeId)
    }
    val parseResult = handshake.parseInviteDetailed(trimmed)
    val payload = (parseResult as? InviteParseResult.Ok)?.payload
        ?: return BeginAcceptResult.Error(inviteErrorText(parseResult, strings) ?: strings.invalidInvite)

    if (payload.signingPublicKey.contentEquals(owner.publicSigningKey)) {
        return BeginAcceptResult.Error(strings.selfInviteError)
    }
    if (contacts.findByStadeId(payload.stadeId) != null) {
        val a = alias.trim()
        if (a.isNotEmpty()) runCatching { contacts.rename(payload.stadeId, a) }
        return BeginAcceptResult.Error(strings.alreadyAdded(payload.stadeId))
    }

    val addrs = payload.addresses
    if (addrs.isEmpty()) return BeginAcceptResult.NoAddress(payload)

    val lanOnly = addrs.none { it.startsWith("tor://") }
    connections.queueDial(addrs)

    val a = alias.trim()
    val targetId = payload.stadeId
    appScope.launch {
        val added = withTimeoutOrNull(5 * 60_000L) {
            contacts.observeContacts(owner.id).first { list -> list.any { it.id == targetId } }
            true
        } ?: false
        if (added && a.isNotEmpty()) runCatching { contacts.rename(targetId, a) }
    }
    return BeginAcceptResult.Dialing(payload, addrs.size, lanOnly)
}
