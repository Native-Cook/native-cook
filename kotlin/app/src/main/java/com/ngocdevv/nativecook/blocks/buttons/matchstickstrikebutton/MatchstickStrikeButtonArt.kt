package com.ngocdevv.nativecook.blocks.buttons.matchstickstrikebutton

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.blocks.buttons.matchstickstrikebutton.MatchstickMotion.clamp01
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 Matchstick Strike Button — the drawing, ported from matchstickArt.ts. One canvas: a baked table
 (walnut planks, the product card with an amber candle jar) and a baked matchbox (oxblood label,
 sandpaper striker, printing), with the match, the CTA's letters, sparks, the warm light and the
 fire shader drawn over them every frame. A match going back into the box is drawn under the box
 image. Below API 33 the fire is drawn with gradients (MatchFallback).
*/

internal const val MATCH_CTA = "Strike to start"
private val FLAME_GOLD = doubleArrayOf(1.0, 0.77, 0.42)
private val FLASH = doubleArrayOf(1.0, 0.96, 0.84)
private val PRINT = doubleArrayOf(0.23, 0.075, 0.06)

internal class MatchFonts {
    val cta = MatchstickStrikeButtonGfx.font(Typeface.DEFAULT, 600, 31f)
    val caps = MatchstickStrikeButtonGfx.font(Typeface.DEFAULT, 700, 9f)
    val small = MatchstickStrikeButtonGfx.font(Typeface.DEFAULT, 400, 12f)
    val jar = MatchstickStrikeButtonGfx.font(Typeface.SERIF, 700, 7.5f)
}

internal class MatchScene(val w: Float, val h: Float, top: Float, bottom: Float, fonts: MatchFonts) {
    val cardX = 16f
    val cardY = top + 112
    val cardW = w - 32
    val cardH = min(142f, max(112f, h * 0.165f)).roundToInt().toFloat()
    val boxX = 20f
    val boxW = w - 40
    val boxH = min(196f, max(160f, h * 0.23f)).roundToInt().toFloat()
    val boxY = h - bottom - 92 - boxH
    /** Letter left edges (the glyphs are drawn one by one so each can catch on its own). */
    val letterX = FloatArray(MATCH_CTA.length)
    val geo: MatchGeo
    /** Where the box image sits (it only covers what it draws). */
    val boxRect = RectF(boxX - 60, boxY - 20, boxX - 60 + boxW + 120, boxY - 20 + boxH + 110)

    init {
        val strikerTop = boxY + 14
        val strikerBottom = boxY + 40
        val strikerLeft = boxX + 18
        val strikerRight = boxX + boxW - 18
        val baseline = boxY + (boxH * 0.6f).roundToInt()
        val widths = FloatArray(MATCH_CTA.length)
        fonts.cta.getTextWidths(MATCH_CTA, widths)
        var x = w / 2 - widths.sum() / 2
        val letters = DoubleArray(widths.size)
        for (i in widths.indices) {
            letterX[i] = x
            letters[i] = (x + widths[i] / 2).toDouble()
            x += widths[i]
        }
        val midStrip = (strikerTop + strikerBottom) / 2.0
        geo = MatchGeo(
            restX = boxX + 34.0, restY = boxY - 32.0,
            strikeFromX = strikerRight - 20.0, strikeFromY = midStrip,
            strikeToX = strikerLeft + 36.0, strikeToY = midStrip,
            glideFromX = letterX[0] - 12.0, glideFromY = baseline + 16.0,
            glideToX = x + 12.0, glideToY = baseline + 16.0,
            strikerTop = strikerTop.toDouble(), strikerBottom = strikerBottom.toDouble(),
            strikerLeft = strikerLeft.toDouble(), strikerRight = strikerRight.toDouble(),
            boxTop = boxY.toDouble(),
            letters = letters,
            baseline = baseline.toDouble(),
            matchLength = min(270f, w * 0.68f).toDouble(),
        )
    }
}

