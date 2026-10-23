package com.ngocdevv.nativecook.blocks.navigation.actiongridtabbar

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

data class TabItem(val label: String, val icon: ImageVector)

/** An action of the grid: an icon, or a short text such as "Aa" in its place. */
data class Action(val label: String, val icon: ImageVector? = null, val glyph: String? = null)

/**
 * @param ink Titles and icons.
 * @param shape The placeholder shapes on a card.
 */
data class Theme(
    val dark: Boolean,
    val page: Color,
    val ink: Color,
    val card: Color,
    val shape: Color,
    val bar: Color,
    val highlight: Color,
    val panel: Color,
    val cell: Color,
    val label: Color,
)

val LightTheme = Theme(
    dark = false, page = Color(0xFFF3F3F3), ink = Color(0xFF111111), card = Color(0xFFFBFBFB), shape = Color(0xFFE9E9E9),
    bar = Color(0xFFFBFBFB), highlight = Color(0xFFEDEDED), panel = Color(0xFFFAFAFA), cell = Color(0xFFEFEFEF), label = Color(0xFF8A8A8E),
)

val DarkTheme = Theme(
    dark = true, page = Color(0xFF0E0D10), ink = Color.White, card = Color(0xFF1A1A1D), shape = Color(0xFF2A2A2E),
    bar = Color(0xFF232425), highlight = Color(0xFF343437), panel = Color(0xFF1C1C1F), cell = Color(0xFF2A2A2D), label = Color(0xFF8A8A8E),
)

val SampleTabs = listOf(
    TabItem("Home", Icons.Outlined.Home),
    TabItem("Inbox", Icons.Outlined.Inbox),
    TabItem("Alerts", Icons.Outlined.Notifications),
    TabItem("Apps", Icons.Outlined.Layers),
)

val SampleActions = listOf(
    Action("Trim", Icons.Outlined.ContentCut),
    Action("Crop", Icons.Outlined.Crop),
    Action("Enhance", Icons.Outlined.AutoFixHigh),
    Action("Text", glyph = "Aa"),
    Action("Audio", Icons.Outlined.MusicNote),
    Action("Speed", Icons.Outlined.Speed),
    Action("Duplicate", Icons.Outlined.ContentCopy),
    Action("Undo", Icons.AutoMirrored.Outlined.Undo),
    Action("Share", Icons.Outlined.IosShare),
    Action("Save", Icons.Outlined.BookmarkBorder),
    Action("Delete", Icons.Outlined.Delete),
)

// The heights of the placeholder cards on each tab's page, so that a new tab is a new page.
private val Pages = listOf(listOf(120f, 72f, 72f, 72f), listOf(72f, 72f, 72f, 72f, 72f), listOf(72f, 120f, 72f, 72f), listOf(120f, 120f, 72f))

/** The period of the autoPlay loop, in seconds, and what it presses at each moment: a tab, or the plus (null). */
internal const val LOOP = 10f
private val Steps: List<Pair<Float, Int?>> = listOf(1.0f to 1, 2.0f to 2, 3.0f to 3, 4.2f to 0, 5.4f to null, 8.4f to null)

// After a touch the loop waits this long, returns to rest and starts over.
private const val USER_HOLD = 3f

private const val PAGE_MARGIN = 20f
private const val TITLE_TOP = 12f
private const val TITLE_HEIGHT = 22f
private const val TITLE_GAP = 20f

// The bottom row: the bar, a gap and the round plus button, between these side margins.
private const val MARGIN = 24f
private const val GAP = 8f
private const val BAR_HEIGHT = 48f
private const val PLUS = 48f
private const val PLUS_ARM = 16f
private const val PLUS_STROKE = 1.8f

// The row's bottom edge above the bottom of the screen. A bottom inset taller than a gesture bar's
// is a row of buttons, which the row stays clear of.
private const val BAR_BOTTOM = 24f
private const val GESTURE_INSET = 34f
private const val BUTTONS_CLEARANCE = 8f
private const val ICON = 22f

// The capsule behind the selected tab's icon, and how far the first and the last sit from the bar's ends.
private const val HIGHLIGHT_WIDTH = 60f
private const val HIGHLIGHT_HEIGHT = 40f
private const val HIGHLIGHT_INSET = 4f
private const val HIGHLIGHT_MIN = 36f

