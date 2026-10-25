package com.ngocdevv.nativecook.blocks.modals.parachutedropalert

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.blocks.modals.parachutedropalert.ParachuteGfx.Companion.linear
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 Parachute Drop Alert — the drawing, ported from parachuteArt.ts. Two canvases: the app under the
 text (sky with clouds and rooftops, the sheet, its cards, route and tab-bar icons: baked once off
 the main thread, never redrawn) and the alert layer over it (scrim, suspension lines, the canopies
 and the card, redrawn each frame from the pose). A canopy is seven gores between elliptical
 meridians with a scalloped hem, coral and cream, shaded across the dome; collapsing, its height
 falls, its hem sags in folds and its lines go slack.
*/

private const val CORAL = "#EE6B4D"
private const val INK = "#1D2B3D"

internal class ParachuteFonts {
    private fun font(weight: Int, size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.DEFAULT, weight, false)
        else Typeface.create(Typeface.DEFAULT, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
        textSize = size
    }
    val title = font(600, 17f)
    val body = font(400, 14f)
    val button = font(600, 16f)
}

internal class ParachuteLayout(val w: Float, val h: Float, val top: Float, bottom: Float) {
    val skyH = top + 250
    val sheetY = skyH - 24
    val card1Y = sheetY + 22
    val card1H = 176f
    val sectionY = card1Y + card1H + 22
    val rowsY = sectionY + 30
    val tabY = h - max(bottom, 8f) - 56
    val rows = max(0, min(3, floor((tabY - 12 - rowsY) / 78).toInt()))
    val cardW = min(w - 40, 360f)
    val cardH = 172f
    val canW = cardW * 0.74f
    private val pad = 20f
    private val bw = (cardW - pad * 2 - 10) / 2
    /** Button rects in card space (from the card's top left): later, track. */
    val later = floatArrayOf(pad, cardH - 62, bw, 44f)
    val track = floatArrayOf(pad + bw + 10, cardH - 62, bw, 44f)
    val landX = w / 2
    val landY = skyH + 44
    val geo = ParachuteGeo(w.toDouble(), h.toDouble(), cardW.toDouble(), cardH.toDouble(), landX.toDouble(), landY.toDouble(), canW.toDouble(), canW * 0.42, 104.0)
}

internal class ParachuteLayers(val app: ParachuteBaked, val card: ParachuteBaked)

internal object ParachuteDropAlertArt {
    private fun cloud(c: Canvas, x: Float, y: Float, s: Float, a: Double, gx: ParachuteGfx) {
        val puffs = floatArrayOf(-0.9f, 0.1f, 0.32f, -0.3f, -0.25f, 0.42f, 0.25f, -0.38f, 0.5f, 0.8f, 0.05f, 0.3f)
        for (i in puffs.indices step 3) c.drawCircle(x + puffs[i] * 60 * s, y + puffs[i + 1] * 30 * s, puffs[i + 2] * 70 * s, gx.blurred("#FFFFFF", a, 6 * s))
        c.drawOval(x - 70 * s, y + 4 * s, x + 70 * s, y + 26 * s, gx.blurred("#FFFFFF", a * 0.9, 6 * s))
    }

    /** A parcel: a little box in cream lines. */
    fun parcel(c: Canvas, x: Float, y: Float, s: Float, color: String, gx: ParachuteGfx) {
        val p = Path().apply {
            moveTo(x, y - 9 * s)
            lineTo(x + 10 * s, y - 4 * s)
            lineTo(x + 10 * s, y + 7 * s)
            lineTo(x, y + 12 * s)
            lineTo(x - 10 * s, y + 7 * s)
            lineTo(x - 10 * s, y - 4 * s)
            close()
            moveTo(x - 10 * s, y - 4 * s)
            lineTo(x, y + 1 * s)
            lineTo(x + 10 * s, y - 4 * s)
            moveTo(x, y + 1 * s)
            lineTo(x, y + 12 * s)
            moveTo(x - 5 * s, y - 6.5f * s)
            lineTo(x + 5 * s, y - 1.5f * s)
        }
        c.drawPath(p, roundJoin(gx.stroke(color, 1.6f * s)))
    }

    private fun roundJoin(p: Paint) = p.apply { strokeJoin = Paint.Join.ROUND }

