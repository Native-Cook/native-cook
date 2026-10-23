package com.ngocdevv.nativecook.blocks.lists.raceresultstandings

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The 10-second autoplay loop is the race replay. It rests at 0.00-0.80, lifts the
 * circuit through replay time 0.00-0.85, launches at 1.95, runs six overtakes from
 * 2.55-5.30, and takes the flag at 5.90. From 6.15-7.10 the cars fade, the circuit
 * flies home, and the header relights; everything is still by 7.00 and the replay
 * clock stops at 7.10 (loop time 7.90). Loop time 7.90-10.00 returns to the exact
 * opening rest frame.
 */
internal object RaceResultStandingsMotion {
    const val LOOP: Double = 10.0
    const val RESUME_AFTER: Double = 2000.0
    const val SWITCH: Double = 1.7
    const val LAP: Double = 1.3
    const val LAP_END: Double = 1.15

    data class Tail(val lag: Double, val width: Double, val alpha: Double)

    val TAIL: Array<Tail> = arrayOf(
        Tail(lag = 0.03, width = 2.6, alpha = 1.0),
        Tail(lag = 0.06, width = 2.1, alpha = 0.8),
        Tail(lag = 0.09, width = 1.6, alpha = 0.6),
        Tail(lag = 0.12, width = 1.15, alpha = 0.4),
        Tail(lag = 0.15, width = 0.7, alpha = 0.22),
    )

    const val GLOW_LAG: Double = 0.08
    const val FL_ROLL: Double = 0.3

    data class Press(
        val down: Double,
        val up: Double,
        val scale: Double,
        val chevronOpen: Double,
    )

    val PRESS: Press = Press(down = 0.1, up = 0.12, scale = 0.97, chevronOpen = 1.5)

    data class WordWindow(val start: Double, val dur: Double)

    data class StaggeredWindow(val start: Double, val step: Double, val dur: Double)

    data class Windows(
        val word: WordWindow,
        val podium: StaggeredWindow,
        val points: StaggeredWindow,
        val row: StaggeredWindow,
        val badge: BadgeWindow,
    )

    data class BadgeWindow(val from: Double, val to: Double)

    val WINDOWS: Windows = Windows(
        word = WordWindow(start = 0.0, dur = 0.3),
        podium = StaggeredWindow(start = 0.06, step = 0.07, dur = 0.32),
        points = StaggeredWindow(start = 0.3, step = 0.07, dur = 0.32),
        row = StaggeredWindow(start = 0.08, step = 0.07, dur = 1.25),
        badge = BadgeWindow(from = 0.35, to = 0.65),
    )

    data class SpringSpec(val omega: Double, val zeta: Double)

    val ROW_SPRING: SpringSpec = SpringSpec(omega = 7.2, zeta = 0.78)

    data class Row(
        val laneRise: Double,
        val laneFall: Double,
        val leanMax: Double,
        val leanSpeed: Double,
        val dimFall: Double,
        val sinkFall: Double,
        val liftRise: Double,
        val trailPerSpeed: Double,
        val trailAlphaSpeed: Double,
        val trailAlpha: Double,
        val roll: Double,
    )

    val ROW: Row = Row(
        laneRise = 10.0,
        laneFall = -4.0,
        leanMax = 1.6,
        leanSpeed = 600.0,
        dimFall = 0.6,
        sinkFall = 0.03,
        liftRise = 0.025,
        trailPerSpeed = 0.05,
        trailAlphaSpeed = 500.0,
        trailAlpha = 0.85,
        roll = 0.62,
    )

    data class Drag(
        val span: Double,
        val commitAt: Double,
        val commitVelocity: Double,
        val activeOffset: Double,
        val failOffset: Double,
        val rubber: Double,
        val rubberFalloff: Double,
    )

    val DRAG: Drag = Drag(
        span = 0.6,
        commitAt = 0.5,
        commitVelocity = 500.0,
        activeOffset = 14.0,
        failOffset = 12.0,
        rubber = 0.55,
        rubberFalloff = 120.0,
    )

