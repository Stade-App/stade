package dev.stade.link

import dev.stade.AppContainer

data class LinkPreview(val url: String, val title: String, val description: String)

private val URL_REGEX = Regex("""https?://[^\s]+""")

fun extractFirstUrl(text: String): String? = URL_REGEX.find(text)?.value

expect suspend fun fetchLinkPreview(url: String, container: AppContainer): LinkPreview?
