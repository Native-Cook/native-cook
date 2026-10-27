package com.ngocdevv.nativecook.blocks.gestures.paperdecktutorial

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * `IllustratedDeck`: the illustrated cover that opens the loop and the "This or that" deck that
 * closes it, drawn in dp on a canvas whose origin is the deck's top-left corner, over the paper sheets.
 *
 * The cover is a 27 dp rounded rectangle one 5 dp inset smaller than the card on every side. It rises
 * from `introTravel` below its place (0.2 to 0.7 s) and turns about its vertical axis under a
 * perspective of 1600 (`[{ perspective }, { translateY }, { rotateY }]`). Two smaller covers peek above
 * it before the tutorial (sunny, then night) and after it (thinking, then sunny). After the tutorial the
 * burger-or-apple card rises into place and, at 17.5 s, fades into the thinking cover.
 */
internal class PaperDeckTutorialCoverPainter(private val assets: PaperDeckTutorialAssets) {
    private val script = PaperDeckTutorialScript

    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val matrix = Matrix()
    private val coverMatrix = Matrix()
    private val matrixValues = FloatArray(9)
    private val round = Path()
    private val box = RectF()

    fun draw(canvas: Canvas, pose: PaperDeckTutorialPose, geometry: PaperDeckTutorialGeometry, reduced: Boolean) {
        val outro = pose.step == 6
        val time = pose.time
        val flip = pose.flip
        val rise = if (outro) script.ease(script.clamp((time - 14.9) / 0.3)) else 1.0
        val opacity = when {
            reduced -> if (outro) 1.0 else 0.0
            outro -> rise
            time < 0.8417 && flip < 0.5 -> 1.0
            else -> 0.0
        }
        if (opacity <= 0.0) return
        val lift = if (outro) {
            geometry.introTravel + 160.0 * (1.0 - rise)
        } else {
            geometry.introTravel * (1.0 - script.track(time, script.LIFT))
        }
        val turn = if (outro) 0.0 else flip * 180.0

        val width = geometry.width.toFloat()
        val height = geometry.height.toFloat()
        val coverWidth = width - 10f
        val coverHeight = height - 10f

        val grouped = opacity < 1.0
        if (grouped) canvas.saveLayerAlpha(null, (opacity * 255).roundToInt())
        canvas.save()
        setTurnMatrix(lift, turn, width / 2f, height / 2f)
        canvas.concat(matrix)
        // The cover's box sits 5 dp in from the card's corner (`top: 5, left: 5`).
        canvas.translate(5f, 5f)

        // The covers behind peek above the front one and fade out as it lifts (0.2 to 0.6 s).
        val backs = if (outro) 1.0 else 1.0 - script.track(time, script.BACKS_FADE)
        if (backs > 0.0) {
            val backsGrouped = backs < 1.0
            if (backsGrouped) canvas.saveLayerAlpha(null, (backs * 255).roundToInt())
            drawBack(canvas, coverWidth, coverHeight, 0.67f, (geometry.introTravel * 0.58).toFloat(), assets.sunny, assets.thinking, outro, false)
            drawBack(canvas, coverWidth, coverHeight, 0.83f, (geometry.introTravel * 0.3504).toFloat(), assets.night, assets.sunny, outro, true)
            if (backsGrouped) canvas.restore()
        }

        // The front cover: the thinking cover, and the choice card on top of it through the outro.
        canvas.save()
        clipRound(canvas, coverWidth, coverHeight)
        fillPaint.color = 0xFF9260DC.toInt()
        canvas.drawRect(0f, 0f, coverWidth, coverHeight, fillPaint)
        drawCover(canvas, assets.thinking, coverWidth, coverHeight, 255)
        val choice = if (outro) 1.0 - script.ease(script.clamp((time - 17.5) / 0.4)) else 0.0
        if (choice > 0.0) drawCover(canvas, assets.choice, coverWidth, coverHeight, (choice * 255).roundToInt())
        canvas.restore()

        canvas.restore()
        if (grouped) canvas.restore()
    }

