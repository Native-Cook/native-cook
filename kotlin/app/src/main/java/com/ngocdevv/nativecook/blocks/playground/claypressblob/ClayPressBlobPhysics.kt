package com.ngocdevv.nativecook.blocks.playground.claypressblob

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

// The clay's soft body: a closed ring of mass points around a pressure volume, stepped in fixed
// substeps once a frame. Units are dp (the React Native points) and seconds; every point has a mass
// of 1, so a force is an acceleration. Ported line for line from the React Native block's
// ClayPressBlobPhysics.ts.

/** Points around the ring. */
internal const val CLAY_POINTS = 48
// Fixed substep and the most frame time one frame may consume (a long hitch is cut short).
private const val SUBSTEP = 1.0 / 240
private const val MAX_FRAME = 1.0 / 30

// Neighbours are tied by springs of this stiffness (1/s²), damped along the edge.
private const val EDGE_K = 2600.0
private const val EDGE_DAMP = 6.0
// Every point is pulled toward its place on the rest shape around the centroid. Soft, so the clay
// wobbles at about 1.8 Hz; much stiffer while a pinch is holding it.
private const val SHAPE_K = 130.0
private const val PINCH_K = 420.0
// The trapped volume pushes out along each edge's normal as the area drops below its rest area.
private const val PRESSURE_K = 26000.0
// Bending stiffness along the rim (1/s²).
private const val BEND_K = 900.0
// Damping of each point's motion relative to the body (wobble), and of the body itself (air).
private const val INNER_DAMP = 3.4
private const val AIR_DAMP = 0.6
// The body drifts back to its home on a slightly underdamped spring.
private const val HOME_K = 11.0
private const val HOME_DAMP = 4.6
// Soft walls: a point that comes within WALL_CUSHION dp of one meets a damped spring that grows
// from nothing across that cushion and stiffens past it (WALL_K × (1 + (depth / WALL_GIVE)²)), so
// a thrown body slows before the wall and flattens against it into a rounded squash, never a cut.
// WALL_GRIP drags lightly along the wall. Only a far safety rail, WALL_RAIL dp past the wall,
// holds a point that something has thrown wildly.
private const val WALL_K = 2200.0
private const val WALL_DAMP = 40.0
private const val WALL_GRIP = 4.0
private const val WALL_GIVE = 12.0
private const val WALL_CUSHION = 28.0
private const val WALL_RAIL = 80.0

// Plasticity: a point pushed more than YIELD dp off its rest radius drags the rest radius after
// it; the marks creep back round and are smoothed along the ring.
private const val YIELD = 3.0
private const val YIELD_RATE = 5.5
private const val RECOVER_RATE = 0.95
private const val SMOOTH_RATE = 2.2
// Pressing spreads the clay: the rest radius grows by up to SPREAD dp, most on the finger's side.
private const val SPREAD = 7.0

// A finger that lands outside the clay is a disc of FINGER_R dp that pushes the rim in on a soft
// contact spring (CONTACT_K, CONTACT_DAMP), never by moving points outright.
private const val FINGER_R = 24.0
private const val CONTACT_K = 2600.0
private const val CONTACT_DAMP = 30.0
// A finger pressed in near the rim notches it: rim points within the dent's reach are pulled in
// by up to NOTCH dp (on NOTCH_K), so a press at the edge dents the edge instead of pushing a lump.
private const val NOTCH = 11.0
private const val NOTCH_K = 260.0
private const val MOVE_SLOP = 9.0
private const val TAP_TIME = 0.26
// Drag: grab reach, the Gaussian patch, its spring and the rubber band's limit.
private const val GRAB_REACH = 46.0
private const val GRAB_SIGMA = 2.4
private const val GRAB_SPAN = 6
private const val GRAB_K = 950.0
private const val GRAB_DAMP = 38.0
private const val PULL_MAX = 130.0
// A pulled lump keeps WALL_ROOM dp inside the walls, easing into that limit rather than meeting it.
private const val WALL_ROOM = 26.0
// Throws.
private const val FLICK_SPEED = 700.0
private const val FLICK_SHARE = 0.45
private const val FLICK_MAX = 850.0
private const val THROW_KEEP = 0.1
// Pinch limits, and the share of the squeeze that stays.
private const val PINCH_MIN = 0.6
private const val PINCH_MAX = 1.5
private const val PINCH_SET = 0.3

