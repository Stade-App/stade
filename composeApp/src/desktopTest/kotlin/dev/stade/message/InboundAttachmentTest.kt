package dev.stade.message

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

@OptIn(ExperimentalEncodingApi::class)
class InboundAttachmentTest {

    @Test
    fun decodesAttachmentWithinSenderLimit() {
        val attachment = byteArrayOf(1, 2, 3, 4)

        assertContentEquals(attachment, decodeInboundAttachment(Base64.Default.encode(attachment)))
    }

    @Test
    fun rejectsAttachmentAboveSenderLimitBeforeDecoding() {
        val encoded = Base64.Default.encode(ByteArray(MAX_ATTACHMENT_BYTES + 1))

        assertNull(decodeInboundAttachment(encoded))
    }

    @Test
    fun rejectsOversizedIncomingVideo() {
        val encoded = Base64.Default.encode(ByteArray(MAX_ATTACHMENT_BYTES + 1))
        val message = Message(
            id = "message",
            contactId = "contact",
            direction = MessageDirection.IN,
            body = VIDEO_BODY_PREFIX + encoded,
            timestamp = 0L,
            delivered = true,
            read = false
        )

        assertNull(message.videoBytes())
    }
}
