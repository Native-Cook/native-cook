package com.ngocdevv.nativecook.blocks.navigation.switcherdecknav

import android.graphics.Matrix
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

// The deck. A page in the deck is DECK_SCALE of the screen, centred DECK_Y dp below the middle.
// Its place depends on p, its slot minus the deck's scroll (0 = the page in front): pages to the
// right lie on top and spread wide, pages to the left tuck under, recede and darken, like the
// pages of a fanned deck. Every page is turned about its upright axis (right edge away), more so
// the further left it lies.
private const val DECK_SCALE = 0.6f
private const val DECK_Y = 22f
private const val SPREAD = 0.56f
private const val SPREAD_SKEW = 0.15f
private const val RECEDE = 0.045f
private const val TURN_LEFT = 30f
private const val TURN_FRONT = 17f
private const val TURN_RIGHT = 8f
private const val PERSPECTIVE = 1100f
private const val DIM_PER_PAGE = 0.2f
private const val DIM_MAX = 0.55f
// Corner radius of a page in its own (unscaled) dp: none on the full screen, then rounded as soon
// as it starts to shrink.
private const val RADIUS_SHRINKING = 50f
private const val RADIUS_DECK = 38f
// The title chip above each page, in screen dp (drawn inside the page, so divided by its scale).
private const val CHIP_HEIGHT = 28f
private const val CHIP_GAP = 12f

// Pulling the handle: the finger travels OPEN_DIST of the screen's height to open the deck all
// the way. A release is projected 0.18 s ahead; past OPEN_AT it opens, otherwise it falls back.
private const val HANDLE_ZONE = 70f
private const val OPEN_DIST = 0.42f
private const val OPEN_AT = 0.38f
private const val THROW = 0.18f
private const val FOLLOW_X = 0.55f
// Scrolling: one page per SCROLL_STEP of the page width; a release is thrown 0.22 s ahead.
private const val SCROLL_STEP = 0.55f
private const val SCROLL_THROW = 0.22f
// Flicking a page up closes it: faster than DISMISS_SPEED dp/s or past DISMISS_AT of its height.
private const val DISMISS_SPEED = 800f
private const val DISMISS_AT = 0.28f
private const val DISMISS_MS = 300f

private const val PAN_SLOP = 6f
private const val TAP_MS = 350L

private class SpringSpec(val durationMs: Float, val dampingRatio: Float)

private val OpenSpring = SpringSpec(560f, 0.82f)
private val ZoomSpring = SpringSpec(540f, 0.9f)
private val ScrollSpring = SpringSpec(620f, 0.88f)
private val SlotSpring = SpringSpec(520f, 0.84f)
private val BackSpring = SpringSpec(420f, 0.8f)
private const val REDUCED_MS = 220f
private val EaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

// autoPlay (seconds). The loop starts and ends on the journal at full screen. Each stroke is a
// virtual finger: the handle pulled up, a flick across the deck (to `to`), or a tap on a page.
private const val LOOP = 12f

private enum class StrokeKind { Handle, Flick, Tap }

private class Stroke(val kind: StrokeKind, val t0: Float, val t1: Float, val from: Float, val to: Float)

private val Strokes = listOf(
    Stroke(StrokeKind.Handle, 0.9f, 1.7f, 0f, 0f),
    Stroke(StrokeKind.Flick, 2.75f, 3.05f, 0f, 2f),
    Stroke(StrokeKind.Flick, 3.95f, 4.25f, 2f, 3f),
    Stroke(StrokeKind.Flick, 5.0f, 5.3f, 3f, 2f),
    Stroke(StrokeKind.Tap, 6.15f, 6.25f, 2f, 2f),
    Stroke(StrokeKind.Handle, 7.55f, 8.35f, 2f, 2f),
    Stroke(StrokeKind.Flick, 9.25f, 9.55f, 2f, 0f),
    Stroke(StrokeKind.Tap, 10.35f, 10.45f, 0f, 0f),
)
// A touch stops the loop for USER_HOLD s; it then goes back to the journal over LEAD s and restarts.
private const val USER_HOLD = 5f
private const val LEAD = 2.2f

private fun clamp(x: Float, lo: Float, hi: Float) = min(hi, max(lo, x))