    data class Settle(val duration: Double, val dampingRatio: Double)

    val SETTLE: Settle = Settle(duration = 400.0, dampingRatio = 0.8)

    const val REDUCED_FADE: Double = 100.0

    data class Flash(val from: Double, val to: Double, val stroke: Double)

    val FLASH: Flash = Flash(from = 2.0, to = 13.0, stroke = 1.4)

    fun clamp01(v: Double): Double {
        return when {
            v < 0.0 -> 0.0
            v > 1.0 -> 1.0
            else -> v
        }
    }

    fun seg(t: Double, a: Double, b: Double): Double {
        return clamp01((t - a) / (b - a))
    }

    fun lerp(a: Double, b: Double, p: Double): Double {
        return a + (b - a) * p
    }

    private fun bezier(p: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val x = clamp01(p)
        if (x == 0.0 || x == 1.0) {
            return x
        }
        var low = 0.0
        var high = 1.0
        repeat(18) {
            val m = (low + high) / 2.0
            val u = 1.0 - m
            val at = 3.0 * u * u * m * x1 + 3.0 * u * m * m * x2 + m * m * m
            if (at < x) {
                low = m
            } else {
                high = m
            }
        }
        val m = (low + high) / 2.0
        val u = 1.0 - m
        return 3.0 * u * u * m * y1 + 3.0 * u * m * m * y2 + m * m * m
    }

    fun easeOut(p: Double): Double {
        return bezier(p = p, x1 = 0.23, y1 = 1.0, x2 = 0.32, y2 = 1.0)
    }

    fun spring(tau: Double, s: SpringSpec): Double {
        if (tau <= 0.0) {
            return 0.0
        }
        val wd = s.omega * sqrt(1.0 - s.zeta * s.zeta)
        val env = exp(-s.zeta * s.omega * tau)
        return 1.0 - env * (cos(wd * tau) + ((s.zeta * s.omega) / wd) * sin(wd * tau))
    }

    fun rowProgress(u: Double, dest: Int): Double {
        val local = (u * SWITCH - WINDOWS.row.start - WINDOWS.row.step * dest.toDouble()) / WINDOWS.row.dur
        if (local <= 0.0) {
            return 0.0
        }
        if (local >= 1.0) {
            return 1.0
        }
        return spring(local * WINDOWS.row.dur, ROW_SPRING)
    }

    fun rowSpeed(u: Double, dest: Int): Double {
        val local = (u * SWITCH - WINDOWS.row.start - WINDOWS.row.step * dest.toDouble()) / WINDOWS.row.dur
        if (local <= 0.0 || local >= 1.0) {
            return 0.0
        }
        val tau = local * WINDOWS.row.dur
        val h = 0.004
        val before = max(0.0, tau - h)
        return (spring(tau + h, ROW_SPRING) - spring(before, ROW_SPRING)) / (tau + h - before)
    }

    private fun windowed(u: Double, start: Double, dur: Double): Double {
        return easeOut(seg(u * SWITCH, start, start + dur))
    }

    fun wordProgress(u: Double): Double {
        return windowed(u, WINDOWS.word.start, WINDOWS.word.dur)
    }

    fun podiumProgress(u: Double, slot: Int): Double {
        return windowed(u, WINDOWS.podium.start + WINDOWS.podium.step * slot.toDouble(), WINDOWS.podium.dur)
    }

    fun pointsProgress(u: Double, slot: Int): Double {
        return windowed(u, WINDOWS.points.start + WINDOWS.points.step * slot.toDouble(), WINDOWS.points.dur)
    }

    fun towards(from: Int, p: Double): Double {
        return if (from == 0) p else 1.0 - p
    }

    fun lapPlace(lap: Double): Double {
        if (lap <= 0.0) {
            return 0.0
        }
        if (lap >= 1.0) {
            return 1.0
        }
        return lap - sin(2.0 * Math.PI * lap) / (2.0 * Math.PI)
    }

