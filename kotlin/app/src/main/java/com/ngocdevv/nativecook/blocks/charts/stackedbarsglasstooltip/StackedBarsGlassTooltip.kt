package com.ngocdevv.nativecook.blocks.charts.stackedbarsglasstooltip

import android.app.Activity
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.blocks.charts.stackedbarsglasstooltip.StackedBarsGlassTooltipMacros.drawBars
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private typealias Macros = StackedBarsGlassTooltipMacros

private val SKY = listOf(Color(0xFFFDE8F2), Color(0xFFE9E4FF), Color(0xFFDDEBFF))
private val INK = Color(0xFF1B1B2F)
private val MUTE = Color(0xFF8A8AA0)
private val SOFT = Color(0xFF6B6B85)
private val LINE = Color(0xFFEFEFF5)
private val BASELINE = Color(0xFFE5E5F0)
private val CARD = Color.White
private val UP = Color(0xFFFF5C5C)
private val UP_FILL = Color(0xFFFFECEC)
private fun shade(alpha: Float) = Color(60 / 255f, 40 / 255f, 120 / 255f, alpha)
// Bottom to top, each a gradient from its top to its bottom.
private val PROTEIN = listOf(Color(0xFF2D8BFF), Color(0xFF00B7FF))
private val FAT = listOf(Color(0xFFC77DFF), Color(0xFFD99AFF))
private val CARBS = listOf(Color(0xFFFFD95A), Color(0xFFFFC130))
private val SWATCH_CARBS = Color(0xFFFFB800)
private val SWATCH_FAT = Color(0xFFC77DFF)
private val SWATCH_PROTEIN = Color(0xFF2D8BFF)
// No backdrop blur here: the tooltip takes React Native's fallback for platforms without one.
private val GLASS = Color.White.copy(alpha = 0.85f)

private val MARGIN = 16.dp
private val CARD_PADDING = 20.dp
private val CARD_RADIUS = 28.dp
private val CARD_MAX_HEIGHT = 620.dp
private const val AXIS = 30f
private const val LABELS = 22f
private const val GRID_LINES = 5
private const val COLUMN = 28f
// A column takes this share of its slot, up to COLUMN wide.
private const val COLUMN_SHARE = 0.62f
private const val SEGMENT_GAP = 2f
private const val COLUMN_RADIUS = 10f
private const val LIFT = 6f
private const val DIM = 0.45f
private const val TIP_WIDTH = 140f
private const val TIP_HEIGHT = 96f
private val TIP_RADIUS = 16.dp
// The tooltip floats this far above the hovered column, and may rise this far over the legend.
private const val TIP_GAP = 10f
private const val TIP_RISE = 44f
private const val FINGER = 40f
private val SEGMENTED = 32.dp
private val SEGMENT_WIDTH = 64.dp

private const val MORPH = 0.8
private const val STILL_MORPH = 0.3
private const val FOCUS_IN = 0.15
private const val FOCUS_OUT = 0.2
// The tooltip glides to a new column (300 ms, damping ratio 0.82); the finger ring trails the touch
// (380 ms, critically damped). Perceptual durations, as Reanimated reads them.
private val GLIDE = SpringConfig(0.3, 0.82)
private val TRAIL = SpringConfig(0.38, 1.0)
// Both springs are at rest well within this.
private const val SETTLE = 0.8
private const val PILL_MS = 260
private val EASE_OUT_CUBIC = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
private val EASE_IN_OUT_QUAD = CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)
// Fades of the labels keyed by the range.
private const val PERIOD_IN_MS = 200
private const val AXIS_IN = 0.22
private const val DATES_IN = 0.22
private const val DATES_DELAY = MORPH / 2
private const val DATES_OUT = 0.15

/**
 * autoPlay, in seconds from the start of a loop: the finger goes down on a column, drags right,
 * rests, drags back and lifts; then the control switches to the next range and back.
 */
const val STACKED_BARS_GLASS_TOOLTIP_LOOP = 9.0
// time, column (fractions glide between columns)
private val SCRUB = listOf(0.8 to 1.0, 1.2 to 1.0, 2.9 to 6.0, 3.3 to 6.0, 4.5 to 2.0, 5.0 to 2.0)
private const val LIFT_AT = 5.0
private const val SWITCH_AT = 5.6
private const val SWITCH_BACK_AT = 7.6
/** After the last touch autoPlay waits this long, then comes back to rest. */
private const val USER_HOLD = 3.0
// The scripted finger's height in the chart.
private const val FINGER_HEIGHT = 0.55f

