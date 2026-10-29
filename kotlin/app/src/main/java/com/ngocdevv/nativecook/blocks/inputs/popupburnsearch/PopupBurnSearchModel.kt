package com.ngocdevv.nativecook.blocks.inputs.popupburnsearch

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A point and a rectangle in dp, as doubles (the Swift reference works in CGFloat). */
data class PbsPoint(val x: Double, val y: Double)

data class PbsRect(val x: Double, val y: Double, val w: Double, val h: Double) {
    val minX get() = x
    val minY get() = y
    val maxX get() = x + w
    val maxY get() = y + h
    val midX get() = x + w / 2
    val midY get() = y + h / 2
    fun contains(px: Double, py: Double) = px >= x && px <= maxX && py >= y && py <= maxY
}

class PbsParticle(val x: Double, val y: Double, val size: Double, val angle: Double, val color: Int, val alpha: Double)

/** Timeline, geometry and touch logic of Popup Burn Search (a port of PopupBurnSearchModel.swift). */
object PbsModel {
    const val LOOP = 9.0
    const val NEVER = 1e9

    const val QUERY = "WHAT SHOULD I COOK TONIGHT?"
    val ANSWERS = listOf(
        listOf("order", "noodles"), listOf("just toast"), listOf("cereal", "again"),
        listOf("call mom"), listOf("eggs,", "somehow"), listOf("raid the", "fridge"),
    )
    private val OFFSETS = listOf(
        PbsPoint(-62.0, -140.0), PbsPoint(64.0, -88.0), PbsPoint(-70.0, -22.0),
        PbsPoint(70.0, 32.0), PbsPoint(-62.0, 96.0), PbsPoint(58.0, 150.0),
    )
    val STATUSES = listOf(
        "TYPE A QUESTION, THEN SEARCH", "6 ANSWERS FOUND",
        "1 ANSWER LEFT", "2 ANSWERS LEFT", "3 ANSWERS LEFT", "4 ANSWERS LEFT", "5 ANSWERS LEFT",
        "NOTHING LEFT. ASK AGAIN?",
    )

    const val WIN_W = 214.0
    const val WIN_H = 118.0
    const val TITLE_H = 18.0
    const val BURN_LENGTH = 1.8

    const val TYPE_START = 0.9
    const val BURST_AT = 2.5
    const val PRESS_AT = 3.05
    const val SPAWN_AT = 3.25
    const val SPAWN_GAP = 0.16
    const val BURN_AT = 4.9
    const val BURN_GAP = 0.18
    const val SELECT_AT = 7.65
    const val BURST_OFF = 7.7

    // Layout

    fun barY(h: Double) = h * 0.29
    fun buttonX(w: Double) = w / 2 + 122
    fun pileY(h: Double) = barY(h) + 260

    fun windowRect(i: Int, w: Double, h: Double): PbsRect {
        val cx = w / 2 + OFFSETS[i].x
        val cy = pileY(h) + OFFSETS[i].y
        return PbsRect(cx - WIN_W / 2, cy - WIN_H / 2, WIN_W, WIN_H)
    }

    fun buttonHit(x: Double, y: Double, w: Double, h: Double) = abs(x - buttonX(w)) < 44 && abs(y - barY(h)) < 36

    // Math

    fun hash(x: Double): Double {
        val v = sin(x) * 43758.5453
        return v - floor(v)
    }

    fun h3(a: Int, b: Int, c: Double) = hash(a * 12.7 + b * 57.3 + c * 3.71 + 0.5)

