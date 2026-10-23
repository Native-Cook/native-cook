package com.ngocdevv.nativecook.blocks.gestures.scratchticket

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** A ticket's prize: the photo under the foil and the line printed on the stub. */
internal class ScratchPrize(val photo: String, val prize: String, val detail: String) {
    /** Photos are drawn about 330 dp wide: 1000 px covers 3× screens. */
    val url get() = "https://images.unsplash.com/$photo?auto=format&fit=crop&w=1000&q=80"
}

internal val ScratchPrizes = listOf(
    ScratchPrize("photo-1536633125620-8a3245c11ffa", "A picnic in the sunflowers", "Hollin Farm · for two"),
    ScratchPrize("photo-1552160793-cbaf3ebcba72", "Golden hour photo walk", "Wren Valley · any Saturday"),
    ScratchPrize("photo-1627891374362-c6ca6c675cb3", "A year of Friday bouquets", "Marrow Hill Flowers"),
    ScratchPrize("photo-1530655937400-7f00928c51e1", "Two nights at Alder Barn", "Breakfast included"),
)

internal const val TAU = (PI * 2).toFloat()

// Layout (dp): the ticket is at most TICKET_MAX wide with SIDE margins; the scratch window is
// SCRATCH_ASPECT as tall as it is wide; the button sits BUTTON_GAP under the ticket.
private const val TICKET_MAX = 370f
private const val SIDE = 20f
private const val SCRATCH_TOP = 114f
private const val SCRATCH_ASPECT = 0.7f
private const val STUB = 100f
private const val BUTTON_GAP = 26f
internal const val BUTTON_HEIGHT = 54f

// Scratching: the foil is split into GRID_X × GRID_Y cells; a cell counts as cleared when the
// brush's middle passes over it, and at REVEAL_AT of them the rest dissolves over DISSOLVE_TIME s.
internal const val BRUSH = 46f
private const val GRID_X = 16
private const val GRID_Y = 12
private const val REVEAL_AT = 0.6f
private const val DISSOLVE_TIME = 1.1f
private val DissolveEase = CubicBezierEasing(0.33f, 0f, 0.2f, 1f)
// A flake tears off for every FLAKE_EVERY dp the finger scratches through fresh foil.
private const val FLAKE_EVERY = 7f
private const val FLAKE_MAX = 240
// The finger's path is kept as points; past this many the rest of the foil dissolves, so a long
// scrub over one corner never grows the path (and its per-frame rebuild) without bound.
private const val POINT_MAX = 4000
private const val FLAKE_GRAVITY = 1300f
private const val FLAKE_DRAG = 2.6f
private const val CONFETTI = 110
private const val CONFETTI_GRAVITY = 1150f
private const val CONFETTI_DRAG = 1.8f
internal val ConfettiColours = intArrayOf(0xFFEEA41C.toInt(), 0xFFF7E9C6.toInt(), 0xFFE2673D.toInt(), 0xFF5F9C46.toInt(), 0xFF8EC5E6.toInt())
// The foil's light follows the finger, easing at this rate (1/s); left alone it drifts slowly.
private const val TILT_RATE = 6f

// Tickets change places: the new one springs in from the right, the old one is thrown left.
// Dragging the ticket left past COMMIT_DISTANCE of the screen, or flicking faster than
// COMMIT_SPEED (dp/s), throws it too; to the right it only gives a RUBBER band.
private const val ENTER_MS = 800f
private const val ENTER_RATIO = 0.8f
private const val LEAVE_MS = 650f
private const val LEAVE_RATIO = 1f
private const val BACK_MS = 450f
private const val BACK_RATIO = 0.8f
private const val COMMIT_DISTANCE = 0.3f
private const val COMMIT_SPEED = 800f
private const val RUBBER = 70f
private const val FADE_TIME = 0.32f
private val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

// autoPlay loop (seconds): a virtual finger scratches a zigzag over the foil, the prize shows,
// then "New ticket" brings the next one in and it rests, unscratched, as the loop began.
private const val LOOP = 9f
private const val FINGER_DOWN_AT = 0.7f
private const val FINGER_UP_AT = 3.9f
private const val ZIGZAG_LEGS = 7
private const val ZIGZAG_INSET = 22f
private const val NEW_AT = 6.5f
// A touch pauses the loop this long; then it resumes from a fresh ticket.
private const val USER_HOLD = 4f

internal fun clamp(x: Float, lo: Float, hi: Float) = min(hi, max(lo, x))

