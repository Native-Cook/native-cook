package com.ngocdevv.nativecook.blocks.navigation.compassneedlenav

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Typeface
import com.ngocdevv.nativecook.blocks.navigation.compassneedlenav.CompassMotion.clamp01
import com.ngocdevv.nativecook.blocks.navigation.compassneedlenav.CompassMotion.wrap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Compass Needle Nav — the drawing, ported from compassArt.ts. One canvas: a baked background
 (night-green ground, the dock plate), four baked pages (Map, Trips, Gear, Me) that hang on a
 wheel centred on the compass and are drawn rotated each frame, and the compass: a baked base
 (housing, bezel with degrees, the liquid well and its card), the needle drawn each frame with its
 shadow, an air bubble, and a baked glass dome over it. Everything is in dp.
*/

internal val CompassTabs = arrayOf("Map", "Trips", "Gear", "Me")
private val ACCENT = compassRgb(0xF07A3A)
private val INK = compassRgb(0xEEF1E8)
private val MUTE = compassRgba(238, 241, 232, 0.62)
private val FAINT = compassRgba(238, 241, 232, 0.38)
private val CARD = intArrayOf(compassRgb(0x1E2F28), compassRgb(0x1A2923), compassRgb(0x16231E))

internal class CompassFonts {
    val title = CompassNeedleNavGfx.font(Typeface.DEFAULT, 700, 32f)
    val body = CompassNeedleNavGfx.font(Typeface.DEFAULT, 400, 15f)
    val bodyBold = CompassNeedleNavGfx.font(Typeface.DEFAULT, 600, 16f)
    val small = CompassNeedleNavGfx.font(Typeface.DEFAULT, 400, 13f)
    val tiny = CompassNeedleNavGfx.font(Typeface.DEFAULT, 700, 10f)
    val degree = CompassNeedleNavGfx.font(Typeface.DEFAULT, 600, 8.5f)
    val cardinal = CompassNeedleNavGfx.font(Typeface.DEFAULT, 700, 15f)
    val big = CompassNeedleNavGfx.font(Typeface.DEFAULT, 700, 22f)
}

internal class CompassScene(val w: Float, val h: Float, top: Float, bottom: Float) {
    val r = min(104f, min(w * 0.27f, (h - top - bottom) * 0.13f))
    val cy = h - bottom - 46 - r
    val cx = w / 2
    val pageTop = top + 44
    val pageH = cy - r - 72 - pageTop
    /** The compass images are drawn in a box of this size around (cx, cy). */
    val box = ceil(r * 2 + 48)
    val boxRect = RectF(cx - box / 2, cy - box / 2, cx + box / 2, cy + box / 2)
}

internal class CompassLayers(val ground: Bitmap, val pages: Array<Bitmap>, val compass: Bitmap, val glass: Bitmap)

internal object CompassNeedleNavArt {
    private fun hash(n: Double): Double {
        val v = sin(n * 12.9898 + 4.1414) * 43758.5453
        return v - floor(v)
    }

    private class Ctx(val g: CompassNeedleNavGfx, val fonts: CompassFonts) {
        fun stroke(color: Int, alpha: Double, width: Float) = g.stroke(g.paint(color, alpha), width, round = true)
        fun text(c: Canvas, s: String, x: Float, y: Float, font: Paint, color: Int, alpha: Double = 1.0) {
            font.color = color
            if (alpha < 1) font.alpha = (font.alpha * alpha).roundToInt()
            c.drawText(s, x, y, font)
        }
    }