// The grid: four columns of 44 dp buttons with a label under each.
private const val COLUMNS = 4
private const val PAD = 16f
private const val PAD_BOTTOM = 14f
private const val CELL = 44f
private const val ROW = 60f
private const val ROW_GAP = 14f
private const val PANEL_RADIUS = 26f

// The shadow of the bar and of the plus: its drop and the sigma of its blur (a 10 dp blur); the
// lifted highlight's is wider (16 dp).
private const val SHADOW_Y = 2f
private const val SHADOW_SIGMA = 5f
private const val LIFT_SHADOW_SIGMA = 8f

/** A spring of mass 1. */
private fun physical(stiffness: Float, damping: Float) = spring(dampingRatio = damping / (2f * sqrt(stiffness)), stiffness = stiffness, visibilityThreshold = 0.001f)

// The highlight's two ends each follow a spring to the new tab. The end in front takes the fast
// one, so the capsule is about a third wider in mid-flight between neighbours.
private val Fast = physical(520f, 38f)
private val Slow = physical(230f, 26f)

// Milliseconds. It lifts while it moves: up in LIFT_UP, back in LIFT_DOWN.
private const val LIFT = 0.08f
private const val LIFT_UP = 140
private const val LIFT_DOWN = 240

// The page: the title cross-fades, the cards also slide.
private const val PAGE_FADE = 300
private const val PAGE_SLIDE = 12f

// The bar stretches into the panel on one spring and shrinks back on a firmer one.
private val Open = physical(130f, 20f)
private val Close = physical(200f, 24f)

// The tab icons are gone by this share of the way.
private const val TABS_GONE = 0.5f

// The plus turns to a cross on a spring that overshoots, and swells for a moment as the grid opens.
private val Turn = physical(300f, 18f)
private const val TURN_DEGREES = 45f
private const val SWELL = 1.25f
private const val SWELL_UP = 160
private const val SWELL_DOWN = 260

// The rows of the grid come in from the bottom one, each fading in while it grows, and leave together.
private const val CELLS_AFTER = 120
private const val STAGGER = 20
private const val CELLS_IN = 300
private const val CELLS_OUT = 150
private const val CELLS_SCALE = 0.9f
private const val PRESS_SCALE = 0.94f

// A cubic ease-out.
private val Ease = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

private fun text(size: Float, lineHeight: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    textAlign = TextAlign.Center,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** An icon drawn in a box of [size] dp, tinted while drawing. */
@Composable
private fun Glyph(icon: ImageVector, size: Float, color: Color) {
    val painter = rememberVectorPainter(icon)
    Box(Modifier.size(size.dp).drawBehind { with(painter) { draw(this@drawBehind.size, colorFilter = ColorFilter.tint(color)) } })
}

/**
 * One row of the grid: it fades in and grows when the grid opens, [after] milliseconds behind the
 * shape, and the rows leave together, faster. With animations off in the system it appears at once.
 */
@Composable
private fun Reveal(shown: Boolean, after: Int, still: Boolean, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(shown) {
        if (shown) {
            progress.animateTo(1f, tween(CELLS_IN, delayMillis = after, easing = Ease))
        } else {
            progress.animateTo(0f, tween(CELLS_OUT, easing = Ease))
        }
    }
    Box(
        Modifier.graphicsLayer {
            val scale = if (still) 1f else CELLS_SCALE + (1f - CELLS_SCALE) * progress.value
            alpha = progress.value
            scaleX = scale
            scaleY = scale
        },
    ) { content() }
}

@Composable
private fun Cell(action: Action, theme: Theme, enabled: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) PRESS_SCALE else 1f, tween(120), label = "press")
    Column(
        Modifier
            .size(CELL.dp, ROW.dp)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = action.label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(CELL.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .background(theme.cell, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (action.icon != null) {
                Glyph(action.icon, ICON, theme.ink)
            } else {
                BasicText(action.glyph.orEmpty(), style = text(17f, 22f, FontWeight.Medium, theme.ink), maxLines = 1)
            }
        }
        // Wider than its cell, so a long label is not cut.
        BasicText(
            action.label,
            style = text(10f, 12f, FontWeight.Normal, theme.label),
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp).requiredWidth((CELL + 20f).dp).clearAndSetSemantics {},
        )
    }
}