private fun now() = System.nanoTime() / 1e9

/** Reanimated's default timing curve. */
private fun quad(t: Double): Double {
    val x = t.coerceIn(0.0, 1.0)
    return if (x < 0.5) 2 * x * x else 1 - (-2 * x + 2).pow(2) / 2
}

private fun cubic(t: Double): Double {
    val x = t.coerceIn(0.0, 1.0)
    return if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
}

/** The scripted finger's column at a time of the loop, eased between the rows of SCRUB. */
private fun scriptedColumn(time: Double): Double {
    if (time <= SCRUB[0].first) return SCRUB[0].second
    for (k in 1 until SCRUB.size) {
        if (time < SCRUB[k].first) {
            val (at0, c0) = SCRUB[k - 1]
            val (at1, c1) = SCRUB[k]
            val u = (time - at0) / (at1 - at0)
            return c0 + (c1 - c0) * u * u * (3 - 2 * u)
        }
    }
    return SCRUB.last().second
}

/** The times at which the scripted finger moves onto another column, and the column (-1 lifted). */
private val COLUMN_STEPS: List<Pair<Double, Int>> = buildList {
    add(SCRUB[0].first to SCRUB[0].second.toInt())
    for (k in 1 until SCRUB.size) {
        val (at0, c0) = SCRUB[k - 1]
        val (at1, c1) = SCRUB[k]
        val from = c0.toInt()
        val to = c1.toInt()
        if (from == to) continue
        val dir = if (to > from) 1 else -1
        var next = from + dir
        while (true) {
            // The column changes where the glide crosses a half: the smoothstep's inverse, in closed form.
            val edge = min(next, next - dir) + 0.5
            val y = (edge - c0) / (c1 - c0)
            val u = 0.5 - sin(asin(1 - 2 * y) / 3)
            add(at0 + (at1 - at0) * u to next)
            if (next == to) break
            next += dir
        }
    }
    add(LIFT_AT to -1)
}

/** 2500 → "2.5k", 450 → "450". */
private fun axisLabel(value: Double): String {
    if (value < 1000) return Macros.round(value).toLong().toString()
    return String.format(Locale.US, "%.1f", value / 1000).removeSuffix(".0") + "k"
}

private fun grouped(value: Double) = String.format(Locale.US, "%,d", Macros.round(value).toLong())

private class SpringConfig(duration: Double, dampingRatio: Double) {
    val stiffness = (2 * PI / duration).pow(2)
    val damping = 4 * PI * dampingRatio / duration
}

/** A damped spring of unit mass, solved exactly: its offset from the target and velocity after [dt]. */
private fun springStep(x: Double, v: Double, dt: Double, config: SpringConfig): Pair<Double, Double> {
    val w0 = sqrt(config.stiffness)
    val zeta = config.damping / (2 * w0)
    val decay = exp(-zeta * w0 * dt)
    if (abs(zeta - 1) < 1e-6) {
        val b = v + w0 * x
        return (x + b * dt) * decay to (v - w0 * b * dt) * decay
    }
    val wd = w0 * sqrt(1 - zeta * zeta)
    val c = cos(wd * dt)
    val s = sin(wd * dt)
    return decay * (x * c + (v + zeta * w0 * x) / wd * s) to decay * (v * c - (zeta * w0 * v + w0 * w0 * x) / wd * s)
}

private class Spring(
    val value: Double,
    val velocity: Double = 0.0,
    val target: Double = value,
    val start: Double = 0.0,
    val config: SpringConfig? = null,
) {
    fun sample(at: Double): Pair<Double, Double> {
        val c = config ?: return target to 0.0
        val (x, v) = springStep(value - target, velocity, max(0.0, at - start), c)
        return target + x to v
    }

    /** Heads for [to] from where the spring is at [at], keeping its speed; a null config jumps there. */
    fun retarget(to: Double, at: Double, config: SpringConfig?): Spring {
        if (config == null) return Spring(to)
        val (x, v) = sample(at)
        return Spring(x, v, to, at, config)
    }
}

/** A number from one value to another over a time span, on Reanimated's default curve. */
private class Fade(val from: Double = 0.0, val to: Double = from, val start: Double = 0.0, val duration: Double = 0.0) {
    fun value(at: Double) = if (duration <= 0) to else from + (to - from) * quad((at - start) / duration)
}

