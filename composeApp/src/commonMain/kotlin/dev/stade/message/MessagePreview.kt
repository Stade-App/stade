package dev.stade.message

private const val PREVIEW_MAX = 120

fun previewBody(body: String, photoLabel: String, voiceLabel: String? = null): String {
    if (body.startsWith(IMAGE_BODY_PREFIX)) return photoLabel
    if (voiceLabel != null && body.startsWith(VOICE_BODY_PREFIX)) return voiceLabel
    val firstLine = body.lineSequence().firstOrNull() ?: return ""
    return if (firstLine.length > PREVIEW_MAX) firstLine.substring(0, PREVIEW_MAX) else firstLine
}

