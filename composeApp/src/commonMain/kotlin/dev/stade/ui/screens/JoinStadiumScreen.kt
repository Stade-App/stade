package dev.stade.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import dev.stade.AppContainer
import dev.stade.contact.InviteParseResult
import dev.stade.identity.LocalIdentity
import dev.stade.stadium.PendingStadiumJoin
import dev.stade.ui.i18n.LocalStrings
import dev.stade.ui.inviteErrorText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinStadiumScreen(container: AppContainer, owner: LocalIdentity, onBack: () -> Unit) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var pastedCode by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.joinStadiumTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                strings.joinStadiumHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = pastedCode,
                onValueChange = { pastedCode = it },
                label = { Text(strings.stadiumInviteLabel) },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                maxLines = 4,
                shape = MaterialTheme.shapes.medium,
                trailingIcon = {
                    IconButton(onClick = {
                        val clipped = clipboard.getText()?.text
                        if (!clipped.isNullOrEmpty()) pastedCode = clipped
                    }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = strings.pasteButton)
                    }
                }
            )
            FilledTonalButton(
                enabled = pastedCode.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                onClick = {
                    scope.launch {
                        val trimmed = pastedCode.trim()
                        val split = container.stadiums.splitInviteLink(trimmed)
                        if (split == null) {
                            status = strings.notAStadiumInvite
                            return@launch
                        }
                        val (handshakePart, stadiumDataPart) = split
                        val stadiumData = container.stadiums.parseStadiumData(stadiumDataPart)
                        if (stadiumData == null) {
                            status = strings.notAStadiumInvite
                            return@launch
                        }
                        val parseResult = container.handshake.parseInviteDetailed(handshakePart)
                        val payload = (parseResult as? InviteParseResult.Ok)?.payload
                        if (payload == null) {
                            status = inviteErrorText(parseResult, strings) ?: strings.invalidInvite
                            return@launch
                        }
                        if (payload.signingPublicKey.contentEquals(owner.publicSigningKey)) {
                            status = strings.selfInviteError
                            return@launch
                        }
                        val addrs = payload.addresses
                        if (addrs.isEmpty()) {
                            status = strings.inviteAcceptedNoAddr
                            return@launch
                        }
                        container.stadiums.storePendingJoin(
                            payload.stadeId,
                            PendingStadiumJoin(stadiumData.stadiumId, stadiumData.stadiumName, stadiumData.inviteToken)
                        )
                        container.connections.queueDial(addrs)
                        status = strings.stadiumJoinDialing(stadiumData.stadiumName)
                        pastedCode = ""
                    }
                }
            ) { Text(strings.joinStadiumAction) }
            status?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
