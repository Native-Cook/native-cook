package com.ngocdevv.nativecook.blocks.playground.dominochain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// The camera looks down at the table at PITCH, from DISTANCE away, with focal length FOCAL (dp).
// Its aim point sits AIM_Z into the table, and shows on screen at AIM_AT of the height.
private const val PITCH = 52 * PI / 180
private const val DISTANCE = 820.0
internal const val FOCAL = 960.0
private const val AIM_Z = 30.0
private const val AIM_AT = 0.56
// The far rail of the table shows this far below the top inset, dp.
internal const val RAIL_AT = 150.0
internal const val RAIL_HEIGHT = 12.0
internal const val RAIL_DEPTH = 18.0

// The autoPlay spiral, in table units: centre, outer and inner radius, turns and start angle.
internal const val SPIRAL_X = 0.0
internal const val SPIRAL_Z = -5.0
private const val SPIRAL_OUTER = 146.0
private const val SPIRAL_INNER = 32.0
private const val SPIRAL_TURNS = 2.05
private const val SPIRAL_START = PI * 0.62

// A finger counts as on a domino within this distance of its top, dp.
private const val HIT = 30.0
// A flick tips a domino when it moves along the domino's heading at least this fast, dp/s.
private const val FLICK_SPEED = 260.0
// A drag this long across a fallen run stands it back up, dp.
private const val SWEEP_DISTANCE = 90.0
// While a path is drawn, its points are kept at least this far apart (table units), and the
// finger is smoothed over this share of the way each move.
private const val PATH_STEP = 3.0
private const val PATH_SMOOTH = 0.55
// New dominoes pop up at least this long after the one before, s.
private const val POP_GAP = 0.03
// One click (light haptic) every this many hits.
private const val CLICK_EVERY = 3

private const val TICK = 1.0 / 120
private const val MAX_TICKS = 4

// autoPlay, s: the spiral is drawn, tipped, swept back up and cleared.
private const val LOOP = 12.0
private const val DRAW_FROM = 0.4
private const val DRAW_TO = 2.6
private const val TAP_AT = 3.2
private const val SWEEP_FROM = 8.0
private const val SWEEP_TO = 8.5
private const val CLEAR_AT = 10.8
// A touch pauses autoPlay this long, s.
private const val USER_HOLD = 4.0

// What happened during a frame, for haptics and the hint.
internal const val EVENT_CLICK = 1
internal const val EVENT_TIPPED = 2
internal const val EVENT_RAISED = 4
internal const val EVENT_FALLEN = 8

private val SinP = sin(PITCH)
private val CosP = cos(PITCH)
internal val CamY = DISTANCE * SinP
internal val CamZ = AIM_Z - DISTANCE * CosP

/** Whether a time `at` was passed going from `before` to `t` on a looping clock. */
private fun crossed(before: Double, t: Double, at: Double) =
    (before < at && t >= at) || (t < before && (at > before || at <= t))

/** `x || 1` of JavaScript, for a length that may be zero. */
private fun orOne(v: Double) = if (v == 0.0 || v.isNaN()) 1.0 else v

/** Where the table sits on the screen: the camera's aim point and the far rail. */
internal class DominoLayout(val width: Double, val height: Double, val railY: Double) {
    val cx = width / 2
    val cy = height * AIM_AT

    /** The table's far edge, in table units, from where the rail shows on screen. */
    val farZ: Double = if (width == 0.0) {
        400.0
    } else {
        val ys = -(railY - cy) / FOCAL
        val dy = ys * CosP - SinP
        val dz = ys * SinP + CosP
        if (dy >= -1e-4) 400.0 else CamZ + dz * (-CamY / dy)
    }

    /** Screen x, y and depth of a point on or above the table, into [out]. */
    fun project(x: Double, y: Double, z: Double, out: DoubleArray) {
        val py = y - CamY
        val pz = z - CamZ
        val yc = py * CosP + pz * SinP
        val zc = -py * SinP + pz * CosP
        out[0] = cx + (FOCAL * x) / zc
        out[1] = cy - (FOCAL * yc) / zc
        out[2] = zc
    }

    /** The point on the table under a screen point into [out] (x, z); false above the horizon. */
    fun unproject(sx: Double, sy: Double, out: DoubleArray): Boolean {
        val xs = (sx - cx) / FOCAL
        val ys = -(sy - cy) / FOCAL
        val dx = xs
        val dy = ys * CosP - SinP
        val dz = ys * SinP + CosP
        if (dy >= -1e-4) return false
        val t = -CamY / dy
        out[0] = dx * t
        out[1] = CamZ + dz * t
        return true
    }
}

