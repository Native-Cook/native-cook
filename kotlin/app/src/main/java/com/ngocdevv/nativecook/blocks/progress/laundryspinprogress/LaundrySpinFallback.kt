package com.ngocdevv.nativecook.blocks.progress.laundryspinprogress

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Below Android 13 there is no RuntimeShader: the washer drawn with gradients and paths in dp. The
 drum's perforations (one pre-built path of dots) and lifters turn with the drum's angle, the six
 garments are tinted ovals at the script's positions (fading a little while smeared by the spin),
 the water is a sloshing path with a band of suds, and the fog is a pale veil. No cloth texture, no
 glass magnification.
 */
internal class LaundryFallback {
    private var holes: Path? = null
    private var holesFor = -1f
    private val water = Path()
    private val glass = Path()

    private val colors = arrayOf(
        Color(0xFF3D5C8F), Color(0xFFDB6154), Color(0xFFE8B54D),
        Color(0xFF78A180), Color(0xFFEDEBE3), Color(0xFFA391C7),
    )

    private fun holesPath(rd: Float): Path {
        holes?.let { if (holesFor == rd) return it }
        val p = Path()
        val step = 0.075f * rd
        val hr = 0.024f * rd
        var y = -rd
        var row = 0
        while (y <= rd) {
            var x = -rd + if (row % 2 == 1) step * 0.5f else 0f
            while (x <= rd) {
                val r = sqrt(x * x + y * y)
                if (r > 0.3f * rd && r < 0.98f * rd) p.addOval(androidx.compose.ui.geometry.Rect(Offset(x, y), hr))
                x += step
            }
            y += step * 0.866f
            row += 1
        }
        holes = p
        holesFor = rd
        return p
    }

    fun DrawScope.draw(g: LaundryGeo, s: DoubleArray, gm: DoubleArray) {
        val u = g.unit.toFloat()
        val w = g.w.toFloat()
        val h = g.h.toFloat()
        val c = Offset(g.cx.toFloat(), g.cy.toFloat())
        val rg = g.rg.toFloat()
        val bez = rg + 36 * u
        drawRect(Brush.verticalGradient(listOf(Color(0xFFF3F3F2), Color(0xFFDBDCDD)), startY = 0f, endY = h))
        val sc = Offset(c.x, c.y + 10 * u)
        drawCircle(Brush.radialGradient(0.8f to Color(0x38000000), 1f to Color(0x00000000), center = sc, radius = bez + 22 * u), bez + 22 * u, sc)
        drawCircle(Brush.verticalGradient(listOf(Color(0xFFFAFAF8), Color(0xFFD6D9DB)), startY = c.y - bez, endY = c.y + bez), bez, c)
        drawCircle(
            Brush.verticalGradient(listOf(Color(0xFFF7F9FF), Color(0xFF8D939C), Color(0xFFE6E9EE), Color(0xFF40454D)), startY = c.y - rg - 20 * u, endY = c.y + rg + 20 * u),
            rg + 20 * u,
            c,
        )

        // through the glass
        glass.reset()
        glass.addOval(androidx.compose.ui.geometry.Rect(c, rg))
        val rd = rg * 0.9f
        val angle = s[S_ANGLE].toFloat()
        clipPath(glass) {
            drawCircle(Brush.radialGradient(listOf(Color(0xFF47494F), Color(0xFFA8AAAF)), c, rd), rg, c)
            withTransform({
                translate(c.x, c.y)
                rotate(-angle * 180f / PI.toFloat(), Offset.Zero)
            }) {
                drawPath(holesPath(rd), Color(0xD90D0D0F))
                for (k in 0 until 3) {
                    rotate(k * 120f, Offset.Zero) {
                        drawRect(Color(0xFFBFC1C4), Offset(0.8f * rd, -0.14f * rd), Size(0.2f * rd, 0.28f * rd))
                    }
                }
            }
            // garments
            for (i in 0 until LaundryGarments) {
                val o = i * 5
                val gx = c.x + gm[o].toFloat() * rd
                val gy = c.y + gm[o + 1].toFloat() * rd
                val rot = gm[o + 2].toFloat()
                val blur = gm[o + 3].toFloat()
                val st = gm[o + 4].toFloat()
                val a = 0.36f * rd * (1 + st)
                val b = 0.23f * rd / (1 + st * 0.6f)
                val shade = 0.55f + 0.45f * min(1f, (sqrt((gm[o] * gm[o] + gm[o + 1] * gm[o + 1]).toFloat()) + 0.3f) / 0.9f)
                val col = colors[i]
                val tone = Color(col.red * shade, col.green * shade, col.blue * shade)
                withTransform({
                    translate(gx, gy)
                    rotate(rot * 180f / PI.toFloat(), Offset.Zero)
                }) {
                    drawOval(tone, Offset(-a, -b), Size(a * 2, b * 2), alpha = 1f - blur * 0.5f)
                    drawOval(Color(0x24000000), Offset(-a * 0.6f, -b * 0.2f), Size(a * 1.2f, b * 0.5f), alpha = 1f - blur)
                }
            }
            // water and suds
            val level = s[S_LEVEL].toFloat()
            if (level > 0.002f) {
                val slosh = s[S_SLOSH].toFloat()
                water.reset()
                val n = 32
                for (k in 0..n) {
                    val dx = -1f + 2f * k / n
                    val surf = 1f - 2f * level - 0.2f * slosh * dx + slosh * (0.035f * sin(dx * 5f + angle * 0.9f))
                    val x = c.x + dx * rd
                    val y = c.y + surf * rd
                    if (k == 0) water.moveTo(x, y) else water.lineTo(x, y)
                }
                water.lineTo(c.x + rd, c.y + rd)
                water.lineTo(c.x - rd, c.y + rd)
                water.close()
                drawPath(water, Brush.verticalGradient(listOf(Color(0x5CC8E6F2), Color(0x8C5C8099)), startY = c.y + (1 - 2 * level) * rd, endY = c.y + rd))
                val suds = s[S_SUDS].toFloat()
                if (suds > 0.01f) drawPath(water, Color(0xFFF7F8FA), alpha = 0.85f * min(1f, suds * 1.4f), style = Stroke(width = suds * 0.16f * rd))
            }
            // the rubber seal and the glass
            drawCircle(Color(0xFF1A1A1C), rg * 0.95f, c, style = Stroke(width = rg * 0.1f))
            drawCircle(Brush.radialGradient(0.7f to Color(0x00000000), 1f to Color(0x407A9A90), center = c, radius = rg), rg, c)
            drawArc(Color(0x59FFFFFF), 150f, 80f, false, Offset(c.x - rg * 0.78f, c.y - rg * 0.78f), Size(rg * 1.56f, rg * 1.56f), style = Stroke(width = rg * 0.07f))
            val fog = s[S_FOG].toFloat()
            if (fog > 0.001f) drawCircle(Color(0xFFE6E9E9), rg, c, alpha = fog * 0.88f)
        }

        // the door handle
        val hx = c.x + rg + 28 * u
        drawRoundRect(
            Brush.horizontalGradient(listOf(Color(0xFFF5F7FA), Color(0xFF737880)), startX = hx - 6 * u, endX = hx + 6 * u),
            Offset(hx - 6 * u, c.y - 42 * u),
            Size(12 * u, 84 * u),
            androidx.compose.ui.geometry.CornerRadius(6 * u),
        )
        drawRect(Brush.radialGradient(0.55f to Color(0x00000000), 1f to Color(0x14000000), center = Offset(w / 2, h / 2), radius = max(w, h) * 0.7f))
    }
}
