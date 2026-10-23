package com.ngocdevv.nativecook.blocks.navigation.spotlighttabbar

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** A tab: its title, and a solid glyph in a 30 × 30 box as SVG path data made of M, L, C and Z, filled even-odd. */
class SpotlightTab(val title: String, glyph: String) {
    internal val glyph = PathParser().parsePathString(glyph).toPath().apply { fillType = PathFillType.EvenOdd }
}

object SpotlightGlyphs {
    const val PERSON =
        "M15 4.5C17.76 4.5 20 6.74 20 9.5C20 12.26 17.76 14.5 15 14.5C12.24 14.5 10 12.26 10 9.5C10 6.74 12.24 4.5 15 4.5Z" +
            "M15 17C19.42 17 23 19.01 23 21.5C23 23.99 19.42 26 15 26C10.58 26 7 23.99 7 21.5C7 19.01 10.58 17 15 17Z"
    const val HOUSE =
        "M15 4L26 13.5L26 24C26 25.1 25.1 26 24 26L6 26C4.9 26 4 25.1 4 24L4 13.5Z" +
            "M12 26L12 20C12 18.34 13.34 17 15 17C16.66 17 18 18.34 18 20L18 26Z"
    const val BELL =
        "M15 4C10.6 4 8 7.2 8 11.5L8 16.5L5.5 20.5C5.1 21.2 5.6 22 6.4 22L23.6 22C24.4 22 24.9 21.2 24.5 20.5L22 16.5L22 11.5C22 7.2 19.4 4 15 4Z" +
            "M12 23.5L18 23.5C18 25.2 16.7 26.5 15 26.5C13.3 26.5 12 25.2 12 23.5Z"
    const val CAMERA =
        "M7.5 9L10.2 9L11.6 6.6C11.9 6.2 12.3 6 12.8 6L17.2 6C17.7 6 18.1 6.2 18.4 6.6L19.8 9L22.5 9C24.43 9 26 10.57 26 12.5L26 21.5C26 23.43 24.43 25 22.5 25L7.5 25C5.57 25 4 23.43 4 21.5L4 12.5C4 10.57 5.57 9 7.5 9Z" +
            "M15 12C17.76 12 20 14.24 20 17C20 19.76 17.76 22 15 22C12.24 22 10 19.76 10 17C10 14.24 12.24 12 15 12Z" +
            "M15 14.2C16.55 14.2 17.8 15.45 17.8 17C17.8 18.55 16.55 19.8 15 19.8C13.45 19.8 12.2 18.55 12.2 17C12.2 15.45 13.45 14.2 15 14.2Z"
}

val SampleSpotlightTabs = listOf(
    SpotlightTab("Profile", SpotlightGlyphs.PERSON),
    SpotlightTab("Home", SpotlightGlyphs.HOUSE),
    SpotlightTab("Alerts", SpotlightGlyphs.BELL),
    SpotlightTab("Camera", SpotlightGlyphs.CAMERA),
)

private val Field = Color(0xFFC4C4C4)
private val Pill = Color(0xFF686868)
private val Shade = Color(0xFF3A3A3A)
private val Ink = Color(0xFF3A3A3A)
private val CardFill = Color(0xFFD0D0D0)
private val LineFill = Color(0xFFB6B6B6)
private val Mark = Color(0xFF9A9A9A)

// The pill: its margins, its height, and the first and last glyph's distance from its ends.
private const val SIDE = 28.0
private const val PILL_HEIGHT = 64.0
private const val PILL_LIFT = 6.0
private const val EDGE = 62.0
private const val GLYPH = 30.0

// The lamp lies on the pill's top edge; its beam widens from the lamp to the pill's bottom edge.
private const val LAMP = 62.0
private const val LAMP_HEIGHT = 6.0
private const val BEAM_FOOT = 72.0

// Room around the pill in its canvas, for the shadow and the lamp.
private const val BLEED = 44.0
private const val SHADOW_DROP = 10.0
private const val SHADOW_SIGMA = 11f

// The page.
private const val MARGIN = 24.0
private const val TITLE_TOP = 25.0
private val Cards = listOf(180, 110, 110)
private const val CARD_GAP = 14.0
private const val CARD_MARK = 44.0

/** Seconds. The loop goes once round the tabs: a move every HOP_EVERY from HOP_AT. */
const val LOOP = 9.0
private const val HOP_AT = 2.0
private const val HOP_EVERY = 1.6

