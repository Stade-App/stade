package dev.stade.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.stade.ui.i18n.LocalStrings
import dev.stade.vanish.VanishDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VanishDurationSheet(
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    val strings = LocalStrings.current
    val options = listOf(
        strings.vanishDuration30Min to VanishDuration.THIRTY_MINUTES,
        strings.vanishDuration1Hour to VanishDuration.ONE_HOUR,
        strings.vanishDuration6Hours to VanishDuration.SIX_HOURS,
        strings.vanishDuration12Hours to VanishDuration.TWELVE_HOURS,
        strings.vanishDuration1Day to VanishDuration.ONE_DAY
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text(
                strings.vanishDurationSheetTitle,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            options.forEach { (label, durationMs) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(durationMs) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