    fun bakeLayers(s: CompassScene, fonts: CompassFonts, scale: Float): CompassLayers {
        val x = Ctx(CompassNeedleNavGfx().apply { liveBlur = true }, fonts)
        val ground = CompassNeedleNavGfx.bake(s.w, s.h, scale) { c -> ground(c, s, x) }
        val pages = Array(4) { k ->
            CompassNeedleNavGfx.bake(s.w, s.pageH, scale) { c ->
                when (k) {
                    0 -> mapPage(c, s.w, s.pageH, x)
                    1 -> tripsPage(c, s.w, s.pageH, x)
                    2 -> gearPage(c, s.w, s.pageH, x)
                    else -> mePage(c, s.w, s.pageH, x)
                }
            }
        }
        val compass = CompassNeedleNavGfx.bake(s.box, s.box, scale) { c -> compass(c, s, x) }
        val glass = CompassNeedleNavGfx.bake(s.box, s.box, scale) { c -> glass(c, s, x) }
        return CompassLayers(ground, pages, compass, glass)
    }

    /** Ground and the dock plate the compass sits on. */
    private fun ground(c: Canvas, s: CompassScene, x: Ctx) {
        val g = x.g
        val w = s.w
        val h = s.h
        val full = RectF(0f, 0f, w, h)
        c.drawRect(full, g.shaded(g.linear(0f, 0f, 0f, h, intArrayOf(compassRgb(0x13221D), compassRgb(0x0F1C18), compassRgb(0x0C1714), compassRgb(0x0A1310)), floatArrayOf(0f, 0.4f, 0.75f, 1f))))
        c.drawRect(full, g.shaded(g.radial(w * 0.5f, s.pageTop + s.pageH * 0.4f, h * 0.6f, intArrayOf(compassRgba(120, 160, 130, 0.10), 0))))
        CompassNeedleNavGrain(128, 128, 1.1, 1.1, 5).draw(c, full, 0.1, Paint())
        val dockY = s.cy - s.r - 62
        g.rr(c, 0f, dockY - 6, w, h - dockY + 40, 34f, g.blurred(0xFF000000.toInt(), 0.45, 18f))
        g.rr(c, 0f, dockY, w, h - dockY + 40, 34f, g.shaded(g.linear(0f, dockY, 0f, h, intArrayOf(compassRgb(0x1B2B25), compassRgb(0x15231E), compassRgb(0x101B17)))))
        g.rr(c, 0.5f, dockY + 0.5f, w - 1, h - dockY + 40, 34f, x.stroke(compassRgb(0xD8F0E0), 0.1, 1f))
    }

    private fun cardRect(c: Canvas, x: Ctx, cx: Float, y: Float, w: Float, h: Float, r: Float = 20f) {
        val g = x.g
        g.rr(c, cx, y + 8, w, h, r, g.blurred(0xFF000000.toInt(), 0.35, 12f))
        g.rr(c, cx, y, w, h, r, g.shaded(g.linear(0f, y, 0f, y + h, CARD)))
        g.rr(c, cx + 0.5f, y + 0.5f, w - 1, h - 1, r - 0.5f, x.stroke(compassRgb(0xD8F0E0), 0.08, 1f))
    }

    private fun header(c: Canvas, x: Ctx, title: String, sub: String) {
        x.text(c, title, 20f, 34f, x.fonts.title, INK)
        x.text(c, sub, 20f, 58f, x.fonts.body, MUTE)
    }