/** What the finger and the focus look like at a moment, in points of the chart. */
private class Motion(val focus: Float, val focusX: Float, val trailX: Float, val trailY: Float)

/** The hand's finger and the springs it drives. */
private data class Hand(
    val on: Boolean = false,
    val x: Float = 0f,
    val y: Float = 0f,
    val column: Int = -1,
    val focus: Fade = Fade(),
    val focusX: Spring = Spring(0.0),
    val trailX: Spring = Spring(0.0),
    val trailY: Spring = Spring(0.0),
) {
    fun motion(at: Double) = Motion(
        focus.value(at).toFloat(), focusX.sample(at).first.toFloat(), trailX.sample(at).first.toFloat(), trailY.sample(at).first.toFloat(),
    )
}

/** The screen's moving parts, kept in one remembered object so handlers read current values. */
private class MacrosState(initialRange: Int, autoPlay: Boolean) {
    var ranges: List<StackedBarsGlassTooltipRange> = Macros.RANGES
    var restRange = initialRange
    var still = false
    var tick: () -> Unit = {}
    var onRangeChange: ((StackedBarsGlassTooltipRange) -> Unit)? = null
    var onSelect: ((Int?) -> Unit)? = null

    var range by mutableIntStateOf(initialRange)
    var fromRange by mutableIntStateOf(initialRange)
    var hovered by mutableStateOf<Int?>(null)
    var fromBars by mutableStateOf(Macros.bars(Macros.RANGES[initialRange]))
    var toBars by mutableStateOf(fromBars)
    var switchAt by mutableDoubleStateOf(-1e9)
    var automatic by mutableStateOf(autoPlay)
    var origin by mutableDoubleStateOf(now())
    var hand by mutableStateOf(Hand())
    var touches by mutableIntStateOf(0)
    var holding = false
    var lastTouch = 0.0
    // The frame clock runs only while something moves; otherwise the screen is drawn at `sampleAt`.
    var autoMoving by mutableStateOf(false)
    var handMoving by mutableStateOf(false)
    var busyUntil by mutableDoubleStateOf(0.0)
    var sampleAt by mutableDoubleStateOf(now())
    var frame by mutableDoubleStateOf(now())
    var chartW = 1f
    var chartH = 1f

    private var cachedAt = Double.NaN
    private var cached = Motion(0f, 0f, 0f, 0f)

    val moving get() = if (automatic) autoMoving else handMoving
    val date get() = if (moving) frame else sampleAt
    val morphFor get() = if (still) STILL_MORPH else MORPH
    private val otherRange get() = (restRange + 1) % ranges.size

    private fun loopTime(at: Double) = (at - origin).coerceAtLeast(0.0) % STACKED_BARS_GLASS_TOOLTIP_LOOP

    fun motion(at: Double): Motion {
        if (at == cachedAt) return cached
        cached = if (automatic) autoMotion(loopTime(at)) else hand.motion(at)
        cachedAt = at
        return cached
    }

    private fun pitch() = Macros.columnLayout(ranges[restRange].columns.size, chartW, COLUMN, COLUMN_SHARE).first

    private fun columnAt(x: Float, n: Int) = floor(x / (chartW / n)).toInt().coerceIn(0, n - 1)

    /**
     * The scripted finger at a time of the loop, through the same springs a touch drives. The
     * springs are stepped frame by frame from the landing, as they chase the finger.
     */
    private fun autoMotion(t: Double): Motion {
        val n = ranges[restRange].columns.size
        val pitch = pitch()
        fun fingerX(time: Double) = (scriptedColumn(time) + 0.5) * pitch
        fun center(time: Double) = (columnAt(fingerX(time).toFloat(), n) + 0.5) * pitch
        val landing = SCRUB[0].first
        val focus = if (t < LIFT_AT) quad((t - landing) / FOCUS_IN) else 1 - quad((t - LIFT_AT) / FOCUS_OUT)
        val y = chartH * FINGER_HEIGHT
        if (t < landing) return Motion(focus.toFloat(), center(landing).toFloat(), fingerX(t).toFloat(), y)
        if (still) return Motion(focus.toFloat(), center(min(t, LIFT_AT - 0.001)).toFloat(), fingerX(t).toFloat(), y)
        // A fresh touch starts at its column and under the finger.
        var fx = center(landing)
        var fv = 0.0
        var tx = fingerX(landing)
        var tv = 0.0
        val end = min(t, LIFT_AT + SETTLE)
        var frame = 0
        while (true) {
            val at = landing + frame / 60.0
            if (at >= end) break
            val dt = min(1 / 60.0, end - at)
            val goal = center(min(at, LIFT_AT - 0.001))
            val (fo, fvel) = springStep(fx - goal, fv, dt, GLIDE)
            fx = goal + fo
            fv = fvel
            val target = fingerX(at)
            val (to, tvel) = springStep(tx - target, tv, dt, TRAIL)
            tx = target + to
            tv = tvel
            frame++
        }
        // Once lifted, the ring stays under the finger as it fades.
        return Motion(focus.toFloat(), fx.toFloat(), (if (t < LIFT_AT) tx else fingerX(t)).toFloat(), y)
    }

