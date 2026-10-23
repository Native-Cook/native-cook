package com.ngocdevv.nativecook.blocks.media.loupemagnifier

import android.graphics.Bitmap
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

// The glass: its radius, the width of its metal rim (dp), and its zoom range.
internal const val RADIUS = 92f
internal const val RIM = 13f
private const val ZOOM = 2.5f
private const val MIN_ZOOM = 1.5f
private const val MAX_ZOOM = 5f
// Barrel distortion: at the edge of the glass the photo is spread by this much more than in the
// middle; the colours split by up to FRINGE there, red outwards and blue inwards.
internal const val BARREL = 0.32f
internal const val FRINGE = 0.04f
// The photo outside the glass is dimmed to this.
internal const val DIM = 0.66f
// The loupe trails the finger on a spring (unit mass), and a release throws it THROW s ahead.
private const val STIFFNESS = 300f
private const val DAMPING = 25f
private const val THROW = 0.1f
private const val EDGE_BAND = 60f
// The loupe's middle stays this share of its radius inside the screen's edges.
private const val EDGE_KEEP = 0.62f
// Its highlight slides against its speed, up to TILT_MAX of the radius, and turns with it.
private const val TILT_PER_SPEED = 0.00012f
private const val TILT_MAX = 0.16f
// A tap elsewhere jumps the loupe there on an arc, lifting it towards the viewer on the way.
private const val JUMP_TIME = 0.52f
private const val JUMP_ARC = 0.35f
internal const val JUMP_LIFT = 0.08f
// A tap on the glass toggles between the resting zoom and a close look.
private const val CLOSE_ZOOM = 4f
private const val ZOOM_STIFFNESS = 220f
private const val ZOOM_DAMPING = 22f
// The loupe's shadow: offset and softness at rest and lifted.
internal val SHADOW = floatArrayOf(8f, 14f, 18f)
internal val SHADOW_LIFTED = floatArrayOf(18f, 30f, 34f)
private const val PAN_SLOP = 2f
private const val TAP_SECONDS = 0.3f

// The self-playing wander: the loupe glides from stop to stop on a smooth curve (TRAVEL s), zooming
// out a little on the way, and rests on each one (DWELL s). One of the moves is a jump.
private const val TRAVEL = 1.5f
private const val DWELL = 1.1f
private const val TRAVEL_ZOOM = 2.1f
private const val JUMP_LEG = 2
// After a touch the wander waits this long (s) before it starts again.
private const val USER_HOLD = 3.5f
internal const val IMAGE_FADE = 0.6f

internal fun clamp01(x: Float) = x.coerceIn(0f, 1f)
private fun easeInOut(p: Float) = if (p < 0.5f) 4f * p * p * p else 1f - (-2f * p + 2f).pow(3) / 2f

private fun rubber(x: Float, lo: Float, hi: Float, band: Float) = when {
    x < lo -> lo - band * (1f - 1f / ((lo - x) / band + 1f))
    x > hi -> hi + band * (1f - 1f / ((x - hi) / band + 1f))
    else -> x
}

/** Catmull-Rom through four values, at `t` between the middle two. */
private fun spline(a: Float, b: Float, c: Float, d: Float, t: Float): Float {
    val t2 = t * t
    val t3 = t2 * t
    return 0.5f * (2f * b + (c - a) * t + (2f * a - 5f * b + 4f * c - d) * t2 + (3f * b - a - 3f * c + d) * t3)
}

/** A point on a quadratic arc from a to b, bowed up by `bow` of their distance. */
private fun arc(ax: Float, ay: Float, bx: Float, by: Float, bow: Float, t: Float): FloatArray {
    val cx = (ax + bx) / 2f
    val cy = (ay + by) / 2f - hypot(bx - ax, by - ay) * bow
    val u = 1f - t
    return floatArrayOf(u * u * ax + 2f * u * t * cx + t * t * bx, u * u * ay + 2f * u * t * cy + t * t * by)
}

/** Where the wander stops, as shares of the screen, and the zoom at each stop. */
class LoupeStop(val x: Float, val y: Float, val zoom: Float)

private class Wander(val x: Float, val y: Float, val zoom: Float, val lift: Float, val jump: Boolean)

/**
 * Where the wander is at `t` seconds into the loop, in shares of the screen, with its zoom and how
 * lifted it is. Each stop is a dwell then a move to the next stop.
 */
