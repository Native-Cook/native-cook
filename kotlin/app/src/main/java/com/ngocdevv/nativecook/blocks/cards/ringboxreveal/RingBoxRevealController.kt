package com.ngocdevv.nativecook.blocks.cards.ringboxreveal

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

/** The pose the 3D stage and the Canvas layers show. */
internal class RingPose {
    var t = 0.0
    var open = 0.0
    var shot = 0.0
    var yaw = 0.0
    var burst = 0.0
    var mFrom = 0
    var mTo = 0
    var mProg = 0.0
    var sFrom = 0
    var sTo = 0
    var sProg = 0.0
    var carat = RingMotion.CARAT_MODEL
}

/** What one frame shows: the pose and the controls. */
internal class RingFrame {
    val pose = RingPose()
    var pickerMetal = 0
    var pickerStone = 0
    var sliderCarat = RingMotion.CARAT_MODEL
    var booked = false
    var filmPlaying = false
}

/**
 * The block's one source of truth: the film's clock and whatever a finger set. `frame(now)` is a pure function of
 * the stored fields and a frame time in seconds (Choreographer's clock), read by the Filament stage, the Canvas
 * layers and the controls, so they never disagree. Everything runs on the main thread.
 *
 * The modes of the React Native block: the film (auto, played from the buyer's choices); a finger's values (manual:
 * timings and springs started by a touch, a pick or a tap); and 2 s after the last touch the ease back (the camera
 * pulls back, the lid closes, the choices blend home), after which the film restarts.
 */
internal class RingBoxRevealController(autoPlay: Boolean, val reduced: Boolean) {
    private enum class Mode { AUTO, MANUAL, BACK }

    private val film = autoPlay && !reduced
    private var mode = if (film) Mode.AUTO else Mode.MANUAL
    private var start = -1.0
    private var heldT = 0.0
    private var metalHome = 0
    private var stoneHome = 0
    private var caratHome = RingMotion.CARAT_MODEL
    private var open = RingTrack.still(if (reduced) 1.0 else 0.0)
    private var shot = RingTrack.still(if (reduced) 1.0 else 0.0)
    private var yaw = RingTrack.still(0.0)
    private var burst = RingTrack.still(0.0)
    private var carat = RingTrack.still(RingMotion.CARAT_MODEL)
    private var mFrom = 0
    private var mTo = 0
    private var mProg = RingTrack.still(0.0)
    private var sFrom = 0
    private var sTo = 0
    private var sProg = RingTrack.still(0.0)
    private var lastActivity = 0.0
    private var busyUntil = 0.0
    private var bookedUntil = -1.0
    var dragging = false
        private set
    private var dragKind = 0 // 0 none, 1 lid, 2 turn
    private var dragFrom = 0.0
    private var yawFrom = 0.0

    /** True once the 3D stage shows the box: the Canvas layers draw its shadows and glints from then on. */
    var stageReady = false

    private val steps = DoubleArray(3)
    private val scratch = RingFrame()

    /** Starts the film's clock with the first frame the box is on screen. */
    fun begin(now: Double) {
        if (mode == Mode.AUTO && start < 0) start = now
    }

    private fun filmPose(t: Double, p: RingPose) {
        p.t = t
        p.open = RingMotion.filmOpen(t)
        p.shot = RingMotion.filmShot(t)
        p.yaw = RingMotion.filmYaw(t)
        p.burst = RingMotion.filmBurst(t)
        RingMotion.filmSteps(t, RingMotion.METAL_STEPS, steps)
        p.mFrom = (metalHome + steps[0].toInt()) % RingMotion.METALS.size
        p.mTo = (metalHome + steps[1].toInt()) % RingMotion.METALS.size
        p.mProg = steps[2]
        RingMotion.filmSteps(t, RingMotion.STONE_STEPS, steps)
        p.sFrom = (stoneHome + steps[0].toInt()) % RingMotion.STONES.size
        p.sTo = (stoneHome + steps[1].toInt()) % RingMotion.STONES.size
        p.sProg = steps[2]
        p.carat = RingMotion.filmCarat(t, caratHome)
    }

    private fun manualPose(now: Double, p: RingPose) {
        p.t = heldT
        p.open = open.at(now)
        p.shot = shot.at(now)
        p.yaw = yaw.at(now)
        p.burst = burst.at(now)
        p.mFrom = mFrom
        p.mTo = mTo
        p.mProg = mProg.at(now)
        p.sFrom = sFrom
        p.sTo = sTo
        p.sProg = sProg.at(now)
        p.carat = carat.at(now)
    }

