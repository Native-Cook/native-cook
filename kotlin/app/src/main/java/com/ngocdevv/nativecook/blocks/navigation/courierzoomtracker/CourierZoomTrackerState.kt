package com.ngocdevv.nativecook.blocks.navigation.courierzoomtracker

import com.ngocdevv.nativecook.blocks.navigation.courierzoomtracker.CourierMotion as M
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal val COURIER_QUICK_REPLIES = listOf("Coming down now", "Leave it at the door")
internal const val COURIER_PHONE = "+44 20 7946 0958"
internal const val COURIER_ACTION = 48.0
internal const val COURIER_ACTION_GAP = 12.0
internal const val COURIER_PANEL_H = 204.0
internal const val COURIER_PANEL_PAD = 20.0
internal const val COURIER_ROW_CY = 132.0 + 24

/** The screen's geometry in dp. */
internal class CourierGeometry(val width: Double, val height: Double, val top: Double, bottom: Double) {
    val bottomInset = max(bottom, 16.0)
    val panelW = if (width >= 600) min(480.0, width - 32) else width
    val panelLeft = (width - panelW) / 2
    val panelTop = height - COURIER_PANEL_H - bottomInset
    val lay = CourierLay(width, height, width / 2, top + (panelTop - top) * 0.55, min(width, 520.0) - 40, panelTop - top - 70, panelTop)
    val locateX = width - 16 - COURIER_ACTION / 2
    val locateY = panelTop - 16 - COURIER_ACTION / 2

    /** Centres of locate, Call and Message (what the ghost finger taps), as x, y pairs. */
    val buttons: DoubleArray = run {
        val msgX = panelLeft + panelW - COURIER_PANEL_PAD - COURIER_ACTION / 2
        doubleArrayOf(locateX, locateY, msgX - COURIER_ACTION - COURIER_ACTION_GAP, panelTop + COURIER_ROW_CY, msgX, panelTop + COURIER_ROW_CY)
    }
}

/** Everything one frame shows. */
internal data class CourierFrame(
    val t: Double,
    val cam: CourierPose,
    val follow: Boolean,
    val vanD: Double,
    val vanA: Double,
    val travEnd: Double,
    val travA: Double,
    val arrived: Double,
    val toast: Double,
    val calling: Double,
    val doorPulse: Double,
    val dip: Double,
    val live: Double,
    val sent: String,
)

/** A value that rises to 1, holds, and falls (the toasts), or holds still. Times in seconds. */
internal class CourierPulse(private val from: Double, private val start: Double, private val hold: Double, private val down: Double) {
    fun at(now: Double): Double {
        if (hold < 0) return from
        val e = now - start
        if (e < 0.3) return M.lerp(from, 1.0, M.easeOutCubic(M.clamp01(e / 0.3)))
        if (e < 0.3 + hold) return 1.0
        return 1 - M.clamp01((e - 0.3 - hold) / down)
    }

    companion object {
        fun constant(v: Double) = CourierPulse(v, -1e9, -1.0, 0.0)
    }
}

internal class CourierCamAnim(val from: CourierPose, val to: CourierPose, val start: Double, val duration: Double)
internal class CourierDecay(val x: Double, val y: Double, val vx: Double, val vy: Double, val start: Double)

/** What a finger set: written only by touches, read every frame. Times are seconds on the frame clock. */
internal class CourierTouch {
    var origin = 0.0
    var manual = false
    var camBase = CourierPose(0.0, 0.0, 0.0, 0.0)
    var camAnim: CourierCamAnim? = null
    var decay: CourierDecay? = null
    var follow = true
    var vanD = 0.0
    var travEnd = 0.0
    var arrived = 0.0
    var toast = CourierPulse.constant(0.0)
    var calling = CourierPulse.constant(0.0)
    var pauseT = 0.0
    var liveFrom = 0.0
    var liveStart = -1e9
    var dialog = 0
    var sent = COURIER_QUICK_REPLIES[0]
    var panning = false
    var pinching = false
    var lastActivity = -1e9

    /** The finger's camera now: the base, a glide, and a fling's decay on top. */
    fun camera(now: Double): CourierPose {
        var c = camBase
        camAnim?.let { a ->
            val k = if (a.duration <= 0) 1.0 else M.easeBezier((now - a.start) / a.duration)
            c = CourierPose.lerp(a.from, a.to, k)
        }
        decay?.let { d ->
            val e = M.decayTravel(now - d.start)
            c = c.copy(
                x = min(max(d.x + d.vx * e, CourierCity.CITY_X0), CourierCity.CITY_X1),
                y = min(max(d.y + d.vy * e, CourierCity.CITY_Y0), CourierCity.CITY_Y1),
            )
        }
        return c
    }