/** Reanimated's default timing curve, ease in and out (quadratic). */
internal fun inOutQuad(t: Float) = if (t < 0.5f) 2 * t * t else 1 - (-2 * t + 2).pow(2) / 2

/** Resistance past an edge: follows the finger at first, never further than RUBBER. */
private fun rubber(x: Float) = RUBBER * (1 - 1 / (1 + x / RUBBER))

/** True when the clock went past [at] between [before] and [now], wrapping round the loop. */
private fun passed(before: Float, now: Float, at: Float) =
    if (now >= before) before < at && now >= at else at > before || at <= now

/** Where things sit, in dp. `ticket*` is on screen; `scratch*` and the rest are inside the ticket. */
internal class ScratchGeometry(val width: Float, val height: Float, insetTop: Float, insetBottom: Float) {
    // Never narrower than the scratch window's margins: a zero first measure would hand negative
    // sizes to Modifier.size (which throws) and a negative radius to the window's gradient.
    val ticketW = min(width - SIDE * 2, TICKET_MAX).coerceAtLeast(88f)
    val scratchX = 22f
    val scratchY = SCRATCH_TOP
    val scratchW = ticketW - 44f
    val scratchH = (scratchW * SCRATCH_ASPECT).roundToInt().toFloat()
    val perfY = SCRATCH_TOP + scratchH + 24f
    val ticketH = perfY + STUB
    val ticketX = (width - ticketW) / 2f
    val ticketY = insetTop + max(12f, (height - insetTop - insetBottom - (ticketH + BUTTON_GAP + BUTTON_HEIGHT)) / 2f)
    val buttonY = ticketY + ticketH + BUTTON_GAP
}

/**
 * A number driven like a Reanimated shared value: set outright, or animated by a sequence of
 * timings and duration springs (stiffness solved as Reanimated solves it) stepped by the frame clock.
 */
internal class ScratchValue(var value: Float) {
    sealed interface Step
    class Timing(val to: Float, val seconds: Float, val curve: (Float) -> Float = ::inOutQuad) : Step
    class Spring(val to: Float, val ms: Float, val ratio: Float, val velocity: Float = 0f) : Step

    private val steps = ArrayDeque<Step>()
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
    private var easing: (Float) -> Float = { it }

    val moving get() = mode != 0

    /** Puts the value somewhere at once, stopping whatever was moving it. */
    fun set(next: Float) {
        value = next
        mode = 0
        velocity = 0f
        steps.clear()
    }

    fun run(vararg next: Step) {
        // A spring that takes over from a spring keeps its speed, as Reanimated's does.
        val carried = if (mode == 1) velocity else 0f
        steps.clear()
        steps.addAll(next)
        mode = 0
        start(carried)
    }

    private fun start(carried: Float) {
        when (val step = steps.removeFirstOrNull() ?: return) {
            is Timing -> {
                mode = 2
                from = value
                to = step.to
                time = 0f
                duration = step.seconds
                velocity = 0f
                easing = step.curve
            }
            is Spring -> {
                val v = carried + step.velocity
                val x0 = value - step.to
                mode = 1
                to = step.to
                velocity = v
                zeta = step.ratio
                stiffness = stiffnessFor(x0, v, step.ms, step.ratio)
                omega0 = sqrt(stiffness)
                omega1 = if (zeta < 1f) omega0 * sqrt(1f - zeta * zeta) else 0f
                initialEnergy = energy(x0, v)
            }
        }
    }

    /** Advances by [dt] s; true on the frame the whole sequence finishes. */
    fun step(dt: Float): Boolean {
        var done = false
        when (mode) {
            0 -> return false
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
                if (initialEnergy == 0f || !initialEnergy.isFinite() || energy(to - value, velocity) / initialEnergy <= ENERGY_THRESHOLD) {
                    value = to
                    velocity = 0f
                    done = true
                }
            }
            else -> {
                time += dt
                val p = if (duration > 0f) clamp(time / duration, 0f, 1f) else 1f
                value = from + (to - from) * easing(p)
                done = p >= 1f
            }
        }
        if (!done) return false
        mode = 0
        start(0f)
        return mode == 0
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

/** Foil flakes and confetti: a piece's place, speed, turn, size, age and colour. */
internal class ScratchPiece(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var angle: Float, val spin: Float, val size: Float, var age: Float, val life: Float,
    val colour: Int, val flip: Float,
)

internal object ScratchPieces {
    /** Moves every piece one step: drag, gravity, spin; drops the ones that are spent or off screen. */
    fun step(list: MutableList<ScratchPiece>, dt: Float, gravity: Float, drag: Float, floor: Float) {
        if (list.isEmpty()) return
        val keep = exp(-drag * dt)
        val it = list.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.age += dt
            p.vx *= keep
            p.vy = p.vy * keep + gravity * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.angle += p.spin * dt
            if (p.age >= p.life || p.y > floor) it.remove()
        }
    }