/** Pushing past [lo, hi] meets a soft resistance that never quite gives. */
private fun rubber(x: Float, lo: Float, hi: Float, give: Float): Float {
    if (x > hi) return hi + (1f - 1f / (((x - hi) * 0.55f) / give + 1f)) * give
    if (x < lo) return lo - (1f - 1f / (((lo - x) * 0.55f) / give + 1f)) * give
    return x
}

/** Horizontal offset of a page's centre from the screen's, for a page p places from the front. */
private fun deckX(p: Float, cardWidth: Float) = cardWidth * p * (SPREAD + SPREAD_SKEW * tanh(p))

private fun deckScale(p: Float) = DECK_SCALE * (1f - RECEDE * clamp(-p, 0f, 3f))

private fun deckTurn(p: Float): Float {
    val q = clamp(p, -3f, 3f)
    return if (q < 0f) TURN_FRONT + (TURN_LEFT - TURN_FRONT) * (-q / 3f) else TURN_FRONT + (TURN_RIGHT - TURN_FRONT) * (q / 3f)
}

/** The slot of the topmost page under a point in the deck, or -1. Pages to the right lie on top. */
private fun hitSlot(x: Float, y: Float, count: Int, scroll: Float, width: Float, height: Float): Int {
    val cardWidth = width * DECK_SCALE
    for (k in count - 1 downTo 0) {
        val p = k - scroll
        val s = deckScale(p) / DECK_SCALE
        val cx = width / 2f + deckX(p, cardWidth)
        val cy = height / 2f + DECK_Y
        if (abs(x - cx) < (cardWidth * s) / 2f && abs(y - cy) < (height * DECK_SCALE * s) / 2f) return k
    }
    return -1
}

/** Whether a clock that went from `before` to `now` (wrapping at LOOP) crossed `at`. */
private fun crossed(before: Float, now: Float, at: Float) =
    (before < at && now >= at) || (now < before && (at > before || at <= now))

/** JavaScript's Math.round: halves go up. */
private fun jsRound(x: Float) = kotlin.math.floor(x + 0.5f)

/**
 * A number moved the way a Reanimated shared value is: set at once, sprung like
 * `withSpring({ duration, dampingRatio, velocity })`, or timed on a curve.
 */
private class DeckValue(initial: Float) {
    var value = initial
        private set
    private var mode = 0 // 0 still, 1 spring, 2 timing
    private var to = 0f
    private var velocity = 0f
    private var zeta = 0f
    private var omega0 = 0f
    private var omega1 = 0f
    private var stiffness = 0f
    private var initialEnergy = 0f
    private var from = 0f
    private var time = 0f
    private var duration = 0f
    private var easing: Easing = LinearEasing

    /** Puts the value somewhere at once, stopping whatever was moving it. */
    fun set(next: Float) {
        value = next
        mode = 0
        velocity = 0f
    }

    fun spring(target: Float, spec: SpringSpec, speed: Float = 0f) {
        // A spring already heading to the same place carries on as it was.
        if (mode == 1 && to == target) return
        // A timing has no speed of its own to hand on; Reanimated drops the speed asked for too.
        var v = when (mode) {
            1 -> velocity + speed
            2 -> 0f
            else -> speed
        }
        // Speed that would carry the first frame away from the target is dropped.
        if ((target > value && v < 0f) || (target < value && v > 0f)) v = 0f
        val x0 = value - target
        mode = 1
        to = target
        velocity = v
        zeta = spec.dampingRatio
        stiffness = stiffnessFor(x0, v, spec.durationMs, zeta)
        omega0 = sqrt(stiffness)
        omega1 = if (zeta < 1f) omega0 * sqrt(1f - zeta * zeta) else 0f
        initialEnergy = energy(x0, speed)
    }

    fun timing(target: Float, durationMs: Float, curve: Easing = LinearEasing) {
        mode = 2
        from = value
        to = target
        time = 0f
        duration = durationMs / 1000f
        easing = curve
        velocity = 0f
    }