internal class MatchLayers(val table: Bitmap, val box: Bitmap)

internal object MatchstickStrikeButtonArt {
    private fun f(v: Double) = v.toFloat()

    fun bakeLayers(s: MatchScene, fonts: MatchFonts, scale: Float): MatchLayers {
        val g = MatchstickStrikeButtonGfx().apply { liveBlur = true }
        val gp = Paint()
        val fine = MatchstickStrikeButtonGrain(128, 128, 1.1, 1.1, 4)
        val plank = MatchstickStrikeButtonGrain(498, 129, 166.0, 3.0, 9)
        val label = MatchstickStrikeButtonGrain(128, 128, 0.77, 0.77, 21)
        val grit = MatchstickStrikeButtonGrain(96, 96, 0.42, 0.42, 13)
        val speckle = MatchstickStrikeButtonGrain(96, 96, 1.1, 1.1, 17)
        val table = MatchstickStrikeButtonGfx.bake(s.w, s.h, scale) { c -> table(c, s, fonts, g, gp, fine, plank) }
        val r = s.boxRect
        val box = MatchstickStrikeButtonGfx.bake(r.width(), r.height(), scale, r.left, r.top) { c -> box(c, s, fonts, g, gp, label, grit, speckle) }
        return MatchLayers(table, box)
    }