    private fun mapPage(c: Canvas, W: Float, H: Float, ctx: Ctx) {
        val g = ctx.g
        val f = ctx.fonts
        header(c, ctx, "Map", "Larch Ridge · day 2 of 3")
        val x = 16f
        val y = 74f
        val w = W - 32
        val h = H - y - 8
        cardRect(c, ctx, x, y, w, h, 24f)
        c.save()
        c.clipPath(Path().apply { addRoundRect(RectF(x, y, x + w, y + h), 24f, 24f, Path.Direction.CW) })
        c.drawRect(x, y, x + w, y + h, g.shaded(g.linear(x, y, x + w, y + h, intArrayOf(compassRgb(0x22352C), compassRgb(0x1D2E26), compassRgb(0x192820)))))
        // A tarn in the lower left.
        val lake = Path().apply {
            moveTo(x - 10, y + h * 0.72f)
            cubicTo(x + w * 0.2f, y + h * 0.62f, x + w * 0.34f, y + h * 0.8f, x + w * 0.24f, y + h + 10)
            lineTo(x - 10, y + h + 10)
            close()
        }
        c.drawPath(lake, g.shaded(g.linear(x, y + h * 0.65f, x + w * 0.3f, y + h, intArrayOf(compassRgb(0x2E5966), compassRgb(0x244955), compassRgb(0x1C3A44)))))
        // Contours round two peaks; every fourth line is an index line.
        val peaks = arrayOf(floatArrayOf(x + w * 0.66f, y + h * 0.36f, 1f), floatArrayOf(x + w * 0.28f, y + h * 0.3f, 0.7f))
        for (p in peaks.indices) {
            val px = peaks[p][0]
            val py = peaks[p][1]
            val sz = peaks[p][2]
            for (k in 0 until 11) {
                val b = Path()
                for (i in 0..72) {
                    val a = i / 72.0 * PI * 2
                    val r = (10 + k * 13) * sz + 6 * sin(3 * a + k * 0.5 + p) + 4 * sin(5 * a + k * 0.9) + 3 * sin(2 * a - k * 0.3)
                    val px2 = (px + cos(a) * r * 1.15).toFloat()
                    val py2 = (py + sin(a) * r * 0.85).toFloat()
                    if (i == 0) b.moveTo(px2, py2) else b.lineTo(px2, py2)
                }
                b.close()
                c.drawPath(b, ctx.stroke(compassRgb(0x9CC4A6), if (k % 4 == 0) 0.32 else 0.16, if (k % 4 == 0) 1.2f else 0.8f))
            }
        }
        // The trail: dashed, from the trailhead to the ridge, with a glow.
        val trail = Path().apply {
            moveTo(x + w * 0.12f, y + h * 0.92f)
            cubicTo(x + w * 0.38f, y + h * 0.82f, x + w * 0.3f, y + h * 0.56f, x + w * 0.48f, y + h * 0.5f)
            cubicTo(x + w * 0.62f, y + h * 0.45f, x + w * 0.58f, y + h * 0.3f, x + w * 0.66f, y + h * 0.36f)
        }
        val glow = ctx.stroke(ACCENT, 0.35, 7f)
        glow.maskFilter = g.blur(4f)
        c.drawPath(trail, glow)
        val dash = ctx.stroke(ACCENT, 1.0, 2.6f)
        dash.pathEffect = DashPathEffect(floatArrayOf(7f, 6f), 0f)
        c.drawPath(trail, dash)
        c.drawCircle(x + w * 0.12f, y + h * 0.92f, 6f, g.paint(compassRgb(0xEEF1E8)))
        c.drawCircle(x + w * 0.12f, y + h * 0.92f, 3f, g.paint(ACCENT))
        // You are here: a halo and a dot on the trail.
        c.drawCircle(x + w * 0.48f, y + h * 0.5f, 16f, g.paint(compassRgb(0x7FB8FF), 0.18))
        c.drawCircle(x + w * 0.48f, y + h * 0.5f, 7f, g.paint(compassRgb(0xEEF1E8)))
        c.drawCircle(x + w * 0.48f, y + h * 0.5f, 5f, g.paint(compassRgb(0x3D8BFF)))
        // Summit flag.
        val fx = x + w * 0.66f
        val fy = y + h * 0.36f
        c.drawLine(fx, fy, fx, fy - 18, ctx.stroke(compassRgb(0xEEF1E8), 0.9, 1.4f))
        c.drawPath(Path().apply { moveTo(fx, fy - 18); lineTo(fx + 12, fy - 14); lineTo(fx, fy - 10); close() }, g.paint(ACCENT))
        ctx.text(c, "Larch Peak 2,184 m", fx + 6, fy + 16, f.tiny, INK, 0.8)
        c.restore()
        // Route chip over the map.
        val chipW = min(w - 24, 236f)
        val cy = y + h - 64
        g.rr(c, x + 12, cy + 6, chipW, 52f, 16f, g.blurred(0xFF000000.toInt(), 0.4, 8f))
        g.rr(c, x + 12, cy, chipW, 52f, 16f, g.paint(compassRgb(0x0F1A16), 0.86))
        ctx.text(c, "Larch Ridge Loop", x + 26, cy + 22, f.bodyBold, INK)
        ctx.text(c, "11.4 km  ·  +640 m  ·  4 h 20", x + 26, cy + 40, f.small, MUTE)
    }

