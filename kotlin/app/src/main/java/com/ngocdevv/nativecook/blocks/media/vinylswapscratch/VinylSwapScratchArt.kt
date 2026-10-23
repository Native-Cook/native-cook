package com.ngocdevv.nativecook.blocks.media.vinylswapscratch

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** The two invented records and everything drawn in the block's canvas, ported from VinylSwapScratchArt.swift. */
internal class VinylAlbum(
    val title: String,
    val artist: String,
    val top: Color,
    val bottom: Color,
    val glow: Color,
    val accent: Color,
)

internal val vinylAlbums = listOf(
    VinylAlbum("Slow Satellites", "The Amber Rooms", Color(0xFF8C6A1F), Color(0xFF15110A), Color(0xFFF2B632), Color(0xFFFFC845)),
    VinylAlbum("Glass Harbor", "Noa Lind", Color(0xFF2F6E7A), Color(0xFF0A1416), Color(0xFF7FD1C7), Color(0xFF7FE3D6)),
)

/** Linear mix in sRGB, like the Swift version. */
internal fun mixColor(a: Color, b: Color, t: Double): Color {
    val f = t.toFloat()
    return Color(a.red + (b.red - a.red) * f, a.green + (b.green - a.green) * f, a.blue + (b.blue - a.blue) * f, 1f)
}

/** The screen's geometry in dp, from its size and safe area. */
internal class VinylGeo(val w: Float, val h: Float, val top: Float, val bottom: Float) {
    val r = 0.56f * w
    val labelR = 0.42f * r
    val center = Offset(0.29f * w, 0.355f * h)
    val pivot = Offset(0.86f * w, 0.165f * h)
    /** Where the stylus rests: 80 % out on the record, 35° below its right. */
    val stylus = run {
        val a = 35.0 * PI / 180
        Offset(center.x + (0.8 * r * cos(a)).toFloat(), center.y + (0.8 * r * sin(a)).toFloat())
    }
    val armLength = hypot(stylus.x - pivot.x, stylus.y - pivot.y)
    /** The arm's angle from straight down (positive towards the left), in the groove and swung out. */
    val armRest = atan2(-(stylus.x - pivot.x), stylus.y - pivot.y)
    val armOut = armRest - 0.42f
    /** How far a record slides to leave the screen on either side. */
    val slide = max(center.x + r + 40, w - center.x + r + 40)
    val stripTop = center.y + r + 26
    val titleTop = stripTop + STRIP_H + 28
    val controlsTop = max(titleTop + 76, h - max(bottom, 16f) - 66)

    companion object {
        const val STRIP_H = 56f
        /** Where the scripted finger lands, as an angle on the record. */
        const val CONTACT = -50.0
    }
}