    /** Walnut table, the product card and its candle jar. */
    private fun table(
        c: Canvas, s: MatchScene, fonts: MatchFonts, g: MatchstickStrikeButtonGfx, gp: Paint,
        fine: MatchstickStrikeButtonGrain, plank: MatchstickStrikeButtonGrain,
    ) {
        val w = s.w
        val h = s.h
        val full = RectF(0f, 0f, w, h)
        c.drawRect(full, g.shaded(g.linear(0f, 0f, 0f, h, intArrayOf(matchRgb(0x21160F), matchRgb(0x1A110C), matchRgb(0x150E0A), matchRgb(0x100B08)), floatArrayOf(0f, 0.4f, 0.75f, 1f))))
        // Long grain along the planks, and the seams between them.
        plank.draw(c, full, 0.22, gp)
        fine.draw(c, full, 0.06, gp)
        var y = 96f
        while (y < h) {
            c.drawLine(0f, y, w, y, g.paint(matchRgb(0x070403), 0.55))
            c.drawLine(0f, y + 1, w, y + 1, g.paint(matchRgb(0xFFD9B0), 0.05))
            y += 118
        }
        c.drawRect(full, g.shaded(g.radial(w * 0.5f, h * 0.55f, h * 0.72f, intArrayOf(0, matchRgba(5, 3, 2, 0.25), matchRgba(5, 3, 2, 0.68)), floatArrayOf(0.35f, 0.72f, 1f))))

        // Card: two shadows, a warm three-stop body, hairline highlight.
        val r = 22f
        g.rr(c, s.cardX, s.cardY + 14, s.cardW, s.cardH, r, g.blurred(0xFF000000.toInt(), 0.5, 16f))
        g.rr(c, s.cardX, s.cardY + 1.5f, s.cardW, s.cardH, r, g.blurred(0xFF000000.toInt(), 0.55, 1.5f))
        g.rr(c, s.cardX, s.cardY, s.cardW, s.cardH, r, g.shaded(g.linear(0f, s.cardY, 0f, s.cardY + s.cardH, intArrayOf(matchRgb(0x3A2A20), matchRgb(0x2D2119), matchRgb(0x261B15)))))
        c.save()
        val cardRect = RectF(s.cardX, s.cardY, s.cardX + s.cardW, s.cardY + s.cardH)
        c.clipPath(Path().apply { addRoundRect(cardRect, r, r, Path.Direction.CW) })
        fine.draw(c, cardRect, 0.08, gp)
        c.restore()
        g.rr(c, s.cardX + 0.5f, s.cardY + 0.5f, s.cardW - 1, s.cardH - 1, r - 0.5f, g.stroke(g.paint(matchRgb(0xFFE2C2), 0.12), 1f))

        // The candle jar: amber glass, cream wax with three wicks, a printed paper band.
        val jh = s.cardH - 32
        val jw = (jh * 0.8f).roundToInt().toFloat()
        val jx = s.cardX + 18
        val jy = s.cardY + 16
        c.drawOval(RectF(jx - 4, jy + jh - 8, jx + jw + 4, jy + jh + 6), g.blurred(0xFF000000.toInt(), 0.55, 5f))
        g.rr(c, jx, jy + 6, jw, jh - 6, 12f, g.shaded(g.linear(jx, 0f, jx + jw, 0f, intArrayOf(matchRgb(0x4A240C), matchRgb(0x9C5A22), matchRgb(0xC98A45), matchRgb(0x7A4316), matchRgb(0x3B1C09)), floatArrayOf(0f, 0.28f, 0.42f, 0.75f, 1f))))
        c.drawOval(RectF(jx + 4, jy + 2, jx + jw - 4, jy + 16), g.shaded(g.linear(0f, jy, 0f, jy + 14, intArrayOf(matchRgb(0xF4E6CC), matchRgb(0xE2CDA8), matchRgb(0xC9B08A)))))
        val wick = g.stroke(g.paint(matchRgb(0x2A1B12)), 1.6f, round = true)
        for (i in -1..1) c.drawLine(jx + jw / 2 + i * jw * 0.2f, jy + 9, jx + jw / 2 + i * jw * 0.2f + 1, jy + 3, wick)
        val by = jy + jh * 0.42f
        c.drawRect(jx + 1, by, jx + jw - 1, by + jh * 0.3f, g.shaded(g.linear(jx, 0f, jx + jw, 0f, intArrayOf(matchRgb(0xCDB894), matchRgb(0xEBDDC2), matchRgb(0xD9C6A3), matchRgb(0xB29C78)), floatArrayOf(0f, 0.35f, 0.7f, 1f))))
        val label = "FIG & CEDAR"
        fonts.jar.color = matchRgba(0x4A, 0x2A, 0x18, 0.9)
        c.drawText(label, jx + jw / 2 - fonts.jar.measureText(label) / 2, by + jh * 0.15f + 3, fonts.jar)
        c.drawLine(jx + 10, by + jh * 0.24f, jx + jw - 10, by + jh * 0.24f, g.paint(matchRgb(0x4A2A18), 0.4))
        // Glass highlights: a long specular strip and a rim.
        g.rr(c, jx + jw * 0.16f, jy + 12, jw * 0.1f, jh - 26, 3f, g.shaded(g.linear(0f, jy + 10, 0f, jy + jh, intArrayOf(matchRgba(255, 236, 206, 0.42), matchRgba(255, 236, 206, 0.1), matchRgba(255, 236, 206, 0.0)))))
        g.rr(c, jx + 0.5f, jy + 6.5f, jw - 1, jh - 7, 11.5f, g.stroke(g.paint(matchRgb(0xFFE0B0), 0.3), 1f))
    }

