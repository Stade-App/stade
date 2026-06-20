package dev.stade.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.stade.AppContainer
import dev.stade.identity.LocalIdentity
import dev.stade.ui.i18n.LocalStrings

@Composable
fun IncomingInviteDialog(
    container: AppContainer,
    owner: LocalIdentity,
    code: String,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val parsed = remember(code) { runCatching { container.handshake.parseInvite(code) }.getOrNull() }

    var name by remember(code) { mutableStateOf(parsed?.nickname ?: "") }
    var confirmed by remember(code) { mutableStateOf(false) }
    var status by remember(code) { mutableStateOf<String?>(null) }
    var accepted by remember(code) { mutableStateOf(false) }
    var targetId by remember(code) { mutableStateOf<String?>(null) }

    val knownContacts by container.contacts.observeContacts(owner.id).collectAsState(initial = emptyList())
    val isAdded = targetId != null && knownContacts.any { it.id == targetId }
    LaunchedEffect(isAdded) {
        if (isAdded) {
            status = strings.contactAdded(name.ifBlank { parsed?.nickname ?: "" })
            accepted = true
        }
    }

    val lanOnly = parsed != null && parsed.addresses.isNotEmpty() &&
        parsed.addresses.none { it.startsWith("tor://") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.addContactDialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (parsed == null) {
                    Text(strings.invalidInvite, color = MaterialTheme.colorScheme.error)
                } else {
                    Text(strings.incomingInviteMessage)
                    Text(
                        parsed.nickname.ifBlank { parsed.stadeId },
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        parsed.stadeId,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(strings.contactNameLabel) },
                        singleLine = true,
                        enabled = !accepted,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    )
                    if (!accepted) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = confirmed,
                                onCheckedChange = { confirmed = it }
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                strings.confirmAddCheckbox,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (lanOnly && !accepted) {
                        Text(
                            strings.inviteLanOnlyWarning,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                status?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            if (parsed != null && !accepted) {
                TextButton(
                    enabled = name.isNotBlank() && confirmed,
                    onClick = {
                        when (val result = container.beginAcceptInvite(owner, code, name, strings)) {
                            is BeginAcceptResult.Error -> status = result.message
                            is BeginAcceptResult.NoAddress -> {
                                status = strings.inviteAcceptedNoAddr
                                accepted = true
                            }
                            is BeginAcceptResult.Dialing -> {
                                targetId = result.payload.stadeId
                                status = strings.connectingInBackground(
                                    name.ifBlank { result.payload.nickname }
                                )
                                accepted = true
                            }
                        }
                    }
                ) { Text(strings.addAction) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (accepted) strings.understood else strings.notNowAction)
            }
        }
    )
}
