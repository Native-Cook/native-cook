package com.ngocdevv.nativecook.blocks.lists.foryoucardstack

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.content.res.Resources
import androidx.core.content.res.ResourcesCompat
import com.ngocdevv.nativecook.R
import kotlin.math.roundToInt

internal enum class ForYouCoverKind(val title: String) {
    Loud("Loud Type"),
    Seeing("The Life of Colour"),
    Objects("The Shape of Everyday Objects"),
    Pages("Pages & Margins"),
    Sky("Blue Hours"),
    Arches("Soft Geometry"),
    Grid("Grid Works"),
    Lines("Flow Lines"),
    Shapes("Small Rooms")
}

private val PageColor = Color.parseColor("#ECD7D4")
private val InkColor = Color.parseColor("#161010")
private val PlaceholderColor = Color.parseColor("#CDB9B5")
private val DesignColor = Color.parseColor("#4E42F8")
private val NextCardColor = Color.parseColor("#E9488F")
private val PlayerColor = Color.parseColor("#4A44F5")
private val ReadingColor = Color.parseColor("#C26CF2")
private val WhiteColor = Color.WHITE
private val DimColor = Color.argb((.4f * 255f).roundToInt(), 255, 255, 255)
private val FaintColor = Color.argb((.28f * 255f).roundToInt(), 255, 255, 255)

private const val CoverWidth = 200f
private const val CoverHeight = 300f
private const val PageWidth = 190f
private const val PageHeight = 285f
private const val PageScale = .95f

private val passageLines = listOf(
    listOf("Colour", "is", "the", "most", "restless", "medium"),
    listOf("on", "paper.", "Trial,", "then", "error."),
    listOf("Our", "memory", "for", "colour", "is", "poor"),
    listOf("for", "what", "is", "in", "front", "of", "us.", "We", "rarely"),
    listOf("see", "a", "colour", "alone.", "Every", "colour"),
    listOf("depends", "on", "what", "stands", "beside", "it"),
)

/** Native canvas renderer: all coordinates use the RN block's point/dp coordinate system. */
internal class ForYouCardStackPainter(context: Context) {
    private val resources: Resources = context.resources
    private val anton = ResourcesCompat.getFont(context, R.font.foryoucardstack_anton_regular)
        ?: Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    private val interMedium = ResourcesCompat.getFont(context, R.font.foryoucardstack_inter_medium)
        ?: Typeface.create("sans-serif", Typeface.NORMAL)
    private val interRegular = ResourcesCompat.getFont(context, R.font.foryoucardstack_inter_regular)
        ?: Typeface.create("sans-serif", Typeface.NORMAL)
    private val interHeavy = ResourcesCompat.getFont(context, R.font.foryoucardstack_inter_extrabold)
        ?: Typeface.create("sans-serif", Typeface.BOLD)
    private val systemSans = Typeface.create("sans-serif", Typeface.NORMAL)
    private val serif = Typeface.create("serif", Typeface.NORMAL)
    private val serifBold = Typeface.create("serif", Typeface.BOLD)
    private val serifItalic = Typeface.create("serif", Typeface.ITALIC)

    private val avatar = BitmapFactory.decodeResource(resources, R.drawable.foryoucardstack_avatar)
    private val coverSky = BitmapFactory.decodeResource(resources, R.drawable.foryoucardstack_cover_sky)
    private val landscapePage = BitmapFactory.decodeResource(resources, R.drawable.foryoucardstack_page_landscape)
    private val portraitPage = BitmapFactory.decodeResource(resources, R.drawable.foryoucardstack_page_portrait)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val playerShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val coverColorCache = HashMap<String, Int>()
    private val camera = Camera()
    private val cameraMatrix = Matrix()

    init {
        // RN's `perspective: 1200` is a 1200dp camera distance. Android Camera
        // uses inches (72 points per inch), while the caller scales its native
        // canvas to dp before drawing, so 1200dp -> 1200/72 camera units.
        camera.setLocation(0f, 0f, -1_200f / 72f)
    }