    /** The hand takes the finger, the focus and the springs from where the loop has them. */
    fun takeover(at: Double) {
        if (!automatic) return
        val t = loopTime(at)
        val m = autoMotion(t)
        val n = ranges[restRange].columns.size
        val pitch = pitch()
        val on = t >= SCRUB[0].first && t < LIFT_AT
        val x = ((scriptedColumn(t) + 0.5) * pitch).toFloat()
        val column = if (on) columnAt(x, n) else -1
        var next = Hand(
            on = on, x = x, y = chartH * FINGER_HEIGHT, column = column,
            focus = Fade(m.focus.toDouble()), focusX = Spring(m.focusX.toDouble()),
            trailX = Spring(m.trailX.toDouble()), trailY = Spring(m.trailY.toDouble()),
        )
        // A spring caught mid-glide keeps going where it was heading.
        if (on && !still) {
            val later = autoMotion(min(t + 1 / 60.0, LIFT_AT - 0.001))
            next = next.copy(
                focusX = Spring(m.focusX.toDouble(), (later.focusX - m.focusX) * 60.0, (column + 0.5) * pitch, at, GLIDE),
                focus = Fade(m.focus.toDouble(), 1.0, at, FOCUS_IN * (1 - m.focus)),
            )
        }
        hand = next
        automatic = false
        autoMoving = false
        keepMoving(at + SETTLE)
    }

    /** Where the finger is, and whether it is down: picks the column, moves the focus and the ring. */
    fun setFinger(on: Boolean, x: Float, y: Float, byHand: Boolean) {
        val at = now()
        val was = hand
        val n = toBars.kcal.size
        val column = if (on) columnAt(x, n) else -1
        var next = was
        if (column != was.column) {
            if (column >= 0) {
                val center = (column + 0.5) * (chartW / n)
                // A fresh touch starts at its column; moving on, the focus glides.
                next = next.copy(focusX = was.focusX.retarget(center, at, if (was.column < 0 || still) null else GLIDE))
            }
            next = next.copy(column = column)
        }
        if (on != was.on) {
            next = next.copy(focus = Fade(was.focus.value(at), if (on) 1.0 else 0.0, at, if (on) FOCUS_IN else FOCUS_OUT))
        }
        val follow = if (!was.on || still || !on) null else TRAIL
        hand = next.copy(
            on = on, x = x, y = y,
            trailX = was.trailX.retarget(x.toDouble(), at, follow),
            trailY = was.trailY.retarget(y.toDouble(), at, follow),
        )
        if (column != was.column) hover(column, byHand)
        keepMoving(at + SETTLE)
    }

    fun hover(index: Int, byHand: Boolean) {
        hovered = if (index < 0) null else index
        if (byHand && index >= 0) tick()
        onSelect?.invoke(if (index < 0) null else index)
    }

    fun select(index: Int, byHand: Boolean) {
        if (index == range) return
        val at = now()
        // The switch starts from the range drawn now.
        fromBars = toBars
        toBars = Macros.bars(ranges[index])
        fromRange = range
        range = index
        switchAt = at
        if (byHand) tick()
        if (!automatic) keepMoving(at + max(morphFor, DATES_DELAY + DATES_IN))
        onRangeChange?.invoke(ranges[index])
    }

    fun pressRange(index: Int) {
        takeover(now())
        setFinger(false, hand.x, hand.y, true)
        touched()
        select(index, true)
    }

    fun keepMoving(until: Double) {
        if (until > busyUntil) busyUntil = until
        handMoving = true
    }

    /** A touch takes over from autoPlay; the loop comes back USER_HOLD seconds after the last one. */
    fun touched() {
        lastTouch = now()
        touches++
    }

    suspend fun waitUntil(at: Double) {
        while (now() < at) delay(((at - now()) * 1000).roundToInt().coerceAtLeast(1).toLong())
    }