    /** Moves the value on by [dt] seconds; true on the frame its motion ends. */
    fun step(dt: Float): Boolean {
        when (mode) {
            1 -> {
                val x0 = value - to
                val v0 = velocity
                if (zeta < 1f) {
                    val sin1 = sin(omega1 * dt)
                    val cos1 = cos(omega1 * dt)
                    val envelope = exp(-zeta * omega0 * dt)
                    val frag = envelope * (sin1 * ((v0 + zeta * omega0 * x0) / omega1) + x0 * cos1)
                    value = to + frag
                    velocity = -zeta * omega0 * frag + envelope * (cos1 * (v0 + zeta * omega0 * x0) - omega1 * x0 * sin1)
                } else {
                    val envelope = exp(-omega0 * dt)
                    value = to + envelope * (x0 + (v0 + omega0 * x0) * dt)
                    velocity = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * dt) + envelope * (v0 + omega0 * x0)
                }
                if (initialEnergy == 0f || energy(to - value, velocity) / initialEnergy <= ENERGY_THRESHOLD) {
                    value = to
                    velocity = 0f
                    mode = 0
                    return true
                }
            }
            2 -> {
                time += dt
                val p = if (duration > 0f) clamp(time / duration, 0f, 1f) else 1f
                value = from + (to - from) * easing.transform(p)
                if (p >= 1f) {
                    mode = 0
                    return true
                }
            }
        }
        return false
    }

    private fun energy(x: Float, v: Float) = 0.5f * stiffness * x * x + 0.5f * v * v

    companion object {
        const val ENERGY_THRESHOLD = 6e-9f

        /** The stiffness Reanimated solves for so a spring of mass 1 settles in 1.5 × the duration. */
        fun stiffnessFor(x0: Float, v0: Float, durationMs: Float, zeta: Float): Float {
            if (x0 == 0f && v0 == 0f) return 0f
            val settling = durationMs * 1.5 / 1000.0
            fun diff(k: Double): Double {
                val omega = sqrt(k) * zeta
                val a = x0 + (v0 + x0 * omega) * settling
                val e = exp(-omega * settling)
                val xt = a * e
                val vt = a * e * -omega + (v0 + x0 * omega) * e
                val e0 = 0.5 * k * x0 * x0 + 0.5 * v0 * v0
                val et = 0.5 * k * xt * xt + 0.5 * vt * vt
                return et / e0 - ENERGY_THRESHOLD
            }
            var lo = Math.ulp(1.0)
            var hi = 8e3
            val direction = if (diff(hi) >= diff(lo)) 1 else -1
            var current = (lo + hi) / 2
            var left = 100
            while (abs(diff(current)) > ENERGY_THRESHOLD * 1e-3 && left > 0) {
                left -= 1
                if (diff(current) * direction < 0) lo = current else hi = current
                current = (lo + hi) / 2
            }
            return current.toFloat()
        }
    }
}

private enum class Mode { None, Undecided, Handle, Scroll, Lift }

/** How one page is drawn this frame, in dp. */
private class CardState {
    var alpha = 1f
    var tx = 0f
    var ty = 0f
    var scale = 1f
    var turn = 0f
    var radius = 0f
    var dim = 0f
    var chip = 0f
}

/** The deck's state, stepped once a frame like the RN frame callback, and the gestures that move it. */
private class DeckEngine(val count: Int) {
    // 0 = the active page fills the screen, 1 = the deck is open.
    val open = DeckValue(0f)
    // Which slot of the deck is in front, continuously.
    val scroll = DeckValue(0f)
    var active = 0
    val dragX = DeckValue(0f)
    var liftId = -1
    val lift = DeckValue(0f)
    // The page flicked away, removed once its flight ends.
    var leaving = -1
    // Page ids in deck order; pages not in it have been closed. Compose reads it for the labels.
    var order: List<Int> = List(count) { it }
    var orderVersion by mutableIntStateOf(0)
    val slots = Array(count) { DeckValue(it.toFloat()) }
    val presence = Array(count) { DeckValue(1f) }
    var mode = Mode.None
    var startX = 0f
    var startY = 0f
    var startOpen = 0f
    var startScroll = 0f
    var lastRound = 0
    // autoPlay: the loop's clock, a pause after a touch, and the virtual finger.
    var clock = 0f
    var looping = false
    var hold = 0f
    var fingerX = 0f
    var fingerY = 0f
    val fingerAlpha = DeckValue(0f)
    var fingerPress = 0f
    var reduced = false
    var width = 0f
    var height = 0f
    var bottomInset = 0f
    val cards = Array(count) { CardState() }
    // Bumped once a frame; the drawing reads it, so it is drawn again without composing.
    var tick by mutableLongStateOf(0L)

