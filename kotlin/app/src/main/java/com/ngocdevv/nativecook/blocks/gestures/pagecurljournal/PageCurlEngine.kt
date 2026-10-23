package com.ngocdevv.nativecook.blocks.gestures.pagecurljournal

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Interaction modes.
internal const val IDLE = 0
internal const val PENDING = 1 // finger down, direction not picked yet
internal const val DRAG = 2
internal const val SETTLE = 3
internal const val HOLD = 4
internal const val RIFFLING = 5
internal const val NUDGING = 6

internal const val HAPTIC_TICK = 0
internal const val HAPTIC_HALF = 1
internal const val HAPTIC_LAND = 2

/** One turning sheet: its grabbed point, where that point is now, and which page it is. */
internal class Slot(width: Float, time: FrameTime) {
    var front = 0
    var gx = width
    var gy = 0f
    val tx = Anim(width, time)
    val ty = Anim(0f, time)
    var on = 0f
}

/**
 * The block's state machine, one for one with the React Native block's worklets: touch in, a sheet's grab
 * point and tip out. Everything runs on the main thread; positions are points (dp).
 */
internal class PageCurlEngine(
    val width: Float,
    val height: Float,
    private val density: Float,
    private val reduced: Boolean,
    private val autoPlay: Boolean,
    private val haptic: (Int) -> Unit,
    private val onPage: (Int) -> Unit,
) {
    private val time = FrameTime()
    private val s = width / REF.WIDTH

    // Slot 0 is the sheet a finger turns; slots 1 and 2 only fly during a riffle, on top of it.
    val slots = Array(RIFFLE.SLOTS) { Slot(width, time) }
    private val slot0 = slots[0]
    private var underIdx = 1

    private val fromStart = when {
        QA_START < SCRIPT[1].t0 -> 0
        QA_START < SCRIPT[2].t0 -> 1
        QA_START < SCRIPT[3].t0 -> 2
        else -> LAST
    }
    private val initial = when {
        QA_HOLD == QaHold.B -> LAST
        QA_START > 0f -> fromStart
        QA_HOLD == QaHold.P1 -> 1
        QA_HOLD == QaHold.P2 -> 2
        else -> 0
    }

    /** The page the book lies open on: the one drawn at rest. */
    var viewPage = initial
        private set
    var cur = initial
        private set
    var mode = IDLE
        private set
    var ready = false
        private set

    private var baseX = 0f
    private var baseY = 0f
    private var startX = 0f
    private var startY = 0f
    private var gain = 1f
    private var preLifted = false
    private val fade = Anim(-1f, time)
    var flex = 0f
        private set
    private var flexV = 0f
    private var lastTipX = width
    private var lastTipY = 0f
    private var scripted = false // true while the autoplay finger is driving
    private var lastTouch = -1e9
    private var clock = QA_START.toDouble()
    private var playing = true
    private var landingFeel = false
    private var riffleLeft = 0
    private var riffleTo = 0
    private var grainX = 0f
    private var grainY = 0f
    private var grainTime = -1e9
    private var pastHalf = false
    private var lastNudge = -1e9
    val twinkle = Anim(1f, time)
    private val foilT = Anim(0f, time)
    private var lastFoil = 0.0
    private var scriptAt = -1
    private var nudgeDone = false
    private var lastFrameMs = -1.0

    /** The phone's roll, radians, from the rotation sensor. */
    private var roll = 0f

    // The page-stack hairlines: how many sheets are left to turn, eased.
    private val stackLeft = Anim((LAST - initial).toFloat(), time)
    private var stackTarget = LAST - initial

    // After a page lands the curl canvases step aside, a few frames later so the landed page is already drawn.
    private var afterLand = 0
    private var dirty = true

    private val anims = listOf(
        slots[0].tx, slots[0].ty, slots[1].tx, slots[1].ty, slots[2].tx, slots[2].ty,
        fade, twinkle, foilT, stackLeft,
    )
    private val tipA = FloatArray(2)
    private val tipB = FloatArray(2)
    private val pointA = FloatArray(2)
    private val pointB = FloatArray(2)

    fun markReady() {
        ready = true
        dirty = true
    }

    fun invalidate() {
        dirty = true
    }

    fun onRoll(value: Float) {
        roll = value
        if (cur == LAST) dirty = true
    }

    /** True when the screen has to be drawn again; reading it clears the request. */
    fun needsDraw(): Boolean {
        val busy = dirty || mode != IDLE || afterLand > 0 || abs(flex) > 1e-4f || abs(flexV) > 1e-4f ||
            anims.any { it.running }
        dirty = false
        return busy
    }

    // ---- Landings ----

    private fun landed(page: Int, changed: Boolean, feel: Boolean) {
        viewPage = page
        afterLand = 3
        onPage(page)
        if (changed && feel) haptic(HAPTIC_LAND)
    }

    /** Payoffs on arrival: the title's sparkles twinkle, the cover's foil catches the light once. */
    private fun arrive(page: Int) {
        if (reduced) return
        if (page == 0) {
            twinkle.set(0f)
            twinkle.animate(TimingRun(1f, ms(TWINKLE.DURATION_MS), EASE_IN_OUT))
        }
        if (page == LAST) {
            foilT.set(0f)
            lastFoil = clock
            foilT.animate(TimingRun(1f, ms(FOIL.SWEEP_MS), EASE_IN_OUT) { foilT.set(0f) })
        }
    }

    private fun settleDone(over: Boolean) {
        val page = if (over) underIdx else slot0.front
        val changed = page != cur
        cur = page
        mode = IDLE
        fade.set(-1f)
        preLifted = false
        if (changed) arrive(page)
        landed(page, changed, landingFeel)
    }

    /** A riffle ends when its last sheet lands. */
    private fun riffleLanded() {
        riffleLeft -= 1
        if (riffleLeft > 0) return
        val page = riffleTo
        cur = page
        mode = IDLE
        arrive(page)
        landed(page, true, landingFeel)
    }

    // ---- Turning ----

    /** Riffle: the sheet in hand comes back, and every earlier sheet follows it, staggered. */
    private fun startRiffle(vx: Float, vy: Float) {
        val f0 = slot0.front
        val n = min(f0, RIFFLE.SLOTS - 1)
        mode = RIFFLING
        riffleLeft = n + 1
        riffleTo = f0 - n
        flexV += abs(vx) * TURN.RELEASE_KICK
        slot0.ty.animate(SpringRun(slot0.gy, sp(RIFFLE.spring), vy))
        slot0.tx.animate(SpringRun(slot0.gx, sp(RIFFLE.spring), vx) { riffleLanded() })
        for (i in 1..n) {
            val sl = slots[i]
            val y = slot0.gy + (if (i % 2 == 1) -1f else 1f) * 18f * i * s
            sl.front = f0 - i
            sl.gx = width
            sl.gy = y
            sl.tx.set(-width)
            sl.ty.set(y)
            sl.on = 1f
            sl.tx.animate(
                DelayRun(
                    ms(RIFFLE.STAGGER_MS * i),
                    SpringRun(width, sp(RIFFLE.spring), RIFFLE.LAUNCH * s * QA_SPEED) { riffleLanded() },
                ),
            )
        }
    }

    /** Release the sheet with the finger's velocity: it goes over or falls back on a mass-spring. */
    private fun release(over: Boolean, vx: Float, vy: Float) {
        landingFeel = !scripted
        clampTip(slot0.gx, slot0.gy, slot0.tx.value, slot0.ty.value, height, tipA)
        slot0.tx.set(tipA[0])
        slot0.ty.set(tipA[1])
        if (!over && gain == TURN.BACK_FACTOR && vx / QA_SPEED > RIFFLE.VELOCITY && slot0.front > 0 && !reduced) {
            startRiffle(vx, vy)
            return
        }
        mode = SETTLE
        // The flung sheet billows: its speed kicks the roll's flex spring.
        flexV += hypot(vx, vy) * TURN.RELEASE_KICK
        val config = sp(if (over) TURN.over else TURN.back)
        slot0.ty.animate(SpringRun(slot0.gy, config, vy))
        slot0.tx.animate(SpringRun(if (over) -slot0.gx else slot0.gx, config, vx) { settleDone(over) })
    }

    /** Turn by button, tap or accessibility action: the right edge lifts at 62 % and is flicked over. */
    fun turnBy(step: Int) {
        if (mode != IDLE || !ready) return
        val c = cur
        val target = c + step
        if (target < 0 || target > LAST) return
        slot0.on = 1f
        dirty = true
        if (reduced) {
            landingFeel = !scripted
            slot0.front = c
            underIdx = target
            fade.set(0f)
            mode = SETTLE
            fade.animate(
                TimingRun(1f, FADE_MS, EASE_OUT) {
                    slot0.front = target
                    settleDone(false)
                },
            )
            return
        }
        gain = 1f
        if (step > 0) {
            if (!preLifted) {
                // Not already lifted by the finger: lift the right edge at 62 %.
                val y = height * TURN.BUTTON_GRAB_Y
                slot0.gx = width
                slot0.gy = y
                slot0.tx.set(width - 1f)
                slot0.ty.set(y)
            }
            slot0.front = c
            underIdx = target
            release(true, -1600f * s * QA_SPEED, -500f * s * QA_SPEED)
        } else {
            val y = height * TURN.BUTTON_GRAB_Y
            slot0.front = target
            underIdx = c
            slot0.gx = width
            slot0.gy = y
            slot0.tx.set(-width)
            slot0.ty.set(y)
            release(false, 1200f * s * QA_SPEED, -400f * s * QA_SPEED)
        }
    }

    // ---- Input (the same path for the finger and the script) ----

    private fun down(x: Float, y: Float) {
        val m = mode
        if (m == HOLD || m == RIFFLING || !ready) return
        if ((m == SETTLE || m == NUDGING) && fade.value < 0f) {
            // Catch the sheet mid-flight: it stays where it is and follows the finger from here.
            slot0.tx.cancel()
            slot0.ty.cancel()
            clampTip(slot0.gx, slot0.gy, slot0.tx.value, slot0.ty.value, height, tipA)
            baseX = tipA[0]
            baseY = tipA[1]
            startX = x
            startY = y
            if (m == NUDGING) gain = 1f
            mode = DRAG
            return
        }
        if (m != IDLE) return
        startX = x
        startY = y
        mode = PENDING
        pastHalf = false
        grainX = x
        grainY = y
        // Anticipation: a touch near the right edge lifts the corner within a frame.
        val c = cur
        if (!reduced && c < LAST && x > width * TURN.PRE_LIFT_ZONE) {
            slot0.front = c
            underIdx = c + 1
            slot0.gx = x
            slot0.gy = y
            slot0.tx.set(x)
            slot0.ty.set(y)
            gain = 1f
            preLifted = true
            slot0.on = 1f
            slot0.tx.animate(SpringRun(x - TURN.PRE_LIFT * s, sp(SpringConfig.perceptual(150f, 1f))))
        }
    }

    private fun move(x: Float, y: Float) {
        if (mode == PENDING) {
            val dx = x - startX
            if (abs(dx) < TURN.PICK_SLOP && abs(y - startY) < TURN.PICK_SLOP) return
            val c = cur
            if (reduced) return
            slot0.tx.cancel()
            if (dx < 0f && c < LAST) {
                // Forward: the point under the finger is the point of the sheet that follows it.
                val lift = if (preLifted) TURN.PRE_LIFT * s else 0f
                slot0.front = c
                underIdx = c + 1
                slot0.gx = startX
                slot0.gy = startY
                baseX = startX - lift
                baseY = startY
                gain = 1f
            } else if (dx > 0f && c > 0) {
                // Back: the previous sheet lies turned over the spine; its fold follows the finger 1:1.
                slot0.front = c - 1
                underIdx = c
                slot0.gx = width
                slot0.gy = startY
                baseX = -width
                baseY = startY
                gain = TURN.BACK_FACTOR
            } else {
                return
            }
            preLifted = false
            mode = DRAG
            slot0.on = 1f
        }
        if (mode != DRAG) return
        val nx = baseX + gain * (x - startX)
        val ny = baseY + gain * (y - startY)
        slot0.tx.set(nx)
        slot0.ty.set(ny)
        if (scripted) return
        // Haptic grain: a tick per step of fold travel, and a light catch where the sheet passes half a turn.
        val now = clock
        if (hypot(x - grainX, y - grainY) >= GRAIN.STEP_PT * s && now - grainTime >= GRAIN.MIN_GAP_MS) {
            grainX = x
            grainY = y
            grainTime = now
            haptic(HAPTIC_TICK)
        }
        clampTip(slot0.gx, slot0.gy, nx, ny, height, tipA)
        val half = turnProgress(slot0.gx, slot0.gy, tipA[0], tipA[1]) > TURN.COMMIT_PROGRESS
        if (half != pastHalf) {
            pastHalf = half
            haptic(HAPTIC_HALF)
        }
    }

    private fun up(x: Float, y: Float, vx: Float, vy: Float) {
        val m = mode
        if (m == PENDING) {
            mode = IDLE
            val dx = x - startX
            val c = cur
            if (reduced && abs(dx) > 30f) {
                turnBy(if (dx < 0f) 1 else -1)
                return
            }
            // A tap: the right margin or the hint turns forward, the left margin back; elsewhere a lifted corner drops.
            val hintTop = (TYPE.HINT_TOP - 12f) * s
            val onHint = c == 0 && y > hintTop && y < hintTop + 44f * s
            if (x > width * 0.66f || onHint) {
                turnBy(1)
            } else if (x < width * 0.33f) {
                turnBy(-1)
            } else if (preLifted) {
                gain = 1f
                release(false, 0f, 0f)
            }
            return
        }
        if (m != DRAG) return
        val g = gain
        val p = turnProgress(slot0.gx, slot0.gy, slot0.tx.value, slot0.ty.value)
        val v = g * vx / QA_SPEED // thresholds are in real time, also in slow-motion review
        val over = v < -TURN.COMMIT_VELOCITY || (p > TURN.COMMIT_PROGRESS && v < TURN.COMMIT_VELOCITY)
        release(over, g * vx, g * vy)
    }

    /** Idle nudge: the bottom-right corner lifts and falls back with a wobble. */
    private fun nudge() {
        val c = cur
        if (mode != IDLE || c >= LAST || reduced || !ready) return
        lastNudge = clock
        mode = NUDGING
        slot0.front = c
        underIdx = c + 1
        gain = 1f
        slot0.gx = width
        slot0.gy = height
        slot0.tx.set(width)
        slot0.ty.set(height)
        slot0.on = 1f
        val lx = width + NUDGE.LIFT_X * s
        val ly = height + NUDGE.LIFT_Y * s
        slot0.ty.animate(
            SequenceRun(
                listOf(
                    SpringRun(ly, sp(NUDGE.up)),
                    DelayRun(ms(NUDGE.HOLD_MS), SpringRun(height, sp(NUDGE.down))),
                ),
            ),
        )
        slot0.tx.animate(
            SequenceRun(
                listOf(
                    SpringRun(lx, sp(NUDGE.up)),
                    DelayRun(
                        ms(NUDGE.HOLD_MS),
                        SpringRun(width, sp(NUDGE.down)) {
                            flexV += NUDGE.KICK
                            settleDone(false)
                        },
                    ),
                ),
            ),
        )
    }

    // ---- The finger ----

    fun touchDown(x: Float, y: Float) {
        lastTouch = clock
        playing = false
        dirty = true
        if (scripted) {
            // A real finger takes over from the script mid-stroke.
            scripted = false
            if (mode == DRAG) {
                baseX = slot0.tx.value
                baseY = slot0.ty.value
                startX = x
                startY = y
                return
            }
            if (mode == PENDING) mode = IDLE
        }
        down(x, y)
    }

    fun touchMove(x: Float, y: Float) {
        lastTouch = clock
        move(x, y)
    }

    fun touchUp(x: Float, y: Float, vx: Float, vy: Float) {
        lastTouch = clock
        up(x, y, vx, vy)
    }

    // ---- One clock ----

    /** The first frame of a new recording period must be the same resting title page as the first frame. */
    private fun resetLoopRest() {
        for (slot in slots) {
            slot.tx.set(width)
            slot.ty.set(0f)
            slot.gx = width
            slot.gy = 0f
            slot.front = 0
            slot.on = 0f
        }
        underIdx = 1
        cur = 0
        if (viewPage != 0) {
            viewPage = 0
            onPage(0)
        }
        mode = IDLE
        fade.set(-1f)
        preLifted = false
        gain = 1f
        flex = 0f
        flexV = 0f
        lastTipX = width
        lastTipY = 0f
        twinkle.set(1f)
        foilT.set(0f)
        stackLeft.set(LAST.toFloat())
        stackTarget = LAST
        afterLand = 0
        scripted = false
        landingFeel = false
        scriptAt = -1
        nudgeDone = false
        dirty = true
    }

    /**
     * One clock: the sheet's flex (a damped spring driven by the tip's speed), the ambient timers and the
     * autoplay script. [nowMs] is the frame's time.
     */
    fun frame(nowMs: Double) {
        val sincePrevious = if (lastFrameMs < 0.0) 16.0 else nowMs - lastFrameMs
        lastFrameMs = nowMs
        time.nowMs = nowMs
        for (a in anims) a.step(nowMs)

        // After React has put the landed page on top, the curl canvases step aside.
        if (afterLand > 0) {
            afterLand -= 1
            if (afterLand == 0 && mode == IDLE) {
                for (sl in slots) sl.on = 0f
            }
        }

        // Page-stack edge: one hairline per sheet still to turn, fading as each one goes.
        val left = LAST - cur
        if (left != stackTarget) {
            stackTarget = left
            if (reduced) {
                stackLeft.set(left.toFloat())
            } else {
                stackLeft.animate(TimingRun(left.toFloat(), 260f, EASE_OUT))
            }
        }

        val dt = min(0.05, sincePrevious / 1000.0).toFloat() * QA_SPEED
        val previousClock = clock
        clock += dt * 1000.0
        if (QA_HOLD == QaHold.A) {
            // Re-assert the held frame every frame, so nothing at start-up can move it.
            mode = HOLD
            slot0.front = 0
            underIdx = 1
            slot0.gx = HOLD_A.grab[0] * s
            slot0.gy = HOLD_A.grab[1] * s
            slot0.tx.set(HOLD_A.tip[0] * s)
            slot0.ty.set(HOLD_A.tip[1] * s)
            slot0.on = 1f
        }

        clampTip(slot0.gx, slot0.gy, slot0.tx.value, slot0.ty.value, height, tipA)
        val speed = hypot(tipA[0] - lastTipX, tipA[1] - lastTipY) / max(dt, 1e-3f)
        lastTipX = tipA[0]
        lastTipY = tipA[1]
        val target = if (mode == HOLD || reduced) 0f else min(TURN.FLEX_MAX, speed * TURN.FLEX_PER_SPEED)
        val k = TURN.flex.stiffness / TURN.flex.mass
        val c = TURN.flex.damping / TURN.flex.mass
        if (target != 0f || abs(flex) > 1e-4f || abs(flexV) > 1e-4f) {
            flexV += (k * (target - flex) - c * flexV) * dt
            flex = max(-0.5f, flex + flexV * dt)
        } else if (flex != 0f) {
            flex = 0f
            flexV = 0f
        }

        if (QA_HOLD != null || !ready) return
        val now = clock
        // Ambient: the cover's foil sweeps every few seconds while it rests.
        if (!reduced && cur == LAST && mode == IDLE && now - lastFoil > FOIL.EVERY_MS) arrive(LAST)

        if (!autoPlay) {
            val idle = now - lastTouch
            if (idle > NUDGE.AFTER_MS && idle < NUDGE.AFTER_MS + NUDGE.EVERY_MS * NUDGE.MAX && now - lastNudge > NUDGE.EVERY_MS) {
                nudge()
            }
            return
        }
        if (!playing) {
            if (mode != IDLE || now - lastTouch < IDLE_RESUME_MS) return
            // Resume from rest: from the title page at the top of the loop, otherwise at the riffle back from the cover.
            val page = cur
            playing = true
            scriptAt = -1
            clock = when (page) {
                0 -> 0.0
                LAST -> SCRIPT[4].t0 - 400.0
                else -> SCRIPT[page + 1].t0 - 400.0
            }
            return
        }
        if (!reduced && (clock / LOOP_MS).toInt() > (previousClock / LOOP_MS).toInt()) {
            // Reassert the resting title page at the recording seam.
            resetLoopRest()
        }
        val t = (clock % LOOP_MS).toFloat()
        if (t < 100f) nudgeDone = false
        if (t >= NUDGE_AT && !nudgeDone) {
            nudgeDone = true
            nudge()
        }
        var active = -1
        for (i in SCRIPT.indices) {
            if (t >= SCRIPT[i].t0 && t <= SCRIPT[i].t1) active = i
        }
        val prev = scriptAt
        if (prev >= 0 && prev != active) {
            // The stroke ended: lift with the path's speed at its end.
            val stroke = SCRIPT[prev]
            val flick = stroke.t1 - stroke.t0 < 700f
            strokePoint(stroke, 0.98f, flick, pointA)
            strokePoint(stroke, 1f, flick, pointB)
            val span = 0.02f * (stroke.t1 - stroke.t0) / 1000f
            move(pointB[0] * s, pointB[1] * s)
            up(
                pointB[0] * s,
                pointB[1] * s,
                (pointB[0] - pointA[0]) * s / span * QA_SPEED,
                (pointB[1] - pointA[1]) * s / span * QA_SPEED,
            )
            scripted = false
        }
        if (active >= 0) {
            val stroke = SCRIPT[active]
            val u = (t - stroke.t0) / (stroke.t1 - stroke.t0)
            strokePoint(stroke, u, stroke.t1 - stroke.t0 < 700f, pointA)
            if (prev != active) {
                scripted = true
                down(pointA[0] * s, pointA[1] * s)
            } else {
                move(pointA[0] * s, pointA[1] * s)
            }
        }
        scriptAt = active
    }

    // ---- What the screen draws ----

    /** Fills the uniforms of sheet [slot]. Slot 0 sits over the page beneath; the others over transparent. */
    fun curlUniforms(slot: Int, out: CurlUniforms) {
        val sl = slots[slot]
        val g0 = sl.gx
        val g1 = sl.gy
        clampTip(g0, g1, sl.tx.value, sl.ty.value, height, tipB)
        val cx = tipB[0]
        val cy = tipB[1]
        val d = hypot(g0 - cx, g1 - cy)
        val hold = mode == HOLD
        val ramp = if (hold) 1f else rollRamp(d, 2f * g0, TURN.RAMP_IN * s, TURN.RAMP_OUT * s)
        // The higher the sheet, the softer and wider its shadow (equal to the fitted values at the source's lift).
        val p = min(1f, d / max(1f, 2f * g0))
        val over = slot == 0
        out.sizeW = width
        out.sizeH = height
        out.grabX = g0
        out.grabY = g1
        out.tipX = cx
        out.tipY = cy
        out.r0 = CURL.RADIUS * s * ramp * (1f + flex)
        out.r1 = CURL.RADIUS_SLOPE
        out.rmin = CURL.RADIUS_MIN * s
        out.frontIdx = sl.front.toFloat()
        out.underIdx = if (over) underIdx.toFloat() else 0f
        out.shA = CURL.SHADOW * (LIFT.DARK_BASE + LIFT.DARK_PER_TURN * p)
        out.shW = CURL.SHADOW_WIDTH * s * (LIFT.WIDTH_BASE + LIFT.WIDTH_PER_TURN * p)
        out.eA = CURL.EDGE_SHADOW
        out.eW = CURL.EDGE_SHADOW_WIDTH * s
        out.amb1 = CURL.FRONT_LIGHT
        out.amb2 = CURL.BACK_LIGHT
        out.g2 = CURL.BACK_GAMMA
        out.bg = CURL.BACK_PAPER
        out.ba = CURL.SHOW_THROUGH
        out.grain = CURL.GRAIN
        out.edgeInk = CURL.EDGE_INK
        out.px = density
        out.fade = if (over) fade.value else -1f
        out.clearUnder = if (over) 0f else 1f
        out.spec = CURL.SHEEN
    }

    /** Foil on the cover title: the sweep, or (with no sweep running) the phone's tilt. */
    fun foilUniforms(out: FoilUniforms) {
        val angle = FOIL.ANGLE_DEG * Math.PI.toFloat() / 180f
        val sweeping = foilT.value > 0f
        out.dirX = cos(angle)
        out.dirY = sin(angle)
        out.center = if (sweeping) (FOIL.REST + (FOIL.REACH - FOIL.REST) * foilT.value) * s else (150f + roll * FOIL.TILT_GAIN) * s
        out.width = FOIL.WIDTH * s
        out.amount = if (sweeping) FOIL.AMOUNT else if (reduced) 0f else FOIL.AMOUNT * min(1f, abs(roll) * 6f)
    }

    /** The foil shows on the resting cover. */
    fun foilVisible(): Boolean = viewPage == LAST && cur == LAST && slot0.on == 0f

    /** Opacity of the hairline for the sheet [index] still to turn. */
    fun stackAlpha(index: Int): Float = max(0f, min(1f, stackLeft.value - index))

    /** A curl canvas is on screen while its sheet is out. */
    fun slotShown(slot: Int): Boolean = ready && slots[slot].on > 0f
}