    private fun r() = Random.nextFloat()

    /** Adds [count] foil flakes torn off around (x, y), thrown a little along the finger's motion. */
    fun flakes(list: MutableList<ScratchPiece>, count: Int, x: Float, y: Float, vx: Float, vy: Float, spread: Float) {
        repeat(count) {
            val a = r() * TAU
            val d = sqrt(r()) * spread
            list += ScratchPiece(
                x + cos(a) * d, y + sin(a) * d, vx * 0.12f + (r() - 0.5f) * 140f, vy * 0.12f - 40f - r() * 160f,
                r() * TAU, (r() - 0.5f) * 14f, 2f + r() * 3.5f, 0f, 0.9f + r() * 0.7f, 0, r() * 6f,
            )
        }
    }

    /** A burst of confetti from a rectangle's centre, fanned upwards. */
    fun confetti(list: MutableList<ScratchPiece>, count: Int, x: Float, y: Float, width: Float, colours: Int) {
        for (k in 0 until count) {
            val a = -PI.toFloat() / 2f + (r() - 0.5f) * 2.1f
            val speed = 420f + r() * 720f
            list += ScratchPiece(
                x + (r() - 0.5f) * width * 0.6f, y + (r() - 0.5f) * 30f, cos(a) * speed, sin(a) * speed,
                r() * TAU, (r() - 0.5f) * 9f, 6f + r() * 5f, 0f, 1.5f + r() * 0.9f, k % colours, r() * 6f,
            )
        }
    }

    /**
     * Every piece of a colour (−1: all of them) as a small turning quad added to [path]. A piece
     * narrows as it flips over (cos of its flip phase) and shrinks away over its last quarter.
     */
    fun path(path: Path, list: List<ScratchPiece>, colour: Int, aspect: Float) {
        path.reset()
        for (p in list) {
            if (colour >= 0 && p.colour != colour) continue
            val t = p.age / p.life
            val fade = if (t > 0.75f) (1 - t) / 0.25f else 1f
            val size = p.size * fade
            val w = size * (0.2f + 0.8f * abs(cos(p.flip + p.age * 7f)))
            val h = size * aspect
            val c = cos(p.angle)
            val s = sin(p.angle)
            val ax = c * w / 2
            val ay = s * w / 2
            val bx = -s * h / 2
            val by = c * h / 2
            path.moveTo(p.x - ax - bx, p.y - ay - by)
            path.lineTo(p.x + ax - bx, p.y + ay - by)
            path.lineTo(p.x + ax + bx, p.y + ay + by)
            path.lineTo(p.x - ax + bx, p.y - ay + by)
            path.close()
        }
    }
}

/** The state of one ticket on the table. Two of these take turns: the one in play and the next. */
internal class ScratchSlot(var ticket: Int, offset: Float, shown: Float) {
    /** Finger path inside the scratch area as x, y pairs; NaN pairs split the strokes. */
    val points = ArrayList<Float>()
    /** Which cells of the coverage grid the finger has cleared, and how many. */
    val grid = BooleanArray(GRID_X * GRID_Y)
    var cleared = 0
    var revealed = false
    /** When it was revealed (engine seconds), for the prize line's entrance. */
    var revealedAt = -1f
    /** 0 → 1 as the rest of the foil dissolves. */
    val dissolve = ScratchValue(0f)
    /** A short bump on reveal, 0 at rest. */
    val pop = ScratchValue(0f)
    /** Horizontal travel (dp) and opacity while tickets change places. */
    val offset = ScratchValue(offset)
    val shown = ScratchValue(shown)
    // Bumped whenever the strokes change, so the drawn path is rebuilt only then.
    var version = 0

    fun reset(next: Int) {
        ticket = next
        points.clear()
        grid.fill(false)
        cleared = 0
        revealed = false
        revealedAt = -1f
        dissolve.set(0f)
        pop.set(0f)
        version += 1
    }

