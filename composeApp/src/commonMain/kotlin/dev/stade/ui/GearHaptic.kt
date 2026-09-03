package dev.stade.ui

import androidx.compose.runtime.Composable

interface GearHaptic {
    suspend fun play()
}

@Composable
expect fun rememberGearHaptic(): GearHaptic
