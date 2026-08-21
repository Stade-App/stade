package dev.stade.ui.screens

import androidx.compose.runtime.State

expect fun getStadeyVisible(): State<Boolean>
expect fun setStadeyVisible(value: Boolean)
