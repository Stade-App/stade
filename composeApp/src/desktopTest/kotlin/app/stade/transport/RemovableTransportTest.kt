package app.stade.transport

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RemovableTransportTest {

    private val tempDir: File = File(System.getProperty("java.io.tmpdir"), "stade-removable-test-${System.nanoTime()}").apply { mkdirs() }

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun roundTripBetweenTwoNodes() = runBlocking {
        val cfg = { "dir=${tempDir.absolutePath}" }
        val nodeA = RemovableTransport(nodeId = "aaaa1111", configProvider = cfg)
        val nodeB = RemovableTransport(nodeId = "bbbb2222", configProvider = cfg)

        val incomingAtB = CompletableDeferred<Connection>()
        nodeA.start { }
        nodeB.start { conn -> incomingAtB.complete(conn) }

        val connA = withTimeout(10_000) { nodeA.connect("removable://bbbb2222") }
        assertNotNull(connA, "initiator connection should be created")

        val hello = byteArrayOf(1, 2, 3, 4, 5)
        connA.send(hello)

        val connB = withTimeout(10_000) { incomingAtB.await() }
        val receivedAtB = withTimeout(10_000) { connB.receive() }
        assertNotNull(receivedAtB, "node B should receive the frame")
        assertTrue(hello.contentEquals(receivedAtB), "node B payload should match what A sent")

        val reply = byteArrayOf(9, 8, 7)
        connB.send(reply)
        val receivedAtA = withTimeout(10_000) { connA.receive() }
        assertNotNull(receivedAtA, "node A should receive the reply")
        assertTrue(reply.contentEquals(receivedAtA), "node A payload should match what B sent")

        connA.close()
        connB.close()
        nodeA.stop()
        nodeB.stop()
    }

    @Test
    fun selfAddressRequiresConfiguredDir() {
        val unconfigured = RemovableTransport(nodeId = "node", configProvider = { "" })
        assertEquals(null, unconfigured.selfAddress())

        val configured = RemovableTransport(nodeId = "node", configProvider = { "dir=${tempDir.absolutePath}" })
        assertEquals("removable://node", configured.selfAddress())
    }
}
