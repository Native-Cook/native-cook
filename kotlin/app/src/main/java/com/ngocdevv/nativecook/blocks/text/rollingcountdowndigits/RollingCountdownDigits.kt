package com.ngocdevv.nativecook.blocks.text.rollingcountdowndigits

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// A workout interval timer on near-black: a huge "01:02" in a heavy condensed face counts down.
// Each digit that changes rolls in its own window: the old digit slides up and out of the window
// while the new one rises from below into place (1.12 windows of travel), stretched up to 10 %
// while it moves (a hint of motion blur), the old one fading to 0.5 and the new one in from 0.5;
// when several digits change they go from the right, 0.05 s apart. The colon dips on every tick,
// and a lime bar under the time drains with it. Going up (a drag, or the restart) rolls the other
// way: old digits drop out of the bottom, new ones come from above. A port of the SwiftUI block,
// number for number.
//
// autoPlay timeline (LOOP = 9 s), in seconds shown:
//   0.0-0.6   rest at 01:02
//   0.6, 1.6, 2.6, 3.6, 4.6, 5.6   ticks: 01:01, 01:00, 00:59 (three digits roll), 00:58, 00:57,
//             00:56; each roll 0.42 s, ease-out cubic; the colon dips to 0.45 over 0.5 s (a sine bump)
//   5.6-7.4   holds at 00:56
//   7.4-8.3   restart: back to 01:02 in one roll the other way (0.7 s, ease-in-out cubic; three
//             digits, 0.05 s apart)
//   8.3-9.0   rest at 01:02
// The lime dot beside the round breathes (0.35-1, a cosine every 1.5 s, full at 0), so the
// screen is never quite still.
//
// Touch: dragging up adds a second per 24 dp, dragging down takes one away (0:00 to 9:59); every
// step rolls the digits that change (0.3 s, ease-out cubic) with a tick. 2 s after the finger
// lifts the time rolls back to 01:02 like the restart and the loop starts over.

/** Seconds. */
private const val LOOP = 9f
private const val REST = 62
/** The value the bar is full at. */
private const val TOTAL = 90f
private const val TICK_DURATION = 0.42f
private const val STEP_DURATION = 0.3f
private const val RESTART_DURATION = 0.7f
private const val STAGGER = 0.05f
private const val COLON_DIP = 0.55f
private const val COLON_DURATION = 0.5f
/** A roll moves each digit this many windows; the digits stretch by up to STRETCH mid-roll. */
private const val TRAVEL = 1.12f
private const val STRETCH = 0.1f
/** The live dot breathes once every PULSE seconds (six times a loop). */
private const val PULSE = 1.5f
/** Dp of drag per second. */
private const val DRAG_STEP = 24f
private const val USER_HOLD = 2f
private const val SIZE_LIMIT = 180f

private val BACKGROUND = Color(0xFF0B0B0C)
private val INK = Color(0xFFF5F5F2)
private val LIME = Color(0xFFD4FF3F)

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)

/** `inOut`: ease-in-out cubic, else ease-out cubic. */
private fun ease(t: Float, inOut: Boolean) =
    if (!inOut) 1 - (1 - t).pow(3) else if (t < 0.5f) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2

/** A change of the shown value, starting at `time` (seconds into the loop). */
private class Change(val time: Float, val from: Int, val to: Int, val duration: Float, val inOut: Boolean)

/** The autoPlay changes, in order. */
private val CHANGES: List<Change> = List(6) { i -> Change(0.6f + i, REST - i, REST - i - 1, TICK_DURATION, false) } +
    Change(7.4f, REST - 6, REST, RESTART_DURATION, true)

/** The four digits of `value` seconds as m:ss with a leading zero: [m tens, m ones, s tens, s ones]. */
private fun digitsOf(value: Int): IntArray {
    val m = value / 60
    val s = value % 60
    return intArrayOf(m / 10, m % 10, s / 10, s % 10)
}

/** One digit window: rolling from `from` to `to`, `progress` 0-1, `up` when the value grows. */
private class Cell(val from: Int, val to: Int, val progress: Float, val up: Boolean)

private fun still(d: Int) = Cell(d, d, 1f, false)