    private fun tile(c: Canvas, x: Float, y: Float, size: Float, from: String, to: String, gx: ParachuteGfx) {
        c.drawRoundRect(x, y, x + size, y + size, size * 0.3f, size * 0.3f, gx.shaded(linear(x, y, x + size, y + size, arrayOf(from, to))))
        c.drawRoundRect(x + 0.5f, y + 0.5f, x + size - 0.5f, y + size - 0.5f, size * 0.3f, size * 0.3f, gx.stroke("#FFFFFF", 1f, 0.25))
    }

    private fun cardBox(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, gx: ParachuteGfx) {
        c.drawRoundRect(x, y + 6, x + w, y + 6 + h, r, r, gx.blurred("#5B4A3A", 0.12, 10f))
        c.drawRoundRect(x, y + 1, x + w, y + 1 + h, r, r, gx.blurred("#5B4A3A", 0.12, 1.5f))
        c.drawRoundRect(x, y, x + w, y + h, r, r, gx.shaded(linear(x, y, x, y + h, arrayOf("#FFFDFA", "#FBF8F3"))))
    }

    private fun roofs(c: Canvas, w: Float, yb: Float, seed: Double, color: String, hgt: Float, gx: ParachuteGfx) {
        val b = Path()
        b.moveTo(0f, yb)
        var x = 0f
        var i = 0
        while (x < w) {
            val bw = (26 + 30 * abs(sin(seed + i * 1.7))).toFloat()
            val bh = (hgt * (0.45 + 0.55 * abs(sin(seed * 2 + i * 2.3)))).toFloat()
            b.lineTo(x, yb - bh)
            if (i % 3 == 1) {
                b.lineTo(x + bw * 0.5f, yb - bh - 12)
                b.lineTo(x + bw, yb - bh)
            } else {
                if (i % 2 == 0) {
                    b.lineTo(x + bw * 0.25f, yb - bh)
                    b.lineTo(x + bw * 0.25f, yb - bh - 9)
                    b.lineTo(x + bw * 0.35f, yb - bh - 9)
                    b.lineTo(x + bw * 0.35f, yb - bh)
                }
                b.lineTo(x + bw, yb - bh)
            }
            x += bw
            i += 1
        }
        b.lineTo(w, yb)
        b.close()
        c.drawPath(b, gx.paint(color))
    }