    /** The matchbox, transparent around it, so a match can pass behind it. */
    private fun box(
        c: Canvas, s: MatchScene, fonts: MatchFonts, g: MatchstickStrikeButtonGfx, gp: Paint,
        labelGrain: MatchstickStrikeButtonGrain, grit: MatchstickStrikeButtonGrain, speckle: MatchstickStrikeButtonGrain,
    ) {
        val x = s.boxX
        val y = s.boxY
        val w = s.boxW
        val h = s.boxH
        val geo = s.geo
        // Shadows on the table, then the drawer's edge (the box's thickness) under the label.
        g.rr(c, x, y + 22, w, h, 10f, g.blurred(0xFF000000.toInt(), 0.55, 20f))
        g.rr(c, x, y + 4, w, h + 10, 8f, g.blurred(0xFF000000.toInt(), 0.6, 2f))
        g.rr(c, x, y + h - 12, w, 24f, 8f, g.shaded(g.linear(0f, y + h - 10, 0f, y + h + 12, intArrayOf(matchRgb(0x5A2019), matchRgb(0x3E140F), matchRgb(0x2A0D0A)))))
        // The label: oxblood paper with grain and a soft sheen from the upper left.
        val faceRect = RectF(x, y, x + w, y + h)
        g.rr(c, x, y, w, h, 8f, g.shaded(g.linear(x, y, x + w * 0.4f, y + h, intArrayOf(matchRgb(0x8A3328), matchRgb(0x74281F), matchRgb(0x621F18), matchRgb(0x561A14)), floatArrayOf(0f, 0.35f, 0.7f, 1f))))
        c.save()
        c.clipPath(Path().apply { addRoundRect(faceRect, 8f, 8f, Path.Direction.CW) })
        labelGrain.draw(c, faceRect, 0.12, gp)
        c.drawRect(faceRect, g.shaded(g.radial(x + w * 0.2f, y, w * 0.9f, intArrayOf(matchRgba(255, 214, 180, 0.14), matchRgba(255, 214, 180, 0.0)))))
        c.restore()
        c.drawLine(x + 8, y + 0.5f, x + w - 8, y + 0.5f, g.paint(matchRgb(0xFFC9B0), 0.3))
        // A cream double border printed inside the label.
        g.rr(c, x + 8, y + 52, w - 16, h - 62, 5f, g.stroke(g.paint(matchRgb(0xF1DEC2), 0.55), 1.2f))
        g.rr(c, x + 12, y + 56, w - 24, h - 70, 3f, g.stroke(g.paint(matchRgb(0xF1DEC2), 0.55), 0.6f))
        // Sandpaper striker: red-brown grit with dark speckle, a pressed edge.
        val sx = f(geo.strikerLeft)
        val sw = f(geo.strikerRight - geo.strikerLeft)
        val st = f(geo.strikerTop)
        val sb = f(geo.strikerBottom)
        val strip = RectF(sx, st, sx + sw, sb)
        c.drawRoundRect(strip, 4f, 4f, g.shaded(g.linear(0f, st, 0f, sb, intArrayOf(matchRgb(0x5E3222), matchRgb(0x4A2518), matchRgb(0x3A1C12)))))
        c.save()
        c.clipPath(Path().apply { addRoundRect(strip, 4f, 4f, Path.Direction.CW) })
        grit.draw(c, strip, 0.55, gp)
        speckle.draw(c, strip, 0.35, gp, MatchstickStrikeButtonGfx.multiply)
        c.restore()
        c.drawRoundRect(strip, 4f, 4f, g.stroke(g.paint(matchRgb(0x1A0905), 0.6), 1f))
        c.drawLine(sx + 4, sb + 0.8f, sx + sw - 4, sb + 0.8f, g.paint(matchRgb(0xFFC9B0), 0.18))
        // Printing: the brand line in caps, the hint and the count.
        val caps = "HEARTH · SAFETY MATCHES"
        fonts.caps.color = matchRgba(0xF1, 0xDE, 0xC2, 0.7)
        c.drawText(caps, x + w / 2 - fonts.caps.measureText(caps) / 2, y + 72, fonts.caps)
        val hint = "Swipe the match across the strip"
        fonts.small.color = matchRgba(0xF1, 0xDE, 0xC2, 0.55)
        c.drawText(hint, x + w / 2 - fonts.small.measureText(hint) / 2, f(geo.baseline) + 30, fonts.small)
        val count = "No. 7  ·  40 long matches"
        fonts.caps.color = matchRgba(0xF1, 0xDE, 0xC2, 0.45)
        c.drawText(count, x + w / 2 - fonts.caps.measureText(count) / 2, y + h - 22, fonts.caps)
    }
}