// One move: the lamp slides for SLIDE, and SLIDE_PER_TAB longer for each tab after the first. The
// foot of the beam follows LAG behind, so the beam leans back and straightens on arrival.
private const val SLIDE = 0.45
private const val SLIDE_PER_TAB = 0.05
private const val LAG = 0.06

// The beam's strength at rest; on arrival it flares by FLARE and settles in FLARE_FOR.
private const val BEAM = 0.55
private const val FLARE = 0.15
private const val FLARE_FOR = 0.25

// Across the whole bar the lamp dims by DIM in mid-move; a dim left by a move that was cut short
// fades in DIM_FADE.
private const val DIM = 0.6
private const val DIM_FADE = 0.15

// A glyph is lit while the lamp is within this many tabs of it.
private const val REACH = 0.55

// The page: the old one fades out, the new one in, and the mark on its top card a little later.
private const val PAGE_OUT = 0.15
private const val PAGE_IN = 0.25
private const val MARK_AFTER = 0.1
private const val MARK_IN = 0.25

// A finger that takes the lamp away from where it is gets it in this time.
private const val CATCH = 0.15

// The clocks of a move run this long, well past its last change.
private const val REST = 1.2

// With reduced motion the light fades from one tab to the next in this time.
private const val FADE = 0.2

// After a touch the loop waits, then goes back to its first tab and waits for that to end.
private const val USER_HOLD = 3.0
private const val SETTLE = 1.0

/**
 * Where the lamp's move started, or the finger that holds it.
 *
 * @param held A finger holds the lamp: it is at the finger, [offset] tabs aside at first.
 * @param x The lamp's place at the start, in tabs.
 * @param foot The beam's foot at the start.
 * @param dim How dim the lamp was at the start.
 */
private class Lamp(val held: Boolean, val offset: Double, val target: Double, val x: Double, val foot: Double, val dim: Double)

/** The lights on the bar: one, and with reduced motion a second one fading in at the target. */
private class Lights(val x: Double, val foot: Double, val power: Double, val flare: Double, val nextX: Double, val nextPower: Double)

/** Where a change of page started. */
private class Turn(val target: Int, val from: Int, val presence: Double)

private fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)

private fun smooth(t: Double): Double {
    val u = clamp01(t)
    return u * u * (3 - 2 * u)
}

private fun easeOut(t: Double): Double {
    val u = 1 - clamp01(t)
    return 1 - u * u * u
}

private fun easeInOut(t: Double): Double {
    val u = clamp01(t)
    return if (u < 0.5) 4 * u * u * u else 1 - 4 * (1 - u) * (1 - u) * (1 - u)
}

private fun resting(tab: Int) = Lamp(false, 0.0, tab.toDouble(), tab.toDouble(), tab.toDouble(), 0.0)

/** The lights, [t] seconds into a move or a hold. */
private fun lights(lamp: Lamp, t: Double, finger: Double, last: Double, reduced: Boolean): Lights {
    if (lamp.held) {
        val x = (finger + lamp.offset * (1 - easeOut(t / CATCH))).coerceIn(0.0, last)
        return Lights(x, x, 1 - lamp.dim * (1 - clamp01(t / DIM_FADE)), 0.0, x, 0.0)
    }
    if (reduced) {
        val faded = clamp01(t / FADE)
        return Lights(lamp.x, lamp.x, 1 - faded, 0.0, lamp.target, faded)
    }
    val distance = abs(lamp.target - lamp.x)
    val slide = SLIDE + SLIDE_PER_TAB * max(distance - 1, 0.0)
    val far = DIM * clamp01((distance - 1) / 2)
    val dim = max(far * sin(PI * clamp01(t / slide)), lamp.dim * (1 - clamp01(t / DIM_FADE)))
    return Lights(
        x = lamp.x + (lamp.target - lamp.x) * easeInOut(t / slide),
        foot = lamp.foot + (lamp.target - lamp.foot) * easeInOut((t - LAG) / slide),
        power = 1 - dim,
        flare = FLARE * sin(PI * clamp01((t - slide) / FLARE_FOR)),
        nextX = lamp.target,
        nextPower = 0.0,
    )
}

