package com.ngocdevv.nativecook.blocks.onboarding.objectsignindissolve

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.ngocdevv.nativecook.blocks.onboarding.objectsignindissolve.ObjectSigninDissolveMotion as M
import com.ngocdevv.nativecook.blocks.onboarding.objectsignindissolve.ObjectSigninDissolveTheme as Theme
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Below API 33 there is no AGSL, so the pear is drawn with paths and gradients: the same silhouette as the shader's signed-distance
 * pear (a bulb, a shoulder, a pinched waist, a leaning stalk), the skin lit from the upper left with russet round the stalk and freckles
 * that turn with the yaw, and above the screen's cutting plane the same neon shell of scan lines (violet high up, magenta, the seam's
 * yellow at the cut) with the 3 pt seam, banked by the finger. The theme wipe is a disc: inside it the pear is lit for the light page.
 * Coordinates are the pear's own points (the canvas is scaled by the layout scale), y down, origin at the pear's centre.
 */
internal class ObjectSigninDissolveFallback {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val sil = Path()
    private val stalk = Path()
    private val region = Path()
    private val wipe = Path()
    private val oval = RectF()

    private class Freckle(val theta: Double, val y: Float, val half: Float, val r: Float)

    private val freckles: List<Freckle>

    init {
        val r1 = 78f
        val r2 = 33f
        val hh = 198f
        val yb = 76f
        val b = (r1 - r2) / hh
        val a = sqrt(1 - b * b)
        val phi = Math.toDegrees(asin(b.toDouble())).toFloat()
        val bulbX = r1 * a
        val bulbY = yb - r1 * b
        val shX = r2 * a
        val shY = yb - hh - r2 * b
        // bulb, clockwise from its right tangent to its left tangent; the pinched left side; the shoulder; the pinched right side
        oval.set(-r1, yb - r1, r1, yb + r1)
        sil.arcTo(oval, -phi, 180f + 2 * phi, true)
        sil.quadTo(-38f, -35f, -shX, shY)
        oval.set(-r2, yb - hh - r2, r2, yb - hh + r2)
        sil.arcTo(oval, 180f + phi, 180f - 2 * phi, false)
        sil.quadTo(38f, -35f, bulbX, bulbY)
        sil.close()
        stalk.moveTo(0f, -146f)
        stalk.lineTo(7f, -170f)
        stalk.lineTo(18f, -190f)

        // half width of the silhouette at height y, for freckles on the cylinder
        fun half(y: Float): Float {
            if (y >= bulbY) return sqrt(max(r1 * r1 - (y - yb) * (y - yb), 0f))
            if (y <= shY) return sqrt(max(r2 * r2 - (y + 122f) * (y + 122f), 0f))
            var lo = 0f
            var hi = 1f
            for (i in 0 until 14) {
                val t = (lo + hi) / 2
                val yy = (1 - t) * (1 - t) * bulbY + 2 * t * (1 - t) * -35f + t * t * shY
                if (yy > y) lo = t else hi = t
            }
            val t = (lo + hi) / 2
            return (1 - t) * (1 - t) * bulbX + 2 * t * (1 - t) * 38f + t * t * shX
        }
        var seed = 12345L
        fun rnd(): Double {
            seed = (seed * 1103515245L + 12345L) and 0x7fffffffL
            return seed / 2147483647.0
        }
        freckles = List(90) {
            val y = (-138 + rnd() * 288).toFloat()
            Freckle(rnd() * 2 * PI, y, half(y), (0.9 + rnd() * 0.9).toFloat())
        }
    }

    private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun lerpColor(a: Int, b: Int, t: Float, alpha: Float = 1f): Int = Color.argb(
        (255 * alpha).toInt().coerceIn(0, 255),
        mix(Color.red(a).toFloat(), Color.red(b).toFloat(), t).toInt(),
        mix(Color.green(a).toFloat(), Color.green(b).toFloat(), t).toInt(),
        mix(Color.blue(a).toFloat(), Color.blue(b).toFloat(), t).toInt(),
    )

    private fun shellRgb(): Int = Color.rgb(
        (Theme.shell[0] * 255).toInt(), (Theme.shell[1] * 255).toInt(), (Theme.shell[2] * 255).toInt(),
    )
    private fun seamRgb(): Int = Color.rgb(
        (Theme.seam[0] * 255).toInt(), (Theme.seam[1] * 255).toInt(), (Theme.seam[2] * 255).toInt(),
    )
    private fun coreRgb(): Int = Color.rgb(
        (Theme.seamCore[0] * 255).toInt(), (Theme.seamCore[1] * 255).toInt(), (Theme.seamCore[2] * 255).toInt(),
    )

