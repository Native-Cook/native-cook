package com.ngocdevv.nativecook.blocks.gestures.dayonemedal

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** The source ribbon's two Verlet bands: 15 constraints and 71 rendered samples each. */
internal class DayOneMedalRibbon {
    private class Point(var x: Float = 0f, var y: Float = 0f, var px: Float = 0f, var py: Float = 0f)
    private class Sample(var x: Float = 0f, var y: Float = 0f, var nx: Float = 1f, var ny: Float = 0f,
                         var width: Float = 13f, var light: Float = 1f)
    private class Band {
        val points = Array(15) { Point() }
        val samples = Array(71) { Sample() }
        var length = 1f
        var topX = 0f
        var topY = 0f
        var endX = 0f
        var endY = 0f
    }
    private val bands = Array(2) { Band() }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private var active = false
    private var leaving = false
    private var caught = false
    private var previousWorn = 0f
    private var releaseX = 0f
    private var releaseY = 0f
    private var releaseWorn = 1f
    private var tipX = 0f
    private var tipY = 0f
    private var previousStep = 1f / 120f
    var opacity = 0f
        private set

    fun reset() {
        active = false
        leaving = false
        caught = false
        previousWorn = 0f
        opacity = 0f
    }

    fun step(elapsed: Float, worn: Float, pivotX: Float, pivotY: Float,
             attachX: Float, attachY: Float, reduced: Boolean) {
        val amount = worn.coerceIn(0f, 1f)
        if (amount == 0f) {
            reset()
            return
        }
        val fresh = !active
        if (amount < previousWorn - 0.00001f && !leaving) {
            leaving = true
            releaseX = tipX
            releaseY = tipY
            releaseWorn = previousWorn
        } else if (amount > previousWorn + 0.00001f && leaving) {
            leaving = false
        }
        val catchNow = !leaving && amount >= 0.55f
        val catchKick = catchNow && !caught && !fresh
        var nextX = attachX
        var nextY = attachY
        if (!reduced) {
            if (leaving) {
                val retract = 1f - amount / max(0.0001f, releaseWorn)
                val lift = retract.pow(0.8f) * max(64f, releaseY - pivotY + 64f)
                nextX = releaseX
                nextY = releaseY - lift
            } else {
                val drop = min(1f, amount / 0.55f)
                nextY -= (1f - drop * drop) * max(64f, attachY - pivotY + 64f)
            }
        }
        val travel = if (fresh) 0f else hypot(nextX - tipX, nextY - tipY)
        tipX = nextX
        tipY = nextY
        opacity = if (reduced) amount else min(1f, amount * 12f)
        val duration = elapsed.coerceIn(0f, 0.1f)
        val steps = max(1, max(ceil(duration * 120f).toInt(), min(24, ceil(travel / 4f).toInt())))
        val dt = if (duration > 0f) duration / steps else 1f / 120f
        for (index in 0..1) {
            val band = bands[index]
            val side = if (index == 0) -1f else 1f
            val tx = pivotX + side * 46f
            val ty = pivotY
            val ex = tipX - side * 4f
            val ey = tipY - 13f
            if (fresh || reduced) seed(band, tx, ty, ex, ey, side, reduced)
            if (!reduced && (duration > 0f || fresh || travel > 0f)) {
                val oldTX = band.topX
                val oldTY = band.topY
                val oldEX = band.endX
                val oldEY = band.endY
                repeat(steps) { sub ->
                    val blend = (sub + 1f) / steps
                    for (i in 1..13) {
                        val p = band.points[i]
                        val ratio = dt / if (sub == 0) previousStep else dt
                        val drag = exp(-3.8f * dt)
                        val vx = ((p.x - p.px) * ratio).coerceIn(-900f * dt, 900f * dt)
                        val vy = ((p.y - p.py) * ratio).coerceIn(-900f * dt, 900f * dt)
                        p.px = p.x
                        p.py = p.y
                        p.x += vx * drag
                        p.y += vy * drag + 430f * dt * dt
                        if (catchKick && sub == 0) p.x += side * 38f * dt * sin(i / 14f * PI.toFloat())
                    }
                    val bend = 1f - exp(-5f * dt)
                    for (i in 1..13) {
                        val p = band.points[i]
                        val a = band.points[i - 1]
                        val c = band.points[i + 1]
                        p.x += ((a.x + c.x) * 0.5f - p.x) * bend
                        p.y += ((a.y + c.y) * 0.5f - p.y) * bend
                    }
                    constrain(band, oldTX + (tx - oldTX) * blend, oldTY + (ty - oldTY) * blend,
                              oldEX + (ex - oldEX) * blend, oldEY + (ey - oldEY) * blend)
                }
            }
            band.topX = tx
            band.topY = ty
            band.endX = ex
            band.endY = ey
            sample(band, side, reduced)
        }
        previousStep = dt
        active = true
        caught = catchNow
        previousWorn = amount
    }

