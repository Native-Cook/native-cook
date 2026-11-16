package com.ngocdevv.nativecook.blocks.modals.gelcubeunbox

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.ngocdevv.nativecook.blocks.modals.gelcubeunbox.GelCubeUnboxMotion as M
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Below API 33 there is no shader: the same picture built from gradients and paths in the reference's 402 x 874 design units (the canvas is
 * translated to the design origin and scaled by S). The floor (the page, its haze, the shadow, the caustic and the reflection), the gel cube as three
 * lit faces with the core glowing through them, the lid that swings up about its back edge and shows its underside, the fan of light that falls
 * across the floor, and the glass pill, sheet, chip, close cross and clock. No refraction, absorption or ray-boxed bevels.
 */
internal class GelCubeUnboxFallback {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val rect = RectF()

    private fun col(c: FloatArray, a: Double = 1.0, k: Double = 1.0): Int = Color.argb(
        (a.coerceIn(0.0, 1.0) * 255).toInt(), (c[0] * k * 255).coerceIn(0.0, 255.0).toInt(), (c[1] * k * 255).coerceIn(0.0, 255.0).toInt(),
        (c[2] * k * 255).coerceIn(0.0, 255.0).toInt(),
    )

    private fun mix(a: FloatArray, b: FloatArray, t: Double) = floatArrayOf(
        (a[0] + (b[0] - a[0]) * t).toFloat(), (a[1] + (b[1] - a[1]) * t).toFloat(), (a[2] + (b[2] - a[2]) * t).toFloat(),
    )

