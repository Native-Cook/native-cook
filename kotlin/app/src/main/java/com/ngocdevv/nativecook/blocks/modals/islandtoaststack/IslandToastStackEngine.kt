package com.ngocdevv.nativecook.blocks.modals.islandtoaststack

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

internal enum class Kind { Call, Message, Upload, Timer }

// ── Island geometry (dp) ─────────────────────────────────────────────────────────────────────────
// The pill is 126 × 37 dp, centred just under the status bar, and hidden at rest.
internal const val ISLAND_W = 126f
internal const val ISLAND_H = 37f
private const val SIDE = 10f // gap between an open toast and the screen's edges
internal const val TOAST_H = 99f // the island's own band (beside the camera) plus a row under it
internal const val BIG_H = 228f
private const val MAX_RADIUS = 44f // continuous corners, never more than half the height
private const val SMOOTHING = 0.6f // corner smoothing of the continuous corners (0 = circular)

// Island springs: a lively one going out, a calmer one coming home. Width leads height a little.
private const val OPEN_W_MS = 520.0
private const val OPEN_W_RATIO = 0.7
private const val OPEN_H_MS = 600.0
private const val OPEN_H_RATIO = 0.74
private const val CLOSE_MS = 500.0
private const val CLOSE_RATIO = 0.86
private const val CONTENT_DELAY = 210.0 // ms the shape gets to settle before its content fades in
private const val CONTENT_IN = 240.0
private const val CONTENT_OUT = 110.0
private const val SHOW_TIME = 1.9 // s a toast stays open before it drops into the stack

// Pulling the island down opens it into the big timer card.
private const val BIG_DRAG = 170f
private const val BIG_COMMIT = 0.42f
private const val BIG_FLICK = 600f // dp/s
// The island sits in the system's top-edge swipe (the notification shade), which can take a pull that
// starts on it. A pull also starts anywhere in this band under the closed island, the closed stack's
// front card included, where the system leaves it alone.
internal const val PULL_REACH = 108f // dp below the closed island
internal const val PULL_START = 10f // dp down before a pull from the band takes over

// ── Stack ────────────────────────────────────────────────────────────────────────────────────────
// Cards are the system's notifications: an icon, a title with the time beside it, and a line or two.
internal const val CARD_H = 80f
private const val CARD_RADIUS = 22f
private const val CARD_GAP = 8f
private const val DECK_GAP = 12f // between the island and the stack
private const val PEEK = 9f // dp each card behind shows under the one in front
private const val PEEK_INSET = 11f // dp each card behind is narrower on either side
private const val HEADER = 46f // room for the header when the stack is open
private const val ARRIVE_MS = 680.0
private const val ARRIVE_RATIO = 0.76
private const val SLOT_MS = 480.0
private const val SLOT_RATIO = 0.84
private const val EXPAND_MS = 520.0
private const val EXPAND_RATIO = 0.82
// Overlapping, concave fillets round off the corners where the island meets a card; apart, a neck
// joins them, thins and snaps at BRIDGE_BREAK dp.
private const val FILLET = 9f
private const val BRIDGE_BREAK = 24f
private const val BRIDGE_FLARE = 0.35f
// Clearing: cards rise back into the island one after another and it gulps each one.
private const val ABSORB_MS = 460.0
private const val ABSORB_STAGGER = 0.12
private const val GULP_MS = 420.0
private const val GULP_RATIO = 0.45
private const val GULP_VELOCITY = 320f
private const val DISMISS_DISTANCE = 0.32f // share of the width
private const val DISMISS_FLICK = 900f // dp/s

internal const val UPLOAD_TIME = 9.0
internal const val TIMER_SECONDS = 25.0 * 60.0

// autoPlay (s): four toasts, the stack opened and trimmed, the island pulled open, everything drawn
// back into the island, at rest again before the loop ends.
private const val LOOP = 16.0
private const val USER_HOLD = 4.0 // s a touch pauses the script
private const val REST = 1.2 // s to settle back to rest before the script starts over

private val EaseOutCurve = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private val EaseInOutCurve = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

/** The easing curves the values below use. */
internal enum class Ease {
    Linear, QuadInOut, Out, InOut;

    fun at(t: Double): Double = when (this) {
        Linear -> t
        // Reanimated's default for withTiming.
        QuadInOut -> if (t < 0.5) 2 * t * t else 1 - (-2 * t + 2).pow(2) / 2
        Out -> EaseOutCurve.transform(t.toFloat()).toDouble()
        InOut -> EaseInOutCurve.transform(t.toFloat()).toDouble()
    }
}