    /** The app under the alert (everything but its text). */
    private fun drawApp(c: Canvas, g: ParachuteLayout, gx: ParachuteGfx) {
        val w = g.w
        val h = g.h
        c.drawRect(0f, 0f, w, g.skyH + 10, gx.shaded(linear(0f, 0f, 0f, g.skyH, arrayOf("#8FB8DD", "#B4CFE7", "#D8E2EC", "#F2DCCB"), floatArrayOf(0f, 0.4f, 0.72f, 1f))))
        c.drawCircle(w * 0.82f, g.top + 40, 90f, gx.blurred("#FFF4E0", 0.45, 40f))
        cloud(c, w * 0.18f, g.top + 118, 0.8f, 0.75, gx)
        cloud(c, w * 0.86f, g.top + 168, 0.6f, 0.6, gx)
        cloud(c, w * 0.52f, g.top + 214, 0.45f, 0.5, gx)
        // Rooftops on the horizon, two layers.
        roofs(c, w, g.skyH + 4, 1.3, "#BCCBDA", 54f, gx)
        roofs(c, w, g.skyH + 4, 4.1, "#A5B7C9", 34f, gx)
        ParachuteGrain(3).draw(c, RectF(0f, 0f, w, g.skyH + 10), 0.05)
        // The sheet.
        c.drawRoundRect(0f, g.sheetY, w, h + 40, 28f, 28f, gx.shaded(linear(0f, g.sheetY, 0f, h, arrayOf("#F7F3EE", "#F3EEE7", "#EEE8E0"))))
        c.drawRoundRect(w / 2 - 18, g.sheetY + 8, w / 2 + 18, g.sheetY + 13, 2.5f, 2.5f, gx.paint("#1D2B3D", 0.14))
        // Card 1: the parcel on its way, with its route.
        val x = 16f
        val cw = w - 32
        cardBox(c, x, g.card1Y, cw, g.card1H, 22f, gx)
        tile(c, x + 16, g.card1Y + 16, 44f, "#F58B66", "#E2553C", gx)
        parcel(c, x + 38, g.card1Y + 37, 1f, "#FFF3EA", gx)
        val ry = g.card1Y + 104
        val x0 = x + 30
        val x1 = x + cw - 30
        c.drawLine(x0, ry, x1, ry, gx.stroke("#1D2B3D", 3f, 0.1))
        val done = x0 + (x1 - x0) * 0.78f
        val prog = gx.strokeShaded(linear(x0, 0f, done, 0f, arrayOf("#F6A07E", CORAL)), 3f, round = true)
        c.drawLine(x0, ry, done, ry, prog)
        for (k in 0 until 4) {
            val sx = x0 + (x1 - x0) * k / 3
            val reached = sx <= done
            c.drawCircle(sx, ry, 7f, gx.paint(if (reached) CORAL else "#E4DDD3"))
            c.drawCircle(sx, ry, 3f, gx.paint("#FFFDFA"))
        }
        // The van, just past "Out for delivery".
        c.drawCircle(done, ry, 12f, gx.blurred(CORAL, 0.35, 5f))
        c.drawCircle(done, ry, 10f, gx.paint("#FFFDFA"))
        c.drawCircle(done, ry, 10f, gx.stroke(CORAL, 2f))
        c.drawRoundRect(done - 5.5f, ry - 3.5f, done + 2.5f, ry + 2.5f, 1.2f, 1.2f, gx.paint(CORAL))
        c.drawRoundRect(done + 2, ry - 1.5f, done + 5.5f, ry + 2.5f, 1f, 1f, gx.paint(CORAL))
        c.drawLine(x + 16, g.card1Y + 140, x + cw - 16, g.card1Y + 140, gx.stroke("#1D2B3D", 1f, 0.07))
        // Rows.
        for (r in 0 until g.rows) {
            val y = g.rowsY + r * 78
            cardBox(c, x, y, cw, 68f, 20f, gx)
            tile(c, x + 14, y + 12, 44f, if (r % 2 == 0) "#9EC3E3" else "#E9C37A", if (r % 2 == 0) "#6E9DC9" else "#D79E45", gx)
            parcel(c, x + 36, y + 33, 0.9f, "#FFFBF4", gx)
        }
        // Tab bar.
        c.drawRect(0f, g.tabY, w, h, gx.shaded(linear(0f, g.tabY, 0f, h, arrayOf("#FBF8F4", "#F6F2EC"))))
        c.drawLine(0f, g.tabY, w, g.tabY, gx.stroke("#1D2B3D", 1f, 0.08))
        for (k in 0 until 4) {
            val cx = w * (k + 0.5f) / 4
            val cy = g.tabY + 18
            val col = if (k == 1) CORAL else "#8A93A0"
            when (k) {
                0 -> c.drawPath(Path().apply {
                    moveTo(cx - 8, cy - 1); lineTo(cx, cy - 8); lineTo(cx + 8, cy - 1)
                    moveTo(cx - 6, cy - 3); lineTo(cx - 6, cy + 7); lineTo(cx + 6, cy + 7); lineTo(cx + 6, cy - 3)
                }, roundJoin(gx.stroke(col, 1.8f)))
                1 -> {
                    c.drawPath(Path().apply {
                        moveTo(cx, cy + 8)
                        cubicTo(cx - 11, cy - 2, cx - 7, cy - 9, cx, cy - 9)
                        cubicTo(cx + 7, cy - 9, cx + 11, cy - 2, cx, cy + 8)
                    }, roundJoin(gx.stroke(col, 1.8f)))
                    c.drawCircle(cx, cy - 2, 2.6f, gx.stroke(col, 1.8f))
                }
                2 -> parcel(c, cx, cy - 1, 0.75f, col, gx)
                else -> {
                    c.drawCircle(cx, cy - 4, 4f, gx.stroke(col, 1.8f))
                    c.drawPath(Path().apply { moveTo(cx - 8, cy + 8); quadTo(cx, cy - 2, cx + 8, cy + 8) }, roundJoin(gx.stroke(col, 1.8f)))
                }
            }
        }
    }