    // Feedback, set by the composable.
    var light: () -> Unit = {}
    var medium: () -> Unit = {}
    var selection: () -> Unit = {}
    var opened: (Int) -> Unit = {}

    fun animate(value: DeckValue, target: Float, spec: SpringSpec, speed: Float = 0f) {
        if (reduced) value.timing(target, REDUCED_MS, EaseOutStrong) else value.spring(target, spec, speed)
    }

    /** The deck order changed: a page that stays slides to its new slot; one that comes back fades in at it. */
    fun reorder(next: List<Int>) {
        val before = order
        order = next
        orderVersion += 1
        for (id in 0 until count) {
            val was = before.indexOf(id)
            val now = next.indexOf(id)
            if (now >= 0 && was >= 0) {
                if (now != was) animate(slots[id], now.toFloat(), SlotSpring)
            } else if (now >= 0) {
                slots[id].set(now.toFloat())
                presence[id].set(0f)
                presence[id].timing(1f, 420f, EaseOutStrong)
            } else {
                presence[id].set(0f)
            }
        }
    }

    fun restoreAll() {
        // The flicked-away page is still parked off screen; it comes back from its slot instead.
        liftId = -1
        leaving = -1
        lift.set(0f)
        reorder(List(count) { it })
    }

    fun openDeck(speed: Float, user: Boolean) {
        animate(open, 1f, OpenSpring, speed)
        animate(dragX, 0f, BackSpring)
        if (user) light()
    }

    fun closeDeck(speed: Float) {
        animate(open, 0f, BackSpring, speed)
        animate(dragX, 0f, BackSpring)
    }

    /** Zooms the page in [slot] back to the full screen. */
    fun openSlot(slot: Int, user: Boolean) {
        if (order.isEmpty()) return
        val k = slot.coerceIn(0, order.size - 1)
        active = order[k]
        animate(scroll, k.toFloat(), ZoomSpring)
        animate(open, 0f, ZoomSpring)
        if (user) {
            light()
            opened(order[k])
        }
    }

    fun scrollTo(slot: Float, speed: Float) {
        animate(scroll, clamp(slot, 0f, (order.size - 1).toFloat()), ScrollSpring, speed)
    }

    fun touched() {
        hold = USER_HOLD
        fingerAlpha.timing(0f, 120f)
    }

    fun frame(dt: Float) {
        script(dt)
        open.step(dt)
        scroll.step(dt)
        dragX.step(dt)
        if (lift.step(dt) && leaving >= 0 && leaving == liftId) {
            // The page has flown off: it leaves the deck and the rest close the gap.
            val id = leaving
            leaving = -1
            reorder(order.filter { it != id })
        }
        fingerAlpha.step(dt)
        for (i in 0 until count) {
            slots[i].step(dt)
            presence[i].step(dt)
        }
        // A selection tick each time another page reaches the front under the user's finger.
        val round = jsRound(scroll.value).toInt()
        if (round != lastRound && mode == Mode.Scroll) selection()
        lastRound = round
        place()
        tick += 1
    }