/**
 * A number moved by springs and timings the way Reanimated moves a shared value: a new spring
 * takes over the speed of a spring still running, a delayed animation lets the one before it run
 * on during the delay, and a spring or timing restarted towards the same target carries on.
 */
internal class Animated(initial: Float) {
    var value = initial.toDouble()
        private set
    val float get() = value.toFloat()

    private sealed class Planned {
        class Spring(val target: Double, val durationMs: Double, val ratio: Double, val velocity: Double, val clamp: Boolean) : Planned()
        class Timing(val target: Double, val duration: Double, val ease: Ease) : Planned()
    }

    private sealed class Running {
        class Spring(val spring: SpringMotion) : Running()
        class Timing(val target: Double, val from: Double, var elapsed: Double, val duration: Double, val ease: Ease) : Running()
        class Delay(var remaining: Double, val next: Planned, var previous: Running?) : Running()
    }

    private var running: Running? = null
    private var onDone: (() -> Unit)? = null

    val isAnimating get() = running != null

    /** Puts the value somewhere at once, stopping whatever was moving it. */
    fun set(next: Float) {
        value = next.toDouble()
        running = null
        onDone = null
    }

    fun spring(target: Float, durationMs: Double, ratio: Double, velocity: Float = 0f, clamp: Boolean = false, done: (() -> Unit)? = null) {
        running = start(Planned.Spring(target.toDouble(), durationMs, ratio, velocity.toDouble(), clamp), running)
        onDone = done
    }

    fun timing(target: Float, durationMs: Double, ease: Ease = Ease.QuadInOut, done: (() -> Unit)? = null) {
        running = start(Planned.Timing(target.toDouble(), durationMs / 1000.0, ease), running)
        onDone = done
    }

    /** A timing that starts after `delayMs`; until then whatever moved the value keeps moving it. */
    fun delayedTiming(delayMs: Double, target: Float, durationMs: Double, ease: Ease = Ease.QuadInOut) {
        running = Running.Delay(delayMs / 1000.0, Planned.Timing(target.toDouble(), durationMs / 1000.0, ease), running)
        onDone = null
    }

    private fun start(plan: Planned, previous: Running?): Running = when (plan) {
        is Planned.Spring -> {
            val old = (previous as? Running.Spring)?.spring
            if (old != null && old.target == plan.target && old.durationMs == plan.durationMs && old.ratio == plan.ratio) {
                previous as Running
            } else {
                // A spring still running hands its speed on; anything else has none to give.
                val carried = when {
                    previous == null -> plan.velocity
                    old != null -> old.velocity + plan.velocity
                    else -> 0.0
                }
                Running.Spring(SpringMotion(value, plan.target, carried, plan.velocity, plan.durationMs, plan.ratio, plan.clamp))
            }
        }
        is Planned.Timing -> {
            val old = previous as? Running.Timing
            if (old != null && old.target == plan.target) {
                Running.Timing(plan.target, old.from, old.elapsed, plan.duration, plan.ease)
            } else {
                Running.Timing(plan.target, value, 0.0, plan.duration, plan.ease)
            }
        }
    }

    fun step(dt: Double) {
        val current = running ?: return
        val (next, finished) = advance(current, dt)
        running = if (finished) null else next
        if (finished) {
            val done = onDone
            onDone = null
            done?.invoke()
        }
    }

    private fun advance(r: Running, dt: Double): Pair<Running?, Boolean> = when (r) {
        is Running.Spring -> {
            val done = r.spring.step(dt)
            value = r.spring.value
            r to done
        }
        is Running.Timing -> {
            r.elapsed += dt
            if (r.elapsed >= r.duration || r.duration <= 0) {
                value = r.target
                null to true
            } else {
                value = r.from + (r.target - r.from) * r.ease.at(r.elapsed / r.duration)
                r to false
            }
        }
        is Running.Delay -> {
            r.remaining -= dt
            if (r.remaining <= 0) {
                start(r.next, r.previous) to false
            } else {
                r.previous?.let { p ->
                    val (moved, done) = advance(p, dt)
                    r.previous = if (done) null else moved
                }
                r to false
            }
        }
    }
}

/**
 * Reanimated's `withSpring({ duration, dampingRatio })`: the stiffness is solved so the spring's
 * energy falls to the threshold after 1.5 × the duration, and each frame follows the exact solution.
 */