    /** The alert card, drawn at the origin. */
    private fun drawCard(c: Canvas, g: ParachuteLayout, f: ParachuteFonts, gx: ParachuteGfx) {
        val w = g.cardW
        val h = g.cardH
        c.drawRoundRect(0f, 0f, w, h, 26f, 26f, gx.shaded(linear(0f, 0f, 0f, h, arrayOf("#FFFEFB", "#FBF7F1", "#F5EFE6"))))
        c.drawRoundRect(0.5f, 0.5f, w - 0.5f, h - 0.5f, 25.5f, 25.5f, gx.stroke("#FFFFFF", 1f, 0.9))
        c.save()
        c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, w, h), 26f, 26f, Path.Direction.CW) })
        ParachuteGrain(9).draw(c, RectF(0f, 0f, w, h), 0.05)
        c.restore()
        tile(c, 20f, 20f, 48f, "#F58B66", "#E2553C", gx)
        parcel(c, 44f, 43f, 1.1f, "#FFF3EA", gx)
        fun text(s: String, x: Float, y: Float, color: String, font: Paint) {
            font.color = parachuteColor(color)
            c.drawText(s, x, y, font)
        }
        text("Your parcel is 4 min away", 82f, 40f, INK, f.title)
        text("Leo is on Linden Street now.", 82f, 62f, "#5C6776", f.body)
        text("Door code 2417 is ready.", 82f, 81f, "#5C6776", f.body)
        val l = g.later
        c.drawRoundRect(l[0], l[1], l[0] + l[2], l[1] + l[3], l[3] / 2, l[3] / 2, gx.paint("#EEE7DD"))
        text("Later", l[0] + l[2] / 2 - f.button.measureText("Later") / 2, l[1] + 28, INK, f.button)
        val t = g.track
        c.drawRoundRect(t[0], t[1] + 2, t[0] + t[2], t[1] + 2 + t[3], t[3] / 2, t[3] / 2, gx.blurred("#C8452C", 0.35, 4f))
        c.drawRoundRect(t[0], t[1], t[0] + t[2], t[1] + t[3], t[3] / 2, t[3] / 2, gx.shaded(linear(0f, t[1], 0f, t[1] + t[3], arrayOf("#F48563", CORAL, "#DC5236"))))
        c.drawRoundRect(t[0] + 0.5f, t[1] + 0.5f, t[0] + t[2] - 0.5f, t[1] + t[3] - 0.5f, t[3] / 2, t[3] / 2, gx.stroke("#FFFFFF", 1f, 0.25))
        text("Track", t[0] + t[2] / 2 - f.button.measureText("Track") / 2, t[1] + 28, "#FFF8F2", f.button)
    }

    suspend fun bakeLayers(g: ParachuteLayout, f: ParachuteFonts, scale: Float): ParachuteLayers {
        val ctx = coroutineContext
        val gx = ParachuteGfx(live = false)
        val app = ParachuteGfx.bake(0f, 0f, g.w, g.h, scale) { drawApp(it, g, gx) }
        ctx.ensureActive()
        val card = ParachuteGfx.bake(0f, 0f, g.cardW, g.cardH, scale) { drawCard(it, g, f, gx) }
        return ParachuteLayers(app, card)
    }
}

/** Draws the alert layer on the screen's canvas (in dp). */
internal class ParachutePainter {
    private val gx = ParachuteGfx(live = true)
    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val outline = Path()
    private val m = DoubleArray(2)
    private val a = DoubleArray(2)
    private val z = DoubleArray(2)
    private val cardRect = RectF()

    /** Meridian point s (0 apex .. 1 hem) of gore edge e (0..GORES) of a canopy, in its own frame (hem centre at 0). */
    private fun meridian(c: ParachuteCanopy, e: Double, s: Double, out: DoubleArray) {
        val phi = (e / PARACHUTE_GORES - 0.5) * PI
        val k = c.collapse
        out[0] = (c.w / 2) * sin(phi) * sin(s * PI / 2) * (1 + 0.1 * k * sin(e * 2.1 + s * 3))
        // Collapsing: the hem drops in folds and the crown sinks below it.
        val sag = k * (10 + 14 * abs(sin(e * 1.9 + 0.6))) * s
        out[1] = -c.h * cos(s * PI / 2) + sag
    }

