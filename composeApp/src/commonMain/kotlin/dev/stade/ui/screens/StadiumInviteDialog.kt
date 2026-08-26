package dev.stade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.stade.AppContainer
import dev.stade.identity.LocalIdentity
import dev.stade.share.isShareSheetSupported
import dev.stade.share.shareText
import dev.stade.stadium.StadiumInfo
import dev.stade.ui.components.Avatar
import dev.stade.ui.i18n.LocalStrings
import kotlinx.coroutines.launch

@Composable
fun StadiumInviteDialog(
    container: AppContainer,
    owner: LocalIdentity,
    stadium: StadiumInfo,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var showContactPicker by remember { mutableStateOf(false) }

    suspend fun buildInvite(): String? {
        val addrs = container.connections.selfAddresses()
        if (addrs.none { it.startsWith("tor://") }) {
            status = strings.inviteNotReadyForRemote
            return null
        }
        val handshakeInvite = container.handshake.createInvite(owner, addrs)
        return container.stadiums.buildInviteLink(handshakeInvite.display, stadium)
    }

    if (showContactPicker) {
        StadiumInviteContactPickerDialog(
            container = container,
            owner = owner,
            stadium = stadium,
            onBack = { showContactPicker = false },
            onDone = onDismiss
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(strings.stadiumInviteDialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            buildInvite()?.let {
                                clipboard.setText(AnnotatedString(it))
                                status = strings.inviteCodeCopied(it.length)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.heightIn(max = 18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(strings.copyStadiumInviteAction)
                }
                if (isShareSheetSupported) {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                buildInvite()?.let { shareText(it, stadium.name) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.heightIn(max = 18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.shareInviteAction)
                    }
                }
                FilledTonalButton(
                    onClick = { showContactPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.heightIn(max = 18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(strings.sendInviteToContactAction)
                }
                status?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(strings.closeAction) }
        }
    )
}

@Composable
private fun StadiumInviteContactPickerDialog(
    container: AppContainer,
    owner: LocalIdentity,
    stadium: StadiumInfo,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val allContacts by remember(owner.id) { container.contacts.observeContacts(owner.id) }.collectAsState(initial = emptyList())
    val memberIds = remember(stadium.id) { container.stadiums.getMemberContactIds(stadium.id).toSet() }
    val contacts = remember(allContacts, memberIds) { allContacts.filter { it.kind == 0 && it.id !in memberIds } }
    var selectedContactIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    AlertDialog(
        onDismissRequest = onBack,
        shape = RoundedCornerShape(28.dp),
        icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
        title = { Text(strings.sendInviteToContactAction) },
        text = {
            if (contacts.isEmpty()) {
                Text(strings.noContactsToAdd, style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    itemsIndexed(contacts, key = { _, c -> c.id }) { _, contact ->
                        val checked = selectedContactIds.contains(contact.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    selectedContactIds = if (checked) selectedContactIds - contact.id
                                    else selectedContactIds + contact.id
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                Avatar(name = contact.nickname, size = 36.dp, keySeed = contact.publicSigningKey, avatarBytes = contact.avatar)
                                if (checked) {
                                    Box(
                                        Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surface)
                                            .padding(2.dp)
                                    ) {
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(10.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(contact.nickname, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedContactIds.isNotEmpty(),
                onClick = {
                    val toInvite = selectedContactIds.toList()
                    scope.launch {
                        val addrs = container.connections.selfAddresses()
                        if (addrs.none { it.startsWith("tor://") }) {
                            onDone()
                            return@launch
                        }
                        val handshakeInvite = container.handshake.createInvite(owner, addrs)
                        val inviteCode = container.stadiums.buildInviteLink(handshakeInvite.display, stadium)
                        toInvite.forEach { cid ->
                            runCatching { container.stadiumChat.sendStadiumInviteToContact(owner, cid, inviteCode) }
                        }
                        onDone()
                    }
                }
            ) { Text(strings.sendAction) }
        },
        dismissButton = {
            TextButton(onClick = onBack) { Text(strings.cancel) }
        }
    )
}
