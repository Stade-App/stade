package dev.stade.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.stade.ui.MIN_BACKUP_PASSPHRASE_LEN
import dev.stade.ui.i18n.LocalStrings

@Composable
fun BackupPassphraseDialog(
    title: String,
    body: String,
    confirmLabel: String,
    requireConfirmation: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    var passphrase by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }

    val tooShort = passphrase.length < MIN_BACKUP_PASSPHRASE_LEN
    val mismatched = requireConfirmation && repeated.isNotEmpty() && repeated != passphrase
    val ready = !tooShort && (!requireConfirmation || repeated == passphrase)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(body, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    singleLine = true,
                    label = { Text(strings.backupPassphraseLabel) },
                    visualTransformation = PasswordVisualTransformation(),
                    isError = passphrase.isNotEmpty() && tooShort,
                    supportingText = {
                        if (passphrase.isNotEmpty() && tooShort) {
                            Text(strings.backupPassphraseTooShort)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (requireConfirmation) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = repeated,
                        onValueChange = { repeated = it },
                        singleLine = true,
                        label = { Text(strings.backupPassphraseRepeatLabel) },
                        visualTransformation = PasswordVisualTransformation(),
                        isError = mismatched,
                        supportingText = {
                            if (mismatched) Text(strings.backupPassphraseMismatch)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = ready, onClick = { onConfirm(passphrase) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}