// Dents.
internal const val CLAY_DENT_RADIUS = 30.0
private const val DENTS = 6
private const val DENT_GROW = 6.5
private const val DENT_HOLD = 0.9
private const val DENT_RECOVER = 2.6
private const val RIDGE_FADE = 1.5

private const val THUD_SPEED = 260.0
private const val RECOLOUR_TIME = 0.75

/** Bits in `events`, cleared by whoever reads them. */
internal const val CLAY_EVENT_PRESS = 1
internal const val CLAY_EVENT_RECOLOUR = 2
internal const val CLAY_EVENT_THUD = 4
internal const val CLAY_EVENT_RELEASE = 8

internal class ClayDent {
    var live = false
    /** Centre, relative to the body's centroid. */
    var x = 0.0
    var y = 0.0
    var depth = 0.0
    var ridge = 0.0
    var growing = false
    /** Seconds since the finger lifted. */
    var since = 0.0
    /** Seeds the fingerprint's tilt and breaks. */
    var seed = 0.0
}

internal class ClayFinger {
    var id = -1L
    var down = false
    /** A scripted finger from autoPlay: no haptics, and a tap sets `tapColour` instead of cycling. */
    var scripted = false
    var tapColour = -1
    var x = 0.0
    var y = 0.0
    var startX = 0.0
    var startY = 0.0
    var startT = 0.0
    var lastX = 0.0
    var lastY = 0.0
    var lastT = 0.0
    var vx = 0.0
    var vy = 0.0
    var moved = false
    var dent = -1
}

private fun clamp(v: Double, lo: Double, hi: Double) = min(hi, max(lo, v))

private fun wrap(i: Int, n: Int) = ((i % n) + n) % n

/** A rubber band: follows a short pull one to one and never goes past `max`. */
private fun rubber(d: Double, max: Double) = max * (1 - exp(-d / max))

/** How much a wall holds a point `d` dp into its cushion: 0 at the cushion's edge, 1 at the wall. */
private fun wallHold(d: Double) = min(1.0, d / WALL_CUSHION)

/** The wall's stiffness `d` dp into its cushion: growing across it, stiffening past the wall. */
private fun wallStiffness(d: Double): Double {
    val past = max(0.0, d - WALL_CUSHION) / WALL_GIVE
    return WALL_K * wallHold(d) * (1 + past * past)
}

/** `v` eased into [lo, hi]: unchanged well inside, approaching an edge without ever passing it. */
private fun softLimit(v: Double, lo: Double, hi: Double, ease: Double): Double = when {
    v > hi - ease -> hi - ease + ease * tanh((v - (hi - ease)) / ease)
    v < lo + ease -> lo + ease - ease * tanh((lo + ease - v) / ease)
    else -> v
}