    suspend fun settle() {
        val until = busyUntil
        waitUntil(until)
        sampleAt = until
        handMoving = false
    }

    private fun pause() {
        sampleAt = now()
        autoMoving = false
    }

    suspend fun autoplay(autoPlay: Boolean) {
        if (!autoPlay) return
        if (!automatic) {
            if (holding) return
            // Back to rest: the resting range first, then the loop from its start.
            var resume = lastTouch + USER_HOLD
            waitUntil(resume)
            if (range != restRange) {
                select(restRange, false)
                resume += MORPH
                waitUntil(resume)
            }
            hand = Hand()
            origin = resume
            sampleAt = resume
            automatic = true
        }
        val shift = max(morphFor, DATES_DELAY + DATES_IN)
        val events = (COLUMN_STEPS.map { (at, column) -> at to { hover(column, false) } } + listOf(
            SCRUB[0].first to { autoMoving = true },
            LIFT_AT + FOCUS_OUT to { pause() },
            SWITCH_AT to { select(otherRange, false); autoMoving = true },
            SWITCH_AT + shift to { pause() },
            SWITCH_BACK_AT to { select(restRange, false); autoMoving = true },
            SWITCH_BACK_AT + shift to { pause() },
        )).sortedBy { it.first }
        // Joining a loop already under way, the events before now are skipped.
        val joined = now()
        var start = origin + floor(max(0.0, joined - origin) / STACKED_BARS_GLASS_TOOLTIP_LOOP) * STACKED_BARS_GLASS_TOOLTIP_LOOP
        while (true) {
            for ((at, run) in events) {
                if (start + at < joined) continue
                waitUntil(start + at)
                run()
            }
            start += STACKED_BARS_GLASS_TOOLTIP_LOOP
            waitUntil(start)
        }
    }
}

private fun style(size: Int, lineHeight: Int, color: Color, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f, tabular: Boolean = false) =
    TextStyle(
        color = color,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        fontWeight = weight,
        letterSpacing = tracking.sp,
        fontFeatureSettings = if (tabular) "tnum" else null,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

/** A number that rolls up when it changes: the old one leaves upward as the new one comes in. */
@Composable
private fun Rolling(value: String, style: TextStyle, still: Boolean, modifier: Modifier = Modifier, alignment: Alignment = Alignment.CenterStart) {
    val rise = with(LocalDensity.current) { 25.dp.roundToPx() }
    AnimatedContent(
        value,
        modifier,
        transitionSpec = {
            if (still) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (fadeIn(tween(220, easing = EASE_IN_OUT_QUAD)) + slideInVertically(tween(220, easing = EASE_IN_OUT_QUAD)) { rise }) togetherWith
                    (fadeOut(tween(180, easing = EASE_IN_OUT_QUAD)) + slideOutVertically(tween(180, easing = EASE_IN_OUT_QUAD)) { -rise })
            } using SizeTransform(clip = true) { _, _ -> snap() }
        },
        contentAlignment = alignment,
        label = "rolling",
    ) { text -> BasicText(text, style = style, maxLines = 1) }
}

/**
 * A macros screen: a week of stacked columns of protein, fat and carbs on a white card. Press and
 * drag across the chart: the column under the finger lifts while the others dim, and a glass
 * tooltip glides above it with that day's grams. Day, Week and Month morph the columns into each other.
 *
 * @param initialRange The range shown at rest; autoPlay switches to the next one and back.
 * @param autoPlay Plays a scrub across the week and a switch to the month by itself, every
 *   [STACKED_BARS_GLASS_TOOLTIP_LOOP] seconds.
 * @param onSelect Called with the column under the finger, or null when it lifts.
 */