    private fun seed(b: Band, tx: Float, ty: Float, ex: Float, ey: Float, side: Float, straight: Boolean) {
        val dx = ex - tx
        val dy = ey - ty
        val span = max(1f, hypot(dx, dy))
        val belly = if (straight) 0f else span * 0.115f * side
        for (i in 0..14) {
            val t = i / 14f
            val bow = sin(PI.toFloat() * t) * belly
            val p = b.points[i]
            p.x = tx + dx * t + dy / span * bow
            p.y = ty + dy * t - dx / span * bow
            p.px = p.x
            p.py = p.y
        }
        b.topX = tx
        b.topY = ty
        b.endX = ex
        b.endY = ey
        b.length = span * (if (straight) 1f else 1.033f) / 14f
    }

    private fun constrain(b: Band, tx: Float, ty: Float, ex: Float, ey: Float) {
        b.length = max(1f, hypot(ex - tx, ey - ty)) * 1.033f / 14f
        b.points[0].x = tx
        b.points[0].y = ty
        b.points[14].x = ex
        b.points[14].y = ey
        for (pass in 0 until 384) {
            for (j in 0..13) {
                val i = if (pass % 2 == 0) j else 13 - j
                val a = b.points[i]
                val c = b.points[i + 1]
                val dx = c.x - a.x
                val dy = c.y - a.y
                val span = max(0.00001f, hypot(dx, dy))
                val correction = (span - b.length) / span
                val wa = if (i == 0) 0f else if (i + 1 == 14) 1f else 0.5f
                val wb = if (i + 1 == 14) 0f else if (i == 0) 1f else 0.5f
                a.x += dx * correction * wa
                a.y += dy * correction * wa
                c.x -= dx * correction * wb
                c.y -= dy * correction * wb
            }
            if (pass % 8 == 7) {
                var error = 0f
                for (i in 0..13) {
                    val a = b.points[i]
                    val c = b.points[i + 1]
                    error = max(error, abs(hypot(c.x - a.x, c.y - a.y) / b.length - 1f))
                }
                if (error < 0.001f) break
            }
        }
    }

    private fun catmull(a: Float, b: Float, c: Float, d: Float, t: Float): Float =
        0.5f * (2f * b + (-a + c) * t + (2f * a - 5f * b + 4f * c - d) * t * t +
                 (-a + 3f * b - 3f * c + d) * t * t * t)

    private fun sample(b: Band, side: Float, straight: Boolean) {
        for (i in 0..70) {
            val at = i / 70f * 14f
            val j = min(13, at.toInt())
            val f = at - j
            val a = b.points[max(0, j - 1)]
            val p = b.points[j]
            val c = b.points[j + 1]
            val d = b.points[min(14, j + 2)]
            b.samples[i].x = catmull(a.x, p.x, c.x, d.x, f)
            b.samples[i].y = catmull(a.y, p.y, c.y, d.y, f)
        }
        for (i in 0..70) {
            val a = b.samples[max(0, i - 1)]
            val c = b.samples[min(70, i + 1)]
            val p = b.samples[i]
            val span = max(0.001f, hypot(c.x - a.x, c.y - a.y))
            p.nx = (c.y - a.y) / span
            p.ny = -(c.x - a.x) / span
            val t = i / 70f
            val centre = if (side < 0f) 0.73f else 0.84f
            val fold = if (straight) 0f else exp(-((t - centre) / 0.12f).pow(2))
            p.width = 13f * (1f - 0.62f * fold)
            p.light = (0.86f - 0.4f * fold - 0.16f * p.ny + 0.05f * side).coerceIn(0.35f, 1f)
        }
    }