/**
 * The window `cell` (0-3) under a change that started `elapsed` seconds ago, or null when the
 * change leaves that digit alone. Changed digits start from the right, STAGGER apart.
 */
private fun cellOf(cell: Int, change: Change, elapsed: Float): Cell? {
    val a = digitsOf(change.from)
    val b = digitsOf(change.to)
    if (a[cell] == b[cell]) return null
    val rank = (cell + 1 until 4).count { a[it] != b[it] }
    val p = clamp01((elapsed - rank * STAGGER) / change.duration)
    return Cell(a[cell], b[cell], ease(p, change.inOut), change.to > change.from)
}

/** The four windows `t` seconds into the loop. */
private fun cellsAt(t: Float): List<Cell> {
    val cells = digitsOf(REST).map { still(it) }.toMutableList()
    for (change in CHANGES) {
        if (change.time > t) continue
        for (i in 0 until 4) cellOf(i, change, t - change.time)?.let { cells[i] = it }
    }
    return cells
}

/** The colon's opacity `t` seconds into the loop: it dips on every tick. */
private fun colonAt(t: Float): Float {
    var dip = 0f
    for (change in CHANGES) {
        if (change.inOut) continue
        val u = (t - change.time) / COLON_DURATION
        if (u > 0f && u < 1f) dip = maxOf(dip, sin(PI.toFloat() * u))
    }
    return 1 - COLON_DIP * dip
}

/** The bar's value `t` seconds into the loop: the shown value, moving with the ones digit's roll. */
private fun valueAt(t: Float): Float {
    var v = REST.toFloat()
    for (change in CHANGES) {
        if (change.time > t) continue
        val p = ease(clamp01((t - change.time) / change.duration), change.inOut)
        v = change.from + (change.to - change.from) * p
    }
    return v
}

/** The digits' outlines (px, origin on the baseline) and the window they roll in, for one size. */
private class DigitType(typeface: Typeface, widthDp: Float, density: Float) {
    val size: Float
    val digits: List<androidx.compose.ui.graphics.Path>
    val advances: FloatArray
    val colon: androidx.compose.ui.graphics.Path
    val colonAdvance: Float
    /** Every digit window is this wide (the widest digit's advance), px. */
    val cell: Float
    /** The window, from the baseline: its top (negative) and its height, px. */
    val top: Float
    val height: Float
    val width get() = 4 * cell + colonAdvance

    init {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = 100f
        }
        val probeCell = (0..9).maxOf { paint.measureText(it.toString()) }
        val probeWidth = 4 * probeCell + paint.measureText(":")
        size = min(SIZE_LIMIT, floor(100f * widthDp / probeWidth))
        paint.textSize = size * density
        fun path(s: String) = android.graphics.Path().also { paint.getTextPath(s, 0, s.length, 0f, 0f, it) }
        val zero = path("0")
        digits = (0..9).map { if (it == 0) zero.asComposePath() else path(it.toString()).asComposePath() }
        advances = FloatArray(10) { paint.measureText(it.toString()) }
        colon = path(":").asComposePath()
        colonAdvance = paint.measureText(":")
        cell = advances.max()
        val ink = RectF()
        @Suppress("DEPRECATION")
        zero.computeBounds(ink, true)
        val pad = 0.04f * size * density
        top = ink.top - pad
        height = ink.height() + 2 * pad
    }
}

/**
 * @param autoPlay Counts down and restarts by itself, every [LOOP] seconds.
 */
