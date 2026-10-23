package com.ngocdevv.nativecook.blocks.playground.fireflyswarm

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// autoPlay, one LOOP in seconds. Every periodic thing (blinks, wind, the hover of a resting
// firefly, the stars) repeats a whole number of times per LOOP, so the loop closes cleanly.
internal const val LOOP = 20f
internal const val TAU = (PI * 2).toFloat()
internal const val W0 = TAU / LOOP

// The swarm: COUNT fireflies, simulated at a fixed STEP (s), at most MAX_STEPS per frame.
internal const val COUNT = 150
private const val STEP = 1f / 60f
private const val MAX_STEPS = 3
// Boids: neighbours within SIGHT dp, kept SPACE dp apart. Free flight tops out at CRUISE dp/s,
// steering at TURN dp/s². Weights for separation, alignment, cohesion and wander.
private const val SIGHT = 46f
private const val SPACE = 16f
private const val CRUISE = 58f
private const val TURN = 90f
private const val SEPARATE = 1.6f
private const val ALIGN = 0.5f
private const val COHERE = 0.35f
private const val WANDER = 0.7f
// They keep to the air over the meadow: between BAND_TOP and BAND_BOTTOM of the height, and
// MARGIN dp in from the sides, pushed back at EDGE_PUSH per dp over.
private const val BAND_TOP = 0.28f
private const val BAND_BOTTOM = 0.84f
private const val MARGIN = 36f
private const val EDGE_PUSH = 1.6f
// A finger: they orbit it at ORBIT_SPEED dp/s on their own radius, up to RUSH dp/s, gathering in
// over GATHER per s. A release faster than FLICK dp/s scatters them with up to BLAST dp/s.
private const val ORBIT_SPEED = 150f
private const val RUSH = 240f
private const val GATHER = 4f
internal const val FLICK = 900f
private const val BLAST = 520f
private const val BLAST_REACH = 170f
// Holding still for HOLD_TIME calms them (HUSH damping per s) and pulls their blinks into a wave
// rolling out from the finger: one pulse every 1 / SYNC_RATE s, travelling at WAVE_SPEED dp/s.
internal const val HOLD_TIME = 0.45f
internal const val HOLD_SLOP = 14f
private const val HUSH = 1.3f
private const val HELD_PULL = 0.35f
private const val HELD_REACH = 150f
internal const val SYNC_RATE = 1f
internal const val WAVE_SPEED = 240f
private const val SYNC_PULL = 7f
private const val SYNC_IN = 1.6f
private const val SYNC_OUT = 0.45f
private const val DRIFT_BACK = 1.2f
// Resting (autoPlay only): each firefly hovers over its own spot in the meadow, held there by a
// spring of HOME_OMEGA rad/s.
private const val HOME_OMEGA = 3.2f
// A touch pauses autoPlay; it comes back RESUME s after the finger lifts.
private const val RESUME = 3f

// The script.
private const val EIGHT_FROM = 1.6f
private const val EIGHT_TIME = 6.6f
private const val EIGHT_WIDTH = 0.22f
private const val EIGHT_HEIGHT = 0.14f
private const val EIGHT_Y = 0.54f
private const val SWIPE_TIME = 0.22f
private const val SWIPE_TO_X = 0.82f
private const val SWIPE_TO_Y = 0.4f
private const val PRESS_FROM = 9.4f
private const val PRESS_TIME = 3.8f
private const val PRESS_AT_X = 0.46f
private const val PRESS_AT_Y = 0.5f
private const val HOME_FROM = 14.4f
private const val HOME_TIME = 2.6f

// Meadow: the grass bends in BUCKET dp columns where fireflies pass low over it.
internal const val BUCKET = 24f
private const val BEND_OMEGA = 6f
private const val BEND_DAMPING = 0.35f
private const val BEND_GAIN = 0.0045f
private const val BEND_MAX = 0.5f

private const val EMBER = 0.1f

/** The seeded generator the React Native version uses (mulberry32), so the meadow and the swarm match. */
internal class FireflyRandom(seed: Int) {
    private var s = seed

    fun next(): Float {
        s += 0x6d2b79f5
        var t = s
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        return (((t xor (t ushr 14)).toLong() and 0xffffffffL).toDouble() / 4294967296.0).toFloat()
    }
}

