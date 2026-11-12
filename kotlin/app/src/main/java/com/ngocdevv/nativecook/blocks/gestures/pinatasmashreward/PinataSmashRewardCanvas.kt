package com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardModel.PinataSmashRewardFrame
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardResources.PinataSmashRewardView
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardMotion.bulbLevel
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardMotion.clamp01
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardMotion.hash
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardMotion.seg
import kotlin.math.*

/** Native Canvas counterpart to psr-draw.ts; all coordinates remain in the 402 × 874 art space. */
internal class PinataSmashRewardCanvas(private val res: PinataSmashRewardResources) {
    private val mesh = PinataSmashRewardMesh()
    private val blur = PinataSmashRewardBlur()
    private val placed = PinataSmashRewardMesh.PinataSmashRewardPlaced()
    private val paint = Paint()
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val white = Color.WHITE
    private val purple = Color.rgb(135, 69, 221)
    private val glow = Color.rgb(181, 124, 255)
    private val confetti = intArrayOf(0xFFCE38FA.toInt(), 0xFF59DA8F.toInt(), 0xFF72A5F1.toInt(), 0xFFB257EC.toInt(), 0xFFEFB238.toInt(), 0xFFE94B91.toInt())
    private val filters = mutableMapOf<Int, PorterDuffColorFilter>()
    private val atop = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    private val screen = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    private fun rect(x: Double, y: Double, w: Double, h: Double) = RectF(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())
    private fun fill(color: Int, alpha: Double = 1.0, shader: Shader? = null): Paint {
        paint.reset(); paint.isAntiAlias = true; paint.isFilterBitmap = true; paint.color = color; paint.shader = shader
        paint.alpha = (Color.alpha(color) * clamp01(alpha)).roundToInt()
        return paint
    }
    private fun stroke(color: Int, width: Double, alpha: Double = 1.0): Paint {
        fill(color, alpha); paint.style = Paint.Style.STROKE; paint.strokeWidth = width.toFloat(); return paint
    }
    private fun mix(a: Int, b: Int, t: Double) = Color.rgb((Color.red(a) + (Color.red(b) - Color.red(a)) * t).roundToInt(), (Color.green(a) + (Color.green(b) - Color.green(a)) * t).roundToInt(), (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).roundToInt())
    private fun faded(c: Int, a: Double) = (c and 0x00FFFFFF) or ((clamp01(a) * 255).roundToInt() shl 24)
    private fun roundRect(c: Canvas, x: Double, y: Double, w: Double, h: Double, radius: Double, p: Paint) = c.drawRoundRect(rect(x, y, w, h), radius.toFloat(), radius.toFloat(), p)
    private fun circle(c: Canvas, x: Double, y: Double, r: Double, p: Paint) = c.drawCircle(x.toFloat(), y.toFloat(), r.toFloat(), p)
    private fun ribbon(c: Canvas, f: PinataSmashRewardFrame) {
        val x0 = res.ox + res.attach.x * res.k - 8
        val path = Path().apply { moveTo(x0.toFloat(), -30f); quadTo(((x0 + f.ax) / 2 - f.bow * 2).toFloat(), ((-30 + f.ay) / 2).toFloat(), f.ax.toFloat(), f.ay.toFloat()) }
        c.drawPath(path, stroke(0xFFD9C6F2.toInt(), 16.2)); c.drawPath(path, stroke(0xFFE9D7FF.toInt(), 16.2 - 1.6)); c.drawPath(path, stroke(0xFFF3E9FF.toInt(), 5.0, 0.75))
    }
    private fun token(c: Canvas, x: Double, y: Double, s: Double, a: Double) {
        val r = 17 * s
        if (r < 0.5 || a <= 0.01) return
        circle(c, x, y, r, fill(white, a, LinearGradient((x - r * 0.5).toFloat(), (y - r).toFloat(), (x + r * 0.4).toFloat(), (y + r).toFloat(), intArrayOf(0xFFB57BE0.toInt(), 0xFF8B41BE.toInt(), 0xFF6E2C9C.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)))
        circle(c, x, y, r * 0.84, fill(0xFF8B41BE.toInt(), a))
        c.drawOval(rect(x - r * 0.62, y - r * 0.8, r * 0.9, r * 0.5), fill(white, 0.18 * a))
        val w = r * 0.42; val h = r * 0.26
        val path = Path()
        repeat(2) { val cy = y - r * 0.14 + it * r * 0.34; path.moveTo((x - w).toFloat(), (cy + h * 0.5).toFloat()); path.lineTo(x.toFloat(), (cy - h * 0.5).toFloat()); path.lineTo((x + w).toFloat(), (cy + h * 0.5).toFloat()) }
        val p = stroke(white, 2.2 * s, 0.9 * a); p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND; c.drawPath(path, p)
    }
    private fun bolt(x: Double, y: Double): Path {
        val pts = doubleArrayOf(8.2, 0.0, 0.6, 10.0, 6.0, 10.0, 4.6, 17.0, 12.4, 6.6, 7.1, 6.6)
        return Path().apply { moveTo((x + pts[0]).toFloat(), (y + pts[1]).toFloat()); for (i in 1 until 6) lineTo((x + pts[i * 2]).toFloat(), (y + pts[i * 2 + 1]).toFloat()); close() }
    }
    private fun pill(c: Canvas, f: PinataSmashRewardFrame) {
        val py = 427.7 + 56; val ph = 45.5; val w = f.pillW; val h = ph + (43.6 - ph) * f.wB; val cx = 201.3 + f.shake
        val x = 201.3 - w / 2 + f.shake; val y = py + (ph - h) / 2
        val sum = max(1e-4, f.wL + f.wB + f.wD)
        val col = mix(mix(0xFF141414.toInt(), Color.BLACK, f.wB / sum), 0xFFF0EFEB.toInt(), f.wD / sum)
        roundRect(c, x, y, w, h, 11.0, fill(col))
        c.save(); c.clipPath(Path().apply { addRoundRect(rect(x, y, w, h), 11f, 11f, Path.Direction.CW) })
        val base = py + ph / 2 + 15.5 * 0.35
        val la = clamp01((f.wL - 0.55) / 0.45)
        if (la > 0.01) {
            val tx = cx - (res.labelTextW + 8 + 13) / 2
            res.label.color = white; res.label.alpha = (la * 255).roundToInt(); res.drawText(c, "Keep tapping", tx, base, res.label)
            val p = stroke(white, 1.6, la); p.strokeJoin = Paint.Join.ROUND
            c.drawPath(bolt(tx + res.labelTextW + 8, py + ph / 2 - 17.0 / 2), p)
        }
        val ba = clamp01((f.wB - 0.4) / 0.6)
        if (ba > 0.01) {
            val tx = cx - 50; val ty = py + ph / 2 - 6.7 / 2
            roundRect(c, tx, ty, 100.0, 6.7, 6.7 / 2, fill(0xFFD6D6D4.toInt(), ba))
            val fw = min(100.0, max(6.0, 100 * f.fill))
            if (f.fillGlow > 0.01) {
                blur.bar(c, tx - 1, ty - 1, fw + 2, 8.7, 6.7 / 2 + 1, glow, 0.55 * f.fillGlow * ba)
            }
            roundRect(c, tx, ty, fw, 6.7, 6.7 / 2, fill(purple, ba))
        }
        val da = min(clamp01((f.wD - 0.5) / 0.5), f.doneText)
        if (da > 0.01) { res.label.color = 0xFFA3A3A0.toInt(); res.label.alpha = (da * 255).roundToInt(); res.drawText(c, "Come back tomorrow", cx - res.doneTextW / 2, base, res.label) }
        c.restore()
        if (f.sparks >= 0 && f.sparks < 0.5) {
            val u = f.sparks / 0.5; val ex = 201.3 + 60; val ey = py + ph / 2
            for (i in 0 until 6) {
                val a = (-70 + i * 28) * PI / 180; val r0 = 6 + 34 * u; val r1 = r0 + 9 * (1 - u)
                val p = stroke(purple, 2.0, 1 - u); p.strokeCap = Paint.Cap.ROUND
                c.drawLine((ex + cos(a) * r0).toFloat(), (ey + sin(a) * r0).toFloat(), (ex + cos(a) * r1).toFloat(), (ey + sin(a) * r1).toFloat(), p)
            }
        }
    }
    private fun header(c: Canvas, f: PinataSmashRewardFrame, top: Double) {
        res.title.color = 0xFF141414.toInt(); res.title.alpha = 255; res.drawText(c, "Daily piñata", 24.0, 78 + top, res.title)
        val x0 = res.chipX0; val w = res.chipWidth; val cy = 58 + 18 + top; val b = (1 + 0.08 * f.chipBump).toFloat()
        c.save(); c.scale(b, b, (x0 + w / 2).toFloat(), cy.toFloat())
        roundRect(c, x0, 58 + top, w, 36.0, 18.0, fill(0xFFF3EEFD.toInt()))
        token(c, x0 + 8 + 11, cy, 11.0 / 17, 1.0)
        res.chipFont.color = 0xFF6B2FB3.toInt(); res.chipFont.alpha = 255
        res.drawText(c, floor(f.count + 0.5).toInt().toString(), x0 + 8 + 22 + 7, cy + 15 * 0.36, res.chipFont)
        c.restore()
    }
    private fun confetti(c: Canvas, f: PinataSmashRewardFrame) {
        for (v in f.conf) {
            if (v.a <= 0.01) continue
            val hw = v.w / 2; val hh = v.h / 2; val ca = cos(v.rot); val sa = sin(v.rot)
            val pts = if (v.diamond) doubleArrayOf(0.0, -hh, hw, 0.0, 0.0, hh, -hw, 0.0) else doubleArrayOf(-hw, -hh, hw, -hh, hw, hh, -hw, hh)
            val p = Path()
            for (q in 0 until 4) { val x = v.x + pts[q * 2] * ca - pts[q * 2 + 1] * sa; val y = v.y + pts[q * 2] * sa + pts[q * 2 + 1] * ca; if (q == 0) p.moveTo(x.toFloat(), y.toFloat()) else p.lineTo(x.toFloat(), y.toFloat()) }
            p.close(); c.drawPath(p, fill(confetti[v.c], v.a))
        }
    }
    private fun streamers(c: Canvas, f: PinataSmashRewardFrame) {
        if (f.streamers < 0) return
        for (i in 0 until 10) {
            val delay = hash(i + 40.0) * 0.5; val tau = f.streamers - delay
            if (tau < 0) continue
            val v = 180 + 80 * hash(i + 41.0); val x0 = 24 + 354 * (i + hash(i + 42.0) * 0.8) / 10; val y0 = -60 + v * tau
            if (y0 > 900) continue
            val sway = 20 * sin(tau * 2.4 + i); val path = Path()
            for (s in 0..12) { val q = s / 12.0; val x = x0 + sway + 7 * sin(q * PI * 3 + tau * 9 + i); val y = y0 + q * 46; if (s == 0) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat()) }
            val p = stroke(if (i % 2 == 0) 0xFF59DA8F.toInt() else 0xFFCE38FA.toInt(), 3.0, f.fade * (1 - seg(y0, 780.0, 860.0))); p.strokeCap = Paint.Cap.ROUND; c.drawPath(path, p)
        }
    }
    private fun atlas(c: Canvas, f: PinataSmashRewardFrame) {
        imagePaint.colorFilter = null; imagePaint.xfermode = null
        for (l in f.loot) {
            if (l.a <= 0.01) continue
            val s = res.sprites[l.k]; val scale = s.scale * l.s * max(0.25, abs(l.sx))
            c.save(); c.translate(l.x.toFloat(), l.y.toFloat()); c.rotate((l.rot * 180 / PI).toFloat()); c.scale(scale.toFloat(), scale.toFloat())
            imagePaint.alpha = (clamp01(l.a) * 255).roundToInt()
            c.drawBitmap(res.atlas, s.rect, rect(-s.cx, -s.cy, s.rect.width().toDouble(), s.rect.height().toDouble()), imagePaint); c.restore()
        }
    }
    private fun glow(c: Canvas, x: Double, y: Double, r: Double, tint: Int, alpha: Double, blend: PorterDuffXfermode) {
        if (alpha <= 0.003 || r <= 0.5) return
        val s = res.sprites[10]; val scale = r / s.cx
        imagePaint.alpha = (clamp01(alpha) * 255).roundToInt(); imagePaint.colorFilter = filters.getOrPut(tint) { PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN) }; imagePaint.xfermode = blend
        c.drawBitmap(res.atlas, s.rect, rect(x - scale * s.cx, y - scale * s.cy, s.rect.width() * scale, s.rect.height() * scale), imagePaint)
    }
    fun draw(c: Canvas, f: PinataSmashRewardFrame, view: PinataSmashRewardView) {
        mesh.beginFrame(); blur.beginFrame(view.s); c.drawColor(white); c.save(); c.scale(view.s.toFloat(), view.s.toFloat()); c.translate(0f, view.dy.toFloat())
        c.save()
        if (f.stage != 1.0) { c.translate(f.stageX.toFloat(), f.stageY.toFloat()); c.scale(f.stage.toFloat(), f.stage.toFloat()); c.translate(-f.stageX.toFloat(), -f.stageY.toFloat()) }
        val sw = 150 * (1 - 0.4 * f.shadowLift); val sh = 18 * (1 - 0.4 * f.shadowLift)
        blur.shadow(c, f.ax - 10 - sw / 2, 838 - sh / 2, sw, sh, 0.05 * (1 - 0.5 * f.shadowLift), f.stage)
        ribbon(c, f)
        c.saveLayer(null, null)
        mesh.vertices(res, f, res.tailGrid, true); mesh.draw(c, res.tailGrid, res.tailShader, 1.0, view.s.toFloat())
        mesh.vertices(res, f, res.bodyGrid, false)
        if (f.burst < 0.999) mesh.draw(c, res.bodyGrid, res.bodyShader, 1.0, view.s.toFloat())
        if (f.burst > 0.001) mesh.draw(c, res.bodyGrid, res.burstShader, f.burst, view.s.toFloat())
        if (f.crack1 > 0.002 && f.burst < 0.999) mesh.draw(c, res.bodyGrid, res.crack1Shader, f.crack1 * (1 - f.burst), view.s.toFloat())
        if (f.crack2 > 0.002 && f.burst < 0.999) mesh.draw(c, res.bodyGrid, res.crack2Shader, f.crack2 * (1 - f.burst), view.s.toFloat())
        val bulbXY = DoubleArray(res.bulbs.size * 2); val levels = DoubleArray(res.bulbs.size)
        for ((i, b) in res.bulbs.withIndex()) {
            mesh.place(res, f, b.x, b.y, placed); bulbXY[i * 2] = placed.x; bulbXY[i * 2 + 1] = placed.y
            val lv = bulbLevel(i, f); levels[i] = lv
            glow(c, placed.x, placed.y, 20 + 10 * max(0.0, lv - 1), b.color, 0.34 * min(1.8, lv), atop)
        }
        if (f.bloom > 0.002) { mesh.place(res, f, (f.bloomX - res.ox) / res.k, (f.bloomY - res.oy) / res.k, placed); glow(c, placed.x, placed.y, 90.0, white, f.bloom * 1.1, atop) }
        c.restore()
        for ((i, b) in res.bulbs.withIndex()) {
            val lv = levels[i]; val x = bulbXY[i * 2]; val y = bulbXY[i * 2 + 1]; val r = b.r * res.k
            glow(c, x, y, r * (2.4 + 1.4 * clamp01(lv - 1)), b.color, 0.45 + 0.5 * clamp01(lv - 1), screen)
            glow(c, x, y, r * 1.1, white, 0.3 + 0.6 * clamp01(lv - 0.9), screen)
        }
        if (f.hole > 0.002) { mesh.place(res, f, res.flank.x, res.flank.y, placed); glow(c, placed.x, placed.y, 52.0, Color.rgb(255, 212, 107), f.hole * 1.3, screen) }
        if (f.rippleA > 0.002) {
            val color = 0xFFDDF5F5.toInt(); val r = f.rippleR
            circle(c, f.rippleX, f.rippleY, r, fill(white, shader = RadialGradient(f.rippleX.toFloat(), f.rippleY.toFloat(), r.toFloat(), intArrayOf(faded(color, f.rippleA * 0.55), faded(color, f.rippleA * 0.8), faded(color, 0.0)), floatArrayOf(0f, max(0.0, 1 - 24 / r).toFloat(), 1f), Shader.TileMode.CLAMP)))
        }
        if (f.ringA > 0.002) circle(c, f.ringX, f.ringY, f.ringR, stroke(glow, 3.0, f.ringA * 0.6))
        atlas(c, f); confetti(c, f); streamers(c, f)
        c.restore(); pill(c, f); c.restore()
        c.save(); c.scale(view.s.toFloat(), view.s.toFloat())
        val bottom = 106 + view.top
        c.drawRect(rect(0.0, -10.0, 402.0, bottom + 10), fill(white))
        c.drawRect(rect(0.0, bottom, 402.0, 24.0), fill(white, shader = LinearGradient(0f, bottom.toFloat(), 0f, (bottom + 24).toFloat(), intArrayOf(white, 0x00FFFFFF), null, Shader.TileMode.CLAMP)))
        c.restore(); c.save(); c.scale(view.s.toFloat(), view.s.toFloat()); c.translate(0f, view.dy.toFloat())
        for (tk in f.tokens) { for (q in tk.tx.indices.reversed()) token(c, tk.tx[q], tk.ty[q], tk.s * (0.9 - q * 0.08), 0.11 - q * 0.025); token(c, tk.x, tk.y, tk.s, tk.a) }
        c.restore(); c.save(); c.scale(view.s.toFloat(), view.s.toFloat()); header(c, f, view.top); c.restore()
        if (f.flash > 0.002) c.drawRect(rect(0.0, 0.0, view.w, view.h), fill(white, f.flash))
    }
}
