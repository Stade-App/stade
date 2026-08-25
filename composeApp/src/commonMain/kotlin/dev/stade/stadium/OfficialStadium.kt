package dev.stade.stadium

import dev.stade.AppContainer
import dev.stade.contact.InviteParseResult
import dev.stade.crypto.Encoding
import dev.stade.identity.LocalIdentity

object OfficialConfig {
    const val STADIUM_INVITE_CODE: String = ""

    val creatorStadeId: String? by lazy {
        val idx = STADIUM_INVITE_CODE.indexOf(STADIUM_INVITE_SUFFIX_MARKER)
        if (idx < 0) return@lazy null
        val b32 = STADIUM_INVITE_CODE.substring(idx + STADIUM_INVITE_SUFFIX_MARKER.length)
        val bytes = runCatching { Encoding.fromBase32(b32.trim()) }.getOrNull() ?: return@lazy null
        bytes.decodeToString().split(Char(0x1f).toString()).getOrNull(3)
    }
}

val StadiumInfo.isOfficial: Boolean
    get() = creatorStadeId.isNotEmpty() && creatorStadeId == OfficialConfig.creatorStadeId

suspend fun joinOfficialStadiumIfNeeded(container: AppContainer, owner: LocalIdentity) {
    val code = OfficialConfig.STADIUM_INVITE_CODE
    if (code.isBlank()) return

    val split = container.stadiums.splitInviteLink(code) ?: return
    val (handshakePart, stadiumDataPart) = split
    val stadiumData = container.stadiums.parseStadiumData(stadiumDataPart) ?: return

    if (container.stadiums.allStadiums(owner.id).any { it.id == stadiumData.stadiumId }) return

    val payload = (container.handshake.parseInviteDetailed(handshakePart) as? InviteParseResult.Ok)?.payload ?: return
    if (payload.signingPublicKey.contentEquals(owner.publicSigningKey)) return
    val addrs = payload.addresses
    if (addrs.isEmpty()) return

    container.sync.unforget(payload.stadeId)
    val pending = PendingStadiumJoin(stadiumData.stadiumId, stadiumData.stadiumName, stadiumData.inviteToken)
    container.stadiums.storePendingJoin(payload.stadeId, pending)

    val existingContact = container.contacts.findByStadeId(payload.stadeId)
    if (existingContact != null) {
        container.stadiumChat.sendJoinRequest(owner, existingContact.id, pending)
    } else {
        container.connections.queueDial(addrs)
    }
}