    private fun tripsPage(c: Canvas, W: Float, H: Float, ctx: Ctx) {
        val g = ctx.g
        val f = ctx.fonts
        header(c, ctx, "Trips", "3 planned this month")
        val trips = arrayOf(
            Triple("Larch Ridge Loop", "Sat 14 Jun · 11.4 km · +640 m", 1),
            Triple("Cinder Falls", "Sun 22 Jun · 6.2 km · +310 m", 2),
            Triple("Mirror Tarns overnight", "Fri 27 Jun · 18.9 km · +1,020 m", 3),
        )
        val top = 76f
        val gap = 12f
        val ch = min(104f, (H - top - 8 - gap * 2) / 3)
        for (i in trips.indices) {
            val y = top + i * (ch + gap)
            cardRect(c, ctx, 16f, y, W - 32, ch)
            val t = trips[i]
            ctx.text(c, t.first, 32f, y + ch / 2 - 4, f.bodyBold, INK)
            ctx.text(c, t.second, 32f, y + ch / 2 + 16, f.small, MUTE)
            // Elevation profile on the right.
            val px = W - 32 - 84
            val py = y + ch / 2 + 18
            val b = Path()
            b.moveTo(px, py)
            for (k in 0..14) {
                val v = 0.3 + 0.7 * abs(sin(k * 0.45 + t.third) * 0.6 + sin(k * 0.21 + t.third * 2) * 0.4)
                b.lineTo(px + 84f * k / 14, (py - v * 34).toFloat())
            }
            b.lineTo(px + 84, py)
            b.close()
            c.drawPath(b, g.shaded(g.linear(0f, py - 34, 0f, py, intArrayOf(compassRgba(240, 122, 58, 0.55), compassRgba(240, 122, 58, 0.05)))))
            if (i == 0) {
                // A "next" pill after the name, on its line.
                val px0 = 32 + f.bodyBold.measureText(t.first) + 8
                val py0 = y + ch / 2 - 18
                g.rr(c, px0, py0, 42f, 18f, 9f, g.paint(ACCENT, 0.18))
                ctx.text(c, "NEXT", px0 + 21 - f.tiny.measureText("NEXT") / 2, py0 + 12.5f, f.tiny, ACCENT)
            }
        }
    }

    private fun gearPage(c: Canvas, W: Float, H: Float, ctx: Ctx) {
        val g = ctx.g
        val f = ctx.fonts
        header(c, ctx, "Gear", "Pack weight 7.4 of 9.0 kg")
        val y0 = 78f
        g.rr(c, 20f, y0, W - 40, 8f, 4f, g.paint(compassRgb(0xEEF1E8), 0.1))
        g.rr(c, 20f, y0, (W - 40) * 0.82f, 8f, 4f, g.shaded(g.linear(20f, 0f, W - 20, 0f, intArrayOf(compassRgb(0xF5A15E), ACCENT, compassRgb(0xD85A24)))))
        val items = arrayOf(
            Triple("Rain shell", "320 g", true),
            Triple("Headlamp", "86 g", true),
            Triple("Water filter", "140 g", false),
            Triple("First-aid kit", "210 g", true),
            Triple("Trekking poles", "480 g", false),
            Triple("Down jacket", "390 g", true),
        )
        val top = y0 + 24
        cardRect(c, ctx, 16f, top, W - 32, H - top - 8)
        val rowH = min(52f, (H - top - 16) / items.size)
        for (i in items.indices) {
            val (name, weight, done) = items[i]
            val y = top + 8 + i * rowH
            val cyy = y + rowH / 2
            if (done) {
                c.drawCircle(36f, cyy, 10f, g.paint(ACCENT))
                c.drawPath(Path().apply { moveTo(31.5f, cyy); lineTo(35f, cyy + 3.5f); lineTo(41f, cyy - 3.5f) }, ctx.stroke(compassRgb(0x14211C), 1.0, 2f))
            } else {
                c.drawCircle(36f, cyy, 9.5f, ctx.stroke(compassRgb(0xEEF1E8), 0.35, 1.5f))
            }
            ctx.text(c, name, 58f, cyy + 5, f.body, if (done) INK else MUTE)
            ctx.text(c, weight, W - 32 - f.small.measureText(weight), cyy + 5, f.small, FAINT)
            if (i < items.size - 1) c.drawLine(58f, y + rowH, W - 32, y + rowH, ctx.stroke(compassRgb(0xEEF1E8), 0.07, 1f))
        }
    }