/**
 * A page with a floating tab bar and a round plus button next to it. The highlight behind the
 * selected tab's icon slides to a new tab stretched, its front end first. The plus turns into a
 * cross while the bar itself stretches upward into a panel holding a grid of actions; pressing
 * an action, the cross or the page shrinks it back.
 *
 * @param actions The actions of the grid, four to a row.
 * @param initialTab The tab that is selected at first, and the one autoPlay comes back to.
 * @param autoPlay Goes through the tabs and opens the grid by itself every [LOOP] seconds.
 * @param onToggle Called when the grid opens (true) and closes (false).
 */
@Composable
fun ActionGridTabBar(
    tabs: List<TabItem> = SampleTabs,
    actions: List<Action> = SampleActions,
    theme: Theme = LightTheme,
    initialTab: Int = 0,
    autoPlay: Boolean = false,
    onSelect: ((index: Int) -> Unit)? = null,
    onToggle: ((open: Boolean) -> Unit)? = null,
    onAction: ((index: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    // The loop outlives a recomposition: it reads what the caller passes through these.
    val home by rememberUpdatedState(initialTab)
    val chosen by rememberUpdatedState(onSelect)
    val toggled by rememberUpdatedState(onToggle)

    var selected by remember { mutableIntStateOf(initialTab) }
    var open by remember { mutableStateOf(false) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }

    // Where the highlight's left and right ends are, in tabs. These are read while drawing or
    // measuring only. With animations off in the system they all land at once.
    val left = remember { Animatable(initialTab.toFloat()) }
    val right = remember { Animatable(initialTab.toFloat()) }
    val lift = remember { Animatable(0f) }
    // How far the bar is stretched into the panel, 0 to 1.
    val stretch = remember { Animatable(0f) }
    val turn = remember { Animatable(0f) }
    val swell = remember { Animatable(1f) }

    // Dark icons on the status bar over the light theme, light ones over the dark theme.
    DisposableEffect(view, theme.dark) {
        val controller = view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = !theme.dark
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }

    fun select(index: Int, byHand: Boolean) {
        if (open || index == selected) return
        // The loop that plays by itself is silent.
        if (byHand) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        val forward = index > selected
        val x = index.toFloat()
        scope.launch { left.animateTo(x, if (forward) Slow else Fast) }
        scope.launch { right.animateTo(x, if (forward) Fast else Slow) }
        scope.launch {
            lift.animateTo(1f, tween(LIFT_UP, easing = Ease))
            lift.animateTo(0f, tween(LIFT_DOWN, easing = Ease))
        }
        selected = index
        chosen?.invoke(index)
    }

    fun toggle(byHand: Boolean) {
        val next = !open
        if (byHand) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        open = next
        scope.launch { stretch.animateTo(if (next) 1f else 0f, if (next) Open else Close) }
        scope.launch { turn.animateTo(if (next) TURN_DEGREES else 0f, Turn) }
        if (next) {
            scope.launch {
                swell.animateTo(SWELL, tween(SWELL_UP, easing = Ease))
                swell.animateTo(1f, tween(SWELL_DOWN, easing = Ease))
            }
        }
        toggled?.invoke(next)
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            // Rest is the grid closed on the first tab.
            if (open) toggle(byHand = false)
            select(home, byHand = false)
        }
        // Every step is timed from the loop's origin, so a late wake does not push the next one back.
        var origin = System.nanoTime()
        suspend fun until(at: Float) = delay(max(0L, (origin + (at * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            for ((at, tab) in Steps) {
                until(at)
                if (tab == null) toggle(byHand = false) else select(tab, byHand = false)
            }
            until(LOOP)
            origin += (LOOP * 1e9f).toLong()
        }
    }

    val shadow = remember { SoftRoundRect(Color.Black.copy(alpha = 0.06f), SHADOW_SIGMA) }
    val liftShadow = remember { SoftRoundRect(Color.Black.copy(alpha = 0.12f), LIFT_SHADOW_SIGMA) }

    BoxWithConstraints(modifier.fillMaxSize().background(theme.page)) {
        val bars = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
        val navigation = bars.calculateBottomPadding().value
        val bottom = if (navigation > GESTURE_INSET) navigation + BUTTONS_CLEARANCE else BAR_BOTTOM
        val barWidth = maxWidth.value - MARGIN * 2 - GAP - PLUS
        val rows = (actions.size + COLUMNS - 1) / COLUMNS
        val panelHeight = PAD + rows * ROW + (rows - 1) * ROW_GAP + PAD_BOTTOM
        // The centre of the first tab from the bar's left edge, and the distance between two tabs.
        val first = HIGHLIGHT_INSET + HIGHLIGHT_WIDTH / 2f
        val pitch = (barWidth - first * 2f) / max(1, tabs.size - 1)

        // A tab's page: its name, which cross-fades, and placeholder cards, which also slide.
        tabs.forEachIndexed { t, tab ->
            val shown = t == selected
            val fade = animateFloatAsState(if (shown) 1f else 0f, tween(PAGE_FADE, easing = Ease), label = "page")
            val aside = animateFloatAsState(if (shown) 0f else if (t < selected) -PAGE_SLIDE else PAGE_SLIDE, tween(PAGE_FADE, easing = Ease), label = "slide")
            Column(
                Modifier
                    .matchParentSize()
                    .then(if (shown) Modifier else Modifier.clearAndSetSemantics {})
                    .padding(top = bars.calculateTopPadding() + TITLE_TOP.dp),
            ) {
                BasicText(
                    tab.label,
                    style = text(17f, TITLE_HEIGHT, FontWeight.SemiBold, theme.ink),
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().height(TITLE_HEIGHT.dp).graphicsLayer { alpha = fade.value }.semantics { heading() },
                )
                Spacer(Modifier.height(TITLE_GAP.dp))
                Column(
                    Modifier
                        .graphicsLayer {
                            alpha = fade.value
                            translationX = aside.value.dp.toPx()
                        }
                        .padding(horizontal = PAGE_MARGIN.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    for (height in Pages[t % Pages.size]) {
                        Row(
                            Modifier.fillMaxWidth().height(height.dp).background(theme.card, RoundedCornerShape(20.dp)).padding(horizontal = 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(36.dp).background(theme.shape, CircleShape))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(120.dp, 8.dp).background(theme.shape, CircleShape))
                                Box(Modifier.size(76.dp, 6.dp).background(theme.shape, CircleShape))
                            }
                        }
                    }
                }
            }
        }

        // The page closes the grid.
        if (open) {
            Box(
                Modifier.matchParentSize().clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = "Close") {
                    touches++
                    toggle(byHand = true)
                },
            )
        }

        // The bar and the panel are one shape: its height, its corners and its fill follow the stretch.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = MARGIN.dp, bottom = bottom.dp)
                .width(barWidth.dp)
                .layout { measurable, constraints ->
                    val tall = (BAR_HEIGHT + (panelHeight - BAR_HEIGHT) * stretch.value).dp.roundToPx()
                    val placeable = measurable.measure(constraints.copy(minHeight = tall, maxHeight = tall))
                    layout(placeable.width, tall) { placeable.place(0, 0) }
                }
                .drawBehind {
                    val radius = BAR_HEIGHT / 2f + (PANEL_RADIUS - BAR_HEIGHT / 2f) * stretch.value
                    scale(density, density, pivot = Offset.Zero) {
                        drawIntoCanvas { shadow.draw(it.nativeCanvas, 0f, SHADOW_Y, size.width / density, size.height / density, radius) }
                    }
                    drawRoundRect(lerp(theme.bar, theme.panel, stretch.value.coerceIn(0f, 1f)), cornerRadius = CornerRadius(radius.dp.toPx()))
                }
                .graphicsLayer {
                    clip = true
                    shape = RoundedCornerShape((BAR_HEIGHT / 2f + (PANEL_RADIUS - BAR_HEIGHT / 2f) * stretch.value).dp.toPx())
                },
        ) {
            // The grid keeps its place: the shape's top edge uncovers it. Closed, it is invisible but
            // still there, so its buttons are switched off.
            Column(
                Modifier
                    .matchParentSize()
                    .wrapContentHeight(Alignment.Bottom, unbounded = true)
                    .height(panelHeight.dp)
                    .then(if (open) Modifier else Modifier.clearAndSetSemantics {})
                    .padding(start = PAD.dp, end = PAD.dp, top = PAD.dp),
                verticalArrangement = Arrangement.spacedBy(ROW_GAP.dp),
            ) {
                for (row in 0 until rows) {
                    // The bottom row comes first.
                    Reveal(open, CELLS_AFTER + (rows - 1 - row) * STAGGER, still) {
                        Row(Modifier.fillMaxWidth().height(ROW.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            for (column in 0 until COLUMNS) {
                                val index = row * COLUMNS + column
                                val action = actions.getOrNull(index)
                                if (action == null) {
                                    Spacer(Modifier.size(CELL.dp, ROW.dp))
                                } else {
                                    Cell(action, theme, enabled = open) {
                                        touches++
                                        onAction?.invoke(index)
                                        toggle(byHand = true)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(BAR_HEIGHT.dp)
                    .graphicsLayer { alpha = 1f - (stretch.value / TABS_GONE).coerceIn(0f, 1f) }
                    .then(if (open) Modifier.clearAndSetSemantics {} else Modifier)
                    .drawBehind {
                        val wide = max(HIGHLIGHT_MIN, abs(right.value - left.value) * pitch + HIGHLIGHT_WIDTH)
                        val centre = Offset(first + (left.value + right.value) / 2f * pitch, BAR_HEIGHT / 2f)
                        val x = centre.x - wide / 2f
                        val y = (BAR_HEIGHT - HIGHLIGHT_HEIGHT) / 2f
                        // Drawn in dp. The capsule lifts while it moves: a little larger, over a wider shadow.
                        scale(density, density, pivot = Offset.Zero) {
                            scale(1f + LIFT * lift.value, centre) {
                                drawIntoCanvas { liftShadow.draw(it.nativeCanvas, x, y + SHADOW_Y, wide, HIGHLIGHT_HEIGHT, HIGHLIGHT_HEIGHT / 2f, alpha = lift.value) }
                                drawRoundRect(theme.highlight, Offset(x, y), Size(wide, HIGHLIGHT_HEIGHT), CornerRadius(HIGHLIGHT_HEIGHT / 2f))
                            }
                        }
                    },
            ) {
                tabs.forEachIndexed { t, tab ->
                    val current = t == selected
                    Box(
                        Modifier
                            .offset(x = (first + t * pitch - HIGHLIGHT_WIDTH / 2f).dp)
                            .size(HIGHLIGHT_WIDTH.dp, BAR_HEIGHT.dp)
                            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = !open, role = Role.Tab) {
                                touches++
                                select(t, byHand = true)
                            }
                            .semantics {
                                contentDescription = tab.label
                                this.selected = current
                            },
                        contentAlignment = Alignment.Center,
                    ) { Glyph(tab.icon, ICON, theme.ink) }
                }
            }
        }

        // The plus button, which turns into a cross.
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = MARGIN.dp, bottom = bottom.dp)
                .size(PLUS.dp)
                .graphicsLayer {
                    scaleX = swell.value
                    scaleY = swell.value
                }
                .drawBehind {
                    scale(density, density, pivot = Offset.Zero) {
                        drawIntoCanvas { shadow.draw(it.nativeCanvas, 0f, SHADOW_Y, PLUS, PLUS, PLUS / 2f) }
                        drawCircle(theme.bar, PLUS / 2f, Offset(PLUS / 2f, PLUS / 2f))
                        rotate(turn.value, Offset(PLUS / 2f, PLUS / 2f)) {
                            val from = (PLUS - PLUS_ARM) / 2f
                            val to = (PLUS + PLUS_ARM) / 2f
                            drawLine(theme.ink, Offset(from, PLUS / 2f), Offset(to, PLUS / 2f), PLUS_STROKE, StrokeCap.Round)
                            drawLine(theme.ink, Offset(PLUS / 2f, from), Offset(PLUS / 2f, to), PLUS_STROKE, StrokeCap.Round)
                        }
                    }
                }
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                    touches++
                    toggle(byHand = true)
                }
                .semantics { contentDescription = if (open) "Close" else "Actions" },
        )
    }
}