    /** Fills `out` with the frame at `now` (s). */
    fun frame(now: Double, out: RingFrame) {
        if (mode == Mode.AUTO) {
            val p = out.pose
            if (start < 0) {
                filmPose(0.0, p)
                out.pickerMetal = metalHome
                out.pickerStone = stoneHome
                out.sliderCarat = caratHome
                out.booked = false
                out.filmPlaying = true
                return
            }
            val t = max(0.0, now - start) % RingMotion.LOOP
            filmPose(t, p)
            out.pickerMetal = if (p.mProg <= 0.001) p.mFrom else p.mTo
            out.pickerStone = if (p.sProg <= 0.001) p.sFrom else p.sTo
            out.sliderCarat = RingMotion.filmCaratStep(t, caratHome)
            out.booked = RingMotion.filmBooked(t)
            out.filmPlaying = true
            return
        }
        if (mode == Mode.MANUAL && film && !dragging) {
            val r0 = maxOf(lastActivity, busyUntil, mProg.end, sProg.end) + RingMotion.IDLE
            if (now >= r0) startReturn(r0)
        }
        if (mode == Mode.BACK && now >= open.end) {
            mode = Mode.AUTO
            start = open.end
            frame(now, out)
            return
        }
        manualPose(now, out.pose)
        out.pickerMetal = mTo
        out.pickerStone = sTo
        out.sliderCarat = caratHome
        out.booked = now < bookedUntil
        out.filmPlaying = false
    }

    private fun startReturn(r0: Double) {
        val p = scratch.pose
        manualPose(r0, p)
        val d = RingMotion.RETURN_DURATION
        shot = RingTrack.timing(p.shot, 0.0, r0, d * 0.7, curve = RingMotion::easeTouch)
        yaw = RingTrack.timing(p.yaw, 0.0, r0, d, curve = RingMotion::easeTouch)
        burst = RingTrack.timing(p.burst, 0.0, r0, 0.3)
        carat = RingTrack.timing(p.carat, caratHome, r0, 0.4)
        if (mTo != metalHome || p.mProg < 1) {
            mFrom = if (p.mProg >= 0.5) mTo else mFrom
            mTo = metalHome
            mProg = RingTrack.timing(0.0, 1.0, r0, RingMotion.BLEND_S)
        }
        if (sTo != stoneHome || p.sProg < 1) {
            sFrom = if (p.sProg >= 0.5) sTo else sFrom
            sTo = stoneHome
            sProg = RingTrack.timing(0.0, 1.0, r0, RingMotion.BLEND_S)
        }
        open = RingTrack.timing(p.open, 0.0, r0, d * 0.7, delay = d * 0.3, curve = RingMotion::easeTouch)
        mode = Mode.BACK
    }

    // ------------------------------------------------------------ touch

    /** Any touch pauses the film (or the ease back) where it is: the current pose becomes the manual values. */
    private fun pause(now: Double) {
        val f = scratch
        frame(now, f)
        val p = f.pose
        heldT = p.t
        open = RingTrack.still(p.open)
        shot = RingTrack.still(p.shot)
        yaw = RingTrack.still(p.yaw)
        burst = RingTrack.still(p.burst)
        carat = RingTrack.still(p.carat)
        if (p.mProg >= 1 || p.mProg <= 0) {
            val m = if (p.mProg >= 1) p.mTo else p.mFrom
            mFrom = m; mTo = m; mProg = RingTrack.still(1.0)
        } else {
            mFrom = p.mFrom; mTo = p.mTo
            mProg = RingTrack.timing(p.mProg, 1.0, now, RingMotion.BLEND_S * (1 - p.mProg))
        }
        if (p.sProg >= 1 || p.sProg <= 0) {
            val s = if (p.sProg >= 1) p.sTo else p.sFrom
            sFrom = s; sTo = s; sProg = RingTrack.still(1.0)
        } else {
            sFrom = p.sFrom; sTo = p.sTo
            sProg = RingTrack.timing(p.sProg, 1.0, now, RingMotion.BLEND_S * (1 - p.sProg))
        }
        if (mode == Mode.AUTO) {
            // the film's booking ends with it
            bookedUntil = if (f.booked) now + 0.5 else -1.0
        }
        mode = Mode.MANUAL
        lastActivity = now
    }