    /** The finger's strokes as one smooth path: each point is the control of a curve through midpoints. */
    fun strokes(path: Path) {
        path.reset()
        var open = false
        var px = 0f
        var py = 0f
        var i = 0
        while (i < points.size) {
            val x = points[i]
            val y = points[i + 1]
            i += 2
            if (x.isNaN()) {
                open = false
                continue
            }
            if (!open) {
                path.moveTo(x, y)
                // A dot even if the finger never moves.
                path.lineTo(x + 0.1f, y)
                open = true
            } else {
                path.quadraticTo(px, py, (px + x) / 2f, (py + y) / 2f)
            }
            px = x
            py = y
        }
    }
}

/** What the table asks of the screen around it: haptics and a callback. */
internal interface ScratchHost {
    fun success()
    fun light()
    fun revealed(prize: String)
}

/** The table's state, stepped once a frame like the RN frame callback, and the gestures that drive it. */
internal class ScratchEngine(private val host: ScratchHost) {
    val prizes = ScratchPrizes
    var reducedMotion = false
    var geo = ScratchGeometry(400f, 800f, 0f, 0f)

    val slots = listOf(ScratchSlot(0, 0f, 1f), ScratchSlot(1, 10000f, 0f))
    // How many tickets have been dealt, and which slot is in play.
    private var dealt = 1
    var active = 0
        private set
    private var switching = false
    var phase = 0f
        private set
    var tiltX = 0f
        private set
    var tiltY = 0f
        private set
    private var goalX = 0f
    private var goalY = 0f
    val flakes = ArrayList<ScratchPiece>()
    val confetti = ArrayList<ScratchPiece>()
    // What the finger is doing: 0 nothing, 1 scratching, 2 dragging the ticket.
    private var mode = 0
    var fingerX = 0f
        private set
    var fingerY = 0f
        private set
    private var fingerOn = false
    private var fingerFresh = false
    val fingerAlpha = ScratchValue(0f)
    val press = ScratchValue(1f)
    val emphasis = ScratchValue(0f)
    private var clock = 0f
    private var looping = false
    private var hold = 0f
    var elapsed = 0f
        private set
    private var parked = false

    fun setAutoPlay(on: Boolean) {
        looping = on
        clock = 0f
        hold = 0f
    }

    fun step(dt: Float) {
        if (dt <= 0f) return
        // The waiting slot sits just off the right edge once the screen is measured.
        if (!parked) {
            parked = true
            if (!reducedMotion && active != 1) slots[1].offset.set(geo.width * 1.15f)
        }
        elapsed += dt
        phase = (phase + dt * TAU / LOOP) % TAU
        val p = phase

        slots.forEachIndexed { k, s ->
            s.dissolve.step(dt)
            s.pop.step(dt)
            s.shown.step(dt)
            if (s.offset.step(dt) && k == active) switching = false
        }
        if (reducedMotion && switching && !slots[active].shown.moving) switching = false
        fingerAlpha.step(dt)
        press.step(dt)
        emphasis.step(dt)

        // autoPlay: a fixed script on a clock, paused for a while by any touch.
        if (hold > 0f) {
            hold -= dt
            if (hold <= 0f && looping) {
                if (slots[active].cleared > 0) {
                    newTicket(false, 0f)
                    clock = NEW_AT
                } else {
                    clock = 0f
                }
            }
        } else if (looping) {
            val before = clock
            val t = (before + dt) % LOOP
            clock = t
            if (passed(before, t, FINGER_DOWN_AT) && !switching) {
                fingerOn = true
                fingerFresh = true
                fingerAlpha.run(ScratchValue.Timing(1f, 0.16f))
            }
            if (fingerOn) {
                val u = clamp((t - FINGER_DOWN_AT) / (FINGER_UP_AT - FINGER_DOWN_AT), 0f, 1f)
                // The virtual finger's point: side to side ZIGZAG_LEGS times, going down.
                val legs = u * ZIGZAG_LEGS
                val leg = min(ZIGZAG_LEGS - 1, floor(legs).toInt())
                val f = legs - leg
                val across = if (leg % 2 == 0) f else 1 - f
                val x = geo.ticketX + geo.scratchX + ZIGZAG_INSET + across * (geo.scratchW - ZIGZAG_INSET * 2)
                val y = geo.ticketY + geo.scratchY + ZIGZAG_INSET + u * (geo.scratchH - ZIGZAG_INSET * 2)
                val fresh = fingerFresh
                val vx = if (fresh) 0f else (x - fingerX) / dt
                val vy = if (fresh) 0f else (y - fingerY) / dt
                fingerX = x
                fingerY = y
                fingerFresh = false
                scratchAt(x, y, vx, vy, fresh, false)
                if (u >= 1f || slots[active].revealed) {
                    fingerOn = false
                    fingerAlpha.run(ScratchValue.Timing(0f, 0.22f))
                }
            }
            if (passed(before, t, NEW_AT)) {
                press.run(ScratchValue.Timing(0.97f, 0.11f), ScratchValue.Timing(1f, 0.18f))
                newTicket(false, 0f)
            }
        }

        // The foil's light: towards the finger while scratching, otherwise a slow drift on the loop.
        if (mode != 1 && !fingerOn) {
            goalX = sin(p) * 0.45f
            goalY = cos(p) * 0.3f
        }
        val ease = 1 - exp(-dt * TILT_RATE)
        tiltX += (goalX - tiltX) * ease
        tiltY += (goalY - tiltY) * ease

        ScratchPieces.step(flakes, dt, FLAKE_GRAVITY, FLAKE_DRAG, geo.height + 20f)
        ScratchPieces.step(confetti, dt, CONFETTI_GRAVITY, CONFETTI_DRAG, geo.height + 20f)
    }

