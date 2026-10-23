package com.ngocdevv.nativecook.blocks.playground.glassmarblestray

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

// The set: glass tint, the two colours of the swirl, and how many vanes it has. The seventh is the
// larger shooter.
internal val MARBLES = listOf(
    Marble(14.0, intArrayOf(150, 190, 255), intArrayOf(28, 66, 196), intArrayOf(120, 200, 255), 3),
    Marble(14.0, intArrayOf(255, 204, 128), intArrayOf(214, 86, 20), intArrayOf(255, 222, 150), 3),
    Marble(14.0, intArrayOf(140, 228, 170), intArrayOf(248, 248, 236), intArrayOf(36, 146, 88), 3),
    Marble(14.0, intArrayOf(255, 226, 214), intArrayOf(138, 22, 34), intArrayOf(240, 148, 118), 4),
    Marble(14.0, intArrayOf(228, 240, 246), intArrayOf(255, 255, 255), intArrayOf(186, 208, 230), 3),
    Marble(14.0, intArrayOf(212, 182, 255), intArrayOf(106, 48, 188), intArrayOf(232, 204, 255), 3),
    Marble(17.5, intArrayOf(242, 240, 232), intArrayOf(214, 38, 40), intArrayOf(248, 196, 40), 4),
    Marble(14.0, intArrayOf(150, 238, 232), intArrayOf(0, 136, 148), intArrayOf(255, 255, 255), 3),
    Marble(14.0, intArrayOf(204, 204, 210), intArrayOf(28, 28, 34), intArrayOf(160, 160, 172), 3),
    Marble(14.0, intArrayOf(255, 226, 150), intArrayOf(158, 88, 28), intArrayOf(255, 244, 200), 3),
    Marble(14.0, intArrayOf(255, 202, 216), intArrayOf(218, 58, 108), intArrayOf(255, 230, 240), 3),
    Marble(14.0, intArrayOf(214, 248, 160), intArrayOf(88, 158, 30), intArrayOf(244, 255, 220), 3),
    Marble(14.0, intArrayOf(172, 176, 255), intArrayOf(30, 30, 118), intArrayOf(255, 190, 88), 3),
)

// The tray (dp): outer size limits, the frame's width and the depth of its sides.
private const val TRAY_MAX_W = 372.0
private const val TRAY_RATIO = 1.34
internal const val FRAME = 24.0
internal const val TRAY_DEPTH = 16.0
internal const val OUTER_CORNER = 28.0
// Below this width (dp) the tray has no room for its cups and is not built.
private const val MIN_TRAY_W = 150.0
// Perspective: the eye is FOCAL dp above the tray.
internal const val FOCAL = 900.0
// Tilt: at most MAX_TILT (rad) when dragged; a drag of DRAG_SCALE dp gets about three quarters of
// the way (tanh), and the tray slides with the finger by SLIDE_SHARE, up to SLIDE_MAX dp.
private const val MAX_TILT = 0.24
private const val DRAG_SCALE = 150.0
private const val SLIDE_SHARE = 0.07
private const val SLIDE_MAX = 16.0
// A shake: the finger reverses SHAKE_REVERSALS times within SHAKE_WINDOW s, each faster than
// SHAKE_SPEED dp/s.
private const val SHAKE_SPEED = 1000.0
private const val SHAKE_WINDOW = 0.5
private const val SHAKE_REVERSALS = 2
private const val SHAKE_COOLDOWN = 0.7
// A tap within this reach (dp) of a marble flicks it away from the finger at FLICK_SPEED dp/s.
private const val TAP_REACH = 12.0
private const val FLICK_SPEED = 620.0
// After a touch, autoPlay waits this long (s).
private const val USER_HOLD = 3.5
// Haptic clicks, at most one every CLICK_GAP s.
private const val CLICK_GAP = 0.09
// The last REST_BLEND s of a loop ease every marble onto the state the loop started from.
private const val REST_BLEND = 1.1
// A pan starts after this much movement (dp); a tap is shorter than this (s).
private const val PAN_SLOP = 4.0
private const val TAP_TIME = 0.5

// Haptic requests, read and cleared by the view.
internal const val EVENT_CLICK = 1
internal const val EVENT_SHAKE = 2
internal const val EVENT_FLICK = 4