    /** a soft gaussian blob: an ellipse of radii (rx, ry) around (cx, cy), `color` at `alpha` in the middle falling to nothing */
    private fun blob(c: Canvas, cx: Double, cy: Double, rx: Double, ry: Double, color: Int, alpha: Double) {
        if (alpha <= 0.002) return
        val r = 2.2f
        val a = Color.alpha(color) / 255.0 * alpha
        fun stop(k: Double) = Color.argb((a * k * 255).coerceIn(0.0, 255.0).toInt(), Color.red(color), Color.green(color), Color.blue(color))
        c.save()
        c.translate(cx.toFloat(), cy.toFloat())
        c.scale(rx.toFloat(), ry.toFloat())
        fill.shader = RadialGradient(
            0f, 0f, r, intArrayOf(stop(1.0), stop(0.78), stop(0.37), stop(0.105), stop(0.018), stop(0.0)),
            floatArrayOf(0f, 0.5f / r, 1f / r, 1.5f / r, 2f / r, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRect(-r, -r, r, r, fill)
        fill.shader = null
        c.restore()
    }

    private fun poly(c: Canvas, p: Paint, vararg xy: Double) {
        path.rewind()
        path.moveTo(xy[0].toFloat(), xy[1].toFloat())
        var i = 2
        while (i < xy.size) {
            path.lineTo(xy[i].toFloat(), xy[i + 1].toFloat())
            i += 2
        }
        path.close()
        c.drawPath(path, p)
    }

    private fun faceGradient(x0: Double, y0: Double, x1: Double, y1: Double, a: Int, b: Int) {
        fill.shader = LinearGradient(x0.toFloat(), y0.toFloat(), x1.toFloat(), y1.toFloat(), a, b, Shader.TileMode.CLAMP)
    }

    fun draw(c: Canvas, w: Float, h: Float, ox: Double, oy: Double, s: Double, flags: Int, u: GelCubeUnboxUniforms, pal: GelCubeUnboxTheme.Palette) {
        val progress = M.clamp01(u.state[0].toDouble())
        val core = u.state[1].toDouble()
        val fan = u.state[2].toDouble()
        val caustic = u.state[3].toDouble()
        val flash = u.optics[1].toDouble()
        val angle = u.optics[0].toDouble()
        val showClose = flags and 1 != 0
        val showDetails = flags and 2 != 0
        // the page: a vertical gradient over the design height, a little deeper at the top
        val top = pal.page.map { it * 0.94f }.toFloatArray()
        val bottom = floatArrayOf(pal.page[0] + 0.023f, pal.page[1] + 0.029f, pal.page[2] + 0.048f)
        fill.shader = LinearGradient(0f, oy.toFloat(), 0f, (oy + 874 * s).toFloat(), col(top), col(bottom), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
        c.save()
        c.translate(ox.toFloat(), oy.toFloat())
        c.scale(s.toFloat(), s.toFloat())
        val floorC = col(pal.floor)
        // the floor band, the haze to the left, the cube's shadow to the right, the contact line
        fill.shader = LinearGradient(
            0f, 0f, 0f, 874f, intArrayOf(Color.TRANSPARENT, col(pal.floor, 0.29 * 0.6), col(pal.floor, 0.29), col(pal.floor, 0.29 * 0.6), Color.TRANSPARENT),
            floatArrayOf(0f, 289f / 874 - 0.14f, 289f / 874, 289f / 874 + 0.14f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRect(-ox.toFloat() / s.toFloat(), 0f, (w / s).toFloat(), 874f, fill)
        fill.shader = null
        blob(c, 105.0, 282.0, 245.0, 73.0, floorC, 0.82)
        blob(c, 330.0, 315.0, 118.0, 23.0, Color.argb(255, 0, 0, 0), 0.42)
        blob(c, 211.0, 330.0, 83.0, 6.0, Color.argb(255, 0, 0, 0), 0.16)
        blob(c, 240.0, 316.0, 74 + 34 * fan, 16 + 9 * fan, floorC, 0.25 * caustic * (1 + 0.5 * fan))
        blob(c, 190.0, 355.0, 42.0, 49.0, floorC, 0.32 * caustic)
        // the fan of light: from the cube's opening down across the floor, its front running ahead of the lid
        val p1 = M.clamp01(fan)
        if (p1 > 0.01) {
            val front = 340.0 + 520.0 * Math.pow(p1, 0.8)
            val alpha = 0.55 * M.smooth(0.0, 0.07, p1) * (1 + flash)
            val wTop = 35.0
            val wBot = 35.0 + 0.42 * max(0.0, front - 290.0)
            fill.shader = LinearGradient(
                0f, 276f, 0f, front.toFloat(), intArrayOf(Color.TRANSPARENT, col(pal.fan, alpha * 0.8), col(pal.fan, alpha * 0.55), Color.TRANSPARENT),
                floatArrayOf(0f, 0.12f, 0.75f, 1f), Shader.TileMode.CLAMP,
            )
            poly(c, fill, 199 - wTop, 276.0, 199 + wTop, 276.0, 199 + wBot, front, 199 - wBot, front)
            fill.shader = null
        }
        cube(c, progress, core, angle, flash, pal)
        // the glass pill, sheet, chip, close cross and clock: the same incident light as the floor
        val lightP = M.smooth(0.24, 0.32, progress)
        val lightS = M.smooth(0.37, 0.45, progress)
        val coreC = pal.core
        // pill
        rect.set(16f, 497f, 386f, 555f)
        fill.color = col(coreC, 0.1 + 0.12 * lightP)
        c.drawRoundRect(rect, 29f, 29f, fill)
        line.shader = LinearGradient(0f, 497f, 0f, 555f, col(coreC, 0.35 + 0.35 * lightP), col(coreC, (0.35 + 0.35 * lightP) * 0.25), Shader.TileMode.CLAMP)
        line.strokeWidth = 1.2f
        c.drawRoundRect(rect, 29f, 29f, line)
        line.shader = null
        // sheet
        rect.set(0f, 596f, 402f, 1204f)
        fill.color = col(pal.underside, 0.05)
        c.drawRoundRect(rect, 48f, 48f, fill)
        line.shader = LinearGradient(0f, 596f, 0f, 640f, col(coreC, 0.35 + 0.4 * lightS), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        line.strokeWidth = 1.2f
        c.drawRoundRect(rect, 48f, 48f, line)
        line.shader = null
        fill.shader = LinearGradient(0f, 596f, 0f, 640f, col(coreC, 0.16 * lightS), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, 48f, 48f, fill)
        fill.shader = null
        if (showDetails) {
            rect.set(149f, 629f, 253f, 663f)
            fill.color = col(coreC, 0.025)
            c.drawRoundRect(rect, 17f, 17f, fill)
            line.shader = LinearGradient(0f, 629f, 0f, 663f, col(coreC, 0.65), col(coreC, 0.2), Shader.TileMode.CLAMP)
            line.strokeWidth = 1f
            c.drawRoundRect(rect, 17f, 17f, line)
            line.shader = null
            // the clock: a ring and two hands
            val clock = col(mix(pal.core, floatArrayOf(pal.page[0] * 0.58f, pal.page[1] * 0.58f, pal.page[2] * 0.58f), lightS))
            line.color = clock
            line.strokeWidth = 1.1f
            c.drawCircle(174f, 646f, 6.4f, line)
            c.drawLine(174f, 646f, 174f, 644f, line)
            c.drawLine(174f, 646f, 175.6f, 646f, line)
        }
        if (showClose) {
            rect.set(340f, 44f, 384f, 88f)
            line.shader = LinearGradient(340f, 88f, 384f, 44f, col(coreC, 0.85), col(coreC, 0.15), Shader.TileMode.CLAMP)
            line.strokeWidth = 1.2f
            c.drawOval(rect, line)
            line.shader = null
            line.color = col(coreC)
            line.strokeWidth = 1.7f
            c.drawLine(354.5f, 58.5f, 369.5f, 73.5f, line)
            c.drawLine(369.5f, 58.5f, 354.5f, 73.5f, line)
        }
        c.restore()
    }

    /**
     * The cube in design units: three faces of a box (top A B C D, left A D .. , right D C ..), the core glowing through them, then the lid.
     * Vertices: A (86,154) left, B (185,129) back, C (305,152) right, D (211,178) front; floor corners (86,308), (211,340), (305,306).
     */
    private fun cube(c: Canvas, progress: Double, core: Double, angle: Double, flash: Double, pal: GelCubeUnboxTheme.Palette) {
        val open = progress > 0.001
        val dy = 22.7 * M.smooth(0.0, 0.04, progress) // the body is 14 % lower once the lid is its own slab
        val ax = 86.0
        val ay = 154.0 + dy
        val bx = 185.0
        val by = 129.0 + dy
        val cx = 305.0
        val cy = 152.0 + dy
        val dx = 211.0
        val dyy = 178.0 + dy
        val tintC = pal.tint
        val left = mix(tintC, pal.edge, 0.1)
        val topC = mix(tintC, pal.core, 0.32)
        val right = floatArrayOf(tintC[0] * 0.78f, tintC[1] * 0.78f, tintC[2] * 0.8f)
        // body silhouette, for the clip of the glow
        val silhouette = Path().apply {
            moveTo(ax.toFloat(), ay.toFloat()); lineTo(bx.toFloat(), by.toFloat()); lineTo(cx.toFloat(), cy.toFloat()); lineTo(305f, 306f)
            lineTo(211f, 340f); lineTo(86f, 308f); close()
        }
        // faces
        faceGradient(86.0, ay, 211.0, 340.0, col(left, 0.96, 1.05), col(left, 0.96, 0.7))
        poly(c, fill, ax, ay, dx, dyy, 211.0, 340.0, 86.0, 308.0)
        faceGradient(305.0, cy, 211.0, 340.0, col(right, 0.96, 1.05), col(right, 0.96, 0.72))
        poly(c, fill, dx, dyy, cx, cy, 305.0, 306.0, 211.0, 340.0)
        faceGradient(bx, by, dx, dyy, col(topC, 0.96), col(topC, 0.96, 0.86))
        poly(c, fill, ax, ay, bx, by, cx, cy, dx, dyy)
        fill.shader = null
        // the core glowing through the gel (brighter with the lid up), clipped to the body
        c.save()
        c.clipPath(silhouette)
        blob(c, 196.0, 250.0, 76.0, 70.0, col(pal.core), 0.62 * core * (1 - 0.18 * progress))
        blob(c, 196.0, 300.0, 100.0, 34.0, col(pal.tint, 1.0, 0.6), 0.35)
        c.restore()
        // an open top: the inner pedestal that shows once the lid has left
        if (open) {
            val inner = floatArrayOf(tintC[0] * 0.42f, tintC[1] * 0.53f, tintC[2] * 0.75f)
            fill.color = col(inner, 0.78 * M.smooth(0.0, 0.18, progress))
            poly(c, fill, ax + 12, ay + 1, bx, by + 7, cx - 12, cy + 1, dx, dyy - 6)
            fill.color = col(pal.core, 0.3 * core * progress)
            poly(c, fill, ax + 28, ay + 2, bx, by + 14, cx - 28, cy + 2, dx, dyy - 14)
        }
        // bevels and the vertical front edge
        line.shader = null
        line.color = col(pal.edge, 0.55)
        line.strokeWidth = 1.2f
        for ((x0, y0, x1, y1) in listOf(
            doubleArrayOf(ax, ay, dx, dyy), doubleArrayOf(dx, dyy, cx, cy), doubleArrayOf(bx, by, cx, cy), doubleArrayOf(ax, ay, bx, by),
            doubleArrayOf(ax, ay, 86.0, 308.0), doubleArrayOf(cx, cy, 305.0, 306.0), doubleArrayOf(dx, dyy, 211.0, 340.0),
        )) c.drawLine(x0.toFloat(), y0.toFloat(), x1.toFloat(), y1.toFloat(), line)
        line.color = col(tintC, 0.5, 0.6)
        c.drawLine(86f, 308f, 211f, 340f, line)
        c.drawLine(211f, 340f, 305f, 306f, line)
        if (!open) {
            // the seal: a light leak along the front-left top edge
            line.color = col(pal.edge, 0.12 * (1 + flash) * 2.2)
            line.strokeWidth = 1.6f
            c.drawLine(86f, 154f, 211f, 177f, line)
        }
        if (!open) return
        // the lid: a slab hinged on the body's back top edge, its front edge swinging up and back by `angle`
        val sn = sin(angle)
        val cs = cos(angle)
        val zx = -99.0
        val zy = 25.0
        val tx = 132.0 * 0.14 * sn
        val ty = -(33.3 * 0.14 * sn) - 162.0 * 0.14 * cs
        val hbx = bx
        val hby = by
        val hcx = cx
        val hcy = cy
        // lid bottom front edge
        val lax = hbx + cs * zx
        val lay = hby + cs * zy - 121.5 * sn
        val ldx = lax + (cx - bx)
        val ldy = lay + (cy - by)
        // lid top = bottom + thickness
        val tax = lax + tx
        val tay = lay + ty
        val tdx = ldx + tx
        val tdy = ldy + ty
        val tbx = hbx + tx
        val tby = hby + ty
        val tcx = hcx + tx
        val tcy = hcy + ty
        val facing = M.smooth(0.2, 0.55, angle / (PI / 180) / 58.0) // past ~20 degrees the underside is the face that shows
        val topLid = mix(tintC, pal.core, 0.25)
        val under = mix(mix(tintC, pal.underside, 0.5), pal.core, 0.25 * core * facing)
        // underside (the slab's bottom: hinge edge to the lifted front edge)
        if (facing > 0.01) {
            faceGradient(hbx, hby, lax, lay, col(under, 0.92 * facing), col(under, 0.92 * facing, 0.72))
            poly(c, fill, hbx, hby, hcx, hcy, ldx, ldy, lax, lay)
            fill.shader = null
        }
        // front thickness and left side
        fill.color = col(mix(tintC, pal.edge, 0.2), 0.95, 0.9)
        poly(c, fill, lax, lay, ldx, ldy, tdx, tdy, tax, tay)
        fill.color = col(mix(tintC, pal.edge, 0.2), 0.95, 0.78)
        poly(c, fill, lax, lay, hbx, hby, tbx, tby, tax, tay)
        // top surface
        faceGradient(tbx, tby, tdx, tdy, col(topLid, 0.96 * (1 - 0.5 * facing)), col(topLid, 0.96 * (1 - 0.5 * facing), 0.88))
        poly(c, fill, tax, tay, tbx, tby, tcx, tcy, tdx, tdy)
        fill.shader = null
        line.color = col(pal.edge, 0.6)
        line.strokeWidth = 1.2f
        for ((x0, y0, x1, y1) in listOf(
            doubleArrayOf(tax, tay, tdx, tdy), doubleArrayOf(tdx, tdy, tcx, tcy), doubleArrayOf(tbx, tby, tcx, tcy), doubleArrayOf(tax, tay, tbx, tby),
            doubleArrayOf(lax, lay, ldx, ldy), doubleArrayOf(lax, lay, tax, tay), doubleArrayOf(ldx, ldy, tdx, tdy),
        )) c.drawLine(x0.toFloat(), y0.toFloat(), x1.toFloat(), y1.toFloat(), line)
    }
}