/** How much light falls on a glyph. */
private fun lit(now: Lights, index: Int) =
    clamp01(smooth(1 - abs(now.x - index) / REACH) * now.power + smooth(1 - abs(now.nextX - index) / REACH) * now.nextPower)

/** How much of a page shows, [t] seconds into a change. */
private fun presence(page: Int, turn: Turn, t: Double): Double {
    if (t < PAGE_OUT) return if (page == turn.from) turn.presence * (1 - max(t, 0.0) / PAGE_OUT) else 0.0
    return if (page == turn.target) clamp01((t - PAGE_OUT) / PAGE_IN) else 0.0
}

/** A beam in the pill's box, in dp: from the lamp's width at the top edge to its foot at the bottom. */
private fun DrawScope.drawBeam(path: Path, top: Double, foot: Double, color: Color, opacity: Double) {
    path.rewind()
    path.moveTo((top - LAMP / 2).toFloat(), 0f)
    path.lineTo((top + LAMP / 2).toFloat(), 0f)
    path.lineTo((foot + BEAM_FOOT / 2).toFloat(), PILL_HEIGHT.toFloat())
    path.lineTo((foot - BEAM_FOOT / 2).toFloat(), PILL_HEIGHT.toFloat())
    path.close()
    drawPath(path, Brush.verticalGradient(listOf(color.copy(alpha = opacity.toFloat()), color.copy(alpha = 0f)), startY = 0f, endY = PILL_HEIGHT.toFloat()))
}

private fun DrawScope.drawLamp(x: Double, color: Color, opacity: Double) {
    drawRoundRect(
        color = color,
        topLeft = Offset((x - LAMP / 2).toFloat(), (-LAMP_HEIGHT / 2).toFloat()),
        size = Size(LAMP.toFloat(), LAMP_HEIGHT.toFloat()),
        cornerRadius = CornerRadius((LAMP_HEIGHT / 2).toFloat()),
        alpha = opacity.toFloat(),
    )
}

