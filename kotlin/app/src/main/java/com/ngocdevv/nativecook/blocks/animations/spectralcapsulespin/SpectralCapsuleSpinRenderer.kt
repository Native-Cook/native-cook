package com.ngocdevv.nativecook.blocks.animations.spectralcapsulespin

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import com.ngocdevv.nativecook.blocks.animations.spectralcapsulespin.SpectralCapsuleSpinTimeline.hash
import com.ngocdevv.nativecook.blocks.animations.spectralcapsulespin.SpectralCapsuleSpinTimeline.smoothstep
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal object ScsPalette {
    const val BACKGROUND = 0x040508
    const val VIGNETTE = 0x0B0E18
    const val BODY = 0x05070D
    const val FROST = 0xC8D7EB
    const val FIBRE = 0xB9C9E6
    const val INNER_GLOW = 0x2D4DFF
    const val RIM_CORE = 0xF4F7FF
    const val WARM_FRINGE = 0xFFB458
    const val COOL_FRINGE = 0x8A6BFF
    const val CAP_BASE = 0xECE8FF
    const val CAUSTIC_CORE = 0xFFE24A
    const val CAUSTIC_MID = 0xFFB040
    const val CAUSTIC_EDGE = 0xFF6A3D
    const val CRESCENT = 0xFF4D5A
    const val SEAM = 0x48E0D6
    const val FAR_SEAM = 0x9FB6FF
    const val HALO = 0x1A2C7A
    val RINGS = intArrayOf(0xFFF1D6, 0xE6F27A, 0x5C8DFF)

    /** The dispersion band, red round to red, twice round the circle. */
    private val SPECTRUM = intArrayOf(0xFF3B5C, 0xFF9F1C, 0xFFE14D, 0x4BE38A, 0x29C6FF, 0x5A5BFF, 0xC64BFF, 0xFF3B5C)
    val SPECTRUM_TWICE: IntArray = (SPECTRUM.toList() + SPECTRUM.drop(1)).map { argb(it, 1.0) }.toIntArray()

    fun argb(rgb: Int, alpha: Double): Int = ((alpha.coerceIn(0.0, 1.0) * 255 + 0.5).toInt() shl 24) or (rgb and 0xFFFFFF)
}

/** Faint frosted fibres inside the glass: seeded strokes drawn once into a bitmap. */
internal object ScsFibres {
    const val SIZE = 480f
    private const val COUNT = 420
    private const val SCALE = 2f

    val bitmap: Bitmap by lazy {
        val bmp = Bitmap.createBitmap((SIZE * SCALE).toInt(), (SIZE * SCALE).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(SCALE, SCALE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 0.6f
        }
        val path = Path()
        for (n in 0 until COUNT) {
            val i = n.toDouble()
            val x = hash(i, 1.0) * SIZE
            val y = hash(i, 2.0) * SIZE
            var length = 6 + 20 * hash(i, 3.0)
            val angle = 2 * PI * hash(i, 4.0)
            val bend = (hash(i, 5.0) - 0.5) * length * 0.6
            var alpha = 0.12 + 0.20 * hash(i, 6.0)
            if (n % 13 == 0) {
                length *= 2.2
                alpha = 0.38
            }
            val dx = cos(angle) * length / 2
            val dy = sin(angle) * length / 2
            paint.color = ScsPalette.argb(ScsPalette.FIBRE, alpha)
            path.reset()
            path.moveTo((x - dx).toFloat(), (y - dy).toFloat())
            path.quadTo((x - sin(angle) * bend).toFloat(), (y + cos(angle) * bend).toFloat(), (x + dx).toFloat(), (y + dy).toFloat())
            canvas.drawPath(path, paint)
        }
        bmp
    }
}

/** Draws the capsule for one pose; coordinates in dp, the canvas already scaled by the density. */
internal class SpectralCapsuleSpinRenderer {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()
    private val blurs = HashMap<Float, BlurMaskFilter>()

    private fun blur(sigma: Float) = blurs.getOrPut(sigma) { BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL) }

    private fun path(points: List<ScsPoint>, closed: Boolean = true): Path {
        val p = Path()
        points.forEachIndexed { i, pt -> if (i == 0) p.moveTo(pt.x.toFloat(), pt.y.toFloat()) else p.lineTo(pt.x.toFloat(), pt.y.toFloat()) }
        if (closed) p.close()
        return p
    }

    /** Reset a paint before each use so a previous shader, alpha or blur never leaks in. */
    private fun Paint.solid(rgb: Int, alpha: Double): Paint {
        shader = null
        maskFilter = null
        color = ScsPalette.argb(rgb, alpha)
        return this
    }

