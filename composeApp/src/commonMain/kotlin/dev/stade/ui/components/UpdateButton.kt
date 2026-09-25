package dev.stade.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.stade.AppContainer

@Composable
expect fun UpdateAvailableButton(container: AppContainer, modifier: Modifier = Modifier)