/** Draws the moving parts each frame; holds its reusable paints, paths and the fire shader. */
internal class MatchPainter(private val fonts: MatchFonts) {
    private val g = MatchstickStrikeButtonGfx()
    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()
    private val dst = RectF()
    private val young = Path()
    private val old = Path()
    private val letter = CharArray(1)
    private val ctaPaint = Paint(fonts.cta)
    private val fire: RuntimeShader? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) matchFireShader() else null
    private val firePaint = Paint()
    private val fallback = MatchFallback()
    private val spare = MatchState()

    private fun f(v: Double) = v.toFloat()

    fun frame(c: Canvas, s: MatchScene, layers: MatchLayers, pose: MatchPose) {
        val len = f(s.geo.matchLength)
        src.set(0, 0, layers.table.width, layers.table.height)
        dst.set(0f, 0f, s.w, s.h)
        c.drawBitmap(layers.table, src, dst, imagePaint)
        // A match on its way into (or out of) the box is under the box.
        if (pose.match.front < 0.5) {
            c.save()
            c.clipRect(0f, 0f, s.w, s.boxY + s.boxH - 12)
            match(c, pose.match, len, pose.lit)
            c.restore()
        }
        src.set(0, 0, layers.box.width, layers.box.height)
        c.drawBitmap(layers.box, src, s.boxRect, imagePaint)
        // A scrape on the strip: darker, polished grit.
        if (pose.scrape[2] > 0.005) {
            val x0 = f(min(pose.scrape[0], pose.scrape[1]))
            val x1 = f(max(pose.scrape[0], pose.scrape[1]))
            val my = f((s.geo.strikerTop + s.geo.strikerBottom) / 2)
            g.rr(c, x0, my - 3, max(4f, x1 - x0), 6f, 3f, g.blurred(matchRgb(0x140805), pose.scrape[2], 2f))
        }
        letters(c, s, pose.heat)
        if (pose.spare > 0.005) {
            spare.x = s.geo.restX
            spare.y = s.geo.restY
            spare.alpha = pose.spare
            match(c, spare, len, 0.0)
        }
        if (pose.match.front >= 0.5) match(c, pose.match, len, pose.lit)
        // Warm light from the flame over everything it reaches.
        val bx = f(pose.match.x)
        val by = f(pose.match.y - 6)
        if (pose.light > 0.01) {
            val light = g.shaded(g.radial(bx, by - 24, 360f, intArrayOf(matchRgba(255, 150, 70, 0.42), matchRgba(255, 120, 50, 0.16), matchRgba(255, 110, 40, 0.0)), floatArrayOf(0f, 0.4f, 1f)), clamp01(pose.light))
            light.xfermode = MatchstickStrikeButtonGfx.screen
            c.drawRect(dst, light)
        }
        sparks(c, pose.sparks)
        if (pose.lit > 0.005 || pose.flare > 0.005 || pose.puff[3] > 0.01 || pose.curl[3] > 0.01) {
            val sh = fire
            if (sh != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                sh.setFloatUniform("flame", bx, by, f(pose.lit), f(pose.flare))
                sh.setFloatUniform("shape", f(pose.lean), f(pose.phase * 7), f(pose.phase * 11), 0f)
                sh.setFloatUniform("puff", f(pose.puff[0]), f(pose.puff[1]), f(pose.puff[2]), f(pose.puff[3]))
                sh.setFloatUniform("curl", f(pose.curl[0]), f(pose.curl[1]), f(pose.curl[2]), f(pose.curl[3]))
                firePaint.shader = sh
                c.drawRect(fireBounds(pose, bx, by), firePaint)
            } else {
                fallback.draw(c, g, pose, bx, by)
            }
        }
    }

    /** The rectangle the fire shader needs this frame (flame and both smoke columns). */
    private val bounds = RectF()

    private fun fireBounds(pose: MatchPose, bx: Float, by: Float): RectF {
        var l = bx - 90
        var r = bx + 90
        var t = by - 130
        var b = by + 40
        for (sm in arrayOf(pose.puff, pose.curl)) {
            if (sm[3] > 0.01 && sm[2] >= 0) {
                l = min(l, f(sm[0]) - 110)
                r = max(r, f(sm[0]) + 110)
                t = min(t, f(sm[1]) - 330)
                b = max(b, f(sm[1]) + 10)
            }
        }
        bounds.set(l, t, r, b)
        return bounds
    }

    private val bodyRect = RectF()

    /** One match in its own frame: head at the origin, the stick along +x. */
    private fun matchBody(c: Canvas, m: MatchState, len: Float, lit: Double) {
        g.rr(c, 2f, -3.6f, len, 7.2f, 2.5f, g.shaded(g.linear(0f, -3.6f, 0f, 3.6f, intArrayOf(matchRgb(0xF3E0B6), matchRgb(0xDEC08A), matchRgb(0xB98F58)))))
        val fibre = g.paint(matchRgb(0x9C7444), 0.22).apply { strokeWidth = 0.6f }
        c.drawLine(14f, -1.2f, len - 10, -1.4f, fibre)
        c.drawLine(30f, 1.6f, len - 30, 1.3f, fibre)
        // Char creeping down from the head, with a glowing edge while it burns.
        if (m.burnt > 0.01) {
            val cl = f(6 + 28 * m.char)
            c.drawRect(2f, -3.7f, 2 + cl, 3.7f, g.shaded(g.linear(cl - 8, 0f, cl, 0f, intArrayOf(matchRgb(0x241710), matchRgba(36, 23, 16, 0.0))), clamp01(m.burnt)))
            if (lit > 0.02) c.drawRect(cl - 4, -3.6f, cl - 1, 3.6f, g.blurred(matchRgb(0xFF8A3A), 0.7 * lit, 1.5f))
        }
        // The head: a red bulb going to charcoal when struck, with a highlight.
        bodyRect.set(-9f, -6.2f, 9f, 6.2f)
        c.drawOval(bodyRect, g.shaded(g.radial(-3f, -2.5f, 11f, intArrayOf(matchRgb(0xD2564A), matchRgb(0xA3291F), matchRgb(0x6E1712)))))
        if (m.burnt > 0.01) {
            bodyRect.set(-9.5f, -6.6f, 9.5f, 6.6f)
            c.drawOval(bodyRect, g.shaded(g.radial(-2f, -2f, 10f, intArrayOf(matchRgb(0x4A3328), matchRgb(0x261A14), matchRgb(0x140E0B))), clamp01(m.burnt)))
        }
        bodyRect.set(-5.5f, -4.6f, 0.5f, -1.6f)
        c.drawOval(bodyRect, g.paint(matchRgb(0xFFE6D6), 0.42 * (1 - clamp01(m.burnt))))
    }

    private fun match(c: Canvas, m: MatchState, len: Float, lit: Double) {
        if (m.alpha < 0.005) return
        val layered = m.alpha < 0.999
        if (layered) c.saveLayerAlpha(null, (255 * m.alpha).roundToInt())
        // The shadow falls down-screen; held up, it is further, larger and softer.
        c.save()
        c.translate(f(m.x + 2 + 8 * m.lift), f(m.y + 4 + 18 * m.lift))
        c.rotate(f(m.angle))
        g.rr(c, -8f, -5f, len + 10, 10f, 5f, g.blurred(0xFF000000.toInt(), 0.5 - 0.18 * m.lift, f(1.8 + 5 * m.lift)))
        c.restore()
        c.save()
        c.translate(f(m.x), f(m.y))
        c.rotate(f(m.angle))
        val sc = f(1 + 0.05 * m.lift)
        c.scale(sc, sc)
        matchBody(c, m, len, lit)
        c.restore()
        if (layered) c.restore()
    }

    private fun sparks(c: Canvas, sp: MatchSparks) {
        if (sp.count == 0) return
        young.rewind()
        old.rewind()
        val v = sp.v
        for (i in 0 until sp.count) {
            val o = i * 6
            val x0 = v[o]
            val y0 = v[o + 1]
            val vx = v[o + 2]
            val vy = v[o + 3]
            val a = v[o + 4]
            val life = v[o + 5]
            val a0 = max(0.0, a - 0.03)
            val b = if (a / life < 0.5) young else old
            b.moveTo(f(x0 + vx * a0), f(y0 + vy * a0 + 450 * a0 * a0))
            b.lineTo(f(x0 + vx * a), f(y0 + vy * a + 450 * a * a))
        }
        for (k in 0..1) {
            val path = if (k == 0) young else old
            val color = if (k == 0) matchRgb(0xFFE7A8) else matchRgb(0xFF9A45)
            val width = if (k == 0) 1.6f else 1.2f
            val glow = g.stroke(g.blurred(color, 0.55, 3f), width * 3.5f, round = true)
            glow.xfermode = MatchstickStrikeButtonGfx.plus
            c.drawPath(path, glow)
            val core = g.stroke(g.paint(color), width, round = true)
            core.xfermode = MatchstickStrikeButtonGfx.plus
            c.drawPath(path, core)
        }
    }

    private fun letters(c: Canvas, s: MatchScene, heat: DoubleArray) {
        val y = f(s.geo.baseline)
        val font = ctaPaint
        for (i in MATCH_CTA.indices) {
            val ch = MATCH_CTA[i]
            if (ch == ' ') continue
            letter[0] = ch
            val x = s.letterX[i]
            val h = heat.getOrElse(i) { 0.0 }
            // Printed (debossed): a light lower lip, then the dark ink.
            font.color = matchRgba(0xFF, 0xC7, 0xB0, 0.16 * (1 - clamp01(h)))
            c.drawText(letter, 0, 1, x, y + 0.9f, font)
            if (h > 0.01 && g.liveBlur) {
                font.color = matchRgbf(1.0, 0.5, 0.18, 0.85 * min(1.2, h))
                font.maskFilter = g.blur(8f)
                font.xfermode = MatchstickStrikeButtonGfx.screen
                c.drawText(letter, 0, 1, x, y, font)
                font.maskFilter = null
                font.xfermode = null
            }
            val k = clamp01(h)
            val fl = clamp01((h - 1) / 0.6)
            font.color = matchRgbf(
                PRINT[0] + (FLAME_GOLD[0] - PRINT[0]) * k + (FLASH[0] - FLAME_GOLD[0]) * fl,
                PRINT[1] + (FLAME_GOLD[1] - PRINT[1]) * k + (FLASH[1] - FLAME_GOLD[1]) * fl,
                PRINT[2] + (FLAME_GOLD[2] - PRINT[2]) * k + (FLASH[2] - FLAME_GOLD[2]) * fl,
            )
            c.drawText(letter, 0, 1, x, y, font)
        }
    }
}

