package dev.stade.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import dev.stade.link.findLinks

@Composable
fun LinkifiedText(
    text: String,
    color: Color,
    linkColor: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    textAlign: TextAlign? = null
) {
    val annotated = remember(text, linkColor) { buildLinkedString(text, linkColor) }
    if (annotated == null) {
        Text(text, color = color, style = style, textAlign = textAlign, modifier = modifier)
    } else {
        Text(annotated, color = color, style = style, textAlign = textAlign, modifier = modifier)
    }
}

private fun buildLinkedString(text: String, linkColor: Color): AnnotatedString? {
    val spans = findLinks(text)
    if (spans.isEmpty()) return null
    val styles = TextLinkStyles(
        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
    )
    return buildAnnotatedString {
        var cursor = 0
        for (span in spans) {
            if (span.start > cursor) append(text.substring(cursor, span.start))
            withLink(LinkAnnotation.Url(span.url, styles = styles)) {
                append(text.substring(span.start, span.end))
            }
            cursor = span.end
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}
