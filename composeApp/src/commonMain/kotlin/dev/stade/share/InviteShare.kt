package dev.stade.share

expect object InviteShare {
    fun share(invite: String, ownerNickname: String): String
}

