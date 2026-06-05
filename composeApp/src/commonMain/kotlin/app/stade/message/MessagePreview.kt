package app.stade.message

private const val PREVIEW_MAX = 120

fun previewBody(body: String, photoLabel: String): String {
    if (body.startsWith(IMAGE_BODY_PREFIX)) return photoLabel
    val firstLine = body.lineSequence().firstOrNull() ?: return ""
    return if (firstLine.length > PREVIEW_MAX) firstLine.substring(0, PREVIEW_MAX) else firstLine
}

