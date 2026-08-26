package dev.stade.monero

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

actual fun moneroQrMatrix(text: String): Array<BooleanArray>? = runCatching {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 0
    )
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
    Array(matrix.height) { y -> BooleanArray(matrix.width) { x -> matrix.get(x, y) } }
}.getOrNull()