    private fun Paint.shaded(s: Shader, blurSigma: Float = 0f): Paint {
        color = 0xFF000000.toInt()
        alpha = 255
        shader = s
        maskFilter = if (blurSigma > 0f) blur(blurSigma) else null
        return this
    }

    private fun sweep(c: ScsPoint, angle: Double): Shader {
        val shader = SweepGradient(c.x.toFloat(), c.y.toFloat(), ScsPalette.SPECTRUM_TWICE, null)
        matrix.reset()
        matrix.setRotate(Math.toDegrees(angle).toFloat(), c.x.toFloat(), c.y.toFloat())
        shader.setLocalMatrix(matrix)
        return shader
    }

    fun draw(
        canvas: Canvas, width: Float, height: Float,
        phi: Double, hueShift: Double, warmGain: Double, radius: Double, halfLength: Double,
    ) {
        val center = ScsPoint(width / 2.0, height / 2.0)
        val g = SpectralCapsuleSpinGeometry(center, radius, halfLength, phi)
        val light = SpectralCapsuleSpinTimeline.Light(g.facing)
        val warm = light.warm * warmGain
        val silhouette = path(g.silhouette())
        val cap = path(g.nearCap())
        val nearC = g.nearC
        val nearR = g.nearR
        val u = SpectralCapsuleSpinGeometry.U
        val v = g.across
        val spectralAngle = -PI / 4 + phi + hueShift
        val cx = center.x.toFloat()
        val cy = center.y.toFloat()

        // The glass body, everything clipped to the silhouette.
        canvas.save()
        canvas.clipPath(silhouette)
        canvas.drawPath(silhouette, fill.solid(ScsPalette.BODY, 0.92))

        val frost = light.frost
        if (frost > 0.005) {
            val span = radius * 1.1
            val shader = LinearGradient(
                (center.x - v.x * span).toFloat(), (center.y - v.y * span).toFloat(),
                (center.x + v.x * span).toFloat(), (center.y + v.y * span).toFloat(),
                intArrayOf(
                    ScsPalette.argb(ScsPalette.FROST, 0.55 * frost),
                    ScsPalette.argb(ScsPalette.FROST, 0.16 * frost),
                    ScsPalette.argb(ScsPalette.FROST, 0.55 * frost),
                ),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
            )
            canvas.drawPath(silhouette, fill.shaded(shader))
        }

        val s = ScsFibres.SIZE
        val bitmap = ScsFibres.bitmap
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(-45f)
        canvas.scale(max(abs(g.cosP), 0.32).toFloat(), 1f)
        image.alpha = (min(1.0, 0.8 + 0.6 * frost) * 255).toInt()
        canvas.drawBitmap(bitmap, null, RectF((-s / 2 + g.sinP * 30).toFloat(), -s / 2, (s / 2 + g.sinP * 30).toFloat(), s / 2), image)
        canvas.restore()

        stroke.solid(ScsPalette.INNER_GLOW, 0.9 * light.rim)
        stroke.maskFilter = blur(12f)
        stroke.strokeWidth = 34f
        canvas.drawPath(silhouette, stroke)
        stroke.maskFilter = null

        // The near cap catching the light: a pale lens, a red crescent at the pole, a warm caustic.
        if (warm > 0.005 || light.fibres > 0.005) {
            canvas.save()
            canvas.clipPath(cap)
            canvas.drawPath(cap, fill.solid(ScsPalette.CAP_BASE, 0.8 * smoothstep(0.0, 0.55, warm)))
            val red = g.towardsPole(0.55 + 0.3 * abs(g.cosP))
            val redRadius = (0.75 * nearR).toFloat()
            canvas.drawCircle(
                red.x.toFloat(), red.y.toFloat(), redRadius,
                fill.shaded(
                    RadialGradient(
                        red.x.toFloat(), red.y.toFloat(), redRadius,
                        intArrayOf(ScsPalette.argb(ScsPalette.CRESCENT, 0.85 * warm), ScsPalette.argb(ScsPalette.CRESCENT, 0.0)),
                        null, Shader.TileMode.CLAMP,
                    ),
                ),
            )
            val hot = g.towardsPole(0.12 + 0.35 * abs(g.cosP))
            canvas.drawCircle(
                hot.x.toFloat(), hot.y.toFloat(), nearR.toFloat(),
                fill.shaded(
                    RadialGradient(
                        hot.x.toFloat(), hot.y.toFloat(), nearR.toFloat(),
                        intArrayOf(
                            ScsPalette.argb(ScsPalette.CAUSTIC_CORE, warm),
                            ScsPalette.argb(ScsPalette.CAUSTIC_MID, 0.9 * warm),
                            ScsPalette.argb(ScsPalette.CAUSTIC_EDGE, 0.5 * warm),
                            ScsPalette.argb(ScsPalette.CAUSTIC_EDGE, 0.0),
                        ),
                        floatArrayOf(0f, 0.4f, 0.7f, 1f), Shader.TileMode.CLAMP,
                    ),
                ),
            )
            if (light.fibres > 0.005) {
                canvas.save()
                canvas.translate(nearC.x.toFloat(), nearC.y.toFloat())
                canvas.rotate(45f)
                image.alpha = (light.fibres * 255).toInt()
                canvas.drawBitmap(bitmap, null, RectF(-s / 4, -s / 4, s / 4, s / 4), image)
                canvas.restore()
            }
            canvas.restore()
        }

        // The dispersion band: a crescent of spectrum along the cap's rim, and along the seam.
        val rainbow = light.rainbow
        val seamBand = max(0.7 * rainbow, 0.9 * warm)
        if (rainbow > 0.005 || seamBand > 0.005) {
            canvas.save()
            canvas.clipPath(cap)
            val shader = sweep(nearC, spectralAngle)
            stroke.shaded(shader, 4f)
            stroke.alpha = (rainbow * 255).toInt()
            stroke.strokeWidth = (nearR * 0.36).toFloat()
            canvas.drawPath(path(g.nearRimArc(nearR * 0.22), closed = false), stroke)
            stroke.alpha = (seamBand * 255).toInt()
            stroke.strokeWidth = (nearR * 0.16).toFloat()
            canvas.drawPath(path(g.nearSeamFront(), closed = false), stroke)
            canvas.restore()
            stroke.solid(ScsPalette.SEAM, 0.8 * warm)
            stroke.strokeWidth = 2f
            canvas.drawPath(path(g.nearSeamFront(), closed = false), stroke)
        }

        // The far seam and the stacked rings that read through the glass when it points at us.
        val farSeam = 0.3 * g.facing * (1 - light.rings)
        if (farSeam > 0.01) {
            stroke.solid(ScsPalette.FAR_SEAM, farSeam)
            stroke.strokeWidth = 1.5f
            canvas.drawPath(path(g.ring(0.0)), stroke)
        }
        if (light.rings > 0.005) {
            listOf(0.0, 0.35, 0.7).forEachIndexed { index, lambda ->
                stroke.solid(ScsPalette.RINGS[index], light.rings * (0.95 - 0.15 * index))
                stroke.maskFilter = blur(1.2f)
                stroke.strokeWidth = (4.5 - index).toFloat()
                canvas.drawPath(path(g.ringBack(lambda), closed = false), stroke)
            }
            stroke.solid(ScsPalette.RIM_CORE, 0.8 * light.rings)
            stroke.maskFilter = blur(1.2f)
            stroke.strokeWidth = 2.5f
            canvas.drawPath(path(g.ring(1.0)), stroke)
            stroke.maskFilter = null
        }
        canvas.restore()

        // The rim: a warm outer fringe (strongest bottom-left), a soft white bloom, a bright core.
        val span = halfLength + radius
        stroke.shaded(
            LinearGradient(
                (center.x - u.x * span).toFloat(), (center.y - u.y * span).toFloat(),
                (center.x + u.x * span).toFloat(), (center.y + u.y * span).toFloat(),
                intArrayOf(
                    ScsPalette.argb(ScsPalette.WARM_FRINGE, 0.95),
                    ScsPalette.argb(ScsPalette.WARM_FRINGE, 0.55),
                    ScsPalette.argb(ScsPalette.COOL_FRINGE, 0.5),
                ),
                null, Shader.TileMode.CLAMP,
            ),
            6f,
        )
        stroke.strokeWidth = 16f
        canvas.drawPath(silhouette, stroke)

        stroke.solid(0xFFFFFF, 0.65 * light.rim)
        stroke.maskFilter = blur(4f)
        stroke.strokeWidth = 10f
        canvas.drawPath(silhouette, stroke)

        // A thin spectral fringe on the near cap's rim, always faintly there.
        canvas.save()
        canvas.clipPath(silhouette)
        stroke.shaded(sweep(nearC, spectralAngle), 2f)
        stroke.alpha = ((0.25 + 0.55 * light.rainbow) * 255).toInt()
        stroke.strokeWidth = 5f
        canvas.drawPath(path(g.nearRimArc(3.0), closed = false), stroke)
        canvas.restore()

        stroke.solid(ScsPalette.RIM_CORE, 0.95 * light.rim)
        stroke.strokeWidth = 3.4f
        canvas.drawPath(silhouette, stroke)
    }
}