    fun draw(canvas: Canvas, width: Float, height: Float, motion: ForYouCardStackMotion, reduced: Boolean) {
        canvas.drawColor(PageColor)
        val push = motion.push.coerceIn(0f, 1f)

        canvas.save()
        if (reduced) {
            val layer = canvas.saveLayerAlpha(null, ((1f - push) * 255f).roundToInt().coerceIn(0, 255))
            drawHome(canvas, width, motion.deck)
            canvas.restoreToCount(layer)
        } else {
            canvas.translate(-width * .3f * push, 0f)
            drawHome(canvas, width, motion.deck)
            fillPaint.color = Color.BLACK
            fillPaint.alpha = (.12f * push * 255f).roundToInt().coerceIn(0, 255)
            canvas.drawRect(-width, 0f, width * 2f, height, fillPaint)
        }
        canvas.restore()

        if (!reduced && push > 0f) {
            canvas.save()
            canvas.translate(width * (1f - push), 0f)
            playerShadowPaint.style = Paint.Style.FILL
            playerShadowPaint.color = PlayerColor
            playerShadowPaint.alpha = 255
            playerShadowPaint.setShadowLayer(18f, -4f, 0f, Color.argb((.18f * 255f).roundToInt(), 0, 0, 0))
            canvas.drawRect(0f, 0f, width, ForYouCardStackSpec.screenHeight + 100f, playerShadowPaint)
            playerShadowPaint.clearShadowLayer()
            canvas.restore()
        }

        canvas.save()
        if (reduced) {
            val layer = canvas.saveLayerAlpha(null, (push * 255f).roundToInt().coerceIn(0, 255))
            drawPlayer(canvas, width, motion)
            canvas.restoreToCount(layer)
        } else {
            canvas.translate(width * (1f - push), 0f)
            drawPlayer(canvas, width, motion)
        }
        canvas.restore()
    }

    private fun drawHome(canvas: Canvas, width: Float, deck: Float) {
        drawTextTop(canvas, "For you", 28.6f, 81.6f, 29.4f, InkColor, interMedium)
        drawAvatar(canvas, width - 30f - 52f, 75f, 52f)

        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 1.9f
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.color = InkColor
        canvas.drawCircle(34f + 8.8f, 160f + 8.8f, 7.6f, strokePaint)
        canvas.drawLine(34f + 14.4f, 160f + 14.4f, 34f + 20f, 160f + 20f, strokePaint)
        drawTextTop(canvas, "Book or author", 74f, 162f, 15.4f, PlaceholderColor, interRegular)

        fillPaint.style = Paint.Style.FILL
        fillPaint.color = NextCardColor
        fillPaint.alpha = 255
        canvas.drawRoundRect(0f, 830f, width, 1_030f, 32f, 32f, fillPaint)
        fillPaint.color = DesignColor
        canvas.drawRoundRect(0f, 240f, width, 840f, 32f, 32f, fillPaint)

        drawTextTop(canvas, "Design", 28f, 274.7f, 29.4f, WhiteColor, interMedium)
        fillPaint.color = Color.argb((.4f * 255f).roundToInt(), 255, 255, 255)
        canvas.drawRoundRect(width - 30f - 40f, 288.5f, width - 30f, 290.5f, 1f, 1f, fillPaint)
        drawTextTop(canvas, "NEW & TRENDING", 29f, 328.7f, 13.7f, WhiteColor, interHeavy)
        drawTextTop(canvas, "SEE ALL", width - 34f, 328.7f, 13.7f, WhiteColor, interHeavy, Paint.Align.RIGHT)

        val rowTop = 366f
        val coverWidth = 124.3f
        val coverHeight = 186f
        val rowStep = 135.6f
        val chartsStep = rowStep
        val deckLeft = 40.7f
        val deckValue = deck

        val spines = intArrayOf(Color.parseColor("#F2C21A"), Color.parseColor("#B9C2FE"), Color.parseColor("#9FB0F0"))
        for (index in spines.indices.reversed()) {
            val inset = 4f * (index + 2)
            val left = deckLeft - 9.5f * (index + 2)
            fillPaint.color = spines[index]
            fillPaint.alpha = 255
            canvas.drawRect(left, rowTop + inset, left + coverWidth, rowTop + coverHeight - inset, fillPaint)
        }

        for ((index, kind) in listOf(ForYouCoverKind.Objects, ForYouCoverKind.Pages).withIndex()) {
            drawCover(canvas, kind, deckLeft + rowStep * (index + 1) + rowStep * deckValue, rowTop, coverWidth, coverHeight)
        }

        val seeingLeft = deckLeft - 9.5f * (1f - deckValue)
        val seeingScale = (178f / 186f) + (1f - 178f / 186f) * deckValue
        drawCoverCentered(canvas, ForYouCoverKind.Seeing, seeingLeft, rowTop, coverWidth, coverHeight, seeingScale)
        drawCover(canvas, ForYouCoverKind.Loud, deckLeft + rowStep * deckValue, rowTop, coverWidth, coverHeight)

        drawTextTop(canvas, "TOP CHARTS", 28f, 586.4f, 13.7f, WhiteColor, interHeavy)
        for ((index, kind) in listOf(ForYouCoverKind.Sky, ForYouCoverKind.Arches, ForYouCoverKind.Grid).withIndex()) {
            drawCover(canvas, kind, 28f + chartsStep * index, 625f, coverWidth, coverHeight)
        }
    }