    private fun mePage(c: Canvas, W: Float, H: Float, ctx: Ctx) {
        val g = ctx.g
        val f = ctx.fonts
        header(c, ctx, "Me", "Joined 2023 · Cascade Club")
        val top = 76f
        cardRect(c, ctx, 16f, top, W - 32, 92f)
        c.drawCircle(56f, top + 46, 26f, g.shaded(g.linear(30f, top + 20, 82f, top + 72, intArrayOf(compassRgb(0xF5A15E), ACCENT, compassRgb(0x9C3A16)))))
        ctx.text(c, "AK", 56 - f.bodyBold.measureText("AK") / 2, top + 52, f.bodyBold, compassRgb(0xFFF4EA))
        ctx.text(c, "Ari Kestrel", 96f, top + 42, f.bodyBold, INK)
        ctx.text(c, "Next summit: Larch Peak", 96f, top + 62, f.small, MUTE)
        val stats = arrayOf("312" to "km hiked", "41" to "summits", "18,920" to "m climbed")
        val sy = top + 108
        val sw = (W - 32 - 16) / 3
        val sh = min(96f, H - sy - 8)
        for (i in 0 until 3) {
            val x = 16 + i * (sw + 8)
            cardRect(c, ctx, x, sy, sw, sh, 18f)
            ctx.text(c, stats[i].first, x + 14, sy + sh / 2 + 2, f.big, INK)
            ctx.text(c, stats[i].second, x + 14, sy + sh / 2 + 22, f.small, MUTE)
        }
        val by = sy + sh + 16
        if (by + 60 < H) {
            ctx.text(c, "BADGES", 20f, by + 12, f.tiny, FAINT)
            for (i in 0 until 5) {
                val x = 40f + i * 58
                val y = by + 44
                if (y + 24 > H) break
                c.drawCircle(x, y, 22f, g.shaded(g.radial(x - 6, y - 8, 30f, intArrayOf(compassRgb(0x2E4A3E), compassRgb(0x1C2E26)))))
                c.drawCircle(x, y, 22f, ctx.stroke(if (i < 3) ACCENT else compassRgb(0xEEF1E8), if (i < 3) 0.7 else 0.2, 1.5f))
                val m = Path().apply {
                    moveTo(x - 11, y + 7)
                    lineTo(x - 3, (y - 6 - hash(i.toDouble()) * 3).toFloat())
                    lineTo(x + 2, y + 1)
                    lineTo(x + 6, y - 4)
                    lineTo(x + 12, y + 7)
                    close()
                }
                c.drawPath(m, g.paint(compassRgb(0xEEF1E8), if (i < 3) 0.85 else 0.3))
            }
        }
    }