@Composable
fun StackedBarsGlassTooltip(
    modifier: Modifier = Modifier,
    title: String = "Macros",
    heading: String = "How your intake changed over time",
    hint: String = "Press and drag across the chart",
    ranges: List<StackedBarsGlassTooltipRange> = Macros.RANGES,
    initialRange: Int = 1,
    carbsLabel: String = "Carbs",
    fatLabel: String = "Fat",
    proteinLabel: String = "Protein",
    autoPlay: Boolean = false,
    onRangeChange: ((StackedBarsGlassTooltipRange) -> Unit)? = null,
    onSelect: ((Int?) -> Unit)? = null,
) {
    val still = rememberReducedMotion()
    val view = LocalView.current
    val state = remember {
        MacrosState(initialRange, autoPlay).also {
            it.fromBars = Macros.bars(ranges[initialRange])
            it.toBars = it.fromBars
        }
    }
    state.ranges = ranges
    state.restRange = initialRange
    state.still = still
    state.onRangeChange = onRangeChange
    state.onSelect = onSelect
    state.tick = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }

    // Dark status bar icons on the pale screen.
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (before != null) controller.isAppearanceLightStatusBars = before }
    }
    LaunchedEffect(state.busyUntil) { state.settle() }
    LaunchedEffect(state.touches, autoPlay) { state.autoplay(autoPlay) }
    LaunchedEffect(state.moving) {
        while (state.moving) withFrameNanos { state.frame = it / 1e9 }
    }

    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val range = ranges[state.range]
    val legend = listOf(
        Triple(carbsLabel, SWATCH_CARBS) { day: StackedBarsGlassTooltipDay -> day.carbs },
        Triple(fatLabel, SWATCH_FAT) { day: StackedBarsGlassTooltipDay -> day.fat },
        Triple(proteinLabel, SWATCH_PROTEIN) { day: StackedBarsGlassTooltipDay -> day.protein },
    )
    Column(
        modifier
            .fillMaxSize()
            .background(Brush.linearGradient(SKY, start = Offset.Zero, end = Offset.Infinite))
            .padding(horizontal = MARGIN)
            .padding(top = insets.calculateTopPadding() + 8.dp, bottom = max(insets.calculateBottomPadding(), 16.dp)),
    ) {
        TitleRow(state, title, still)
        Column(
            Modifier
                .weight(1f)
                .heightIn(max = CARD_MAX_HEIGHT)
                .fillMaxWidth()
                .dropShadow(RoundedCornerShape(CARD_RADIUS), Shadow(radius = 40.dp, color = shade(0.12f), offset = DpOffset(0.dp, 16.dp)))
                .clip(RoundedCornerShape(CARD_RADIUS))
                .background(CARD)
                .border(1.dp, Color.White, RoundedCornerShape(CARD_RADIUS))
                .padding(CARD_PADDING),
        ) {
            val columns = range.columns
            val averageKcal = columns.sumOf { Macros.calories(it) } / columns.size
            BasicText(heading, style = style(16, 21, INK, FontWeight.SemiBold))
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.Bottom) {
                    BasicText(grouped(averageKcal), style = style(30, 36, INK, FontWeight.SemiBold, -0.6f, tabular = true))
                    BasicText(" cal", Modifier.padding(bottom = 6.dp), style = style(14, 18, MUTE, FontWeight.Medium))
                }
                Box(
                    Modifier.padding(start = 10.dp).height(22.dp).background(UP_FILL, CircleShape).padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("↑ ${String.format(Locale.US, "%.1f", range.change)}%", style = style(12, 16, UP, FontWeight.SemiBold))
                }
            }
            Row(Modifier.padding(top = 14.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                for ((label, color, grams) in legend) {
                    Column(Modifier.widthIn(min = 64.dp)) {
                        val average = columns.sumOf { grams(it) }.toDouble() / columns.size
                        Rolling("${Macros.round(average).toInt()} g", style(20, 25, color, FontWeight.SemiBold, tabular = true), still)
                        BasicText("Avg. ${label.lowercase()}", style = style(11, 14, MUTE))
                    }
                }
            }
            Chart(state, heading, legend, still, Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp))
        }
        BasicText(hint, Modifier.fillMaxWidth().padding(top = 16.dp), style = style(13, 18, SOFT).copy(textAlign = TextAlign.Center))
    }
}