    private fun drawPlayer(canvas: Canvas, width: Float, motion: ForYouCardStackMotion) {
        fillPaint.style = Paint.Style.FILL
        fillPaint.color = PlayerColor
        fillPaint.alpha = 255
        canvas.drawRect(0f, 0f, width, ForYouCardStackSpec.screenHeight + 100f, fillPaint)

        val look = forYouLook(motion.open, motion.close)
        drawWave(canvas, width, look.wave)
        drawPassage(canvas, motion.words, look.text)

        val neighbourW = PageWidth * .87f
        val neighbourH = PageHeight * .87f
        drawCover(canvas, ForYouCoverKind.Lines, 22f - neighbourW, 291.5f - neighbourH / 2f, neighbourW, neighbourH, look.neighbours)
        drawCover(canvas, ForYouCoverKind.Shapes, 348f, 291.5f - neighbourH / 2f, neighbourW, neighbourH, look.neighbours)

        drawBook(canvas, look)
        val titleShift = 64f * look.title
        drawTextTop(canvas, "Ines Marlowe", width / 2f, 477.8f + titleShift, 12.4f, DimColor, interRegular, Paint.Align.CENTER)
        drawTextTop(canvas, "Inner Light", width / 2f, 494.1f + titleShift, 27f, WhiteColor, interMedium, Paint.Align.CENTER)
        drawTextTop(canvas, "of Color", width / 2f, 534.1f + titleShift, 27f, WhiteColor, interMedium, Paint.Align.CENTER)
        drawTextTop(canvas, "Chapter 1 of 25", width / 2f, 576.6f + titleShift, 13f, FaintColor, interRegular, Paint.Align.CENTER, alpha = look.chapter)

        drawControls(canvas, width, motion)
        drawProgress(canvas, width)
        drawBack(canvas, motion.pressBack)
        drawBookIcon(canvas)
    }

    private fun drawWave(canvas: Canvas, width: Float, amount: Float) {
        val left = 443f
        val controlX = 230f
        val controlY = 412f
        val right = 430f
        val offset = -(left + 40f) * (1f - amount)
        val path = Path().apply {
            moveTo(0f, offset)
            lineTo(width, offset)
            lineTo(width, right + offset)
            quadTo(controlX, controlY + offset, 0f, left + offset)
            close()
        }
        fillPaint.color = ReadingColor
        fillPaint.alpha = 255
        canvas.drawPath(path, fillPaint)
    }

    private fun drawPassage(canvas: Canvas, words: Float, opacity: Float) {
        val readingPaint = textPaint
        readingPaint.typeface = serif
        readingPaint.textSize = 19.3f
        readingPaint.textAlign = Paint.Align.LEFT
        readingPaint.letterSpacing = 0f
        readingPaint.color = WhiteColor
        val margin = 19.3f * .25f
        val lineHeight = 33f
        val fontMetrics = readingPaint.fontMetrics
        for ((lineIndex, line) in passageLines.withIndex()) {
            var x = 36.6f
            val top = 141f + lineHeight * lineIndex
            val baseline = top - fontMetrics.ascent
            for ((wordIndex, word) in line.withIndex()) {
                val at = lineIndex + (wordIndex + 1).toFloat() / line.size
                readingPaint.alpha = if (at <= words + .001f) {
                    (opacity * 255f).roundToInt()
                } else {
                    (opacity * .42f * 255f).roundToInt()
                }
                canvas.drawText(word, x, baseline, readingPaint)
                x += readingPaint.measureText(word) + margin
            }
        }
        readingPaint.alpha = 255
    }

    private fun drawBook(canvas: Canvas, look: ForYouLook) {
        canvas.save()
        canvas.translate(look.spineX, look.centerY - PageHeight / 2f)
        canvas.scale(look.scale, look.scale, 0f, PageHeight / 2f)

        drawSpreadText(canvas, 0f, 0f, PageWidth, PageHeight)
        val leaves = listOf(
            Triple(0, look.leaf0, 10 + if (look.leaf0 >= .5f) 0 else 0),
            Triple(1, look.leaf1, 10 + if (look.leaf1 >= .5f) 1 else -1),
            Triple(2, look.leaf2, 10 + if (look.leaf2 >= .5f) 2 else -2),
        ).sortedBy { entry ->
            val f = entry.second
            if (f > 0f && f < 1f) 30 else entry.third
        }
        for ((index, flip, _) in leaves) drawLeaf(canvas, index, flip)
        canvas.restore()
    }

    private fun drawLeaf(canvas: Canvas, index: Int, flip: Float) {
        canvas.save()
        camera.save()
        camera.rotateY(-180f * flip)
        camera.getMatrix(cameraMatrix)
        camera.restore()
        cameraMatrix.preTranslate(0f, -PageHeight / 2f)
        cameraMatrix.postTranslate(0f, PageHeight / 2f)
        canvas.concat(cameraMatrix)

        val back = flip >= .5f
        if (back) {
            // Camera's back face is mirrored; RN's nested rotateY(180deg) corrects the artwork.
            canvas.scale(-1f, 1f, PageWidth / 2f, PageHeight / 2f)
        }
        when (index) {
            0 -> if (back) drawLandscapeHalf(canvas, right = false) else drawCoverDesignAtPage(canvas, ForYouCoverKind.Seeing)
            1 -> if (back) drawPortraitPage(canvas) else drawLandscapeHalf(canvas, right = true)
            else -> if (back) drawSpreadArt(canvas, 0f, 0f, PageWidth, PageHeight) else drawSpreadText(canvas, 0f, 0f, PageWidth, PageHeight)
        }
        canvas.restore()
    }