/**
 * The fire below API 33, with gradients instead of the shader: smoke as a column of soft grey
 * blobs, the flame as a teardrop path (amber body, a white core, a blue base) leaning with the
 * motion, a flicker in its height, and the fireball and bloom as radial gradients.
 */
internal class MatchFallback {
    private val path = Path()
    private val core = Path()

    fun draw(c: Canvas, g: MatchstickStrikeButtonGfx, pose: MatchPose, bx: Float, by: Float) {
        smoke(c, g, pose.puff)
        smoke(c, g, pose.curl)
        val lit = pose.lit
        val lean = pose.lean.toFloat()
        if (lit > 0.01) {
            val hf = (58 * lit * (1 + 0.06 * kotlin.math.sin(pose.phase * 7 * 5) + 0.04 * kotlin.math.sin(pose.phase * 11 * 9))).toFloat()
            val wf = (9.5 * kotlin.math.sqrt(lit)).toFloat()
            val base = by + 4
            teardrop(path, bx, base, wf, hf, lean)
            val a = clamp01(lit * 1.4)
            c.drawPath(path, g.shaded(g.linear(0f, base, 0f, base - hf, intArrayOf(matchRgbf(0.32, 0.46, 1.0), matchRgbf(1.0, 0.85, 0.48), matchRgbf(1.0, 0.6, 0.22), matchRgbf(1.0, 0.42, 0.13, 0.0)), floatArrayOf(0f, 0.18f, 0.7f, 1f)), a))
            teardrop(core, bx, base - hf * 0.08f, wf * 0.45f, hf * 0.6f, lean * 0.7f)
            c.drawPath(core, g.paint(matchRgbf(1.0, 0.97, 0.88), 0.85 * a))
        }
        // Glow round the flame and the fireball's bloom (added light).
        val gx = bx + lean * 6
        val gy = (by - 14 * lit).toFloat()
        if (lit > 0.01) {
            val p = g.shaded(g.radial(gx, gy, 60f, intArrayOf(matchRgbf(1.0, 0.62, 0.28, 0.42 * lit), matchRgbf(1.0, 0.62, 0.28, 0.0))))
            p.xfermode = MatchstickStrikeButtonGfx.plus
            c.drawCircle(gx, gy, 60f, p)
        }
        val flare = pose.flare
        if (flare > 0.01) {
            val fy = (by - 6 * flare).toFloat()
            val p = g.shaded(g.radial(bx, fy, 70f, intArrayOf(matchRgbf(1.0, 0.72, 0.38, 0.95 * flare), matchRgbf(1.0, 0.72, 0.38, 0.0))))
            p.xfermode = MatchstickStrikeButtonGfx.plus
            c.drawCircle(bx, fy, 70f, p)
            val r = (18 * flare + 2).toFloat()
            c.drawCircle(bx, fy, r, g.shaded(g.radial(bx, fy, r, intArrayOf(matchRgbf(1.0, 0.98, 0.9), matchRgbf(1.0, 0.7, 0.3), matchRgbf(1.0, 0.7, 0.3, 0.0)), floatArrayOf(0f, 0.6f, 1f)), flare))
        }
    }

