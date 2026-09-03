package dev.stade.ui

import androidx.compose.runtime.Composable

private object NoGearHaptic : GearHaptic {
    override suspend fun play() {}
}

@Composable
actual fun rememberGearHaptic(): GearHaptic = NoGearHaptic
