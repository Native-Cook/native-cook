package com.ngocdevv.nativecook.blocks.splash.daylighticonreveal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Draws the day-and-night icon the way the React Native block places it: the artwork's 256 dp
 * square goes through the 4x4 projection of `imagePlane` (origin at the square's top-left corner),
 * then the light sweep crosses it inside the artwork's rounded body.
 *
 * The recede at the end of the loop is part of the same drawing: the square shrinks 4% about its
 * own centre. Its opacity and blur are applied by the layer around the canvas (see
 * [DaylightIconRevealIcon]), because a blur needs a render effect.
 */
internal class DaylightIconRevealIconPainter(private val bitmap: Bitmap) {
    private val timeline = DaylightIconRevealTimeline
    private val size = timeline.IMAGE_SIZE.toFloat()
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val destination = RectF(0f, 0f, size, size)
    private val plane = DoubleArray(16)
    private val values = FloatArray(9)
    private val matrix = Matrix()

    // `glintClip`: the artwork's body, with a corner radius of 23.5% of its width.
    private val bodyLeft = (size * timeline.BODY_X).toFloat()
    private val bodyTop = (size * timeline.BODY_Y).toFloat()
    private val bodyWidth = (size * timeline.BODY_WIDTH).toFloat()
    private val bodyHeight = (size * timeline.BODY_HEIGHT).toFloat()
    private val bodyRadius = (size * timeline.BODY_WIDTH * 0.235).toFloat()
    private val bodyClip = Path().apply {
        addRoundRect(RectF(0f, 0f, bodyWidth, bodyHeight), bodyRadius, bodyRadius, Path.Direction.CW)
    }

    // `glint`: a 96 dp wide band, 60 dp taller than the body above and below, with a soft white core.
    private val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f,
            0f,
            GLINT_WIDTH,
            0f,
            intArrayOf(0x00FFFFFF, 0x40FFFFFF, 0xB8FFFFFF.toInt(), 0x40FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    /** The icon's opacity from the pose track and the recede (`iconStyle` times `recede`). */
    fun layerAlpha(t: Double, reduced: Boolean): Float {
        val poseOpacity = if (reduced) 1.0 else timeline.poseChannel(artTime(t, reduced), 7)
        return (poseOpacity * (1.0 - recede(t, reduced))).toFloat()
    }

    /** 0 until 7.45 s, then 1 at 8.15 s on the strong ease-out: how far the icon has receded. */
    fun recede(t: Double, reduced: Boolean): Double =
        if (reduced) 0.0 else timeline.Ease.at(timeline.segment(t, 7.45, 8.15))

    /** How much the projection scales the icon at rest, to carry a blur from the icon's units to the screen's. */
    fun restScale(artWidth: Float, artHeight: Float): Double {
        timeline.imagePlane(4.0, artWidth.toDouble(), artHeight.toDouble(), plane)
        return sqrt(abs(plane[0] * plane[5] - plane[1] * plane[4]))
    }

    /**
     * Draws the frame at loop time [t]. [left] and [top] place the square in the hero (dp), and
     * [artWidth] and [artHeight] are the sizes the projection is scaled by (dp).
     */
    fun draw(canvas: Canvas, density: Float, t: Double, reduced: Boolean, left: Float, top: Float, artWidth: Float, artHeight: Float) {
        timeline.imagePlane(artTime(t, reduced), artWidth.toDouble(), artHeight.toDouble(), plane)
        // The 4x4 column-major array holds the 2D projection as [x', y', w] = rows of
        // (0, 4, 12), (1, 5, 13) and (3, 7, 15); android.graphics.Matrix is row-major 3x3.
        values[Matrix.MSCALE_X] = plane[0].toFloat()
        values[Matrix.MSKEW_X] = plane[4].toFloat()
        values[Matrix.MTRANS_X] = plane[12].toFloat()
        values[Matrix.MSKEW_Y] = plane[1].toFloat()
        values[Matrix.MSCALE_Y] = plane[5].toFloat()
        values[Matrix.MTRANS_Y] = plane[13].toFloat()
        values[Matrix.MPERSP_0] = plane[3].toFloat()
        values[Matrix.MPERSP_1] = plane[7].toFloat()
        values[Matrix.MPERSP_2] = plane[15].toFloat()
        matrix.setValues(values)

        val shrink = (1.0 - 0.04 * recede(t, reduced)).toFloat()
        canvas.save()
        canvas.scale(density, density)
        canvas.translate(left, top)
        canvas.concat(matrix)
        canvas.translate(size / 2f, size / 2f)
        canvas.scale(shrink, shrink)
        canvas.translate(-size / 2f, -size / 2f)
        canvas.drawBitmap(bitmap, null, destination, imagePaint)
        drawGlint(canvas, t, reduced)
        canvas.restore()
    }

    /** `glint`: one sweep of light, 2.5 s to 3.4 s, a 96 dp band turned 18 degrees crossing the body. */
    private fun drawGlint(canvas: Canvas, t: Double, reduced: Boolean) {
        if (reduced) return
        val p = timeline.segment(t, 2.5, 3.4)
        val opacity = sin(p * PI)
        if (opacity <= 0.002) return
        canvas.save()
        canvas.translate(bodyLeft, bodyTop)
        canvas.clipPath(bodyClip)
        // translateX(-110 + SWEEP(p) * 400) then rotate(18deg): the band turns about its own centre first.
        canvas.translate((-110.0 + timeline.Sweep.at(p) * 400.0).toFloat(), 0f)
        canvas.rotate(18f, GLINT_WIDTH / 2f, bodyHeight / 2f)
        glintPaint.alpha = (opacity * 255.0).roundToInt().coerceIn(0, 255)
        canvas.drawRect(0f, -GLINT_OVERHANG, GLINT_WIDTH, bodyHeight + GLINT_OVERHANG, glintPaint)
        canvas.restore()
    }

    /** `artTime`: the pose track stops at 4 s, and Reduce Motion shows the finished pose. */
    private fun artTime(t: Double, reduced: Boolean): Double = if (reduced) 4.0 else min(t, 4.0)

    private companion object {
        const val GLINT_WIDTH = 96f
        const val GLINT_OVERHANG = 60f
    }
}
