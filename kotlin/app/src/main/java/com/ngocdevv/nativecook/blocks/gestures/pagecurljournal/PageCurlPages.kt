package com.ngocdevv.nativecook.blocks.gestures.pagecurljournal

import android.graphics.Bitmap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The four pages of the book, drawn in device pixels on the 402-pt reference grid. One unit of the grid is
 * [k] pixels. The same drawing is the live page at rest and the snapshot the curl samples. Font scale is
 * fixed at 1: the pages are artwork on a fixed grid, like the snapshots they are.
 */
internal class PageCurlPages(
    widthDp: Float,
    heightDp: Float,
    private val density: Float,
    fonts: FontFamily.Resolver,
) {
    val widthPx = (widthDp * density).roundToInt()
    val heightPx = (heightDp * density).roundToInt()

    /** Pixels per reference point. */
    private val k = widthPx / REF.WIDTH

    /** Reference points to dp, the unit of the font sizes below. */
    private val s = widthDp / REF.WIDTH

    private val measurer = TextMeasurer(fonts, Density(density, 1f), LayoutDirection.Ltr, 64)
    private val w = widthPx.toFloat()
    private val sparkle = sparklePath()

    private fun style(
        line: Line,
        color: Color,
        align: TextAlign = TextAlign.Unspecified,
        letterSpacing: TextUnit = TextUnit.Unspecified,
    ) = TextStyle(
        color = color,
        fontSize = (line.size * s).sp,
        fontWeight = FontWeight(line.weight),
        fontFamily = FontFamily.Default,
        lineHeight = (line.line * s).sp,
        letterSpacing = letterSpacing,
        textAlign = align,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

    /** A block as wide as the screen with centred lines. */
    private fun centred(text: String, line: Line, color: Color, letterSpacing: TextUnit = TextUnit.Unspecified): TextLayoutResult =
        measurer.measure(
            text = text,
            style = style(line, color, TextAlign.Center, letterSpacing),
            // Wrapping on at the screen's width: only then are the lines centred in it. Every line is far shorter.
            softWrap = true,
            constraints = Constraints(minWidth = widthPx, maxWidth = widthPx),
        )

    /** A block as wide as its text. */
    private fun fitted(text: String, line: Line, color: Color, letterSpacing: TextUnit = TextUnit.Unspecified): TextLayoutResult =
        measurer.measure(
            text = text,
            style = style(line, color, TextAlign.Left, letterSpacing),
            softWrap = false,
            constraints = Constraints(),
        )

    // Title page.
    private val meet = centred(COPY.MEET, TYPE.eyebrow, INK.eyebrow)
    private val brandSpacing = (-0.4f * s).sp
    private val brandHead = fitted(BRAND.NAME.substring(0, 1), TYPE.brand, INK.text, brandSpacing)
    private val brandDotless = fitted("ı", TYPE.brand, INK.text, brandSpacing)
    private val brandTail = fitted(BRAND.NAME.substring(2), TYPE.brand, INK.text, brandSpacing)
    private val markSpacing = (-2f).sp // not scaled: the source's letter spacing is a fixed 2 dp
    private val markLine = Line(0f, 62f, 900, 74f)
    private val markHead = fitted("R", markLine, ICON.markInk, markSpacing)
    private val markDotless = fitted("ı", markLine, ICON.markInk, markSpacing)
    private val intro = centred(COPY.INTRO, TYPE.intro, INK.text)
    private val hint = fitted(COPY.HINT, TYPE.hint, INK.mute)

    // Preface.
    private val preface = centred(COPY.PREFACE, TYPE.eyebrow, INK.eyebrow)
    private val prefaceTitle = centred(COPY.PREFACE_TITLE, TYPE.title, INK.text)
    private val rowTitles = COPY.ROWS.map { fitted(it.title, TYPE.rowTitle, INK.text) }
    private val rowBodies = COPY.ROWS.map { fitted(it.body, TYPE.rowBody, INK.text) }
    private val footnote = centred(COPY.FOOTNOTE, TYPE.footnote, INK.footnote)
    private val folio2 = centred("2", TYPE.folio, INK.mute)

    // Foreword.
    private val foreword = centred(COPY.FOREWORD, TYPE.eyebrow, INK.eyebrow)
    private val forewordTitle = centred(COPY.FOREWORD_TITLE, TYPE.title, INK.text)
    private val body = fitted(COPY.BODY, TYPE.body, INK.text)
    private val folio3 = centred("3", TYPE.folio, INK.mute)

    // Cover.
    private val cover = centred(COPY.COVER, TYPE.cover, INK.text)
    private val caption = centred(COPY.CAPTION, TYPE.caption, INK.caption, (0.3f * s).sp)
    private val author = centred(COPY.AUTHOR, TYPE.author, INK.author)
    private val folio4 = centred("4", TYPE.folio, INK.mute)

    /** Draws page [index]. [twinkle] runs 0 to 1 as the title's sparkles twinkle (1 = at rest). */
    fun draw(scope: DrawScope, index: Int, twinkle: Float) {
        with(scope) {
            drawRect(INK.paper)
            when (index) {
                0 -> titlePage(twinkle)
                1 -> prefacePage()
                2 -> forewordPage()
                else -> coverPage()
            }
        }
    }

    /** The four pages as bitmaps, for the curl to sample. The title is taken at rest, as the source's snapshot is. */
    fun snapshots(): List<Bitmap> = List(4) { index ->
        val image = ImageBitmap(widthPx, heightPx)
        CanvasDrawScope().draw(
            Density(density, 1f),
            LayoutDirection.Ltr,
            Canvas(image),
            Size(w, heightPx.toFloat()),
        ) {
            this@PageCurlPages.draw(this, index, 1f)
        }
        image.asAndroidBitmap()
    }

    private fun DrawScope.text(layout: TextLayoutResult, x: Float, y: Float) {
        drawText(layout, topLeft = Offset(x, y))
    }

    // ---- Title page ----

    private fun DrawScope.titlePage(twinkle: Float) {
        text(meet, 0f, TYPE.eyebrow.top * k)
        wordmark(twinkle)
        mark(twinkle)
        text(intro, 0f, TYPE.intro.top * k)
        hintRow()
    }

    /** "Ritualbook" with its i dotted by a sparkle, as the source's wordmark is. */
    private fun DrawScope.wordmark(twinkle: Float) {
        val top = TYPE.brand.top * k
        val headWidth = brandHead.size.width.toFloat()
        val dotlessWidth = brandDotless.size.width.toFloat()
        val tailWidth = brandTail.size.width.toFloat()
        val left = (w - (headWidth + dotlessWidth + tailWidth)) / 2f
        text(brandHead, left, top)
        text(brandDotless, left + headWidth, top)
        text(brandTail, left + headWidth + dotlessWidth, top)
        val size = 7f * k
        val cx = left + headWidth + 0.3f * k + size / 2f
        val cy = top + 4.6f * k + size / 2f
        twinkling(cx, cy, size / 2f, twinkle, TWINKLE.STAGGER_MS / TWINKLE.DURATION_MS) {
            drawPath(sparkle, ICON.markInk)
        }
    }

    /** The app mark: a white squircle with a heavy monogram and its bottom-left corner curling up like a page. */
    private fun DrawScope.mark(twinkle: Float) {
        val left = LAYOUT.MARK_LEFT * k
        val top = LAYOUT.MARK_TOP * k
        val size = LAYOUT.MARK_SIZE * k
        val radius = LAYOUT.MARK_RADIUS * k
        // A hairline border, one device pixel; children sit inside it.
        val border = 1f
        drawRoundRect(INK.paper, Offset(left, top), Size(size, size), CornerRadius(radius))
        drawRoundRect(
            INK.iconLine,
            Offset(left + border / 2f, top + border / 2f),
            Size(size - border, size - border),
            CornerRadius(radius - border / 2f),
            style = Stroke(border),
        )
        val inner = size - 2f * border
        val clip = Path().apply {
            addRoundRect(RoundRect(left + border, top + border, left + border + inner, top + border + inner, CornerRadius(radius - border)))
        }
        clipPath(clip) {
            val originX = left + border
            val originY = top + border
            val lettersTop = originY + 18f * k
            val headWidth = markHead.size.width.toFloat()
            val dotlessWidth = markDotless.size.width.toFloat()
            val lettersLeft = originX + (inner - (headWidth + dotlessWidth)) / 2f
            text(markHead, lettersLeft, lettersTop)
            text(markDotless, lettersLeft + headWidth, lettersTop)
            val dotSize = 20f * k
            val dotX = lettersLeft + headWidth - 3f * k + dotSize / 2f
            val dotY = lettersTop + 4f * k + dotSize / 2f
            twinkling(dotX, dotY, dotSize / 2f, twinkle, 0f) {
                drawPath(sparkle, ICON.markInk)
            }
            translate(originX, originY) {
                scale(inner / 100f, inner / 100f, Offset.Zero) {
                    drawPath(
                        flapPath,
                        Brush.linearGradient(
                            0f to ICON.curlDark,
                            0.45f to ICON.curlMid,
                            1f to ICON.curlLight,
                            start = Offset(22f, 78f),
                            end = Offset(4f, 96f),
                        ),
                    )
                    drawPath(
                        lipPath,
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = 0f),
                            1f to Color.White.copy(alpha = 0.95f),
                            start = Offset(20f, 80f),
                            end = Offset(30f, 90f),
                        ),
                    )
                }
            }
        }
    }

    private fun DrawScope.hintRow() {
        val top = TYPE.hint.top * k
        val rowHeight = TYPE.hint.line * k
        val arrow = LAYOUT.HINT_ARROW_SIZE * k
        val gap = LAYOUT.HINT_ARROW_GAP * k
        val textWidth = hint.size.width.toFloat()
        val left = (w - (textWidth + gap + arrow)) / 2f
        text(hint, left, top)
        translate(left + textWidth + gap, top + (rowHeight - arrow) / 2f) {
            scale(arrow / LAYOUT.HINT_ARROW_SIZE, arrow / LAYOUT.HINT_ARROW_SIZE, Offset.Zero) {
                drawPath(
                    arrowPath,
                    INK.mute,
                    style = Stroke(1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }

    // ---- Preface ----

    private fun DrawScope.prefacePage() {
        text(preface, 0f, TYPE.eyebrow.top * k)
        text(prefaceTitle, 0f, TYPE.title.top * k)
        for (i in 0 until 2) {
            val iconSize = LAYOUT.ROW_ICON * k
            val iconLeft = (LAYOUT.ROW_ICON_X - LAYOUT.ROW_ICON / 2f) * k
            val iconTop = (LAYOUT.ROW_ICON_Y[i] - LAYOUT.ROW_ICON / 2f) * k
            translate(iconLeft, iconTop) {
                if (i == 0) {
                    checkBadge(iconSize)
                } else {
                    barsIcon(iconSize)
                }
            }
            val textLeft = LAYOUT.ROW_TEXT_LEFT * k
            val titleTop = LAYOUT.ROW_TITLE_TOP[i] * k
            text(rowTitles[i], textLeft, titleTop)
            text(rowBodies[i], textLeft, titleTop + TYPE.rowTitle.line * k + LAYOUT.ROW_GAP * k)
        }
        shield()
        text(footnote, 0f, TYPE.footnote.top * k)
        text(folio2, 0f, TYPE.folio.top * k)
    }

    private fun DrawScope.checkBadge(size: Float) {
        scale(size / 28f, size / 28f, Offset.Zero) {
            drawRoundRect(
                Brush.linearGradient(
                    0f to ICON.check[0],
                    0.55f to ICON.check[1],
                    1f to ICON.check[2],
                    start = Offset(24f, 3f),
                    end = Offset(5f, 26f),
                ),
                Offset.Zero,
                Size(28f, 28f),
                CornerRadius(14f),
            )
            val tick = Path().apply {
                moveTo(8.2f, 14.6f)
                lineTo(12.4f, 18.6f)
                lineTo(20f, 9.6f)
            }
            drawPath(tick, Color.White, style = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    private fun DrawScope.barsIcon(size: Float) {
        scale(size / 30f, size / 30f, Offset.Zero) {
            val gradient = Brush.linearGradient(
                0f to ICON.bars[0],
                1f to ICON.bars[1],
                start = Offset(0f, 3f),
                end = Offset(0f, 25f),
            )
            for (bar in BARS) {
                drawRoundRect(gradient, Offset(bar.x, 24f - bar.h), Size(4.6f, bar.h), CornerRadius(1f), alpha = bar.o)
            }
            drawRoundRect(ICON.barsBase, Offset(1f, 26.2f), Size(28f, 2.2f), CornerRadius(1.1f))
        }
    }

    /** The privacy shield: an outline with its left half filled, centred above the footnote. */
    private fun DrawScope.shield() {
        val size = LAYOUT.SHIELD_SIZE * k
        val unit = size / LAYOUT.SHIELD_SIZE
        translate((w - size) / 2f, LAYOUT.SHIELD_TOP * k) {
            scale(unit, unit, Offset.Zero) {
                val half = Path().apply { addRect(androidx.compose.ui.geometry.Rect(0f, 0f, LAYOUT.SHIELD_SIZE / 2f, LAYOUT.SHIELD_SIZE)) }
                clipPath(half) {
                    drawPath(shieldPath, INK.footnote)
                }
                drawPath(shieldPath, INK.footnote, style = Stroke(1.1f, join = StrokeJoin.Round))
            }
        }
    }

    // ---- Foreword ----

    private fun DrawScope.forewordPage() {
        text(foreword, 0f, TYPE.eyebrow.top * k)
        text(forewordTitle, 0f, TYPE.title.top * k)
        text(body, LAYOUT.BODY_LEFT * k, TYPE.body.top * k)
        text(folio3, 0f, TYPE.folio.top * k)
    }

    // ---- Cover ----

    private fun DrawScope.coverPage() {
        val r = LAYOUT.SPARKLE_SIZE * k / 2f
        coloredSparkle(LAYOUT.SPARKLE_CX * k, LAYOUT.SPARKLE_CY * k, r)
        text(cover, 0f, TYPE.cover.top * k)
        text(caption, 0f, TYPE.caption.top * k)
        text(author, 0f, TYPE.author.top * k)
        text(folio4, 0f, TYPE.folio.top * k)
    }

    /** The source tints its sparkle with four hues around its centre. */
    private fun DrawScope.coloredSparkle(cx: Float, cy: Float, r: Float) {
        translate(cx, cy) {
            scale(r, r, Offset.Zero) {
                drawPath(
                    sparkle,
                    Brush.linearGradient(
                        0f to ICON.sparkle[0],
                        0.5f to Color(0xFFF2A2B8),
                        1f to ICON.sparkle[2],
                        start = Offset(0f, -1f),
                        end = Offset(0f, 1f),
                    ),
                )
                drawPath(
                    sparkle,
                    Brush.linearGradient(
                        0f to ICON.sparkle[3].copy(alpha = 0.9f),
                        0.5f to Color.White.copy(alpha = 0f),
                        1f to ICON.sparkle[1].copy(alpha = 0.95f),
                        start = Offset(-1f, 0f),
                        end = Offset(1f, 0f),
                    ),
                )
            }
        }
    }

    /**
     * A sparkle that twinkles once when [twinkle] runs 0 to 1: it swells and turns (45 degrees), then rests.
     * [delay] staggers it, as a fraction of the run. [shape] draws the unit sparkle.
     */
    private fun DrawScope.twinkling(cx: Float, cy: Float, r: Float, twinkle: Float, delay: Float, shape: DrawScope.() -> Unit) {
        val u = max(0f, min(1f, (twinkle - delay) / (1f - delay)))
        val swell = sin(PI.toFloat() * u)
        val grow = 1f + (TWINKLE.SCALE - 1f) * swell
        translate(cx, cy) {
            scale(r * grow, r * grow, Offset.Zero) {
                rotate(TWINKLE.TURN_DEG * u, Offset.Zero) {
                    shape()
                }
            }
        }
    }

    private class Bar(val x: Float, val h: Float, val o: Float)

    private companion object {
        val BARS = listOf(Bar(3f, 15f, 0.95f), Bar(9.6f, 20f, 0.85f), Bar(16.2f, 16f, 0.7f), Bar(22.8f, 10f, 0.5f))

        /** A four-point star of radius 1, centred on the origin. */
        fun sparklePath(): Path {
            val q = 0.17f
            return Path().apply {
                moveTo(0f, -1f)
                quadraticTo(q, -q, 1f, 0f)
                quadraticTo(q, q, 0f, 1f)
                quadraticTo(-q, q, -1f, 0f)
                quadraticTo(-q, -q, 0f, -1f)
                close()
            }
        }

        /** The mark's curling corner, on a 100 x 100 grid. */
        val flapPath = Path().apply {
            moveTo(0f, 60f)
            cubicTo(10f, 70f, 30f, 88f, 41f, 100f)
            lineTo(0f, 100f)
            close()
        }
        val lipPath = Path().apply {
            moveTo(0f, 60f)
            cubicTo(10f, 70f, 30f, 88f, 41f, 100f)
            lineTo(46f, 100f)
            cubicTo(34f, 88f, 14f, 70f, 0f, 57f)
            close()
        }

        /** "arrow.right", medium weight, on a 13.5-point box. */
        val arrowPath = Path().apply {
            moveTo(0.8f, 6.75f)
            lineTo(12.6f, 6.75f)
            moveTo(8.0f, 2.2f)
            lineTo(12.6f, 6.75f)
            lineTo(8.0f, 11.3f)
        }

        /** "shield.lefthalf.filled" on a 15.5-point box. */
        val shieldPath = Path().apply {
            moveTo(7.75f, 0.8f)
            cubicTo(10.0f, 2.4f, 12.2f, 2.9f, 14.2f, 3.0f)
            lineTo(14.2f, 7.6f)
            cubicTo(14.2f, 11.0f, 11.4f, 13.6f, 7.75f, 14.9f)
            cubicTo(4.1f, 13.6f, 1.3f, 11.0f, 1.3f, 7.6f)
            lineTo(1.3f, 3.0f)
            cubicTo(3.3f, 2.9f, 5.5f, 2.4f, 7.75f, 0.8f)
            close()
        }
    }
}