/**
 * Each firefly's fixed traits: where it rests and how it hovers there, its blink (a whole number
 * of flashes per LOOP), its depth, its orbit and its wander.
 */
internal object Traits {
    val homeX = FloatArray(COUNT)
    val homeY = FloatArray(COUNT)
    val bobX = FloatArray(COUNT)
    val bobY = FloatArray(COUNT)
    val bobXK = FloatArray(COUNT)
    val bobYK = FloatArray(COUNT)
    val bobXP = FloatArray(COUNT)
    val bobYP = FloatArray(COUNT)
    val blinkK = FloatArray(COUNT)
    val blinkP = FloatArray(COUNT)
    val depth = FloatArray(COUNT)
    val orbit = FloatArray(COUNT)
    val spin = FloatArray(COUNT)
    val wanderA = FloatArray(COUNT)
    val wanderB = FloatArray(COUNT)

    init {
        val r = FireflyRandom(11)
        for (i in 0 until COUNT) {
            homeX[i] = 0.05f + 0.9f * r.next()
            homeY[i] = 0.36f + 0.48f * r.next().pow(0.8f)
            bobX[i] = 6 + 12 * r.next()
            bobY[i] = 4 + 9 * r.next()
            bobXK[i] = 1 + floor(r.next() * 2)
            bobYK[i] = 1 + floor(r.next() * 3)
            bobXP[i] = r.next() * TAU
            bobYP[i] = r.next() * TAU
            blinkK[i] = 7 + floor(r.next() * 7)
            blinkP[i] = r.next()
            depth[i] = 0.55f + 0.65f * r.next()
            orbit[i] = 30 + 95 * r.next().pow(0.7f)
            spin[i] = if (r.next() < 0.82f) 1f else -1f
            wanderA[i] = r.next() * TAU
            wanderB[i] = r.next() * TAU
        }
    }
}

internal fun clamp01(v: Float) = if (v < 0f) 0f else if (v > 1f) 1f else v

internal fun smooth(t: Float): Float {
    val x = clamp01(t)
    return x * x * (3 - 2 * x)
}

/** A firefly's flash at blink phase `p`: a quick rise, a slower fade, an ember between. */
internal fun flash(p: Float): Float {
    val f = p - floor(p)
    if (f < 0.1f) return EMBER + (1 - EMBER) * smooth(f / 0.1f)
    return EMBER + (1 - EMBER) * exp(-(f - 0.1f) * 8)
}

/** The swarm's state, stepped at a fixed 60 Hz like the React Native frame callback. */
internal class Swarm {
    val x = FloatArray(COUNT)
    val y = FloatArray(COUNT)
    val vx = FloatArray(COUNT)
    val vy = FloatArray(COUNT)
    val drift = FloatArray(COUNT)
    var bend = FloatArray(0)
    private var bendSpeed = FloatArray(0)
    private var push = FloatArray(0)
    var elapsed = 0f
    private var spare = 0f
    var w = 0f
    var h = 0f
    var ready = false

    // The finger: down or not, where, how strongly it pulls (0–1), and whether it is held still.
    var fingerDown = false
    var fingerX = 0f
    var fingerY = 0f
    private var pull = 0f
    var holding = false
    // Synchronised blinking: its strength (0–1) and the point its wave rolls out from.
    var sync = 0f
    var syncX = 0f
    var syncY = 0f
    // How strongly the fireflies are held to their resting spots (autoPlay).
    var home = 0f
    private var clock = 0f
    private var autoFinger = false
    private var hold = 0f

    private val grid = HashMap<Int, ArrayList<Int>>()
    private val pool = ArrayList<ArrayList<Int>>()
    private val out = FloatArray(2)

    fun reset(width: Float, height: Float) {
        w = width
        h = height
        val t = Traits
        // Everyone starts on their resting spot, moving as the hover moves.
        for (i in 0 until COUNT) {
            x[i] = t.homeX[i] * w + t.bobX[i] * sin(t.bobXP[i])
            y[i] = t.homeY[i] * h + t.bobY[i] * sin(t.bobYP[i])
            vx[i] = t.bobX[i] * t.bobXK[i] * W0 * cos(t.bobXP[i])
            vy[i] = t.bobY[i] * t.bobYK[i] * W0 * cos(t.bobYP[i])
            drift[i] = 0f
        }
        val n = ceil(w / BUCKET).toInt() + 1
        bend = FloatArray(n)
        bendSpeed = FloatArray(n)
        push = FloatArray(n)
        ready = true
    }