/** A fresh, round body at rest in the middle of the stage. */
internal class ClaySim(
    width: Double,
    height: Double,
    val top: Double,
    val bottom: Double,
    val paletteSize: Int,
    /** Reduced motion: no throws, and the wobble dies out fast. */
    val calm: Boolean,
) {
    var time = 0.0
    var acc = 0.0
    val left = 20.0
    val right = width - 20
    val r0 = min(min(width * 0.3, (bottom - top) * 0.3), 132.0)
    val homeX = width / 2
    val homeY = (top + bottom) / 2
    private val cosA = DoubleArray(CLAY_POINTS) { cos(it.toDouble() / CLAY_POINTS * PI * 2) }
    private val sinA = DoubleArray(CLAY_POINTS) { sin(it.toDouble() / CLAY_POINTS * PI * 2) }
    val x = DoubleArray(CLAY_POINTS) { homeX + cosA[it] * r0 }
    val y = DoubleArray(CLAY_POINTS) { homeY + sinA[it] * r0 }
    val vx = DoubleArray(CLAY_POINTS)
    val vy = DoubleArray(CLAY_POINTS)
    val rest = DoubleArray(CLAY_POINTS) { r0 }
    // Scratch for the forces of one substep.
    private val ax = DoubleArray(CLAY_POINTS)
    private val ay = DoubleArray(CLAY_POINTS)
    /** The round body's area, and what pressing has spread it by since. */
    private val baseArea = 0.5 * CLAY_POINTS * r0 * r0 * sin(2 * PI / CLAY_POINTS)
    private var spreadArea = 0.0
    private val edge0 = 2 * r0 * sin(PI / CLAY_POINTS)
    var cx = homeX
    var cy = homeY
    var cvx = 0.0
    var cvy = 0.0
    val fingers = arrayOf(ClayFinger(), ClayFinger())
    /** 0 none, 1 press, 2 drag, 3 pinch. */
    var mode = 0
    private var grab = -1
    private val grabFromX = DoubleArray(CLAY_POINTS)
    private val grabFromY = DoubleArray(CLAY_POINTS)
    // Scratch: each point's place on the rest shape this substep.
    private val tx = DoubleArray(CLAY_POINTS)
    private val ty = DoubleArray(CLAY_POINTS)
    private var pinchFrom = 1.0
    private var pinchScale = 1.0
    private var pinchUx = 1.0
    private var pinchUy = 0.0
    private var pressSide = 0.0
    private var flying = false
    private var throwByUser = false
    private var thudAt = -10.0
    val dents = Array(DENTS) { ClayDent() }
    var colourFrom = 0
    var colourTo = 0
    var recolour = 1.0
    var recolourX = homeX
    var recolourY = homeY
    var events = 0
    /** The user (not autoPlay) caused the events in `events`. */
    var eventsByUser = false

    /** Is (px, py) inside the ring? An even-odd ray cast over the points. */
    fun contains(px: Double, py: Double): Boolean {
        var inside = false
        var j = CLAY_POINTS - 1
        for (i in 0 until CLAY_POINTS) {
            val yi = y[i]
            val yj = y[j]
            if ((yi > py) != (yj > py) && px < (x[j] - x[i]) * (py - yi) / (yj - yi) + x[i]) inside = !inside
            j = i
        }
        return inside
    }

    fun centroid() {
        var sx = 0.0
        var sy = 0.0
        var svx = 0.0
        var svy = 0.0
        for (i in 0 until CLAY_POINTS) {
            sx += x[i]
            sy += y[i]
            svx += vx[i]
            svy += vy[i]
        }
        cx = sx / CLAY_POINTS
        cy = sy / CLAY_POINTS
        cvx = svx / CLAY_POINTS
        cvy = svy / CLAY_POINTS
    }

    private fun signedArea(): Double {
        var a = 0.0
        for (i in 0 until CLAY_POINTS) {
            val j = (i + 1) % CLAY_POINTS
            a += x[i] * y[j] - x[j] * y[i]
        }
        return a / 2
    }

    private fun startDent(px: Double, py: Double): Int {
        var slot = 0
        var shallowest = Double.POSITIVE_INFINITY
        for (i in 0 until DENTS) {
            val d = dents[i]
            if (!d.live) {
                slot = i
                shallowest = -1.0
                break
            }
            if (d.depth < shallowest) {
                shallowest = d.depth
                slot = i
            }
        }
        val d = dents[slot]
        d.live = true
        d.x = px - cx
        d.y = py - cy
        d.depth = 0.0
        d.ridge = 0.0
        d.growing = true
        d.since = 0.0
        // A deterministic seed from where it landed.
        d.seed = abs(sin(px * 12.9898 + py * 78.233) * 43758.5453) % 1.0
        return slot
    }

    private fun stopDent(slot: Int) {
        if (slot < 0) return
        val d = dents[slot]
        d.growing = false
        d.since = 0.0
        // A touch too short to leave a mark leaves none.
        if (d.depth < 0.06) d.live = false
    }

    private fun fingerOf(id: Long) = fingers.firstOrNull { it.down && it.id == id }

    private fun downCount() = fingers.count { it.down }

    private fun beginPinch() {
        val a = fingers[0]
        val b = fingers[1]
        if (mode == 1 || mode == 2) for (f in fingers) stopDent(f.dent)
        mode = 3
        grab = -1
        pinchFrom = max(20.0, hypot(b.x - a.x, b.y - a.y))
        pinchScale = 1.0
    }

    /** A finger lands. `scripted` marks autoPlay's own fingers. */
    fun touchDown(id: Long, px: Double, py: Double, scripted: Boolean, tapColour: Int) {
        val f = fingers.firstOrNull { !it.down } ?: return
        f.id = id
        f.down = true
        f.scripted = scripted
        f.tapColour = tapColour
        f.x = px
        f.y = py
        f.startX = px
        f.startY = py
        f.startT = time
        f.lastX = px
        f.lastY = py
        f.lastT = time
        f.vx = 0.0
        f.vy = 0.0
        f.moved = false
        f.dent = -1
        flying = false
        if (downCount() == 2) {
            beginPinch()
            return
        }
        mode = 1
        grab = -1
        if (contains(px, py)) {
            f.dent = startDent(px, py)
            // The side of the body the finger is on, as an angle, for the spread.
            pressSide = atan2(py - cy, px - cx)
            events = events or CLAY_EVENT_PRESS
            eventsByUser = !scripted
        }
    }

    /** The ring patch a drag takes hold of, or -1 for none. */
    private fun pickGrab(f: ClayFinger): Int {
        var nearest = 0
        var nearestD = Double.POSITIVE_INFINITY
        for (i in 0 until CLAY_POINTS) {
            val d = (x[i] - f.startX) * (x[i] - f.startX) + (y[i] - f.startY) * (y[i] - f.startY)
            if (d < nearestD) {
                nearestD = d
                nearest = i
            }
        }
        if (sqrt(nearestD) < GRAB_REACH) return nearest
        if (!contains(f.startX, f.startY)) return -1
        // From inside, the side the finger pulls toward.
        val dx = f.x - f.startX
        val dy = f.y - f.startY
        val len = hypot(dx, dy).let { if (it == 0.0) 1.0 else it }
        var best = -1
        var bestDot = Double.NEGATIVE_INFINITY
        for (i in 0 until CLAY_POINTS) {
            val ox = x[i] - cx
            val oy = y[i] - cy
            val ol = hypot(ox, oy).let { if (it == 0.0) 1.0 else it }
            val dot = (ox * dx + oy * dy) / (ol * len)
            if (dot > bestDot) {
                bestDot = dot
                best = i
            }
        }
        return best
    }

    fun touchMove(id: Long, px: Double, py: Double) {
        val f = fingerOf(id) ?: return
        f.x = px
        f.y = py
        // The finger's speed, smoothed over the moves that span at least 4 ms.
        val dt = time - f.lastT
        if (dt > 0.004) {
            f.vx = f.vx * 0.35 + (px - f.lastX) / dt * 0.65
            f.vy = f.vy * 0.35 + (py - f.lastY) / dt * 0.65
            f.lastX = px
            f.lastY = py
            f.lastT = time
        }
        if (!f.moved && hypot(px - f.startX, py - f.startY) > MOVE_SLOP) {
            f.moved = true
            if (mode == 1) {
                stopDent(f.dent)
                mode = 2
                grab = pickGrab(f)
                x.copyInto(grabFromX)
                y.copyInto(grabFromY)
            }
        }
    }

    /** Starts the colour change to palette entry `to`, spreading from (px, py). */
    fun recolourTo(to: Int, px: Double, py: Double) {
        if (to == colourTo && recolour >= 1) return
        // A change caught half way finishes at once, so the new one starts from a single colour.
        colourFrom = if (recolour < 0.5) colourFrom else colourTo
        colourTo = to
        recolour = 0.0
        recolourX = px - cx
        recolourY = py - cy
    }

    /** A pinch leaves PINCH_SET of its squeeze in the clay, which then creeps back round. */
    private fun setPinch() {
        val sa = pinchScale
        val sb = 1 / sqrt(sa)
        val ux = pinchUx
        val uy = pinchUy
        for (i in 0 until CLAY_POINTS) {
            val ox = cosA[i]
            val oy = sinA[i]
            val along = (ox * ux + oy * uy) * sa
            val across = (-ox * uy + oy * ux) * sb
            val squeezed = rest[i] * hypot(along, across)
            rest[i] += (squeezed - rest[i]) * PINCH_SET
        }
    }

    /** A finger lifts. Ends a press, a tap, a drag (maybe a throw) or a pinch. */
    fun touchUp(id: Long, cancelled: Boolean) {
        val f = fingerOf(id) ?: return
        f.down = false
        stopDent(f.dent)
        f.dent = -1
        // A finger that stopped before it lifted throws nothing.
        val still = time - f.lastT > 0.08
        val fvx = if (still) 0.0 else f.vx
        val fvy = if (still) 0.0 else f.vy
        if (mode == 3) {
            // Once a pinch has started, the clay waits for every finger to lift.
            if (downCount() == 0) {
                setPinch()
                mode = 0
                events = events or CLAY_EVENT_RELEASE
                eventsByUser = !f.scripted
            }
            return
        }
        if (cancelled) {
            mode = 0
            grab = -1
            return
        }
        if (mode == 1 && !f.moved && time - f.startT < TAP_TIME) {
            val next = if (f.tapColour >= 0) f.tapColour else (colourTo + 1) % paletteSize
            recolourTo(next, f.x, f.y)
            events = events or CLAY_EVENT_RECOLOUR
            eventsByUser = !f.scripted
        }
        if (mode == 2) {
            val speed = hypot(fvx, fvy)
            if (grab >= 0 && speed > FLICK_SPEED && !calm) {
                val k = min(FLICK_MAX, speed * FLICK_SHARE) / speed
                // The pulled lump lets go of most of its own speed, so the body flies as one piece.
                centroid()
                for (i in 0 until CLAY_POINTS) {
                    vx[i] = cvx + (vx[i] - cvx) * THROW_KEEP + fvx * k
                    vy[i] = cvy + (vy[i] - cvy) * THROW_KEEP + fvy * k
                }
                flying = true
                throwByUser = !f.scripted
            }
            if (grab >= 0) {
                events = events or CLAY_EVENT_RELEASE
                eventsByUser = !f.scripted
            }
        }
        mode = 0
        grab = -1
    }

    /** How many real (not scripted) fingers are down. */
    fun userFingers() = fingers.count { it.down && !it.scripted }

    /** Lifts every scripted finger without a throw or a tap, when a real touch takes over from autoPlay. */
    fun cancelScripted() {
        for (f in fingers) if (f.down && f.scripted) touchUp(f.id, true)
    }

    /** One fixed substep of the body. */
    private fun substep(h: Double) {
        val n = CLAY_POINTS
        centroid()
        val area = signedArea()
        val restArea = baseArea + spreadArea
        val pressure = PRESSURE_K * (restArea / max(area, restArea * 0.2) - 1)
        val stiff = if (mode == 3) PINCH_K else SHAPE_K

        // The pinch as a stretch along its axis and the inverse squeeze across it.
        var sa = 1.0
        var sb = 1.0
        var ux = 1.0
        var uy = 0.0
        if (mode == 3) {
            val a = fingers[0]
            val b = fingers[1]
            val span = hypot(b.x - a.x, b.y - a.y)
            val raw = span / pinchFrom
            // Past its limits the squeeze follows at a quarter of the pull.
            sa = when {
                raw < PINCH_MIN -> PINCH_MIN - (PINCH_MIN - raw) * 0.25
                raw > PINCH_MAX -> PINCH_MAX + (raw - PINCH_MAX) * 0.25
                else -> raw
            }
            sb = 1 / sqrt(sa)
            val s = if (span == 0.0) 1.0 else span
            ux = (b.x - a.x) / s
            uy = (b.y - a.y) / s
            pinchScale = sa
            pinchUx = ux
            pinchUy = uy
        }

        val inner = if (calm) INNER_DAMP * 3 else INNER_DAMP
        for (i in 0 until n) {
            var ox = cosA[i] * rest[i]
            var oy = sinA[i] * rest[i]
            if (mode == 3) {
                val along = ox * ux + oy * uy
                val across = -ox * uy + oy * ux
                ox = ux * along * sa - uy * across * sb
                oy = uy * along * sa + ux * across * sb
            }
            tx[i] = cx + ox
            ty[i] = cy + oy
            var fx = (cx + ox - x[i]) * stiff
            var fy = (cy + oy - y[i]) * stiff
            // Pressure along the outward normal of the two edges that meet here.
            val p = (i + 1) % n
            val m = (i + n - 1) % n
            fx += pressure * (y[p] - y[m]) / (2 * edge0)
            fy += pressure * -(x[p] - x[m]) / (2 * edge0)
            // Internal and air damping, and the pull home.
            fx += -(vx[i] - cvx) * inner - vx[i] * AIR_DAMP + (homeX - cx) * HOME_K - cvx * HOME_DAMP
            fy += -(vy[i] - cvy) * inner - vy[i] * AIR_DAMP + (homeY - cy) * HOME_K - cvy * HOME_DAMP
            ax[i] = fx
            ay[i] = fy
        }
        // Bending: each point keeps the rest shape's bend with its neighbours.
        for (i in 0 until n) {
            val p = (i + 1) % n
            val m = (i + n - 1) % n
            ax[i] += ((x[p] + x[m] - 2 * x[i]) - (tx[p] + tx[m] - 2 * tx[i])) * BEND_K
            ay[i] += ((y[p] + y[m] - 2 * y[i]) - (ty[p] + ty[m] - 2 * ty[i])) * BEND_K
        }
        // Edge springs.
        for (i in 0 until n) {
            val j = (i + 1) % n
            val dx = x[j] - x[i]
            val dy = y[j] - y[i]
            val len = hypot(dx, dy).let { if (it == 0.0) 0.0001 else it }
            val restLen = edge0 * (rest[i] + rest[j]) / (2 * r0)
            val ex = dx / len
            val ey = dy / len
            val relV = (vx[j] - vx[i]) * ex + (vy[j] - vy[i]) * ey
            val f = (len - restLen) * EDGE_K + relV * EDGE_DAMP
            ax[i] += ex * f
            ay[i] += ey * f
            ax[j] -= ex * f
            ay[j] -= ey * f
        }
        // A dragged patch rides the finger, rubber-banded.
        if (mode == 2 && grab >= 0) {
            var f = if (fingers[0].down) fingers[0] else fingers[1]
            if (!f.down) f = fingers[0]
            val dx = f.x - f.startX
            val dy = f.y - f.startY
            val len = hypot(dx, dy)
            val k = if (len > 0) rubber(len, PULL_MAX) / len else 0.0
            for (o in -GRAB_SPAN..GRAB_SPAN) {
                val i = wrap(grab + o, n)
                val w = exp(-(o * o).toDouble() / (2 * GRAB_SIGMA * GRAB_SIGMA))
                val gx = softLimit(grabFromX[i] + dx * k, left + WALL_ROOM, right - WALL_ROOM, WALL_ROOM)
                val gy = softLimit(grabFromY[i] + dy * k, top + WALL_ROOM, bottom - WALL_ROOM, WALL_ROOM)
                ax[i] += w * ((gx - x[i]) * GRAB_K - vx[i] * GRAB_DAMP)
                ay[i] += w * ((gy - y[i]) * GRAB_K - vy[i] * GRAB_DAMP)
            }
        }
        // Soft walls: px, py are how deep a point is into a wall's cushion, signed the way it is pushed.
        var hit = 0.0
        val near = WALL_CUSHION
        for (i in 0 until n) {
            val px = if (x[i] < left + near) left + near - x[i] else if (x[i] > right - near) right - near - x[i] else 0.0
            val py = if (y[i] < top + near) top + near - y[i] else if (y[i] > bottom - near) bottom - near - y[i] else 0.0
            if (px != 0.0) {
                hit = max(hit, -sign(px) * vx[i])
                val grip = wallHold(abs(px))
                ax[i] += px * wallStiffness(abs(px)) - (if (sign(px) * vx[i] < 0) vx[i] * WALL_DAMP * grip else 0.0)
                ay[i] -= vy[i] * WALL_GRIP * grip
            }
            if (py != 0.0) {
                hit = max(hit, -sign(py) * vy[i])
                val grip = wallHold(abs(py))
                ay[i] += py * wallStiffness(abs(py)) - (if (sign(py) * vy[i] < 0) vy[i] * WALL_DAMP * grip else 0.0)
                ax[i] -= vx[i] * WALL_GRIP * grip
            }
        }
        // A finger that landed outside the clay pushes the rim in on a soft contact spring.
        if (mode == 1) {
            for (f in fingers) {
                if (!f.down || f.dent >= 0) continue
                for (i in 0 until n) {
                    val dx = x[i] - f.x
                    val dy = y[i] - f.y
                    val d = hypot(dx, dy)
                    if (d < FINGER_R && d > 0.0001) {
                        val vn = (vx[i] * dx + vy[i] * dy) / d
                        val push = (FINGER_R - d) * CONTACT_K - (if (vn < 0) vn * CONTACT_DAMP else 0.0)
                        ax[i] += push * dx / d
                        ay[i] += push * dy / d
                    }
                }
            }
        }
        // A dent near the rim notches it: the rim points it reaches are drawn in toward the centre.
        for (dent in dents) {
            if (!dent.live || dent.depth < 0.02) continue
            val reach = hypot(dent.x, dent.y)
            val facing = atan2(dent.y, dent.x)
            val d = dent.depth * dent.depth * (3 - 2 * dent.depth)
            for (i in 0 until n) {
                val ang = i.toDouble() / n * PI * 2
                var da = ang - facing
                da -= floor(da / (PI * 2) + 0.5) * PI * 2
                val along = da * rest[i] / CLAY_DENT_RADIUS
                if (along > 2.6 || along < -2.6) continue
                val over = clamp((reach + CLAY_DENT_RADIUS * 0.9 - rest[i]) / (CLAY_DENT_RADIUS * 0.9), 0.0, 1.0)
                if (over <= 0) continue
                val pull = NOTCH * d * over * exp(-along * along) * NOTCH_K
                ax[i] -= cosA[i] * pull
                ay[i] -= sinA[i] * pull
            }
        }
        // Integrate (semi-implicit Euler).
        for (i in 0 until n) {
            vx[i] += ax[i] * h
            vy[i] += ay[i] * h
            x[i] += vx[i] * h
            y[i] += vy[i] * h
        }
        // The far safety rail, for a point thrown wildly; the soft walls do the real work.
        for (i in 0 until n) {
            x[i] = clamp(x[i], left - WALL_RAIL, right + WALL_RAIL)
            y[i] = clamp(y[i], top - WALL_RAIL, bottom + WALL_RAIL)
        }
        if (flying && hit > THUD_SPEED && time - thudAt > 0.4) {
            thudAt = time
            flying = false
            events = events or CLAY_EVENT_THUD
            eventsByUser = throwByUser
        }

        // Plasticity: rest radii yield to big pushes, creep back round and smooth along the ring.
        val next = ax
        for (i in 0 until n) {
            val d = (x[i] - cx) * cosA[i] + (y[i] - cy) * sinA[i]
            val e = d - rest[i]
            var r = rest[i]
            // A pinch holds the clay to its own shape; what it leaves behind is set when it lets go.
            if (mode == 3) {
                next[i] = r
                continue
            }
            if (e > YIELD) r += (e - YIELD) * YIELD_RATE * h else if (e < -YIELD) r += (e + YIELD) * YIELD_RATE * h
            r += (r0 - r) * RECOVER_RATE * h
            r += (rest[(i + 1) % n] + rest[(i + n - 1) % n] - 2 * rest[i]) * SMOOTH_RATE * h
            next[i] = r
        }
        // A press spreads the clay, most on the finger's side, and the volume with it.
        spreadArea -= spreadArea * RECOVER_RATE * h
        val wedge = sin(2 * PI / n)
        if (mode == 1) {
            for (f in fingers) {
                if (!f.down || f.dent < 0) continue
                val dent = dents[f.dent]
                if (!dent.growing) continue
                val rate = (1 - dent.depth) * DENT_GROW * h
                val off = hypot(dent.x, dent.y) / r0
                for (i in 0 until n) {
                    val ang = i.toDouble() / n * PI * 2
                    val facing = max(0.0, cos(ang - pressSide))
                    val grow = SPREAD * rate * (0.35 + 0.65 * facing * facing * off)
                    next[i] += grow
                    spreadArea += rest[i] * grow * wedge
                }
            }
        }
        for (i in 0 until n) rest[i] = clamp(next[i], r0 * 0.55, r0 * 1.7)
    }

    /** Advances dents, the colour change and the body by one frame of `frameDt` seconds. */
    fun step(frameDt: Double) {
        val dt = min(frameDt, MAX_FRAME)
        time += dt
        for (d in dents) {
            if (!d.live) continue
            if (d.growing) {
                d.depth += (1 - d.depth) * min(1.0, DENT_GROW * dt)
                d.ridge = d.depth
            } else {
                d.since += dt
                d.ridge = max(0.0, d.ridge - dt / RIDGE_FADE)
                if (d.since > DENT_HOLD) d.depth = max(0.0, d.depth - dt / DENT_RECOVER)
                if (d.depth <= 0) d.live = false
            }
        }
        if (recolour < 1) {
            recolour = min(1.0, recolour + dt / RECOLOUR_TIME)
            if (recolour >= 1) colourFrom = colourTo
        }
        acc += dt
        var steps = 0
        while (acc >= SUBSTEP && steps < 10) {
            substep(SUBSTEP)
            acc -= SUBSTEP
            steps += 1
        }
        centroid()
        // A guard: if anything ever went non-finite, the clay is laid back down round at home.
        if (!cx.isFinite() || !cy.isFinite() || !cvx.isFinite() || !cvy.isFinite()) heal()
    }

    /** Lays the clay back down round and still at home, keeping its colour. */
    private fun heal() {
        for (i in 0 until CLAY_POINTS) {
            x[i] = homeX + cosA[i] * r0
            y[i] = homeY + sinA[i] * r0
            vx[i] = 0.0
            vy[i] = 0.0
            rest[i] = r0
        }
        spreadArea = 0.0
        acc = 0.0
        mode = 0
        grab = -1
        flying = false
        for (f in fingers) f.down = false
        for (d in dents) d.live = false
        centroid()
    }
}
