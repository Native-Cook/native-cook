package com.ngocdevv.nativecook.blocks.gestures.yoyopullrefresh

import kotlin.math.*

/** The committed RN timeline. All animation quantities use seconds and dp. */
internal object YoyoPullRefreshMotion {
    const val LOOP = 6.0
    val labels = listOf("Pull to refresh", "Refreshing…", "Up to date", "Pull to refresh")
    fun seg(t: Double, a: Double, b: Double) = ((t - a) / (b - a)).coerceIn(0.0, 1.0)
    fun out(x: Double) = 1 - (1 - x).pow(3)
    fun quad(x: Double) = if (x < 0.5) 2 * x * x else 1 - (-2 * x + 2).pow(2) / 2
    fun spin(t: Double): Double = when {
        t < .8 -> 0.0; t < 1.7 -> .8; t < 2.05 -> .8 + 4.2 * seg(t, 1.7, 2.05)
        t < 3.6 -> 5.0; t < 4.2 -> 5 - 4 * seg(t, 3.6, 4.2); else -> 1.0
    }
    fun turns(t: Double): Double {
        if (t <= .8) return 0.0
        var a = .8 * (min(t, 1.7) - .8)
        if (t <= 1.7) return a
        val s = seg(t, 1.7, 2.05); a += .35 * (.8 * s + 2.1 * s * s)
        if (t <= 2.05) return a
        a += 5 * (min(t, 3.6) - 2.05)
        if (t <= 3.6) return a
        val q = seg(t, 3.6, 4.2); a += .6 * (5 * q - 2 * q * q)
        return if (t <= 4.2) a else a + (t - 4.2)
    }
    fun pullY(d: Double) = -90 + 1.3 * max(0.0, d)
    fun beatD(t: Double, from: Double): Double {
        val u = t - 1.7
        if (t < 3.75) return 190 + (from - 190) * exp(-7 * u) * cos(14 * u) + 20 * exp(-7 * u) * sin(14 * u)
        return if (t < 4.25) 190 * (1 - quad(seg(t, 3.75, 4.25))) else 0.0
    }
    fun beatY(t: Double, from: Double): Double = when {
        t < 2.05 -> from + (258 - from) * seg(t, 1.7, 2.05).pow(2)
        t < 3.6 -> 258 + 12 * exp(-6 * (t - 2.05)) * sin(18 * (t - 2.05))
        t < 4.2 -> 258 - 348 * quad(seg(t, 3.6, 4.2)); else -> -90.0
    }
    data class Status(val from: Int = 0, val to: Int = 0, val u: Double = 1.0) {
        val shown get() = if (u < .5) from else to
        val alpha get() = if (u < .5) 1 - 2 * u else 2 * u - 1
    }
    fun statusAt(t: Double): Status {
        val times = doubleArrayOf(-1.0, 1.75, 3.7, 5.3)
        val k = (1..3).lastOrNull { t >= times[it] } ?: 0
        return if (k == 0) Status() else Status(k - 1, k, seg(t, times[k], times[k] + .24))
    }
    data class Frame(val d: Double = 0.0, val y: Double = -90.0, val turns: Double = 0.0,
                     val spin: Double = 0.0, val pulse: Double = 0.0, val status: Status = Status(),
                     val finger: Double = 0.0, val alpha: Double = 1.0)
    fun loopFrame(t: Double): Frame {
        val d = when { t < .8 -> 0.0; t < 1.7 -> 190 * out(seg(t, .8, 1.7)); else -> beatD(t, 190.0) }
        return Frame(d, if (t < 1.7) pullY(d) else beatY(t, 157.0), turns(t), spin(t), t, statusAt(t),
                     seg(t, .7, .82) * (1 - seg(t, 1.7, 1.82)))
    }
    fun reducedFrame(u: Double) = if (u < 0) Frame(alpha = 0.0) else Frame(y = 258.0, turns = .5 * u,
        spin = .5, pulse = u, status = statusAt(min(6.0, 1.7 + u)), alpha = seg(u, 0.0, .2) * (1 - seg(u, 1.9, 2.1)))
    fun dragD(total: Double, overflow: Double): Double {
        if (total > 0) return 190 * (1 - exp(-total / 190))
        if (total >= -overflow) return total
        val over = -overflow - total; return -overflow - over / (1 + over / 120)
    }
}

