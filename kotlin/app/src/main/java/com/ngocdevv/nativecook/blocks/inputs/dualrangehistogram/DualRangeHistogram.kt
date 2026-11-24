package com.ngocdevv.nativecook.blocks.inputs.dualrangehistogram

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val INSET = THUMB / 2
private const val MORPH_S = 0.42
private const val COUNT_S = 0.22
private const val TICK_PRICE = 250
private const val TICK_MIN_S = 0.07

private val MUTED = Color(0xFFDDD6CA)
private val TRACK = Color(0xFFE4DDD1)
private val FILL_TOP = Color(0xFFE8703F)
private val FILL_MID = Color(0xFFD9542F)
private val FILL_LOW = Color(0xFFB83F2A)
private val THUMB_FILL = Color(0xFFFFFDF9)
private val THUMB_EDGE = Color(0xFFE6DFD3)
private val TAG = Color(0xFF2A2420)
private val TAG_INK = Color(0xFFFBF6EE)
private val GHOST = Color(42, 36, 32).copy(alpha = 0.2f)
private val THUMB_SHADOW_NEAR = Color(60, 40, 25).copy(alpha = 0.28f)
private val THUMB_SHADOW_FAR = Color(60, 40, 25).copy(alpha = 0.16f)

/** A value moved by Reanimated-style springs (mass 1), integrated in small steps. */
private class Spring(var x: Double) {
    var v = 0.0
    var to = x
    var spec = RELEASE_SPRING
    var on = false

    fun set(value: Double) {
        x = value
        v = 0.0
        on = false
    }

    fun cancel() {
        on = false
    }

    /** Like `withSpring`: keeps the running velocity unless one is given. */
    fun animate(target: Double, s: SpringSpec, velocity: Double? = null) {
        if (!on) v = 0.0
        if (velocity != null) v = velocity
        to = target
        spec = s
        on = true
    }

    fun step(dt: Double, rest: Double): Boolean {
        if (!on) return false
        val steps = max(1, (dt * 480).toInt())
        val h = dt / steps
        repeat(steps) {
            val a = -spec.stiffness * (x - to) - spec.damping * v
            v += a * h
            x += v * h
        }
        if (abs(x - to) < rest && abs(v) < rest * 20) {
            x = to
            v = 0.0
            on = false
        }
        return true
    }
}

/** Everything that moves. Read in layer and draw lambdas only (through [tick]), so nothing recomposes per frame. */
@Stable
private class RangeState(var restLow: Double, var restHigh: Double, var gap: Double, film: Boolean, bins: FloatArray) {
    var film = film
    var plan = makeFilm(restLow, restHigh, gap)
    var tick by mutableLongStateOf(0L)

    var mode = if (film) MODE_AUTO else MODE_MANUAL
    var clock = 0.0
    var loopStart = -1.0
    var lastTouch = 0.0
    var returnAt = 0.0
    var holding = false

    val low = Spring(restLow)
    val high = Spring(restHigh)
    val pressLow = Spring(0.0)
    val pressHigh = Spring(0.0)
    var ghost = 0.0
    var ghostSide = 0

    var grabbed = -1 // 0 low, 1 high, -1 none yet
    var nearest = 0
    var startX = 0f
    var grabOffset = 0f
    var lastTick = 0.0
    var tickKey = -1

    var binsFrom = bins.copyOf()
    var binsTo = bins.copyOf()
    var morph = 1.0
    var morphStart = -1.0

    var countShown = 0.0
    var countFrom = 0.0
    var countTo = -1.0
    var countStart = 0.0

    var onReturn: () -> Unit = {}
    var onTick: () -> Unit = {}

    val lowU get() = shownLow(low.x, high.x, gap)
    val highU get() = shownHigh(low.x, high.x, gap)

    fun retargetBins(next: FloatArray) {
        val current = FloatArray(next.size) { mixF(binsFrom.getOrElse(it) { 0f }, binsTo.getOrElse(it) { 0f }, morph.toFloat()) }
        binsFrom = current
        binsTo = next.copyOf()
        morph = 0.0
        morphStart = clock
        tick++
    }