@Composable
private fun TitleRow(state: MacrosState, title: String, still: Boolean) {
    val ranges = state.ranges
    val pill by animateFloatAsState(
        state.range.toFloat(),
        if (still) snap() else tween(PILL_MS, easing = EASE_OUT_CUBIC),
        label = "pill",
    )
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            BasicText(title, style = style(30, 36, INK, FontWeight.Bold, -0.6f))
            // The period of a new range fades in; the old one is gone at once.
            key(state.range) {
                val shown = remember { Animatable(if (state.fromRange == state.range || still) 1f else 0f) }
                LaunchedEffect(Unit) { shown.animateTo(1f, tween(PERIOD_IN_MS, easing = EASE_IN_OUT_QUAD)) }
                BasicText(
                    ranges[state.range].period,
                    Modifier.padding(top = 2.dp).graphicsLayer { alpha = shown.value },
                    style = style(13, 18, SOFT),
                    maxLines = 1,
                )
            }
        }
        Box(Modifier.height(SEGMENTED).background(Color.White.copy(alpha = 0.7f), CircleShape)) {
            Box(
                Modifier
                    .offset { androidx.compose.ui.unit.IntOffset((3.dp.toPx() + pill * SEGMENT_WIDTH.toPx()).roundToInt(), 3.dp.roundToPx()) }
                    .size(SEGMENT_WIDTH - 6.dp, SEGMENTED - 6.dp)
                    .dropShadow(CircleShape, Shadow(radius = 8.dp, color = shade(0.14f), offset = DpOffset(0.dp, 2.dp)))
                    .background(CARD, CircleShape),
            )
            Row {
                ranges.forEachIndexed { index, item ->
                    val on = index == state.range
                    Box(
                        Modifier
                            .size(SEGMENT_WIDTH, SEGMENTED)
                            .semantics {
                                role = Role.Tab
                                selected = on
                            }
                            .clickable(remember { MutableInteractionSource() }, indication = null) { state.pressRange(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(item.name, style = style(13, 17, if (on) INK else SOFT, if (on) FontWeight.SemiBold else FontWeight.Medium))
                    }
                }
            }
        }
    }
}

@Composable
private fun Chart(
    state: MacrosState,
    heading: String,
    legend: List<Triple<String, Color, (StackedBarsGlassTooltipDay) -> Int>>,
    still: Boolean,
    modifier: Modifier,
) {
    val density = LocalDensity.current.density
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val axisStyle = style(10, 12, MUTE, tabular = true)
    val dateStyle = style(10, 13, MUTE).copy(textAlign = TextAlign.Center)
    val connectorGlow = remember { SoftRoundRect(shade(0.25f), 1.5f) }
    val paints = remember {
        StackedBarsGlassTooltipMacros.Paints(listOf(PROTEIN, FAT, CARBS), SEGMENT_GAP, COLUMN_RADIUS, LIFT, DIM, CARD, COLUMN, COLUMN_SHARE)
    }
    val range = state.ranges[state.range]
    // Where the tooltip and its hairline stand, in points of the chart.
    fun tipTop(motion: Motion) = state.chartH - Macros.stackTop(state.toBars, motion.focusX, state.chartW, state.chartH) - LIFT
    fun tipY(motion: Motion) = max(-TIP_RISE, tipTop(motion) - TIP_GAP - TIP_HEIGHT)

    Box(
        modifier
            .onSizeChanged {
                state.chartW = max(1f, it.width / density - AXIS)
                state.chartH = max(1f, it.height / density - LABELS)
            }
            .semantics { contentDescription = "$heading, ${range.period}" }
            .pointerInput(state) {
                fun place(position: Offset) = Offset(
                    (position.x / density - AXIS).coerceIn(0f, state.chartW - 1),
                    (position.y / density).coerceIn(0f, state.chartH),
                )
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    state.holding = true
                    state.takeover(now())
                    state.touched()
                    val first = place(down.position)
                    state.setFinger(true, first.x, first.y, true)
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.pressed) {
                            val at = place(change.position)
                            state.setFinger(true, at.x, at.y, true)
                        }
                        change.consume()
                    } while (change.pressed)
                    state.holding = false
                    state.setFinger(false, state.hand.x, state.hand.y, true)
                    state.touched()
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val date = state.date
            val motion = state.motion(date)
            val w = state.chartW
            val h = state.chartH
            val since = date - state.switchAt
            val switched = state.fromRange != state.range
            scale(density, density, pivot = Offset.Zero) {
                for (k in 0..GRID_LINES) {
                    val y = h * (1 - k.toFloat() / GRID_LINES)
                    drawRect(if (k == 0) BASELINE else LINE, Offset(AXIS, y), androidx.compose.ui.geometry.Size(w, 1f))
                }
                val m = cubic(since / state.morphFor).toFloat()
                translate(AXIS, 0f) {
                    // Reduced motion swaps the columns halfway through a short fade instead of moving them.
                    if (still) {
                        val fade = 1f - sin(PI.toFloat() * m)
                        drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(0f, 0f, w, h + LIFT), androidx.compose.ui.graphics.Paint().apply { alpha = fade })
                        drawBars(state.fromBars, state.toBars, if (m < 0.5f) 0f else 1f, motion.focusX, motion.focus, w, h, paints)
                        drawContext.canvas.restore()
                    } else {
                        drawBars(state.fromBars, state.toBars, m, motion.focusX, motion.focus, w, h, paints)
                    }
                }
                // A hairline from the tooltip down to the top of the column, when there is room for one.
                val top = tipTop(motion)
                val from = tipY(motion) + TIP_HEIGHT
                if (top > from && motion.focus > 0f) {
                    val x = AXIS + motion.focusX - 0.5f
                    drawIntoCanvas { connectorGlow.draw(it.nativeCanvas, x, from, 1f, top - from, 0f, motion.focus) }
                    drawRect(Color.White.copy(alpha = 0.9f * motion.focus), Offset(x, from), androidx.compose.ui.geometry.Size(1f, top - from))
                }
            }
            // The axis of the new range fades in; the old one is gone at once.
            val axisAlpha = if (switched && !still) quad(since / AXIS_IN).toFloat() else 1f
            for (k in 0..GRID_LINES) {
                val layout = measurer.measure(axisLabel(range.max * k / GRID_LINES), axisStyle)
                val y = h * (1 - k.toFloat() / GRID_LINES) * density
                drawText(layout, topLeft = Offset(0f, y - layout.size.height / 2f), alpha = axisAlpha)
            }
            // Dates: the old ones fade out, the new ones in once the columns have half moved.
            fun dates(of: StackedBarsGlassTooltipRange, alpha: Float) {
                if (alpha <= 0f) return
                val pitch = Macros.columnLayout(of.columns.size, w, COLUMN, COLUMN_SHARE).first
                for ((i, column) in of.columns.withIndex()) {
                    if (column.label.isEmpty()) continue
                    val layout = measurer.measure(column.label, dateStyle)
                    val x = (AXIS + (i + 0.5f) * pitch) * density - layout.size.width / 2f
                    drawText(layout, topLeft = Offset(x, (h + 6) * density), alpha = alpha)
                }
            }
            if (switched && !still) dates(state.ranges[state.fromRange], (1 - quad(since / DATES_OUT)).toFloat())
            dates(range, if (switched && !still) quad((since - DATES_DELAY) / DATES_IN).toFloat() else 1f)
        }
        // The ring that trails the finger.
        Box(
            Modifier
                .graphicsLayer {
                    val motion = state.motion(state.date)
                    translationX = (AXIS + motion.trailX - FINGER / 2) * density
                    translationY = (motion.trailY - FINGER / 2) * density
                    alpha = motion.focus
                }
                .size(FINGER.dp)
                .dropShadow(CircleShape, Shadow(radius = 10.dp, color = shade(0.18f), offset = DpOffset(0.dp, 2.dp)))
                .background(Color.White.copy(alpha = 0.12f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape),
        )
        Tooltip(
            state, legend, still,
            Modifier.graphicsLayer {
                val motion = state.motion(state.date)
                translationX = (AXIS + min(state.chartW - TIP_WIDTH, max(-AXIS, motion.focusX - TIP_WIDTH / 2))) * density
                translationY = tipY(motion) * density
                val scale = if (still) 1f else 0.94f + 0.06f * motion.focus
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin.Center
                alpha = motion.focus
            },
        )
    }
}