    fun seg(t: Double, a: Double, b: Double) = min(max((t - a) / (b - a), 0.0), 1.0)
    fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
    fun easeOutCubic(x: Double) = 1 - (1 - x).pow(3)
    fun easeInCubic(x: Double) = x * x * x
    fun easeInOutCubic(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun easeInOutSine(x: Double) = -(cos(PI * x) - 1) / 2
    fun easeOutBack(x: Double): Double {
        val c1 = 1.2
        val c3 = c1 + 1
        return 1 + c3 * (x - 1).pow(3) + c1 * (x - 1).pow(2)
    }
    fun easeInBack(x: Double): Double {
        val c1 = 1.70158
        val c3 = c1 + 1
        return c3 * x * x * x - c1 * x * x
    }
    fun spring(t: Double, response: Double, damping: Double): Double {
        if (t <= 0) return 0.0
        val w0 = 2 * PI / response
        val wd = w0 * sqrt(1 - damping * damping)
        return 1 - exp(-damping * w0 * t) * (cos(wd * t) + damping * w0 / wd * sin(wd * t))
    }

    fun charTime(i: Int) = 0.9 + 0.056 * i + (hash(i + 1.0) - 0.5) * 0.03

    // Burn geometry (age a since a window ignites)

    fun burnFront(a: Double, r: PbsRect): Double {
        val u = easeInOutSine(seg(a, 0.15, 1.6))
        return r.maxY + 4 - u * (WIN_H + 26)
    }

    fun flameDie(a: Double) = 1 - easeInCubic(seg(a, 1.25, 1.8))
    fun flameGrow(a: Double) = easeOutBack(seg(a, 0.0, 0.28)) * flameDie(a)

    /** The jagged burn edge, 24 points from left − 2 to right + 14, clamped to the window's top. */
    fun edgePoints(i: Int, a: Double, r: PbsRect): List<PbsPoint> {
        val front = burnFront(a, r)
        val q = floor(a * 12)
        return (0 until 24).map { k ->
            val x = r.minX - 2 + k * 10
            val j = (hash(k * 3.1 + i * 17.9) - 0.5) * 12 + 4 * sin(k * 1.7 + q * 0.5)
            PbsPoint(x, max(front + j, r.minY - 2))
        }
    }

    private val FLAME_H = doubleArrayOf(1.0, 1.0, 0.7, 0.45)
    private val FLAME_W = doubleArrayOf(1.0, 1.0, 0.8, 0.6)
    private val FLAME_D = doubleArrayOf(26.0, 26.0, 18.0, 10.0)

    /** One layer (0 shadow, 1 red, 2 orange, 3 yellow) of a window's flame crown, as a closed polygon. */
    fun flameContour(i: Int, l: Int, a: Double, r: PbsRect): List<PbsPoint> {
        val g = flameGrow(a)
        val front = burnFront(a, r)
        val left = r.minX - 8
        val right = r.maxX + 8
        val mid = (left + right) / 2
        val n = 6
        val step = (right - left) / n
        val q = floor(a * 12)
        val ws = FLAME_W[l] * (0.3 + 0.7 * flameDie(a))
        val hs = FLAME_H[l]
        fun px(x: Double) = mid + (x - mid) * ws
        val valley = 10 * g * hs
        val pts = ArrayList<PbsPoint>(30)
        pts.add(PbsPoint(px(left), front + 2))
        pts.add(PbsPoint(px(left), front - valley))
        for (j in 0 until n) {
            val base = left + (j + 0.5) * step
            val tall = 70 + 50 * h3(j, i, 1.0)
            val alt = if (j % 2 == 1) 0.55 else if (j == 0 || j == n - 1) 0.8 else 1.0
            val flick = 0.85 + 0.25 * h3(j, i, q + 50)
            val hgt = tall * alt * flick * g * hs
            val tipX = base + (base - mid) * 0.45 + (h3(j, i, q + 90) - 0.5) * 16
            pts.add(PbsPoint(px(tipX), front - hgt))
            pts.add(PbsPoint(px(left + (j + 1) * step), front - valley))
        }
        pts.add(PbsPoint(px(right), front + 2))
        val depth = FLAME_D[l] * g
        for (k in 1 until 8) {
            val f = k / 8.0
            pts.add(PbsPoint(px(right - (right - left) * f), front + 2 + depth * 4 * f * (1 - f)))
        }
        return pts
    }

    fun embers(i: Int, a: Double, r: PbsRect): List<PbsParticle> {
        val out = ArrayList<PbsParticle>(17)
        for (k in 0 until 12) {
            val b = 0.3 + 0.09 * k
            val life = 0.8 + 0.3 * h3(k, i, 3.0)
            val s = (a - b) / life
            if (s < 0 || s >= 1) continue
            val x0 = r.minX + (0.08 + 0.84 * h3(k, i, 4.0)) * WIN_W
            val y0 = burnFront(b, r)
            val x = x0 + 12 * sin(2 * PI * (s * 1.2 + h3(k, i, 6.0)))
            val y = y0 - s * (70 + 70 * h3(k, i, 5.0))
            val color = if (s < 0.35) 0xFFE03D else if (s < 0.7) 0xFF8C1A else 0xE8203A
            val alpha = if (s < 0.6) 1.0 else 1 - (s - 0.6) / 0.4
            out.add(PbsParticle(x, y, 6 - 4 * s, 0.0, color, alpha))
        }
        for (k in 0 until 5) {
            val b = 0.5 + 0.2 * k
            val s = (a - b) / 1.1
            if (s < 0 || s >= 1) continue
            val x0 = r.minX + (0.1 + 0.8 * h3(k, i, 7.0)) * WIN_W
            val y0 = burnFront(b, r)
            val x = x0 + (h3(k, i, 8.0) - 0.5) * 32 * sin(PI * s)
            val y = y0 - s * (50 + 40 * h3(k, i, 9.0))
            out.add(PbsParticle(x, y, 4 + 2 * h3(k, i, 10.0), 3 * s, 0x1C120C, 0.85 * (1 - s)))
        }
        return out
    }

    /** Starburst polygon around (0, 0), 13 spikes; q is the 8 fps boil frame. */
    fun burstPoints(q: Double): List<PbsPoint> {
        val n = 13
        val pts = ArrayList<PbsPoint>(26)
        for (j in 0 until n) {
            val base = j.toDouble() / n * 2 * PI - PI / 2
            val boil = 1 + (hash(j * 7.7 + q * 1.3) - 0.5) * 0.1
            val outer = (52 + 30 * hash(j * 4.3 + 11)) * boil
            val inner = 27 + 6 * hash(j * 2.9 + 23)
            val ao = base + (hash(j * 5.9 + 31) - 0.5) * 0.24
            val ai = base + PI / n
            pts.add(PbsPoint(outer * cos(ao), outer * sin(ao)))
            pts.add(PbsPoint(inner * cos(ai), inner * sin(ai)))
        }
        return pts
    }
}

/** What happens when, in one time frame (loop time while playing, takeover time after a touch). */
class PbsStory {
    var typeStart = PbsModel.NEVER
    var selectAt = PbsModel.NEVER
    var burstAt = PbsModel.NEVER
    var burstOff = PbsModel.NEVER
    var pressAt = -PbsModel.NEVER
    val spawn = DoubleArray(6) { PbsModel.NEVER }
    val burn = DoubleArray(6) { PbsModel.NEVER }
    var cursorUntil = PbsModel.NEVER

