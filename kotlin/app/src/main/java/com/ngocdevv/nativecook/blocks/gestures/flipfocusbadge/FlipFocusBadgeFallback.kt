package com.ngocdevv.nativecook.blocks.gestures.flipfocusbadge

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.ngocdevv.nativecook.blocks.gestures.flipfocusbadge.FlipFocusBadgeGeometry as G
import com.ngocdevv.nativecook.blocks.gestures.flipfocusbadge.FlipFocusBadgeTheme as Theme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Below API 33 there is no runtime shader: the page's film as gradients and the badge as a drawing, turned about its vertical axis by squashing it by
 * cos(yaw): the scalloped black-glass plate with its enamel edge, the periwinkle numeral (or the back's FOCUS and clock), the "hr", the "+" sparkle, the
 * orbit ring with its two sparkles. The same pose as the shader's, with no 3D lighting.
 */
internal class FlipFocusBadgeFallback(private val bold: Typeface) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = bold
        textAlign = Paint.Align.CENTER
    }
    private val navy = Theme.outline

    /** the scalloped plate's outline, r(a) = 73.3 + 11.1 sqrt(sin^2(6a) + 0.012) */
    private val plate = Path().apply {
        val n = 360
        for (i in 0..n) {
            val a = i * 2 * PI / n
            val sn = sin(6 * a)
            val r = 73.3 + 11.1 * sqrt(sn * sn + 0.012)
            val x = (cos(a) * r).toFloat()
            val y = (sin(a) * r).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    private fun star(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        val p = Path()
        val k = 0.24f
        p.moveTo(x, y - r)
        p.quadTo(x + r * k, y - r * k, x + r, y)
        p.quadTo(x + r * k, y + r * k, x, y + r)
        p.quadTo(x - r * k, y + r * k, x - r, y)
        p.quadTo(x - r * k, y - r * k, x, y - r)
        p.close()
        line.color = navy
        line.strokeWidth = 2.2f
        c.drawPath(p, line)
        fill.shader = null
        fill.color = color
        c.drawPath(p, fill)
    }

    private fun ringArc(c: Canvas, from: Float, sweep: Float, width: Float, color: Int) {
        val oval = RectF(-139.4f, -35.3f, 139.4f, 35.3f)
        line.color = color
        line.strokeWidth = width
        line.strokeCap = Paint.Cap.ROUND
        c.drawArc(oval, from, sweep, false, line)
    }

    /** the ring as an ellipse tilted by the pin's roll, split so the far half sits behind the plate */
    private fun ring(c: Canvas, look: FlipFocusBadgeLook, front: Boolean) {
        val xr = max(abs(cos(look.ringYaw * G.D2R)), 0.1).toFloat()
        c.save()
        c.translate(-6.6f, 24.3f)
        c.rotate(-Theme.RING_ROLL.toFloat())
        c.scale(xr, 1f)
        // the 17 degree gap sits lower left (canvas angles 120.6 .. 137.7); the far half is the top one (180 .. 360)
        val segs = if (front) listOf(0f to 120.6f, 137.7f to 180f) else listOf(180f to 360f)
        for ((a, b) in segs) {
            ringArc(c, a, b - a, 16.4f, navy)
            ringArc(c, a, b - a, 13.4f, Theme.periwinkle)
        }
        c.restore()
    }

    /** `k` = canvas dp per design pt; the canvas is at the badge's viewport origin */
    fun draw(c: Canvas, k: Float, look: FlipFocusBadgeLook) {
        val cosY = cos(look.yaw * G.D2R)
        val xs = max(abs(cosY), 0.045).toFloat()
        val facing = cosY >= 0
        c.save()
        c.translate((Theme.CENTRE_X * k).toFloat(), 150f * k)
        val sc = k * look.press.toFloat()
        c.scale(sc, sc)
        ring(c, look, false)
        // the plate
        c.save()
        c.scale(xs, 1f)
        fill.shader = LinearGradient(-84f, -84f, 84f, 84f, intArrayOf(0xFF2A2A2E.toInt(), 0xFF141416.toInt(), 0xFF1E1E22.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(plate, fill)
        // the studio's strip, a soft band that slides across the glass as the plate turns
        c.save()
        c.clipPath(plate)
        val slide = (((look.yaw % 360) + 360) % 360 / 360.0 * 220 - 110).toFloat()
        c.rotate(-14f)
        fill.shader = LinearGradient(slide - 30f, 0f, slide + 30f, 0f, intArrayOf(0x00FFFFFF, 0x2EFFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(slide - 30f, -140f, slide + 30f, 140f, fill)
        c.restore()
        fill.shader = null
        line.color = Theme.edge
        line.strokeWidth = 1.5f
        c.drawPath(plate, line)
        c.restore()
        // the face: numeral on the front, FOCUS and the clock on the back
        if (facing) front(c, look, xs) else back(c, look, xs)
        ring(c, look, true)
        // sparkles on the ring
        val head = G.ringPoint(Theme.RING_HEAD_DEG, look.ringYaw, look.pitch)
        val sat = G.ringPoint(Theme.RING_SAT_REST_DEG + 360 * (look.sat - 0.4), look.ringYaw, look.pitch)
        star(c, head[0].toFloat(), -head[1].toFloat(), (14 * (1 + 0.25 * look.glintHead)).toFloat(), 0xFF9AB8FA.toInt())
        star(c, sat[0].toFloat(), -sat[1].toFloat(), (13 * (1 + 0.25 * look.glintSat)).toFloat(), 0xFF9AB8FA.toInt())
        c.restore()
    }

    private fun front(c: Canvas, look: FlipFocusBadgeLook, xs: Float) {
        val value = Math.round(look.value).toInt().toString()
        // the numeral: a heavy italic, sized to the plate's margins (the pin's "10" is 121 pt wide)
        text.textSize = 124.5f
        val w = text.measureText(value)
        val size = 124.5f * min(1f, 124f / w)
        text.textSize = size
        val a = (0.35 + 0.65 * look.depth).coerceIn(0.0, 1.0).toFloat()
        c.save()
        c.scale(xs, 1f)
        c.translate(Theme.NUM_PITCH_LEFT.toFloat(), -Theme.NUM_BASE_Y.toFloat())
        c.skew(-0.34f, 0f)
        text.style = Paint.Style.STROKE
        text.strokeWidth = 3.2f
        text.color = navy
        text.alpha = (255 * a).toInt()
        text.shader = null
        c.drawText(value, 0f, 0f, text)
        text.style = Paint.Style.FILL
        text.shader = LinearGradient(0f, -size * 0.73f, 0f, 0f, 0xFFC5D3FF.toInt(), Theme.numeral, Shader.TileMode.CLAMP)
        text.alpha = (255 * a).toInt()
        c.drawText(value, 0f, 0f, text)
        text.shader = null
        c.restore()
        // "hr", and the "+" sparkle with its dot
        c.save()
        c.scale(xs, 1f)
        val pop = look.popHr.toFloat()
        text.textSize = 26f * pop
        text.style = Paint.Style.STROKE
        text.strokeWidth = 2.6f
        text.color = Color.rgb(8, 10, 26)
        c.drawText("hr", 51.5f, 20f, text)
        text.style = Paint.Style.FILL
        text.color = Color.rgb(237, 241, 251)
        c.drawText("hr", 51.5f, 20f, text)
        val pp = look.popPlus.toFloat()
        star(c, -56.5f, -14.2f, 15f * pp * (1f + 0.25f * look.glintPlus.toFloat()), 0xFFDCE3FB.toInt())
        star(c, -38f, -29.8f, 4f * pp, 0xFFDCE3FB.toInt())
        c.restore()
    }

    private fun back(c: Canvas, look: FlipFocusBadgeLook, xs: Float) {
        val b = G.backLayout(look.clock)
        val mm = (b[0].toInt() * 10 + b[1].toInt()).toString().padStart(2, '0')
        val ss = (b[2].toInt() * 10 + b[3].toInt()).toString().padStart(2, '0')
        c.save()
        c.scale(xs, 1f)
        text.style = Paint.Style.FILL
        text.shader = null
        text.color = Color.rgb(237, 241, 251)
        text.textSize = 46f
        text.fontFeatureSettings = "tnum"
        c.drawText("$mm:$ss", 0f, 32.4f, text)
        text.fontFeatureSettings = ""
        text.textSize = 14f
        text.letterSpacing = 0.18f
        text.alpha = 158
        c.drawText("FOCUS", 0f, -29.5f, text)
        text.letterSpacing = 0f
        text.alpha = 255
        c.restore()
    }

    /** the page's film without a shader: the near-black ramp and the badge's contact shadow */
    fun backdrop(c: Canvas, w: Float, h: Float, shadowX: Float, shadowY: Float, shadowHalfW: Float, shadowHalfH: Float, amt: Float, ramp0: Float, ramp1: Float) {
        fill.shader = LinearGradient(
            0f, ramp0, 0f, ramp1,
            Theme.pageTop, Theme.pageMid, Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, w, h, fill)
        c.save()
        c.translate(shadowX, shadowY)
        c.scale(1f, shadowHalfH / shadowHalfW)
        fill.shader = RadialGradient(0f, 0f, shadowHalfW * 1.1f, intArrayOf(Color.argb((255 * 0.55 * amt).toInt(), 0, 0, 0), Color.TRANSPARENT), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, shadowHalfW * 1.1f, fill)
        c.restore()
        fill.shader = null
    }
}
