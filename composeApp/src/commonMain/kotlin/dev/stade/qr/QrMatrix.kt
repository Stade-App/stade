package dev.stade.qr

class QrMatrix(val size: Int, private val data: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = data[y * size + x]
    fun toFlatBooleans(): BooleanArray = data
}

expect object QrEncoder {
    fun encode(payload: String, errorCorrection: QrErrorCorrection = QrErrorCorrection.M): QrMatrix
}

enum class QrErrorCorrection { L, M, Q, H }
