package com.ngocdevv.nativecook.blocks.sheets.savedplacesmap

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Closed-form motion values ported directly from motion.ts. */
internal object SavedPlacesMapMotion {
    const val LOOP = 7.5
    const val RESUME_AFTER = 2.0
    const val RISE = 150f
    val CONTROLS_FADE = doubleArrayOf(0.1, 0.5)

    object T {
        const val rise = 1.0
        const val clusterIn = 1.5
        const val press = 2.8
        const val check = 2.9
        const val unpress = 4.0
        const val uncheck = 4.08
        const val fall = 4.75
        const val clusterOut = 4.75
    }

    data class Spring(val omega: Double, val zeta: Double, val amp: Double = 0.0)

    object Springs {
        val sheetRise = Spring(7.5, 0.72)
        val sheetFall = Spring(8.0, 1.0)
        val roll = Spring(20.0, 0.7)
        val pop = Spring(18.0, 0.4, 0.22)
        val press = Spring(22.0, 0.6)
    }

    object Cluster {
        const val dur = 1.0
        const val span = 0.4
        const val glide = 0.6
        const val arc = 0.15
        const val endScale = 0.55
        const val fadeFrom = 0.7
        const val landFrom = 0.75
        const val gulp = 0.06
        const val bubbleFrom = 0.9
    }

    object Grab {
        const val scale = 1.12
        const val lead = 0.15
        const val inDuration = 0.12
        const val outDuration = 0.15
        const val hold = 0.15
    }

    const val TOGGLE_GAP = 0.3

    object Press {
        const val checkbox = 0.9
        const val tile = 0.97
        const val inDuration = 0.1
    }

    object Visit {
        const val fill = 0.15
        val draw = doubleArrayOf(0.05, 0.3)
        const val roll = 0.06
        val badge = doubleArrayOf(0.06, 0.26)
        const val fadeOut = 0.15
        const val reducedFade = 0.2
    }

    object Drag {
        const val rubber = 5.0
        const val project = 0.15
        const val slop = 6f
    }

    data class VisitFrame(
        val fill: Double,
        val draw: Double,
        val roll: Double,
        val badge: Double,
        val pop: Double,
    )

    data class Frame(
        val sheet: Double = 0.0,
        val cluster: Double = 0.0,
        val fill: Double = 0.0,
        val draw: Double = 0.0,
        val roll: Double = 0.0,
        val badge: Double = 0.0,
        val visitPop: Double = 1.0,
        val tapIndex: Int = -1,
        val tapPop: Double = 1.0,
        val press: Double = 0.0,
        val grab: Double = 0.0,
        val pressTarget: Int = SavedPlacesMapController.HIT_CHECKBOX,
    )

    val REST = Frame()

    fun clamp01(value: Double): Double = value.coerceIn(0.0, 1.0)

    fun seg(time: Double, start: Double, end: Double): Double {
        return clamp01((time - start) / (end - start))
    }

    fun lerp(start: Double, end: Double, progress: Double): Double {
        return start + (end - start) * progress
    }

    private fun bezier(progress: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val x = clamp01(progress)
        if (x == 0.0 || x == 1.0) {
            return x
        }
        var low = 0.0
        var high = 1.0
        repeat(18) {
            val middle = (low + high) / 2.0
            val inverse = 1.0 - middle
            val at = 3.0 * inverse * inverse * middle * x1 + 3.0 * inverse * middle * middle * x2 + middle * middle * middle
            if (at < x) {
                low = middle
            } else {
                high = middle
            }
        }
        val middle = (low + high) / 2.0
        val inverse = 1.0 - middle
        return 3.0 * inverse * inverse * middle * y1 + 3.0 * inverse * middle * middle * y2 + middle * middle * middle
    }

    fun easeOut(progress: Double): Double = bezier(progress, 0.23, 1.0, 0.32, 1.0)

    fun easeInOut(progress: Double): Double = bezier(progress, 0.77, 0.0, 0.175, 1.0)