    /** Throws every firefly away from (ox, oy), harder the closer it is, a little along (dx, dy). */
    fun scatter(ox: Float, oy: Float, dx: Float, dy: Float, reducedMotion: Boolean) {
        val l = hypot(dx, dy).let { if (it == 0f) 1f else it }
        for (i in 0 until COUNT) {
            val px = x[i] - ox
            val py = y[i] - oy
            val r = hypot(px, py).let { if (it == 0f) 1f else it }
            val power = BLAST * exp(-r / BLAST_REACH) * (if (reducedMotion) 0.3f else 1f)
            vx[i] += px / r * power + dx / l * power * 0.35f
            vy[i] += py / r * power + dy / l * power * 0.35f
        }
        pull = 0f
    }

    fun startSync(sx: Float, sy: Float) {
        holding = true
        syncX = sx
        syncY = sy
    }

    fun touched() {
        hold = RESUME
        home = 0f
        if (autoFinger) {
            autoFinger = false
            holding = false
        }
    }

    fun step(dt: Float, auto: Boolean, reducedMotion: Boolean) {
        if (!ready) return
        // autoPlay moves a finger along the script; a touch hands control back for a while.
        if (hold > 0f) {
            if (!fingerDown) hold -= dt
            if (hold <= 0f) clock = HOME_FROM
        } else if (auto) {
            val t = (clock + dt) % LOOP
            clock = t
            if (scriptFinger(t)) {
                if (!autoFinger) {
                    autoFinger = true
                    fingerDown = true
                }
                fingerX = out[0]
                fingerY = out[1]
                if (t - PRESS_FROM > HOLD_TIME && t <= PRESS_FROM + PRESS_TIME && !holding) startSync(out[0], out[1])
            } else if (autoFinger) {
                autoFinger = false
                fingerDown = false
                holding = false
                // The script's swipe ends in a flick.
                if (t < PRESS_FROM) scatter(SWIPE_TO_X * w, SWIPE_TO_Y * h, SWIPE_TO_X - 0.5f, SWIPE_TO_Y - EIGHT_Y, reducedMotion)
            }
            // Held home while resting; let go as the finger arrives; called home again at the end.
            home = when {
                t < EIGHT_FROM -> 1f
                t < EIGHT_FROM + 1 -> 1 - smooth(t - EIGHT_FROM)
                t >= HOME_FROM -> smooth((t - HOME_FROM) / HOME_TIME)
                else -> 0f
            }
        }

        // The finger's pull and the sync come and go smoothly; held still, the finger keeps a gentle
        // pull so the calmed swarm drifts in round it.
        val pulling = if (fingerDown) (if (holding) HELD_PULL else 1f) else 0f
        pull += (pulling - pull) * min(1f, GATHER * dt)
        sync += ((if (holding) 1f else 0f) - sync) * min(1f, (if (holding) SYNC_IN else SYNC_OUT) * dt)

        // Fixed steps of the swarm.
        var acc = spare + dt
        var steps = 0
        while (acc >= STEP && steps < MAX_STEPS) {
            acc -= STEP
            steps += 1
            advance(reducedMotion)
        }
        spare = acc
    }

    /** Where autoPlay's finger is at `t` s into the loop, into [out]; false while it is up. */
    private fun scriptFinger(t: Float): Boolean {
        val eight = (t - EIGHT_FROM) / EIGHT_TIME
        if (eight in 0f..1f) {
            val a = eight * TAU
            out[0] = w * (0.5f + EIGHT_WIDTH * sin(a))
            out[1] = h * (EIGHT_Y + EIGHT_HEIGHT * sin(2 * a))
            return true
        }
        val swipe = (t - EIGHT_FROM - EIGHT_TIME) / SWIPE_TIME
        if (swipe > 0f && swipe <= 1f) {
            val e = swipe * swipe
            out[0] = w * (0.5f + (SWIPE_TO_X - 0.5f) * e)
            out[1] = h * (EIGHT_Y + (SWIPE_TO_Y - EIGHT_Y) * e)
            return true
        }
        val press = (t - PRESS_FROM) / PRESS_TIME
        if (press in 0f..1f) {
            out[0] = w * PRESS_AT_X
            out[1] = h * PRESS_AT_Y
            return true
        }
        return false
    }