    fun frame(now: Double, count: MutableFloatState?, lowPrice: () -> Double, highPrice: () -> Double) {
        val dt = clamp(now - clock, 1.0 / 240, 0.05)
        clock = now
        var moved = false
        when (mode) {
            MODE_AUTO -> {
                if (!film) {
                    mode = MODE_MANUAL
                    ghost = 0.0
                    pressLow.set(0.0)
                    pressHigh.set(0.0)
                    low.set(restLow)
                    high.set(restHigh)
                } else {
                    if (loopStart < 0) loopStart = now
                    val t = (now - loopStart) % LOOP
                    low.set(filmLow(t, plan))
                    high.set(filmHigh(t, plan))
                    pressLow.set(filmPressLow(t))
                    pressHigh.set(filmPressHigh(t))
                    ghost = filmGhost(t)
                    ghostSide = filmGhostSide(t)
                }
                moved = true
            }
            MODE_MANUAL -> {
                if (film && !holding && now - lastTouch > IDLE_S) {
                    mode = MODE_RETURN
                    returnAt = now + RETURN_S
                    low.animate(restLow, RELEASE_SPRING)
                    high.animate(restHigh, RELEASE_SPRING)
                    onReturn()
                }
            }
            else -> if (now >= returnAt) {
                low.set(restLow)
                high.set(restHigh)
                loopStart = now
                mode = MODE_AUTO
                moved = true
            }
        }
        if (low.step(dt, 1e-5)) moved = true
        if (high.step(dt, 1e-5)) moved = true
        if (pressLow.step(dt, 1e-4)) moved = true
        if (pressHigh.step(dt, 1e-4)) moved = true

        if (morph < 1.0) {
            morph = easeMorph(seg(now, morphStart, morphStart + MORPH_S))
            if (now >= morphStart + MORPH_S) morph = 1.0
            moved = true
        }

        // The count: the bins' share inside the range, rolled to each new whole number.
        val target = rangeCount(binsFrom, binsTo, morph, lowU, highU).roundToInt().toDouble()
        if (countTo < 0) {
            countTo = target
            countShown = target
        } else if (target != countTo) {
            countFrom = countShown
            countTo = target
            countStart = now
        }
        if (countShown != countTo) {
            val k = seg(now, countStart, countStart + COUNT_S)
            countShown = mix(countFrom, countTo, easeMorph(k))
            if (k >= 1.0) countShown = countTo
        }
        if (count != null && count.floatValue != countShown.toFloat()) count.floatValue = countShown.toFloat()

        // A selection tick each $250 under a held thumb and one at the limit.
        val key = if (!holding || mode != MODE_MANUAL || grabbed < 0) {
            -1
        } else {
            val atLimit = if (grabbed == 0) low.x < 0 || low.x > high.x - gap else high.x > 1 || high.x < low.x + gap
            val price = if (grabbed == 0) lowPrice() else highPrice()
            floor(price / TICK_PRICE).toInt() * 2 + if (atLimit) 1 else 0
        }
        if (key >= 0 && tickKey >= 0 && key != tickKey && now - lastTick >= TICK_MIN_S) {
            lastTick = now
            onTick()
        }
        tickKey = key

        if (moved) tick++
    }
}

/**
 * A price histogram over a two-thumb range. Bars inside the range take the colour, bars outside drain and sink, the
 * labels ride the thumbs and roll their digits, and the thumbs squash when held and refuse to cross with a rubber band.
 *
 * [bins] are listings per price bin, evenly spaced from [minValue] to [maxValue]; a new array morphs the bars. Thumbs land on
 * multiples of [step], at least [minGap] apart. [count] receives the number of listings in the range, rolled to whole
 * numbers. Change [resetKey] to send the thumbs back to [defaultValue].
 */
