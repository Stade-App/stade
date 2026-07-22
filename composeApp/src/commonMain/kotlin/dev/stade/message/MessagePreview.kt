package dev.stade.message

private const val PREVIEW_MAX = 120

fun previewBody(body: String, photoLabel: String, voiceLabel: String? = null): String {
    val effective = parseReplyWrapper(body)?.second ?: body
    if (effective.startsWith(IMAGE_BODY_PREFIX)) return photoLabel
    if (voiceLabel != null && effective.startsWith(VOICE_BODY_PREFIX)) return voiceLabel
    val firstLine = effective.lineSequence().firstOrNull() ?: return ""
    return if (firstLine.length > PREVIEW_MAX) firstLine.substring(0, PREVIEW_MAX) else firstLine
}