private fun wander(stops: List<LoupeStop>, t: Float): Wander {
    val n = stops.size
    if (n == 0 || !t.isFinite()) return Wander(0.5f, 0.5f, ZOOM, 0f, false)
    val leg = TRAVEL + DWELL
    val i = Math.floorMod(floor(t / leg).toInt(), n)
    val local = t - floor(t / leg) * leg
    val a = stops[i]
    if (local < DWELL) return Wander(a.x, a.y, a.zoom, 0f, false)
    val p = (local - DWELL) / TRAVEL
    val e = easeInOut(p)
    val b = stops[(i + 1) % n]
    // Zoom eases out towards TRAVEL_ZOOM half way, then in to the next stop's.
    val zoom = if (p < 0.5f) a.zoom + (TRAVEL_ZOOM - a.zoom) * easeInOut(p * 2f) else TRAVEL_ZOOM + (b.zoom - TRAVEL_ZOOM) * easeInOut(p * 2f - 1f)
    if (i == JUMP_LEG) {
        val q = arc(a.x, a.y, b.x, b.y, JUMP_ARC, e)
        return Wander(q[0], q[1], zoom, sin(PI.toFloat() * p), true)
    }
    val z = stops[(i - 1 + n) % n]
    val c = stops[(i + 2) % n]
    return Wander(spline(z.x, a.x, b.x, c.x, e), spline(z.y, a.y, b.y, c.y, e), zoom, 0f, false)
}

internal class LoupePhoto(val bitmap: Bitmap, val at: Float)

/**
 * The loupe's state, stepped once a frame like the RN frame callback, and a stand-in for the React
 * Native gestures: a tap racing a pan (on the fingers' average) and a pinch run together.
 */
internal class LoupeEngine(route: List<LoupeStop>, private val tick: () -> Unit, private val land: () -> Unit) {
    // An empty route (never in the samples) falls back to the sample one's first stop.
    private val stops = route.ifEmpty { listOf(LoupeStop(0.42f, 0.44f, 2.6f)) }
    // The loupe's middle, where it is heading, and its speed (dp, dp/s).
    var x = 0f
    var y = 0f
    private var vx = 0f
    private var vy = 0f
    private var tx = 0f
    private var ty = 0f
    private var placed = false
    private var grabX = 0f
    private var grabY = 0f
    private var dragging = false
    // Zoom on a spring, and the pinch's starting zoom.
    var zoom = ZOOM
    private var zoomSpeed = 0f
    private var zoomTo = ZOOM
    private var zoomFrom = ZOOM
    // A jump under way: from, to, seconds since it began (−1 for none); and how lifted the loupe is.
    private var jump = floatArrayOf(0f, 0f, 0f, 0f)
    private var jumpT = -1f
    var lift = 0f
    // The highlight's slide, smoothed.
    var tiltX = 0f
    var tiltY = 0f
    private var script = 0f
    private var hold = 0f
    var elapsed = 0f
    var photo: LoupePhoto? = null
    var auto = false
    var reduced = false

    // The screen, in dp, and the safe area at its top and bottom.
    var width = 0f
    var height = 0f
    var top = 0f
    var bottom = 0f

    fun restartLoop() {
        script = 0f
        hold = 0f
    }

    /** Keeps the loupe's middle on the screen. */
    private fun inside(px: Float, py: Float): FloatArray {
        val m = RADIUS * EDGE_KEEP
        return floatArrayOf(px.coerceIn(m, max(m, width - m)), py.coerceIn(top + m, max(top + m, height - bottom - m)))
    }

    private fun touched() {
        hold = USER_HOLD
    }

