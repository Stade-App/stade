package dev.stade.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.stade.ui.i18n.LocalStrings

private const val INDICATOR_ANIM_MS = 220
private val BAR_MAX_WIDTH = 440.dp

@Composable
fun HomeActionBar(
    onAddContact: () -> Unit,
    onCreateGroup: () -> Unit,
    onCreateStadium: () -> Unit,
    onJoinStadium: () -> Unit,
    onOpenRadar: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    var stadiumMenuOpen by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(max = BAR_MAX_WIDTH).fillMaxWidth(),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 10.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HomeAction(
                    icon = Icons.Default.PersonAdd,
                    label = strings.navContact,
                    onClick = onAddContact,
                    modifier = Modifier.weight(1f)
                )
                HomeAction(
                    icon = Icons.Default.GroupAdd,
                    label = strings.navGroup,
                    onClick = onCreateGroup,
                    modifier = Modifier.weight(1f)
                )
                Box(modifier = Modifier.weight(1f)) {
                    HomeAction(
                        icon = Icons.Default.Podcasts,
                        label = strings.navStadium,
                        onClick = { stadiumMenuOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = stadiumMenuOpen,
                        onDismissRequest = { stadiumMenuOpen = false },
                        offset = DpOffset(x = 0.dp, y = 8.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text(strings.createStadiumAction) },
                            leadingIcon = { Icon(Icons.Default.AddCircleOutline, contentDescription = null) },
                            onClick = {
                                stadiumMenuOpen = false
                                onCreateStadium()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(strings.joinStadiumAction) },
                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                            onClick = {
                                stadiumMenuOpen = false
                                onJoinStadium()
                            }
                        )
                    }
                }
                if (onOpenRadar != null) {
                    HomeAction(
                        icon = Icons.AutoMirrored.Filled.BluetoothSearching,
                        label = strings.navRadar,
                        onClick = onOpenRadar,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val indicatorWidth by animateDpAsState(
        targetValue = if (pressed) 48.dp else 34.dp,
        animationSpec = tween(INDICATOR_ANIM_MS, easing = FastOutSlowInEasing),
        label = "indicatorWidth"
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (pressed) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = tween(INDICATOR_ANIM_MS),
        label = "indicatorColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (pressed) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(INDICATOR_ANIM_MS),
        label = "contentColor"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(indicatorWidth)
                .height(30.dp)
                .clip(CircleShape)
                .background(indicatorColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(21.dp),
                tint = contentColor
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