@Composable
fun DualRangeHistogram(
    bins: FloatArray,
    minValue: Int,
    maxValue: Int,
    step: Int,
    minGap: Int,
    defaultValue: Pair<Int, Int>,
    width: Dp,
    chartHeight: Dp,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    count: MutableFloatState? = null,
    resetKey: Int = 0,
    onChange: ((Int, Int) -> Unit)? = null,
) {
    val reduced = rememberLiveReducedMotion()
    val film = autoPlay && !reduced
    val density = LocalDensity.current.density
    val haptics = LocalHapticFeedback.current
    val base = minValue.toDouble()
    val range = (maxValue - minValue).toDouble()
    val gap = minGap / range
    val stepU = step / range
    val restLow = (defaultValue.first - minValue) / range
    val restHigh = (defaultValue.second - minValue) / range
    val w = width.value
    val chartH = chartHeight.value
    val span = w - INSET * 2

    val state = remember { RangeState(restLow, restHigh, gap, film, bins) }
    var settled by remember { mutableStateOf(defaultValue.first.toDouble() to defaultValue.second.toDouble()) }
    val changeCallback by rememberUpdatedState(onChange)

    fun report(a: Double, b: Double) {
        settled = a to b
        changeCallback?.invoke(a.roundToInt(), b.roundToInt())
    }

    fun lowPrice() = base + clamp(state.lowU, 0.0, 1.0) * range
    fun highPrice() = base + clamp(state.highU, 0.0, 1.0) * range

    SideEffect {
        state.film = film
        if (state.restLow != restLow || state.restHigh != restHigh || state.gap != gap) {
            state.restLow = restLow
            state.restHigh = restHigh
            state.gap = gap
            state.plan = makeFilm(restLow, restHigh, gap)
        }
        state.onReturn = { report(base + restLow * range, base + restHigh * range) }
        state.onTick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
    }

    // New bins morph; the first ones are already in place.
    val firstBins = remember { mutableStateOf(true) }
    LaunchedEffect(bins) {
        if (firstBins.value) firstBins.value = false else state.retargetBins(bins)
    }
    val firstReset = remember { mutableStateOf(true) }
    LaunchedEffect(resetKey) {
        if (firstReset.value) {
            firstReset.value = false
        } else {
            state.low.animate(restLow, RELEASE_SPRING)
            state.high.animate(restHigh, RELEASE_SPRING)
            settled = (base + restLow * range) to (base + restHigh * range)
        }
    }

    LaunchedEffect(state) {
        while (true) {
            withFrameNanos { nanos -> state.frame(nanos / 1e9, count, ::lowPrice, ::highPrice) }
        }
    }

    fun nudge(side: Int, direction: Int) {
        state.mode = MODE_MANUAL
        state.lastTouch = state.clock
        val a = state.low.x
        val b = state.high.x
        if (side == 0) {
            val next = clamp(a + direction * stepU * 2, 0.0, b - gap)
            state.low.animate(next, RELEASE_SPRING)
            report(base + next * range, base + b * range)
        } else {
            val next = clamp(b + direction * stepU * 2, a + gap, 1.0)
            state.high.animate(next, RELEASE_SPRING)
            report(base + a * range, base + next * range)
        }
    }

    val totalH = chartH + TRACK_AREA + TAG_H + 4f
    val thumbTop = chartH + (TRACK_AREA - THUMB) / 2
    val trackY = chartH + TRACK_AREA / 2 - TRACK_H / 2
    val bars = remember { Path() }
    val fill = remember(chartH) {
        Brush.verticalGradient(0f to FILL_TOP, 0.55f to FILL_MID, 1f to FILL_LOW, startY = 0f, endY = chartH * density)
    }

    Box(
        modifier
            .size(width, totalH.dp)
            .pointerInput(state, span, stepU, gap) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    val x0 = down.position.x / density
                    // onBegin
                    state.mode = MODE_MANUAL
                    state.holding = true
                    state.lastTouch = state.clock
                    state.ghost = 0.0
                    state.low.cancel()
                    state.high.cancel()
                    val lx = INSET + state.lowU * span
                    val hx = INSET + state.highU * span
                    val dl = abs(x0 - lx)
                    val dh = abs(x0 - hx)
                    // Thumbs in contact: the side the finger is on decides.
                    val side = if (abs(dl - dh) < 2) (if (x0 > (lx + hx) / 2) 1 else 0) else if (dl < dh) 0 else 1
                    state.nearest = side
                    state.startX = x0
                    state.pressLow.set(0.0)
                    state.pressHigh.set(0.0)
                    if (min(dl, dh) <= GRAB_R) {
                        state.grabbed = side
                        state.grabOffset = ((if (side == 0) lx else hx) - x0).toFloat()
                        (if (side == 0) state.pressLow else state.pressHigh).animate(1.0, PRESS_SPRING)
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    } else {
                        state.grabbed = -1
                        state.grabOffset = 0f
                    }
                    state.tick++
                    var lastX = x0
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPosition(c.uptimeMillis, c.position)
                        lastX = c.position.x / density
                        if (!c.pressed) break
                        if (c.position == c.previousPosition) continue
                        c.consume()
                        // onUpdate
                        state.lastTouch = state.clock
                        if (state.grabbed < 0) {
                            if (abs(lastX - state.startX) <= TAP_SLOP) continue
                            val s = state.nearest
                            state.grabbed = s
                            (if (s == 0) state.pressLow else state.pressHigh).animate(1.0, PRESS_SPRING)
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        }
                        val u = (lastX + state.grabOffset - INSET) / span.toDouble()
                        if (state.grabbed == 0) state.low.set(constrain(u, 0.0, state.high.x - gap))
                        else state.high.set(constrain(u, state.low.x + gap, 1.0))
                        state.tick++
                    }
                    // onFinalize
                    state.holding = false
                    state.lastTouch = state.clock
                    state.pressLow.animate(0.0, PRESS_SPRING)
                    state.pressHigh.animate(0.0, PRESS_SPRING)
                    var s = state.grabbed
                    var target: Double
                    var velocity = 0.0
                    if (s < 0) {
                        // A tap on the chart: the nearest thumb jumps to the bar under the finger.
                        s = state.nearest
                        val n = state.binsTo.size
                        val bin = floor((lastX - INSET) / span * n).toInt().coerceIn(0, n - 1)
                        target = (bin + 0.5) / n
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    } else {
                        velocity = tracker.calculateVelocity().x / density / span.toDouble()
                        target = (if (s == 0) state.low.x else state.high.x) + velocity * FLING_S
                    }
                    target = (target / stepU).roundToInt() * stepU
                    val lowNow = state.low.x
                    val highNow = state.high.x
                    if (s == 0) {
                        target = clamp(target, 0.0, floor((highNow - gap) / stepU + 1e-6) * stepU)
                        state.low.animate(target, RELEASE_SPRING, velocity)
                        report(base + target * range, base + highNow * range)
                    } else {
                        target = clamp(target, ceil((lowNow + gap) / stepU - 1e-6) * stepU, 1.0)
                        state.high.animate(target, RELEASE_SPRING, velocity)
                        report(base + lowNow * range, base + target * range)
                    }
                    state.grabbed = -1
                    state.tick++
                }
            },
    ) {
        Canvas(Modifier.size(width, (chartH + TRACK_AREA).dp)) {
            state.tick
            val d = density
            val a = state.binsFrom
            val b = state.binsTo
            val morph = state.morph
            val n = b.size
            val lowU = state.lowU
            val highU = state.highU
            val slot = span / n
            val barW = max(1f, slot - BAR_GAP)
            val lowX = INSET + lowU.toFloat() * span
            val highX = INSET + highU.toFloat() * span
            val pl = clamp(state.pressLow.x, 0.0, 2.0).toFloat()
            val ph = clamp(state.pressHigh.x, 0.0, 2.0).toFloat()
            var peak = 1f
            for (i in 0 until n) peak = max(peak, mixF(a.getOrElse(i) { 0f }, b[i], morph.toFloat()))
            val room = chartH - LIFT_PT - 2
            bars.reset()
            val r = min(BAR_RADIUS, barW / 2)
            for (i in 0 until n) {
                val cx = INSET + (i + 0.5f) * slot
                val inside = (smooth(seg(cx.toDouble(), (lowX - slot).toDouble(), (lowX + slot).toDouble())) *
                    (1 - smooth(seg(cx.toDouble(), (highX - slot).toDouble(), (highX + slot).toDouble())))).toFloat()
                val dl = (cx - lowX) / LIFT_SIGMA
                val dh = (cx - highX) / LIFT_SIGMA
                val lift = LIFT_PT * (pl * exp(-dl * dl) + ph * exp(-dh * dh))
                val value = mixF(a.getOrElse(i) { 0f }, b[i], morph.toFloat()) / peak
                val h = max(3f, room * value * mixF(SINK, 1f, inside) + lift)
                bars.addRoundRect(
                    RoundRect(
                        (cx - barW / 2) * d, (chartH - h) * d, (cx + barW / 2) * d, chartH * d,
                        CornerRadius(r * d),
                    ),
                )
            }
            drawPath(bars, MUTED)
            clipRect(left = lowX * d, top = 0f, right = max(lowX, highX) * d, bottom = chartH * d) {
                drawPath(bars, fill)
            }
            drawRoundRect(
                TRACK,
                topLeft = Offset(INSET * d, trackY * d),
                size = Size(span * d, TRACK_H * d),
                cornerRadius = CornerRadius(TRACK_H / 2 * d),
            )
            drawRoundRect(
                FILL_MID,
                topLeft = Offset(lowX * d, trackY * d),
                size = Size(max(0f, (highU - lowU).toFloat() * span) * d, TRACK_H * d),
                cornerRadius = CornerRadius(TRACK_H / 2 * d),
            )
        }

        RangeThumb(
            state = state,
            side = 0,
            top = thumbTop,
            span = span,
            label = "Minimum price",
            value = settled.first,
            onNudge = { nudge(0, it) },
        )
        RangeThumb(
            state = state,
            side = 1,
            top = thumbTop,
            span = span,
            label = "Maximum price",
            value = settled.second,
            onNudge = { nudge(1, it) },
        )

        Box(
            Modifier
                .offset(y = (chartH + (TRACK_AREA - 46f) / 2).dp)
                .size(46.dp)
                .graphicsLayer {
                    state.tick
                    val u = if (state.ghostSide == 0) state.lowU else state.highU
                    val p = clamp(if (state.ghostSide == 0) state.pressLow.x else state.pressHigh.x, 0.0, 1.0).toFloat()
                    alpha = clamp(state.ghost, 0.0, 1.0).toFloat() * 0.9f
                    translationX = (INSET + u.toFloat() * span - 23f) * density
                    val s = mixF(1.12f, 0.94f, p)
                    scaleX = s
                    scaleY = s
                }
                .background(GHOST, CircleShape),
        )

        // The labels ride their thumbs and move apart instead of overlapping.
        val tagTop = chartH + TRACK_AREA + 2f
        PriceTag(top = tagTop, x = {
            tagLeft(state, span, w)
        }, value = ::lowPrice, tick = { state.tick })
        PriceTag(top = tagTop, x = {
            val l = INSET + state.lowU.toFloat() * span - TAG_W / 2
            val r = INSET + state.highU.toFloat() * span - TAG_W / 2
            val right = if (r - l < TAG_W + TAG_GAP) (l + r) / 2 + (TAG_W + TAG_GAP) / 2 else r
            clampF(right, tagLeft(state, span, w) + TAG_W + TAG_GAP, w - TAG_W)
        }, value = ::highPrice, tick = { state.tick })
    }
}

