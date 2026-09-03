package dev.stade.radar

import androidx.compose.runtime.State

expect fun getRadarIntroSuppressed(): State<Boolean>
expect fun setRadarIntroSuppressed(value: Boolean)

expect fun getRadarAnonymous(): State<Boolean>
expect fun setRadarAnonymous(value: Boolean)

expect fun getRadarInvisible(): State<Boolean>
expect fun setRadarInvisible(value: Boolean)
