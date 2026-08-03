package dev.stade.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.stade.ui.i18n.LocalStrings

@Composable
fun ChatListFabMenu(
    onAddContact: () -> Unit,
    onCreateGroup: () -> Unit,
    onCreateStadium: () -> Unit,
    onJoinStadium: () -> Unit
) {
    val strings = LocalStrings.current
    var isFabExpanded by remember { mutableStateOf(false) }
    var isStadiumFabExpanded by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AnimatedVisibility(
            visible = !isFabExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedVisibility(
                    visible = isStadiumFabExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FabPill(
                            label = strings.createStadiumAction,
                            icon = Icons.Default.Podcasts,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            scale = 0.75f,
                            onClick = {
                                isStadiumFabExpanded = false
                                onCreateStadium()
                            }
                        )
                        FabPill(
                            label = strings.joinStadiumAction,
                            icon = Icons.Default.Podcasts,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            scale = 0.75f,
                            onClick = {
                                isStadiumFabExpanded = false
                                onJoinStadium()
                            }
                        )
                    }
                }

                val stadiumFabCornerRadius by animateDpAsState(
                    targetValue = if (isStadiumFabExpanded) 20.dp else 12.dp,
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                )
                val stadiumFabRotation by animateFloatAsState(
                    targetValue = if (isStadiumFabExpanded) 45f else 0f,
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                )
                SmallFloatingActionButton(
                    onClick = { isStadiumFabExpanded = !isStadiumFabExpanded },
                    shape = RoundedCornerShape(stadiumFabCornerRadius),
                    containerColor = if (isStadiumFabExpanded) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = if (isStadiumFabExpanded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Icon(
                        imageVector = if (isStadiumFabExpanded) Icons.Default.Close else Icons.Default.Podcasts,
                        contentDescription = if (isStadiumFabExpanded) strings.cancel else null,
                        modifier = Modifier.rotate(stadiumFabRotation)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isFabExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FabPill(
                    label = strings.createGroupAction,
                    icon = Icons.Default.GroupAdd,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = {
                        isFabExpanded = false
                        onCreateGroup()
                    }
                )
                FabPill(
                    label = strings.addContactAction,
                    icon = Icons.Default.PersonAdd,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = {
                        isFabExpanded = false
                        onAddContact()
                    }
                )
            }
        }

        val fabCornerRadius by animateDpAsState(
            targetValue = if (isFabExpanded) 28.dp else 16.dp,
            animationSpec = tween(280, easing = FastOutSlowInEasing)
        )
        val fabIconRotation by animateFloatAsState(
            targetValue = if (isFabExpanded) 45f else 0f,
            animationSpec = tween(280, easing = FastOutSlowInEasing)
        )
        FloatingActionButton(
            onClick = {
                isFabExpanded = !isFabExpanded
                if (isFabExpanded) isStadiumFabExpanded = false
            },
            shape = RoundedCornerShape(fabCornerRadius),
            containerColor = if (isFabExpanded) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primary,
            contentColor = if (isFabExpanded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = if (isFabExpanded) strings.cancel else null,
                modifier = Modifier.rotate(fabIconRotation)
            )
        }
    }
}

@Composable
private fun FabPill(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    scale: Float = 1f
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = (6f * scale).dp
    ) {
        Row(
            modifier = Modifier.padding(
                start = (20f * scale).dp,
                end = (14f * scale).dp,
                top = (12f * scale).dp,
                bottom = (12f * scale).dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((10f * scale).dp)
        ) {
            Text(
                text = label,
                style = if (scale < 1f) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge
            )
            Icon(icon, contentDescription = null, modifier = Modifier.size((24f * scale).dp))
        }
    }
}