    fun typed(t: Double): Int {
        if (t >= selectAt + 0.25) return 0
        var n = 0
        for (i in PbsModel.QUERY.indices) if (t >= typeStart + PbsModel.charTime(i) - PbsModel.TYPE_START) n++
        return n
    }

    fun standing(i: Int) = spawn[i] < PbsModel.NEVER && burn[i] >= PbsModel.NEVER
    fun gone(i: Int, t: Double) = spawn[i] >= PbsModel.NEVER || (burn[i] < PbsModel.NEVER && t >= burn[i] + PbsModel.BURN_LENGTH)

    companion object {
        fun base(): PbsStory = PbsStory().apply {
            typeStart = PbsModel.TYPE_START
            selectAt = PbsModel.SELECT_AT
            burstAt = PbsModel.BURST_AT
            burstOff = PbsModel.BURST_OFF
            pressAt = PbsModel.PRESS_AT
            for (i in 0 until 6) {
                spawn[i] = PbsModel.SPAWN_AT + PbsModel.SPAWN_GAP * i
                burn[i] = PbsModel.BURN_AT + PbsModel.BURN_GAP * (5 - i)
            }
        }

        val BASE = base()

        /** The base story with every event after t dropped (a touch took over at loop time t). */
        fun cut(t: Double): PbsStory = base().apply {
            if (typeStart > t) typeStart = PbsModel.NEVER
            if (selectAt > t) selectAt = PbsModel.NEVER
            if (burstAt > t) burstAt = PbsModel.NEVER
            if (burstOff > t) burstOff = PbsModel.NEVER
            if (pressAt > t) {
                pressAt = -PbsModel.NEVER
                for (i in 0 until 6) spawn[i] = PbsModel.NEVER
            }
            for (i in 0 until 6) if (burn[i] > t) burn[i] = PbsModel.NEVER
            cursorUntil = t
        }

        fun shown(t: Double): PbsStory = PbsStory().apply {
            typeStart = t - 10
            burstAt = t - 10
            for (i in 0 until 6) spawn[i] = t - 10
            cursorUntil = -PbsModel.NEVER
        }
    }
}

class PbsFrame {
    var t = 0.0
    var typed = 0
    var selected = false
    var caret = true
    var burstScale = 0.0
    var burstAngle = 0.0
    var burstQ = 0.0
    var press = 0.0
    var cursorX = 0.0
    var cursorY = 0.0
    var cursorAlpha = 0.0
    var cursorSquish = 1.0
    var status = 0
    val spawnAge = DoubleArray(6) { -1.0 }
    val burnAge = DoubleArray(6) { -1.0 }
    var reduced = false

