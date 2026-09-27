package dev.stade

import android.util.Log

actual fun reportBackgroundFailure(throwable: Throwable) {
    Log.e("Stade", "background task failed", throwable)
}
