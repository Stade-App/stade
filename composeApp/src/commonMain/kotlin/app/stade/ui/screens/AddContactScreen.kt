package app.stade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.stade.AppContainer
import app.stade.contact.InviteParseResult
import app.stade.identity.LocalIdentity
import app.stade.share.InviteShare
import app.stade.transport.TransportType
import app.stade.ui.components.StadeIdCard
import app.stade.ui.i18n.LocalStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContactScreen(container: AppContainer, owner: LocalIdentity, onBack: () -> Unit) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    val torPlugin = remember { container.transports.get(TransportType.TOR) }
    val torInfo by remember(torPlugin) {
        torPlugin?.info ?: kotlinx.coroutines.flow.MutableStateFlow(null)
    }.collectAsState(initial = null)

    var invite by remember {
        mutableStateOf(container.handshake.createInvite(owner, container.connections.selfAddresses()))
    }
    LaunchedEffect(torInfo?.running) {
        invite = container.handshake.createInvite(owner, container.connections.selfAddresses())
    }
    var alias by remember { mutableStateOf("") }
    var pastedCode by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var statusSticky by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()

    val pendingInvite by container.pendingInvite.collectAsState()
    val pendingDials by container.connections.pendingDials.collectAsState()
    var dialingTargetAddrs by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(pendingInvite) {
        val p = pendingInvite
        if (!p.isNullOrBlank() && pastedCode.isBlank()) {
            pastedCode = p
            status = strings.pendingInviteOpened
            statusSticky = true
            container.pendingInvite.value = null
        }
    }

    LaunchedEffect(status, statusSticky) {
        if (status != null && !statusSticky) {
            delay(8000)
            status = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.addContactTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StepCard(stepNumber = 1, title = strings.step1Title) {
                Text(
                    strings.step1Description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (torInfo?.running != true) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        strings.torStartingInviteHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(invite.display))
                        status = strings.inviteCodeCopied(invite.display.length)
                        statusSticky = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(strings.copyInviteCode(invite.display.length))
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        status = InviteShare.share(invite.display, owner.nickname)
                        statusSticky = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(strings.shareAsFile)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    strings.yourStadeId,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                StadeIdCard(stadeId = owner.stadeId)
            }

            StepCard(stepNumber = 2, title = strings.step2Title) {
                OutlinedTextField(
                    value = pastedCode,
                    onValueChange = { pastedCode = it },
                    label = { Text(strings.inviteCodeLabel) },
                    placeholder = { Text("STADE2-…") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = MaterialTheme.shapes.medium,
                    supportingText = {
                        val n = pastedCode.replace(Regex("[^A-Za-z0-9]"), "").length
                        Text(strings.charCount(n))
                    }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text(strings.contactNameLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    enabled = pastedCode.isNotBlank() && alias.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    onClick = {
                        scope.launch {
                            try {
                                val trimmed = pastedCode.trim()
                                val looksLikeStadeId = Regex("^STADE-[0-9A-Za-z]{4}-[0-9A-Za-z]{4}-[0-9A-Za-z]{4}$")
                                    .matches(trimmed.uppercase())
                                if (looksLikeStadeId) {
                                    status = strings.inviteCodeIsStadeId
                                    return@launch
                                }
                                val result = container.handshake.parseInviteDetailed(trimmed)
                                val parsed = when (result) {
                                    is InviteParseResult.Ok -> result.payload
                                    is InviteParseResult.MissingPrefix -> {
                                        status = strings.inviteMissingPrefix(result.firstChars)
                                        return@launch
                                    }
                                    is InviteParseResult.TooShort -> {
                                        status = strings.inviteTooShort(result.actual, result.expected)
                                        return@launch
                                    }
                                    is InviteParseResult.TrailingBytes -> {
                                        status = strings.inviteTrailingBytes(result.extra)
                                        return@launch
                                    }
                                    InviteParseResult.BadMagic -> {
                                        status = strings.inviteBadMagic
                                        return@launch
                                    }
                                    is InviteParseResult.BadVersion -> {
                                        status = strings.inviteBadVersion(result.version)
                                        return@launch
                                    }
                                    is InviteParseResult.BadNickname -> {
                                        status = strings.inviteBadNickname(result.length)
                                        return@launch
                                    }
                                    is InviteParseResult.BadAddressBlob -> {
                                        status = strings.inviteBadAddressBlob(result.length)
                                        return@launch
                                    }
                                    InviteParseResult.EdVerifyFail -> {
                                        status = strings.inviteEdVerifyFail
                                        return@launch
                                    }
                                    InviteParseResult.MlDsaVerifyFail -> {
                                        status = strings.inviteMlDsaVerifyFail
                                        return@launch
                                    }
                                    is InviteParseResult.DecodeError -> {
                                        status = strings.inviteDecodeError(result.cause)
                                        return@launch
                                    }
                                }
                                if (parsed.signingPublicKey.contentEquals(owner.publicSigningKey)) {
                                    status = strings.selfInviteError
                                    return@launch
                                }
                                if (container.contacts.findByStadeId(parsed.stadeId) != null) {
                                    status = strings.alreadyAdded(parsed.stadeId)
                                    return@launch
                                }
                                val addrs = parsed.addresses
                                if (addrs.isEmpty()) {
                                    status = strings.inviteAcceptedNoAddr
                                    statusSticky = true
                                } else {
                                    container.connections.queueDial(addrs)
                                    dialingTargetAddrs = addrs.toSet()
                                    status = strings.inviteAccepted(parsed.nickname, addrs.size)
                                    statusSticky = true
                                    val targetId = parsed.stadeId

                                    // 5 dakika boyunca bağlantı bekle (Tor descriptor yayılması + circuit + handshake)
                                    val added = withTimeoutOrNull(5 * 60_000L) {
                                        container.contacts.observeContacts(owner.id).first { list ->
                                            list.any { it.id == targetId }
                                        }
                                        true
                                    } ?: false
                                    if (added || container.contacts.findByStadeId(targetId) != null) {
                                        status = strings.contactAdded(parsed.nickname)
                                        statusSticky = true
                                        dialingTargetAddrs = emptySet()
                                    } else {
                                        status = strings.connectionTimeout
                                        statusSticky = true
                                    }
                                }
                                pastedCode = ""
                                alias = ""
                            } catch (e: Exception) {
                                status = strings.error(e.message ?: "")
                            }
                        }
                    }
                ) { Text(strings.acceptInvite) }
                if (dialingTargetAddrs.isNotEmpty()) {
                    val activeAttempts = pendingDials.filterKeys { it in dialingTargetAddrs }.values
                    if (activeAttempts.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Column {
                            for (a in activeAttempts.sortedByDescending { it.timestamp }) {
                                Text(
                                    "• ${a.address.take(40)}…  —  ${a.detail ?: a.status.name}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                status?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    stepNumber: Int,
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stepNumber.toString(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