    fun lineFlash(lap: Double): Double {
        if (lap < 1.0 || lap >= LAP_END) {
            return 0.0
        }
        return (lap - 1.0) / (LAP_END - 1.0)
    }

    fun cometAlpha(lap: Double): Double {
        if (lap <= 0.0 || lap >= LAP_END) {
            return 0.0
        }
        return clamp01(lap / 0.06) * (1.0 - clamp01((lap - 1.0) / (LAP_END - 1.0)))
    }

    fun springVelocity(tau: Double, s: SpringSpec): Double {
        if (tau <= 0.0) {
            return 0.0
        }
        val wd = s.omega * sqrt(1.0 - s.zeta * s.zeta)
        return ((s.omega * s.omega) / wd) * exp(-s.zeta * s.omega * tau) * sin(wd * tau)
    }

    fun smoothstep(p: Double): Double {
        val value = clamp01(p)
        return value * value * (3.0 - 2.0 * value)
    }

    const val REPLAY_AT: Double = 0.8

    data class Replay(
        val dotsIn: DoubleArray,
        val lightsOn: Double,
        val go: Double,
        val finish: Double,
        val close: Double,
        val dotsOut: Double,
        val dim: Dim,
        val end: Double,
    )

    data class Dim(val inside: Double, val outFrom: Double, val out: Double)

    val REPLAY: Replay = Replay(
        dotsIn = doubleArrayOf(0.55, 0.9),
        lightsOn = 0.85,
        go = 1.95,
        finish = 5.9,
        close = 6.15,
        dotsOut = 0.25,
        dim = Dim(inside = 0.35, outFrom = 0.4, out = 0.45),
        end = 7.1,
    )

    data class Point(val x: Double, val y: Double)

    data class MapMotion(
        val scale: Double,
        val from: Point,
        val to: Point,
        val tilt: Double,
        val perspective: Double,
        val spring: SpringSpec,
        val settle: Double,
        val hiResFrom: Double,
        val press: Double,
    )

    val MAP: MapMotion = MapMotion(
        scale = 2.35,
        from = Point(x = 340.0, y = 194.0),
        to = Point(x = 201.0, y = 240.0),
        tilt = 10.0,
        perspective = 700.0,
        spring = SpringSpec(omega = 9.0, zeta = 0.8),
        settle = 0.85,
        hiResFrom = 0.25,
        press = 0.97,
    )

    data class Glow(val radius: Double, val blur: Double, val alpha: Double)

    data class Pill(val w: Double, val h: Double)

    data class Lights(
        val count: Int,
        val step: Double,
        val on: Double,
        val off: Double,
        val cx: Double,
        val cy: Double,
        val pitch: Double,
        val r: Double,
        val pill: Pill,
        val glow: Glow,
        val housingIn: DoubleArray,
        val housingOut: DoubleArray,
    )

    val LIGHTS: Lights = Lights(
        count = 5,
        step = 0.18,
        on = 0.06,
        off = 0.08,
        cx = 201.0,
        cy = 124.0,
        pitch = 15.0,
        r = 4.5,
        pill = Pill(w = 84.0, h = 17.0),
        glow = Glow(radius = 2.0, blur = 4.0, alpha = 0.45),
        housingIn = doubleArrayOf(0.55, 0.8),
        housingOut = doubleArrayOf(0.35, 0.65),
    )

    data class Race(
        val laps: Double,
        val launch: Double,
        val gridGap: Double,
        val raceGap: Double,
        val gapSettle: Double,
        val pass: Double,
        val lateral: Double,
        val gridStagger: Double,
        val staggerOut: Double,
        val coolDelay: Double,
        val coolDown: Double,
        val trailLag: Double,
    )

    val RACE: Race = Race(
        laps = 2.0,
        launch = 0.35,
        gridGap = 0.03,
        raceGap = 0.022,
        gapSettle = 1.2,
        pass = 0.35,
        lateral = 1.0,
        gridStagger = 0.8,
        staggerOut = 0.6,
        coolDelay = 0.25,
        coolDown = 0.5,
        trailLag = 0.09,
    )