/**
 * The frame loop of the React Native block (its `useFrameCallback`, script and gestures), step for
 * step: two runs (the live one and the one sinking away), the path being drawn, the finger and the
 * autoPlay clock on one fixed tick. No Android here: it compiles on its own.
 */
internal class DominoEngine(auto: Boolean, val reduced: Boolean) {
    var layout = DominoLayout(0.0, 0.0, 0.0)
    var started = false
        private set

    val runs = arrayOf(DominoRun(), DominoRun())
    /** Which run is the live one; the other may still be sinking away. */
    var live = 0
        private set
    /** The path being drawn (table units), the smoothed finger and the arc length placed so far. */
    val path = Polyline()
    var drawing = false
        private set
    private var smoothX = 0.0
    private var smoothZ = 0.0
    private var placed = 0.0
    /** Arc length of the path drawn so far (table units). */
    private var pathLength = 0.0
    private var lastBorn = 0.0
    /** When the drawn path was finished, for its fading trace. */
    var traceEnd = -10.0
        private set

    private var acc = 0.0
    private var clicks = 0
    var wasFallen = false
        private set

    // The finger: what it is doing (0 none, 1 drawing, 2 flicking, 3 sweeping), where it is, and the
    // domino a flick started on. autoPlay drives the same finger, and shows it.
    private var mode = 0
    var fingerX = 0.0
        private set
    var fingerY = 0.0
        private set
    private var flickOn = -1
    private var swept = false
    var ghost = 0.0
        private set
    private var ghostOn = false

    private var clock = 0.0
    private var hold = 0.0
    private var looping = auto

    /** What happened since the events were last read (EVENT_… bits). */
    var events = 0

    private val spiral = Polyline.spiral(SPIRAL_X, SPIRAL_Z, SPIRAL_OUTER, SPIRAL_INNER, SPIRAL_TURNS, SPIRAL_START)
    private val spiralLength = spiral.length()
    private val pa = DoubleArray(4)
    private val pb = DoubleArray(4)
    private val pp = DoubleArray(4)
    private val scr = DoubleArray(3)
    private val scr2 = DoubleArray(3)

    val liveRun: DominoRun get() = runs[live]

    /** Restarts the autoPlay clock (autoPlay switched on or off). */
    fun setAuto(auto: Boolean) {
        looping = auto
        clock = 0.0
        hold = 0.0
    }

    /** Lays the spiral at once (no pop). */
    private fun laySpiral(c: DominoRun) {
        var s = 0.0
        while (s <= spiralLength) {
            spiral.pointAt(max(0.0, s - 3), pa)
            spiral.pointAt(s + 3, pb)
            spiral.pointAt(s, pp)
            val hx = pb[0] - pa[0]
            val hz = pb[1] - pa[1]
            val l = orOne(hypot(hx, hz))
            c.add(pp[0], pp[1], hx / l, hz / l, -1.0)
            s += SPACING
        }
    }

    // ─── Actions, shared by the finger and the script ────────────────────────────────────────────