    /** Deals the next ticket into the waiting slot and swaps the two. */
    private fun deal(user: Boolean, velocity: Float) {
        val from = active
        val to = 1 - from
        val next = (dealt + 1) % prizes.size
        dealt += 1
        val coming = slots[to]
        val leaving = slots[from]
        coming.reset(next)
        active = to
        emphasis.run(ScratchValue.Timing(0f, 0.24f))
        if (user) host.light()
        if (reducedMotion) {
            coming.offset.set(0f)
            coming.shown.run(ScratchValue.Timing(1f, FADE_TIME))
            leaving.shown.run(ScratchValue.Timing(0f, FADE_TIME))
            return
        }
        coming.shown.set(1f)
        // Parked off the right edge first, so it always comes in from there.
        coming.offset.set(geo.width * 1.15f)
        coming.offset.run(ScratchValue.Spring(0f, ENTER_MS, ENTER_RATIO))
        leaving.offset.run(ScratchValue.Spring(-geo.width * 1.25f, LEAVE_MS, LEAVE_RATIO, velocity))
    }

    private fun newTicket(user: Boolean, velocity: Float) {
        if (switching) return
        switching = true
        fingerOn = false
        fingerAlpha.run(ScratchValue.Timing(0f, 0.16f))
        deal(user, velocity)
    }

    private fun reveal(k: Int, user: Boolean) {
        val s = slots[k]
        s.revealed = true
        s.revealedAt = elapsed
        s.dissolve.run(ScratchValue.Timing(1f, DISSOLVE_TIME) { DissolveEase.transform(it) })
        emphasis.run(ScratchValue.Timing(1f, 0.3f))
        if (!reducedMotion) {
            s.pop.run(ScratchValue.Timing(1f, 0.14f) { EaseOut.transform(it) }, ScratchValue.Spring(0f, 600f, 0.45f))
            ScratchPieces.confetti(
                confetti, CONFETTI,
                geo.ticketX + geo.scratchX + geo.scratchW / 2f, geo.ticketY + geo.scratchY + geo.scratchH / 2f,
                geo.scratchW, ConfettiColours.size,
            )
        }
        if (user) host.success()
        host.revealed(prizes[s.ticket].prize)
    }

