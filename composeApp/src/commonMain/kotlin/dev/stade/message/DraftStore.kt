package dev.stade.message

import dev.stade.db.StadeDb

enum class DraftScope(val prefix: String) {
    DIRECT("c"),
    GROUP("g"),
    STADIUM("s")
}

private const val MAX_DRAFT_CHARS = 20_000

private fun draftKey(scope: DraftScope, id: String) = "draft:${scope.prefix}:$id"

fun loadDraft(db: StadeDb, scope: DraftScope, id: String): String {
    if (id.isBlank()) return ""
    return runCatching {
        db.stadeDbQueries.getKv(draftKey(scope, id)).executeAsOneOrNull()
    }.getOrNull()?.decodeToString().orEmpty()
}

fun saveDraft(db: StadeDb, scope: DraftScope, id: String, text: String) {
    if (id.isBlank()) return
    val key = draftKey(scope, id)
    runCatching {
        if (text.isBlank()) {
            db.stadeDbQueries.deleteKv(key)
        } else {
            db.stadeDbQueries.putKv(key, text.take(MAX_DRAFT_CHARS).encodeToByteArray())
        }
    }
}

fun clearDraft(db: StadeDb, scope: DraftScope, id: String) {
    if (id.isBlank()) return
    runCatching { db.stadeDbQueries.deleteKv(draftKey(scope, id)) }
}