internal class SpringMotion(
    start: Double,
    val target: Double,
    speed: Double,
    configured: Double,
    val durationMs: Double,
    val ratio: Double,
    private val clamp: Boolean,
) {
    var value = start
        private set
    var velocity: Double
        private set
    private val omega0: Double
    private val omega1: Double
    private val stiffness: Double
    private val initialEnergy: Double
    private val startOffset = start - target

    init {
        var v = speed
        // Velocity that would carry the first frame away from the target is dropped.
        if ((target > start && v < 0) || (target < start && v > 0)) v = 0.0
        velocity = v
        val x0 = startOffset
        val zeta = ratio
        val settling = durationMs / 1000.0 * 1.5
        fun energy(x: Double, v: Double, k: Double) = 0.5 * k * x * x + 0.5 * v * v
        fun difference(k: Double): Double {
            val w = sqrt(k) * zeta
            val xt = (x0 + (v + x0 * w) * settling) * exp(-w * settling)
            val vt = (x0 + (v + x0 * w) * settling) * exp(-w * settling) * -w + (v + x0 * w) * exp(-w * settling)
            return energy(xt, vt, k) / energy(x0, v, k) - THRESHOLD
        }
        // Bisection, as Reanimated solves it (its mass of 4 only scales the stiffness it finds).
        var low = Math.ulp(1.0)
        var high = 2e3
        val rising = difference(high) >= difference(low)
        var k = (low + high) / 2
        var left = 100
        while (abs(difference(k)) > THRESHOLD * 1e-3 && left > 0) {
            left -= 1
            if ((difference(k) < 0) == rising) low = k else high = k
            k = (low + high) / 2
        }
        if (!k.isFinite()) k = 170.0
        stiffness = k
        omega0 = sqrt(k)
        omega1 = if (zeta < 1) omega0 * sqrt(1 - zeta * zeta) else 0.0
        // Reanimated measures the starting energy with the configured speed alone.
        initialEnergy = energy(x0, configured, k)
    }

    fun step(dt: Double): Boolean {
        if (initialEnergy == 0.0) {
            value = target
            return true
        }
        val x0 = value - target
        val v0 = velocity
        val zeta = ratio
        if (zeta < 1) {
            val sin1 = sin(omega1 * dt)
            val cos1 = cos(omega1 * dt)
            val envelope = exp(-zeta * omega0 * dt)
            val frag = envelope * (sin1 * ((v0 + zeta * omega0 * x0) / omega1) + x0 * cos1)
            value = target + frag
            velocity = -zeta * omega0 * frag + envelope * (cos1 * (v0 + zeta * omega0 * x0) - omega1 * x0 * sin1)
        } else {
            val envelope = exp(-omega0 * dt)
            value = target + envelope * (x0 + (v0 + omega0 * x0) * dt)
            velocity = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * dt) + envelope * (v0 + omega0 * x0)
        }
        if (clamp) {
            // overshootClamping: done as soon as it passes the target.
            val lower = if (startOffset >= 0) target else target + startOffset
            val upper = lower + abs(startOffset)
            if (value < lower || value > upper) {
                value = target
                velocity = 0.0
                return true
            }
        }
        val now = 0.5 * stiffness * (target - value) * (target - value) + 0.5 * velocity * velocity
        if (now / initialEnergy <= THRESHOLD) {
            value = target
            velocity = 0.0
            return true
        }
        return false
    }

    private companion object {
        const val THRESHOLD = 6e-9
    }
}

// ── Shapes ───────────────────────────────────────────────────────────────────────────────────────

internal fun clamp01(x: Float) = if (x.isNaN()) 0f else x.coerceIn(0f, 1f)
internal fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
internal fun radiusFor(w: Float, h: Float) = max(0f, min(MAX_RADIUS, min(h / 2f, w / 2f)))

private val SquircleCorner = FloatArray(18)

/**
 * A rounded rectangle with continuous ("squircle") corners, as iOS draws them: each corner eases
 * from the straight edge into a shorter circular arc over (1 + smoothing) × radius. When the sides
 * are too short for that the smoothing gives way, down to a plain capsule. Written into [path].
 */