    // Driven from the frame clock so a recording of one loop joins up cleanly.
    private fun script(dt: Float) {
        if (mode != Mode.None) hold = USER_HOLD
        if (hold > 0f) {
            hold -= dt
            if (hold <= 0f && looping) clock = -LEAD
            return
        }
        if (!looping || width <= 0f) return
        val w = width
        val h = height
        val before = clock
        var t = before + dt
        if (t >= LOOP) t -= LOOP
        clock = t

        // The lead-in after a touch: everything put back, then the journal opened from the deck.
        if (before < 0f) {
            if (crossed(before, t, -LEAD + 0.001f)) {
                if (order.size < count) restoreAll()
                if (active != 0 || open.value > 0.02f) openDeck(0f, false) else clock = 0f
            }
            if (crossed(before, t, -1.1f)) openSlot(0, false)
            return
        }

        val cardWidth = w * DECK_SCALE
        val step = cardWidth * SCROLL_STEP
        val handleY = h - bottomInset - HANDLE_BOTTOM - HANDLE_HEIGHT / 2f
        val deckCY = h / 2f + DECK_Y
        var alpha = 0f
        for (st in Strokes) {
            val u = (t - st.t0) / (st.t1 - st.t0)
            // The finger shows a little before it moves and stays a moment after it lifts.
            val near = clamp(min((t - st.t0 + 0.3f) / 0.2f, (st.t1 + 0.35f - t) / 0.2f), 0f, 1f)
            if (near > alpha) alpha = near
            if (t < st.t0 - 0.3f || t > st.t1 + 0.35f) continue
            val e = EaseInOut.transform(clamp(u, 0f, 1f))
            when (st.kind) {
                StrokeKind.Handle -> {
                    fingerX = w / 2f + 26f * e
                    fingerY = handleY - e * 0.5f * OPEN_DIST * h
                    if (u in 0f..1f) {
                        open.set(0.5f * e)
                        dragX.set(26f * e * FOLLOW_X)
                    }
                    if (crossed(before, t, st.t1)) openDeck(2.2f, false)
                }
                StrokeKind.Flick -> {
                    // The deck follows the finger for part of the way; the release throws it the rest.
                    val travel = -(st.to - st.from) * step * 0.7f
                    fingerX = w / 2f - travel / 2f + travel * e
                    fingerY = deckCY + 40f
                    if (u in 0f..1f) scroll.set(st.from - (travel * e) / step)
                    if (crossed(before, t, st.t1)) scrollTo(st.to, sign(st.to - st.from) * 2.2f)
                }
                StrokeKind.Tap -> {
                    fingerX = w / 2f + deckX(st.to - scroll.value, cardWidth) - cardWidth * 0.08f
                    fingerY = deckCY + 30f
                    fingerPress = clamp(1f - abs(t - (st.t0 + st.t1) / 2f) / 0.25f, 0f, 1f)
                    if (crossed(before, t, st.t1)) openSlot(st.to.toInt(), false)
                }
            }
        }
        fingerAlpha.set(alpha)
    }

    /** Where every page is drawn this frame. */
    private fun place() {
        val o = open.value
        val shown = clamp(o, 0f, 1f)
        val cardWidth = width * DECK_SCALE
        for (id in 0 until count) {
            val c = cards[id]
            val p = slots[id].value - scroll.value
            val dx = deckX(p, cardWidth)
            val ds = deckScale(p)
            val turn = deckTurn(p)
            val lifted = if (liftId == id) lift.value else 0f
            var alpha = 1f
            if (active == id) {
                // The open page shrinks from the full screen into its place in the deck, following the finger.
                c.tx = dx * o + dragX.value
                c.ty = DECK_Y * o
                c.scale = 1f + (ds - 1f) * o
                c.turn = turn * o
            } else {
                // The other pages wait off to their side and slide in as the deck opens.
                c.tx = dx + (1f - shown) * width * 1.15f * (if (p >= 0f) 1f else -1f)
                c.ty = DECK_Y
                c.scale = ds
                c.turn = turn
                alpha = min(1f, shown * 1.6f)
            }
            val presenceNow = presence[id].value
            c.alpha = alpha * presenceNow * (1f - clamp(-lifted / (height * 0.5f), 0f, 1f))
            c.ty += lifted
            c.scale *= 0.92f + 0.08f * presenceNow
            c.radius = when {
                o <= 0f -> 0f
                o < 0.08f -> RADIUS_SHRINKING * o / 0.08f
                o < 1f -> RADIUS_SHRINKING + (RADIUS_DECK - RADIUS_SHRINKING) * (o - 0.08f) / 0.92f
                else -> RADIUS_DECK
            }
            c.dim = clamp(-p * DIM_PER_PAGE, 0f, DIM_MAX) * shown
            c.chip = clamp((o - 0.55f) / 0.45f, 0f, 1f)
        }
    }

    // Gestures: a pan after 6 dp of travel, or a tap lifted within 350 ms that never travelled that far.