    private fun canopyPath(c: ParachuteCanopy, b: Path): Path {
        b.reset()
        for (s in 0..8) {
            meridian(c, 0.0, s / 8.0, m)
            if (s == 0) b.moveTo(m[0].toFloat(), m[1].toFloat()) else b.lineTo(m[0].toFloat(), m[1].toFloat())
        }
        for (e in 1..PARACHUTE_GORES) {
            meridian(c, e - 1.0, 1.0, a)
            meridian(c, e.toDouble(), 1.0, z)
            b.quadTo(((a[0] + z[0]) / 2).toFloat(), ((a[1] + z[1]) / 2 - c.h * 0.09 * (1 - c.collapse * 0.5)).toFloat(), z[0].toFloat(), z[1].toFloat())
        }
        for (s in 8 downTo 0) {
            meridian(c, PARACHUTE_GORES.toDouble(), s / 8.0, m)
            b.lineTo(m[0].toFloat(), m[1].toFloat())
        }
        b.close()
        return b
    }

    private fun gorePath(c: ParachuteCanopy, e: Int, b: Path): Path {
        b.reset()
        for (s in 0..8) {
            meridian(c, e.toDouble(), s / 8.0, m)
            if (s == 0) b.moveTo(m[0].toFloat(), m[1].toFloat()) else b.lineTo(m[0].toFloat(), m[1].toFloat())
        }
        meridian(c, e + 1.0, 1.0, z)
        b.quadTo(((m[0] + z[0]) / 2).toFloat(), ((m[1] + z[1]) / 2 - c.h * 0.09 * (1 - c.collapse * 0.5)).toFloat(), z[0].toFloat(), z[1].toFloat())
        for (s in 8 downTo 0) {
            meridian(c, e + 1.0, s / 8.0, m)
            b.lineTo(m[0].toFloat(), m[1].toFloat())
        }
        b.close()
        return b
    }

    /** Where a canopy's lines meet the card (two clips at the card's top corners), in screen space. */
    private fun attach(g: ParachuteGeo, p: ParachutePose, side: Int, out: DoubleArray) {
        val lx = side * (g.cardW / 2 - 34)
        val ly = -g.cardH / 2
        val cs = cos(p.cardRot)
        val sn = sin(p.cardRot)
        out[0] = p.cardX + lx * cs - ly * sn
        out[1] = p.cardY + lx * sn + ly * cs
    }

    private fun drawCanopy(cv: Canvas, g: ParachuteGeo, p: ParachutePose, c: ParachuteCanopy) {
        if (c.alpha <= 0.001) return
        // Lines first: from each hem point to the nearer clip (or trailing free once released).
        val line = gx.stroke("#2E3A4A", 1f, 0.6 * c.alpha)
        line.strokeJoin = Paint.Join.ROUND
        val cs = cos(c.tilt)
        val sn = sin(c.tilt)
        for (e in 0..PARACHUTE_GORES) {
            meridian(c, e.toDouble(), 1.0, m)
            val hx = c.x + m[0] * cs - m[1] * sn
            val hy = c.y + m[0] * sn + m[1] * cs
            if (c.attached) {
                attach(g, p, if (e < PARACHUTE_GORES / 2.0) -1 else 1, a)
            } else {
                a[0] = c.x + (e.toDouble() / PARACHUTE_GORES - 0.5) * 30 - 40
                a[1] = c.y + g.lines * 0.55
            }
            val mx = (hx + a[0]) / 2 + c.slack * 18 * (e.toDouble() / PARACHUTE_GORES - 0.5)
            val my = (hy + a[1]) / 2 + c.slack * 30
            path.reset()
            path.moveTo(hx.toFloat(), hy.toFloat())
            path.quadTo(mx.toFloat(), my.toFloat(), a[0].toFloat(), a[1].toFloat())
            cv.drawPath(path, line)
        }
        cv.save()
        cv.translate(c.x.toFloat(), c.y.toFloat())
        cv.rotate((c.tilt * 180 / PI).toFloat())
        val w = c.w.toFloat()
        val h = c.h.toFloat()
        canopyPath(c, outline)
        val layer = cv.saveLayerAlpha(-w, -h * 1.6f, w, h * 1.2f + 40, (255 * c.alpha).toInt().coerceIn(0, 255))
        for (e in 0 until PARACHUTE_GORES) cv.drawPath(gorePath(c, e, path), gx.paint(if (e % 2 == 0) CORAL else "#FBEFE2"))
        // Shade across the dome: lit from the upper left, darker at the far edge and the hem.
        cv.save()
        cv.clipPath(outline)
        cv.drawRect(-w, -h * 1.4f, w, h * 0.4f, gx.shaded(linear(-w / 2, 0f, w / 2, 0f, arrayOf("rgba(255,255,255,0.22)", "rgba(255,255,255,0)", "rgba(60,30,30,0.1)", "rgba(60,30,30,0.32)"), floatArrayOf(0f, 0.35f, 0.7f, 1f))))
        cv.drawRect(-w, -h * 1.4f, w, h * 0.4f, gx.shaded(linear(0f, -h, 0f, 10f, arrayOf("rgba(255,255,255,0.1)", "rgba(0,0,0,0)", "rgba(70,30,20,0.25)"), floatArrayOf(0f, 0.6f, 1f))))
        cv.restore()
        for (e in 1 until PARACHUTE_GORES) {
            path.reset()
            for (s in 0..8) {
                meridian(c, e.toDouble(), s / 8.0, m)
                if (s == 0) path.moveTo(m[0].toFloat(), m[1].toFloat()) else path.lineTo(m[0].toFloat(), m[1].toFloat())
            }
            cv.drawPath(path, gx.stroke("#7A3426", 0.8f, 0.35))
        }
        cv.drawPath(outline, gx.stroke("#7A3426", 1f, 0.3).apply { strokeJoin = Paint.Join.ROUND })
        // The vent at the crown.
        meridian(c, PARACHUTE_GORES / 2.0, 0.04, m)
        val vx = m[0].toFloat()
        val vy = m[1].toFloat()
        cv.drawOval(vx - w * 0.05f, vy - 2, vx + w * 0.05f, vy + 3, gx.paint("#3A2420", 0.55 * (1 - c.collapse)))
        cv.restoreToCount(layer)
        cv.restore()
    }