// Light comes from the upper left, in front of the tray.
internal val LIGHT = doubleArrayOf(-0.45, -0.62, 0.64)

/** Where the tray sits on screen and how it is turned. */
internal class Pose {
    var cx = 0.0
    var cy = 0.0
    var tiltX = 0.0
    var tiltY = 0.0
    private var ca = 1.0
    private var sa = 0.0
    private var cb = 1.0
    private var sb = 0.0

    // The last turned or projected point.
    var px = 0.0
    var py = 0.0
    var pz = 0.0
    var ps = 1.0

    fun set(cx: Double, cy: Double, tiltX: Double, tiltY: Double) {
        this.cx = cx
        this.cy = cy
        this.tiltX = tiltX
        this.tiltY = tiltY
        ca = cos(tiltX)
        sa = sin(tiltX)
        cb = cos(tiltY)
        sb = sin(tiltY)
    }

    /** A point of the tray's frame (u, v, w; w up out of the floor) turned by the tilt, into px/py/pz. */
    fun turn(u: Double, v: Double, w: Double) {
        val y1 = v * ca - w * sa
        val z1 = v * sa + w * ca
        px = u * cb + z1 * sb
        py = y1
        pz = -u * sb + z1 * cb
    }

    /** A point of the tray's frame on screen (px, py) and its perspective scale (ps). */
    fun project(u: Double, v: Double, w: Double) {
        turn(u, v, w)
        val s = FOCAL / (FOCAL - pz)
        px = cx + px * s
        py = cy + py * s
        ps = s
    }

    /** The homography that draws the tray's floor plane (w = 0) on screen, row-major (Skia/Android order). */
    fun floorMatrix(out: FloatArray) {
        val f = FOCAL
        out[0] = (cb + (cx * sb) / f).toFloat()
        out[1] = (sa * sb - (cx * sa * cb) / f).toFloat()
        out[2] = cx.toFloat()
        out[3] = ((cy * sb) / f).toFloat()
        out[4] = (ca - (cy * sa * cb) / f).toFloat()
        out[5] = cy.toFloat()
        out[6] = (sb / f).toFloat()
        out[7] = (-(sa * cb) / f).toFloat()
        out[8] = 1f
    }

    /** The light in the tray's own frame, into [out]: it moves across the floor as the tray tilts. */
    fun trayLight(out: DoubleArray) {
        val x1 = LIGHT[0] * cb - LIGHT[2] * sb
        val z1 = LIGHT[0] * sb + LIGHT[2] * cb
        val y1 = LIGHT[1]
        out[0] = x1
        out[1] = y1 * ca + z1 * sa
        out[2] = -y1 * sa + z1 * ca
    }
}

/**
 * The tray, its marbles, the autoPlay script and the touch state, stepped like the React Native
 * frame callback: whole ticks of 1/60 s, at most three a frame. Plain Kotlin, so it also runs in a
 * command-line parity check against the React Native code. [auto] is passed in at construction so
 * the very first frame is already the script's first frame.
 */
internal class GlassMarblesTrayEngine(auto: Boolean, val reduced: Boolean) {
    var events = 0
    /** The hardest click behind the last EVENT_CLICK (dp/s). */
    var clickHardness = 0.0

    private var looping = auto

    var width = 0.0
        private set
    var height = 0.0
        private set
    private var insetTop = 0.0
    private var insetBottom = 0.0
    var trayW = 0.0
        private set
    var trayH = 0.0
        private set
    private var cx = 0.0
    /** The tray's resting centre on screen (dp). */
    var cy = 0.0
        private set
    var tray: Tray? = null
        private set

    var world: World? = null
        private set
    private var rest: World? = null
    /** Where each marble was last drawn on screen, for taps. */
    val sx = DoubleArray(MARBLES.size)
    val sy = DoubleArray(MARBLES.size)
    val sr = DoubleArray(MARBLES.size)
    /** Seconds since the last click haptic. */
    private var sinceClick = 1.0