    /**
     * The pear. `w`: screen width; `pearCy`: its centre y; `s`: the layout scale; `screenH`: the screen height; `wipeR`: the disc's radius;
     * `lightMix`: Reduce Motion's cross-fade; `reduce`: whether the disc is a cross-fade.
     */
    fun draw(
        c: Canvas,
        w: Float,
        pearCy: Float,
        s: Float,
        screenH: Float,
        k: ObjectSigninDissolveLook,
        clock: Double,
        wipeR: Float,
        lightMix: Float,
        reduce: Boolean,
    ) {
        drawVariant(c, w, pearCy, s, k, clock, false)
        if (reduce) {
            if (lightMix > 0.01f) {
                val count = c.saveLayerAlpha(0f, 0f, w, screenH, (255 * lightMix.coerceIn(0f, 1f)).toInt())
                drawVariant(c, w, pearCy, s, k, clock, true)
                c.restoreToCount(count)
            }
        } else if (wipeR > 0.5f) {
            c.save()
            wipe.rewind()
            wipe.addCircle(M.WIPE_CX.toFloat(), screenH + M.WIPE_DROP.toFloat(), wipeR, Path.Direction.CW)
            c.clipPath(wipe)
            drawVariant(c, w, pearCy, s, k, clock, true)
            c.restore()
        }
    }

    private fun drawVariant(c: Canvas, w: Float, pearCy: Float, s: Float, k: ObjectSigninDissolveLook, clock: Double, light: Boolean) {
        val save = c.save()
        c.translate(w / 2, pearCy)
        c.scale(s * k.scale.toFloat(), s * k.scale.toFloat())
        val planeLocal = M.planeY(k.c).toFloat()
        val slope = tan(k.roll).toFloat()
        val yaw = Math.toRadians(k.yaw)

        // ---- skin, below the plane ----
        region.rewind()
        region.moveTo(-260f, planeLocal - 260f * slope)
        region.lineTo(260f, planeLocal + 260f * slope)
        region.lineTo(260f, 400f)
        region.lineTo(-260f, 400f)
        region.close()
        c.save()
        c.clipPath(region)
        c.save()
        c.rotate(6f)
        drawSkin(c, k, yaw, light)
        c.restore()
        c.restore()

        // ---- shell, above the plane ----
        if (k.seam > 0.001) {
            region.rewind()
            region.moveTo(-260f, planeLocal - 260f * slope)
            region.lineTo(260f, planeLocal + 260f * slope)
            region.lineTo(260f, -400f)
            region.lineTo(-260f, -400f)
            region.close()
            c.save()
            c.clipPath(region)
            drawShell(c, k, clock, planeLocal, light, slope)
            c.restore()
            drawSeam(c, k, planeLocal, slope)
        }
        c.restoreToCount(save)
    }