    fun advance(dt: Float) {
        elapsed += dt
        if (width <= 0f) return
        if (!placed) {
            // Start resting on the first stop.
            x = stops[0].x * width
            y = stops[0].y * height
            tx = x
            ty = y
            zoom = if (auto) stops[0].zoom else ZOOM
            zoomTo = zoom
            placed = true
        }

        var lifted = 0f
        if (auto && hold <= 0f && !dragging) {
            val t = (script + dt) % (stops.size * (TRAVEL + DWELL))
            script = t
            val w = wander(stops, t)
            tx = w.x * width
            ty = w.y * height
            zoomTo = w.zoom
            if (w.jump) {
                // The jump leg flies on its own arc, not the spring.
                x = tx
                y = ty
                vx = 0f
                vy = 0f
                lifted = w.lift
            }
        } else if (hold > 0f && !dragging) {
            hold -= dt
            if (hold <= 0f) script = 0f
        }

        if (jumpT >= 0f) {
            val t = jumpT + dt
            val p = clamp01(t / JUMP_TIME)
            val q = arc(jump[0], jump[1], jump[2], jump[3], JUMP_ARC, easeInOut(p))
            x = q[0]
            y = q[1]
            lifted = sin(PI.toFloat() * p)
            if (p >= 1f) {
                jumpT = -1f
                tx = jump[2]
                ty = jump[3]
                vx = 0f
                vy = 0f
                land()
            } else {
                jumpT = t
            }
        } else if (!(auto && hold <= 0f && lifted > 0f)) {
            if (reduced) {
                x = tx
                y = ty
                vx = 0f
                vy = 0f
            } else {
                // Long frames are split in two: this stiff spring is not stable over a 64 ms step
                // (it would oscillate out to infinity at a sustained 15 fps). At 60 fps nothing changes.
                val steps = if (dt > 0.034f) 2 else 1
                val h = dt / steps
                repeat(steps) {
                    vx += (STIFFNESS * (tx - x) - DAMPING * vx) * h
                    vy += (STIFFNESS * (ty - y) - DAMPING * vy) * h
                    x += vx * h
                    y += vy * h
                }
            }
        }
        lift = lifted

        // The zoom springs to its target; each whole step it passes under a pinch ticks.
        val before = zoom
        zoomSpeed += (ZOOM_STIFFNESS * (zoomTo - zoom) - ZOOM_DAMPING * zoomSpeed) * dt
        zoom = if (reduced) zoomTo else zoom + zoomSpeed * dt

        // The highlight slides against the loupe's speed.
        val ax = if (reduced) 0f else (-vx * TILT_PER_SPEED * 10f).coerceIn(-TILT_MAX, TILT_MAX)
        val ay = if (reduced) 0f else (-vy * TILT_PER_SPEED * 10f).coerceIn(-TILT_MAX, TILT_MAX)
        val ease = 1f - exp(-dt / 0.1f)
        tiltX += (ax - tiltX) * ease
        tiltY += (ay - tiltY) * ease
        if (dragging && floor(before) != floor(zoom)) tick()
    }

    // MARK: touches

    private val fingers = ArrayList<FloatArray>()
    private val fingerIds = ArrayList<Long>()
    private var downTime = 0L
    private var originX = 0f
    private var originY = 0f
    private var offsetX = 0f
    private var offsetY = 0f
    private val samples = ArrayDeque<FloatArray>()
    private var panActive = false
    private var pinchActive = false
    private var tapFailed = false
    private var pinchDistance = 1f

    private fun centroid(): FloatArray {
        var sx = 0f
        var sy = 0f
        for (f in fingers) {
            sx += f[0]
            sy += f[1]
        }
        val n = max(1, fingers.size)
        return floatArrayOf(sx / n, sy / n)
    }

    /** The pan's position: the fingers' average, kept from jumping when a finger comes or goes. */
    private fun focus(): FloatArray {
        val c = centroid()
        return floatArrayOf(c[0] + offsetX, c[1] + offsetY)
    }

    private fun velocity(): FloatArray {
        val first = samples.firstOrNull() ?: return floatArrayOf(0f, 0f)
        val last = samples.last()
        val span = last[0] - first[0]
        if (span < 0.001f) return floatArrayOf(0f, 0f)
        return floatArrayOf((last[1] - first[1]) / span, (last[2] - first[2]) / span)
    }

    // Sample times count from here: seconds of uptime in a Float lose their milliseconds.
    private val clockBase = System.nanoTime()

    private fun sample() {
        val t = (System.nanoTime() - clockBase) / 1e9f
        val f = focus()
        samples.addLast(floatArrayOf(t, f[0], f[1]))
        while (samples.isNotEmpty() && t - samples.first()[0] > 0.1f) samples.removeFirst()
    }

    private inline fun rebase(change: () -> Unit) {
        val before = focus()
        change()
        val c = centroid()
        offsetX = before[0] - c[0]
        offsetY = before[1] - c[1]
        samples.clear()
        sample()
    }

    private fun spread(): Float {
        if (fingers.size < 2) return 1f
        return max(1f, hypot(fingers[1][0] - fingers[0][0], fingers[1][1] - fingers[0][1]))
    }

