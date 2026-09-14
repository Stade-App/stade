package dev.stade.link

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinkParseTest {

    @Test
    fun aPlainHttpsLinkIsFound() {
        val spans = findLinks("check https://stade.dev out")
        assertEquals(1, spans.size)
        assertEquals("https://stade.dev", spans[0].url)
    }

    @Test
    fun aBareWwwLinkGetsAScheme() {
        val spans = findLinks("go to www.stade.dev now")
        assertEquals(1, spans.size)
        assertEquals("https://www.stade.dev", spans[0].url)
        assertEquals("www.stade.dev", "go to www.stade.dev now".substring(spans[0].start, spans[0].end))
    }

    @Test
    fun trailingSentencePunctuationIsNotPartOfTheLink() {
        for (suffix in listOf(".", ",", "!", "?", ";", ":")) {
            val spans = findLinks("see https://stade.dev$suffix")
            assertEquals("https://stade.dev", spans.single().url, "suffix $suffix leaked into the url")
        }
    }

    @Test
    fun aBalancedParenthesisInsideTheUrlSurvives() {
        val spans = findLinks("https://en.wikipedia.org/wiki/Foo_(bar)")
        assertEquals("https://en.wikipedia.org/wiki/Foo_(bar)", spans.single().url)
    }

    @Test
    fun aWrappingParenthesisIsExcluded() {
        val spans = findLinks("(https://stade.dev)")
        assertEquals("https://stade.dev", spans.single().url)
    }

    @Test
    fun severalLinksInOneMessageAreAllFound() {
        val text = "https://a.example and www.b.example plus http://c.example/x"
        val spans = findLinks(text)
        assertEquals(3, spans.size)
        assertEquals(listOf("https://a.example", "https://www.b.example", "http://c.example/x"), spans.map { it.url })
        assertTrue(spans.zipWithNext().all { (a, b) -> a.end <= b.start }, "spans must not overlap")
    }

    @Test
    fun spansPointAtTheRightSliceOfTheOriginalText() {
        val text = "before https://stade.dev/path?q=1 after"
        val span = findLinks(text).single()
        assertEquals("https://stade.dev/path?q=1", text.substring(span.start, span.end))
    }

    @Test
    fun textWithoutLinksYieldsNothing() {
        for (t in listOf("", "hello world", "not.a.link here", "3.14", "a@b.example")) {
            assertEquals(emptyList(), findLinks(t), "unexpected link in: $t")
        }
    }

    @Test
    fun extractFirstUrlAgreesWithFindLinks() {
        val text = "one https://a.example two https://b.example"
        assertEquals(findLinks(text).first().url, extractFirstUrl(text))
        assertEquals(null, extractFirstUrl("nothing here"))
    }

    @Test
    fun randomTextNeverThrowsAndSpansStayInBounds() {
        val random = Random(1337)
        val alphabet = "abc:/.?=&()[]<>\"' \nhttpswww"
        repeat(3000) {
            val text = (0 until random.nextInt(0, 60)).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
            for (span in findLinks(text)) {
                assertTrue(span.start in 0..text.length, "start out of bounds in: $text")
                assertTrue(span.end in span.start..text.length, "end out of bounds in: $text")
            }
        }
    }
}