    data class Car(val core: Double, val centre: Double, val glow: Double, val glowBlur: Double, val glowAlpha: Double)

    val CAR: Car = Car(core = 2.9, centre = 1.2, glow = 7.0, glowBlur = 4.0, glowAlpha = 0.55)

    data class Trail(val part: Double, val width: Double, val alpha: Double)

    val TRAIL: Array<Trail> = arrayOf(
        Trail(part = 1.0, width = 3.4, alpha = 0.22),
        Trail(part = 0.6, width = 2.6, alpha = 0.45),
        Trail(part = 0.28, width = 2.0, alpha = 0.9),
    )

    data class Tag(
        val size: Double,
        val padX: Double,
        val h: Double,
        val above: Double,
        val rise: Double,
        val radius: Double,
        val show: DoubleArray,
        val hide: DoubleArray,
    )

    val TAG: Tag = Tag(
        size = 7.5,
        padX = 4.0,
        h = 12.0,
        above = 9.0,
        rise = 3.0,
        radius = 6.0,
        show = doubleArrayOf(0.3, 0.14),
        hide = doubleArrayOf(0.55, 0.7),
    )

    data class FinishFlash(val dur: Double, val from: Double, val to: Double, val stroke: Double)

    val FINISH_FLASH: FinishFlash = FinishFlash(dur = 0.35, from = 2.0, to = 13.0, stroke = 1.4)

    data class Pass(val at: Double, val up: Int, val down: Int)

    val REWIND: Array<Pass> = arrayOf(
        Pass(at = 0.15, up = 2, down = 1),
        Pass(at = 0.22, up = 4, down = 3),
    )

    val PASSES: Array<Pass> = arrayOf(
        Pass(at = 2.55, up = 1, down = 2),
        Pass(at = 3.05, up = 3, down = 4),
        Pass(at = 3.6, up = 1, down = 0),
        Pass(at = 4.15, up = 5, down = 4),
        Pass(at = 4.75, up = 0, down = 1),
        Pass(at = 5.3, up = 4, down = 5),
    )

    private val REPLAY_EVENTS: Array<Pass> = REWIND + PASSES

    val GRID_SLOT: IntArray = intArrayOf(0, 2, 1, 4, 3, 5)

    private val raceTime = REPLAY.finish - REPLAY.go
    private val raceVelocity = RACE.laps / (raceTime - RACE.launch * (1.0 - exp(-raceTime / RACE.launch)))
    private val coolAt = raceTime + RACE.coolDelay
    private val coolDistance = raceVelocity * (coolAt - RACE.launch * (1.0 - exp(-coolAt / RACE.launch)))
    private val coolVelocity = raceVelocity * (1.0 - exp(-coolAt / RACE.launch))

    fun leaderLaps(r: Double): Double {
        val tau = r - REPLAY.go
        if (tau <= 0.0) {
            return 0.0
        }
        if (tau <= coolAt) {
            return raceVelocity * (tau - RACE.launch * (1.0 - exp(-tau / RACE.launch)))
        }
        return coolDistance + coolVelocity * RACE.coolDown * (1.0 - exp(-(tau - coolAt) / RACE.coolDown))
    }

    fun leaderPace(r: Double): Double {
        val tau = r - REPLAY.go
        if (tau <= 0.0) {
            return 0.0
        }
        if (tau <= coolAt) {
            return 1.0 - exp(-tau / RACE.launch)
        }
        return (coolVelocity / raceVelocity) * exp(-(tau - coolAt) / RACE.coolDown)
    }

    fun trackRank(index: Int, r: Double): Double {
        var rank = GRID_SLOT[index].toDouble()
        for (pass in PASSES) {
            val direction = when (index) {
                pass.up -> -1
                pass.down -> 1
                else -> 0
            }
            if (direction != 0) {
                rank += direction * smoothstep((r - pass.at + RACE.pass) / (2.0 * RACE.pass))
            }
        }
        return rank
    }

