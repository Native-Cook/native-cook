package com.ngocdevv.nativecook.blocks.sheets.iconmorphsheet

import kotlin.math.*

/** The authored 16.4-second choreography. All times are seconds. */
internal object IconMorphTimeline {
    const val PERIOD = 16.4
    const val STEP_DURATION = 1.15
    const val CLOSE_DURATION = 0.46
    fun clamp(n: Double, lo: Double = 0.0, hi: Double = 1.0) = n.coerceIn(lo, hi)
    fun mix(a: Double, b: Double, t: Double) = a + (b - a) * t
    fun progress(t: Double, start: Double, duration: Double) = clamp((t - start) / duration)
    fun bezier(p: Double, x1: Double = 0.23, y1: Double = 1.0, x2: Double = 0.32, y2: Double = 1.0): Double {
        if (p <= 0) return 0.0
        if (p >= 1) return 1.0
        var lo = 0.0
        var hi = 1.0
        var u = p
        repeat(20) {
            val v = 1 - u
            val x = 3 * v * v * u * x1 + 3 * v * u * u * x2 + u * u * u
            if (x < p) lo = u else hi = u
            u = (lo + hi) / 2
        }
        val v = 1 - u
        return 3 * v * v * u * y1 + 3 * v * u * u * y2 + u * u * u
    }
    fun spring(t: Double, from: Double, to: Double, stiffness: Double = 320.0,
               damping: Double = 22.0, velocity: Double = 0.0): Double {
        if (t <= 0) return from
        if (t >= 1) return to
        val d = damping / 2
        val w = sqrt(stiffness - d * d)
        val x = from - to
        return to + exp(-d * t) * (x * cos(w * t) + (velocity + d * x) / w * sin(w * t))
    }
    fun bounceAt(t: Double, amplitude: Double = 1.0): Double {
        return if (t < 0 || t >= 0.9) 0.0 else amplitude * 1.6 * exp(-6 * t) * sin(2 * PI * 1.9 * t)
    }
    fun stepEase(p: Double) = bezier(p, 0.45, 0.0, 0.4, 1.0)
    fun sheetEase(p: Double) = bezier(p, 0.32, 0.72, 0.0, 1.0)
    fun sheetHeight(position: Double, short: Double, tall: Double): Double {
        val p = clamp(position, 0.0, 3.0)
        val step = floor(p).toInt()
        return mix(short, tall, mix((step % 2).toDouble(), ((step + 1) % 2).toDouble(), p - step))
    }
    fun labelRoll(p: Double): DoubleArray {
        val out = bezier(progress(p, 0.0, 0.45))
        val incoming = bezier(progress(p, 0.45, 0.55))
        return doubleArrayOf(1 - out, incoming, -12 * out, 12 * (1 - incoming))
    }
    fun rollLabels(f: IconMorphFrame, from: IconMorphFrame, p: Double, opening: Boolean) {
        val r = labelRoll(p)
        if (opening) {
            f.labelClosed = from.labelClosed * r[0]
            f.labelOpen = mix(from.labelOpen, 1.0, r[1])
            f.labelClosedY = mix(from.labelClosedY, -12.0, 1 - r[0])
            f.labelOpenY = mix(if (from.labelOpen == 0.0) 12.0 else from.labelOpenY, 0.0, r[1])
        } else {
            f.labelOpen = from.labelOpen * r[0]
            f.labelClosed = mix(from.labelClosed, 1.0, r[1])
            f.labelOpenY = mix(from.labelOpenY, -12.0, 1 - r[0])
            f.labelClosedY = mix(if (from.labelClosed == 0.0) 12.0 else from.labelClosedY, 0.0, r[1])
        }
    }
    fun buttonLabel(f: IconMorphFrame, index: Int): DoubleArray {
        if (index == 0) return doubleArrayOf(f.labelClosed, f.labelClosedY)
        val r = labelRoll(clamp((f.position - 2.35) / 0.3))
        return doubleArrayOf(f.labelOpen * r[if (index == 1) 0 else 1], f.labelOpenY + r[if (index == 1) 2 else 3])
    }
    fun contentFade(open: Double): Double {
        val p = progress(open, 0.0, 0.25)
        return p * p * (3 - 2 * p)
    }
    fun openingStage(elapsed: Double) = sheetEase(progress(elapsed, 0.08, 0.48))
    fun releaseEase(p: Double, distance: Double, velocity: Double, duration: Double): Double {
        val slope = if (abs(distance) < 0.0001) 0.0 else clamp(velocity * duration / distance, 0.0, 6.0)
        val a = slope * 0.23
        return bezier(a * clamp(p) + (1 - a) * clamp(p) * clamp(p))
    }
    private fun marker(f: IconMorphFrame, t: Double, down: Double, up: Double, kind: Int, x: Double, y: Double) {
        if (t < down - 0.12 || t >= up + 0.2) return
        f.markerOpacity = min(progress(t, down - 0.12, 0.12), 1 - progress(t, up, 0.2))
        f.markerPress = if (t < down) 0.0 else if (t < up) 1.0 else 1 - progress(t, up, 0.12)
        f.markerKind = kind
        f.markerX = x
        f.markerY = y
    }
    private fun buttonBeat(t: Double, down: Double, up: Double): Double {
        if (t < down || t >= up + 1) return 0.0
        return if (t < up) bezier(progress(t, down, 0.12)) else spring(t - up, 1.0, 0.0)
    }
    fun loop(seconds: Double, reduced: Boolean = false): IconMorphFrame {
        val t = round(((seconds % PERIOD + PERIOD) % PERIOD) * 1e9) / 1e9 % PERIOD
        val f = IconMorphFrame()
        val duration = if (reduced) 0.45 else STEP_DURATION
        if (t >= 2.05) f.position = stepEase(progress(t, 2.05, duration))
        if (t >= 6.2 && t < 6.8) {
            val p = progress(t, 6.2, 0.6)
            f.position = 1 + 0.5 * p * p * (3 - 2 * p)
            f.lean = if (reduced) 0.0 else -0.6 * sin(PI * p)
        } else if (t >= 6.8 && t < 7.1) {
            val p = progress(t, 6.8, 0.3)
            f.position = 1.5 - 0.14 * p * p * (3 - 2 * p)
            f.lean = if (reduced) 0.0 else 0.3 * sin(PI * p)
        } else if (t >= 7.1 && t < 7.4) {
            val p = progress(t, 7.1, 0.3)
            f.position = 1.36 + 0.36 * p * p
            f.lean = if (reduced) 0.0 else -0.8 * p
        } else if (t >= 7.4) {
            f.position = mix(1.72, 2.0, if (reduced) bezier(progress(t, 7.4, 0.28)) else releaseEase(progress(t, 7.4, 0.28), 0.28, 2.4, 0.28))
            f.lean = if (reduced || t >= 8.1) 0.0 else -0.8 * exp(-10 * (t - 7.4)) * (1 - progress(t, 7.8, 0.3))
        }
        val fingerTravel = if (t < 6.2) 0.0 else if (t < 7.4) 280 * (f.position - 1) else 280 * 0.72
        if (t >= 11.12) f.position = 2 + stepEase(progress(t, 11.12, duration))
        if (!reduced) f.bounce = bounceAt(t - (2.05 + duration)) + bounceAt(t - 4.35, 0.6) + bounceAt(t - 7.68, 0.7) + bounceAt(t - (11.12 + duration))
        if (t >= 4.2 && t < 4.35) f.press = bezier(progress(t, 4.2, 0.11))
        else if (t >= 4.35 && t < 5.35) f.press = clamp(spring(t - 4.35, 1.0, 0.0))
        f.buttonPress = buttonBeat(t, 1.93, 2.05) + buttonBeat(t, 11.0, 11.12) + buttonBeat(t, 14.33, 14.45) + buttonBeat(t, 15.15, 15.27)
        if (t >= 14.45 && t < 15.27) {
            f.open = 1 - (if (reduced) progress(t, 14.45, 0.2) else sheetEase(progress(t, 14.45, CLOSE_DURATION)))
            f.contents = if (reduced) f.open else contentFade(f.open)
            f.stage = if (reduced) f.open else 1.0
            rollLabels(f, IconMorphFrame(), progress(t, 14.45, 0.26), false)
            f.running = 0.0
            if (t >= (if (reduced) 14.65 else 14.91)) {
                f.position = 0.0
                f.stage = 0.0
            }
        } else if (t >= 15.27) {
            f.position = 0.0
            f.open = if (reduced) progress(t, 15.27, 0.2) else sheetEase(progress(t, 15.27, 0.56))
            f.contents = if (reduced) f.open else contentFade(f.open)
            f.stage = if (reduced) f.open else openingStage(t - 15.27)
            val closed = IconMorphFrame(labelOpen = 0.0, labelClosed = 1.0, labelClosedY = 0.0, labelOpenY = -12.0)
            rollLabels(f, closed, progress(t, 15.27, 0.26), true)
            f.running = 0.0
        }
        marker(f, t, 1.93, 2.05, 0, 201.0, 804.0)
        marker(f, t, 4.2, 4.35, 1, 201.0, 612.0)
        marker(f, t, 6.15, 7.4, 2, 316 - fingerTravel, 716.0)
        marker(f, t, 11.0, 11.12, 0, 201.0, 804.0)
        marker(f, t, 14.33, 14.45, 0, 201.0, 804.0)
        marker(f, t, 15.15, 15.27, 0, 201.0, 804.0)
        return f
    }
}