    /** A teardrop of width 2w and height h standing on (x, base), its tip pushed by lean. */
    private fun teardrop(p: Path, x: Float, base: Float, w: Float, h: Float, lean: Float) {
        val tipX = x + lean * 30
        p.rewind()
        p.moveTo(tipX, base - h)
        p.cubicTo(x + lean * 12 + w * 0.6f, base - h * 0.55f, x + w * 1.05f, base - w * 0.9f, x, base + w * 0.25f)
        p.cubicTo(x - w * 1.05f, base - w * 0.9f, x + lean * 12 - w * 0.6f, base - h * 0.55f, tipX, base - h)
        p.close()
    }

    private fun smoke(c: Canvas, g: MatchstickStrikeButtonGfx, s: MatchQuad) {
        if (s[3] < 0.01 || s[2] < 0) return
        val reach = s[2] * 78
        var sy = 4.0
        while (sy < reach + 30 && sy < 300) {
            val sway = kotlin.math.sin(sy * 0.05 - s[2] * 2.4) * (3 + sy * 0.13)
            val w = 2.5 + sy * 0.1
            val fade = MatchstickMotion.seg(sy, -4.0, 8.0) * (1 - MatchstickMotion.seg(sy, reach - 16, reach + 40)) * (1 - MatchstickMotion.seg(sy, 60.0, 300.0))
            val a = (0.5 * s[3] * fade * 0.5).coerceIn(0.0, 0.8)
            val x = (s[0] + sway).toFloat()
            val y = (s[1] - sy).toFloat()
            val r = (w * 2).toFloat()
            c.drawCircle(x, y, r, g.shaded(g.radial(x, y, r, intArrayOf(matchRgbf(0.66, 0.63, 0.6, a), matchRgbf(0.66, 0.63, 0.6, 0.0)))))
            sy += max(4.0, w * 0.9)
        }
    }
}