    fun setCamera(c: CourierPose) {
        camBase = c
        camAnim = null
        decay = null
    }

    // phase: 0 manual, 1 returning (phaseK, phaseRs), 2 auto (phaseT)
    private var phaseK = 0.0
    private var phaseRs = 0.0
    private var phaseT = 0.0

    private fun phase(now: Double, film: Boolean): Int {
        if (!manual) {
            if (!film) return 0
            phaseT = max(0.0, now - origin) % M.LOOP
            return 2
        }
        if (!film || panning || pinching || dialog != 0) return 0
        val quiet = now - lastActivity - M.idle
        if (quiet <= 0) return 0
        if (quiet < M.returnDur) {
            phaseK = quiet / M.returnDur
            phaseRs = lastActivity + M.idle
            return 1
        }
        phaseT = (quiet - M.returnDur) % M.LOOP
        return 2
    }

    private fun filmFrame(t: Double, g: CourierGeometry) = CourierFrame(
        t = t, cam = M.filmCamera(t, g.lay), follow = M.filmFollow(t), vanD = M.filmVanD(t), vanA = M.filmVanA(t),
        travEnd = M.filmTravEnd(t), travA = M.filmTravA(t), arrived = M.filmArrived(t), toast = M.filmToast(t),
        calling = M.filmCall(t), doorPulse = M.filmDoorPulse(t), dip = 0.0, live = 1.0, sent = COURIER_QUICK_REPLIES[0],
    )

    private fun manualFrame(now: Double): CourierFrame {
        val live = liveFrom * (1 - M.clamp01((now - liveStart) / 0.22))
        return CourierFrame(
            t = pauseT, cam = camera(now), follow = follow, vanD = vanD, vanA = 1.0, travEnd = travEnd, travA = 1.0,
            arrived = arrived, toast = toast.at(now), calling = calling.at(now), doorPulse = 0.0, dip = 0.0, live = live, sent = sent,
        )
    }

    fun frame(now: Double, film: Boolean, g: CourierGeometry): CourierFrame {
        return when (phase(now, film)) {
            2 -> filmFrame(phaseT, g)
            0 -> if (manual) manualFrame(now) else filmFrame(0.0, g)
            else -> {
                // back to the poster: the camera eases home while the van and the copy dip out and come back reset
                val k = phaseK
                val f = manualFrame(phaseRs)
                val rest = filmFrame(0.0, g)
                val e = M.easeInOutCubic(k)
                val d = sin(Math.PI * k)
                val dip = d * d
                val second = k >= 0.5
                rest.copy(
                    cam = CourierPose.lerp(f.cam, rest.cam, e),
                    dip = dip,
                    vanA = 1 - dip,
                    travA = 1 - dip,
                    vanD = if (second) 0.0 else f.vanD,
                    travEnd = if (second) 0.0 else f.travEnd,
                    arrived = if (second) 0.0 else f.arrived * (1 - M.clamp01(k * 2)),
                    toast = f.toast * (1 - M.clamp01(k * 3)),
                    calling = f.calling * (1 - M.clamp01(k * 3)),
                    doorPulse = 0.0,
                    live = 0.0,
                    follow = true,
                    t = f.t,
                    sent = f.sent,
                )
            }
        }
    }

    /** A touch pauses the film where it is (the van stops; the map is the finger's). */
    fun pause(now: Double, film: Boolean, g: CourierGeometry) {
        val ph = phase(now, film)
        if (ph == 0 && manual) {
            setCamera(camera(now))
        } else {
            val v = frame(now, film, g)
            val wasAuto = ph == 2 || !manual
            manual = true
            setCamera(v.cam)
            follow = v.follow
            vanD = v.vanD
            travEnd = v.travEnd
            arrived = v.arrived
            toast = CourierPulse.constant(v.toast)
            calling = CourierPulse.constant(v.calling)
            pauseT = v.t
            if (wasAuto && film) {
                liveFrom = 1.0
                liveStart = now
            } else {
                liveFrom = 0.0
            }
            sent = v.sent
        }
        lastActivity = now
    }
}