    /** Housing, bezel with degrees and letters, and the liquid well with its card (no needle). */
    private fun compass(c: Canvas, s: CompassScene, ctx: Ctx) {
        val g = ctx.g
        val f = ctx.fonts
        val cx = s.box / 2
        val cy = s.box / 2
        val R = s.r
        c.drawCircle(cx, cy + 10, R + 12, g.blurred(0xFF000000.toInt(), 0.5, 14f))
        c.drawCircle(cx, cy + 2, R + 12, g.blurred(0xFF000000.toInt(), 0.6, 2f))
        // Housing: dark anodised metal with a knurled rim and a bright chamfer.
        val sweep = SweepGradient(cx, cy, intArrayOf(compassRgb(0x3A4440), compassRgb(0x8E9893), compassRgb(0x2F3835), compassRgb(0x6E7873), compassRgb(0x2C3431), compassRgb(0x7E8883), compassRgb(0x3A4440)), null)
        c.drawCircle(cx, cy, R + 12, g.shaded(sweep))
        val knurl = ctx.stroke(compassRgb(0x0E1412), 0.5, 1f)
        for (i in 0 until 120) {
            val a = i / 120.0 * PI * 2
            c.drawLine((cx + cos(a) * (R + 8)).toFloat(), (cy + sin(a) * (R + 8)).toFloat(), (cx + cos(a) * (R + 12)).toFloat(), (cy + sin(a) * (R + 12)).toFloat(), knurl)
        }
        c.drawCircle(cx, cy, R + 1, g.shaded(g.linear(cx, cy - R, cx, cy + R, intArrayOf(compassRgb(0xC9D2CD), compassRgb(0x5E6864), compassRgb(0x2A322F)))))
        // Bezel: matte black ring with ticks every 5°, numbers every 30°, the cardinals.
        c.drawCircle(cx, cy, R, g.shaded(g.radial(cx, cy - R * 0.3f, R * 1.2f, intArrayOf(compassRgb(0x26302C), compassRgb(0x1A2220), compassRgb(0x121816)))))
        var d = 0
        while (d < 360) {
            val a = (d - 90) * PI / 180
            val major = d % 30 == 0
            val r0 = R - 3
            val r1 = R - if (major) 12 else 7
            c.drawLine((cx + cos(a) * r0).toFloat(), (cy + sin(a) * r0).toFloat(), (cx + cos(a) * r1).toFloat(), (cy + sin(a) * r1).toFloat(), ctx.stroke(compassRgb(0xE9EEE6), if (major) 0.85 else 0.4, if (major) 1.4f else 0.8f))
            d += 5
        }
        d = 30
        while (d < 360) {
            if (d % 90 != 0) {
                val a = (d - 90) * PI / 180
                val label = d.toString()
                val r = R - 21
                c.save()
                c.translate((cx + cos(a) * r).toFloat(), (cy + sin(a) * r).toFloat())
                c.rotate(d.toFloat())
                ctx.text(c, label, -f.degree.measureText(label) / 2, 3.5f, f.degree, compassRgb(0xE9EEE6), 0.7)
                c.restore()
            }
            d += 30
        }
        val cards = arrayOf("N", "E", "S", "W")
        for (i in 0 until 4) {
            val a = (i * 90 - 90) * PI / 180
            val r = R - 22
            c.save()
            c.translate((cx + cos(a) * r).toFloat(), (cy + sin(a) * r).toFloat())
            c.rotate(i * 90f)
            ctx.text(c, cards[i], -f.cardinal.measureText(cards[i]) / 2, 6f, f.cardinal, if (i == 0) compassRgb(0xF0563F) else compassRgb(0xF2F4EE))
            c.restore()
        }
        // The liquid well: deep tinted glass, an inner shadow at its rim, a faint rose.
        val wr = R * 0.66f
        c.drawCircle(cx, cy, wr + 2, g.paint(compassRgb(0x0A100E)))
        c.drawCircle(cx, cy, wr, g.shaded(g.radial(cx - wr * 0.25f, cy - wr * 0.3f, wr * 1.4f, intArrayOf(compassRgb(0x24453F), compassRgb(0x16302C), compassRgb(0x0D1E1C)))))
        val rose = ctx.stroke(compassRgb(0xBFE3D6), 0.16, 0.8f)
        d = 0
        while (d < 360) {
            val a = d * PI / 180
            val r1 = if (d % 90 == 0) wr * 0.7 else wr * 0.86
            c.drawLine((cx + cos(a) * wr * 0.92).toFloat(), (cy + sin(a) * wr * 0.92).toFloat(), (cx + cos(a) * r1).toFloat(), (cy + sin(a) * r1).toFloat(), rose)
            d += 10
        }
        c.drawCircle(cx, cy, wr * 0.5f, ctx.stroke(compassRgb(0xBFE3D6), 0.1, 0.8f))
        c.save()
        c.clipPath(Path().apply { addCircle(cx, cy, wr, Path.Direction.CW) })
        c.drawCircle(cx, cy + 2, wr + 3, g.stroke(g.blurred(0xFF000000.toInt(), 0.7, 6f), 8f))
        c.restore()
    }

