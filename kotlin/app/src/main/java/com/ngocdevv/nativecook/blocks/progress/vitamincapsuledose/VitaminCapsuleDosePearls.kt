package com.ngocdevv.nativecook.blocks.progress.vitamincapsuledose

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.*

/** Forty colliding pearls, integrated in three short steps like the source compute pass. */
internal class VitaminCapsuleDosePearls {
    private val m = VitaminCapsuleDoseMotion
    private class Pearl {
        var x = 0.0; var y = 0.0; var vx = 0.0; var vy = 0.0
        var age = 0.0; var wait = 0.0; var live = false
    }
    private val pearls = Array(40) { Pearl() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sphere = RadialGradient(-0.35f, -0.45f, 1.4f,
        intArrayOf(Color.rgb(255, 251, 225), Color.rgb(255, 207, 85), Color.rgb(205, 132, 20)),
        floatArrayOf(0f, 0.58f, 1f), Shader.TileMode.CLAMP)
    private fun mouth(l: VitaminCapsuleDoseLayout, f: VitaminCapsuleDoseMotion.Frame) =
        Pair(l.cx + cos(f.tilt) * 0.08 * l.unit, l.cy + f.bob - sin(f.tilt) * 0.08 * l.unit)
    fun spawn(l: VitaminCapsuleDoseLayout, f: VitaminCapsuleDoseMotion.Frame) {
        val mouth = mouth(l, f)
        pearls.forEachIndexed { i, p ->
            val n = i.toDouble()
            p.x = mouth.first + (m.hash(n + 1) - 0.5) * 10
            p.y = mouth.second + (m.hash(n + 2) - 0.5) * 10
            val speed = 120 + 120 * m.hash(n + 3)
            p.vx = cos(f.tilt) * speed + (m.hash(n + 4) - 0.5) * 90
            p.vy = -sin(f.tilt) * speed - 60 * m.hash(n + 5)
            p.age = -0.6 * m.hash(n + 6); p.wait = 0.0; p.live = true
        }
    }
    fun step(delta: Double, l: VitaminCapsuleDoseLayout, f: VitaminCapsuleDoseMotion.Frame,
        goalX: Double, goalY: Double, fx: Double, fy: Double, dx: Double, dy: Double, down: Boolean) {
        if (f.mode == 0) { pearls.forEach { it.live = false }; return }
        val dt = min(1.0 / 30, delta) / 3
        val mouth = mouth(l, f)
        repeat(3) {
            pearls.forEachIndexed { i, p ->
                if (!p.live) return@forEachIndexed
                p.age += dt
                if (p.age < 0) { p.x = mouth.first; p.y = mouth.second; return@forEachIndexed }
                if (f.mode > 2) {
                    if (p.wait < m.hash(i + 7.0) * 0.5) { p.wait += dt; p.vy -= 260 * dt }
                    else {
                        val gx = if (f.mode == 4) goalX else mouth.first
                        val gy = if (f.mode == 4) goalY else mouth.second
                        val tx = gx - p.x; val ty = gy - p.y
                        p.vx += (tx * 90 - p.vx * 13) * dt; p.vy += (ty * 90 - p.vy * 13) * dt
                        if (hypot(tx, ty) < 7) p.live = false
                    }
                } else {
                    p.vy += 1500 * dt; p.vx *= 0.999; p.vy *= 0.999
                    if (down) {
                        val tx = p.x - fx; val ty = p.y - fy; val len = hypot(tx, ty)
                        if (len < 30) {
                            val safe = max(0.001, len)
                            p.x += tx / safe * (30 - len) * 0.5; p.y += ty / safe * (30 - len) * 0.5
                            p.vx += dx / max(delta, 0.004) * 0.25 * (1 - len / 30)
                            p.vy += dy / max(delta, 0.004) * 0.25 * (1 - len / 30)
                        }
                    }
                }
                p.x += p.vx * dt; p.y += p.vy * dt
                if (f.mode <= 2) {
                    if (p.y > l.floor - 5.5) { p.y = l.floor - 5.5; if (p.vy > 0) p.vy *= -0.32; p.vx *= 0.96 }
                    if (p.x < 14 * l.sx + 5.5) { p.x = 14 * l.sx + 5.5; p.vx = abs(p.vx) * 0.4 }
                    if (p.x > l.w - 14 * l.sx - 5.5) { p.x = l.w - 14 * l.sx - 5.5; p.vx = -abs(p.vx) * 0.4 }
                }
            }
            if (f.mode <= 2) for (i in pearls.indices) {
                val a = pearls[i]
                if (!a.live || a.age < 0) continue
                for (j in i + 1 until pearls.size) {
                    val b = pearls[j]
                    if (!b.live || b.age < 0) continue
                    val dxp = a.x - b.x; val dyp = a.y - b.y; val len = hypot(dxp, dyp)
                    if (len < 11 && len > 0.0001) {
                        val nx = dxp / len; val ny = dyp / len; val push = (11 - len) * 0.5
                        a.x += nx * push; a.y += ny * push; b.x -= nx * push; b.y -= ny * push
                        val va = a.vx * nx + a.vy * ny; val vb = b.vx * nx + b.vy * ny
                        if (va < 0) { a.vx -= nx * va * 0.6; a.vy -= ny * va * 0.6 }
                        if (vb > 0) { b.vx -= nx * vb * 0.6; b.vy -= ny * vb * 0.6 }
                    }
                }
            }
        }
    }
    fun draw(canvas: Canvas, l: VitaminCapsuleDoseLayout, f: VitaminCapsuleDoseMotion.Frame, goalY: Double) {
        paint.color = Color.WHITE; paint.alpha = 255; paint.shader = sphere
        pearls.forEach { p -> if (p.live && p.age >= 0) {
            canvas.save(); canvas.translate(p.x.toFloat(), p.y.toFloat()); canvas.scale(5.5f, 5.5f)
            canvas.drawCircle(0f, 0f, 1f, paint); canvas.restore()
        } }
        paint.shader = null
        if (f.take <= 0) return
        // Stable surface samples; a curved stream into the check follows the dissolve front.
        for (i in 0 until 360) {
            val n = i.toDouble(); val release = 0.15 + m.hash(n + 21) * 0.85
            val fly = ((f.take * 1.6 - release) / 0.55).coerceIn(0.0, 1.0)
            if (fly <= 0 || fly >= 1) continue
            val theta = 2 * PI * m.hash(n + 1); val radius = sqrt(m.hash(n + 2))
            var px = cos(theta) * radius; var py = sin(theta) * radius * 0.44
            if (f.a > 1.5) { px *= 0.6; py = py * 1.4 + 0.1 }
            else if (f.a > 0.5) { px *= 0.8; py *= 1.3 }
            else { val c = cos(m.TILT); val s = sin(m.TILT); val x = px; px = c * x - s * py; py = s * x + c * py }
            val ax = l.cx + px * l.unit; val ay = l.cy - py * l.unit
            val tx = l.checkX + (m.hash(n + 7) - 0.5) * 6
            val ty = goalY + (m.hash(n + 8) - 0.5) * 6
            val side = (m.hash(n + 5) - 0.5) * 140
            val cx = ax + (tx - ax) * 0.45 - 60 + side * 0.4
            val cy = ay + (ty - ay) * 0.45 - 30 + side * 0.3
            val e = fly * fly * (3 - 2 * fly); val u = 1 - e
            val alpha = m.seg(fly, 0.0, 0.06) * (1 - m.seg(fly, 0.9, 1.0))
            paint.color = if (i % 3 == 0) Color.rgb(55, 137, 255) else Color.rgb(209, 230, 255)
            paint.alpha = (alpha * 255).toInt()
            canvas.drawCircle((u*u*ax+2*u*e*cx+e*e*tx).toFloat(), (u*u*ay+2*u*e*cy+e*e*ty).toFloat(),
                ((1.3 + 1.6 * m.hash(n + 9)) * (1 - 0.6 * e)).toFloat(), paint)
        }
        paint.alpha = 255
    }
}