    companion object {
        fun make(s: PbsStory, t: Double, reduced: Boolean): PbsFrame {
            val m = PbsModel
            val f = PbsFrame()
            f.t = t
            f.reduced = reduced
            f.typed = s.typed(t)
            f.selected = t >= s.selectAt && t < s.selectAt + 0.25
            val typing = f.typed > 0 && f.typed < m.QUERY.length
            f.caret = !f.selected && (reduced || typing || floor(t / 0.5).toLong() % 2 == 0L)

            if (t >= s.burstAt) {
                val a = t - s.burstAt
                val sp = if (reduced) 1.0 else m.spring(a, 0.5, 0.55)
                var scale = sp
                val p = t - s.pressAt
                if (p >= 0 && p < 0.3 && !reduced) scale *= 1 + 0.12 * sin(PI * p / 0.3)
                scale *= 1 - m.easeInBack(m.seg(t, s.burstOff, s.burstOff + 0.25))
                f.burstScale = max(0.0, scale)
                f.burstAngle = if (reduced) 0.0 else -25 * (1 - sp)
            }
            f.burstQ = if (reduced) 0.0 else floor(t * 8)

            val p = t - s.pressAt
            if (p >= 0 && p < 0.2) {
                f.press = if (p < 0.06) m.easeOutCubic(p / 0.06) else 1 - m.easeOutCubic((p - 0.06) / 0.14)
            }

            if (!reduced) {
                val c = min(t, s.cursorUntil)
                val mv = m.easeInOutCubic(m.seg(c, 2.3, 2.95))
                var x = m.lerp(-82.0, 2.0, mv) + 30 * sin(PI * mv)
                var y = m.lerp(190.0, 6.0, mv)
                val d = m.easeInOutCubic(m.seg(c, 3.6, 4.2))
                x = m.lerp(x, 34.0, d)
                y = m.lerp(y, 74.0, d)
                f.cursorX = x
                f.cursorY = y
                f.cursorAlpha = m.seg(c, 2.3, 2.45) * (1 - m.seg(c, 3.9, 4.2)) * (1 - m.seg(t, s.cursorUntil, s.cursorUntil + 0.2))
                val q = c - m.PRESS_AT
                if (q >= 0 && q < 0.06) f.cursorSquish = 1 - 0.2 * q / 0.06
                else if (q >= 0.06 && q < 0.2) f.cursorSquish = 0.8 + 0.2 * m.easeOutCubic((q - 0.06) / 0.14)
            }

            var anySpawned = false
            var left = 0
            for (i in 0 until 6) {
                if (t >= s.spawn[i]) {
                    f.spawnAge[i] = t - s.spawn[i]
                    anySpawned = true
                }
                if (t >= s.burn[i]) f.burnAge[i] = t - s.burn[i]
                if (s.spawn[i] < m.NEVER && f.burnAge[i] < 1.0) left++
            }
            f.status = when {
                !anySpawned || t >= s.selectAt + 0.25 -> 0
                left == 0 -> 7
                left == 6 -> 1
                else -> left + 1
            }
            return f
        }
    }
}

/** Plays the loop, takes over on touch, and eases back to the loop 2 s after the last touch. */
class PbsDriver {
    private val m = PbsModel
    var manual = false
        private set
    private var loopStart: Double? = null
    private var epoch = 0.0
    private var story = PbsStory()
    private var lastTouch = -PbsModel.NEVER
    private var returning = false
    private var settleAt: Double? = null
    private var reducedReady = false

