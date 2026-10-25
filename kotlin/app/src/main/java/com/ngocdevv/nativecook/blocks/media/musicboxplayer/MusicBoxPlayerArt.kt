package com.ngocdevv.nativecook.blocks.media.musicboxplayer

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.blocks.media.musicboxplayer.MusicBoxGfx.Companion.linear
import com.ngocdevv.nativecook.blocks.media.musicboxplayer.MusicBoxGfx.Companion.radial
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 Music Box Player — the drawing, ported from musicBoxArt.ts. Baked once (off the main thread): a
 violet night room with a warm lamp glow, the walnut box with its lid open (velvet panel, gold
 inlay, an oval mirror), the velvet well, the brass bed plate and bearings, the brass cylinder,
 the comb's base, the front face with an escutcheon and the crank's hub, and the player panel's
 plate. Per frame: the cylinder's seams and pins (on its visible half, shaded by their angle), the
 18 teeth (tips bent by the pins, ringing), a warm glow at each plucked tip, floating notes, the
 governor's fan, the crank, and the playhead.
*/

internal class MusicBoxLayout(val w: Float, val h: Float, top: Float, bottom: Float) {
    val panelH = 176f
    val panelY = h - max(bottom, 12f) - panelH
    val lidTop = top + 110
    val s = max(0.72f, min(1.1f, (panelY - 18 - lidTop) / 430))
    val boxX = 14f
    val boxW = w - 28
    val rimY = lidTop + 150 * s
    val frontY = rimY + 196 * s
    val x0 = boxX + 14
    val x1 = boxX + boxW - 14
    val cylR = 25 * s
    val cylY = rimY + 24 * s + cylR
    val tipY = cylY + cylR * sin(MUSICBOX_CONTACT).toFloat()
    val frontH = 64 * s
    val cylX0 = x0 + 40
    val cylX1 = x1 - 22
    val combY = tipY + 52 * s
    val hubX = boxX + boxW - 50
    val hubY = frontY + frontH / 2
    val arm = 30 * s
    val fanX = x0 + 20
    val fanY = cylY
    val barX = 36f
    val barY = panelY + 58
    val barW = w - 72
    val geo = MusicBoxGeo(
        w.toDouble(), h.toDouble(), cylX0.toDouble(), cylX1.toDouble(), cylY.toDouble(), cylR.toDouble(),
        tipY.toDouble(), combY.toDouble(), hubX.toDouble(), hubY.toDouble(), arm.toDouble(),
    )
}

internal fun musicBoxTimeFont() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.MONOSPACE, 600, false) else Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    textSize = 12f
}

internal object MusicBoxPlayerArt {
    private fun walnut(c: Canvas, x: Float, y: Float, w: Float, h: Float, seed: Double, gx: MusicBoxGfx) {
        c.drawRect(x, y, x + w, y + h, gx.shaded(linear(x, y, x, y + h, arrayOf("#6A3E24", "#55301B", "#40230F"))))
        c.save()
        c.clipRect(x, y, x + w, y + h)
        val b = Path()
        for (i in 0 until Math.round(h / 4)) {
            b.reset()
            val yy = y + i * 4 + 2
            b.moveTo(x, yy)
            for (k in 1..10) b.lineTo(x + w * k / 10, yy + (sin(k * 0.9 + i * 1.7 + seed) * 1.6).toFloat())
            c.drawPath(b, gx.stroke(if (i % 3 == 0) "#2A140A" else "#7C4A2C", 0.7f, 0.35))
        }
        c.restore()
    }

    private fun screw(c: Canvas, x: Float, y: Float, r: Float, gx: MusicBoxGfx) {
        c.drawCircle(x, y, r, gx.shaded(radial(x - r * 0.4f, y - r * 0.4f, r * 1.6f, arrayOf("#FFF0BE", "#C89A45", "#6E4F1C"))))
        c.drawLine(x - r * 0.6f, y + r * 0.3f, x + r * 0.6f, y - r * 0.3f, gx.stroke("#4A3410", 0.8f))
    }

