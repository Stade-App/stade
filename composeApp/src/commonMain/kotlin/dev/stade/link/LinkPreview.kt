package dev.stade.link

import dev.stade.AppContainer

data class LinkPreview(val url: String, val title: String, val description: String)

data class LinkSpan(val start: Int, val end: Int, val url: String)

private val URL_REGEX = Regex("""(?:https?://|www\.)[^\s<>"]+""", RegexOption.IGNORE_CASE)

private const val TRAILING_TRIM = ".,;:!?’'\""

fun extractFirstUrl(text: String): String? = findLinks(text).firstOrNull()?.url

fun findLinks(text: String): List<LinkSpan> {
    if (text.isEmpty()) return emptyList()
    val out = mutableListOf<LinkSpan>()
    for (match in URL_REGEX.findAll(text)) {
        var end = match.range.last + 1
        val start = match.range.first
        while (end > start && shouldTrim(text, start, end - 1)) end--
        if (end - start < 4) continue
        val raw = text.substring(start, end)
        val normalized = if (raw.startsWith("www.", ignoreCase = true)) "https://$raw" else raw
        out += LinkSpan(start, end, normalized)
    }
    return out
}

private fun shouldTrim(text: String, start: Int, index: Int): Boolean {
    val c = text[index]
    if (c in TRAILING_TRIM) return true
    if (c == ')') return text.substring(start, index).count { it == '(' } <= text.substring(start, index).count { it == ')' }
    if (c == ']') return text.substring(start, index).count { it == '[' } <= text.substring(start, index).count { it == ']' }
    return false
}

expect suspend fun fetchLinkPreview(url: String, container: AppContainer): LinkPreview?