    fun frame(now: Double, auto: Boolean, reduced: Boolean): PbsFrame {
        if (reduced) {
            if (!reducedReady) {
                reducedReady = true
                manual = true
                epoch = now
                story = PbsStory.shown(0.0)
            }
            return PbsFrame.make(story, now - epoch, true)
        }
        if (!manual) {
            if (auto) {
                val start = loopStart ?: now.also { loopStart = it }
                val tau = (now - start) % m.LOOP
                return PbsFrame.make(PbsStory.BASE, tau, false)
            }
            return PbsFrame.make(story, now - epoch, false)
        }
        settle(now, now - epoch, auto)
        return PbsFrame.make(story, now - epoch, false)
    }

    /** True while something still moves after a touch (so the frame loop can stop when not playing). */
    fun busy(now: Double) = manual || now - lastTouch < 6

    private fun settle(now: Double, t: Double, auto: Boolean) {
        if (now - lastTouch < 2) return
        if (!returning) {
            returning = true
            igniteAll(t, m.BURN_GAP)
        }
        if (!(0 until 6).all { story.gone(it, t) }) return
        if (settleAt == null) {
            var end = t
            if (story.typed(t) > 0 && story.selectAt >= m.NEVER) story.selectAt = t + 0.1
            if (story.selectAt < m.NEVER) end = max(end, story.selectAt + 0.25)
            if (t >= story.burstAt && story.burstOff >= m.NEVER) story.burstOff = t + 0.15
            if (story.burstAt < m.NEVER && story.burstOff < m.NEVER) end = max(end, story.burstOff + 0.25)
            settleAt = end + 0.3
        }
        val s = settleAt
        if (s != null && t >= s) {
            manual = false
            returning = false
            settleAt = null
            story = PbsStory()
            if (auto) loopStart = now else epoch = now
        }
    }

    private fun igniteAll(t: Double, gap: Double) {
        var rank = 0
        for (i in 5 downTo 0) {
            if (!story.standing(i)) continue
            story.burn[i] = max(t + rank * gap, story.spawn[i] + 0.3)
            rank++
        }
    }

    /** A finger went down: the story becomes ours from here. */
    fun takeOver(now: Double, auto: Boolean) {
        if (!manual) {
            val start = loopStart
            if (auto && start != null) {
                val tau = (now - start) % m.LOOP
                epoch = now - tau
                story = PbsStory.cut(tau)
            } else {
                epoch = now
                story = PbsStory()
            }
            manual = true
        }
        lastTouch = now
        returning = false
        settleAt = null
    }

    /** Tap on the search button. */
    fun search(now: Double): Boolean {
        val t = now - epoch
        story.pressAt = t
        if (story.typed(t) < m.QUERY.length || story.selectAt < m.NEVER) {
            story.typeStart = t - 2
            story.selectAt = m.NEVER
        }
        if (!(t >= story.burstAt && t < story.burstOff)) {
            story.burstAt = t
            story.burstOff = m.NEVER
        }
        var k = 0
        for (i in 0 until 6) {
            if (!story.gone(i, t)) continue
            story.spawn[i] = t + 0.2 + m.SPAWN_GAP * k
            story.burn[i] = m.NEVER
            k++
        }
        return true
    }

    /** Tap on a window: the topmost standing one under the finger ignites. */
    fun burn(x: Double, y: Double, now: Double, w: Double, h: Double): Boolean {
        val t = now - epoch
        for (i in 5 downTo 0) {
            if (!story.standing(i) || t < story.spawn[i]) continue
            if (m.windowRect(i, w, h).contains(x, y)) {
                story.burn[i] = t
                return true
            }
        }
        return false
    }

    /** Long press: everything standing ignites, bottom first. */
    fun burnAll(now: Double): Boolean {
        val t = now - epoch
        val any = (0 until 6).any { story.standing(it) }
        igniteAll(t, 0.12)
        return any
    }
}