    /** Steering towards a desired velocity, capped at TURN, into [out]. */
    private fun steer(dx: Float, dy: Float, vx: Float, vy: Float, speed: Float) {
        val l = hypot(dx, dy)
        if (l < 1e-6f) {
            out[0] = 0f
            out[1] = 0f
            return
        }
        var sx = dx / l * speed - vx
        var sy = dy / l * speed - vy
        val m = hypot(sx, sy)
        if (m > TURN) {
            sx = sx / m * TURN
            sy = sy / m * TURN
        }
        out[0] = sx
        out[1] = sy
    }

    private fun advance(reducedMotion: Boolean) {
        val t = Traits
        val now = elapsed + STEP
        elapsed = now
        val a = if (reducedMotion) pull * 0.4f else pull
        val hm = home
        val hush = if (holding) HUSH else 0f
        val fx = fingerX
        val fy = fingerY
        // Bin everyone by SIGHT-sized cells for the neighbour search (the same keys as the RN Map).
        for (list in grid.values) {
            list.clear()
            pool.add(list)
        }
        grid.clear()
        val cols = ceil(w / SIGHT).toInt() + 2
        for (i in 0 until COUNT) {
            val key = floor(y[i] / SIGHT + 1).toInt() * cols + floor(x[i] / SIGHT + 1).toInt()
            grid.getOrPut(key) { if (pool.isEmpty()) ArrayList() else pool.removeAt(pool.size - 1) }.add(i)
        }
        val maxSpeed = CRUISE + (RUSH - CRUISE) * a
        val s = sync
        for (i in 0 until COUNT) {
            val px = x[i]
            val py = y[i]
            val pvx = vx[i]
            val pvy = vy[i]
            var sepX = 0f
            var sepY = 0f
            var aliX = 0f
            var aliY = 0f
            var cohX = 0f
            var cohY = 0f
            var seen = 0
            val cx = floor(px / SIGHT + 1).toInt()
            val cy = floor(py / SIGHT + 1).toInt()
            for (oy in -1..1) {
                for (ox in -1..1) {
                    val cell = grid[(cy + oy) * cols + cx + ox] ?: continue
                    for (n in 0 until cell.size) {
                        val j = cell[n]
                        if (j == i) continue
                        val dx = px - x[j]
                        val dy = py - y[j]
                        val d2 = dx * dx + dy * dy
                        if (d2 > SIGHT * SIGHT || d2 < 1e-6f) continue
                        seen += 1
                        aliX += vx[j]
                        aliY += vy[j]
                        cohX += x[j]
                        cohY += y[j]
                        if (d2 < SPACE * SPACE) {
                            sepX += dx / d2
                            sepY += dy / d2
                        }
                    }
                }
            }
            var ax = 0f
            var ay = 0f
            val flock = 1 - 0.6f * a
            if (sepX != 0f || sepY != 0f) {
                steer(sepX, sepY, pvx, pvy, maxSpeed)
                ax += out[0] * SEPARATE
                ay += out[1] * SEPARATE
            }
            if (seen > 0) {
                steer(aliX / seen, aliY / seen, pvx, pvy, maxSpeed)
                ax += out[0] * ALIGN * flock
                ay += out[1] * ALIGN * flock
                steer(cohX / seen - px, cohY / seen - py, pvx, pvy, maxSpeed)
                ax += out[0] * COHERE * flock
                ay += out[1] * COHERE * flock
            }
            // Wander: a heading that drifts on two slow sines of its own.
            val heading = t.wanderA[i] + 1.7f * sin(2 * W0 * now + t.wanderB[i]) + 0.9f * sin(5 * W0 * now + t.wanderA[i])
            steer(cos(heading), sin(heading) * 0.7f, pvx, pvy, CRUISE)
            ax += out[0] * WANDER * (1 - a)
            ay += out[1] * WANDER * (1 - a)
            // Stay over the meadow.
            if (py < BAND_TOP * h) ay += (BAND_TOP * h - py) * EDGE_PUSH
            if (py > BAND_BOTTOM * h) ay -= (py - BAND_BOTTOM * h) * EDGE_PUSH
            if (px < MARGIN) ax += (MARGIN - px) * EDGE_PUSH
            if (px > w - MARGIN) ax -= (px - (w - MARGIN)) * EDGE_PUSH
            // Orbit the finger, each on its own radius.
            if (a > 0.001f && hush == 0f) {
                val dx = px - fx
                val dy = py - fy
                val r = hypot(dx, dy).let { if (it == 0f) 1f else it }
                val spin = t.spin[i]
                val radial = ((t.orbit[i] - r) * 2.4f).coerceIn(-RUSH, RUSH)
                val wantX = (-dy / r) * ORBIT_SPEED * spin + (dx / r) * radial
                val wantY = (dx / r) * ORBIT_SPEED * spin + (dy / r) * radial
                ax += (wantX - pvx) * 3 * a
                ay += (wantY - pvy) * 3 * a
            } else if (hush > 0f) {
                // Held still: only the far ones drift in, so the swarm stays spread for the wave.
                val dx = fx - px
                val dy = fy - py
                val r = hypot(dx, dy).let { if (it == 0f) 1f else it }
                val inward = max(0f, r - HELD_REACH) * 0.6f
                ax += dx / r * inward
                ay += dy / r * inward
            }
            ax -= pvx * hush
            ay -= pvy * hush
            // Held home: a critically damped spring to the hovering spot.
            if (hm > 0.001f) {
                val kx = t.bobXK[i] * W0
                val ky = t.bobYK[i] * W0
                val tx = t.homeX[i] * w + t.bobX[i] * sin(kx * now + t.bobXP[i])
                val ty = t.homeY[i] * h + t.bobY[i] * sin(ky * now + t.bobYP[i])
                val tvx = t.bobX[i] * kx * cos(kx * now + t.bobXP[i])
                val tvy = t.bobY[i] * ky * cos(ky * now + t.bobYP[i])
                val hx = HOME_OMEGA * HOME_OMEGA * (tx - px) + 2 * HOME_OMEGA * (tvx - pvx)
                val hy = HOME_OMEGA * HOME_OMEGA * (ty - py) + 2 * HOME_OMEGA * (tvy - pvy)
                ax = ax * (1 - hm) + hx * hm
                ay = ay * (1 - hm) + hy * hm
            }
            var nvx = pvx + ax * STEP
            var nvy = pvy + ay * STEP
            // A flick may throw them faster for a moment; drag brings them back under the cap.
            val speed = hypot(nvx, nvy)
            val cap = maxSpeed + (1000 - maxSpeed) * hm
            if (speed > cap) {
                val k = max(cap / speed, exp(-3 * STEP))
                nvx *= k
                nvy *= k
            }
            vx[i] = nvx
            vy[i] = nvy
            x[i] = px + nvx * STEP
            y[i] = py + nvy * STEP
            // Blink drift: pulled towards the travelling wave while synced, back to its own rhythm after.
            val own = t.blinkP[i] + (t.blinkK[i] / LOOP) * now
            var d = drift[i]
            if (s > 0.001f) {
                val dist = hypot(px - syncX, py - syncY)
                val wave = SYNC_RATE * now - dist / (WAVE_SPEED / SYNC_RATE) * SYNC_RATE
                d += (s * SYNC_PULL * sin(TAU * (wave - own - d))) / TAU * STEP
            }
            // JavaScript's Math.round: halves go up.
            d -= (1 - s) * DRIFT_BACK * (d - floor(d + 0.5f)) * STEP
            drift[i] = d
        }

        // The grass under the swarm bends with the fireflies passing low over it.
        push.fill(0f)
        for (i in 0 until COUNT) {
            val low = clamp01((y[i] - h * 0.6f) / (h * 0.2f))
            if (low <= 0f) continue
            val k = floor(x[i] / BUCKET).toInt()
            if (k < 0 || k >= bend.size) continue
            push[k] += vx[i] * low * BEND_GAIN
        }
        for (k in bend.indices) {
            val left = if (k > 0) push[k - 1] else 0f
            val right = if (k + 1 < push.size) push[k + 1] else 0f
            val target = (push[k] + 0.4f * (left + right)).coerceIn(-BEND_MAX, BEND_MAX)
            bendSpeed[k] += (-BEND_OMEGA * BEND_OMEGA * (bend[k] - target) - 2 * BEND_DAMPING * BEND_OMEGA * bendSpeed[k]) * STEP
            bend[k] += bendSpeed[k] * STEP
        }
    }
}