private fun tagLeft(state: RangeState, span: Float, w: Float): Float {
    val l = INSET + state.lowU.toFloat() * span - TAG_W / 2
    val r = INSET + state.highU.toFloat() * span - TAG_W / 2
    val left = if (r - l < TAG_W + TAG_GAP) (l + r) / 2 - (TAG_W + TAG_GAP) / 2 else l
    return clampF(left, 0f, w - TAG_W * 2 - TAG_GAP)
}

@Composable
private fun RangeThumb(
    state: RangeState,
    side: Int,
    top: Float,
    span: Float,
    label: String,
    value: Double,
    onNudge: (Int) -> Unit,
) {
    val density = LocalDensity.current.density
    val text = String.format(Locale.US, "$%,d", value.roundToInt())
    Box(
        Modifier
            .offset(y = top.dp)
            .size(THUMB.dp)
            .graphicsLayer {
                state.tick
                val u = if (side == 0) state.lowU else state.highU
                val p = clamp(if (side == 0) state.pressLow.x else state.pressHigh.x, 0.0, 1.0).toFloat()
                translationX = (INSET + u.toFloat() * span - THUMB / 2) * density
                scaleX = mixF(1f, SQUASH_X, p)
                scaleY = mixF(1f, SQUASH_Y, p)
            }
            .semantics {
                contentDescription = label
                stateDescription = text
                customActions = listOf(
                    CustomAccessibilityAction("Increase") { onNudge(1); true },
                    CustomAccessibilityAction("Decrease") { onNudge(-1); true },
                )
            }
            .cssBoxShadow(THUMB_SHADOW_NEAR, 1.dp, 3.dp, (THUMB / 2).dp)
            .cssBoxShadow(THUMB_SHADOW_FAR, 6.dp, 16.dp, (THUMB / 2).dp)
            .background(THUMB_FILL, CircleShape)
            .border((1f / density).dp, THUMB_EDGE, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(8.dp).background(FILL_MID, CircleShape))
    }
}