    private fun drawSkin(c: Canvas, k: ObjectSigninDissolveLook, yaw: Double, light: Boolean) {
        val hi = if (light) Color.rgb(0xF4, 0xE8, 0xA0) else Color.rgb(0xE9, 0xDC, 0x8A)
        val mid = if (light) Color.rgb(0xCD, 0xC2, 0x5C) else Color.rgb(0xB7, 0xB1, 0x4B)
        val shade = if (light) Color.rgb(0x91, 0x8F, 0x3E) else Color.rgb(0x58, 0x64, 0x2A)
        val rim = if (light) Color.rgb(0x6B, 0x5A, 0x3C) else Color.rgb(0x2B, 0x21, 0x45)
        val hx = -26f + (k.key * 100).toFloat()
        fill.style = Paint.Style.FILL
        fill.shader = RadialGradient(hx, -34f, 170f, intArrayOf(hi, mid, shade, rim), floatArrayOf(0f, 0.36f, 0.8f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(sil, fill)
        // the kicker from the lower right, in the page's aubergine
        fill.shader = RadialGradient(
            64f, 76f, 96f,
            intArrayOf(if (light) Color.argb(60, 0xCC, 0x9E, 0x7E) else Color.argb(90, 0x4A, 0x34, 0x7A), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP,
        )
        c.drawPath(sil, fill)
        c.save()
        c.clipPath(sil)
        // russet round the stalk and the calyx
        fill.shader = RadialGradient(
            4f, -146f, 54f,
            intArrayOf(Color.argb(215, 0x7A, 0x52, 0x30), Color.TRANSPARENT), null, Shader.TileMode.CLAMP,
        )
        c.drawCircle(4f, -146f, 54f, fill)
        fill.shader = RadialGradient(
            0f, 150f, 36f,
            intArrayOf(Color.argb(170, 0x7A, 0x52, 0x30), Color.TRANSPARENT), null, Shader.TileMode.CLAMP,
        )
        c.drawCircle(0f, 150f, 36f, fill)
        // freckles that turn with the pear
        fill.shader = null
        for (f in freckles) {
            val a = f.theta + yaw
            val depth = cos(a)
            if (depth < 0.08) continue
            fill.color = Color.argb((150 * min(1.0, depth * 1.4)).toInt(), 0x5C, 0x38, 0x1A)
            val squash = max(0.25f, depth.toFloat())
            c.save()
            c.translate((f.half * sin(a)).toFloat(), f.y)
            c.scale(squash, 1f)
            c.drawCircle(0f, 0f, f.r, fill)
            c.restore()
        }
        // the soft specular on the cheek, and a darker edge (Fresnel)
        fill.shader = RadialGradient(
            hx, -34f, 34f,
            intArrayOf(Color.argb(if (light) 70 else 96, 255, 252, 236), Color.TRANSPARENT), null, Shader.TileMode.CLAMP,
        )
        c.drawCircle(hx, -34f, 34f, fill)
        fill.shader = null
        stroke.shader = null
        stroke.color = Color.argb(if (light) 70 else 110, Color.red(rim), Color.green(rim), Color.blue(rim))
        stroke.strokeWidth = 12f
        c.drawPath(sil, stroke)
        c.restore()
        // the woody stalk
        stroke.color = Color.rgb(0x54, 0x36, 0x1C)
        stroke.strokeWidth = 6f
        c.drawPath(stalk, stroke)
        stroke.color = Color.argb(120, 0xA8, 0x7A, 0x44)
        stroke.strokeWidth = 1.6f
        c.save()
        c.translate(-1f, 0f)
        c.drawPath(stalk, stroke)
        c.restore()
    }

    private fun drawShell(c: Canvas, k: ObjectSigninDissolveLook, clock: Double, planeLocal: Float, light: Boolean, slope: Float) {
        val shell = shellRgb()
        val seam = seamRgb()
        val cool = Color.rgb(
            (Color.red(shell) * 0.72f).toInt(), (Color.green(shell) * 0.42f).toInt(), min(255, (Color.blue(shell) * 1.0f).toInt()),
        )
        // neon bloom round the shell's outline, strongest at the cut
        c.save()
        c.rotate(6f)
        stroke.shader = null
        for ((width, alpha) in arrayOf(18f to 0.07f, 10f to 0.12f, 5f to 0.22f)) {
            stroke.color = lerpColor(shell, seam, 0.15f, alpha * k.seam.toFloat())
            stroke.strokeWidth = width
            c.drawPath(sil, stroke)
        }
        // a faint fill so the form still reads, a stronger rim
        c.save()
        c.clipPath(sil)
        fill.shader = null
        fill.color = lerpColor(Color.BLACK, shell, 0.45f, if (light) 0.32f else 0.2f)
        c.drawPath(sil, fill)
        stroke.color = lerpColor(shell, cool, 0.2f, 0.4f)
        stroke.strokeWidth = 10f
        c.drawPath(sil, stroke)
        c.restore()
        c.restore()

        // the scan lines: constant world Y, drifting up; the far wall's lines half a pitch off, dimmer, so it reads as depth
        c.save()
        c.rotate(6f)
        c.clipPath(sil)
        c.rotate(-6f)
        val pitch = 5f
        val offset = ((clock * 8) % pitch).toFloat()
        stroke.strokeCap = Paint.Cap.BUTT
        stroke.strokeWidth = 1.2f
        stroke.color = lerpColor(cool, Color.BLACK, 0.3f, 0.34f)
        var y = -200f - offset + pitch * 0.5f
        while (y < planeLocal + 260f * kotlin.math.abs(slope)) {
            c.drawLine(-130f, y, 130f, y, stroke)
            y += pitch
        }
        y = -200f - offset
        while (y < planeLocal + 260f * kotlin.math.abs(slope)) {
            val up = max(planeLocal - y, 0f)
            val heat = exp(-up / 46f)
            val near = exp(-up / 9f)
            val sh = lerpColor(cool, shell, smooth(0f, 0.7f, min(1f, heat + 0.3f)))
            val col = lerpColor(sh, seam, (near * 0.85f + 0.3f * heat * heat).coerceIn(0f, 1f), 0.95f * (0.4f + 0.6f * exp(-up / 110f)))
            stroke.color = col
            c.drawLine(-130f, y, 130f, y, stroke)
            y += pitch
        }
        stroke.strokeCap = Paint.Cap.ROUND
        c.restore()
    }

    private fun smooth(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    /** The 3 pt seam: a cream core with yellow edges and a soft bloom to both sides, kept inside the silhouette. */
    private fun drawSeam(c: Canvas, k: ObjectSigninDissolveLook, planeLocal: Float, slope: Float) {
        c.save()
        c.rotate(6f)
        c.clipPath(sil)
        c.rotate(-6f)
        val a = k.seam.toFloat()
        stroke.strokeCap = Paint.Cap.BUTT
        for ((width, alpha, color) in arrayOf(
            Triple(12f, 0.12f, seamRgb()),
            Triple(7f, 0.2f, seamRgb()),
            Triple(3f, 1f, seamRgb()),
            Triple(1.2f, 1f, coreRgb()),
        )) {
            stroke.color = lerpColor(color, color, 0f, alpha * a)
            stroke.strokeWidth = width
            c.drawLine(-130f, planeLocal - 130f * slope, 130f, planeLocal + 130f * slope, stroke)
        }
        stroke.strokeCap = Paint.Cap.ROUND
        c.restore()
    }
}