private val TitleStyle = TextStyle(
    color = Ink,
    fontSize = 34.sp,
    lineHeight = 41.sp,
    fontWeight = FontWeight.Bold,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** Seconds on the clock a move is timed against. */
private fun clock() = System.nanoTime() / 1e9

/**
 * A tab bar lit by a lamp: a white bar on the rim of a grey pill throws a cone of light into it,
 * and the glyph under the lamp turns white. Choosing a tab slides the lamp there, its beam leaning
 * back as it goes and flaring as it arrives; the glyphs it passes catch the light for a moment.
 * The lamp can also be dragged along the pill. The page above changes with the tab.
 *
 * @param initialTab The tab that is lit at first, from 0.
 * @param field The screen.
 * @param shade A glyph in the shade.
 * @param light A glyph in the light, the lamp and its beam.
 * @param autoPlay Goes round the tabs by itself every [LOOP] seconds.
 * @param onSelect Called with the tab a tap or a drag chose.
 */
@Composable
fun SpotlightTabBar(
    tabs: List<SpotlightTab> = SampleSpotlightTabs,
    initialTab: Int = 0,
    field: Color = Field,
    pill: Color = Pill,
    shade: Color = Shade,
    light: Color = Color.White,
    autoPlay: Boolean = false,
    onSelect: ((index: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    // The loop and the gestures outlive a recomposition: they read these as state.
    val count by rememberUpdatedState(tabs.size)
    val first by rememberUpdatedState(initialTab.coerceIn(0, max(tabs.size - 1, 0)))
    val picked by rememberUpdatedState(onSelect)

    // The open tab; null until one is chosen.
    var chosen by remember { mutableStateOf<Int?>(null) }
    // Where the lamp's move started, or the finger that holds it, and when.
    var lamp by remember { mutableStateOf(resting(first)) }
    var lampStart by remember { mutableDoubleStateOf(-1e9) }
    // The finger's place along the pill, in tabs, while it holds the lamp.
    var finger by remember { mutableDoubleStateOf(0.0) }
    // Where the change of page started, and when.
    var turn by remember { mutableStateOf(Turn(first, first, 0.0)) }
    var turnStart by remember { mutableDoubleStateOf(-1e9) }
    // From one glyph's centre to the next, in dp.
    var pitch by remember { mutableDoubleStateOf(1.0) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    // The frame clock runs while something moves. Read while drawing only.
    val now = remember { mutableDoubleStateOf(clock()) }

    fun index() = chosen ?: first

    fun last() = max(count - 1, 0)

    fun lightsAt(moment: Double) = lights(lamp, moment - lampStart, finger, last().toDouble(), reduced)

    /** Sends the lamp to a tab from wherever it is, and turns the page if the tab is another one. */
    fun go(target: Int) {
        val moment = clock()
        val was = lightsAt(moment)
        // With reduced motion the light that is stronger is the one that fades out.
        val x = if (was.nextPower > 0.5) was.nextX else was.x
        lamp = Lamp(false, 0.0, target.toDouble(), x, if (was.nextPower > 0.5) x else was.foot, 1 - was.power)
        lampStart = moment
        val page = turn
        if (page.target == target) return
        val t = moment - turnStart
        val leaving = if (t < PAGE_OUT) page.from else page.target
        turn = Turn(target, leaving, presence(leaving, page, t))
        turnStart = moment
    }

    /** Opens a tab: the same path for a tap and for the loop, which is silent. */
    fun select(target: Int, byHand: Boolean) {
        if (target == index()) return
        if (byHand) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        chosen = target
        go(target)
        if (byHand) picked?.invoke(target)
    }

    fun grab(place: Double) {
        touches++
        val moment = clock()
        val was = lightsAt(moment)
        finger = place
        lamp = Lamp(true, was.x - place, was.x, was.x, was.x, 1 - was.power)
        lampStart = moment
    }

    fun release() {
        val target = lightsAt(clock()).x.roundToInt()
        val changed = target != index()
        if (changed) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            chosen = target
        }
        go(target)
        if (changed) picked?.invoke(target)
    }

    fun tap(x: Double) {
        touches++
        select(((x - EDGE) / pitch).roundToInt().coerceIn(0, last()), byHand = true)
    }

    DisposableEffect(view) {
        // The screen is light grey: the status bar's icons have to be dark while it shows.
        val controller = view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }

    LaunchedEffect(lampStart, turnStart) {
        val until = max(lampStart, turnStart) + REST
        while (clock() < until) now.doubleValue = withFrameNanos { it } / 1e9
        now.doubleValue = clock()
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            // The loop starts over from the tab it was given.
            select(first, byHand = false)
            delay((SETTLE * 1000).toLong())
        }
        var origin = System.nanoTime()
        suspend fun until(seconds: Double) = delay(max(0L, (origin + (seconds * 1e9).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            for (k in 0 until count) {
                until(HOP_AT + k * HOP_EVERY)
                select((index() + 1) % count, byHand = false)
            }
            until(LOOP)
            origin += (LOOP * 1e9).toLong()
        }
    }

    // Where the lights are at the clock's moment, and how far the page has turned. Read in draw
    // and layer blocks only, so a moving value redraws without recomposing the screen.
    val state by remember(reduced) { derivedStateOf { lightsAt(now.doubleValue) } }
    val turned by remember { derivedStateOf { now.doubleValue - turnStart } }
    val shadow = remember { SoftRoundRect(Color.Black.copy(alpha = 0.28f), SHADOW_SIGMA) }
    val outline = remember { Path() }
    val beam = remember { Path() }
    val density = LocalDensity.current.density

    BoxWithConstraints(modifier.fillMaxSize().background(field).clipToBounds()) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding().value.toDouble()
        val bottom = insets.calculateBottomPadding().value.toDouble()
        val pillWidth = maxWidth.value - SIDE * 2
        val pillTop = maxHeight.value - max(bottom, 16.0) - PILL_LIFT - PILL_HEIGHT
        val step = if (tabs.size > 1) (pillWidth - EDGE * 2) / (tabs.size - 1) else 0.0
        LaunchedEffect(step) { pitch = if (step > 0) step else 1.0 }

        tabs.forEachIndexed { k, tab ->
            Page(
                tab = tab,
                mark = { if (turned < PAGE_OUT) 1f else clamp01((turned - PAGE_OUT - MARK_AFTER) / MARK_IN).toFloat() },
                modifier = Modifier
                    .offset(x = MARGIN.dp, y = (top + TITLE_TOP).dp)
                    .width((maxWidth.value - MARGIN * 2).dp)
                    .graphicsLayer { alpha = presence(k, turn, turned).toFloat() }
                    .then(if (k == index()) Modifier else Modifier.clearAndSetSemantics {}),
            )
        }

        Canvas(
            Modifier
                .offset(x = (SIDE - BLEED).dp, y = (pillTop - BLEED).dp)
                .size((pillWidth + BLEED * 2).dp, (PILL_HEIGHT + BLEED * 2).dp),
        ) {
            val moment = state
            val w = pillWidth.toFloat()
            val h = PILL_HEIGHT.toFloat()
            // Everything here is drawn in dp, from the pill's top left corner.
            scale(this.density, this.density, pivot = Offset.Zero) {
                translate(BLEED.toFloat(), BLEED.toFloat()) {
                    drawIntoCanvas { shadow.draw(it.nativeCanvas, 0f, SHADOW_DROP.toFloat(), w, h, h / 2) }
                    drawRoundRect(pill, size = Size(w, h), cornerRadius = CornerRadius(h / 2))

                    // The beams stay inside the pill; the lamps lie across its edge.
                    outline.rewind()
                    outline.addRoundRect(RoundRect(Rect(0f, 0f, w, h), CornerRadius(h / 2)))
                    clipPath(outline) {
                        drawBeam(beam, EDGE + moment.x * step, EDGE + moment.foot * step, light, (BEAM + moment.flare) * moment.power)
                        if (moment.nextPower > 0) {
                            val x = EDGE + moment.nextX * step
                            drawBeam(beam, x, x, light, BEAM * moment.nextPower)
                        }
                    }

                    tabs.forEachIndexed { k, tab ->
                        translate((EDGE + k * step - GLYPH / 2).toFloat(), ((PILL_HEIGHT - GLYPH) / 2).toFloat()) {
                            drawPath(tab.glyph, shade)
                            // The lit colour fades in over the shaded one.
                            drawPath(tab.glyph, light, alpha = lit(moment, k).toFloat())
                        }
                    }

                    drawLamp(EDGE + moment.x * step, light, moment.power)
                    if (moment.nextPower > 0) drawLamp(EDGE + moment.nextX * step, light, moment.nextPower)
                }
            }
        }

        // Dragging along the pill takes the lamp with the finger; it goes to the nearest tab on release.
        Box(
            Modifier
                .offset(x = SIDE.dp, y = pillTop.dp)
                .size(pillWidth.dp, PILL_HEIGHT.dp)
                .pointerInput(Unit) { detectTapGestures { tap(it.x.toDouble() / density) } }
                .draggable(
                    state = rememberDraggableState { delta -> finger += delta / density / pitch },
                    orientation = Orientation.Horizontal,
                    enabled = tabs.size > 1,
                    onDragStarted = { start -> grab((start.x / density - EDGE) / pitch) },
                    onDragStopped = { release() },
                )
                .semantics {
                    contentDescription = tabs.getOrNull(index())?.title ?: ""
                    stateDescription = "Tab ${index() + 1} of ${tabs.size}"
                    customActions = tabs.mapIndexed { k, tab ->
                        CustomAccessibilityAction(tab.title) {
                            select(k, byHand = true)
                            true
                        }
                    }
                },
        )
    }
}

/** A tab's page: its title over placeholder cards, the first one with the tab's glyph on it. */
@Composable
private fun Page(tab: SpotlightTab, mark: () -> Float, modifier: Modifier = Modifier) {
    Column(modifier) {
        BasicText(tab.title, style = TitleStyle, maxLines = 1, modifier = Modifier.padding(bottom = (18 - CARD_GAP).dp).semantics { heading() })
        Cards.forEachIndexed { k, height ->
            Box(
                Modifier.padding(top = CARD_GAP.dp).fillMaxWidth().height(height.dp).background(CardFill, RoundedCornerShape(22.dp)).padding(22.dp),
                contentAlignment = Alignment.BottomStart,
            ) {
                if (k == 0) {
                    // The mark leaves with its page and comes back after the next one.
                    Canvas(Modifier.align(Alignment.Center).size(CARD_MARK.dp)) {
                        val size = (CARD_MARK / GLYPH).toFloat() * density
                        scale(size, size, pivot = Offset.Zero) { drawPath(tab.glyph, Mark, alpha = mark()) }
                    }
                }
                Column {
                    Box(Modifier.size(150.dp, 10.dp).background(LineFill, RoundedCornerShape(5.dp)))
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.size(96.dp, 10.dp).background(LineFill, RoundedCornerShape(5.dp)))
                }
            }
        }
    }
}