    /** The glass dome over the well: a crescent highlight and a sharp glint. */
    private fun glass(c: Canvas, s: CompassScene, ctx: Ctx) {
        val g = ctx.g
        val cx = s.box / 2
        val cy = s.box / 2
        val wr = s.r * 0.66f
        c.save()
        c.clipPath(Path().apply { addCircle(cx, cy, wr, Path.Direction.CW) })
        val crescent = Path().apply { addCircle(cx, cy, wr, Path.Direction.CW) }
        val cut = Path().apply { addCircle(cx + wr * 0.22f, cy + wr * 0.26f, wr * 1.02f, Path.Direction.CW) }
        crescent.op(cut, Path.Op.DIFFERENCE)
        c.drawPath(crescent, g.shaded(g.linear(cx - wr, cy - wr, cx, cy, intArrayOf(compassRgba(255, 255, 255, 0.32), compassRgba(255, 255, 255, 0.06)))))
        c.drawOval(RectF(cx - wr * 0.55f, cy - wr * 0.72f, cx - wr * 0.23f, cy - wr * 0.58f), g.blurred(0xFFFFFFFF.toInt(), 0.55, 2f))
        c.restore()
        c.drawCircle(cx, cy, wr, ctx.stroke(compassRgb(0xDDEDE6), 0.22, 1f))
    }
}

/** Draws the moving parts each frame; holds its reusable paints and paths. */
internal class CompassPainter {
    private val g = CompassNeedleNavGfx()
    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fadePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()
    private val dst = RectF()
    private val facet = Path()
    private val shadow = Path()

    private fun image(c: Canvas, img: Bitmap, d: RectF, p: Paint) {
        src.set(0, 0, img.width, img.height)
        c.drawBitmap(img, src, d, p)
    }