    /** The live run's standing domino nearest a screen point (its top), or -1. */
    private fun dominoAt(sx: Double, sy: Double): Int {
        val c = runs[live]
        var best = -1
        var bestD = HIT
        for (i in 0 until c.count) {
            if (c.sink[i] != Double.POSITIVE_INFINITY) continue
            layout.project(c.x[i], TILE_H * 0.6, c.z[i], scr)
            val d = hypot(scr[0] - sx, scr[1] - sy)
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        return best
    }

    private fun beginDraw(sx: Double, sy: Double): Boolean {
        if (!layout.unproject(sx, sy, scr) || scr[1] > layout.farZ - TILE_H) return false
        val ax = scr[0]
        val az = scr[1]
        // The old run sinks away; the new one is laid in the other run.
        runs[live].sinkAll()
        live = 1 - live
        val c = runs[live]
        c.count = 0
        c.hits = 0
        path.clear()
        path.add(ax, az)
        smoothX = ax
        smoothZ = az
        placed = 0.0
        pathLength = 0.0
        lastBorn = c.time - 1
        drawing = true
        traceEnd = Double.POSITIVE_INFINITY
        clicks = 0
        return true
    }

    private fun extendDraw(sx: Double, sy: Double) {
        if (!drawing) return
        if (!layout.unproject(sx, sy, scr)) return
        val z = min(scr[1], layout.farZ - TILE_H)
        smoothX += (scr[0] - smoothX) * PATH_SMOOTH
        smoothZ += (z - smoothZ) * PATH_SMOOTH
        val n = path.size
        if (n == 0) return
        val c = runs[live]
        // The run is full: the path (and its cost per move) stops growing.
        if (c.count >= MAX_DOMINOES || n >= MAX_PATH) return
        val step = hypot(smoothX - path.xs[n - 1], smoothZ - path.zs[n - 1])
        if (!(step >= PATH_STEP)) return
        path.add(smoothX, smoothZ)
        pathLength += step
        val total = pathLength
        // Place a domino every SPACING along the path, a little behind the finger so its heading is known.
        while (placed + 4 <= total) {
            path.pointAt(placed, pp)
            path.pointAt(max(0.0, placed - 4), pa)
            path.pointAt(placed + 4, pb)
            val hx = pb[0] - pa[0]
            val hz = pb[1] - pa[1]
            val l = orOne(hypot(hx, hz))
            val delay = max(0.0, lastBorn + POP_GAP - c.time)
            if (!c.add(pp[0], pp[1], hx / l, hz / l, delay)) break
            lastBorn = c.time + delay
            // The one before now knows where its path goes next.
            if (c.count > 1) {
                val k = c.count - 2
                val dx = c.x[k + 1] - c.x[k]
                val dz = c.z[k + 1] - c.z[k]
                val dl = orOne(hypot(dx, dz))
                val mx = (c.hx[k] + dx / dl) / 2
                val mz = (c.hz[k] + dz / dl) / 2
                val hl = hypot(mx, mz)
                // A hairpin can cancel the two headings out: then it faces the next one.
                if (hl > 1e-6) c.aim(k, mx / hl, mz / hl) else c.aim(k, dx / dl, dz / dl)
            }
            placed += SPACING
        }
    }

    private fun endDraw() {
        if (!drawing) return
        drawing = false
        traceEnd = runs[live].time
        // Too short to be a run: it sinks away again.
        if (runs[live].count < 2) runs[live].sinkAll()
    }

    private fun push(i: Int, spin: Double) {
        if (runs[live].push(i, spin)) events = events or EVENT_TIPPED
    }

    private fun sweep() {
        val c = runs[live]
        if (!c.anyFallen) return
        c.raise(reduced)
        c.hits = 0
        clicks = 0
        events = events or EVENT_RAISED
    }

    /** One step of the autoPlay script, from `before` to `t` s into the loop. */
    private fun script(before: Double, t: Double) {
        if (t >= DRAW_FROM && t <= DRAW_TO) {
            // The finger traces the spiral from its outer end inwards.
            val k = (t - DRAW_FROM) / (DRAW_TO - DRAW_FROM)
            val e = if (k < 0.5) 2 * k * k else 1 - 2 * (1 - k) * (1 - k)
            spiral.pointAt(e * (spiralLength + 6), pp)
            val atX = pp[0]
            val atZ = pp[1]
            layout.project(atX, 0.0, atZ, scr2)
            fingerX = scr2[0]
            fingerY = scr2[1]
            ghostOn = true
            if (crossed(before, t, DRAW_FROM) || !drawing) {
                // Start exactly on the spiral, with no smoothing lag.
                beginDraw(scr2[0], scr2[1])
                smoothX = atX
                smoothZ = atZ
                path.clear()
                path.add(atX, atZ)
                pathLength = 0.0
            } else {
                extendDraw(scr2[0], scr2[1])
            }
        } else if (drawing && t > DRAW_TO) {
            // Finish the tail exactly, then lift.
            spiral.pointAt(spiralLength + 6, pp)
            layout.project(pp[0], 0.0, pp[1], scr2)
            extendDraw(scr2[0], scr2[1])
            endDraw()
            ghostOn = false
        }
        val c = runs[live]
        if (t >= TAP_AT - 0.35 && t < TAP_AT + 0.2 && c.count > 0) {
            layout.project(c.x[0], TILE_H * 0.6, c.z[0], scr2)
            fingerX = scr2[0]
            fingerY = scr2[1]
            ghostOn = t < TAP_AT
        }
        if (crossed(before, t, TAP_AT)) push(0, PUSH)
        if (t >= SWEEP_FROM && t <= SWEEP_TO) {
            val k = (t - SWEEP_FROM) / (SWEEP_TO - SWEEP_FROM)
            fingerX = layout.width * (0.12 + 0.76 * k)
            fingerY = layout.height * 0.78 - 30 * sin(k * PI)
            ghostOn = true
            if (k > 0.45 && c.anyFallen && !c.busy) sweep()
        } else if (t > SWEEP_TO && t < SWEEP_TO + 0.1) {
            ghostOn = false
        }
        if (crossed(before, t, CLEAR_AT)) c.sinkAll()
    }

    /** One frame `frameDt` s after the last one. */
    fun frame(frameDt: Double) {
        if (layout.width == 0.0) return
        if (!started) {
            started = true
            // Without autoPlay the table starts with the spiral standing, ready to tip.
            if (!looping) laySpiral(runs[0])
        }
        val dt = min(frameDt, MAX_TICKS * TICK)
        var pending = acc + dt
        var ticks = 0
        while (pending >= TICK && ticks < MAX_TICKS) {
            pending -= TICK
            ticks += 1
            if (hold > 0) {
                if (mode == 0) hold -= TICK
                if (hold <= 0) clock = 0.0
            } else if (looping) {
                val before = clock
                val t = (before + TICK) % LOOP
                clock = t
                script(before, t)
            }
            runs[0].step(TICK)
            runs[1].step(TICK)
        }
        acc = min(pending, TICK)
        ghost += ((if (ghostOn) 1.0 else 0.0) - ghost) * min(1.0, dt * 16)

        // A click every few hits, on the frame the hit lands.
        val c = runs[live]
        val due = c.hits / CLICK_EVERY
        if (due > clicks) {
            clicks = due
            events = events or EVENT_CLICK
        }
        val down = c.anyFallen
        if (down != wasFallen) {
            wasFallen = down
            events = events or EVENT_FALLEN
        }
    }

    // ─── Gestures ────────────────────────────────────────────────────────────────────────────────

    private fun touched() {
        hold = USER_HOLD
        ghostOn = false
        if (drawing && mode == 0) endDraw()
    }

    /** A drag began at (x0, y0) and is now at (x, y). */
    fun panStart(x0: Double, y0: Double, x: Double, y: Double) {
        if (!started) return
        touched()
        val c = runs[live]
        val falling = c.busy
        if (c.anyFallen) {
            mode = if (falling) 0 else 3
            swept = false
            return
        }
        val hit = dominoAt(x0, y0)
        if (hit >= 0) {
            mode = 2
            flickOn = hit
            return
        }
        if (beginDraw(x0, y0)) {
            mode = 1
            extendDraw(x, y)
        } else {
            mode = 0
        }
    }

    fun panMove(x: Double, y: Double, translationX: Double, translationY: Double) {
        if (!started) return
        if (mode == 1) {
            extendDraw(x, y)
        } else if (mode == 3 && !swept && hypot(translationX, translationY) > SWEEP_DISTANCE) {
            swept = true
            sweep()
        }
    }

    /** The drag ended with the finger moving at (vx, vy) dp/s (`cancelled` when it did not end cleanly). */
    fun panEnd(velocityX: Double, velocityY: Double, cancelled: Boolean = false) {
        if (!started) return
        if (mode == 2 && !cancelled) {
            // A flick along the domino's heading tips it, harder for a faster flick.
            val c = runs[live]
            val i = flickOn
            if (i < 0 || i >= c.count) {
                mode = 0
                return
            }
            layout.project(c.x[i], 0.0, c.z[i], scr)
            layout.project(c.x[i] + c.hx[i] * 10, 0.0, c.z[i] + c.hz[i] * 10, scr2)
            val dx = scr2[0] - scr[0]
            val dy = scr2[1] - scr[1]
            val l = orOne(hypot(dx, dy))
            val along = (velocityX * dx + velocityY * dy) / l
            if (along > FLICK_SPEED) push(i, PUSH * min(1.8, max(1.0, along / 700)))
        }
        if (mode == 1) endDraw()
        mode = 0
    }

    fun tap(x: Double, y: Double) {
        if (!started) return
        touched()
        val i = dominoAt(x, y)
        if (i >= 0) push(i, PUSH)
    }
}
