package dev.stade

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler

const val SHUTDOWN_GRACE_MS = 5_000L

val backgroundFailures = CoroutineExceptionHandler { _, throwable ->
    if (throwable is CancellationException) return@CoroutineExceptionHandler
    reportBackgroundFailure(throwable)
}

expect fun reportBackgroundFailure(throwable: Throwable)