    /**
     * `[{ perspective: 1600 }, { translateY: [lift] }, { rotateY: [turn] deg }]` about the deck's centre
     * ([cx], [cy]): a point (X, Y) from the centre goes to (X cos t, Y + lift) over w = 1 + X sin t / 1600.
     */
    private fun setTurnMatrix(lift: Double, turn: Double, cx: Float, cy: Float) {
        val radians = Math.toRadians(turn)
        matrixValues[Matrix.MSCALE_X] = cos(radians).toFloat()
        matrixValues[Matrix.MSKEW_X] = 0f
        matrixValues[Matrix.MTRANS_X] = 0f
        matrixValues[Matrix.MSKEW_Y] = 0f
        matrixValues[Matrix.MSCALE_Y] = 1f
        matrixValues[Matrix.MTRANS_Y] = lift.toFloat()
        matrixValues[Matrix.MPERSP_0] = (sin(radians) / PERSPECTIVE).toFloat()
        matrixValues[Matrix.MPERSP_1] = 0f
        matrixValues[Matrix.MPERSP_2] = 1f
        matrix.setValues(matrixValues)
        matrix.preTranslate(-cx, -cy)
        matrix.postTranslate(cx, cy)
    }

    /**
     * One of the covers behind: scaled by [scale] about its top centre, then raised by [raise] (`coverBack`:
     * `transformOrigin: 'top center'`, `[{ translateY }, { scale }]`), clipped to the rounded rectangle on
     * #1B2240. After the tutorial ([outro]) a second picture lies over the first, and the nearer cover has
     * a 14% black shade ([shade]).
     */
    private fun drawBack(
        canvas: Canvas,
        coverWidth: Float,
        coverHeight: Float,
        scale: Float,
        raise: Float,
        base: Bitmap,
        over: Bitmap,
        outro: Boolean,
        shade: Boolean,
    ) {
        canvas.save()
        canvas.translate(0f, -raise)
        canvas.translate(coverWidth / 2f, 0f)
        canvas.scale(scale, scale)
        canvas.translate(-coverWidth / 2f, 0f)
        clipRound(canvas, coverWidth, coverHeight)
        fillPaint.color = 0xFF1B2240.toInt()
        canvas.drawRect(0f, 0f, coverWidth, coverHeight, fillPaint)
        drawCover(canvas, base, coverWidth, coverHeight, 255)
        if (outro) drawCover(canvas, over, coverWidth, coverHeight, 255)
        if (shade) {
            fillPaint.color = 0x24000000
            canvas.drawRect(0f, 0f, coverWidth, coverHeight, fillPaint)
        }
        canvas.restore()
    }

    private fun clipRound(canvas: Canvas, coverWidth: Float, coverHeight: Float) {
        box.set(0f, 0f, coverWidth, coverHeight)
        round.reset()
        round.addRoundRect(box, RADIUS, RADIUS, Path.Direction.CW)
        canvas.clipPath(round)
    }

    /** `resizeMode="cover"`: [bitmap] scaled to fill the box and centred, with the overflow cut off by the clip. */
    private fun drawCover(canvas: Canvas, bitmap: Bitmap, coverWidth: Float, coverHeight: Float, alpha: Int) {
        val scale = max(coverWidth / bitmap.width, coverHeight / bitmap.height)
        coverMatrix.setScale(scale, scale)
        coverMatrix.postTranslate((coverWidth - bitmap.width * scale) / 2f, (coverHeight - bitmap.height * scale) / 2f)
        imagePaint.alpha = alpha
        canvas.drawBitmap(bitmap, coverMatrix, imagePaint)
    }

    private companion object {
        /** `borderRadius: 27`. */
        const val RADIUS = 27f

        /** `perspective: 1600`, in dp. */
        const val PERSPECTIVE = 1600f
    }
}
