package dev.stade.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.stade.sticker.ImportResult
import dev.stade.ui.i18n.LocalStrings

@Composable
fun StickerImportResultDialog(result: ImportResult, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (result.added > 0) strings.stickersImported(result.added)
                else strings.stickersImportedNone
            )
        },
        text = {
            Column {
                if (result.packs.isNotEmpty()) {
                    Text(
                        result.packs.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (result.duplicates > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        strings.stickersImportDuplicates(result.duplicates),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (result.unreadable > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        strings.stickersImportUnreadable(result.unreadable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(strings.understood) }
        }
    )
}