internal fun squircle(path: Path, x: Float, y: Float, w: Float, h: Float, radius: Float) {
    path.reset()
    val width = max(0f, w)
    val height = max(0f, h)
    if (!(x + y + width + height).isFinite()) return
    val budget = min(width, height) / 2f
    val r = max(0f, min(radius, budget))
    if (!(r > 0.5f)) {
        path.moveTo(x, y)
        path.lineTo(x + width, y)
        path.lineTo(x + width, y + height)
        path.lineTo(x, y + height)
        path.close()
        return
    }
    val smooth = max(0.0, min(SMOOTHING.toDouble(), budget / r - 1.0))
    val rr = r.toDouble()
    val p = min((1 + smooth) * rr, budget.toDouble())
    val arc = 90 * (1 - smooth) * PI / 180
    val arcLen = sin(arc / 2) * rr * sqrt(2.0)
    val alpha = (PI / 2 - arc) / 2
    val p34 = rr * tan(alpha / 2)
    val beta = PI / 4 * smooth
    val c = p34 * cos(beta)
    val d = c * tan(beta)
    val bb = (p - arcLen - c - d) / 3
    val a = 2 * bb
    val k = 4.0 / 3.0 * tan(arc / 4) * rr
    // One corner in its own frame: u along the edge arriving at the corner, v along the next edge.
    val pts = SquircleCorner
    val values = doubleArrayOf(
        a, 0.0, a + bb, 0.0, a + bb + c, d,
        a + bb + c + cos(beta) * k, d + sin(beta) * k,
        a + bb + c + arcLen - cos(beta + arc) * k, d + arcLen - sin(beta + arc) * k,
        a + bb + c + arcLen, d + arcLen,
        a + bb + c + arcLen + d, d + arcLen + c,
        a + bb + c + arcLen + d, d + arcLen + bb + c,
        p, p,
    )
    for (i in values.indices) pts[i] = values[i].toFloat()
    val pf = p.toFloat()
    path.moveTo(x + pf, y)
    // Corners clockwise from the top right: corner, u, v.
    for (corner in 0 until 4) {
        val cx: Float; val cy: Float; val ux: Float; val uy: Float; val vx: Float; val vy: Float
        when (corner) {
            0 -> { cx = x + width; cy = y; ux = 1f; uy = 0f; vx = 0f; vy = 1f }
            1 -> { cx = x + width; cy = y + height; ux = 0f; uy = 1f; vx = -1f; vy = 0f }
            2 -> { cx = x; cy = y + height; ux = -1f; uy = 0f; vx = 0f; vy = -1f }
            else -> { cx = x; cy = y; ux = 0f; uy = -1f; vx = 1f; vy = 0f }
        }
        val sx = cx - ux * pf
        val sy = cy - uy * pf
        fun px(j: Int) = sx + pts[j] * ux + pts[j + 1] * vx
        fun py(j: Int) = sy + pts[j] * uy + pts[j + 1] * vy
        path.lineTo(sx, sy)
        path.cubicTo(px(0), py(0), px(2), py(2), px(4), py(4))
        path.cubicTo(px(6), py(6), px(8), py(8), px(10), py(10))
        path.cubicTo(px(12), py(12), px(14), py(14), px(16), py(16))
    }
    path.close()
}

/** How far the island reaches either side of its centre at height [y]. */
private fun islandHalfAt(y: Float, top: Float, half: Float, h: Float, r: Float): Float {
    val k = min(max(y, top), top + h)
    val corner = if (k < top + r) top + r else if (k > top + h - r) top + h - r else k
    val dy = k - corner
    return half - r + sqrt(max(0f, r * r - dy * dy))
}

/**
 * The shape that joins the island to a card under it, written into [path]: concave fillets where
 * they overlap, a neck that leaves the island's bottom edge and lands on the card's top edge (both
 * tangentially) once they part, pinching in at the waist and snapping at BRIDGE_BREAK dp.
 */
internal fun bridgePath(path: Path, cx: Float, top: Float, ih: Float, hh: Float, ir: Float, ct: Float, ch: Float, chh: Float, cr: Float): Boolean {
    path.reset()
    val bottom = top + hh
    val gap = ct - bottom
    val cardFlat = ch - cr
    if (!(gap + cx + ih + ch).isFinite() || chh <= 0f) return false
    if (gap < 0f) {
        val depth = -gap
        val side = islandHalfAt(ct, top, ih, hh, ir)
        val f = FILLET * min(1f, min(depth / FILLET, max(0f, (cardFlat - side) / FILLET)))
        if (f < 0.5f || ct <= top) return false
        val upper = islandHalfAt(ct - f, top, ih, hh, ir)
        for (s in intArrayOf(-1, 1)) {
            val sf = s.toFloat()
            path.moveTo(cx + sf * upper, ct - f)
            path.cubicTo(cx + sf * upper, ct - f * 0.45f, cx + sf * (side + f * 0.45f), ct, cx + sf * (side + f), ct)
            path.lineTo(cx + sf * (side + f), ct + 2f)
            path.lineTo(cx + sf * (side - 2f), ct + 2f)
            path.lineTo(cx + sf * (upper - 2f), ct - f)
            path.close()
        }
        return true
    }
    if (gap >= BRIDGE_BREAK) return false
    val p = gap / BRIDGE_BREAK
    val anchor = max(0f, ih - ir)
    val land = min(cardFlat, anchor + FILLET + gap * BRIDGE_FLARE)
    val waist = anchor * (1f - p).pow(1.3f) * 0.82f
    if (anchor < 1f || land < 1f) return false
    val pull = max(0f, ((anchor + land) / 2f - waist) / 0.75f)
    path.moveTo(cx - anchor, bottom - 2f)
    path.lineTo(cx - anchor, bottom)
    path.cubicTo(cx - anchor + pull, bottom, cx - land + pull, ct, cx - land, ct)
    path.lineTo(cx - land, ct + 2f)
    path.lineTo(cx + land, ct + 2f)
    path.lineTo(cx + land, ct)
    path.cubicTo(cx + land - pull, ct, cx + anchor - pull, bottom, cx + anchor, bottom)
    path.lineTo(cx + anchor, bottom - 2f)
    path.close()
    return true
}