    /** The finger at (x, y) on screen: rubs the foil there, counts what it cleared, sheds flakes. */
    private fun scratchAt(x: Float, y: Float, vx: Float, vy: Float, begin: Boolean, user: Boolean) {
        if (switching) return
        val k = active
        val s = slots[k]
        if (s.revealed) return
        val lx = x - geo.ticketX - geo.scratchX - s.offset.value
        val ly = y - geo.ticketY - geo.scratchY
        val n = s.points.size
        val joined = !begin && n >= 2 && !s.points[n - 2].isNaN()
        val px = if (joined) s.points[n - 2] else lx
        val py = if (joined) s.points[n - 1] else ly
        if (joined && hypot(lx - px, ly - py) < 2.5f) return
        if (begin && n > 0) {
            s.points += Float.NaN
            s.points += Float.NaN
        }
        s.points += lx
        s.points += ly
        s.version += 1
        goalX = clamp(lx / geo.scratchW * 2 - 1, -1f, 1f)
        goalY = clamp(ly / geo.scratchH * 2 - 1, -1f, 1f)

        val added = markCells(s.grid, px, py, lx, ly, geo.scratchW, geo.scratchH)
        val full = s.points.size >= POINT_MAX * 2
        if (added == 0) {
            if (full) reveal(k, user)
            return
        }
        s.cleared += added
        if (!reducedMotion && flakes.size < FLAKE_MAX) {
            val count = min(5, 1 + floor(hypot(lx - px, ly - py) / FLAKE_EVERY).toInt())
            ScratchPieces.flakes(flakes, count, x, y, vx, vy, BRUSH * 0.35f)
        }
        if (s.cleared >= REVEAL_AT * GRID_X * GRID_Y || full) reveal(k, user)
    }

    /** Marks the grid cells the brush covers between two points; returns how many were new. */
    private fun markCells(grid: BooleanArray, x0: Float, y0: Float, x1: Float, y1: Float, w: Float, h: Float): Int {
        val cw = w / GRID_X
        val ch = h / GRID_Y
        // A window with no size (or a non-finite point) has nothing to clear.
        if (cw <= 0f || ch <= 0f || !x0.isFinite() || !y0.isFinite() || !x1.isFinite() || !y1.isFinite()) return 0
        val reach = BRUSH * 0.45f
        val steps = max(1, ceil(hypot(x1 - x0, y1 - y0) / 6f).toInt())
        var added = 0
        for (k in 0..steps) {
            val x = x0 + (x1 - x0) * k / steps
            val y = y0 + (y1 - y0) * k / steps
            val c0 = max(0, floor((x - reach) / cw).toInt())
            val c1 = min(GRID_X - 1, floor((x + reach) / cw).toInt())
            val r0 = max(0, floor((y - reach) / ch).toInt())
            val r1 = min(GRID_Y - 1, floor((y + reach) / ch).toInt())
            for (r in r0..r1) {
                for (c in c0..c1) {
                    val at = r * GRID_X + c
                    if (!grid[at] && hypot((c + 0.5f) * cw - x, (r + 0.5f) * ch - y) <= reach) {
                        grid[at] = true
                        added += 1
                    }
                }
            }
        }
        return added
    }

    private fun hideFinger() {
        if (fingerOn) {
            fingerOn = false
            fingerAlpha.run(ScratchValue.Timing(0f, 0.16f))
        }
    }

    // Button

    fun newTicketPressed() {
        hold = USER_HOLD
        hideFinger()
        if (switching) return
        switching = true
        deal(true, 0f)
    }

    fun buttonPressing(down: Boolean) {
        press.run(ScratchValue.Timing(if (down) 0.97f else 1f, if (down) 0.11f else 0.16f))
    }

    // Gestures (dp)

    fun touchBegan(x: Float, y: Float) {
        hold = USER_HOLD
        hideFinger()
        mode = 0
        if (switching) return
        val left = geo.ticketX + geo.scratchX
        val top = geo.ticketY + geo.scratchY
        val inWindow = x > left - 6 && x < left + geo.scratchW + 6 && y > top - 6 && y < top + geo.scratchH + 6
        if (inWindow && !slots[active].revealed) {
            mode = 1
            scratchAt(x, y, 0f, 0f, true, true)
        } else if (x > geo.ticketX && x < geo.ticketX + geo.ticketW && y > geo.ticketY && y < geo.ticketY + geo.ticketH) {
            mode = 2
        }
    }

    fun touchMoved(x: Float, y: Float, tx: Float, vx: Float, vy: Float) {
        if (mode == 1) {
            scratchAt(x, y, vx, vy, false, true)
        } else if (mode == 2 && !switching) {
            // A deal that started mid-drag (the button, or autoPlay resuming) owns the offsets now.
            slots[active].offset.set(if (tx < 0f) tx else rubber(tx))
        }
    }

    fun touchEnded(tx: Float, vx: Float) {
        if (mode == 2 && !switching) {
            if (tx < -geo.width * COMMIT_DISTANCE || vx < -COMMIT_SPEED) {
                newTicket(true, vx)
            } else {
                slots[active].offset.run(ScratchValue.Spring(0f, BACK_MS, BACK_RATIO, vx))
            }
        }
        mode = 0
    }
}