    private var acc = 0.0
    // What the finger asks of the tray.
    private var dragging = false
    private var wantTiltX = 0.0
    private var wantTiltY = 0.0
    private var wantSlideX = 0.0
    private var wantSlideY = 0.0
    // Shake detection: the last strong direction, when it was seen, and reversals so far.
    private var lastDirX = 0.0
    private var lastDirY = 0.0
    private var lastStrongAt = -10.0
    private var reversals = 0
    private var lastShakeAt = -10.0
    var now = 0.0
        private set
    // Requests carried out on the next tick.
    private var shakeNow = false
    private var flickWho = -1
    private var flickDX = 0.0
    private var flickDY = 0.0
    var clock = 0.0
        private set
    var hold = 0.0
        private set
    private val input = TrayInput()
    private val script = TrayInput()
    val pose = Pose()

    // Raw touch state: where it went down, when, and whether the pan has started.
    private var downX = 0.0
    private var downY = 0.0
    private var downAt = 0.0
    private var panning = false

    fun setAuto(auto: Boolean) {
        if (auto == looping) return
        looping = auto
        hold = 0.0
        clock = 0.0
    }

    /** A new size makes a new tray and starts the marbles over. */
    fun setLayout(width: Double, height: Double, insetTop: Double, insetBottom: Double) {
        if (width == this.width && height == this.height && insetTop == this.insetTop && insetBottom == this.insetBottom) return
        this.width = width
        this.height = height
        this.insetTop = insetTop
        this.insetBottom = insetBottom
        // The tray: as wide as fits, centred between the header and the caption.
        val top = insetTop + 70
        val bottom = height - insetBottom - 118
        trayW = max(0.0, min(TRAY_MAX_W, width - 30))
        trayH = min(trayW * TRAY_RATIO, max(200.0, bottom - top))
        cx = width / 2
        cy = (top + bottom) / 2
        // No tray until there is room for its cups (0 before layout, or a transient tiny or unbounded one).
        tray = if (width > 0 && trayW >= MIN_TRAY_W && height.isFinite()) Tray(trayW / 2 - FRAME, trayH / 2 - FRAME, MARBLES.size) else null
        world = null
    }

    /** Points [pose] at the world's current tray. */
    fun updatePose(): Boolean {
        val w = world ?: return false
        pose.set(cx + w.slideX, cy + w.slideY, w.tiltX, w.tiltY)
        return true
    }

    private fun tick(w: World, rest: World, tray: Tray) {
        now += TICK
        sinceClick += TICK
        if (shakeNow) {
            shakeNow = false
            w.scatter(if (reduced) 0.35 else 1.0)
        }
        val who = flickWho
        if (who >= 0) {
            flickWho = -1
            w.flick(who, flickDX, flickDY, if (reduced) FLICK_SPEED * 0.5 else FLICK_SPEED)
        }
        input.clear()
        if (dragging) {
            input.tiltX = wantTiltX
            input.tiltY = wantTiltY
            input.slideX = wantSlideX
            input.slideY = wantSlideY
        }
        if (hold > 0) {
            if (!dragging) hold -= TICK
            if (hold <= 0) clock = 0.0
        } else if (looping) {
            val before = clock
            var t = before + TICK
            if (t >= LOOP) {
                t -= LOOP
                // Home again: the loop starts over from exactly where it began.
                w.copyFrom(rest)
            }
            clock = t
            scriptPose(t, script)
            input.tiltX = script.tiltX
            input.tiltY = script.tiltY
            input.slideX = script.slideX
            input.guide = script.guide
            if (before < SHAKE_AT && t >= SHAKE_AT) w.scatter(1.0)
            if (t > LOOP - REST_BLEND) {
                val k = (t - (LOOP - REST_BLEND)) / REST_BLEND
                w.blendPositions(rest, k * k * 0.25)
                w.blendRotations(rest, k * k * 0.12)
            }
        }
        w.step(tray, input, TICK)
        if (w.clicks > 0 && hold > 0 && sinceClick > CLICK_GAP) {
            sinceClick = 0.0
            events = events or EVENT_CLICK
            clickHardness = w.hardest
        }
    }

    /** One display frame of [elapsed] seconds (null on the first frame). Returns whether it ticked. */
    fun frame(elapsed: Double?): Boolean {
        val tray = tray ?: return false
        if (width <= 0) return false
        if (world == null) {
            world = World(tray, MARBLES)
            rest = World(tray, MARBLES)
            sinceClick = 1.0
            clock = 0.0
        }
        val w = world ?: return false
        val r = rest ?: return false
        val dt = min(elapsed ?: 0.016, MAX_TICKS * TICK)
        var pending = acc + dt
        var ticks = 0
        while (pending >= TICK && ticks < MAX_TICKS) {
            tick(w, r, tray)
            pending -= TICK
            ticks += 1
        }
        acc = min(pending, TICK)
        placeMarbles()
        return ticks > 0
    }