/** How far toward the background a card's black is mixed, [slot] places behind the front. */
private fun slotFade(slot: Float) = when {
    slot <= 1f -> slot * 0.2f
    slot <= 2f -> mix(0.2f, 0.42f, slot - 1f)
    else -> mix(0.42f, 1f, clamp01(slot - 2f))
}

/** A card rectangle with its corner radius and how far its colour is mixed toward the background. */
internal class CardRect(var x: Float = 0f, var y: Float = 0f, var w: Float = 0f, var h: Float = 0f, var r: Float = 0f, var fade: Float = 0f) {
    fun set(x: Float, y: Float, w: Float, h: Float, r: Float, fade: Float): CardRect {
        this.x = x; this.y = y; this.w = w; this.h = h; this.r = r; this.fade = fade
        return this
    }

    fun mixTo(x: Float, y: Float, w: Float, h: Float, r: Float, fade: Float, t: Float, fadeT: Float = t): CardRect = set(
        mix(this.x, x, t), mix(this.y, y, t), mix(this.w, w, t), mix(this.h, h, t), mix(this.r, r, t), mix(this.fade, fade, fadeT),
    )
}

// ── Engine ───────────────────────────────────────────────────────────────────────────────────────

/**
 * One toast's motion: whether its card is drawn, its place in the stack, how far it has come down
 * from the island, a sideways throw, how far it has gone back up, and the island's content.
 */
internal class Motion {
    var vis = 0f
    val slot = Animated(0f)
    val arrive = Animated(1f)
    val tx = Animated(0f)
    val absorb = Animated(0f)
    val content = Animated(0f)
    val values = listOf(slot, arrive, tx, absorb, content)
}

/** The controller: every action the script and the gestures share, stepped once a frame. */
internal class IslandEngine {
    /** Bumped every frame; the scene reads it, so it is laid out and drawn again each time. */
    val frame = mutableLongStateOf(0L)

    // Layout, refreshed from the composable.
    var width = 0f
        private set
    var toastW = ISLAND_W
        private set
    var cardW = ISLAND_W
        private set
    var islandTop = 11f
        private set
    /** Where the pill's centre sits at rest: the middle of the screen. */
    var restX = 0f
        private set
    val deckTop get() = islandTop + ISLAND_H + DECK_GAP
    private var reduced = false

    // What renders.
    var now = 0.0
        private set
    var stack: List<Kind> = emptyList()
        private set
    var presenting: Kind? = null
        private set
    var bigMounted = false
        private set
    var expandedOpen = false
        private set
    var timerStart: Double? = null
        private set
    var uploadStart: Double? = null
        private set

    val islandW = Animated(ISLAND_W)
    val islandH = Animated(ISLAND_H)
    val bigOpacity = Animated(0f)
    val expanded = Animated(0f)
    val upload = Animated(0f)
    val motion = Kind.entries.associateWith { Motion() }

    var onLight: () -> Unit = {}
    var onMedium: () -> Unit = {}

    private var bigOpen = false
    private var clearing = false
    private var dragging = false
    private var dragFrom = 0f
    private var playing = false
    private val timers = ArrayList<Pair<Double, () -> Unit>>()
    private val script = ArrayList<Pair<Double, () -> Unit>>()
    private val scratch = CardRect()

    /** [top] is the top safe inset in dp: the status bar, and any camera cutout under it. */
    fun layout(width: Float, top: Float, reduced: Boolean) {
        this.width = width
        toastW = max(ISLAND_W, min(420f, width - SIDE * 2f))
        cardW = max(ISLAND_W, min(410f, width - 24f))
        islandTop = max(6f, top + 6f)
        restX = width / 2f
        this.reduced = reduced
    }