    private fun drawScene(c: Canvas, g: MusicBoxLayout, f: Paint, gx: MusicBoxGfx) {
        val w = g.w
        val h = g.h
        val s = g.s
        val full = RectF(0f, 0f, w, h)
        c.drawRect(full, gx.shaded(linear(0f, 0f, 0f, h, arrayOf("#231D3A", "#1B1730", "#141126", "#0E0C1C"), floatArrayOf(0f, 0.4f, 0.75f, 1f))))
        c.drawRect(full, gx.shaded(radial(w * 0.5f, g.rimY, w * 0.95f, arrayOf("rgba(255,190,120,0.26)", "rgba(180,120,120,0.1)", "rgba(40,30,70,0)"), floatArrayOf(0f, 0.5f, 1f))))
        // Soft bokeh from the night-light, out of focus behind the box.
        for (i in 0 until 7) {
            val x = w * (0.08f + 0.84f * abs(sin(i * 7.31 + 0.4)).toFloat())
            val y = g.lidTop * (0.35f + 0.6f * abs(sin(i * 3.17 + 1)).toFloat())
            c.drawCircle(x, y, 10 + 18 * abs(sin(i * 1.3)).toFloat(), gx.blurred(if (i % 2 == 0) "#F3C987" else "#B9A6F0", 0.1, 8f))
        }
        val bx = g.boxX
        val bw = g.boxW
        // Shadow under the box.
        c.drawOval(bx - 4, g.frontY + g.frontH - 10, bx + bw + 4, g.frontY + g.frontH + 24, gx.blurred("#05040A", 0.7, 14f))
        // The lid, standing open behind the box: a trapezoid (tilted back), walnut frame, velvet inside.
        val inset = bw * 0.035f
        val lid = Path().apply {
            moveTo(bx + inset, g.lidTop)
            lineTo(bx + bw - inset, g.lidTop)
            lineTo(bx + bw, g.rimY)
            lineTo(bx, g.rimY)
            close()
        }
        c.save()
        c.clipPath(lid)
        walnut(c, bx, g.lidTop, bw, g.rimY - g.lidTop, 3.0, gx)
        c.restore()
        val pad = 12 * s
        val vel = Path().apply {
            moveTo(bx + inset + pad, g.lidTop + pad)
            lineTo(bx + bw - inset - pad, g.lidTop + pad)
            lineTo(bx + bw - pad, g.rimY - pad * 0.5f)
            lineTo(bx + pad, g.rimY - pad * 0.5f)
            close()
        }
        c.drawPath(vel, gx.shaded(radial(bx + bw * 0.4f, g.lidTop + 20, bw * 0.8f, arrayOf("#4C3A78", "#352866", "#241A48"), floatArrayOf(0f, 0.5f, 1f))))
        c.save()
        c.clipPath(vel)
        MusicBoxGrain(5).draw(c, RectF(bx, g.lidTop, bx + bw, g.rimY), 0.12)
        c.restore()
        c.drawPath(vel, gx.stroke("#D9B56A", 1.4f, 0.8).apply { strokeJoin = Paint.Join.ROUND })
        // The oval mirror, catching the lamp.
        val mx = g.w / 2
        val my = (g.lidTop + g.rimY) / 2
        val mrx = bw * 0.2f
        val mry = (g.rimY - g.lidTop) * 0.3f
        c.drawOval(mx - mrx - 4, my - mry - 4, mx + mrx + 4, my + mry + 4, gx.shaded(linear(0f, my - mry, 0f, my + mry, arrayOf("#F5DA94", "#B98B3C", "#7A5520"))))
        val mirror = RectF(mx - mrx, my - mry, mx + mrx, my + mry)
        c.drawOval(mirror, gx.shaded(linear(mx - mrx, my - mry, mx + mrx, my + mry, arrayOf("#9AA4C8", "#5A5F8A", "#2C2E55", "#6E6A98"), floatArrayOf(0f, 0.35f, 0.7f, 1f))))
        c.save()
        c.clipPath(Path().apply { addOval(mirror, Path.Direction.CW) })
        c.drawRect(mx - mrx * 0.6f, my - mry, mx - mrx * 0.25f, my + mry, gx.shaded(linear(mx - mrx * 0.6f, 0f, mx - mrx * 0.25f, 0f, arrayOf("rgba(255,255,255,0)", "rgba(255,255,255,0.3)", "rgba(255,255,255,0)"))))
        c.drawCircle(mx + mrx * 0.3f, my + mry * 0.2f, mry * 0.45f, gx.blurred("#FFC98A", 0.35, 10f))
        c.restore()
        // Brass hinges.
        for (hx in floatArrayOf(bx + bw * 0.22f, bx + bw * 0.78f)) {
            c.drawRect(hx - 16, g.rimY - 5, hx + 16, g.rimY + 3, gx.shaded(linear(0f, g.rimY - 5, 0f, g.rimY + 3, arrayOf("#F4D78C", "#A7782E", "#6B4A18"))))
        }
        // Box rim and the velvet well.
        walnut(c, bx, g.rimY, bw, g.frontY - g.rimY, 9.0, gx)
        c.drawLine(bx, g.rimY + 0.5f, bx + bw, g.rimY + 0.5f, gx.stroke("#E8B888", 1f, 0.5))
        val well = RectF(g.x0, g.rimY + 10 * s, g.x1, g.frontY - 8 * s)
        c.drawRoundRect(well, 6f, 6f, gx.shaded(linear(0f, g.rimY, 0f, g.frontY, arrayOf("#2A1E4C", "#3A2A66", "#2E2254"))))
        c.save()
        c.clipPath(Path().apply { addRoundRect(well, 6f, 6f, Path.Direction.CW) })
        MusicBoxGrain(8).draw(c, RectF(g.x0, g.rimY, g.x1, g.frontY), 0.14)
        c.drawRoundRect(g.x0 - 10, g.rimY + 2 * s, g.x1 + 10, g.rimY + 20 * s, 6f, 6f, gx.blurred("#0C0818", 0.7, 8f))
        c.restore()
        // Brass bed plate with screws, and the bearings at the cylinder's ends.
        val py0 = g.cylY - g.cylR - 10 * s
        val py1 = g.combY + 26 * s
        c.drawRoundRect(g.cylX0 - 12, py0 + 3, g.cylX1 + 12, py1 + 3, 5f, 5f, gx.blurred("#0A0614", 0.6, 5f))
        c.drawRoundRect(g.cylX0 - 12, py0, g.cylX1 + 12, py1, 5f, 5f, gx.shaded(linear(0f, py0, 0f, py1, arrayOf("#D8B56C", "#B58C44", "#8C6628"))))
        c.drawRoundRect(g.cylX0 - 12, py0, g.cylX1 + 12, py1, 5f, 5f, gx.stroke("#FFE9B0", 0.8f, 0.5))
        screw(c, g.cylX0 - 4, py1 - 8 * s, 3 * s, gx)
        screw(c, g.cylX1 + 4, py1 - 8 * s, 3 * s, gx)
        for (ex in floatArrayOf(g.cylX0 - 6, g.cylX1 + 6)) {
            c.drawRect(ex - 5, g.cylY - g.cylR - 6, ex + 5, g.cylY + g.cylR + 6, gx.shaded(linear(ex - 5, 0f, ex + 5, 0f, arrayOf("#8C6628", "#F0D08A", "#7A5520"))))
            c.drawCircle(ex, g.cylY, 3f, gx.paint("#4A3410"))
        }
        // The cylinder: brass, lit from above.
        val cyl = RectF(g.cylX0, g.cylY - g.cylR, g.cylX1, g.cylY + g.cylR)
        c.drawRect(cyl, gx.shaded(linear(0f, g.cylY - g.cylR, 0f, g.cylY + g.cylR, arrayOf("#5E4316", "#C99D4C", "#F6DE9A", "#D9AE5C", "#8A6224", "#3F2B0C"), floatArrayOf(0f, 0.18f, 0.34f, 0.5f, 0.78f, 1f))))
        MusicBoxGrain(4).draw(c, cyl, 0.12)
        // The comb's base: a steel block with screws.
        val cb = g.combY
        c.drawRoundRect(g.cylX0 - 2, cb, g.cylX1 + 2, cb + 16 * s, 3f, 3f, gx.shaded(linear(0f, cb, 0f, cb + 16 * s, arrayOf("#E9EEF2", "#9AA4AE", "#5C656F"))))
        for (k in 0 until 5) screw(c, g.cylX0 + 14 + (g.cylX1 - g.cylX0 - 28) * k / 4, cb + 8 * s, 2.6f * s, gx)
        // Front face: walnut with an inlay band, a keyhole escutcheon and the crank's hub plate.
        walnut(c, bx, g.frontY, bw, g.frontH, 13.0, gx)
        c.drawRect(bx, g.frontY, bx + bw, g.frontY + g.frontH, gx.shaded(linear(0f, g.frontY, 0f, g.frontY + g.frontH, arrayOf("rgba(255,220,180,0.1)", "rgba(0,0,0,0)", "rgba(10,4,0,0.35)"))))
        c.drawLine(bx, g.frontY + 0.5f, bx + bw, g.frontY + 0.5f, gx.stroke("#F0C090", 1f, 0.45))
        c.drawLine(bx + 16, g.frontY + g.frontH * 0.5f, g.hubX - 44, g.frontY + g.frontH * 0.5f, gx.stroke("#D9B56A", 1.2f, 0.6))
        val ex = g.w * 0.42f
        val ey = g.frontY + g.frontH * 0.5f
        c.drawOval(ex - 13, ey - 17, ex + 13, ey + 17, gx.shaded(linear(0f, ey - 17, 0f, ey + 17, arrayOf("#F6DA90", "#B98B3C", "#6E4D1A"))))
        c.drawCircle(ex, ey - 4, 3.2f, gx.paint("#1A0E05"))
        c.drawRect(ex - 1.4f, ey - 4, ex + 1.4f, ey + 6, gx.paint("#1A0E05"))
        c.drawCircle(g.hubX, g.hubY, 16 * s, gx.blurred("#0A0408", 0.5, 4f))
        c.drawCircle(g.hubX, g.hubY, 14 * s, gx.shaded(radial(g.hubX - 4, g.hubY - 5, 18 * s, arrayOf("#FBE6A8", "#C2913E", "#6E4D1A"))))
        // Player panel plate.
        c.drawRoundRect(16f, g.panelY + 10, w - 16, g.panelY + 10 + g.panelH, 24f, 24f, gx.blurred("#05040A", 0.55, 16f))
        c.drawRoundRect(16f, g.panelY, w - 16, g.panelY + g.panelH, 24f, 24f, gx.shaded(linear(0f, g.panelY, 0f, g.panelY + g.panelH, arrayOf("#2C2645", "#231E3A", "#1C1830"))))
        c.drawRoundRect(16.5f, g.panelY + 0.5f, w - 16.5f, g.panelY + g.panelH - 0.5f, 23.5f, 23.5f, gx.stroke("#E9E3FF", 1f, 0.1))
        c.drawRoundRect(g.barX, g.barY - 2, g.barX + g.barW, g.barY + 2, 2f, 2f, gx.paint("#E9E3FF", 0.14))
        val total = "0:$MUSICBOX_SONG_SECONDS"
        f.color = musicBoxColor("#E9E3FF")
        f.alpha = 128
        c.drawText(total, g.barX + g.barW - f.measureText(total), g.barY + 22, f)
        c.drawRect(full, gx.shaded(radial(w / 2, h * 0.45f, hypot(w, h) * 0.62f, arrayOf("rgba(8,6,16,0)", "rgba(8,6,16,0.25)", "rgba(6,4,12,0.6)"), floatArrayOf(0f, 0.6f, 1f))))
    }