    /** Where each marble shows on screen, as the React Native picture records it for taps. */
    fun placeMarbles() {
        val w = world ?: return
        updatePose()
        for (i in 0 until w.n) {
            pose.project(w.x[i], w.y[i], w.r[i] + w.z[i])
            sx[i] = pose.px
            sy[i] = pose.py
            sr[i] = w.r[i] * pose.ps
        }
    }

    private fun touched() {
        hold = USER_HOLD
    }

    // The gestures, as React Native's Gesture Handler reports them.

    fun panStart() {
        touched()
        dragging = true
        reversals = 0
    }

    fun panUpdate(translationX: Double, translationY: Double, velocityX: Double, velocityY: Double) {
        val limit = if (reduced) MAX_TILT * 0.45 else MAX_TILT
        // Pressing the tray down where the finger goes: that side dips away and the marbles follow.
        wantTiltY = limit * tanh(translationX / DRAG_SCALE)
        wantTiltX = -limit * tanh(translationY / DRAG_SCALE)
        val slide = if (reduced) 0.0 else SLIDE_MAX
        wantSlideX = slide * tanh((translationX * SLIDE_SHARE) / SLIDE_MAX)
        wantSlideY = slide * tanh((translationY * SLIDE_SHARE) / SLIDE_MAX)
        // A shake: strong strokes that keep reversing.
        val speed = hypot(velocityX, velocityY)
        if (speed < SHAKE_SPEED) return
        val dx = velocityX / speed
        val dy = velocityY / speed
        val t = now
        if (dx * lastDirX + dy * lastDirY < -0.5 && t - lastStrongAt < SHAKE_WINDOW) {
            reversals += 1
            if (reversals >= SHAKE_REVERSALS && t - lastShakeAt > SHAKE_COOLDOWN) {
                reversals = 0
                lastShakeAt = t
                shakeNow = true
                events = events or EVENT_SHAKE
            }
        } else if (t - lastStrongAt >= SHAKE_WINDOW) {
            reversals = 0
        }
        lastDirX = dx
        lastDirY = dy
        lastStrongAt = t
    }

    fun panFinalize() {
        dragging = false
        hold = USER_HOLD
    }

    fun tap(x: Double, y: Double) {
        if (world == null) return
        var best = -1
        var bestD = Double.POSITIVE_INFINITY
        for (i in sx.indices) {
            val d = hypot(x - sx[i], y - sy[i])
            if (d < sr[i] + TAP_REACH && d < bestD) {
                bestD = d
                best = i
            }
        }
        if (best < 0) return
        touched()
        // Away from the finger; a tap dead centre sends it up the tray.
        var dx = sx[best] - x
        var dy = sy[best] - y
        if (hypot(dx, dy) < sr[best] * 0.3) {
            dx = 0.0
            dy = -1.0
        }
        flickDX = dx
        flickDY = dy
        flickWho = best
        events = events or EVENT_FLICK
    }

    // Raw touches from the view: a pan once the finger has moved PAN_SLOP, otherwise a tap if it
    // lifts within TAP_TIME. [time] is in seconds on any steady clock; velocities come from the view.

    fun touchDown(x: Double, y: Double, time: Double) {
        downX = x
        downY = y
        downAt = time
        panning = false
    }

    fun touchMove(x: Double, y: Double, velocityX: Double, velocityY: Double) {
        val tx = x - downX
        val ty = y - downY
        if (!panning && tx * tx + ty * ty >= PAN_SLOP * PAN_SLOP) {
            panning = true
            panStart()
        }
        if (panning) panUpdate(tx, ty, velocityX, velocityY)
    }

    fun touchUp(x: Double, y: Double, time: Double) {
        if (!panning && time - downAt <= TAP_TIME) tap(x, y)
        panning = false
        panFinalize()
    }

    /** The touch was cut off (the pointer input restarted or left): the tray lets go, no tap. */
    fun touchCancel() {
        if (!panning) return
        panning = false
        panFinalize()
    }
}