    fun step(dt: Double, autoPlay: Boolean) {
        if (autoPlay != playing && width > 0f) {
            if (autoPlay) start() else stop()
        }
        now += dt
        for (v in listOf(islandW, islandH, bigOpacity, expanded, upload)) v.step(dt)
        for (m in motion.values) for (v in m.values) v.step(dt)
        // Timers fire after the values moved, so what they start is first drawn at rest.
        fire(timers)
        fire(script)
        frame.longValue += 1
    }

    private fun fire(list: ArrayList<Pair<Double, () -> Unit>>) {
        while (true) {
            val next = list.minByOrNull { it.first } ?: return
            if (next.first > now) return
            list.remove(next)
            next.second()
        }
    }

    private fun later(seconds: Double, run: () -> Unit) {
        timers.add(now + seconds to run)
    }

    /** Clears every pending timer: the composable is leaving. */
    fun dispose() {
        timers.clear()
        script.clear()
        playing = false
    }

    // ── Geometry ─────────────────────────────────────────────────────────────────────────────────

    /** The island's centre: on the camera at rest, the screen's centre once open. */
    fun islandX(): Float {
        val open = clamp01((islandW.float - ISLAND_W) / max(1f, toastW - ISLAND_W))
        return mix(restX, width / 2f, open)
    }

    private fun slotRect(out: CardRect, slot: Float, e: Float, off: Float): CardRect {
        val s = max(0f, slot)
        // Closed, the cards behind are narrower and only their bottom edge shows under the front one.
        val closedW = cardW - 2f * PEEK_INSET * min(s, 3f)
        val closedY = deckTop + off + PEEK * min(s, 2.4f)
        val openY = deckTop + off + HEADER + s * (CARD_H + CARD_GAP)
        val w = mix(closedW, cardW, e)
        return out.set((width - w) / 2f, mix(closedY, openY, e), w, CARD_H, CARD_RADIUS, mix(slotFade(s), 0f, e))
    }

    /** Where a card is drawn: from the open toast to its slot, and from its slot into the island. */
    fun cardRect(kind: Kind, out: CardRect): CardRect {
        val m = motion.getValue(kind)
        val off = max(0f, islandH.float - ISLAND_H)
        val rest = slotRect(scratch, m.slot.float, expanded.float, off)
        out.set((width - toastW) / 2f, islandTop, toastW, TOAST_H, radiusFor(toastW, TOAST_H), 0f)
        out.mixTo(rest.x, rest.y, rest.w, rest.h, rest.r, rest.fade, m.arrive.float)
        val q = m.absorb.float
        if (q > 0f) {
            // It darkens to the island's black well before it gets there.
            out.mixTo(restX - ISLAND_W / 2f, islandTop, ISLAND_W, ISLAND_H, ISLAND_H / 2f, 0f, q, min(1f, q * 2.5f))
        }
        return out
    }

    /** The fillets or neck joining the island to a card on its way out of it or back in. */
    fun bridge(kind: Kind, path: Path, card: CardRect): Boolean {
        val m = motion.getValue(kind)
        if (reduced || m.vis <= 0f || !(m.arrive.float < 0.999f || m.absorb.float > 0.001f)) return false
        cardRect(kind, card)
        val w = islandW.float
        val h = islandH.float
        return bridgePath(path, islandX(), islandTop, w / 2f, h, radiusFor(w, h), card.y, card.w / 2f, card.h, card.r)
    }

    // ── Actions ──────────────────────────────────────────────────────────────────────────────────

    private fun morph(w: Float, h: Float, open: Boolean, velocity: Float = 0f) {
        if (reduced) {
            islandW.timing(w, 220.0, Ease.Out)
            islandH.timing(h, 220.0, Ease.Out)
            return
        }
        if (open) {
            islandW.spring(w, OPEN_W_MS, OPEN_W_RATIO)
            islandH.spring(h, OPEN_H_MS, OPEN_H_RATIO, velocity)
        } else {
            islandW.spring(w, CLOSE_MS, CLOSE_RATIO)
            islandH.spring(h, CLOSE_MS, CLOSE_RATIO, velocity)
        }
    }

    private fun commitStack(next: List<Kind>) {
        stack = next
        next.forEachIndexed { index, kind ->
            val slot = motion.getValue(kind).slot
            if (reduced) slot.timing(index.toFloat(), 200.0) else slot.spring(index.toFloat(), SLOT_MS, SLOT_RATIO)
        }
    }

