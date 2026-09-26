package dev.stade.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PersonAdd
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
private val CREATE_BUTTON_SIZE = 46.dp

val HOME_BAR_HEIGHT = 96.dp

val LocalHomeBarClearance = androidx.compose.runtime.compositionLocalOf { 0.dp }

private const val BAR_INTRO_MS = 1150
private const val BAR_START_FRACTION = 0.1f
private const val ITEM_POP_TENSION = 2.4f

private val BarRevealEasing = CubicBezierEasing(0.45f, 0f, 0.15f, 1f)

private const val PILL_DROP_MS = 360
private const val PILL_IMPACT_MS = 90
private const val PILL_FALL_HEIGHTS = 2.2f
private const val PILL_FALL_STRETCH = 0.42f
private const val PILL_IMPACT_SQUASH = 0.34f
private val PillFallEasing = CubicBezierEasing(0.5f, 0f, 0.9f, 0.62f)

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

enum class HomeDestination { NONE, CHATS, CREATE, RADAR }

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
    var createMenuOpen by remember { mutableStateOf(false) }

    val order = remember(onOpenRadar != null) {
        buildList {
            add(HomeDestination.CHATS)
            add(HomeDestination.CREATE)
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
        targetValue = if (selectedIndex >= 0 && selected != HomeDestination.CREATE) 1f else 0f,
        animationSpec = tween(INDICATOR_ANIM_MS),
        label = "pillAlpha"
    )

    val onSurface = MaterialTheme.colorScheme.onSurface

    val reveal = remember { Animatable(if (playIntro) 0f else 1f) }
    val pillDrop = remember { Animatable(if (playIntro) 0f else 1f) }
    val pillImpact = remember { Animatable(0f) }
    LaunchedEffect(playIntro) {
        if (!playIntro) {
            reveal.snapTo(1f)
            pillDrop.snapTo(1f)
            pillImpact.snapTo(0f)
            return@LaunchedEffect
        }
        reveal.snapTo(0f)
        pillDrop.snapTo(0f)
        pillImpact.snapTo(0f)
        reveal.animateTo(1f, tween(BAR_INTRO_MS, easing = BarRevealEasing))
        pillDrop.animateTo(1f, tween(PILL_DROP_MS, easing = PillFallEasing))
        pillImpact.animateTo(1f, tween(PILL_IMPACT_MS, easing = LinearEasing))
        pillImpact.animateTo(
            0f,
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        )
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
                    val fall = pillDrop.value
                    if (pillAlpha <= 0.01f || order.isEmpty() || revealed < 0.999f || fall <= 0.001f) {
                        return@Canvas
                    }
                    val slot = size.width / order.size
                    val left = pillIndex * slot
                    val lastIndex = (order.size - 1).toFloat()

                    val impact = pillImpact.value
                    val stretch = 1f + PILL_FALL_STRETCH * (1f - fall)
                    val scaleY = stretch * (1f - PILL_IMPACT_SQUASH * impact)
                    val scaleX = (1f / stretch) * (1f + PILL_IMPACT_SQUASH * 0.55f * impact)
                    val dropOffset = (1f - fall) * size.height * PILL_FALL_HEIGHTS

                    val width = slot * scaleX
                    val height = size.height * scaleY
                    val centerX = left + slot / 2f
                    val centerY = size.height / 2f - dropOffset
                    val rect = Rect(
                        Offset(centerX - width / 2f, centerY - height / 2f),
                        Size(width, height)
                    )

                    val capsule = height / 2f
                    val inner = ITEM_PILL_RADIUS.toPx().coerceAtMost(capsule)
                    val leftEdge = (1f - pillIndex.coerceIn(0f, 1f))
                    val rightEdge = (1f - (lastIndex - pillIndex).coerceIn(0f, 1f))
                    val leftRadius = CornerRadius(lerp(inner, capsule, leftEdge))
                    val rightRadius = CornerRadius(lerp(inner, capsule, rightEdge))

                    val shape = Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = rect,
                                topLeft = leftRadius,
                                topRight = rightRadius,
                                bottomRight = rightRadius,
                                bottomLeft = leftRadius
                            )
                        )
                    }
                    val appear = pillAlpha * fall
                    drawPath(shape, color = onSurface.copy(alpha = 0.10f * appear))
                    drawPath(
                        shape,
                        color = onSurface.copy(alpha = 0.14f * appear),
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
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        HomeCreateAction(
                            label = strings.navCreateAction,
                            selected = selected == HomeDestination.CREATE,
                            expanded = createMenuOpen,
                            appear = itemAppear(1, order.size, revealed),
                            onClick = { createMenuOpen = true }
                        )
                        DropdownMenu(
                            expanded = createMenuOpen,
                            onDismissRequest = { createMenuOpen = false },
                            offset = DpOffset(x = 0.dp, y = 8.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text(strings.addContactTitle) },
                                leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                                onClick = {
                                    createMenuOpen = false
                                    onAddContact()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(strings.createGroupTitle) },
                                leadingIcon = { Icon(Icons.Default.GroupAdd, contentDescription = null) },
                                onClick = {
                                    createMenuOpen = false
                                    onCreateGroup()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(strings.createStadiumAction) },
                                leadingIcon = { Icon(Icons.Default.AddCircleOutline, contentDescription = null) },
                                onClick = {
                                    createMenuOpen = false
                                    onCreateStadium()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(strings.joinStadiumAction) },
                                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                                onClick = {
                                    createMenuOpen = false
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
                            appear = itemAppear(2, order.size, revealed),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeCreateAction(
    label: String,
    selected: Boolean,
    expanded: Boolean,
    appear: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(INDICATOR_ANIM_MS, easing = FastOutSlowInEasing),
        label = "createPress"
    )
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 135f else 0f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "createRotation"
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected || expanded) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = tween(INDICATOR_ANIM_MS),
        label = "createContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected || expanded) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = tween(INDICATOR_ANIM_MS),
        label = "createContent"
    )

    Surface(
        modifier = modifier
            .size(CREATE_BUTTON_SIZE)
            .graphicsLayer {
                val pop = overshoot(appear)
                alpha = (appear * 2f).coerceIn(0f, 1f)
                val entrance = 0.2f + 0.8f * pop
                scaleX = press * entrance
                scaleY = press * entrance
                translationY = (1f - pop) * 26.dp.toPx()
                rotationZ = (1f - pop) * 22f
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        shape = CircleShape,
        color = containerColor,
        shadowElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Add,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier
                    .size(25.dp)
                    .graphicsLayer { rotationZ = rotation }
            )
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
            .padding(horizontal = 8.dp, vertical = 8.dp),
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