    fun alert(c: Canvas, g: ParachuteLayout, l: ParachuteLayers, p: ParachutePose, pressed: Int) {
        if (p.fade <= 0.001) return
        val geo = g.geo
        val faded = p.fade < 0.999
        val save = if (faded) c.saveLayerAlpha(0f, 0f, g.w, g.h, (255 * p.fade).toInt()) else -1
        if (p.scrim > 0.002) c.drawRect(0f, 0f, g.w, g.h, gx.paint("#1A2433", p.scrim))
        if (!p.shown) {
            if (save >= 0) c.restoreToCount(save)
            return
        }
        // Canopies behind the card first (a draped one lies over its top edge, so it comes after).
        for (i in 0 until p.canopyCount) if (p.canopies[i].collapse < 0.35) drawCanopy(c, geo, p, p.canopies[i])
        c.save()
        c.translate(p.cardX.toFloat(), p.cardY.toFloat())
        c.rotate((p.cardRot * 180 / PI).toFloat())
        c.translate(0f, g.cardH / 2)
        c.scale(p.sx.toFloat(), p.sy.toFloat())
        c.translate(-g.cardW / 2, -g.cardH)
        c.drawRoundRect(4f, 22f, g.cardW - 4, 22 + g.cardH, 26f, 26f, gx.blurred("#0E1726", 0.3, 22f))
        c.drawRoundRect(0f, 2f, g.cardW, 2 + g.cardH, 26f, 26f, gx.blurred("#0E1726", 0.22, 2.5f))
        cardRect.set(0f, 0f, g.cardW, g.cardH)
        c.drawBitmap(l.card.bmp, null, cardRect, image)
        if (pressed >= 0) {
            val r = if (pressed == 0) g.later else g.track
            c.drawRoundRect(r[0], r[1], r[0] + r[2], r[1] + r[3], r[3] / 2, r[3] / 2, gx.paint("#1D2B3D", 0.1))
        }
        // The two clips the lines hook into.
        for (s in intArrayOf(-1, 1)) {
            val x = g.cardW / 2 + s * (g.cardW / 2 - 34)
            c.drawCircle(x, 0f, 4.5f, gx.paint("#C9CFD6"))
            c.drawCircle(x, 0f, 4.5f, gx.stroke("#6E7884", 1f))
        }
        c.restore()
        for (i in 0 until p.canopyCount) if (p.canopies[i].collapse >= 0.35) drawCanopy(c, geo, p, p.canopies[i])
        if (save >= 0) c.restoreToCount(save)
    }
}