    private fun startTimer() {
        if (timerStart == null) timerStart = now
    }

    private fun expand(open: Boolean) {
        if (open && stack.isEmpty()) return
        expandedOpen = open
        val target = if (open) 1f else 0f
        if (reduced) expanded.timing(target, 220.0) else expanded.spring(target, EXPAND_MS, EXPAND_RATIO)
    }

    /** The toast drops out of the island as a card at the front of the stack. */
    private fun drop(kind: Kind) {
        // A tap can drop a toast just before its own timer does: the second drop is a no-op.
        if (presenting != kind || kind in stack) return
        val m = motion.getValue(kind)
        m.content.timing(0f, CONTENT_OUT)
        later((CONTENT_OUT - 20.0) / 1000.0) {
            if (presenting != kind || kind in stack) return@later
            m.tx.set(0f)
            m.absorb.set(0f)
            m.slot.set(0f)
            m.arrive.set(0f)
            m.vis = 1f
            if (reduced) m.arrive.timing(1f, 260.0, Ease.Out) else m.arrive.spring(1f, ARRIVE_MS, ARRIVE_RATIO)
            morph(ISLAND_W, ISLAND_H, false)
            commitStack(listOf(kind) + stack.filter { it != kind })
            later(0.32) { presenting = null }
        }
    }

    private fun present(kind: Kind, user: Boolean = false) {
        if (presenting != null || bigOpen || clearing || kind in stack || width == 0f) return
        presenting = kind
        if (kind == Kind.Timer) startTimer()
        if (kind == Kind.Upload) {
            uploadStart = now
            upload.set(0f)
            upload.timing(1f, UPLOAD_TIME * 1000.0, Ease.Linear)
        }
        if (user) onLight()
        morph(toastW, TOAST_H, true)
        motion.getValue(kind).content.delayedTiming(if (reduced) 60.0 else CONTENT_DELAY, 1f, CONTENT_IN, Ease.Out)
        later(SHOW_TIME) { drop(kind) }
    }

    private fun remove(kind: Kind) {
        val m = motion.getValue(kind)
        m.vis = 0f
        m.tx.set(0f)
        val next = stack.filter { it != kind }
        commitStack(next)
        if (next.isEmpty() && expandedOpen) expand(false)
        if (kind == Kind.Upload) uploadStart = null
    }

    private fun dismiss(kind: Kind, direction: Float, user: Boolean = false, velocity: Float = 0f) {
        if (kind !in stack || clearing) return
        if (user) onLight()
        if (reduced) {
            remove(kind)
            return
        }
        motion.getValue(kind).tx.spring(direction * width * 1.2f, 380.0, 1.0, velocity, clamp = true) { remove(kind) }
    }

    private fun openBig(user: Boolean = false, velocity: Float = 0f) {
        if (presenting != null || clearing || width == 0f) return
        bigOpen = true
        bigMounted = true
        startTimer()
        if (user) onMedium()
        morph(toastW, BIG_H, true, velocity)
        bigOpacity.delayedTiming(if (reduced) 60.0 else CONTENT_DELAY - 40.0, 1f, CONTENT_IN, Ease.Out)
    }

    private fun closeBig(velocity: Float = 0f) {
        if (!bigOpen) return
        bigOpen = false
        bigOpacity.timing(0f, CONTENT_OUT)
        later((CONTENT_OUT - 30.0) / 1000.0) { morph(ISLAND_W, ISLAND_H, false, velocity) }
        later(0.42) { if (!bigOpen) bigMounted = false }
    }

    private fun gulp() {
        if (reduced) return
        islandW.spring(ISLAND_W, GULP_MS, GULP_RATIO, GULP_VELOCITY)
        islandH.spring(ISLAND_H, GULP_MS, GULP_RATIO, GULP_VELOCITY * 0.25f)
    }

    /** Every card rises back into the island, front first, and the island gulps each one. */
    private fun clearAll() {
        if (clearing) return
        val cards = stack
        if (expandedOpen) expand(false)
        if (cards.isEmpty()) return
        clearing = true
        cards.forEachIndexed { i, kind ->
            later(i * ABSORB_STAGGER) {
                val m = motion.getValue(kind)
                if (reduced) m.absorb.timing(1f, 220.0) else m.absorb.timing(1f, ABSORB_MS, Ease.InOut) { gulp() }
            }
        }
        later((cards.size - 1) * ABSORB_STAGGER + ABSORB_MS / 1000.0 + 0.06) {
            for (kind in cards) {
                val m = motion.getValue(kind)
                m.vis = 0f
                m.absorb.set(0f)
                m.content.set(0f)
            }
            commitStack(emptyList())
            clearing = false
            timerStart = null
            uploadStart = null
        }
    }