    private fun snapOpen(now: Double) {
        val cur = open.at(now)
        if (reduced) {
            open = RingTrack.timing(cur, 1.0, now, 0.2)
            shot = RingTrack.timing(shot.at(now), 1.0, now, 0.2)
            busyUntil = now + 0.2
            return
        }
        open = RingTrack.spring(cur, 1.0, now, damping = 12.0, stiffness = 340.0, mass = 0.6)
        busyUntil = open.end
        burst = RingTrack.timing(1.0, 0.0, now, 1.3, delay = 0.15, curve = RingMotion::easeOutQuad)
        shot = RingTrack.timing(shot.at(now), 1.0, now, 1.2, delay = 0.35, curve = RingMotion::easeTouch)
    }

    private fun closeBox(now: Double) {
        shot = RingTrack.timing(shot.at(now), 0.0, now, 0.7, curve = RingMotion::easeTouch)
        yaw = RingTrack.timing(yaw.at(now), 0.0, now, 0.6, curve = RingMotion::easeTouch)
        open = RingTrack.timing(open.at(now), 0.0, now, if (reduced) 0.2 else 0.65, delay = 0.25, curve = RingMotion::easeInCubic)
        busyUntil = open.end
    }

    fun tap(now: Double) {
        pause(now)
        if (open.at(now) < 0.5) {
            snapOpen(now)
            return
        }
        val s = shot.at(now)
        shot = RingTrack.timing(s, if (s < 1.5) 2.0 else 1.0, now, if (reduced) 0.2 else 1.0, curve = RingMotion::easeTouch)
        lastActivity = max(lastActivity, shot.end)
    }

    fun dragBegan(now: Double) {
        pause(now)
        dragging = true
        dragKind = 0
        dragFrom = open.at(now)
        yawFrom = yaw.at(now)
    }

    /** dx, dy: the drag's translation in dp. */
    fun dragChanged(now: Double, dx: Double, dy: Double) {
        lastActivity = now
        if (dragKind == 0) {
            if (abs(dx) < 8 && abs(dy) < 8) return
            dragKind = if (abs(dx) > abs(dy) && dragFrom > 0.6) 2 else 1
            if (dragKind == 1) busyUntil = 0.0
        }
        if (dragKind == 1) {
            open = RingTrack.still(min(1.08, max(0.0, dragFrom - dy / LID_PT)))
        } else {
            var v = yawFrom + dx * YAW_PER_PT
            val over = abs(v) - YAW_LIMIT
            if (over > 0) v = sign(v) * (YAW_LIMIT + over / (1 + over * 3))
            yaw = RingTrack.still(v)
        }
    }

    /** vx, vy: the release velocity in dp/s. */
    fun dragEnded(now: Double, vx: Double, vy: Double) {
        dragging = false
        lastActivity = now
        if (dragKind == 1) {
            val projected = open.to - vy / LID_PT * 0.2
            if (projected > 0.45) snapOpen(now) else closeBox(now)
        } else if (dragKind == 2) {
            yaw = RingTrack.spring(yaw.to, 0.0, now, damping = 10.0, stiffness = 40.0, velocity = vx * YAW_PER_PT)
            lastActivity = max(lastActivity, yaw.end - 1.5)
        }
        dragKind = 0
    }

    fun pickMetal(i: Int, now: Double) {
        pause(now)
        metalHome = i
        val current = if (mProg.at(now) >= 0.5) mTo else mFrom
        if (current == i && !mProg.moving(now)) return
        mFrom = current
        mTo = i
        mProg = RingTrack.timing(0.0, 1.0, now, if (reduced) 0.2 else RingMotion.BLEND_S)
    }

    fun pickStone(i: Int, now: Double) {
        pause(now)
        stoneHome = i
        val current = if (sProg.at(now) >= 0.5) sTo else sFrom
        if (current == i && !sProg.moving(now)) return
        sFrom = current
        sTo = i
        sProg = RingTrack.timing(0.0, 1.0, now, if (reduced) 0.2 else RingMotion.BLEND_S)
    }

    fun pickCarat(ct: Double, now: Double) {
        val v = (ct * 4).roundToInt() / 4.0
        pause(now)
        caratHome = v
        carat = if (reduced) RingTrack.still(v) else RingTrack.spring(carat.at(now), v, now, damping = 14.0, stiffness = 180.0)
    }

    fun book(now: Double) {
        pause(now)
        bookedUntil = now + RingMotion.BOOKED_TOUCH
    }

    private companion object {
        const val LID_PT = 110.0
        const val YAW_PER_PT = 0.008
        const val YAW_LIMIT = 0.7
    }
}