    /** One frame; [bob] (radians) drives the needle's slight bob in the liquid. */
    fun frame(c: Canvas, s: CompassScene, layers: CompassLayers, pose: CompassPose, bob: Double) {
        dst.set(0f, 0f, s.w, s.h)
        image(c, layers.ground, dst, imagePaint)

        // Pages on the wheel: each hangs at k × 90° and swings round the compass's centre.
        c.save()
        c.clipRect(0f, s.pageTop - 6, s.w, s.pageTop + s.pageH + 6)
        for (k in layers.pages.indices) {
            val d = wrap(k * 90 - pose.wheel)
            if (abs(d) >= 89) continue
            fadePaint.alpha = (255 * clamp01(1 - (abs(d) / 80).pow(1.5))).roundToInt()
            c.save()
            c.rotate(d.toFloat(), s.cx, s.cy)
            dst.set(0f, s.pageTop, s.w, s.pageTop + s.pageH)
            image(c, layers.pages[k], dst, fadePaint)
            c.restore()
        }
        c.restore()

        image(c, layers.compass, s.boxRect, imagePaint)
        // The heading being steered to glows on the bezel.
        val ta = (pose.target - 90) * PI / 180
        val glow = g.blurred(compassRgb(0xF07A3A), 0.65, 6f)
        glow.xfermode = CompassNeedleNavGfx.plus
        c.drawCircle((s.cx + cos(ta) * (s.r - 8)).toFloat(), (s.cy + sin(ta) * (s.r - 8)).toFloat(), 9f, glow)

        // The needle floats: a shadow offset on the card below it, a slight bob.
        val len = s.r * 0.6f
        val lift = (1 + 0.012 * sin(bob)).toFloat()
        c.save()
        c.translate(3f, 5f)
        c.rotate(pose.needle.toFloat(), s.cx, s.cy)
        shadow.rewind()
        shadow.moveTo(s.cx - len * 0.13f, s.cy)
        shadow.lineTo(s.cx, s.cy - len)
        shadow.lineTo(s.cx + len * 0.13f, s.cy)
        shadow.lineTo(s.cx, s.cy + len * 0.86f)
        shadow.close()
        c.drawPath(shadow, g.blurred(0xFF000000.toInt(), 0.45, 3f))
        c.restore()
        needle(c, s.cx, s.cy, len * lift, pose.needle.toFloat())
        c.drawCircle(s.cx, s.cy, 7f, g.shaded(g.radial(s.cx - 2, s.cy - 2, 8f, intArrayOf(compassRgb(0xF7E4B0), compassRgb(0xB68C42), compassRgb(0x5E4219)))))
        // An air bubble under the glass drifts against the spin.
        val lag = max(-50.0, min(50.0, -pose.spin * 0.12))
        val ba = (225 + lag) * PI / 180
        val br = s.r * 0.48f
        val bx = (s.cx + cos(ba) * br).toFloat()
        val by = (s.cy + sin(ba) * br).toFloat()
        c.drawCircle(bx, by, 4.2f, g.paint(compassRgb(0xCFEDE4), 0.18))
        c.drawCircle(bx, by, 4.2f, g.stroke(g.paint(compassRgb(0xE8FFF6), 0.5), 0.8f, round = true))
        c.drawCircle(bx - 1.3f, by - 1.3f, 1.1f, g.paint(0xFFFFFFFF.toInt(), 0.8))
        image(c, layers.glass, s.boxRect, imagePaint)
        // The finger's bead on the bezel while dragging.
        if (!pose.finger.isNaN()) {
            val fa = (pose.finger - 90) * PI / 180
            val fx = (s.cx + cos(fa) * (s.r + 4)).toFloat()
            val fy = (s.cy + sin(fa) * (s.r + 4)).toFloat()
            c.drawCircle(fx, fy, 12f, g.blurred(compassRgb(0xF07A3A), 0.5, 6f))
            c.drawCircle(fx, fy, 6f, g.paint(compassRgb(0xF07A3A)))
            c.drawCircle(fx, fy, 6f, g.stroke(g.paint(compassRgb(0xFFE3D0), 0.8), 1.2f, round = true))
        }
    }

    private fun needle(c: Canvas, cx: Float, cy: Float, len: Float, angle: Float) {
        c.save()
        c.rotate(angle, cx, cy)
        val wdt = len * 0.13f
        val tail = len * 0.86f
        // Four facets: north red (lit side and shade side), south cream.
        fun tri(x0: Float, y0: Float, x1: Float, y1: Float, color: Int) {
            facet.rewind()
            facet.moveTo(cx, cy)
            facet.lineTo(x0, y0)
            facet.lineTo(x1, y1)
            facet.close()
            c.drawPath(facet, g.paint(color))
        }
        tri(cx - wdt, cy, cx, cy - len, compassRgb(0xE5533F))
        tri(cx + wdt, cy, cx, cy - len, compassRgb(0xA82F22))
        tri(cx - wdt, cy, cx, cy + tail, compassRgb(0xF1ECE0))
        tri(cx + wdt, cy, cx, cy + tail, compassRgb(0xB8B1A2))
        c.drawLine(cx, cy - len + 2, cx, cy - 4, g.stroke(g.paint(compassRgb(0xFFD2C8), 0.35), 0.6f, round = true))
        c.restore()
    }
}