@Composable
private fun Tooltip(
    state: MacrosState,
    legend: List<Triple<String, Color, (StackedBarsGlassTooltipDay) -> Int>>,
    still: Boolean,
    modifier: Modifier,
) {
    val columns = state.ranges[state.range].columns
    val day = state.hovered?.let { columns[min(it, columns.size - 1)] }
    val shape = RoundedCornerShape(TIP_RADIUS)
    Box(
        modifier
            .size(TIP_WIDTH.dp, TIP_HEIGHT.dp)
            .dropShadow(shape, Shadow(radius = 24.dp, color = shade(0.16f), offset = DpOffset(0.dp, 8.dp)))
            .clip(shape)
            .background(GLASS)
            .border(1.dp, Color.White.copy(alpha = 0.8f), shape),
    ) {
        if (day != null) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Rolling(day.title, style(12, 16, INK, FontWeight.SemiBold), still)
                for ((label, color, grams) in legend) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.padding(end = 6.dp).size(6.dp).background(color, CircleShape))
                        BasicText(label, Modifier.weight(1f), style = style(12, 16, SOFT))
                        Rolling("${grams(day)} g", style(12, 16, INK, FontWeight.SemiBold, tabular = true), still, alignment = Alignment.CenterEnd)
                    }
                }
            }
        }
    }
}
