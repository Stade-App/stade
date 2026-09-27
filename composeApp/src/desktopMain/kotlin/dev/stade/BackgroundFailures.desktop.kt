package dev.stade

actual fun reportBackgroundFailure(throwable: Throwable) {
    System.err.println("Stade: background task failed: ${throwable.stackTraceToString()}")
}
