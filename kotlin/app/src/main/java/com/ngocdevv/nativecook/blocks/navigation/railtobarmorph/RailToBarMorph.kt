package com.ngocdevv.nativecook.blocks.navigation.railtobarmorph

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A destination: its name, its line icon, and the solid icon that tints the lit icon of the bar. */
class MorphItem(val id: String, val label: String, val icon: ImageVector, val solid: ImageVector)

private val Items = listOf(
    MorphItem("home", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    MorphItem("explore", "Explore", Icons.Outlined.Explore, Icons.Filled.Explore),
    MorphItem("notifications", "Notifications", Icons.Outlined.Notifications, Icons.Filled.Notifications),
    MorphItem("messages", "Messages", Icons.Outlined.Mail, Icons.Filled.Mail),
    MorphItem("bookmarks", "Bookmarks", Icons.Outlined.BookmarkBorder, Icons.Filled.Bookmark),
    MorphItem("lists", "Lists", Icons.Outlined.Description, Icons.Filled.Description),
    MorphItem("profile", "Profile", Icons.Outlined.Person, Icons.Filled.Person),
)

private enum class Shape { List, Rail, Bar }

private val Field = Color(0xFF2F6BFF)
private val Ink = Color(0xFF111111)
private val CapsuleFill = Color(0xFFE8F0FF)
private val Scrim = Color(0xFF081030)
private const val SCRIM_ALPHA = 0.42f
private val Halo = Color.White.copy(alpha = 0.05f)
private val Card = Color.White.copy(alpha = 0.14f)
private val Line = Color.White.copy(alpha = 0.45f)
private val DotFill = rgba(17, 17, 17, 0.12f)
private const val FILL_ALPHA = 0.2f

private const val HIT = 44f
private const val CAPSULE_X = 16f
private const val CAPSULE = 44f
private const val CAPSULE_PAD = 20f
private const val DOT = 10f
private const val DOT_DROP = 16f
private const val MARGIN = 20f
private const val TITLE_Y = 56f
private val Cards = listOf(150, 96, 96)

// The two lines on a card, as parts of its width; a destination starts at its own place in the table.
private val Lines = listOf(listOf(0.62f, 0.4f), listOf(0.48f, 0.3f), listOf(0.7f, 0.52f))

// The two soft circles behind the content: radii, and how far down the screen their centre is.
private val Halos = listOf(260f, 420f)
private const val HALO_Y = 0.78f

// The list narrows to the rail on a spring: mass 1, stiffness 200, damping 24.
private val WideSpring = spring(dampingRatio = 0.8485f, stiffness = 200f, visibilityThreshold = 0.0005f)

// A label fades and slides over LABEL_IN seconds, LABEL_GAP after the one above it.
private const val LABEL_IN = 0.25f
private const val LABEL_GAP = 0.02f
private const val LABEL_SHIFT = 10f

// From the bar, the labels wait for the list to open; by hand the list collapses to the rail and,
// a moment later, on to the bar. In milliseconds.
private const val LABELS_AFTER = 550
private const val COLLAPSE_AFTER = 250

// The highlight moves in SELECT milliseconds; its leading edge arrives after LEAD of that, so the
// capsule stretches on the way.
private const val SELECT = 250
private const val LEAD = 0.64f

// A tap in the bar: the icon hops for HOP_UP of the time and lands with a squash.
private const val HOP_FOR = 400
private const val HOP_UP = 0.7f
private const val HOP = 8f
private const val SQUASH = 0.1f
private const val SWAP = 250

// Reduced motion: the shapes jump and the surface fades back in.
private const val FADE = 200

// A swipe to the right that starts in this band brings the list out of the bar.
private const val EDGE = 40f
private const val SWIPE = 20f

private const val USER_HOLD = 3000L
private const val LOOP = 10f

private class Step(val at: Float, val shape: Shape? = null, val current: Int? = null)

// The loop that plays by itself: what is due when, in seconds from the start of a loop.
private val Script = listOf(
    Step(0.8f, current = 1),
    Step(1.5f, current = 2),
    Step(2.3f, shape = Shape.Rail),
    Step(3.2f, shape = Shape.Bar),
    Step(4.4f, current = 3),
    Step(5.4f, current = 4),
    Step(6.4f, current = 0),
    Step(7.8f, shape = Shape.List),
)

private fun type(size: Int, line: Int, color: Color, weight: FontWeight) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** A disc with its top right quarter cut out, [MARK] dp across, centred on a point given in dp. */
private fun DrawScope.mark(color: Color, x: Float, y: Float, alpha: Float = 1f) {
    val side = MARK.dp.toPx()
    drawArc(color, 0f, 270f, useCenter = true, topLeft = Offset(x.dp.toPx() - side / 2f, y.dp.toPx() - side / 2f), size = Size(side, side), alpha = alpha)
}

/**
 * One white navigation surface in three shapes: a labelled list, a narrow icon rail and a bottom
 * bar. The surface itself morphs from one to the next, and every icon is the same node all the
 * way: it flies from its row in the column to its place in the bar along a curve.
 *
 * @param items the destinations, in the order of the list.
 * @param barItems how many of them, from the top, the bar holds.
 * @param color the field's colour, which also marks the current destination.
 * @param autoPlay goes through the three shapes by itself, back at the list every [LOOP] seconds.
 */
@Composable
fun RailToBarMorph(
    modifier: Modifier = Modifier,
    items: List<MorphItem> = Items,
    barItems: Int = 5,
    color: Color = Field,
    autoPlay: Boolean = false,
    onSelect: ((MorphItem) -> Unit)? = null,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    // Everything the functions below read is state: they are called long after this composition.
    val reduced by rememberUpdatedState(rememberReducedMotion())
    val list by rememberUpdatedState(items)
    val picked by rememberUpdatedState(onSelect)
    val docked by rememberUpdatedState(min(barItems, items.size))

    var shape by remember { mutableStateOf(Shape.List) }
    var current by remember { mutableIntStateOf(0) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }

    // 1 for the list, 0 for the rail and the bar.
    val wide = remember { Animatable(1f) }
    // 0 for the list and the rail, 1 for the bar: a clock, eased where it is read.
    val dock = remember { Animatable(0f) }
    // The highlight on its way from one row to another.
    var selFrom by remember { mutableIntStateOf(0) }
    var selTo by remember { mutableIntStateOf(0) }
    val selClock = remember { Animatable(1f) }
    // The icon that hops, and the clock of its hop.
    var hopIndex by remember { mutableIntStateOf(-1) }
    val hop = remember { Animatable(1f) }
    // The clock of the labels leaving or coming back, and which of the two it is.
    val labels = remember { Animatable(1f) }
    var showing by remember { mutableStateOf(true) }
    // The whole surface, which fades back in after a jump under reduced motion.
    val fade = remember { Animatable(1f) }

    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

    /** How much of the field's colour destination [index] has: all of it while it is current. */
    fun tint(index: Int): Float {
        val c = selClock.value
        return if (selTo == index) c else if (selFrom == index) 1f - c else 0f
    }

    /** The clock of icon [index]'s hop: 1 when it is not hopping. */
    fun hopOf(index: Int) = if (hopIndex == index) hop.value else 1f

    fun lift(index: Int): Float {
        val u = hopOf(index)
        return if (u < HOP_UP) sin(PI.toFloat() * u / HOP_UP) else 0f
    }

    fun reshape(to: Shape) {
        if (to == shape) return
        val from = shape
        shape = to
        val toWide = if (to == Shape.List) 1f else 0f
        val toDock = if (to == Shape.Bar) 1f else 0f
        if (reduced) {
            scope.launch {
                wide.snapTo(toWide)
                dock.snapTo(toDock)
                labels.snapTo(1f)
                showing = to == Shape.List
                fade.snapTo(0f)
                fade.animateTo(1f, tween(FADE))
            }
            return
        }
        if ((from == Shape.List) != (to == Shape.List)) {
            val total = LABEL_IN + (list.size - 1) * LABEL_GAP
            val wait = if (from == Shape.Bar) LABELS_AFTER else 0
            scope.launch {
                // The clock and its direction change together, so no frame shows one without the other.
                labels.snapTo(0f)
                showing = to == Shape.List
                labels.animateTo(1f, tween((total * 1000).toInt(), delayMillis = wait, easing = LinearEasing))
            }
            scope.launch { wide.animateTo(toWide, WideSpring) }
        }
        if ((from == Shape.Bar) != (to == Shape.Bar)) {
            val wait = if (from == Shape.List) COLLAPSE_AFTER else 0
            val takes = if (to == Shape.Bar) DOCK_IN else DOCK_OUT
            scope.launch { dock.animateTo(toDock, tween((takes * 1000).toInt(), delayMillis = wait, easing = LinearEasing)) }
        }
    }

    fun go(index: Int, hopping: Boolean) {
        if (index !in list.indices) return
        if (index != current) {
            val from = current
            scope.launch {
                selClock.snapTo(0f)
                selFrom = from
                selTo = index
                selClock.animateTo(1f, tween(SELECT, easing = LinearEasing))
            }
            current = index
            picked?.invoke(list[index])
        }
        if (hopping && shape == Shape.Bar && index < docked && !reduced) {
            scope.launch {
                hop.snapTo(0f)
                hopIndex = index
                hop.animateTo(1f, tween(HOP_FOR, easing = LinearEasing))
            }
        }
    }

    /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
    fun touched() {
        touches++
    }

    fun pick(index: Int) {
        touched()
        tick()
        go(index, hopping = true)
        // A row of the list goes there and folds the list away.
        if (shape == Shape.List) reshape(Shape.Bar)
    }

    fun unfold() {
        if (shape != Shape.Bar) return
        touched()
        tick()
        reshape(Shape.List)
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay(USER_HOLD)
            // Back to rest; the list is open again by the time the loop first moves.
            go(0, hopping = false)
            reshape(Shape.List)
        }
        var origin = System.nanoTime()
        suspend fun until(seconds: Float) = delay(max(0L, (origin + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            // Each step sets the state that is due at its time, whatever the steps before it did.
            for (step in Script) {
                until(step.at)
                if (step.shape != null) reshape(step.shape)
                if (step.current != null) go(step.current, hopping = true)
            }
            until(LOOP)
            origin += (LOOP * 1e9f).toLong()
        }
    }

    // The surface covers the top left corner, so the status bar is hidden while the block is open.
    // The navigation bar's icons are light over the field and dark over the white bar.
    val overBar by remember { derivedStateOf { dock.value > 0.5f } }
    val bars = remember(view) { view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) } }
    DisposableEffect(bars) {
        val navigation = bars?.isAppearanceLightNavigationBars
        bars?.hide(WindowInsetsCompat.Type.statusBars())
        onDispose {
            bars?.show(WindowInsetsCompat.Type.statusBars())
            if (navigation != null) bars.isAppearanceLightNavigationBars = navigation
        }
    }
    LaunchedEffect(bars, overBar) { bars?.isAppearanceLightNavigationBars = overBar }

    val measurer = rememberTextMeasurer()
    // The labels' widths in dp, which the capsule hugs.
    val widths = remember(items, density) { items.map { measurer.measure(it.label, type(17, 22, Ink, FontWeight.Medium)).size.width / density.density } }
    val panelPath = remember { Path() }

    BoxWithConstraints(
        modifier.fillMaxSize().background(color).pointerInput(Unit) {
            var from = 0f
            var across = 0f
            detectHorizontalDragGestures(
                onDragStart = { at ->
                    from = at.x
                    across = 0f
                },
                onHorizontalDrag = { _, amount ->
                    across += amount
                    if (from < EDGE.dp.toPx() && across > SWIPE.dp.toPx()) unfold()
                },
            )
        },
    ) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val frame = Frame(maxWidth.value, maxHeight.value, insets.calculateTopPadding().value, BAR + insets.calculateBottomPadding().value)

        // The field's two soft circles.
        Box(
            Modifier.fillMaxSize().drawBehind {
                for (radius in Halos) drawCircle(Halo, radius.dp.toPx(), Offset(size.width / 2f, size.height * HALO_Y))
            },
        )

        // The content clears the rail, and takes the whole width over the bar.
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val left = mix(RAIL + MARGIN, MARGIN, easeInOut(dock.value)).dp.roundToPx()
                    val width = (constraints.maxWidth - left - MARGIN.dp.roundToPx()).coerceAtLeast(0)
                    val placeable = measurable.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = constraints.maxHeight))
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(left, (frame.top + TITLE_Y).dp.roundToPx()) }
                }
                .then(if (shape == Shape.List) Modifier.clearAndSetSemantics {} else Modifier),
        ) {
            Content(items.getOrNull(current)?.label.orEmpty(), current)
        }

        // The mark twice at one place: white on the field, and in colour on the panel, which
        // hides the white one. Then the scrim that dims the field behind the list.
        Box(
            Modifier.fillMaxSize().drawBehind {
                mark(Color.White, markX(wide.value, dock.value, frame), frame.top + MARK_Y, alpha = fade.value)
                drawRect(Scrim, alpha = SCRIM_ALPHA * clamp(wide.value) * (1f - easeInOut(dock.value)))
            },
        )

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = fade.value }
                .drawWithContent {
                    val panel = panelAt(wide.value, dock.value, docked, frame)
                    panelPath.rewind()
                    panelPath.addRoundRect(
                        RoundRect(
                            left = 0f,
                            top = panel.top.dp.toPx(),
                            right = panel.width.dp.toPx(),
                            bottom = size.height,
                            topLeftCornerRadius = CornerRadius(panel.topLeft.dp.toPx()),
                            topRightCornerRadius = CornerRadius(panel.topRight.dp.toPx()),
                            bottomRightCornerRadius = CornerRadius(panel.bottomRight.dp.toPx()),
                            bottomLeftCornerRadius = CornerRadius.Zero,
                        ),
                    )
                    clipPath(panelPath) {
                        drawRect(Color.White)

                        val c = selClock.value
                        val to = selTo.toFloat()
                        val lead = if (reduced) to else mix(selFrom.toFloat(), to, easeOut(c / LEAD))
                        val trail = if (reduced) to else mix(selFrom.toFloat(), to, easeInOut(c))
                        val middle = (lead + trail) / 2f
                        val above = min(widths.lastIndex, middle.toInt())
                        val below = min(widths.lastIndex, above + 1)
                        val width = LABEL_X + mix(widths[above], widths[below], middle - above) + CAPSULE_PAD - CAPSULE_X
                        drawRoundRect(
                            CapsuleFill,
                            topLeft = Offset(CAPSULE_X.dp.toPx(), (frame.top + ROW_Y + min(lead, trail) * PITCH - CAPSULE / 2f).dp.toPx()),
                            size = Size(width.dp.toPx(), (CAPSULE + abs(lead - trail) * PITCH).dp.toPx()),
                            cornerRadius = CornerRadius((CAPSULE / 2f).dp.toPx()),
                            alpha = clamp(wide.value) * (1f - easeInOut(dock.value)),
                        )

                        this@drawWithContent.drawContent()
                        mark(color, markX(wide.value, dock.value, frame), frame.top + MARK_Y)
                    }
                }
                .clearAndSetSemantics {},
        ) {
            // The rows' names. They leave and come back one after another, from the top.
            items.forEachIndexed { index, item ->
                Box(
                    Modifier
                        .offset(LABEL_X.dp, (frame.top + ROW_Y + index * PITCH - 11f).dp)
                        .graphicsLayer {
                            val total = LABEL_IN + (list.size - 1) * LABEL_GAP
                            val arrived = easeOut((labels.value * total - index * LABEL_GAP) / LABEL_IN)
                            val shown = if (showing) arrived else 1f - arrived
                            alpha = shown
                            translationX = if (reduced) 0f else -LABEL_SHIFT.dp.toPx() * (1f - shown)
                        },
                ) {
                    BasicText(item.label, style = type(17, 22, Ink, FontWeight.Medium), maxLines = 1)
                    BasicText(item.label, Modifier.graphicsLayer { alpha = tint(index) }, style = type(17, 22, color, FontWeight.Medium), maxLines = 1)
                }
            }
        }

        // The icons: the same node in the three shapes.
        items.forEachIndexed { index, item ->
            val enabled = shape == Shape.Rail || (shape == Shape.Bar && index < docked)
            Box(
                Modifier
                    .size(HIT.dp)
                    .graphicsLayer {
                        val at = iconAt(index, docked, wide.value, dock.value, frame)
                        translationX = (at.x - HIT / 2f).dp.toPx()
                        translationY = (at.y - HIT / 2f).dp.toPx()
                        alpha = at.alpha * fade.value
                    }
                    .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, role = Role.Tab) { pick(index) }
                    .then(if (enabled) Modifier.semantics { contentDescription = item.label; selected = index == current } else Modifier.clearAndSetSemantics {}),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .offset(y = DOT_DROP.dp)
                        .size(DOT.dp)
                        .graphicsLayer {
                            alpha = tint(index) * iconAt(index, docked, wide.value, dock.value, frame).way
                            scaleX = 1f - 0.6f * lift(index)
                            scaleY = scaleX
                        }
                        .background(DotFill, CircleShape),
                )
                Box(
                    Modifier.size(ICON.dp).graphicsLayer {
                        val at = iconAt(index, docked, wide.value, dock.value, frame)
                        val u = hopOf(index)
                        val squash = if (u < HOP_UP) 1f else 1f - SQUASH * sin(PI.toFloat() * (u - HOP_UP) / (1f - HOP_UP))
                        // The squash is against the ground: the icon's foot stays where it is.
                        translationY = (-HOP * lift(index) + (1f - squash) * ICON / 2f).dp.toPx()
                        scaleX = at.scale
                        scaleY = at.scale * squash
                    },
                ) {
                    // The solid icon shows through a window that opens from the bottom.
                    Icon(
                        item.solid,
                        contentDescription = null,
                        tint = color.copy(alpha = FILL_ALPHA),
                        modifier = Modifier.fillMaxSize().drawWithContent {
                            val filled = tint(index) * iconAt(index, docked, wide.value, dock.value, frame).way
                            clipRect(top = size.height * (1f - filled)) { this@drawWithContent.drawContent() }
                        },
                    )
                    Icon(item.icon, contentDescription = null, tint = Ink, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - tint(index) })
                    Icon(item.icon, contentDescription = null, tint = color, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = tint(index) })
                }
            }
        }

        if (shape == Shape.List) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(start = LIST.dp)
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                        touched()
                        reshape(Shape.Bar)
                    }
                    .semantics { contentDescription = "Close menu" },
            )
            items.forEachIndexed { index, item ->
                Box(
                    Modifier
                        .offset(y = (frame.top + ROW_Y + index * PITCH - PITCH / 2f).dp)
                        .size(LIST.dp, PITCH.dp)
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { pick(index) }
                        .semantics { contentDescription = item.label; selected = index == current },
                )
            }
        }

        if (shape == Shape.Bar) {
            Box(
                Modifier
                    .offset((frame.width / 2f - HIT / 2f).dp, (frame.top + MARK_Y - HIT / 2f).dp)
                    .size(HIT.dp)
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { unfold() }
                    .semantics { contentDescription = "Menu" },
            )
        }
    }
}

/** The sample screen behind the navigation: the destination's name over three cards. */
@Composable
private fun Content(title: String, index: Int) {
    Crossfade(index to title, animationSpec = tween(SWAP, easing = LinearEasing), label = "content") { (shown, name) ->
        Column {
            BasicText(name, Modifier.padding(bottom = 20.dp), style = type(34, 41, Color.White, FontWeight.Bold), maxLines = 1)
            Cards.forEachIndexed { card, height ->
                val lines = Lines[(shown + card) % Lines.size]
                Column(
                    Modifier.padding(bottom = 12.dp).fillMaxWidth().height(height.dp).background(Card, RoundedCornerShape(22.dp)).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
                ) {
                    for (part in lines) Box(Modifier.fillMaxWidth(part).height(10.dp).background(Line, CircleShape))
                }
            }
        }
    }
}
