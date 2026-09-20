package dev.stade.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.Chat
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.util.lerp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Animatable
import kotlin.math.abs
import dev.stade.ui.i18n.LocalStrings

private val ITEM_PILL_RADIUS = 20.dp
private val BAR_INNER_INSET = 8.dp
private const val INDICATOR_ANIM_MS = 260
private val BAR_MAX_WIDTH = 440.dp

val HOME_BAR_HEIGHT = 96.dp

/** Bottom space a screen must leave clear for the floating nav bar, 0.dp where it is hidden. */
val LocalHomeBarClearance = androidx.compose.runtime.compositionLocalOf { 0.dp }

private const val BAR_INTRO_MS = 1150
private const val BAR_START_FRACTION = 0.1f
private const val ITEM_POP_TENSION = 2.4f

private val BarRevealEasing = CubicBezierEasing(0.45f, 0f, 0.15f, 1f)

private fun itemAppear(index: Int, count: Int, reveal: Float): Float {
    if (count <= 0) return 1f
    val center = (count - 1) / 2f
    val distance = abs(index - center)
    val maxDistance = center.coerceAtLeast(0.001f)
    val start = 0.30f + 0.34f * (distance / maxDistance)
    return ((reveal - start) / 0.36f).coerceIn(0f, 1f)
}

private fun overshoot(t: Float, tension: Float = ITEM_POP_TENSION): Float {
    val c3 = tension + 1f
    val u = t - 1f
    return 1f + c3 * u * u * u + tension * u * u
}

enum class HomeDestination { NONE, CHATS, CONTACT, GROUP, STADIUM, RADAR }

@Composable
fun HomeActionBar(
    onOpenChats: () -> Unit,
    onAddContact: () -> Unit,
    onCreateGroup: () -> Unit,
    onCreateStadium: () -> Unit,
    onJoinStadium: () -> Unit,
    onOpenRadar: (() -> Unit)? = null,
    selected: HomeDestination = HomeDestination.NONE,
    playIntro: Boolean = false,
    onIntroFinished: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    var stadiumMenuOpen by remember { mutableStateOf(false) }

    val order = remember(onOpenRadar != null) {
        buildList {
            add(HomeDestination.CHATS)
            add(HomeDestination.CONTACT)
            add(HomeDestination.GROUP)
            add(HomeDestination.STADIUM)
            if (onOpenRadar != null) add(HomeDestination.RADAR)
        }
    }
    val selectedIndex = order.indexOf(selected)
    var restingIndex by remember { mutableStateOf(0) }
    LaunchedEffect(selectedIndex) { if (selectedIndex >= 0) restingIndex = selectedIndex }

    val pillIndex by animateFloatAsState(
        targetValue = if (selectedIndex >= 0) selectedIndex.toFloat() else restingIndex.toFloat(),
        animationSpec = tween(INDICATOR_ANIM_MS, easing = FastOutSlowInEasing),
        label = "pillIndex"
    )
    val pillAlpha by animateFloatAsState(
        targetValue = if (selectedIndex >= 0) 1f else 0f,
        animationSpec = tween(INDICATOR_ANIM_MS),
        label = "pillAlpha"
    )

    val onSurface = MaterialTheme.colorScheme.onSurface

    val reveal = remember { Animatable(if (playIntro) 0f else 1f) }
    LaunchedEffect(playIntro) {
        if (!playIntro) {
            reveal.snapTo(1f)
            return@LaunchedEffect
        }
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(BAR_INTRO_MS, easing = BarRevealEasing))
        onIntroFinished()
    }
    val revealed = reveal.value
    val widthFraction = BAR_START_FRACTION + (1f - BAR_START_FRACTION) * revealed

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(max = BAR_MAX_WIDTH).fillMaxWidth(widthFraction),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 10.dp
        ) {
            Box(modifier = Modifier.padding(BAR_INNER_INSET)) {
                Canvas(Modifier.matchParentSize()) {
                    if (pillAlpha <= 0.01f || order.isEmpty() || revealed < 0.999f) return@Canvas
                    val slot = size.width / order.size
                    val left = pillIndex * slot
                    val lastIndex = (order.size - 1).toFloat()

                    // outer corners follow the bar's own capsule; inner ones stay square-ish
                    val capsule = size.height / 2f
                    val inner = ITEM_PILL_RADIUS.toPx().coerceAtMost(capsule)
                    val leftEdge = (1f - pillIndex.coerceIn(0f, 1f))
                    val rightEdge = (1f - (lastIndex - pillIndex).coerceIn(0f, 1f))
                    val leftRadius = CornerRadius(lerp(inner, capsule, leftEdge))
                    val rightRadius = CornerRadius(lerp(inner, capsule, rightEdge))

                    val shape = Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = Rect(Offset(left, 0f), Size(slot, size.height)),
                                topLeft = leftRadius,
                                topRight = rightRadius,
                                bottomRight = rightRadius,
                                bottomLeft = leftRadius
                            )
                        )
                    }
                    drawPath(shape, color = onSurface.copy(alpha = 0.10f * pillAlpha))
                    drawPath(
                        shape,
                        color = onSurface.copy(alpha = 0.14f * pillAlpha),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HomeAction(
                        icon = Icons.AutoMirrored.Filled.Chat,
                        label = strings.navChats,
                        onClick = onOpenChats,
                        selected = selected == HomeDestination.CHATS,
                        appear = itemAppear(0, order.size, revealed),
                        modifier = Modifier.weight(1f)
                    )
                    HomeAction(
                        icon = Icons.Default.PersonAdd,
                        label = strings.navContact,
                        onClick = onAddContact,
                        selected = selected == HomeDestination.CONTACT,
                        appear = itemAppear(1, order.size, revealed),
                        modifier = Modifier.weight(1f)
                    )
                    HomeAction(
                        icon = Icons.Default.GroupAdd,
                        label = strings.navGroup,
                        onClick = onCreateGroup,
                        selected = selected == HomeDestination.GROUP,
                        appear = itemAppear(2, order.size, revealed),
                        modifier = Modifier.weight(1f)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        HomeAction(
                            icon = Icons.Default.Podcasts,
                            label = strings.navStadium,
                            onClick = { stadiumMenuOpen = true },
                            selected = selected == HomeDestination.STADIUM,
                            appear = itemAppear(3, order.size, revealed),
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
                            selected = selected == HomeDestination.RADAR,
                            appear = itemAppear(4, order.size, revealed),
                            modifier = Modifier.weight(1f)
                        )
                    }
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
    selected: Boolean = false,
    appear: Float = 1f,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val contentColor by animateColorAsState(
        targetValue = if (pressed || selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(INDICATOR_ANIM_MS),
        label = "contentColor"
    )
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(INDICATOR_ANIM_MS, easing = FastOutSlowInEasing),
        label = "pressScale"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(ITEM_PILL_RADIUS))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .graphicsLayer {
                val pop = overshoot(appear)
                alpha = (appear * 2f).coerceIn(0f, 1f)
                val entrance = 0.2f + 0.8f * pop
                scaleX = press * entrance
                scaleY = press * entrance
                translationY = (1f - pop) * 26.dp.toPx()
                rotationZ = (1f - pop) * 22f
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(21.dp),
            tint = contentColor
        )
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