    fun springStep(tau: Double, spring: Spring): Double {
        if (tau <= 0.0) {
            return 0.0
        }
        if (spring.zeta >= 1.0) {
            return 1.0 - exp(-spring.omega * tau) * (1.0 + spring.omega * tau)
        }
        val damped = spring.omega * sqrt(1.0 - spring.zeta * spring.zeta)
        return 1.0 - exp(-spring.zeta * spring.omega * tau) *
            (cos(damped * tau) + spring.zeta * spring.omega / damped * sin(damped * tau))
    }

    fun springValue(tau: Double, start: Double, target: Double, velocity: Double, spring: Spring): Double {
        if (tau <= 0.0) {
            return start
        }
        val displacement = start - target
        if (spring.zeta >= 1.0) {
            return target + exp(-spring.omega * tau) *
                (displacement + (velocity + spring.omega * displacement) * tau)
        }
        val damped = spring.omega * sqrt(1.0 - spring.zeta * spring.zeta)
        val sineCoefficient = (velocity + spring.zeta * spring.omega * displacement) / damped
        return target + exp(-spring.zeta * spring.omega * tau) *
            (displacement * cos(damped * tau) + sineCoefficient * sin(damped * tau))
    }

    fun popScale(tau: Double): Double {
        if (tau <= 0.0 || tau > 1.2) {
            return 1.0
        }
        val spring = Springs.pop
        val damped = spring.omega * sqrt(1.0 - spring.zeta * spring.zeta)
        return 1.0 + spring.amp * exp(-spring.zeta * spring.omega * tau) * sin(damped * tau)
    }

    fun band(sheet: Double): Double {
        if (sheet > 1.0) {
            return 1.0 + (sheet - 1.0) / (1.0 + Drag.rubber * (sheet - 1.0))
        }
        if (sheet < 0.0) {
            return sheet / (1.0 - Drag.rubber * sheet)
        }
        return sheet
    }

    fun sheetAt(time: Double): Double {
        if (time < T.rise) {
            return 0.0
        }
        if (time < T.fall) {
            return springStep(time - T.rise, Springs.sheetRise)
        }
        val tau = time - T.fall
        if (tau > 1.6) {
            return 0.0
        }
        return 1.0 - springStep(tau, Springs.sheetFall)
    }

    fun clusterAt(time: Double): Double {
        if (time < T.fall) {
            return seg(time, T.clusterIn, T.clusterIn + Cluster.dur)
        }
        return 1.0 - seg(time, T.clusterOut, T.clusterOut + Cluster.dur)
    }

    fun pressAt(time: Double): Double {
        fun one(start: Double): Double {
            val dip = start + Press.inDuration
            if (time < start) {
                return 0.0
            }
            if (time < dip) {
                return easeOut(seg(time, start, dip))
            }
            return 1.0 - springStep(time - dip, Springs.press)
        }
        return max(one(T.press), one(T.unpress))
    }

    fun grabAt(time: Double): Double {
        fun one(move: Double): Double {
            val start = move - Grab.lead
            val release = move + Grab.hold
            return easeOut(seg(time, start, start + Grab.inDuration)) *
                (1.0 - easeOut(seg(time, release, release + Grab.outDuration)))
        }
        return max(one(T.rise), one(T.fall))
    }

    fun visitFrame(direction: Int, tau: Double, reduced: Boolean): VisitFrame {
        if (reduced) {
            val progress = seg(tau, 0.0, Visit.reducedFade)
            val value = if (direction == 1) progress else 1.0 - progress
            return VisitFrame(value, value, value, value, 1.0)
        }
        if (direction == 1) {
            return VisitFrame(
                fill = easeOut(seg(tau, 0.0, Visit.fill)),
                draw = easeOut(seg(tau, Visit.draw[0], Visit.draw[1])),
                roll = springStep(tau - Visit.roll, Springs.roll),
                badge = easeOut(seg(tau, Visit.badge[0], Visit.badge[1])),
                pop = popScale(tau - Visit.roll),
            )
        }
        val out = 1.0 - easeOut(seg(tau, 0.0, Visit.fadeOut))
        return VisitFrame(
            fill = out,
            draw = out,
            roll = 1.0 - springStep(tau - 0.02, Springs.roll),
            badge = out,
            pop = 1.0,
        )
    }