internal class VinylPainter {
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val heavy: Typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.DEFAULT, 800, false) else Typeface.DEFAULT_BOLD
    private val mono: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    private val blurs = HashMap<Int, BlurMaskFilter>()

    /** A soft dark shape (Gaussian of [sigma] dp); a plain translucent shape below API 28. */
    private fun DrawScope.shadow(sigma: Float, alpha: Float, draw: (android.graphics.Canvas, Paint) -> Unit) {
        val p = shadowPaint
        p.color = android.graphics.Color.BLACK
        p.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val key = (sigma * 4).roundToInt()
            p.maskFilter = blurs.getOrPut(key) { BlurMaskFilter(sigmaToRadius(key / 4f), BlurMaskFilter.Blur.NORMAL) }
        } else {
            p.maskFilter = null
            p.alpha = (alpha * 0.5f * 255).roundToInt()
        }
        draw(drawContext.canvas.nativeCanvas, p)
        p.maskFilter = null
    }

    private fun DrawScope.text(s: String, x: Float, y: Float, size: Float, color: Color, typeface: Typeface, tracking: Float) {
        val p = textPaint
        p.typeface = typeface
        p.textSize = size
        p.letterSpacing = tracking / size
        p.color = color.toArgb()
        val fm = p.fontMetrics
        drawContext.canvas.nativeCanvas.drawText(s, x, y - (fm.ascent + fm.descent) / 2, p)
    }

    // Background.

    fun DrawScope.background(g: VinylGeo, mix: Double) {
        val a = vinylAlbums[0]
        val b = vinylAlbums[1]
        val top = mixColor(a.top, b.top, mix)
        val bottom = mixColor(a.bottom, b.bottom, mix)
        val glow = mixColor(a.glow, b.glow, mix)
        drawRect(Brush.verticalGradient(0f to top, 0.45f to mixColor(top, bottom, 0.55), 0.8f to bottom, startY = 0f, endY = g.h), size = Size(g.w, g.h))
        // The record's label, as a huge blurred light behind it.
        drawRect(
            Brush.radialGradient(listOf(glow.copy(alpha = 0.42f), glow.copy(alpha = 0.12f), glow.copy(alpha = 0f)), center = g.center, radius = g.w * 1.05f),
            size = Size(g.w, g.h),
        )
        drawRect(
            Brush.radialGradient(listOf(glow.copy(alpha = 0.16f), glow.copy(alpha = 0f)), center = Offset(g.w, g.h * 0.62f), radius = g.w * 0.8f),
            size = Size(g.w, g.h),
        )
    }

    // Record.

    fun DrawScope.disc(g: VinylGeo, album: Int, c: Offset, angle: Double, alpha: Float) {
        if (alpha <= 0.001f) return
        val r = g.r
        // Shadow on the plinth.
        shadow(18f, 0.55f * alpha) { canvas, p -> canvas.drawCircle(c.x + 4, c.y + 14, r, p) }
        drawCircle(Color(0xFF0B0B0C), r, c, alpha = alpha)
        // Grooves: hairlines of alternating brightness, two wider gaps between tracks.
        val inner = g.labelR + 8
        val outer = r - 7
        for (i in 0 until 46) {
            val rad = inner + (outer - inner) * i / 45f
            drawCircle(Color.White.copy(alpha = if (i % 2 == 0) 0.035f else 0.065f), rad, c, alpha = alpha, style = Stroke(0.7f))
        }
        for (f in floatArrayOf(0.34f, 0.68f)) {
            drawCircle(Color(0xFF17171A), inner + (outer - inner) * f, c, alpha = alpha, style = Stroke(3f))
        }
        drawCircle(Color.White.copy(alpha = 0.12f), r - 0.75f, c, alpha = alpha, style = Stroke(1.5f))
        // The sheen: two soft wedges fixed to the light, so the spin reads as gloss.
        val ring = Path().apply {
            fillType = PathFillType.EvenOdd
            addOval(Rect(c, r - 2))
            addOval(Rect(c, g.labelR + 2))
        }
        rotate(-70f, c) {
            drawPath(
                ring,
                Brush.sweepGradient(
                    0f to Color.White.copy(alpha = 0f),
                    0.07f to Color.White.copy(alpha = 0.13f),
                    0.17f to Color.White.copy(alpha = 0f),
                    0.5f to Color.White.copy(alpha = 0f),
                    0.57f to Color.White.copy(alpha = 0.09f),
                    0.67f to Color.White.copy(alpha = 0f),
                    1f to Color.White.copy(alpha = 0f),
                    center = c,
                ),
                alpha = alpha,
            )
        }
        // Everything below turns with the platter.
        translate(c.x, c.y) {
            rotate((angle + if (album == 1) 140.0 else 0.0).toFloat(), Offset.Zero) {
                // Dust on the vinyl.
                for (i in 0 until 6) {
                    val a = i * 1.9 + 0.4
                    val d = g.labelR + 14 + (r - g.labelR - 30) * VinylMotion.hash(i, 9).toFloat()
                    drawCircle(Color.White.copy(alpha = 0.28f), 0.8f, Offset((d * cos(a)).toFloat(), (d * sin(a)).toFloat()), alpha = alpha)
                }
                label(album, g.labelR, alpha)
            }
        }
        // Spindle.
        drawCircle(Brush.radialGradient(listOf(Color(0xFFEBEBEB), Color(0xFF808080)), center = Offset(c.x - 1, c.y - 1), radius = 4.5f), 4.5f, c, alpha = alpha)
        drawCircle(Color(0xFF333333), 1.6f, c, alpha = alpha)
    }

    /** The label, drawn around the origin of an already rotated scope. */
    private fun DrawScope.label(album: Int, r: Float, alpha: Float) {
        val disc = Path().apply { addOval(Rect(Offset.Zero, r)) }
        clipPath(disc) {
            if (album == 0) {
                drawCircle(Color(0xFFF2B632), r, Offset.Zero, alpha = alpha)
                // Sunburst.
                val rays = Path()
                for (i in 0 until 16) {
                    val a0 = i * PI / 8
                    val a1 = a0 + PI / 16
                    rays.moveTo(0f, 0f)
                    rays.lineTo((2 * r * cos(a0)).toFloat(), (2 * r * sin(a0)).toFloat())
                    rays.lineTo((2 * r * cos(a1)).toFloat(), (2 * r * sin(a1)).toFloat())
                    rays.close()
                }
                drawPath(rays, Color(0xFFE0951C), alpha = alpha)
                drawCircle(Color(0xFF5A1A0B).copy(alpha = 0.45f), 0.88f * r, Offset.Zero, alpha = alpha, style = Stroke(1f))
                drawCircle(Color(0xFF8E2A12), 0.25f * r, Offset(0.37f * r, -0.37f * r), alpha = alpha)
                text("THE AMBER ROOMS", 0f, 0.5f * r, max(7f, r * 0.1f), Color(0xFF5A1A0B).copy(alpha = alpha), heavy, 1.2f)
                text("SIDE A · 33⅓", 0f, 0.68f * r, max(6f, r * 0.075f), Color(0xFF5A1A0B).copy(alpha = 0.8f * alpha), mono, 0f)
            } else {
                drawCircle(Color(0xFFEDE6D3), r, Offset.Zero, alpha = alpha)
                val c = Offset(-0.28f * r, 0.22f * r)
                floatArrayOf(0.28f, 0.5f, 0.72f, 0.94f).forEachIndexed { i, f ->
                    drawCircle(Color(0xFF1F6F78).copy(alpha = 1 - i * 0.18f), f * r, c, alpha = alpha, style = Stroke(r * 0.06f))
                }
                drawCircle(Color(0xFFFF6B4A), 0.18f * r, Offset(0.38f * r, -0.44f * r), alpha = alpha)
                text("NOA LIND", 0.18f * r, 0.56f * r, max(7f, r * 0.11f), Color(0xFF0E3B40).copy(alpha = alpha), heavy, 2f)
                text("SIDE B · 33⅓", 0.18f * r, 0.72f * r, max(6f, r * 0.075f), Color(0xFF0E3B40).copy(alpha = 0.8f * alpha), mono, 0f)
            }
        }
        drawCircle(Color.Black.copy(alpha = 0.35f), r - 0.5f, Offset.Zero, alpha = alpha, style = Stroke(1f))
    }

    // Tonearm.

    fun DrawScope.arm(g: VinylGeo, out: Double, lift: Double) {
        val p = g.pivot
        val theta = g.armRest + (g.armOut - g.armRest) * out.toFloat()
        val len = g.armLength
        val base = 0.095f * g.w
        val l = lift.toFloat()
        // The pivot's round base.
        shadow(10f, 0.45f) { canvas, paint -> canvas.drawCircle(p.x + 3, p.y + 8, base, paint) }
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFF2C2C2F), Color(0xFF141416)), center = Offset(p.x - base * 0.3f, p.y - base * 0.4f), radius = base * 1.3f),
            base, p,
        )
        drawCircle(Color.White.copy(alpha = 0.09f), base - 0.5f, p, style = Stroke(1f))

        // The arm's shadow: the same outline in black, offset and softened as it rises.
        shadow(3 + 6 * l, 0.4f - 0.12f * l) { canvas, paint ->
            canvas.save()
            canvas.translate(p.x + 4 + 10 * l, p.y + 8 + 16 * l)
            canvas.rotate(Math.toDegrees(theta.toDouble()).toFloat())
            val s = 1 + 0.04f * l
            canvas.scale(s, s)
            canvas.drawRoundRect(-10f, -50f, 10f, -14f, 4f, 4f, paint)
            canvas.drawRoundRect(-3f, 0f, 3f, len - 26, 3f, 3f, paint)
            canvas.drawRoundRect(6f, len - 30, 20f, len - 27, 1.5f, 1.5f, paint)
            canvas.drawRoundRect(-8f, len - 34, 8f, len + 2, 3f, 3f, paint)
            canvas.drawCircle(0f, 0f, 13f, paint)
            canvas.restore()
        }
        translate(p.x, p.y) {
            rotateRad(theta, Offset.Zero) {
                val s = 1 + 0.04f * l
                scale(s, s, Offset.Zero) {
                    // Counterweight.
                    drawRoundRect(
                        Brush.horizontalGradient(listOf(Color(0xFF3A3A3E), Color(0xFF76767C), Color(0xFF2A2A2D)), startX = -10f, endX = 10f),
                        Offset(-10f, -50f), Size(20f, 36f), CornerRadius(4f),
                    )
                    for (y in floatArrayOf(-42f, -24f)) drawRect(Color.Black.copy(alpha = 0.5f), Offset(-10f, y), Size(20f, 1.2f))
                    // Tube.
                    drawRoundRect(
                        Brush.horizontalGradient(
                            0f to Color(0xFF86868C), 0.4f to Color(0xFFF5F5F7), 0.7f to Color(0xFFA6A6AB), 1f to Color(0xFF5E5E63),
                            startX = -3f, endX = 3f,
                        ),
                        Offset(-3f, 0f), Size(6f, len - 26), CornerRadius(3f),
                    )
                    // Headshell, with its finger lift.
                    drawRoundRect(Color(0xFF8A8A90), Offset(6f, len - 30), Size(14f, 3f), CornerRadius(1.5f))
                    drawRoundRect(
                        Brush.horizontalGradient(listOf(Color(0xFF2A2A2E), Color(0xFF111113)), startX = -8f, endX = 8f),
                        Offset(-8f, len - 34), Size(16f, 36f), CornerRadius(3f),
                    )
                    drawRoundRect(Color.White.copy(alpha = 0.12f), Offset(-8f, len - 34), Size(16f, 36f), CornerRadius(3f), style = Stroke(0.8f))
                    drawCircle(Color(0xFFFFB13B), 1.6f, Offset(0f, len - 6.4f))
                    // Pivot cap.
                    drawCircle(
                        Brush.radialGradient(listOf(Color(0xFFF2F2F4), Color(0xFF7C7C82), Color(0xFF3A3A3E)), center = Offset(-4f, -5f), radius = 16f),
                        13f, Offset.Zero,
                    )
                    drawCircle(Color(0xFF1A1A1C), 4.5f, Offset.Zero)
                }
            }
        }
    }

    // Waveform.

    fun DrawScope.waveform(g: VinylGeo, p: VinylMotion.Pose) {
        val x0 = 24f
        val w = g.w - 48
        val top = g.stripTop
        val h = VinylGeo.STRIP_H
        val strip = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(x0, top, x0 + w, top + h, CornerRadius(14f))) }
        drawPath(strip, Color.Black.copy(alpha = 0.26f))
        drawPath(strip, Color.White.copy(alpha = 0.07f), style = Stroke(1f))
        val pitch = 6.0
        val pos = p.angle / 360 * VinylMotion.BARS_PER_TURN
        val mid = x0 + w / 2
        val speed = p.speed / 360 * VinylMotion.BARS_PER_TURN * pitch
        val intensity = VinylMotion.intensity(p.speed)
        val smear = min(9.0, max(0.0, abs(speed) - 70) * 0.06)
        val accent = mixColor(vinylAlbums[0].accent, vinylAlbums[1].accent, p.mix)
        val loud = 0.12 + 0.88 * p.needle
        val n = (w / 2 / pitch).toInt() + 2
        val first = floor(pos).toInt() - n
        clipPath(strip) {
            for (k in first..first + 2 * n + 1) {
                val x = mid + (k - pos) * pitch
                if (x < x0 + 6 || x > x0 + w - 6) continue
                val edge = min(1.0, min(x - x0 - 6, x0 + w - 6 - x) / 26)
                val hv = VinylMotion.bar(0, k) + (VinylMotion.bar(1, k) - VinylMotion.bar(0, k)) * p.waveMix
                val bh = 4 + (h - 16) * hv * loud
                val played = k < pos
                var color = Color.White.copy(alpha = ((if (played) 0.92 else 0.32) * edge).toFloat())
                if (VinylMotion.isKick(k) && played) color = accent.copy(alpha = edge.toFloat())
                if (intensity > 0) {
                    color = if (played || VinylMotion.isKick(k)) accent.copy(alpha = (edge * (0.92 + 0.08 * intensity)).toFloat())
                    else accent.copy(alpha = (edge * (0.32 + 0.4 * intensity)).toFloat())
                }
                // Bars smear behind their motion when the record is pushed fast.
                val bw = 3 + smear
                val bx = if (speed >= 0) x - 1.5 else x + 1.5 - bw
                val a = if (smear > 0) min(1.0, 3 / bw + 0.25) else 1.0
                drawRoundRect(
                    color.copy(alpha = (color.alpha * a).toFloat()),
                    Offset(bx.toFloat(), (top + h / 2 - bh / 2).toFloat()), Size(bw.toFloat(), bh.toFloat()), CornerRadius(1.5f),
                )
            }
            // Playhead.
            drawRect(Color.White, Offset(mid - 0.75f, top + 6), Size(1.5f, h - 12))
        }
        drawCircle(Color.White, 3f, Offset(mid, top))
    }

    // Hand.

    fun DrawScope.scratchMarks(g: VinylGeo, p: VinylMotion.Pose) {
        val intensity = VinylMotion.intensity(p.speed)
        if (intensity <= 0.01) return
        val c = g.center
        for (j in 0 until 5) {
            val flick = 0.5 + 0.5 * sin(p.clock * 47 + j * 2.3)
            val rad = g.r + 8 + (j % 3) * 6
            val mid = VinylGeo.CONTACT + (j - 2) * 16 + p.fingerTurn * 0.4
            drawArc(
                Color.White.copy(alpha = (0.7 * intensity * flick).toFloat()),
                startAngle = (mid - 7).toFloat(), sweepAngle = 14f, useCenter = false,
                topLeft = Offset(c.x - rad, c.y - rad), size = Size(rad * 2, rad * 2),
                style = Stroke(1.4f, cap = StrokeCap.Round),
            )
        }
    }

    fun DrawScope.finger(g: VinylGeo, p: VinylMotion.Pose) {
        if (p.fingerAlpha <= 0.001) return
        val a = (VinylGeo.CONTACT + p.fingerTurn) * PI / 180
        val c = g.center
        val pt = Offset(c.x + (0.72 * g.r * cos(a)).toFloat(), c.y + (0.72 * g.r * sin(a)).toFloat())
        val r = (22 * p.fingerScale).toFloat()
        drawCircle(Color.White.copy(alpha = (0.2 * p.fingerAlpha).toFloat()), r, pt)
        drawCircle(Color.White.copy(alpha = (0.6 * p.fingerAlpha).toFloat()), r - 0.75f, pt, style = Stroke(1.5f))
    }
}