/** Same takeover, scrolled-end rubber band, short release and idle restart as yoyoMotion.ts. */
internal class YoyoPullRefreshTouch {
    enum class Mode { REST, DRAG, SPRING, REFRESH }
    var manual = false; var touching = false
    var loopStart = 0.0; var lastTouch = -100.0; var mode = Mode.REST
    var base = 0.0; var drag = 0.0; var from = 0.0; var to = 0.0; var at = 0.0
    var refAt = -100.0; var refD = 190.0; var refY = 157.0
    var stFrom = 0; var stTo = 0; var stAt = -1.0
    var turns = 0.0; var spin = 0.0; var overflow = 0.0; var rmAt = -1.0; var realRefresh = false
    fun beat(c: Double) = if (mode == Mode.REFRESH) min(1.7 + (c - refAt), 6.0) else -1.0
    fun d(c: Double): Double = when (mode) {
        Mode.DRAG -> YoyoPullRefreshMotion.dragD(base + drag, overflow)
        Mode.SPRING -> from + (to - from) * YoyoPullRefreshMotion.out(YoyoPullRefreshMotion.seg(c, at, at + .35))
        Mode.REFRESH -> YoyoPullRefreshMotion.beatD(beat(c), refD)
        Mode.REST -> from
    }
    fun status(c: Double): YoyoPullRefreshMotion.Status {
        if (stAt >= 0) return YoyoPullRefreshMotion.Status(stFrom, stTo, YoyoPullRefreshMotion.seg(c, stAt, stAt + .24))
        return if (mode == Mode.REFRESH || refAt > -100) YoyoPullRefreshMotion.statusAt(min(1.7 + (c - refAt), 6.0)) else YoyoPullRefreshMotion.Status()
    }
    fun frame(c: Double, reduced: Boolean): YoyoPullRefreshMotion.Frame {
        if (reduced) return YoyoPullRefreshMotion.reducedFrame(if (rmAt >= 0) c - rmAt else -1.0)
        if (!manual) return YoyoPullRefreshMotion.loopFrame(((c - loopStart) % 6 + 6) % 6)
        return YoyoPullRefreshMotion.Frame(d(c), if (mode == Mode.REFRESH) YoyoPullRefreshMotion.beatY(beat(c), refY) else YoyoPullRefreshMotion.pullY(d(c)),
            turns, spin, if (beat(c) >= 0) beat(c) else c, status(c))
    }
    fun takeOver(c: Double) {
        lastTouch = c
        if (manual) return
        val t = ((c - loopStart) % 6 + 6) % 6
        manual = true; turns = YoyoPullRefreshMotion.turns(t); spin = YoyoPullRefreshMotion.spin(t); stAt = -1.0; refAt = -100.0
        when {
            t >= .8 && t < 1.7 -> { mode = Mode.SPRING; from = YoyoPullRefreshMotion.loopFrame(t).d; to = from; at = c }
            t >= 1.7 -> { mode = Mode.REFRESH; refAt = c - (t - 1.7); refD = 190.0; refY = 157.0 }
            else -> { mode = Mode.REST; from = 0.0 }
        }
    }
    fun canGrab(c: Double) = mode != Mode.REFRESH || beat(c) >= 4.25
    fun grab(c: Double) {
        val offset = d(c); val shown = status(c).shown
        if ((mode == Mode.REFRESH || stAt >= 0 || refAt > -100) && shown != 0 && shown != 3) { stFrom = shown; stTo = 0; stAt = c }
        mode = Mode.DRAG
        base = if (offset > 0) -190 * ln(1 - min(offset, 189.99) / 190) else max(offset, -overflow)
        drag = 0.0; touching = true; lastTouch = c
    }
    fun refresh(c: Double, real: Boolean) {
        val offset = d(c); mode = Mode.REFRESH; refAt = c; refD = max(0.0, offset); refY = YoyoPullRefreshMotion.pullY(offset)
        stAt = -1.0; manual = true; realRefresh = real
    }
    fun release(c: Double) {
        val offset = d(c)
        if (offset > 120) refresh(c, true)
        else { mode = Mode.SPRING; from = offset; to = if (offset > 0) 0.0 else max(-overflow, offset); at = c }
        touching = false; lastTouch = c
    }
    fun step(c: Double, dt: Double, autoPlay: Boolean) {
        if (!manual) return
        val next = if (mode == Mode.REFRESH) YoyoPullRefreshMotion.spin(beat(c)) else if (d(c) > 0) .8 else 0.0
        turns += (spin + next) / 2 * dt; spin = next
        if (mode == Mode.SPRING && c >= at + .35) { mode = Mode.REST; from = to }
        if (!autoPlay || touching || c - lastTouch < 2) return
        if (mode == Mode.REST && abs(from) > .01) { mode = Mode.SPRING; to = 0.0; at = c; return }
        val st = status(c)
        if (mode != Mode.DRAG && !(mode == Mode.SPRING && c < at + .35) && abs(d(c)) <= .01 && st.shown % 3 == 0 && st.u >= 1) {
            manual = false; mode = Mode.REST; from = 0.0; loopStart = c; refAt = -100.0; stAt = -1.0; realRefresh = false
        }
    }
}