    fun raceGap(r: Double): Double {
        return lerp(RACE.gridGap, RACE.raceGap, smoothstep(seg(r, REPLAY.go, REPLAY.go + RACE.gapSettle)))
    }

    fun carLaps(index: Int, r: Double): Double {
        return leaderLaps(r) - raceGap(r) * trackRank(index, r)
    }

    fun carLateral(index: Int, r: Double): Double {
        val grid = if (GRID_SLOT[index] % 2 == 0) RACE.gridStagger else -RACE.gridStagger
        var side = grid * (1.0 - smoothstep(seg(r, REPLAY.go, REPLAY.go + RACE.staggerOut)))
        for (pass in PASSES) {
            val wave = sin(Math.PI * seg(r, pass.at - RACE.pass, pass.at + RACE.pass))
            if (index == pass.up) {
                side += RACE.lateral * wave
            } else if (index == pass.down) {
                side -= RACE.lateral * 0.5 * wave
            }
        }
        return side
    }

    fun carAlpha(r: Double): Double {
        return smoothstep(seg(r, REPLAY.dotsIn[0], REPLAY.dotsIn[1])) *
            (1.0 - smoothstep(seg(r, REPLAY.close, REPLAY.close + REPLAY.dotsOut)))
    }

    private fun finishTimes(): DoubleArray {
        return DoubleArray(6) {
            slot ->
            var low = REPLAY.finish - 0.01
            var high = REPLAY.close
            repeat(40) {
                val middle = (low + high) / 2.0
                if (leaderLaps(middle) - RACE.raceGap * slot >= RACE.laps) {
                    high = middle
                } else {
                    low = middle
                }
            }
            high
        }
    }

    val FINISH_AT: DoubleArray = finishTimes()

    fun tagAlpha(index: Int, r: Double): Double {
        var alpha = 0.0
        for (pass in PASSES) {
            if (pass.up != index) {
                continue
            }
            val value = easeOut(seg(r, pass.at - TAG.show[0], pass.at - TAG.show[1])) *
                (1.0 - easeOut(seg(r, pass.at + TAG.hide[0], pass.at + TAG.hide[1])))
            alpha = max(alpha, value)
        }
        return alpha
    }

    val REWIND_SPRING: SpringSpec = SpringSpec(omega = 12.0, zeta = 0.999)
    const val REWIND_DUR: Double = 0.6

    data class ReplayRow(val y: Double, val speed: Double, val bump: Double, val direction: Int)

    fun replayRow(index: Int, r: Double): ReplayRow {
        var row = index.toDouble()
        var speed = 0.0
        var bump = 0.0
        var direction = 0
        for ((eventIndex, event) in REPLAY_EVENTS.withIndex()) {
            val change = when (index) {
                event.up -> -1
                event.down -> 1
                else -> 0
            }
            if (change == 0) {
                continue
            }
            val tau = r - event.at
            if (tau <= 0.0) {
                continue
            }
            if (eventIndex < REWIND.size) {
                if (tau >= REWIND_DUR) {
                    row += change
                } else {
                    row += change * spring(tau, REWIND_SPRING)
                    direction = change
                }
                continue
            }
            if (tau >= WINDOWS.row.dur) {
                row += change
                continue
            }
            val progress = spring(tau, ROW_SPRING)
            row += change * progress
            speed += change * springVelocity(tau, ROW_SPRING)
            bump = sin(Math.PI * clamp01(progress))
            direction = change
        }
        return ReplayRow(y = row, speed = speed, bump = bump, direction = direction)
    }

    fun mapOpen(r: Double): Double {
        if (r <= 0.0 || r >= REPLAY.end) {
            return 0.0
        }
        val out = if (r >= MAP.settle) 1.0 else spring(r, MAP.spring)
        val back = when {
            r <= REPLAY.close -> 0.0
            r - REPLAY.close >= MAP.settle -> 1.0
            else -> spring(r - REPLAY.close, MAP.spring)
        }
        return out - back
    }