    fun glyphProgress(cluster: Double, delay: Double): Double {
        return easeInOut(seg(cluster, delay, delay + Cluster.glide))
    }

    /** The map's 3D pitch driven by the sheet progress (motion.ts TILT). Ref px. */
    object Tilt {
        const val pitch = 60.0
        const val perspective = 2700.0
        const val scale = 1.8
        const val originX = 603.0
        const val originY = 1140.0
        const val lift = -225.0
        const val max = 1.08
        const val norm = 1.55
        val fog = doubleArrayOf(0.25, 1.0)
    }

    object Stand {
        const val start = 0.25
        const val step = 0.07
        const val span = 0.45
        const val back = 1.4
        const val stalk = 96.0
    }

    object Beam {
        const val h = 360f
        const val w = 22f
        const val core = 4f
        const val opacity = 0.9
        const val flare = 4.0
    }

    object Shadow {
        const val len = 150f
        const val w = 30f
        const val angle = 28f
        const val opacity = 0.34
    }

    object Ripple {
        const val d = 44f
        const val grow = 1.9
        const val at = 0.55
    }

    fun tiltOf(sheet: Double): Double = if (sheet < 0.0) 0.0 else if (sheet > Tilt.max) Tilt.max else sheet

    class Projected(val x: Double, val y: Double, val f: Double, val m: Double)

    /** Screen position of a ground point at pitch progress p, its perspective factor and the billboard scale. */
    fun project(x: Double, y: Double, p: Double): Projected {
        val s = 1.0 + (Tilt.scale - 1.0) * p
        val th = Tilt.pitch * p * Math.PI / 180.0
        val dx = (x - Tilt.originX) * s
        val dy = (y - Tilt.originY) * s
        val z = dy * sin(th)
        val f = Tilt.perspective / (Tilt.perspective - z)
        return Projected(
            x = Tilt.originX + dx * f,
            y = Tilt.originY + dy * cos(th) * f + Tilt.lift * p,
            f = f,
            m = (f * s) / (1.0 + (Tilt.norm - 1.0) * p),
        )
    }

    fun backOut(p: Double): Double {
        val c = Stand.back
        val q = clamp01(p) - 1.0
        return 1.0 + (c + 1.0) * q * q * q + c * q * q
    }

    fun standLinear(p: Double, rank: Int): Double {
        val a = Stand.start + Stand.step * rank
        return seg(p, a, a + Stand.span)
    }

    fun standOf(p: Double, rank: Int): Double = backOut(standLinear(p, rank))

    class Disc(val x: Double, val y: Double, val m: Double, val st: Double)

    /** Screen centre of a café's disc and its billboard scale for its ground anchor (cx, cy). */
    fun discAt(cx: Double, cy: Double, p: Double, rank: Int, radius: Double): Disc {
        val g = project(cx, cy, p)
        val st = standOf(p, rank)
        return Disc(g.x, g.y - st * (Stand.stalk + radius) * g.m, g.m, st)
    }

    fun sample(time: Double, reduced: Boolean = false): Frame {
        if (reduced) {
            return REST
        }
        val loopTime = ((time % LOOP) + LOOP) % LOOP
        val visitDirection: Int
        val visitTau: Double
        if (loopTime >= T.check && loopTime < T.uncheck) {
            visitDirection = 1
            visitTau = loopTime - T.check
        } else if (loopTime >= T.uncheck) {
            visitDirection = 0
            visitTau = loopTime - T.uncheck
        } else {
            visitDirection = 0
            visitTau = 99.0
        }
        val visit = visitFrame(visitDirection, visitTau, reduced = false)
        return Frame(
            sheet = sheetAt(loopTime),
            cluster = clusterAt(loopTime),
            fill = visit.fill,
            draw = visit.draw,
            roll = visit.roll,
            badge = visit.badge,
            visitPop = visit.pop,
            press = pressAt(loopTime),
            grab = grabAt(loopTime),
        )
    }
}
