package app.stade.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

actual object QrEncoder {
    actual fun encode(payload: String, errorCorrection: QrErrorCorrection): QrMatrix {
        val ecl = when (errorCorrection) {
            QrErrorCorrection.L -> ErrorCorrectionLevel.L
            QrErrorCorrection.M -> ErrorCorrectionLevel.M
            QrErrorCorrection.Q -> ErrorCorrectionLevel.Q
            QrErrorCorrection.H -> ErrorCorrectionLevel.H
        }
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ecl,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 0, 0, hints)
        val w = matrix.width
        val h = matrix.height
        require(w == h) { "qr must be square" }
        val data = BooleanArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            data[y * w + x] = matrix.get(x, y)
        }
        return QrMatrix(w, data)
    }
}
