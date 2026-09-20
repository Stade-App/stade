package dev.stade.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.stade.AppContainer
import dev.stade.identity.LocalIdentity
import dev.stade.transport.TransportType
import dev.stade.ui.rememberNetworkOnline
import kotlinx.coroutines.flow.MutableStateFlow
import dev.stade.ui.i18n.LocalStrings

@Composable
fun HomeIdentityHeader(
    container: AppContainer,
    owner: LocalIdentity,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val torInfo by remember {
        container.transports.get(TransportType.TOR)?.info ?: MutableStateFlow(null)
    }.collectAsState()
    val onionState = onionStateOf(rememberNetworkOnline(), torInfo)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Avatar(
            name = owner.nickname,
            size = 38.dp,
            keySeed = owner.publicSigningKey,
            avatarBytes = owner.avatar
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(strings.appTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    owner.nickname,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(6.dp))
                OnionIndicator(
                    state = onionState,
                    contentDescription = onionStateLabel(onionState),
                    size = 14.dp
                )
            }
        }
    }
}