    fun headerDim(r: Double): Double {
        if (r <= 0.0 || r >= REPLAY.end) {
            return 0.0
        }
        val from = REPLAY.close + REPLAY.dim.outFrom
        return easeOut(seg(r, 0.0, REPLAY.dim.inside)) *
            (1.0 - easeOut(seg(r, from, from + REPLAY.dim.out)))
    }

    fun lightOn(index: Int, r: Double): Double {
        val at = REPLAY.lightsOn + LIGHTS.step * index
        return easeOut(seg(r, at, at + LIGHTS.on)) *
            (1.0 - seg(r, REPLAY.go, REPLAY.go + LIGHTS.off))
    }

    fun lightsHousing(r: Double): Double {
        return smoothstep(seg(r, LIGHTS.housingIn[0], LIGHTS.housingIn[1])) *
            (1.0 - smoothstep(seg(r, REPLAY.go + LIGHTS.housingOut[0], REPLAY.go + LIGHTS.housingOut[1])))
    }

    data class ReplayPoints(val alpha: Double, val rise: Double)

    val POINTS_HIDE: DoubleArray = doubleArrayOf(0.1, 0.35)

    fun replayPoints(slot: Int, r: Double): ReplayPoints {
        if (r <= 0.0) {
            return ReplayPoints(alpha = 1.0, rise = 0.0)
        }
        val at = FINISH_AT[slot]
        if (r < at) {
            return ReplayPoints(alpha = 1.0 - easeOut(seg(r, POINTS_HIDE[0], POINTS_HIDE[1])), rise = 0.0)
        }
        val progress = easeOut(seg(r, at, at + WINDOWS.points.dur))
        return ReplayPoints(alpha = progress, rise = 1.0 - progress)
    }

    /** Added to a replay place once every finisher's points are visible. */
    const val REPLAY_FLAGGED: Int = 6

    /**
     * Driver [index]'s running place for accessibility. Exact ties use driver index, so a crossing
     * never exposes duplicate places. A negative value means that no replay is running.
     */
    fun replayPlace(index: Int, r: Double): Int {
        if (r <= 0.0) {
            return -1
        }
        val row = replayRow(index, r).y
        var place = 0
        for (other in RaceResultStandingsTheme.drivers.indices) {
            if (other == index) {
                continue
            }
            val otherRow = replayRow(other, r).y
            if (otherRow < row || otherRow == row && other < index) {
                place++
            }
        }
        return if (r >= FINISH_AT[5]) place + REPLAY_FLAGGED else place
    }

    /** Fills all accessibility places from one set of row poses without per-frame allocation. */
    fun replayPlaces(r: Double, rows: DoubleArray, places: IntArray) {
        if (r <= 0.0) {
            places.fill(-1)
            return
        }
        for (index in RaceResultStandingsTheme.drivers.indices) {
            rows[index] = replayRow(index, r).y
        }
        val flagged = if (r >= FINISH_AT[5]) REPLAY_FLAGGED else 0
        for (index in RaceResultStandingsTheme.drivers.indices) {
            var place = 0
            for (other in RaceResultStandingsTheme.drivers.indices) {
                if (other == index) {
                    continue
                }
                if (rows[other] < rows[index] || rows[other] == rows[index] && other < index) {
                    place++
                }
            }
            places[index] = place + flagged
        }
    }

    fun finishFlash(r: Double): Double {
        val progress = seg(r, REPLAY.finish, REPLAY.finish + FINISH_FLASH.dur)
        return if (progress <= 0.0 || progress >= 1.0) 0.0 else progress
    }

    fun frameAt(t: Double): RaceResultStandingsFrame {
        val replay = t - REPLAY_AT
        val replayClock = if (replay > 0.0 && replay < REPLAY.end) replay else 0.0
        return RaceResultStandingsFrame(rp = replayClock)
    }
}