    fun draw(canvas: Canvas) {
        if (!active) return
        val shades = intArrayOf(0xFF111826.toInt(), 0xFF182235.toInt(), 0xFF202D42.toInt(),
            0xFF29374D.toInt(), 0xFF2D3B50.toInt(), 0xFF29384D.toInt(),
            0xFF233147.toInt(), 0xFF1B283C.toInt(), 0xFF121C2E.toInt())
        for (band in bands) {
            strip(band, -1f, 1f)
            fill.color = 0xFF000006.toInt()
            fill.alpha = (opacity * 62f).toInt()
            canvas.save()
            canvas.translate(2f, 3f)
            canvas.drawPath(path, fill)
            canvas.restore()
            for (lane in 0..8) {
                strip(band, -1f + lane * 2f / 9f, -1f + (lane + 1f) * 2f / 9f + 0.008f)
                fill.color = shades[lane]
                fill.alpha = (opacity * 255f).toInt()
                canvas.drawPath(path, fill)
            }
            for (side in floatArrayOf(-0.8f, 0.8f)) {
                line(band, side)
                edge.color = 0xFF73522E.toInt()
                edge.alpha = (opacity * 230f).toInt()
                edge.strokeWidth = 1.9f
                canvas.drawPath(path, edge)
                edge.color = 0xFFDDA85A.toInt()
                edge.alpha = (opacity * 214f).toInt()
                edge.strokeWidth = 0.95f
                canvas.drawPath(path, edge)
            }
            for (i in 1..70) {
                val a = band.samples[i - 1]
                val b = band.samples[i]
                stripSegment(a, b)
                fill.color = 0xFF000006.toInt()
                fill.alpha = ((1f - (a.light + b.light) * 0.5f) * opacity * 255f).toInt().coerceIn(0, 255)
                canvas.drawPath(path, fill)
            }
            edge.strokeWidth = 0.48f
            for (i in 0..70 step 3) {
                val p = band.samples[i]
                val half = p.width * 0.93f
                edge.color = 0xFF8291A7.toInt()
                edge.alpha = (opacity * 48f).toInt()
                canvas.drawLine(p.x - p.nx * half, p.y - p.ny * half,
                    p.x + p.nx * half, p.y + p.ny * half, edge)
                edge.color = 0xFF030610.toInt()
                edge.alpha = (opacity * 61f).toInt()
                canvas.drawLine(p.x - p.nx * half - p.ny * 0.5f,
                    p.y - p.ny * half + p.nx * 0.5f,
                    p.x + p.nx * half - p.ny * 0.5f,
                    p.y + p.ny * half + p.nx * 0.5f, edge)
            }
        }
    }

    private fun line(b: Band, lane: Float) {
        path.reset()
        b.samples.forEachIndexed { i, p ->
            val x = p.x + p.nx * p.width * lane
            val y = p.y + p.ny * p.width * lane
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
    }

    private fun strip(b: Band, left: Float, right: Float) {
        line(b, left)
        for (i in 70 downTo 0) {
            val p = b.samples[i]
            path.lineTo(p.x + p.nx * p.width * right, p.y + p.ny * p.width * right)
        }
        path.close()
    }

    private fun stripSegment(a: Sample, b: Sample) {
        path.reset()
        path.moveTo(a.x - a.nx * a.width, a.y - a.ny * a.width)
        path.lineTo(b.x - b.nx * b.width, b.y - b.ny * b.width)
        path.lineTo(b.x + b.nx * b.width, b.y + b.ny * b.width)
        path.lineTo(a.x + a.nx * a.width, a.y + a.ny * a.width)
        path.close()
    }
}
