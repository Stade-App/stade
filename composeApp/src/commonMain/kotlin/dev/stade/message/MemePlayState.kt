package dev.stade.message

import dev.stade.db.StadeDb

private fun playedKey(messageId: String) = "memeplayed:$messageId"

fun memeAlreadyPlayed(db: StadeDb, messageId: String): Boolean {
    if (messageId.isBlank()) return true
    return runCatching {
        db.stadeDbQueries.getKv(playedKey(messageId)).executeAsOneOrNull()
    }.getOrNull() != null
}

fun markMemePlayed(db: StadeDb, messageId: String) {
    if (messageId.isBlank()) return
    runCatching { db.stadeDbQueries.putKv(playedKey(messageId), ByteArray(1)) }
}
