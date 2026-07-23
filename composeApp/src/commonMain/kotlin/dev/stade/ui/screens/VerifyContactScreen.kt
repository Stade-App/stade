package dev.stade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.stade.AppContainer
import dev.stade.identity.LocalIdentity
import dev.stade.ui.components.Avatar
import dev.stade.ui.i18n.LocalStrings
import dev.stade.ui.theme.StadeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyContactScreen(
    container: AppContainer,
    owner: LocalIdentity,
    contactId: String,
    onBack: () -> Unit
) {
    val strings = LocalStrings.current
    val contact = remember(contactId) { container.contacts.get(contactId) }
    val safety = remember(contact?.id) {
        contact?.let { container.fingerprint.safetyNumber(owner.publicSigningKey, it.publicSigningKey) }
    }
    var verified by remember(contact?.id) { mutableStateOf(contact?.verified == true) }
    val connected by container.sync.connectedContacts.collectAsState()
    val isOnline = contact != null && connected.contains(contact.id)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(contact?.nickname ?: strings.profileTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Avatar(name = contact?.nickname ?: "?", size = 64.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(contact?.nickname ?: "", style = MaterialTheme.typography.titleMedium)
                    if (contact != null) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(8.dp).clip(CircleShape).background(
                                    if (isOnline) StadeColors.online else StadeColors.offline
                                )
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (isOnline) strings.online else strings.offline,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (verified) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                strings.verifiedLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        strings.safetyNumber,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    SafetyNumberBlock(safety ?: "")
                    Spacer(Modifier.height(12.dp))
                    Text(
                        strings.safetyNumberNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                enabled = !verified,
                onClick = {
                    container.contacts.verify(contactId)
                    verified = true
                }
            ) {
                Text(if (verified) strings.alreadyVerifiedLabel else strings.markAsVerified)
            }
        }
    }
}

@Composable
private fun SafetyNumberBlock(raw: String) {
    val digits = raw.filter { it.isDigit() }
    if (digits.isEmpty()) {
        Text("-", style = MaterialTheme.typography.titleSmall)
        return
    }
    val groups = digits.chunked(5)
    val perRow = if (groups.size >= 4) (groups.size + 3) / 4 else groups.size
    val rows = groups.chunked(perRow.coerceAtLeast(1))

    Column(horizontalAlignment = Alignment.CenterHorizontally,
           verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Text(
                row.joinToString("  "),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    letterSpacing = 1.sp
                )
            )
        }
    }
}
