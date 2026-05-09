package app.stade.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Tıklanabilir, kopyalanan zaman geri bildirim veren Stade ID kartı.
 */
@Composable
fun StadeIdCard(
    stadeId: String,
    title: String = "Stade ID",
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(stadeId) { mutableStateOf(false) }

    // Radiusu buradan kontrol edebilirsin. 12.dp daha az yuvarlaktır.
    val cardShape = MaterialTheme.shapes.medium

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape) // 1. Önce kırpıyoruz ki ripple (tıklama efekti) dışarı taşmasın
            .clickable {    // 2. Sonra tıklanabilir yapıyoruz
                clipboard.setText(AnnotatedString(stadeId))
                copied = true
            },
        shape = cardShape, // Kartın kendi görsel şekli
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedContent(
                targetState = copied,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "icon"
            ) { isCopied ->
                Icon(
                    if (isCopied) Icons.Default.Check else Icons.Default.Badge,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (copied) "Kopyalandı" else title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stadeId,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "Kopyala",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Transport URI'sini kullanıcıya gösterilecek opak bir hâle dönüştürür.
 * Onion ve IP/host bilgisi ASLA tam görüntülenmez.
 */
fun maskAddress(addr: String): String {
    return when {
        addr.startsWith("tor://", ignoreCase = true) -> {
            // tor://abcdef…onion:5901 → "Uzak ağ • a…1"
            val rest = addr.removePrefix("tor://").substringBefore(':')
            val first = rest.firstOrNull()?.toString() ?: "?"
            val last = rest.lastOrNull()?.toString() ?: "?"
            "Uzak ağ • $first…$last"
        }
        addr.startsWith("lan://", ignoreCase = true) -> {
            "Yerel ağ"
        }
        else -> "Ağ"
    }
}