private const val DIGIT_H = 18f
private const val DIGIT_W = 8.6f
private val TAG_TEXT = TextStyle(color = TAG_INK, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")

@Composable
private fun PriceTag(top: Float, x: () -> Float, value: () -> Double, tick: () -> Long) {
    val density = LocalDensity.current.density
    Box(
        Modifier
            .offset(y = top.dp)
            .size(TAG_W.dp, TAG_H.dp)
            .graphicsLayer {
                tick()
                translationX = x() * density
            }
            .background(TAG, RoundedCornerShape((TAG_H / 2).dp)),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$", style = TAG_TEXT, softWrap = false)
            RollingDigit(value, tick, place = 1000, width = DIGIT_W.dp, height = DIGIT_H.dp, style = TAG_TEXT)
            Text(",", style = TAG_TEXT, softWrap = false)
            RollingDigit(value, tick, place = 100, width = DIGIT_W.dp, height = DIGIT_H.dp, style = TAG_TEXT)
            RollingDigit(value, tick, place = 10, lowest = true, width = DIGIT_W.dp, height = DIGIT_H.dp, style = TAG_TEXT)
            Text("0", style = TAG_TEXT, softWrap = false)
        }
    }
}

private val DIGITS = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

/** One odometer column: eleven rows in a clipped strip moved by the value. */
@Composable
internal fun RollingDigit(
    value: () -> Double,
    tick: () -> Long,
    place: Int,
    width: Dp,
    height: Dp,
    style: TextStyle,
    lowest: Boolean = false,
    /** Fades the column out while the number has no digit at this place. */
    hideLeading: Boolean = false,
) {
    Box(Modifier.size(width, height).clipToBounds()) {
        Column(
            Modifier
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .graphicsLayer {
                    tick()
                    val v = value()
                    alpha = if (hideLeading && v < place) max(0.0, (v - place * 0.9) / (place * 0.1)).toFloat() else 1f
                    translationY = -(digitAt(v, place, lowest) * height.toPx()).toFloat()
                },
        ) {
            DIGITS.forEach { d ->
                Box(Modifier.size(width, height), contentAlignment = Alignment.Center) {
                    Text(d, style = style, softWrap = false, maxLines = 1)
                }
            }
        }
    }
}
