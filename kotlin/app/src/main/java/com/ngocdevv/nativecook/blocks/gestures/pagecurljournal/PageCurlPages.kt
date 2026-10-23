package com.ngocdevv.nativecook.blocks.gestures.pagecurljournal

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The four approved printed pages. They are rendered once into bitmaps; idle drawing and every curl frame
 * reuse those bitmaps, so the paper tile, gradients and typography never get rebuilt per frame.
 */
internal class PageCurlPages(
    widthDp: Float,
    heightDp: Float,
    private val density: Float,
    fonts: FontFamily.Resolver,
    private val roman: FontFamily,
    private val italic: FontFamily,
    private val paper: ImageBitmap,
) {
    val widthPx = (widthDp * density).roundToInt()
    val heightPx = (heightDp * density).roundToInt()

    /** Pixels per reference point. */
    private val k = widthPx / REF.WIDTH

    /** Reference points to dp, used by Compose text sizes. */
    private val s = widthDp / REF.WIDTH
    private val w = widthPx.toFloat()
    private val blockWidth = ((REF.WIDTH - 2f * BOOK.MARGIN) * k).roundToInt()
    private val blockX = BOOK.MARGIN * k
    private val measurer = TextMeasurer(fonts, Density(density, 1f), LayoutDirection.Ltr, 96)

    private val renderedPages by lazy(LazyThreadSafetyMode.NONE) {
        List(4) { index -> render(index) }
    }

    /** Draws an already-rendered page. */
    fun draw(scope: DrawScope, index: Int) {
        scope.drawImage(renderedPages[index])
    }

    /** The same cached pages are uploaded for the AGSL curl. */
    fun snapshots(): List<Bitmap> = renderedPages.map { it.asAndroidBitmap() }

    private fun render(index: Int): ImageBitmap {
        val image = ImageBitmap(widthPx, heightPx)
        CanvasDrawScope().draw(
            Density(density, 1f),
            LayoutDirection.Ltr,
            Canvas(image),
            Size(widthPx.toFloat(), heightPx.toFloat()),
        ) {
            sheet()
            when (index) {
                0 -> titlePage()
                1 -> prefacePage()
                2 -> forewordPage()
                else -> coverPage()
            }
        }
        return image
    }

    private fun serifStyle(
        size: Float,
        line: Float,
        color: Color = PAPER.ink,
        italicFace: Boolean = false,
        align: TextAlign = TextAlign.Unspecified,
    ) = TextStyle(
        color = color,
        fontSize = (size * s).sp,
        lineHeight = (line * s).sp,
        fontFamily = if (italicFace) italic else roman,
        textAlign = align,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

    private fun capsStyle(
        size: Float,
        color: Color = PAPER.mute,
        align: TextAlign = TextAlign.Unspecified,
    ) = TextStyle(
        color = color,
        fontSize = (size * s).sp,
        lineHeight = (size * 1.4f * s).sp,
        fontFamily = roman,
        letterSpacing = (size * 0.22f * s).sp,
        textAlign = align,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

    private fun runInStyle(size: Float, color: Color = PAPER.ink) = SpanStyle(
        color = color,
        fontSize = (size * s).sp,
        fontFamily = roman,
        letterSpacing = (size * 0.18f * s).sp,
    )

    private fun centred(text: String, style: TextStyle): TextLayoutResult = measure(
        AnnotatedString(text),
        style.copy(textAlign = TextAlign.Center),
        widthPx,
    )

    private fun fitted(text: String, style: TextStyle): TextLayoutResult = measurer.measure(
        text = text,
        style = style,
        softWrap = false,
        constraints = Constraints(),
    )

    private fun block(text: AnnotatedString, style: TextStyle): TextLayoutResult = measure(text, style, blockWidth)

    private fun measure(text: AnnotatedString, style: TextStyle, width: Int): TextLayoutResult = measurer.measure(
        text = text,
        style = style,
        softWrap = true,
        constraints = Constraints(minWidth = width, maxWidth = width),
    )

    private fun DrawScope.text(layout: TextLayoutResult, x: Float, y: Float) {
        drawText(layout, topLeft = Offset(x, y))
    }

    /** Paper stock, binding shade and fore-edge light shared by every page. */
    private fun DrawScope.sheet() {
        drawRect(PAPER.base)
        val tile = (512f * density).roundToInt()
        var y = 0
        while (y < heightPx) {
            var x = 0
            while (x < widthPx) {
                drawImage(
                    image = paper,
                    dstOffset = IntOffset(x, y),
                    dstSize = IntSize(tile, tile),
                )
                x += tile
            }
            y += tile
        }
        drawRect(
            brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0f to Color(0x3D463018),
                    0.35f to Color(0x12463018),
                    1f to Color.Transparent,
                ),
                startX = 0f,
                endX = BOOK.GUTTER * k,
            ),
            size = Size(BOOK.GUTTER * k, size.height),
        )
        val fore = 14f * k
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Color(0x0F463018)),
                startX = size.width - fore,
                endX = size.width,
            ),
            topLeft = Offset(size.width - fore, 0f),
            size = Size(fore, size.height),
        )
    }

    private fun DrawScope.runningHead(head: String, folio: String) {
        text(centred(head, capsStyle(9.5f)), 0f, BOOK.HEAD * k)
        text(centred(folio, serifStyle(13f, 18f, PAPER.mute, italicFace = true)), 0f, BOOK.FOLIO * k)
    }

    /** Printer's rule with a lozenge and two dots in the middle. */
    private fun DrawScope.ornament(top: Float, width: Float = 132f) {
        val left = (REF.WIDTH - width) * 0.5f * k
        val cy = (top + 5f) * k
        val middle = REF.WIDTH * 0.5f * k
        drawLine(PAPER.rule, Offset(left, cy), Offset(middle - 10f * k, cy), strokeWidth = 0.8f * k)
        drawLine(PAPER.rule, Offset(middle + 10f * k, cy), Offset(left + width * k, cy), strokeWidth = 0.8f * k)
        val lozenge = Path().apply {
            moveTo(middle, (top + 1f) * k)
            lineTo(middle + 4f * k, cy)
            lineTo(middle, (top + 9f) * k)
            lineTo(middle - 4f * k, cy)
            close()
        }
        drawPath(lozenge, PAPER.accent)
        drawCircle(PAPER.accent, radius = k, center = Offset(middle - 7f * k, cy))
        drawCircle(PAPER.accent, radius = k, center = Offset(middle + 7f * k, cy))
    }

    /** Publisher's seal: two blind-stamped rings with the volume and year centred inside. */
    private fun DrawScope.seal(top: Float) {
        val diameter = 104f * k
        drawCircle(
            color = PAPER.rule,
            radius = diameter * 0.5f,
            center = Offset(w * 0.5f, top * k + diameter * 0.5f),
            style = Stroke(k),
        )
        drawCircle(
            color = PAPER.rule,
            radius = (diameter - 10f * k) * 0.5f,
            center = Offset(w * 0.5f, top * k + diameter * 0.5f),
            style = Stroke(0.6f * k),
        )
        val topLine = centred(COPY.SEAL_TOP, capsStyle(8.5f, PAPER.accent))
        val middleLine = centred(COPY.SEAL_MID, serifStyle(19f, 24f, PAPER.accent, italicFace = true))
        val bottomLine = centred(COPY.SEAL_BOTTOM, capsStyle(8.5f, PAPER.accent))
        val contentHeight = topLine.size.height + middleLine.size.height + bottomLine.size.height
        var y = top * k + (diameter - contentHeight) * 0.5f
        text(topLine, 0f, y)
        y += topLine.size.height
        text(middleLine, 0f, y)
        y += middleLine.size.height
        text(bottomLine, 0f, y)
    }

    private fun DrawScope.titlePage() {
        text(centred(COPY.PRESENTS, serifStyle(16f, 22f, PAPER.mute, italicFace = true)), 0f, 150f * k)
        text(centred(COPY.TITLE, serifStyle(46f, 50f)), 0f, 196f * k)
        ornament(318f)
        text(centred(COPY.SUBTITLE, serifStyle(17f, 24f, italicFace = true)), 0f, 348f * k)
        seal(470f)
        text(centred(COPY.IMPRINT, capsStyle(10f)), 0f, 700f * k)
        val hint = fitted(COPY.HINT, serifStyle(15f, 18f, PAPER.mute, italicFace = true))
        text(hint, w - BOOK.MARGIN * k - hint.size.width, BOOK.FOLIO * k)
    }

    private fun DrawScope.prefacePage() {
        runningHead(COPY.HEAD_PREFACE, "ii")
        text(centred(COPY.PREFACE, capsStyle(11f, PAPER.accent)), 0f, 150f * k)
        text(centred(COPY.PREFACE_TITLE, serifStyle(32f, 38f)), 0f, 176f * k)
        ornament(238f, 64f)
        COPY.ROWS.forEachIndexed { index, row ->
            val paragraph = buildAnnotatedString {
                withStyle(runInStyle(12f)) { append(row.title.uppercase(Locale.US)) }
                append("  ")
                append(row.body)
            }
            val layout = block(paragraph, serifStyle(16f, 24f, align = TextAlign.Justify))
            text(layout, blockX, (284f + index * 132f) * k)
        }
        drawRect(PAPER.rule, topLeft = Offset(blockX, 640f * k), size = Size(64f * k, 1f))
        text(block(AnnotatedString(COPY.FOOTNOTE), serifStyle(12.5f, 18f, PAPER.mute)), blockX, 652f * k)
    }

    private fun DrawScope.forewordPage() {
        runningHead(COPY.HEAD_FOREWORD, "iii")
        text(centred(COPY.FOREWORD, capsStyle(11f, PAPER.accent)), 0f, 150f * k)
        text(centred(COPY.FOREWORD_TITLE, serifStyle(32f, 38f)), 0f, 176f * k)
        ornament(238f, 64f)
        var y = 284f * k
        COPY.BODY.forEachIndexed { index, paragraph ->
            val content = if (index == 0) {
                buildAnnotatedString {
                    withStyle(runInStyle(12.5f, PAPER.accent)) { append(COPY.BODY_LEAD) }
                    append(paragraph)
                }
            } else {
                AnnotatedString("\u2003$paragraph")
            }
            val layout = block(content, serifStyle(16f, 24f, align = TextAlign.Justify))
            text(layout, blockX, y)
            y += layout.size.height
        }
        val signoff = block(
            AnnotatedString(COPY.SIGNOFF),
            serifStyle(15f, 22f, PAPER.mute, italicFace = true, align = TextAlign.Right),
        )
        text(signoff, blockX, y + 28f * k)
        ornament(620f, 64f)
    }

    private fun DrawScope.coverPage() {
        text(centred(COPY.TITLE, serifStyle(40f, 46f, PAPER.foil)), 0f, 168f * k)
        ornament(290f)
        text(centred(COPY.BELONGS, capsStyle(10.5f)), 0f, 384f * k)
        text(centred(COPY.AUTHOR, serifStyle(34f, 42f, italicFace = true)), 0f, 414f * k)
        drawRect(
            PAPER.rule,
            topLeft = Offset(72f * k, 462f * k),
            size = Size((REF.WIDTH - 144f) * k, 1f),
        )
        text(centred(COPY.BEGUN, serifStyle(15f, 22f, PAPER.mute, italicFace = true)), 0f, 486f * k)
        seal(600f)
    }
}