    // ── Script ───────────────────────────────────────────────────────────────────────────────────

    private fun runScript() {
        script.clear()
        val steps: List<Pair<Double, () -> Unit>> = listOf(
            0.6 to { present(Kind.Call) },
            3.0 to { present(Kind.Message) },
            5.4 to { present(Kind.Upload) },
            7.8 to { present(Kind.Timer) },
            10.3 to { expand(true) },
            11.4 to { dismiss(Kind.Message, 1f) },
            12.3 to { expand(false) },
            12.9 to { openBig() },
            14.2 to { closeBig() },
            14.7 to { clearAll() },
            LOOP to { runScript() },
        )
        for ((at, run) in steps) script.add(now + at to run)
    }

    private fun start() {
        playing = true
        runScript()
    }

    private fun stop() {
        playing = false
        script.clear()
    }

    /** A touch pauses the script; a while later everything settles back to rest and it starts over. */
    private fun touched() {
        if (!playing) return
        script.clear()
        script.add(
            now + USER_HOLD to {
                closeBig()
                expand(false)
                later(0.45) { clearAll() }
                script.add(now + 0.45 + REST to { runScript() })
            },
        )
    }

    // ── Touch ────────────────────────────────────────────────────────────────────────────────────

    val canPull get() = presenting == null && !clearing

    fun pullStart() {
        if (!canPull) return
        dragging = true
        dragFrom = if (bigOpen) 1f else 0f
        touched()
        bigMounted = true
    }

    fun pullUpdate(dy: Float) {
        if (!dragging) return
        // Pulling down opens the island with the finger; past fully open it stretches on a band.
        val raw = dragFrom + dy / BIG_DRAG
        val p = if (raw <= 1f) max(0f, raw) else 1f + 0.22f * tanh((raw - 1f) * 2f)
        val q = min(1f, p)
        islandW.set(mix(ISLAND_W, toastW, min(1f, q * 1.6f)))
        islandH.set(mix(ISLAND_H, BIG_H, p))
        bigOpacity.set(clamp01((p - 0.5f) / 0.4f))
    }

    fun pullEnd(dy: Float, velocity: Float) {
        if (!dragging) return
        dragging = false
        val p = dragFrom + dy / BIG_DRAG
        val v = if (velocity.isFinite()) velocity else 0f
        val open = v > BIG_FLICK || (v > -BIG_FLICK && p > (if (dragFrom > 0.5f) 1f - BIG_COMMIT else BIG_COMMIT))
        if (presenting != null || clearing) return
        if (open) {
            if (!bigOpen) openBig(true, v) else morph(toastW, BIG_H, true, v)
            bigOpacity.timing(1f, 160.0)
        } else {
            bigOpen = true
            closeBig(v)
        }
    }

    /** A pull the system took away settles where it was going. */
    fun pullCancel() {
        if (!dragging) return
        dragging = false
        if (islandH.float > (ISLAND_H + BIG_H) / 2f) {
            openBig()
        } else {
            bigOpen = true
            closeBig()
        }
    }

    fun tapIsland() {
        touched()
        val kind = presenting
        when {
            bigOpen -> closeBig()
            // A tap on an open toast sends it to the stack straight away.
            kind != null -> if (motion.getValue(kind).content.float > 0.5f) drop(kind)
            else -> Kind.entries.firstOrNull { it !in stack }?.let { present(it, true) }
        }
    }

    fun toggleStack() {
        touched()
        expand(!expandedOpen)
    }

    /** The header's clear button: every card goes back into the island, as when the script clears. */
    fun clearStack() {
        touched()
        clearAll()
    }

    fun cardPanStart() = touched()

    fun cardPanUpdate(kind: Kind, dx: Float) = motion.getValue(kind).tx.set(dx)

    fun cardPanEnd(kind: Kind, dx: Float, velocity: Float) {
        val v = if (velocity.isFinite()) velocity else 0f
        val far = abs(dx) > width * DISMISS_DISTANCE
        val fast = abs(v) > DISMISS_FLICK
        val direction = sign(if (fast) v else dx)
        val along = sign(if (dx != 0f) dx else v)
        if ((far || fast) && direction != 0f && direction == along) {
            dismiss(kind, direction, true, v)
        } else {
            motion.getValue(kind).tx.spring(0f, 420.0, 0.72, v)
        }
    }
}