    private fun drawControls(canvas: Canvas, width: Float, motion: ForYouCardStackMotion) {
        val centerX = width / 2f
        val y = 686f
        canvas.save()
        canvas.scale(motion.pressPlay, motion.pressPlay, centerX, y)
        fillPaint.color = WhiteColor
        fillPaint.alpha = 255
        canvas.drawCircle(centerX, y, 23f, fillPaint)
        val iconAlpha = ((1f - motion.playing) * 255f).roundToInt().coerceIn(0, 255)
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 1.5f
        strokePaint.strokeJoin = Paint.Join.ROUND
        strokePaint.color = PlayerColor
        strokePaint.alpha = iconAlpha
        val play = Path().apply {
            moveTo(centerX - 3.2f, y - 5.2f)
            lineTo(centerX - 3.2f, y + 5.2f)
            lineTo(centerX + 5f, y)
            close()
        }
        canvas.drawPath(play, strokePaint)
        val pauseAlpha = (motion.playing * 255f).roundToInt().coerceIn(0, 255)
        fillPaint.color = PlayerColor
        fillPaint.alpha = pauseAlpha
        // The RN pause SVG is a 16x16 glyph centered in the 46pt button:
        // local bars x=4.4..6.4 and 9.6..11.6, y=3..13.
        canvas.drawRoundRect(centerX - 3.6f, y - 5f, centerX - 1.6f, y + 5f, 1f, 1f, fillPaint)
        canvas.drawRoundRect(centerX + 1.6f, y - 5f, centerX + 3.6f, y + 5f, 1f, 1f, fillPaint)
        canvas.restore()

        drawSkip(canvas, 201f - 57f, y, false)
        drawSkip(canvas, 201f + 57f, y, true)
    }

    private fun drawSkip(canvas: Canvas, x: Float, y: Float, forward: Boolean) {
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 1.7f
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.strokeJoin = Paint.Join.ROUND
        strokePaint.color = WhiteColor
        strokePaint.alpha = 255
        val path = Path()
        val scale = 22f / 24f
        val left = x - 11f
        val top = y - 11f
        val arcBounds = RectF(
            left + 5.5f * scale,
            top + 5.5f * scale,
            left + 18.5f * scale,
            top + 18.5f * scale,
        )
        if (!forward) {
            path.arcTo(arcBounds, 180f, -313.75f, true)
            path.moveTo(left + 7.3f * scale, top + 3.8f * scale)
            path.lineTo(left + 7.3f * scale, top + 7.4f * scale)
            path.lineTo(left + 10.9f * scale, top + 7.4f * scale)
        } else {
            path.arcTo(arcBounds, 0f, 313.75f, true)
            path.moveTo(left + 16.7f * scale, top + 3.8f * scale)
            path.lineTo(left + 16.7f * scale, top + 7.4f * scale)
            path.lineTo(left + 13.1f * scale, top + 7.4f * scale)
        }
        canvas.drawPath(path, strokePaint)
    }

    private fun drawProgress(canvas: Canvas, width: Float) {
        val left = 34.7f
        val right = 35f
        fillPaint.color = FaintColor
        fillPaint.alpha = FaintColor ushr 24
        canvas.drawRect(left, 800f, width - right, 801.5f, fillPaint)
        fillPaint.color = WhiteColor
        fillPaint.alpha = 255
        canvas.drawRect(left, 800f, left + (width - left - right) * .012f, 801.5f, fillPaint)
        drawTextTop(canvas, "00:00:10", left, 804f, 9.5f, DimColor, interRegular)
        drawTextTop(canvas, "01:42:21", width - right, 804f, 9.5f, DimColor, interRegular, Paint.Align.RIGHT)
    }

    private fun drawBack(canvas: Canvas, scale: Float) {
        canvas.save()
        canvas.scale(scale, scale, 43.2f, 89.65f)
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 1.5f
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.strokeJoin = Paint.Join.ROUND
        strokePaint.color = WhiteColor
        strokePaint.alpha = 255
        val path = Path().apply {
            moveTo(48.9f, 89.65f)
            lineTo(37.9f, 89.65f)
            moveTo(43.1f, 84.3f)
            lineTo(37.7f, 89.65f)
            lineTo(43.1f, 95f)
        }
        canvas.drawPath(path, strokePaint)
        canvas.restore()
    }

