package dev.stade.pad

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private fun parse(text: String): PadCatalog? =
    runCatching { json.decodeFromString(PadCatalog.serializer(), text) }.getOrNull()

private fun usable(catalog: PadCatalog): PadCatalog = PadCatalog(
    version = catalog.version,
    sounds = catalog.sounds.filter { it.hasIntegrity }
)

class PadCatalogTest {

    @Test
    fun aPlaceholderEntryDoesNotDiscardTheRestOfTheCatalog() {
        val live = """
            {
              "version": 1,
              "sounds": [
                {
                },
                {
                  "id": "alikoc",
                  "name": "Ali Koç Göğe Bakıyor",
                  "durationMs": 23000,
                  "sha256": "af3a952f158307e8d16cd5d6a5a085b924d64a85f34a08cf273d0bede7517e88",
                  "url": "https://stade.dev/pad/sounds/alikoc.wav"
                }
              ]
            }
        """.trimIndent()

        val parsed = assertNotNull(
            parse(live),
            "an empty placeholder object must not make the whole catalog unparseable"
        )
        val ready = usable(parsed)
        assertEquals(1, ready.sounds.size, "the valid sound entry should survive")
        assertEquals("alikoc", ready.sounds.first().id)
    }

    @Test
    fun entriesWithoutIntegrityAreDropped() {
        val text = """
            {
              "sounds": [
                {"id": "no-hash", "name": "No hash", "url": "https://stade.dev/a.wav"},
                {"id": "no-url", "name": "No url", "sha256": "${"a".repeat(64)}"},
                {"id": "ok", "name": "Fine", "sha256": "${"b".repeat(64)}", "url": "https://stade.dev/b.wav"}
              ]
            }
        """.trimIndent()
        val ready = usable(assertNotNull(parse(text)))
        assertEquals(listOf("ok"), ready.sounds.map { it.id })
    }

    @Test
    fun aShortHashIsRejected() {
        val text = """
            {"sounds": [{"id": "x", "name": "X", "sha256": "abc", "url": "https://stade.dev/x.wav"}]}
        """.trimIndent()
        assertTrue(usable(assertNotNull(parse(text))).sounds.isEmpty())
    }

    @Test
    fun anEmptyCatalogParsesCleanly() {
        val ready = assertNotNull(parse("""{"version": 1}"""))
        assertTrue(ready.isEmpty)
    }

    @Test
    fun malformedJsonIsRejectedWithoutThrowing() {
        assertEquals(null, parse("{ not json"))
        assertEquals(null, parse(""))
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val text = """
            {"sounds": [{"id": "x", "name": "X", "sha256": "${"c".repeat(64)}",
             "url": "https://stade.dev/x.wav", "somethingNew": 42}]}
        """.trimIndent()
        assertEquals(1, usable(assertNotNull(parse(text))).sounds.size)
    }

    @Test
    fun aRetiredMemeSectionIsIgnored() {
        val text = """
            {
              "version": 1,
              "sounds": [
                {"id": "ok", "name": "Fine", "sha256": "${"d".repeat(64)}", "url": "https://stade.dev/b.wav"}
              ],
              "memes": [
                {"id": "gone", "name": "Gone", "sha256": "${"e".repeat(64)}", "url": "https://stade.dev/g.mp4"}
              ]
            }
        """.trimIndent()
        val ready = usable(assertNotNull(parse(text)))
        assertEquals(listOf("ok"), ready.sounds.map { it.id })
    }
}
