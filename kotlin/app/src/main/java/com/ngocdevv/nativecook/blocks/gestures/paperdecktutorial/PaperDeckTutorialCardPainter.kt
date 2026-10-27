package com.ngocdevv.nativecook.blocks.gestures.paperdecktutorial

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The six paper sheets of `PaperCard`, drawn in dp on a canvas whose origin is the deck's top-left
 * corner. Each sheet is a flat plane with the React Native file's projective matrix
 * (`projectedFace`: scale, roll about z and yaw about y with a perspective distance of 1600, the
 * translation applied before the perspective divide), so a card throws and tilts the way it does there.
 *
 * Each sheet is its own shadow, the bowed paper (an SVG path with a gradient fill and a 1 dp edge), a
 * highlight riding the bend, the card's serif title, its emoji and its number. Nothing here allocates
 * per frame beyond a few small strings.
 */
internal class PaperDeckTutorialCardPainter(private val assets: PaperDeckTutorialAssets) {
    private val script = PaperDeckTutorialScript

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val shadowFilters = HashMap<Int, BlurMaskFilter>()
    private val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = 0xFFE7E4EA.toInt()
    }
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = assets.serif
        color = 0xFF141215.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = assets.serif
        color = 0xFF1F1B1F.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val paper = Path()
    private val clip = Path()
    private val bounds = RectF()
    private val symbolBox = RectF()
    private val matrix = Matrix()
    private val gradientMatrix = Matrix()
    private val matrixValues = FloatArray(9)
    private val metrics = Paint.FontMetrics()

    /** `<SvgGradient x1=0 y1=0 x2=0.35 y2=1>` in the path's bounding-box units, white to #F6F4F8. */
    private val paperGradient = LinearGradient(
        0f, 0f, 0.35f, 1f,
        intArrayOf(0xFFFFFFFF.toInt(), 0xFFFDFBFE.toInt(), 0xFFF6F4F8.toInt()),
        floatArrayOf(0f, 0.6f, 1f),
        Shader.TileMode.CLAMP,
    )

    private var sheenShader: LinearGradient? = null
    private var sheenShaderWidth = Float.NaN

    // The title's lines for one width and size, so a frame does not break them again.
    private val titleLines = arrayOfNulls<List<String>>(CARD_COUNT)
    private var titleLinesWidth = Float.NaN
    private var titleLinesSize = Float.NaN

    private val symbols = arrayOfNulls<SymbolImage>(CARD_COUNT)

    /** An emoji drawn once into a bitmap: a bitmap glyph cannot be drawn as text under a perspective matrix. */
    private class SymbolImage(
        val sizeDp: Float,
        val pixelsPerDp: Float,
        val bitmap: Bitmap,
        val baseline: Float,
        val descent: Float,
    )

    /** Draws every sheet that is visible, the last card first, so card 1 ends on top (`zIndex: 10 - index`). */
    fun draw(
        canvas: Canvas,
        pose: PaperDeckTutorialPose,
        grab: Double,
        geometry: PaperDeckTutorialGeometry,
        reduced: Boolean,
        density: Float,
        fontScale: Float,
    ) {
        for (index in CARD_COUNT - 1 downTo 0) {
            drawCard(canvas, index, pose, grab, geometry, reduced, density, fontScale)
        }
    }

    private fun drawCard(
        canvas: Canvas,
        index: Int,
        pose: PaperDeckTutorialPose,
        grab: Double,
        geometry: PaperDeckTutorialGeometry,
        reduced: Boolean,
        density: Float,
        fontScale: Float,
    ) {
        val width = geometry.width
        val height = geometry.height
        val step = pose.step
        val x = pose.x
        val flip = pose.flip
        val time = pose.time
        val depth = index - step
        val top = depth == 0
        val edgeOn = time >= 0.8417 && time < 0.875
        // `opacity: flip < 0.5 || edgeOn || depth < 0 || depth > 2 ? 0 : top ? 1 : rearAlpha`.
        if (flip < 0.5 || edgeOn || depth < 0 || depth > 2) return

        val advance = script.clamp(abs(x) / width)
        val remainingDepth = max(0.0, depth - advance)
        val rearFraction = if (depth > 0) remainingDepth / depth else 0.0
        val introRear = if (step == 0) script.track(time, script.INTRO_REAR) else 1.0
        val rearAlpha = if (step == 0) script.clamp((time - 1.1) / 0.2) else 1.0
        val lastThrow = top && index == 5 && time >= script.LAST_X[0][0]
        val even = depth % 2 == 0

        val tx = if (top) x else (if (even) -1.0 else 1.0) * width * 0.024 * rearFraction * introRear
        val ty = when {
            lastThrow -> geometry.screenHeight * script.track(time, script.LAST_Y)
            top -> -min(36.0, abs(x) * 0.065)
            else -> height * 0.011 * remainingDepth * introRear
        }
        val scale = if (top) 1.0 else 1.0 - 0.015 * remainingDepth * introRear
        // A sheet held near its bottom swings its top the other way: it pivots under the finger.
        val roll = when {
            lastThrow -> script.track(time, script.LAST_ROLL)
            top -> script.clamp(x / 22.0, -24.0, 24.0) * grab
            else -> (if (even) -0.85 else 1.4) * remainingDepth * introRear
        }
        val yaw = when {
            lastThrow -> 0.0
            index == 0 && flip < 1.0 -> 180.0 + flip * 180.0
            top && !reduced -> script.clamp(-x / 23.0, -16.0, 16.0)
            else -> 0.0
        }
        // A sheet held off the deck throws a wider, softer shadow; one lying on it barely any.
        val lift = if (top && !reduced) script.clamp(abs(x) / (width * 0.6)) else 0.0
        val alpha = if (top) 1.0 else rearAlpha
        if (alpha <= 0.0) return

        val bend = when {
            reduced || (index == 5 && step == 5 && time >= script.LAST_X[0][0]) -> 0.0
            step == index -> min(14.0, abs(x) / 28.0)
            else -> 3.5
        }
        val curve = (if (x < 0) -bend else bend).toFloat()
        val w = width.toFloat()
        val h = height.toFloat()

        val grouped = alpha < 1.0
        if (grouped) canvas.saveLayerAlpha(null, (alpha * 255).roundToInt())
        canvas.save()
        setFaceMatrix(tx, ty, scale, roll, yaw, w / 2f, h / 2f)
        canvas.concat(matrix)

        buildPaper(w, h, curve)
        drawShadow(canvas, lift, x)
        drawPaper(canvas)
        drawSheen(canvas, w, h, if (reduced || step != index) 0.0 else script.clamp(abs(x) / (width * 0.5)), x)
        drawCopy(canvas, index, w, h, density, fontScale)

        canvas.restore()
        if (grouped) canvas.restore()
    }

    /**
     * `projectedFace`: the matrix `P(1600) T S Rz Ry` restricted to the card's plane, about the card's
     * centre ([cx], [cy]). With a point (X, Y) from the centre: x' = s (X cos y cos z - Y sin z) + tx,
     * y' = s (X cos y sin z + Y cos z) + ty, over w = 1 + s X sin y / 1600. [roll] and [yaw] are in degrees.
     */
    private fun setFaceMatrix(tx: Double, ty: Double, scale: Double, roll: Double, yaw: Double, cx: Float, cy: Float) {
        val rz = Math.toRadians(roll)
        val ry = Math.toRadians(yaw)
        val cz = cos(rz)
        val sz = sin(rz)
        val cyaw = cos(ry)
        val syaw = sin(ry)
        matrixValues[Matrix.MSCALE_X] = (scale * cz * cyaw).toFloat()
        matrixValues[Matrix.MSKEW_X] = (-scale * sz).toFloat()
        matrixValues[Matrix.MTRANS_X] = tx.toFloat()
        matrixValues[Matrix.MSKEW_Y] = (scale * sz * cyaw).toFloat()
        matrixValues[Matrix.MSCALE_Y] = (scale * cz).toFloat()
        matrixValues[Matrix.MTRANS_Y] = ty.toFloat()
        matrixValues[Matrix.MPERSP_0] = (scale * syaw / PERSPECTIVE).toFloat()
        matrixValues[Matrix.MPERSP_1] = 0f
        matrixValues[Matrix.MPERSP_2] = 1f
        matrix.setValues(matrixValues)
        matrix.preTranslate(-cx, -cy)
        matrix.postTranslate(cx, cy)
    }

    /** The `d` of the animated `Path`: a rounded sheet whose top and bottom edges bow by [curve]. */
    private fun buildPaper(w: Float, h: Float, curve: Float) {
        paper.reset()
        paper.moveTo(26f, 5f)
        paper.quadTo(w * 0.5f, 5f + curve, w - 26f, 5f)
        paper.quadTo(w - 5f, 5f, w - 5f, 26f)
        paper.lineTo(w - 5f, h - 26f)
        paper.quadTo(w - 5f, h - 5f, w - 26f, h - 5f)
        paper.quadTo(w / 2f, h - 5f - curve, 26f, h - 5f)
        paper.quadTo(5f, h - 5f, 5f, h - 26f)
        paper.lineTo(5f, 26f)
        paper.quadTo(5f, 5f, 26f, 5f)
        paper.close()
    }

    /**
     * `shadowOpacity: 0.16 + 0.22 * lift`, `shadowRadius: 9 + 18 * lift`, `shadowOffset: (-x * 0.03, 4 + 14 * lift)`,
     * black. A shadow radius is twice the blur's sigma. Hardware canvases honour mask filters from API 28;
     * older devices draw no shadow.
     */
    private fun drawShadow(canvas: Canvas, lift: Double, x: Double) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val sigma = (9.0 + 18.0 * lift) / 2.0
        val bucket = (sigma * 8.0).roundToInt()
        val filter = shadowFilters.getOrPut(bucket) {
            // Android's blur takes a radius; sigma = 0.57735 * radius + 0.5.
            BlurMaskFilter(max(0.01f, (bucket / 8f - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL)
        }
        shadowPaint.maskFilter = filter
        shadowPaint.alpha = ((0.16 + 0.22 * lift) * 255).roundToInt()
        canvas.save()
        canvas.translate((-x * 0.03).toFloat(), (4.0 + 14.0 * lift).toFloat())
        canvas.drawPath(paper, shadowPaint)
        canvas.restore()
    }

    /** The fill (the gradient is in the path's bounding-box units, as in SVG) and its 1 dp edge, #E7E4EA. */
    private fun drawPaper(canvas: Canvas) {
        paper.computeBounds(bounds, true)
        gradientMatrix.setScale(bounds.width(), bounds.height())
        gradientMatrix.postTranslate(bounds.left, bounds.top)
        paperGradient.setLocalMatrix(gradientMatrix)
        paperPaint.shader = paperGradient
        canvas.drawPath(paper, paperPaint)
        canvas.drawPath(paper, edgePaint)
    }

    /**
     * Light skimming the bowed sheet: a band `0.3 * width` wide, transparent white at its sides and
     * 80% white in the middle, at [amount] times 0.9 opacity, riding the crest of the bend and clipped to the
     * sheet's rounded rectangle (radius 24).
     */
    private fun drawSheen(canvas: Canvas, w: Float, h: Float, amount: Double, x: Double) {
        val opacity = amount * 0.9
        if (opacity <= 0.0) return
        val sheenWidth = w * 0.3f
        if (sheenShader == null || sheenShaderWidth != sheenWidth) {
            sheenShader = LinearGradient(
                0f, 0f, sheenWidth, 0f,
                intArrayOf(0x00FFFFFF, 0xCCFFFFFF.toInt(), 0x00FFFFFF),
                null,
                Shader.TileMode.CLAMP,
            )
            sheenShaderWidth = sheenWidth
        }
        clip.reset()
        clip.addRoundRect(0f, 0f, w, h, 24f, 24f, Path.Direction.CW)
        val shift = ((if (x < 0) 0.62 else 0.08) * w - x * 0.12).toFloat()
        canvas.save()
        canvas.clipPath(clip)
        canvas.translate(shift, 0f)
        sheenPaint.shader = sheenShader
        sheenPaint.alpha = (opacity * 255).roundToInt()
        canvas.drawRect(0f, 0f, sheenWidth, h, sheenPaint)
        canvas.restore()
    }

    /**
     * `cardCopy` and `number`: the title and its emoji, centred in the sheet below a 10 dp top padding
     * with 22 dp at the sides, and the number 38 dp above the bottom. Sizes follow the system font scale up
     * to the caps of the React Native file (1.1 for the title, 1.4 for the emoji and the number). A line
     * taller than its font keeps its extra height above the text, like React Native on Android.
     */
    private fun drawCopy(canvas: Canvas, index: Int, w: Float, h: Float, density: Float, fontScale: Float) {
        val titleScale = min(fontScale, 1.1f)
        val smallScale = min(fontScale, 1.4f)
        val titleSize = 37f * titleScale
        val titleLine = 41f * titleScale
        val symbolSize = 28f * smallScale
        val symbolLine = 36f * smallScale

        titlePaint.textSize = titleSize
        titlePaint.letterSpacing = -0.5f / titleSize
        val lines = titleLinesFor(index, w - 44f, titleSize)
        titlePaint.getFontMetrics(metrics)
        val titleDescent = metrics.descent

        val block = lines.size * titleLine + 40f + symbolLine
        val blockTop = 10f + (h - 10f - block) / 2f
        lines.forEachIndexed { line, text ->
            val baseline = blockTop + line * titleLine + titleLine - titleDescent
            canvas.drawText(text, w / 2f, baseline, titlePaint)
        }

        val symbol = symbolImage(index, symbolSize, density)
        val symbolTop = blockTop + lines.size * titleLine + 40f
        val symbolBaseline = symbolTop + symbolLine - symbol.descent / density
        val symbolWidth = symbol.bitmap.width / density
        val symbolHeight = symbol.bitmap.height / density
        val symbolLeft = w / 2f - symbolWidth / 2f
        val symbolBitmapTop = symbolBaseline - symbol.baseline / density
        symbolBox.set(symbolLeft, symbolBitmapTop, symbolLeft + symbolWidth, symbolBitmapTop + symbolHeight)
        canvas.drawBitmap(symbol.bitmap, null, symbolBox, symbolPaint)

        numberPaint.textSize = 19f * smallScale
        numberPaint.getFontMetrics(metrics)
        val numberBaseline = h - 38f - metrics.descent
        canvas.drawText("\u2013 ${index + 1} \u2013", w / 2f, numberBaseline, numberPaint)
    }

    /** The title broken into lines no wider than [maxWidth]: the card's own line breaks, then words that wrap. */
    private fun titleLinesFor(index: Int, maxWidth: Float, size: Float): List<String> {
        if (titleLinesWidth != maxWidth || titleLinesSize != size) {
            titleLines.fill(null)
            titleLinesWidth = maxWidth
            titleLinesSize = size
        }
        titleLines[index]?.let { return it }
        val lines = ArrayList<String>()
        for (paragraph in script.STEPS[index].title.split('\n')) {
            var line = ""
            for (word in paragraph.split(' ')) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (line.isNotEmpty() && titlePaint.measureText(candidate) > maxWidth) {
                    lines.add(line)
                    line = word
                } else {
                    line = candidate
                }
            }
            lines.add(line)
        }
        titleLines[index] = lines
        return lines
    }

    /** The emoji of card [index] at [sizeDp], drawn once into a bitmap at the screen's density. */
    private fun symbolImage(index: Int, sizeDp: Float, density: Float): SymbolImage {
        val cached = symbols[index]
        if (cached != null && cached.sizeDp == sizeDp && cached.pixelsPerDp == density) return cached
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizeDp * density
            textAlign = Paint.Align.CENTER
        }
        val symbol = script.STEPS[index].symbol
        val font = paint.fontMetrics
        val padding = 4
        val width = ceil(paint.measureText(symbol)).toInt() + 2 * padding
        val height = ceil(font.descent - font.ascent).toInt() + 2 * padding
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val baseline = padding - font.ascent
        Canvas(bitmap).drawText(symbol, width / 2f, baseline, paint)
        val image = SymbolImage(sizeDp, density, bitmap, baseline, font.descent)
        symbols[index] = image
        return image
    }

    private companion object {
        const val CARD_COUNT = 6

        /** `P(1600)`: the perspective distance of the card matrix, in dp. */
        const val PERSPECTIVE = 1600f
    }
}