    suspend fun bakeScene(g: MusicBoxLayout, scale: Float): MusicBoxBaked {
        coroutineContext.ensureActive()
        return MusicBoxGfx.bake(0f, 0f, g.w, g.h, scale) { drawScene(it, g, musicBoxTimeFont(), MusicBoxGfx(live = false)) }
    }
}

/** Draws a frame on the screen's canvas (in dp). */
internal class MusicBoxPainter(g: MusicBoxLayout) {
    private val gx = MusicBoxGfx(live = true)
    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val time = musicBoxTimeFont().apply {
        color = musicBoxColor("#E9E3FF")
        alpha = (255 * 0.75).toInt()
    }
    private val toothPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, g.tipY, 0f, g.combY, MusicBoxGfx.colors(arrayOf("#F7FAFC", "#B7C0C9", "#7D8791")), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
    }
    private val glowShader = RadialGradient(0f, 0f, 22 * g.s, MusicBoxGfx.colors(arrayOf("rgba(255,214,140,0.9)", "rgba(255,180,100,0.3)", "rgba(255,160,80,0)")), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
    private val haloShader = RadialGradient(2f, -6f, 16f, MusicBoxGfx.colors(arrayOf("rgba(255,210,140,0.45)", "rgba(255,210,140,0)")), null, Shader.TileMode.CLAMP)
    private val barShader = LinearGradient(g.barX, 0f, g.barX + g.barW, 0f, MusicBoxGfx.colors(arrayOf("#F3C987", "#E9A6C8")), null, Shader.TileMode.CLAMP)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = glowShader
        xfermode = MusicBoxGfx.plus
    }
    private val tooth = Path()
    private val edges = Path()
    private val shades = Path()
    private val oval = RectF()
    private val bladeColors = MusicBoxGfx.colors(arrayOf("#FFF0C0", "#B8893A"))

    /** An eighth note, about 16 dp tall, its head's centre at the origin. */
    private val note = Path().apply {
        addOval(RectF(-4.6f, -3.2f, 4.6f, 3.2f), Path.Direction.CW)
        moveTo(3.6f, -1f)
        lineTo(3.6f, -16f)
        lineTo(5.2f, -16f)
        cubicTo(6.5f, -12f, 10.5f, -11.5f, 9.5f, -6.5f)
        cubicTo(9f, -9.5f, 7f, -10.5f, 5.2f, -11f)
        lineTo(5.2f, -1f)
        close()
    }

    private fun wrap(a: Double): Double {
        val k = PI * 2
        var x = ((a % k) + k) % k
        if (x > PI) x -= k
        return x
    }

    /** Before the scene is baked: the room, the box and the panel in flat shapes. */
    fun placeholder(c: Canvas, g: MusicBoxLayout) {
        c.drawColor(musicBoxColor("#161328"))
        c.drawRect(g.boxX, g.lidTop, g.boxX + g.boxW, g.frontY + g.frontH, gx.paint("#55301B"))
        c.drawRect(g.cylX0, g.cylY - g.cylR, g.cylX1, g.cylY + g.cylR, gx.paint("#C99D4C"))
        c.drawRoundRect(16f, g.panelY, g.w - 16, g.panelY + g.panelH, 24f, 24f, gx.paint("#231E3A"))
    }

    fun frame(c: Canvas, g: MusicBoxLayout, scene: MusicBoxBaked, p: MusicBoxPose) {
        image.alpha = 255
        scene.draw(c, image)
        val geo = g.geo
        val s = g.s
        // Seams on the cylinder so its turning reads between pins.
        for (k in 0 until 8) {
            val phi = wrap(k * PI / 4 + p.angle)
            val cv = cos(phi)
            if (cv <= 0.05) continue
            val y = g.cylY + g.cylR * sin(phi).toFloat()
            c.drawLine(g.cylX0, y, g.cylX1, y, gx.stroke("#3F2B0C", 0.7f, 0.28 * cv))
        }
        // Pins.
        for (i in 0 until MusicBoxMotion.PIN_COUNT) {
            val phi = wrap(MusicBoxMotion.pinAngle(i) + p.angle)
            val cv = cos(phi)
            if (cv <= 0.04) continue
            val x = MusicBoxMotion.toothX(geo, MusicBoxMotion.PINS[i * 2 + 1]).toFloat()
            val y = g.cylY + g.cylR * sin(phi).toFloat()
            val r = 1.9f * s
            val cvf = cv.toFloat()
            val hgt = r * 2 * max(0.3f, cvf)
            oval.set(x - r, y - r * cvf + 1.2f, x + r, y - r * cvf + 1.2f + hgt)
            c.drawOval(oval, gx.paint("#3A2608", 0.5 * cv))
            oval.set(x - r, y - r * cvf, x + r, y - r * cvf + hgt)
            c.drawOval(oval, gx.paint("#FFF3CF", 0.35 + 0.65 * cv))
        }
        // Teeth: steel strips from the base up to the cylinder, tips bent by the pins.
        val pitch = (g.cylX1 - g.cylX0 - 24) / MUSICBOX_TEETH
        val tw = pitch * 0.64f
        edges.reset()
        shades.reset()
        for (j in 0 until MUSICBOX_TEETH) {
            val x = MusicBoxMotion.toothX(geo, j).toFloat()
            val tip = p.tips[j].toFloat()
            val ty = g.tipY + 1 + tip
            val w0 = tw * (1.12f - 0.24f * j / MUSICBOX_TEETH)
            tooth.reset()
            tooth.moveTo(x - w0 / 2, g.combY + 1)
            tooth.lineTo(x - w0 / 2 + tip * 0.05f, ty + 1.5f)
            tooth.quadTo(x, ty - 1, x + w0 / 2 + tip * 0.05f, ty + 1.5f)
            tooth.lineTo(x + w0 / 2, g.combY + 1)
            tooth.close()
            c.drawPath(tooth, toothPaint)
            edges.moveTo(x - w0 / 2 + 0.7f, g.combY)
            edges.lineTo(x - w0 / 2 + 0.7f, ty + 2)
            shades.moveTo(x + w0 / 2 - 0.6f, g.combY)
            shades.lineTo(x + w0 / 2 - 0.6f, ty + 2)
        }
        c.drawPath(edges, gx.stroke("#FFFFFF", 0.8f, 0.7))
        c.drawPath(shades, gx.stroke("#3E464E", 0.8f, 0.5))
        // Warm glow where a tooth was plucked.
        for (j in 0 until MUSICBOX_TEETH) {
            val k = p.glow[j]
            if (k < 0.02) continue
            val x = MusicBoxMotion.toothX(geo, j).toFloat()
            glowPaint.alpha = (255 * k).toInt().coerceIn(0, 255)
            c.save()
            c.translate(x, g.tipY)
            c.drawCircle(0f, 0f, 22 * s, glowPaint)
            c.restore()
        }
        // Notes floating up.
        for (i in 0 until p.noteCount) {
            val o = i * 5
            val a = p.notes[o + 3]
            if (a < 0.01) continue
            c.save()
            c.translate(p.notes[o].toFloat(), p.notes[o + 1].toFloat())
            c.rotate(p.notes[o + 4].toFloat())
            val k = p.notes[o + 2].toFloat() * s
            c.scale(k, k)
            c.drawCircle(2f, -6f, 16f, gx.shaded(haloShader, a))
            c.drawPath(note, gx.paint("#FFF1D6", a))
            c.restore()
        }
        // The governor's fan: two blades turning about a vertical axle.
        val fw = 9 * s
        val fh = 16 * s
        c.drawLine(g.fanX, g.fanY - fh - 4, g.fanX, g.fanY + fh + 4, gx.stroke("#C9A050", 1.6f))
        for (b in 0 until 2) {
            val ang = p.fan + b * PI
            val cw = cos(ang)
            val half = abs(fw * sin(ang)).toFloat()
            val dir = if (cw >= 0) 1 else -1
            if (half > 0.3f) {
                // A blade's gradient runs across its visible width, so it is made per frame (two small ones).
                val blade = gx.shaded(LinearGradient(g.fanX, 0f, g.fanX + dir * half * 2, 0f, bladeColors, null, Shader.TileMode.CLAMP))
                val left = if (dir > 0) g.fanX else g.fanX - half * 2
                c.drawRect(left, g.fanY - fh / 2, left + half * 2, g.fanY + fh / 2, blade)
            }
        }
        // The crank: arm and ivory knob turning about the hub.
        val ca = p.crank
        val kx = g.hubX + cos(ca).toFloat() * g.arm
        val ky = g.hubY + sin(ca).toFloat() * g.arm
        c.drawLine(g.hubX + 2, g.hubY + 3, kx + 2, ky + 3, gx.stroke("#0A0408", 7 * s, 0.35))
        c.drawLine(g.hubX, g.hubY, kx, ky, gx.stroke("#D6AA55", 6 * s))
        c.drawLine(g.hubX, g.hubY, kx, ky, gx.stroke("#FFF0C4", 1.6f * s, 0.6))
        c.drawCircle(kx + 2, ky + 3, 10 * s, gx.blurred("#0A0408", 0.45, 3f))
        c.save()
        c.translate(kx, ky)
        c.drawCircle(0f, 0f, 9.5f * s, gx.shaded(knob(s)))
        c.restore()
        c.drawCircle(g.hubX, g.hubY, 5 * s, gx.paint("#6E4D1A"))
        c.drawCircle(g.hubX - 1, g.hubY - 1, 2 * s, gx.paint("#FFF0C4", 0.8))
        // Playhead.
        val prog = min(1.0, max(0.0, p.progress))
        val px = g.barX + g.barW * prog.toFloat()
        c.drawRoundRect(g.barX, g.barY - 2, g.barX + max(4f, px - g.barX), g.barY + 2, 2f, 2f, gx.shaded(barShader))
        c.drawCircle(px, g.barY, 7f, gx.blurred("#000000", 0.35, 2f))
        c.drawCircle(px, g.barY, 6f, gx.paint("#FFF6EA"))
        val sec = floor(prog * MUSICBOX_SONG_SECONDS + 1e-4).toInt()
        c.drawText(LABELS[sec.coerceIn(0, MUSICBOX_SONG_SECONDS)], g.barX, g.barY + 22, time)
    }

    private var knobShader: RadialGradient? = null
    private fun knob(s: Float): RadialGradient = knobShader ?: RadialGradient(-3f, -4f, 13 * s, MusicBoxGfx.colors(arrayOf("#FFFBF0", "#EADDC4", "#B9A27E")), null, Shader.TileMode.CLAMP).also { knobShader = it }

    private companion object {
        /** "0:00" … "0:52", made once so the playhead's label never allocates. */
        val LABELS = Array(MUSICBOX_SONG_SECONDS + 1) { "0:${if (it < 10) "0" else ""}$it" }
    }
}
