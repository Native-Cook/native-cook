package com.ngocdevv.nativecook.blocks.onboarding.aifeaturetour

import android.content.Context
import android.graphics.Path
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.onboarding.aifeaturetour.AiFeatureTourMotion as M
import com.ngocdevv.nativecook.blocks.onboarding.aifeaturetour.AiFeatureTourTheme.C
import com.ngocdevv.nativecook.blocks.onboarding.aifeaturetour.AiFeatureTourTheme.UI

/** The pin's pixels are preserved until the final root transform. */
internal class AiFeatureTourArt(context: Context) {
    private val drawing = AiFeatureTourCanvas(context)
    private val blueDays = listOf(2, 4, 6, 8, 10, 12, 15, 17, 19, 22, 24, 26)
    private fun tile(canvas: AiFeatureTourCanvas, x: Double, y: Double, w: Double, h: Double, r: Double, lift: Double = 0.0) {
        canvas.round(x + 2, y + 8 + 4 * lift, w - 4, h, r, C.tileShade, 0.35 + 0.15 * lift, blur = 9 + 4 * lift)
        canvas.round(x, y, w, h, r, C.tile)
        canvas.round(x, y, w, h, r, C.tileEdge, width = 1.5)
    }
    private fun check(canvas: AiFeatureTourCanvas, x: Double, y: Double, r: Double, color: String, k: Double, filled: Boolean) {
        canvas.circle(x, y, r, color, width = if (filled) 0.0 else 2.4)
        if (k <= 0) return
        val ax = x - r * 0.42; val ay = y + r * 0.02
        val bx = x - r * 0.1; val by = y + r * 0.34
        val ex = x + r * 0.46; val ey = y - r * 0.32
        val l1 = hypot(bx - ax, by - ay)
        val l2 = hypot(ex - bx, ey - by)
        val distance = M.clamp01(k) * (l1 + l2)
        val path = Path()
        path.moveTo(ax.toFloat(), ay.toFloat())
        if (distance <= l1) {
            path.lineTo(M.mix(ax, bx, distance / l1).toFloat(), M.mix(ay, by, distance / l1).toFloat())
        } else {
            path.lineTo(bx.toFloat(), by.toFloat())
            path.lineTo(M.mix(bx, ex, (distance - l1) / l2).toFloat(), M.mix(by, ey, (distance - l1) / l2).toFloat())
        }
        canvas.path(path, if (filled) "#FFFFFF" else color, width = r * 0.28)
    }
    private fun runner(canvas: AiFeatureTourCanvas, x: Double, y: Double, s: Double, color: String) {
        canvas.circle(x + 3 * s, y - 9 * s, 2.6 * s, color)
        for (v in arrayOf(doubleArrayOf(1.0, -5.0, -2.0, 3.0), doubleArrayOf(-2.0, 3.0, -7.0, 9.0),
            doubleArrayOf(-2.0, 3.0, 4.0, 6.0), doubleArrayOf(4.0, 6.0, 4.0, 11.0),
            doubleArrayOf(0.0, -3.0, -6.0, -1.0), doubleArrayOf(0.0, -3.0, 6.0, 0.0))) {
            canvas.line(x + v[0] * s, y + v[1] * s, x + v[2] * s, y + v[3] * s, color, 2.6 * s)
        }
    }
    private fun star(canvas: AiFeatureTourCanvas, x: Double, y: Double, r: Double, color: String, alpha: Double = 1.0) {
        val path = Path()
        path.moveTo(x.toFloat(), (y - r).toFloat())
        for (point in arrayOf(x + r to y, x to y + r, x - r to y, x to y - r)) {
            path.quadTo(x.toFloat(), y.toFloat(), point.first.toFloat(), point.second.toFloat())
        }
        path.close()
        canvas.path(path, color, alpha)
    }
    private fun calendar(canvas: AiFeatureTourCanvas, p: Double) {
        tile(canvas, -57.0, -50.0, 114.0, 108.0, 18.0, if (p >= 0) M.bump(p, 0.0, 1.0) else 0.0)
        canvas.saved {
            canvas.clipRound(-57, -50, 114, 108, 18)
            canvas.rect(-57, -50, 114, 26, C.calTop)
        }
        for (x in doubleArrayOf(-38.0, 38.0)) {
            canvas.round(x - 3, -60, 6, 20, 3, "#F3F5F6")
            canvas.round(x - 3, -60, 6, 20, 3, C.calRing, width = 2.0)
        }
        for (k in 0 until 28) {
            val x = -42.0 + (k % 7) * 14
            val y = -10.0 + (k / 7) * 14
            val blue = blueDays.indexOf(k)
            var scale = 1.0
            if (blue >= 0 && p >= 0) scale += 0.9 * M.bump(p, 0.08 + blue * 0.06, 0.22)
            canvas.circle(x, y, 3.2 * scale, if (blue >= 0) C.dotBlue else C.dotGrey)
            if (blue >= 0 && p >= 0) {
                val ring = M.bump(p, 0.1 + blue * 0.06, 0.3)
                if (ring > 0.01) canvas.circle(x, y, 3.2 + 7 * ring, C.dotBlue, 0.6 * ring, width = 1.6)
            }
        }
    }
    private fun trend(canvas: AiFeatureTourCanvas, p: Double) {
        tile(canvas, -57.0, -57.0, 114.0, 114.0, 20.0, if (p >= 0) M.bump(p, 0.0, 1.0) else 0.0)
        for (k in 1 until 4) {
            val v = -57.0 + k * 28
            canvas.line(v, -55, v, 55, C.grid, 1.5)
            canvas.line(-55, v, 55, v, C.grid, 1.5)
        }
        var progress = 1.0
        var dash = 1.0
        if (p >= 0) {
            progress = if (p < 0.1) 1 - p / 0.1 else M.easeOut((p - 0.12) / 0.45)
            dash = if (p < 0.1) 1 - p / 0.1 else M.smooth((p - 0.6) / 0.25)
        }
        val tx = M.mix(-40.0, 12.0, progress); val ty = M.mix(-28.0, 28.0, progress)
        canvas.line(-40, -28, tx, ty, C.greenLine, 7)
        canvas.circle(-40, -28, 7, "#FFFFFF")
        canvas.circle(-40, -28, 7, C.green, width = 4.0)
        if (dash > 0.01) {
            for (i in 0 until 4) {
                val u0 = i / 4.0
                val u1 = min(u0 + 0.12, dash)
                if (u0 > dash) break
                canvas.line(M.mix(12.0, 42.0, u0), M.mix(28.0, -12.0, u0), M.mix(12.0, 42.0, u1), M.mix(28.0, -12.0, u1), C.checkGrey, 4)
            }
            canvas.circle(42, -12, 7 * (if (p >= 0) 1 + 0.3 * M.bump(p, 0.8, 0.2) else 1.0), "#FFFFFF", dash)
            canvas.circle(42, -12, 7, C.checkGrey, dash, width = 4.0)
        }
        canvas.circle(tx, ty, 11, C.green)
        canvas.circle(tx, ty, 11, "#FFFFFF", width = 3.0)
    }
    private fun templates(canvas: AiFeatureTourCanvas, p: Double) {
        val out = if (p >= 0) M.bump(p, 0.05, 0.9) else 0.0
        val ck = if (p >= 0) { if (p < 0.35) 1 - M.smooth(p / 0.15) else M.smooth((p - 0.45) / 0.3) } else 1.0
        for ((y, alpha) in listOf(-42.0 to 0.85, 42.0 to 0.6)) {
            canvas.saved {
                canvas.translate(-4 * out, y + (if (y < 0) -6 else 6) * out)
                canvas.round(-56, -16, 112, 32, 10, C.tileShade, 0.25 * alpha, blur = 5.0)
                canvas.round(-56, -16, 112, 32, 10, "#FFFFFF", alpha)
                canvas.round(-56, -16, 112, 32, 10, C.tileEdge, alpha, width = 1.2)
                if (y < 0) {
                    canvas.circle(-38, -2, 3, "#9AA2AA", alpha)
                    canvas.line(-44, 4, -32, 4, "#9AA2AA", 2.2, alpha)
                } else canvas.line(-44, 2, -32, -3, "#B0B6BD", 2.2, alpha)
                canvas.round(-20, -6, 40, 5, 2.5, C.line, alpha)
                canvas.round(-20, 3, 28, 4, 2, C.line, alpha)
                check(canvas, 40.0, 0.0, 9.0, if (y < 0) "#9AA2AA" else C.checkGrey, 1.0, y < 0)
            }
        }
        canvas.saved {
            canvas.translate(30 * out, 0)
            canvas.scale(1 + 0.06 * out)
            canvas.round(-66, -20, 132, 40, 12, C.tileShade, 0.45, blur = 8.0)
            canvas.round(-66, -20, 132, 40, 12, "#FFFFFF")
            canvas.round(-66, -20, 132, 40, 12, C.tileEdge, width = 1.2)
            runner(canvas, -46.0, 0.0, 1.0, "#1E1E22")
            canvas.round(-26, -7, 46, 6, 3, C.line)
            canvas.round(-26, 4, 32, 5, 2.5, C.line)
            check(canvas, 46.0, 0.0, 11.0, C.check, ck, true)
        }
    }
    private fun avocado(canvas: AiFeatureTourCanvas, p: Double) {
        val next = if (p >= 0) M.bump(p, 0.55, 0.45) else 0.0
        canvas.saved {
            canvas.translate(20 + 14 * next, 28 - 4 * next)
            canvas.rotate(4 + 6 * next)
            tile(canvas, -38.0, -38.0, 76.0, 76.0, 14.0)
            canvas.circle(18, 4, 9, "#E9C9A8")
            canvas.circle(15, 1, 4, "#C49A70")
        }
        canvas.saved {
            canvas.translate(-12, -10)
            canvas.rotate(-2)
            tile(canvas, -48.0, -46.0, 96.0, 96.0, 16.0, if (p >= 0) M.bump(p, 0.0, 1.0) else 0.0)
            canvas.saved {
                canvas.rotate(-28)
                canvas.oval(-26, -34, 52, 66, C.avoSkin)
                canvas.oval(-21, -28, 42, 56, C.avoFlesh)
                canvas.radialOval(-18, -24, 36, 50, 0, 6, 24, C.avoInner, C.avoFlesh)
                canvas.radialOval(-12, -5, 24, 24, -4, 2, 14, "#C9783A", C.pit)
                canvas.circle(-4, 3, 3.5, "#FFFFFF", 0.5)
            }
            if (p >= 0) {
                val sy = M.mix(-46.0, 50.0, M.easeOut((p - 0.08) / 0.5))
                val sa = M.bump(p, 0.06, 0.56)
                if (sa > 0.01) {
                    canvas.saved {
                        canvas.clipRound(-48, -46, 96, 96, 16)
                        canvas.linear(-48, sy - 26, 96, 26, 0, sy - 26, 0, sy,
                            listOf("#8E7BFF", "#8E7BFF"), doubleArrayOf(0.0, 0.28 * sa), doubleArrayOf(0.0, 1.0))
                        canvas.line(-48, sy, 48, sy, "#7C68FF", 2.5, sa)
                    }
                }
                for (k in 0 until 3) {
                    val s = M.bump(p, 0.55 + k * 0.08, 0.35)
                    if (s > 0.01) star(canvas, doubleArrayOf(-38.0, 40.0, 30.0)[k], doubleArrayOf(-40.0, -30.0, 40.0)[k], 9 * s, "#8E7BFF", s)
                }
            }
        }
    }
    private fun clipboard(canvas: AiFeatureTourCanvas, p: Double) {
        tile(canvas, -48.0, -56.0, 96.0, 118.0, 12.0, if (p >= 0) M.bump(p, 0.0, 1.0) else 0.0)
        canvas.round(-26, -70, 52, 22, 9, "#C8CBD0")
        canvas.circle(0, -66, 5, "#E8EAED")
        for (k in 0 until 3) {
            val y = -20.0 + k * 30
            var draw = 1.0
            if (p >= 0) draw = if (p < 0.12) 1 - p / 0.12 else M.easeOut((p - (0.18 + k * 0.2)) / 0.22)
            val alpha = M.clamp01(draw * 1.5)
            if (k == 0) runner(canvas, -28.0, y, 0.75, C.runner)
            else check(canvas, -28.0, y, 7.0, "#B4BAC1", draw, false)
            canvas.round(-12, y - 6, 44 * draw, 4.5, 2, "#C4C9CF", alpha)
            canvas.round(-12, y + 3, 32 * draw, 4, 2, "#D9DDE1", alpha)
        }
    }
    private fun icon(canvas: AiFeatureTourCanvas, i: Int, p: Double) {
        when (i) {
            0 -> calendar(canvas, p)
            1 -> trend(canvas, p)
            2 -> templates(canvas, p)
            3 -> avocado(canvas, p)
            else -> clipboard(canvas, p)
        }
    }
    fun draw(nativeCanvas: android.graphics.Canvas, width: Float, height: Float, frame: M.Frame) {
        val canvas = drawing
        canvas.draw(nativeCanvas) {
            val scale = width / AiFeatureTourTheme.REF_W * AiFeatureTourTheme.S
            val h = height / scale
            canvas.scale(scale)
            canvas.linear(0, 0, 720, h, 0, 0, 0, 520, listOf(C.bgTop, C.bgMid, C.bg), doubleArrayOf(1.0, 1.0, 1.0), doubleArrayOf(0.0, 0.45, 1.0))
            canvas.radial(0, 600, 720, max(0.0, h - 600), 360, 1250, 520, listOf(C.glow, C.glow), doubleArrayOf(0.9, 0.0))
            canvas.text(AiFeatureTourTheme.title, UI.title.x, UI.title.y, "Bold", 42.0, C.title, ax = 0.5)
            val rows = UI.rows
            val button = UI.button
            val bcx = button.x + button.w / 2
            val bcy = button.y + button.h / 2
            for (i in AiFeatureTourTheme.rows.indices) {
                val ty = rows.y0 + i * rows.pitch
                val iy = ty + 42
                val fade = if (i == 5) 0.35 else 1.0
                val row = AiFeatureTourTheme.rows[i]
                val tw = canvas.textWidth(row.first, "SemiBold", 30.0)
                canvas.text(row.first, rows.textX, ty, "SemiBold", 30.0, C.rowTitle, fade)
                // while its icon acts, a violet light runs along the row's title
                val act0 = if (i < 5) frame.act[i] else -1.0
                if (act0 >= 0) {
                    val gx = rows.textX - 80 + (tw + 160) * M.smooth(act0 / 0.8)
                    canvas.textGradient(row.first, rows.textX, ty, "SemiBold", 30.0, gx - 70, gx + 70, fade)
                }
                for (l in 0 until 2) canvas.text(row.second[l], rows.textX, ty + rows.sub[l], "Regular", 27.0, C.sub, fade)
                if (i == 5) {
                    canvas.saved {
                        canvas.translate(rows.iconX + 20, iy)
                        canvas.circle(-4, -6, 26, "#C9CCD3", width = 9.0)
                        canvas.line(15, 13, 34, 32, "#C9CCD3", 11)
                    }
                    continue
                }
                var x = rows.iconX
                var y = iy
                var size = 1.0
                var rotation = 0.0
                var alpha = 1.0
                if (frame.fly >= 0) {
                    val fl = M.flight(i, frame.fly)
                    if (fl.gone) continue
                    if (fl.back > 0) {
                        size = M.mix(0.55, 1.0, fl.back)
                        alpha = M.clamp01(fl.back * 1.6)
                    } else if (fl.out > 0) {
                        val e = fl.out
                        val cx = rows.iconX + 260
                        val cy = M.mix(iy, bcy, 0.2) - 120
                        x = (1 - e) * (1 - e) * rows.iconX + 2 * (1 - e) * e * cx + e * e * bcx
                        y = (1 - e) * (1 - e) * iy + 2 * (1 - e) * e * cy + e * e * bcy
                        size = M.mix(1.0, 0.25, e)
                        rotation = 40 * e * (if (i % 2 == 0) 1 else -1)
                        alpha = 1 - M.smooth((e - 0.75) / 0.25)
                    }
                }
                val act = frame.act[i]
                if (act >= 0) size *= 1 + 0.05 * M.bump(act, 0.0, 1.0)
                canvas.saved {
                    canvas.translate(x, y)
                    canvas.rotate(rotation)
                    canvas.scale(size)
                    canvas.layer(if (alpha < 0.999) alpha else 1.0) { icon(canvas, i, act) }
                }
            }
            canvas.linear(0, button.y - 70, 720, max(0.0, h - button.y + 70), 0, button.y - 70, 0, button.y + 30,
                listOf(C.bg, C.bg), doubleArrayOf(0.0, 0.9), doubleArrayOf(0.0, 1.0))
            val sp = if (frame.fly >= 0) frame.fly - (M.T.sparkle - M.T.press) else -1.0
            val glow = if (sp >= 0) M.bump(sp, 0.0, 0.9) else 0.0
            canvas.saved {
                canvas.translate(bcx, bcy)
                canvas.scale(1 - 0.04 * frame.press + 0.02 * glow)
                if (glow > 0.01) canvas.round(-button.w / 2 - 6, -button.h / 2 - 6, button.w + 12, button.h + 12, button.h / 2 + 6, C.sparkle, 0.7 * glow, blur = 18.0)
                canvas.round(-button.w / 2, -button.h / 2, button.w, button.h, button.h / 2, C.button)
                val tw = canvas.textWidth(AiFeatureTourTheme.button, "SemiBold", 34.0)
                canvas.text(AiFeatureTourTheme.button, -(tw + 50) / 2, 0.0, "SemiBold", 34.0, "#FFFFFF")
                val ax = -(tw + 50) / 2 + tw + 18 + 6 * glow
                canvas.line(ax, 0, ax + 32, 0, "#FFFFFF", 3.6)
                canvas.line(ax + 20, -12, ax + 32, 0, "#FFFFFF", 3.6)
                canvas.line(ax + 20, 12, ax + 32, 0, "#FFFFFF", 3.6)
            }
            if (sp >= 0 && sp < 1.1) {
                val k = M.easeOut(sp / 0.35) * (1 - M.smooth((sp - 0.55) / 0.5))
                if (k > 0.01) {
                    canvas.saved {
                        canvas.translate(bcx, bcy - 2)
                        canvas.rotate(90 * sp)
                        star(canvas, 0.0, 0.0, 46 * k, C.sparkle, 0.95 * k)
                        star(canvas, 0.0, 0.0, 24 * k, "#FFFFFF", k)
                    }
                    for (i in 0 until 8) {
                        val angle = i / 8.0 * PI * 2 + 0.3
                        val distance = 40 + 120 * M.easeOut(sp / 0.8)
                        star(canvas, bcx + cos(angle) * distance * 1.6, bcy + sin(angle) * distance * 0.55,
                            8 * (1 - M.smooth(sp / 0.9)), if (i % 2 != 0) "#FFFFFF" else C.sparkle, 1 - M.smooth(sp / 0.9))
                    }
                }
            }
        }
    }
}