    fun panStart(x: Float, y: Float) {
        touched()
        startX = x
        startY = y
        mode = Mode.Undecided
    }

    fun panUpdate(tx: Float, ty: Float) {
        val w = width
        val h = height
        val n = order.size
        if (mode == Mode.Undecided) {
            val vertical = abs(ty) > abs(tx)
            mode = if (open.value < 0.5f) {
                // Closed: only the handle at the bottom answers, and only to a pull upwards.
                if (startY > h - bottomInset - HANDLE_ZONE && vertical && ty < 0f) {
                    startOpen = open.value
                    Mode.Handle
                } else {
                    Mode.None
                }
            } else if (vertical && ty < 0f) {
                val k = hitSlot(startX, startY, n, scroll.value, w, h)
                if (k >= 0) {
                    liftId = order[k]
                    leaving = -1
                    lift.set(0f)
                    Mode.Lift
                } else {
                    Mode.None
                }
            } else if (!vertical) {
                startScroll = scroll.value
                Mode.Scroll
            } else {
                Mode.None
            }
        }
        when (mode) {
            Mode.Handle -> {
                open.set(rubber(startOpen - ty / (h * OPEN_DIST), 0f, 1f, 0.12f))
                dragX.set(tx * FOLLOW_X)
            }
            Mode.Scroll -> scroll.set(rubber(startScroll - tx / (w * DECK_SCALE * SCROLL_STEP), 0f, (n - 1).toFloat(), 0.35f))
            // The last page left cannot be closed: it only gives a little.
            Mode.Lift -> lift.set(if (ty < 0f && n > 1) ty else rubber(ty, 0f, 0f, 40f))
            else -> Unit
        }
    }

    fun panEnd(vx: Float, vy: Float) {
        val w = width
        val h = height
        val m = mode
        mode = Mode.None
        when (m) {
            Mode.Handle -> {
                val v = -vy / (h * OPEN_DIST)
                if (open.value + v * THROW > OPEN_AT) openDeck(v, true) else closeDeck(v)
            }
            Mode.Scroll -> {
                val v = -vx / (w * DECK_SCALE * SCROLL_STEP)
                scrollTo(jsRound(scroll.value + v * SCROLL_THROW), v)
            }
            Mode.Lift -> {
                val n = order.size
                if (n > 1 && (vy < -DISMISS_SPEED || lift.value < -h * DECK_SCALE * DISMISS_AT)) {
                    medium()
                    leaving = liftId
                    lift.timing(-h * 0.8f, DISMISS_MS, EaseOutStrong)
                    // The front slot stays in range once the page is gone.
                    if (scroll.value > n - 2) scrollTo((n - 2).toFloat(), 0f)
                } else {
                    animate(lift, 0f, BackSpring, vy)
                }
            }
            else -> Unit
        }
    }

    fun tap(x: Float, y: Float) {
        touched()
        if (open.value < 0.85f) return
        val n = order.size
        // The strip under the deck reopens the pages that were closed.
        if (n < count && y > height - bottomInset - 64f) {
            restoreAll()
            return
        }
        val k = hitSlot(x, y, n, scroll.value, width, height)
        openSlot(if (k >= 0) k else jsRound(scroll.value).toInt(), true)
    }
}

/**
 * React Native's `perspective, translateX, translateY, scale, rotateY` about the middle of a view
 * [width] × [height], as a matrix for a canvas in pixels.
 */
private fun projection(matrix: Matrix, values: FloatArray, c: CardState, width: Float, height: Float, density: Float) {
    val rad = c.turn * PI.toFloat() / 180f
    val k = sin(rad) / (PERSPECTIVE * density)
    val cs = cos(rad)
    val hw = width / 2f
    val hh = height / 2f
    val s = c.scale
    val tx = c.tx * density
    val ty = c.ty * density
    // Column vectors: x' = (a·u + b) / (k·u + w0), y' = (c·u + s·v + d) / (k·u + w0).
    val m11 = hw * k + s * cs
    values[0] = m11
    values[1] = 0f
    values[2] = hw + tx - hw * m11
    values[3] = hh * k
    values[4] = s
    values[5] = hh + ty - k * hw * hh - s * hh
    values[6] = k
    values[7] = 0f
    values[8] = 1f - k * hw
    matrix.setValues(values)
}