@Composable
fun RollingCountdownDigits(
    modifier: Modifier = Modifier,
    round: String = "Round 3 of 5",
    session: String = "Core · 20 min",
    title: String = "PLANK HOLD",
    next: String = "Next: Side plank",
    nextTime: String = "0:45",
    pause: String = "Pause",
    skip: String = "Skip",
    hint: String = "Drag the time up or down to change it",
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val reducedMotion = rememberReducedMotion()
    val reduced by rememberUpdatedState(reducedMotion)
    val auto by rememberUpdatedState(autoPlay)

    val typeface = remember(context) {
        runCatching { Typeface.createFromAsset(context.assets, "rollingcountdowndigits/rollingcountdowndigits_Anton-Regular.ttf") }
            .getOrDefault(Typeface.create("sans-serif-condensed", Typeface.BOLD))
    }

    // The frame clock, in nanoseconds; read only in draw and layer blocks.
    val clock = remember { mutableLongStateOf(System.nanoTime()) }
    var origin by remember { mutableLongStateOf(System.nanoTime()) }
    // True from a touch until the time is back at rest and the loop has started over.
    var manual by remember { mutableStateOf(false) }
    // The value set by touch, and the last change of each digit window (with when it started).
    var shown by remember { mutableIntStateOf(REST) }
    val changes = remember { arrayOfNulls<Pair<Change, Long>>(4) }
    var dragging by remember { mutableStateOf(false) }
    // Changes on every touch, which restarts the loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }

    fun loopTime(at: Long) = (((at - origin) / 1e9f) % LOOP).coerceAtLeast(0f)

    fun cells(at: Long): List<Cell> {
        if (manual) {
            val start = digitsOf(shown)
            return (0 until 4).map { i ->
                val last = changes[i] ?: return@map still(start[i])
                cellOf(i, last.first, (at - last.second) / 1e9f) ?: still(start[i])
            }
        }
        if (!auto || reduced) return digitsOf(REST).map { still(it) }
        return cellsAt(loopTime(at))
    }

    fun colonOpacity(at: Long): Float = if (manual || !auto || reduced) 1f else colonAt(loopTime(at))

    /** The live dot: 0.35-1, a cosine that starts full at the loop's start. */
    fun liveOpacity(at: Long): Float =
        if (!auto || reduced) 1f else 0.35f + 0.65f * (0.5f + 0.5f * cos(2 * PI.toFloat() * loopTime(at) / PULSE))

    fun barValue(at: Long): Float {
        if (manual) {
            // The ones digit's roll carries the bar between whole seconds.
            val last = changes[3] ?: return shown.toFloat()
            val c = last.first
            val p = ease(clamp01((at - last.second) / 1e9f / c.duration), c.inOut)
            return c.from + (c.to - c.from) * p
        }
        if (!auto || reduced) return REST.toFloat()
        return valueAt(loopTime(at))
    }

    /** Rolls every digit window that changes from the shown value to `value`. */
    fun roll(value: Int, at: Long, duration: Float, inOut: Boolean) {
        if (value == shown) return
        val change = Change(0f, shown, value, duration, inOut)
        val a = digitsOf(shown)
        val b = digitsOf(value)
        // A window still rolling starts again from the digit it is heading for.
        for (i in 0 until 4) if (a[i] != b[i]) changes[i] = change to at
        // The ones digit always carries the bar.
        changes[3] = change to at
        shown = value
    }

    LaunchedEffect(reducedMotion, manual) {
        if (reducedMotion && !manual) return@LaunchedEffect
        while (true) withFrameNanos { clock.longValue = it.coerceAtLeast(clock.longValue) }
    }

    // 2 s after the last touch the time rolls back to 01:02, and the loop starts over.
    LaunchedEffect(touches) {
        if (touches == 0) return@LaunchedEffect
        delay((USER_HOLD * 1000).toLong())
        if (dragging) return@LaunchedEffect
        roll(REST, clock.longValue, RESTART_DURATION, true)
        delay(((RESTART_DURATION + 3 * STAGGER) * 1000).toLong())
        if (dragging) return@LaunchedEffect
        origin = clock.longValue
        manual = false
    }

    val insets = WindowInsets.safeDrawing.asPaddingValues()

    BoxWithConstraints(modifier.fillMaxSize().background(BACKGROUND)) {
        val type = remember(typeface, maxWidth) { DigitType(typeface, maxWidth.value - 48f, density.density) }
        val typeWidth = with(density) { type.width.toDp() }

        Column(
            Modifier
                .fillMaxSize()
                .pointerInput(reducedMotion) {
                    if (reducedMotion) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        // Take over from the loop at the value it shows (a roll in flight lands at once).
                        val current = cells(clock.longValue).map { it.to }
                        val start = current[0] * 600 + current[1] * 60 + current[2] * 10 + current[3]
                        manual = true
                        dragging = true
                        shown = start
                        changes.fill(null)
                        touches++
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            val dy = (change.position.y - down.position.y) / density.density
                            val steps = (-dy / DRAG_STEP).toInt()
                            val target = (start + steps).coerceIn(0, 599)
                            if (target != shown) {
                                roll(target, clock.longValue, STEP_DURATION, false)
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                        dragging = false
                        touches++
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.padding(top = insets.calculateTopPadding() + 14.dp).padding(horizontal = 24.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Box(
                    Modifier
                        .size(8.dp)
                        .graphicsLayer { alpha = liveOpacity(clock.longValue) }
                        .background(LIME, CircleShape),
                )
                Text(round, modifier = Modifier.padding(start = 8.dp), style = style.copy(color = INK))
                Spacer(Modifier.weight(1f))
                Text(session, style = style.copy(color = INK.copy(alpha = 0.5f)))
            }

            Spacer(Modifier.weight(1f))

            Text(title, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp, color = LIME))

            // The time: four digit windows and the colon.
            Box(
                Modifier
                    .padding(top = 20.dp)
                    .fillMaxWidth()
                    .height(with(density) { type.height.toDp() })
                    .semantics {
                        val d = digitsOf(shown)
                        contentDescription = "${d[0]}${d[1]} minutes ${d[2]}${d[3]} seconds"
                    }
                    .drawBehind {
                        val at = clock.longValue
                        val cells = cells(at)
                        val left = (size.width - type.width) / 2
                        val baseline = -type.top
                        cells.forEachIndexed { i, cell ->
                            val x = left + i * type.cell + (if (i >= 2) type.colonAdvance else 0f)
                            clipRect(x, 0f, x + type.cell, type.height) {
                                val p = cell.progress
                                // The old digit leaves upwards (downwards when the value grows); the new
                                // one follows a little more than a window behind, so the stretched
                                // digits never overlap.
                                val travel = TRAVEL * type.height
                                val dir = if (cell.up) 1f else -1f
                                if (p < 1f) digit(type, cell.from, x, baseline + dir * p * travel, p, 1 - 0.5f * p)
                                digit(type, cell.to, x, baseline - dir * (1 - p) * travel, p, 0.5f + 0.5f * p)
                            }
                        }
                        translate(left + 2 * type.cell, baseline) {
                            drawPath(type.colon, INK.copy(alpha = colonOpacity(at)))
                        }
                    },
            )

            Column(Modifier.padding(top = 28.dp).width(typeWidth), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .drawBehind {
                            val f = clamp01(barValue(clock.longValue) / TOTAL)
                            val r = CornerRadius(size.height / 2)
                            drawRoundRect(INK.copy(alpha = 0.14f), cornerRadius = r)
                            drawRoundRect(LIME, size = Size(size.width * f, size.height), cornerRadius = r)
                        },
                )
                Row(Modifier.fillMaxWidth()) {
                    val style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = INK.copy(alpha = 0.55f), fontFeatureSettings = "tnum")
                    Text(next, style = style)
                    Spacer(Modifier.weight(1f))
                    Text(nextTime, style = style)
                }
            }

            Spacer(Modifier.weight(1f))

            Row(Modifier.padding(horizontal = 24.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(56.dp)
                        .background(INK, CircleShape)
                        .semantics { role = Role.Button },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(pause, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = BACKGROUND))
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(56.dp)
                        .border(1.5.dp, INK.copy(alpha = 0.25f), CircleShape)
                        .semantics { role = Role.Button },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(skip, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = INK))
                }
            }

            Text(
                hint,
                modifier = Modifier.padding(top = 16.dp).padding(bottom = insets.calculateBottomPadding() + 12.dp),
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = INK.copy(alpha = 0.4f)),
            )
        }
    }
}

/** A digit centred in its window, stretched vertically by up to 10 % mid-roll (px). */
private fun DrawScope.digit(type: DigitType, digit: Int, x: Float, baseline: Float, p: Float, opacity: Float) {
    val s = 1 + STRETCH * sin(PI.toFloat() * p)
    val middle = baseline + type.top + type.height / 2
    withTransform({
        translate(x + (type.cell - type.advances[digit]) / 2, middle)
        scale(1f, s, androidx.compose.ui.geometry.Offset.Zero)
        translate(0f, baseline - middle)
    }) {
        drawPath(type.digits[digit], INK.copy(alpha = opacity))
    }
}