    private fun drawBookIcon(canvas: Canvas) {
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 1.4f
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.strokeJoin = Paint.Join.ROUND
        strokePaint.color = WhiteColor
        strokePaint.alpha = 255
        val left = 350f
        val top = 83.3f
        val path = Path().apply {
            moveTo(left + 8.35f, top + 2.4f)
            cubicTo(left + 6.6f, top + 1.1f, left + 4f, top + .8f, left + .8f, top + 1.1f)
            lineTo(left + .8f, top + 12.7f)
            cubicTo(left + 4f, top + 12.4f, left + 6.6f, top + 12.7f, left + 8.35f, top + 14f)
            cubicTo(left + 10.1f, top + 12.7f, left + 12.7f, top + 12.4f, left + 15.9f, top + 12.7f)
            lineTo(left + 15.9f, top + 1.1f)
            cubicTo(left + 12.7f, top + .8f, left + 10.1f, top + 1.1f, left + 8.35f, top + 2.4f)
            moveTo(left + 8.35f, top + 2.4f)
            lineTo(left + 8.35f, top + 14f)
        }
        canvas.drawPath(path, strokePaint)
    }

    private fun drawAvatar(canvas: Canvas, left: Float, top: Float, size: Float) {
        val path = Path().apply { addCircle(left + size / 2f, top + size / 2f, size / 2f, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(path)
        bitmapPaint.alpha = 255
        canvas.drawBitmap(avatar, null, RectF(left, top, left + size, top + size), bitmapPaint)
        canvas.restore()
    }

    private fun drawCover(
        canvas: Canvas,
        kind: ForYouCoverKind,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        alpha: Float = 1f,
    ) {
        canvas.save()
        canvas.clipRect(left, top, left + width, top + height)
        canvas.translate(left, top)
        canvas.scale(width / CoverWidth, height / CoverHeight)
        fillPaint.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
        drawCoverDesign(canvas, kind, alpha)
        canvas.restore()
    }

    private fun drawCoverCentered(
        canvas: Canvas,
        kind: ForYouCoverKind,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        scale: Float,
    ) {
        val scaledWidth = width * scale
        val scaledHeight = height * scale
        drawCover(canvas, kind, left + (width - scaledWidth) / 2f, top + (height - scaledHeight) / 2f, scaledWidth, scaledHeight)
    }

    private fun drawCoverDesign(canvas: Canvas, kind: ForYouCoverKind, alpha: Float = 1f) {
        fun fill(color: Int, opacity: Float = alpha) {
            fillPaint.style = Paint.Style.FILL
            fillPaint.color = color
            fillPaint.alpha = (opacity * 255f).roundToInt().coerceIn(0, 255)
        }
        fun stroke(color: Int, width: Float, opacity: Float = alpha) {
            strokePaint.style = Paint.Style.STROKE
            strokePaint.strokeWidth = width
            strokePaint.strokeCap = Paint.Cap.ROUND
            strokePaint.strokeJoin = Paint.Join.ROUND
            strokePaint.color = color
            strokePaint.alpha = (opacity * 255f).roundToInt().coerceIn(0, 255)
        }

        when (kind) {
            ForYouCoverKind.Loud -> {
                fill(color("#EE3A0A"))
                canvas.drawRect(0f, 0f, 200f, 300f, fillPaint)
                fill(color("#F6C80E"))
                canvas.drawPath(
                    Path().apply {
                        moveTo(0f, 170f)
                        lineTo(170f, 98f)
                        lineTo(170f, 135f)
                        lineTo(108f, 300f)
                        lineTo(0f, 300f)
                        close()
                    },
                    fillPaint,
                )
                fill(color("#F2A3BC"))
                canvas.drawPath(
                    Path().apply {
                        moveTo(0f, 0f)
                        lineTo(170f, 0f)
                        lineTo(170f, 98f)
                        lineTo(0f, 170f)
                        close()
                    },
                    fillPaint,
                )
                for (index in 0..5) {
                    fill(color("#2A1A14"))
                    val width = if (index % 3 == 2) 34f else 46f
                    canvas.drawRect(118f, 112f + index * 9f, 118f + width, 116f + index * 9f, fillPaint)
                }
                for (index in 0..4) {
                    fill(color("#2A1A14"))
                    val width = if (index % 2 == 1) 46f else 58f
                    canvas.drawRect(12f, 232f + index * 8f, 12f + width, 235f + index * 8f, fillPaint)
                }
                textPaint.setShadowLayer(1.5f, 0f, 0f, color("#E8456A"))
                drawTextTop(canvas, "EXTRA", 12f, 14f, 52f, color("#FBD7E2"), anton)
                drawTextTop(canvas, "LOUD", 12f, 64f, 52f, color("#FBD7E2"), anton)
                textPaint.clearShadowLayer()
            }
            ForYouCoverKind.Seeing -> {
                fill(color("#E9B200"))
                canvas.drawRect(0f, 0f, 200f, 300f, fillPaint)
                drawEye(canvas, 98f, color("#2D55D6"), alpha)
                drawEye(canvas, 196f, color("#E2321E"), alpha)
            }
            ForYouCoverKind.Objects -> {
                fill(color("#E2C81C"))
                canvas.drawRect(0f, 0f, 200f, 300f, fillPaint)
                fill(color("#C2401C"))
                val pot = Path().apply {
                    moveTo(28f, 300f)
                    lineTo(30f, 262f)
                    quadTo(24f, 236f, 46f, 214f)
                    lineTo(52f, 184f)
                    quadTo(49f, 172f, 58f, 166f)
                    lineTo(60f, 152f)
                    lineTo(66f, 152f)
                    lineTo(68f, 166f)
                    quadTo(77f, 172f, 74f, 184f)
                    lineTo(80f, 214f)
                    quadTo(92f, 226f, 96f, 244f)
                    lineTo(104f, 228f)
                    quadTo(118f, 204f, 132f, 214f)
                    quadTo(140f, 230f, 120f, 252f)
                    quadTo(108f, 266f, 104f, 276f)
                    lineTo(106f, 300f)
                    close()
                }
                canvas.drawPath(pot, fillPaint)
                stroke(color("#2A1A10"), 2.4f)
                canvas.drawPath(
                    Path().apply {
                        moveTo(104f, 236f)
                        quadTo(126f, 206f, 136f, 220f)
                        quadTo(142f, 240f, 112f, 262f)
                    },
                    strokePaint,
                )
                fill(color("#8D7A3A"))
                canvas.drawRect(132f, 196f, 172f, 199f, fillPaint)
                canvas.drawRect(140f, 204f, 172f, 207f, fillPaint)
                drawTextBlock(canvas, listOf("SHAPE", "of EVERYDAY", "OBJECTS"), 112f, 40f, 18f, 26f, color("#6A5A2A"), systemSans, letterSpacing = 2.5f)
                drawTextTop(canvas, "The", 14f, 40f, 12f, color("#6A5A2A"), serifItalic)
            }
            ForYouCoverKind.Pages -> {
                fill(color("#EC3A1E"))
                canvas.drawRect(0f, 0f, 200f, 300f, fillPaint)
                fill(color("#F7B8A8"))
                for (index in 0..2) {
                    canvas.drawRect(40f, 196f + index * 9f, 160f, 199f + index * 9f, fillPaint)
                }
                fill(WhiteColor)
                canvas.drawRect(58f, 252f, 144f, 259f, fillPaint)
                drawTextBlock(canvas, listOf("Layout", "of", "Books"), 100f, 34f, 40f, 46f, WhiteColor, serifBold)
                drawTextTop(canvas, "The", 92f, 24f, 12f, WhiteColor, serifItalic)
            }
            ForYouCoverKind.Sky -> {
                bitmapPaint.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
                drawBitmapCover(canvas, coverSky, RectF(0f, 0f, 200f, 300f))
                drawTextTop(canvas, "THE", 30f, 18f, 26f, WhiteColor, serifItalic, letterSpacing = 2f)
                drawTextTop(canvas, "SKY", 70f, 50f, 26f, WhiteColor, serifItalic, letterSpacing = 2f)
                drawTextTop(canvas, "DAYS", 112f, 82f, 26f, WhiteColor, serifItalic, letterSpacing = 2f)
            }
            ForYouCoverKind.Arches -> {
                fill(color("#3A6FE0"))
                canvas.drawRect(0f, 0f, 200f, 300f, fillPaint)
                fill(color("#2FA05C"))
                canvas.drawRect(0f, 0f, 96f, 86f, fillPaint)
                fill(color("#14603C"))
                canvas.drawPath(
                    Path().apply {
                        moveTo(0f, 300f)
                        lineTo(0f, 150f)
                        quadTo(0f, 98f, 48f, 98f)
                        quadTo(96f, 98f, 96f, 150f)
                        lineTo(96f, 300f)
                        close()
                    },
                    fillPaint,
                )
                fill(color("#F1EEE6"))
                canvas.drawCircle(148f, 52f, 48f, fillPaint)
                fill(color("#A9C6F2"))
                for (centerY in listOf(120f, 170f, 220f)) {
                    canvas.drawPath(
                        Path().apply {
                            moveTo(100f, centerY)
                            arcTo(RectF(100f, centerY - 48f, 196f, centerY + 48f), 180f, -180f)
                            close()
                        },
                        fillPaint,
                    )
                }
                fill(color("#FFFDF7"))
                canvas.drawRect(0f, 250f, 120f, 300f, fillPaint)
                drawTextBlock(canvas, listOf("MARA", "OKONJO"), 8f, 258f, 17f, 18f, color("#23232A"), serif, left = true, letterSpacing = 1f)
            }
            ForYouCoverKind.Grid -> {
                fill(color("#F2C21A"))
                canvas.drawRect(0f, 0f, 200f, 300f, fillPaint)
                stroke(color("#1A1410"), 3f)
                canvas.drawPath(
                    Path().apply {
                        moveTo(120f, 40f)
                        quadTo(160f, 30f, 176f, 60f)
                        moveTo(150f, 120f)
                        lineTo(196f, 96f)
                    },
                    strokePaint,
                )
                canvas.save()
                canvas.rotate(-72f, 110f, 150f)
                drawTextTop(canvas, "CRASHING", 110f, 100f, 96f, color("#E2231A"), anton, Paint.Align.CENTER)
                canvas.restore()
            }
            ForYouCoverKind.Lines -> {
                fill(color("#5EC3F2"))
                canvas.drawRect(0f, 0f, 200f, 300f, fillPaint)
                val ys = floatArrayOf(40f, 90f, 140f, 190f, 240f)
                for (i in ys.indices) {
                    stroke(if (i % 2 == 1) color("#1F3FA8") else color("#0E2B78"), 10f)
                    val y = ys[i]
                    canvas.drawPath(
                        Path().apply {
                            moveTo(-10f, y)
                            cubicTo(40f, y - 40f, 80f, y + 50f, 120f, y)
                            cubicTo(160f, y - 50f, 190f, y - 30f, 220f, y + 10f)
                        },
                        strokePaint,
                    )
                }
                fill(WhiteColor)
                canvas.drawRect(18f, 226f, 88f, 272f, fillPaint)
            }
            ForYouCoverKind.Shapes -> {
                fill(color("#F6A6C8"))
                canvas.drawRect(0f, 0f, 200f, 300f, fillPaint)
                fill(color("#FFD43A"))
                canvas.drawRect(0f, 170f, 200f, 300f, fillPaint)
                fill(color("#F04A3A"))
                canvas.drawRect(24f, 30f, 110f, 140f, fillPaint)
                fill(WhiteColor)
                canvas.drawRect(110f, 196f, 174f, 266f, fillPaint)
                fill(color("#2B2B2B"))
                for (index in 0..4) {
                    canvas.drawRect(120f, 206f + index * 11f, 164f, 210f + index * 11f, fillPaint)
                }
                fill(WhiteColor, alpha * .8f)
                canvas.drawOval(RectF(124f, 60f, 176f, 112f), fillPaint)
            }
        }
        fillPaint.alpha = 255
        bitmapPaint.alpha = 255
    }

    private fun color(hex: String): Int = coverColorCache.getOrPut(hex) { Color.parseColor(hex) }

    private fun drawEye(canvas: Canvas, y: Float, iris: Int, alpha: Float) {
        val path = Path().apply {
            moveTo(30f, y)
            quadTo(100f, y - 52f, 170f, y)
            quadTo(100f, y + 52f, 30f, y)
            close()
        }
        fillPaint.color = Color.argb((alpha * 255f).roundToInt(), 255, 253, 246)
        fillPaint.alpha = Color.alpha(fillPaint.color)
        canvas.drawPath(path, fillPaint)
        fillPaint.color = iris
        fillPaint.alpha = (alpha * 255f).roundToInt()
        canvas.drawCircle(100f, y, 24f, fillPaint)
        fillPaint.color = Color.argb((alpha * 255f).roundToInt(), 22, 22, 26)
        fillPaint.alpha = Color.alpha(fillPaint.color)
        canvas.drawCircle(100f, y, 11f, fillPaint)
    }

    private fun drawSpreadText(canvas: Canvas, left: Float, top: Float, width: Float, height: Float) {
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(width / 100f, height / 146f)
        fillPaint.color = WhiteColor
        fillPaint.alpha = 255
        canvas.drawRect(0f, 0f, 100f, 146f, fillPaint)
        fillPaint.color = Color.parseColor("#2B2B2B")
        canvas.drawRect(10f, 14f, 50f, 18f, fillPaint)
        fillPaint.color = Color.parseColor("#9A9AA2")
        for (index in 0..8) {
            val ruleWidth = if (index % 4 == 3) 52f else 78f
            canvas.drawRect(10f, 28f + index * 7f, 10f + ruleWidth, 30.4f + index * 7f, fillPaint)
        }
        canvas.restore()
    }

    private fun drawSpreadArt(canvas: Canvas, left: Float, top: Float, width: Float, height: Float) {
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(width / 100f, height / 146f)
        fillPaint.color = Color.parseColor("#FBF6EA")
        fillPaint.alpha = 255
        canvas.drawRect(0f, 0f, 100f, 146f, fillPaint)
        fillPaint.color = Color.parseColor("#F7C531")
        canvas.drawRect(6f, 6f, 48f, 48f, fillPaint)
        fillPaint.color = Color.parseColor("#2D55D6")
        canvas.drawCircle(74f, 27f, 20f, fillPaint)
        fillPaint.color = Color.parseColor("#E2321E")
        canvas.drawPath(
            Path().apply {
                moveTo(8f, 96f)
                arcTo(RectF(8f, 56f, 88f, 136f), 180f, 180f)
                close()
            },
            fillPaint,
        )
        fillPaint.color = Color.parseColor("#16161A")
        canvas.drawPath(
            Path().apply {
                moveTo(50f, 104f)
                lineTo(54f, 118f)
                lineTo(68f, 114f)
                lineTo(58f, 124f)
                lineTo(68f, 134f)
                lineTo(54f, 130f)
                lineTo(50f, 144f)
                lineTo(46f, 130f)
                lineTo(32f, 134f)
                lineTo(42f, 124f)
                lineTo(32f, 114f)
                lineTo(46f, 118f)
                close()
            },
            fillPaint,
        )
        fillPaint.color = Color.parseColor("#2FA67A")
        canvas.drawCircle(18f, 126f, 9f, fillPaint)
        fillPaint.color = Color.parseColor("#F29FC0")
        canvas.drawRect(74f, 110f, 94f, 140f, fillPaint)
        fillPaint.color = Color.parseColor("#16161A")
        canvas.drawPath(
            Path().apply {
                moveTo(58f, 52f)
                lineTo(94f, 52f)
                lineTo(76f, 82f)
                close()
            },
            fillPaint,
        )
        strokePaint.color = Color.parseColor("#16161A")
        strokePaint.alpha = 255
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 4f
        canvas.drawCircle(26f, 68f, 10f, strokePaint)
        canvas.restore()
    }

    private fun drawLandscapeHalf(canvas: Canvas, right: Boolean) {
        canvas.save()
        canvas.clipRect(0f, 0f, PageWidth, PageHeight)
        bitmapPaint.alpha = 255
        val left = if (right) -190f else 0f
        drawBitmapCover(canvas, landscapePage, RectF(left, 0f, left + 380f, 285f))
        canvas.restore()
    }

    private fun drawPortraitPage(canvas: Canvas) {
        bitmapPaint.alpha = 255
        drawBitmapCover(canvas, portraitPage, RectF(0f, 0f, PageWidth, PageHeight))
    }

    private fun drawCoverDesignAtPage(canvas: Canvas, kind: ForYouCoverKind) {
        canvas.save()
        canvas.scale(PageScale, PageScale)
        drawCoverDesign(canvas, kind)
        canvas.restore()
    }

    private fun drawBitmapCover(canvas: Canvas, bitmap: android.graphics.Bitmap, destination: RectF) {
        val sourceWidth = bitmap.width.toFloat()
        val sourceHeight = bitmap.height.toFloat()
        val sourceAspect = sourceWidth / sourceHeight
        val destinationAspect = destination.width() / destination.height()
        val source = if (sourceAspect > destinationAspect) {
            val croppedWidth = sourceHeight * destinationAspect
            Rect(((sourceWidth - croppedWidth) / 2f).roundToInt(), 0, ((sourceWidth + croppedWidth) / 2f).roundToInt(), bitmap.height)
        } else {
            val croppedHeight = sourceWidth / destinationAspect
            Rect(0, ((sourceHeight - croppedHeight) / 2f).roundToInt(), bitmap.width, ((sourceHeight + croppedHeight) / 2f).roundToInt())
        }
        bitmapPaint.alpha = 255
        canvas.drawBitmap(bitmap, source, destination, bitmapPaint)
    }

    private fun drawTextBlock(
        canvas: Canvas,
        lines: List<String>,
        centerX: Float,
        top: Float,
        size: Float,
        lineHeight: Float,
        color: Int,
        typeface: Typeface,
        left: Boolean = false,
        letterSpacing: Float = 0f,
    ) {
        for ((index, line) in lines.withIndex()) {
            drawTextTop(canvas, line, if (left) centerX else centerX, top + index * lineHeight, size, color, typeface, if (left) Paint.Align.LEFT else Paint.Align.CENTER, letterSpacing = letterSpacing)
        }
    }

    private fun drawTextTop(
        canvas: Canvas,
        text: String,
        x: Float,
        top: Float,
        size: Float,
        color: Int,
        typeface: Typeface,
        align: Paint.Align = Paint.Align.LEFT,
        letterSpacing: Float = 0f,
        alpha: Float = 1f,
    ) {
        textPaint.style = Paint.Style.FILL
        textPaint.typeface = typeface
        textPaint.textSize = size
        textPaint.textAlign = align
        textPaint.letterSpacing = if (size > 0f) letterSpacing / size else 0f
        textPaint.color = color
        textPaint.alpha = (Color.alpha(color) * alpha).roundToInt().coerceIn(0, 255)
        val metrics = textPaint.fontMetrics
        canvas.drawText(text, x, top - metrics.ascent, textPaint)
        textPaint.alpha = 255
    }
}