    fun touchesBegan(ids: List<Long>, points: List<FloatArray>) {
        for (n in ids.indices) {
            val p = points[n]
            if (fingers.isEmpty()) {
                fingers.add(p)
                fingerIds.add(ids[n])
                downTime = System.nanoTime()
                originX = p[0]
                originY = p[1]
                offsetX = 0f
                offsetY = 0f
                samples.clear()
                sample()
                panActive = false
                pinchActive = false
                tapFailed = false
            } else {
                rebase {
                    fingers.add(p)
                    fingerIds.add(ids[n])
                }
            }
        }
        if (fingers.size >= 2 && !pinchActive) {
            // The pinch begins (and the tap can no longer win).
            tapFailed = true
            pinchActive = true
            touched()
            dragging = true
            zoomFrom = zoomTo
            pinchDistance = spread()
        }
    }

    fun touchesMoved(ids: List<Long>, points: List<FloatArray>) {
        for (n in ids.indices) {
            val at = fingerIds.indexOf(ids[n])
            if (at >= 0) fingers[at] = points[n]
        }
        if (fingers.isEmpty()) return
        sample()
        val f = focus()
        if (!panActive && hypot(f[0] - originX, f[1] - originY) >= PAN_SLOP) {
            panActive = true
            tapFailed = true
            touched()
            jumpT = -1f
            dragging = true
            // The loupe keeps its distance from the finger, so a finger below it does not hide it.
            grabX = tx - f[0]
            grabY = ty - f[1]
            if (hypot(x - tx, y - ty) > RADIUS) {
                grabX = x - f[0]
                grabY = y - f[1]
            }
        }
        if (panActive) {
            val m = RADIUS * EDGE_KEEP
            tx = rubber(f[0] + grabX, m, width - m, EDGE_BAND)
            ty = rubber(f[1] + grabY, top + m, height - bottom - m, EDGE_BAND)
        }
        if (pinchActive && fingers.size >= 2) {
            zoomTo = rubber(zoomFrom * spread() / pinchDistance, MIN_ZOOM, MAX_ZOOM, 0.6f)
        }
    }

    fun touchesEnded(ids: List<Long>, points: List<FloatArray>) {
        val v = velocity()
        for (n in ids.indices) {
            val at = fingerIds.indexOf(ids[n])
            if (at >= 0) fingers[at] = points[n]
        }
        if (fingers.isEmpty()) return
        val last = fingerIds.all { it in ids }
        if (!last) {
            rebase {
                for (id in ids) {
                    val at = fingerIds.indexOf(id)
                    if (at >= 0) {
                        fingers.removeAt(at)
                        fingerIds.removeAt(at)
                    }
                }
            }
        }
        if (pinchActive && (last || fingers.size < 2)) {
            pinchActive = false
            zoomTo = zoomTo.coerceIn(MIN_ZOOM, MAX_ZOOM)
            dragging = false
            touched()
        }
        if (!last) return
        val point = fingers[0]
        fingers.clear()
        fingerIds.clear()
        if (panActive) {
            val thrown = inside(tx + if (reduced) 0f else v[0] * THROW, ty + if (reduced) 0f else v[1] * THROW)
            tx = thrown[0]
            ty = thrown[1]
            dragging = false
            touched()
            panActive = false
            return
        }
        if (!tapFailed && (System.nanoTime() - downTime) / 1e9f <= TAP_SECONDS) tap(point[0], point[1])
    }

    /**
     * The gesture stopped without every finger lifting (it was cancelled, or a pointer went missing):
     * the loupe is let go where it is heading and the zoom settles inside its range.
     */
    fun touchesLost() {
        if (fingers.isEmpty() && !panActive && !pinchActive && !dragging) return
        fingers.clear()
        fingerIds.clear()
        samples.clear()
        if (pinchActive) zoomTo = zoomTo.coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (panActive) {
            val at = inside(tx, ty)
            tx = at[0]
            ty = at[1]
        }
        panActive = false
        pinchActive = false
        tapFailed = true
        dragging = false
        touched()
    }

    private fun tap(px: Float, py: Float) {
        touched()
        if (hypot(px - x, py - y) < RADIUS + RIM) {
            // On the glass: a closer look, or back.
            zoomTo = if (zoomTo < (ZOOM + CLOSE_ZOOM) / 2f) CLOSE_ZOOM else ZOOM
            tick()
            return
        }
        val to = inside(px, py)
        if (reduced) {
            x = to[0]
            y = to[1]
            tx = to[0]
            ty = to[1]
            return
        }
        jump = floatArrayOf(x, y, to[0], to[1])
        jumpT = 0f
    }
}