/**
 * A photo journal with four pages and an app switcher of its own. Pulling up the handle at the
 * bottom shrinks the page under the finger into a card while the other pages slide in beside
 * it, fanned out as a deck in perspective with a title chip over each. The deck scrolls
 * sideways with momentum; tapping a page zooms it back to the full screen, and flicking one up
 * closes it while the rest close the gap.
 *
 * @param autoPlay Opens the deck, scrolls it, opens another page and goes back, on a loop.
 * @param onOpenPage Called with a page's title when it is opened from the deck.
 */
@Composable
fun SwitcherDeckNav(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    onOpenPage: ((String) -> Unit)? = null,
) {
    val pages = SwitcherDeckPages
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val density = LocalDensity.current
    val onOpen by rememberUpdatedState(onOpenPage)
    val engine = remember { DeckEngine(pages.size) }

    val insets = WindowInsets.safeDrawing
    val top = insets.getTop(density) / density.density
    val bottom = insets.getBottom(density) / density.density

    engine.reduced = reduced
    engine.bottomInset = bottom
    engine.light = { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
    engine.medium = { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
    engine.selection = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    engine.opened = { id -> onOpen?.invoke(pages[id].title) }

    LaunchedEffect(engine, auto) {
        engine.looping = auto
        engine.clock = 0f
        engine.hold = 0f
    }

    LaunchedEffect(engine) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 1f / 60f else min((now - last) / 1e9f, 0.064f)
                last = now
                if (dt > 0f) engine.frame(dt)
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color(0xFF0D0C0B))
            .semantics {
                contentDescription = "Photo journal with ${pages.size} pages. Swipe up from the handle at the bottom to show every page as a deck; tap a page to open it, flick it up to close it."
            }
            .pointerInput(engine) {
                val tracker = VelocityTracker()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    tracker.resetTracking()
                    tracker.addPointerInputChange(down)
                    var panning = false
                    var released = false
                    while (!released) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPointerInputChange(change)
                        val dx = (change.position.x - start.x) / density.density
                        val dy = (change.position.y - start.y) / density.density
                        if (!change.pressed) {
                            if (panning) {
                                val v = tracker.calculateVelocity()
                                engine.panEnd(v.x / density.density, v.y / density.density)
                            } else if (change.uptimeMillis - down.uptimeMillis <= TAP_MS) {
                                engine.tap(change.position.x / density.density, change.position.y / density.density)
                            }
                            released = true
                            continue
                        }
                        if (!panning && hypot(dx, dy) >= PAN_SLOP) {
                            panning = true
                            engine.panStart(start.x / density.density, start.y / density.density)
                        }
                        if (panning) {
                            change.consume()
                            engine.panUpdate(dx, dy)
                        }
                    }
                    // A finger lost to the system still lets go.
                    if (panning && !released) engine.panEnd(0f, 0f)
                    if (!panning) engine.mode = Mode.None
                }
            },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        engine.width = width
        engine.height = height
        // Read so the labels and the drawing order follow the deck.
        @Suppress("UNUSED_VARIABLE") val version = engine.orderVersion
        val order = engine.order
        val rest = (0 until pages.size).filter { it !in order }
        val drawOrder = rest + order

        Backdrop(engine, top, bottom, order.size, pages.size - order.size)

        for (id in pages.indices) {
            DeckCard(engine, id, pages[id], drawOrder.indexOf(id).toFloat(), width, height, top, bottom)
        }

        Box(
            Modifier
                .zIndex(100f)
                .size(46.dp)
                .graphicsLayer {
                    engine.tick
                    translationX = (engine.fingerX - 23f).dp.toPx()
                    translationY = (engine.fingerY - 23f).dp.toPx()
                    val s = 1f - 0.18f * engine.fingerPress
                    scaleX = s
                    scaleY = s
                    alpha = engine.fingerAlpha.value * 0.9f
                }
                .background(Color.White.copy(alpha = 0.35f), CircleShape)
                .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape),
        )
    }
}

private val Cream = Color(0xFFF4EFE6)

/** The warm dark backdrop of the deck, its title and the line under it. */
@Composable
private fun Backdrop(engine: DeckEngine, top: Float, bottom: Float, open: Int, closed: Int) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                engine.tick
                alpha = clamp(engine.open.value, 0f, 1f)
            }
            .background(Brush.verticalGradient(listOf(Color(0xFF23201C), Color(0xFF0D0C0B)))),
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = (top + 14f).dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(
                "SLOW DEPARTURES",
                style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp, color = Color(0xFFC98A64)),
            )
            BasicText(
                "$open open ${if (open == 1) "page" else "pages"}",
                style = TextStyle(fontFamily = FontFamily.Serif, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, color = Cream),
            )
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = (bottom + 18f).dp)) {
            if (closed > 0) {
                Box(
                    Modifier
                        .height(36.dp)
                        .background(Cream.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                        .border(with(LocalDensity.current) { 1f.toDp() }, Cream.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        "Reopen $closed closed ${if (closed == 1) "page" else "pages"}",
                        style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Cream),
                    )
                }
            } else {
                BasicText(
                    "Tap a page to open it · Flick it up to close",
                    style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Cream.copy(alpha = 0.5f)),
                )
            }
        }
    }
}

private class CardPaints {
    val matrix = Matrix()
    val values = FloatArray(9)
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    var shadowFor = Float.NaN
}

/** One page of the app: full screen while it is the open one, a turned card in the deck otherwise. */
@Composable
private fun DeckCard(engine: DeckEngine, id: Int, page: SwitcherDeckPage, order: Float, width: Float, height: Float, top: Float, bottom: Float) {
    val paints = remember { CardPaints() }
    val unit = 1f / DECK_SCALE
    Box(
        Modifier
            .zIndex(order)
            .fillMaxSize()
            // The page fades as one piece, so its shadow never shows through it.
            .graphicsLayer {
                engine.tick
                alpha = engine.cards[id].alpha
            }
            .drawWithContent {
                engine.tick
                val c = engine.cards[id]
                if (c.alpha <= 0.002f) return@drawWithContent
                projection(paints.matrix, paints.values, c, size.width, size.height, density)
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    native.save()
                    native.concat(paints.matrix)
                    if (c.radius > 0f) {
                        // The shadow under the page: 0 24 60 at 55% black.
                        if (paints.shadowFor != density) {
                            paints.shadow.color = page.tint.toArgb()
                            paints.shadow.setShadowLayer(sigmaToRadius(30f * density), 0f, 24f * density, Color.Black.copy(alpha = 0.55f).toArgb())
                            paints.shadowFor = density
                        }
                        val r = c.radius * density
                        native.drawRoundRect(0f, 0f, size.width, size.height, r, r, paints.shadow)
                    }
                    drawContent()
                    native.restore()
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    engine.tick
                    shape = RoundedCornerShape(engine.cards[id].radius.dp)
                    clip = true
                }
                .background(page.tint),
        ) {
            SwitcherDeckPageContent(id, width, height, top, bottom)
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        engine.tick
                        alpha = engine.cards[id].dim
                    }
                    .background(Color(0xFF0D0C0B)),
            )
        }
        Row(
            Modifier
                .offset(y = (-(CHIP_HEIGHT + CHIP_GAP) * unit).dp)
                .wrapContentSize(unbounded = true, align = Alignment.TopStart)
                .graphicsLayer {
                    engine.tick
                    val shown = engine.cards[id].chip
                    alpha = shown
                    translationY = ((1f - shown) * 30f).dp.toPx()
                }
                .height((CHIP_HEIGHT * unit).dp)
                .background(Color(0x1FFFFAF0), RoundedCornerShape((CHIP_HEIGHT / 2f * unit).dp))
                .border(1.5.dp, Color(0x2EFFFAF0), RoundedCornerShape((CHIP_HEIGHT / 2f * unit).dp))
                .padding(horizontal = (11f * unit).dp),
            horizontalArrangement = Arrangement.spacedBy((7f * unit).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size((14f * unit).dp)
                    .background(page.tint, RoundedCornerShape((4f * unit).dp))
                    .border((1.2f * unit).dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape((4f * unit).dp)),
            )
            BasicText(
                page.title,
                style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = (13f * unit).sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp, color = Cream),
            )
        }
    }
}
