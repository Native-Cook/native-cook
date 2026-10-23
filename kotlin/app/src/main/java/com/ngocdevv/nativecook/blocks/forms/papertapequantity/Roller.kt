package com.ngocdevv.nativecook.blocks.forms.papertapequantity

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.roundToInt

const val BAR = 84f
const val PITCH = 53f
const val TAIL = 49f
const val TAPE = 54f
const val DRUM_AT = 62f
const val DRUM_WIDTH = 70f
const val DRUM_HEIGHT = 50f
const val TILE_AT = 124f
const val TILE_WIDTH = 36f
const val TILE_HEIGHT = 50f
const val BLEED = 24f
private const val CAP = 12.5f
private const val DRUM_RADIUS = 8f
private const val WELL = 6f
private const val LABEL_WIDTH = 37f
private const val LABEL_HEIGHT = 26f
private const val WRAP = DRUM_HEIGHT / 2f
private val ARC = WRAP * Math.PI.toFloat() / 2f
private const val SMEAR = 0.8f
private const val SMEAR_MAX = 6.5f
private val Ink = Color(0xFF2A2A2C)
private val Paper = listOf(Color(0xFFF4F3F1), Color(0xFFFDFDFC), Color(0xFFF1F0EE))
private val Orange = listOf(Color(0xFF8C3A0C), Color(0xFFE9620F), Color(0xFFFF9440), Color(0xFFF26A1B), Color(0xFF7E330A))
private val Lean = floatArrayOf(-5f, 4f, -3f, 5f, -4f, 3f, -5f, 4f, -2f, 5f)
private const val DIGIT_SCALE = 0.8f
private const val PAIR_SCALE = 0.62f
private val Up = PathParser().parsePathString("M-6 3.6L0 -3.6L6 3.6Z").toPath()
private val Down = PathParser().parsePathString("M-6 -3.6L0 3.6L6 -3.6Z").toPath()
private val Digits = listOf(
    "M10 3.5C5.5 3.5 4.5 11 5 16C5.5 22 9 24 11.5 22.5C15.5 20 16 9.5 14 5.5C13 3.8 11.5 3.3 10 3.5",
    "M6.5 8C8.5 6.5 10 5 11.5 3C11 9 10.6 16 10.2 23",
    "M5 8C6 3.5 13 2.5 14 7.5C14.8 12 8 17 4.5 22.5C8 20.5 12 22.5 16 21.5",
    "M5.5 5C8.5 2.5 14.5 3 14 7.5C13.6 10.5 10.5 11.8 8.5 12.2C12 12 15.5 14 14.8 18C14 22.5 7.5 23.8 4.5 20.5",
    "M12.5 3C10 8 7 12.5 4.5 15.5C8.5 15.8 12.5 15.5 16.5 15M13 9C12.6 14 12.3 19 12 23.5",
    "M14.5 3.5C11.8 3.3 9.5 3.6 7.5 4C7 6.8 6.6 9.2 6.2 12C9 10.2 14.5 10.8 14.8 15.5C15 20.5 9 24 4.8 20.8",
    "M13.5 3C8.5 6 5 12 5.5 18C6 23.5 13.5 24 14.5 18.5C15.2 14 9 12.5 6 16.5",
    "M4.5 4.5C8 4.8 12 4.4 15.5 3.5C12.5 9.5 9.8 16 8 23.5",
    "M10.5 12.5C5.5 10 6.5 3.5 10.8 3.5C15.5 3.5 15.2 9.5 10.5 12.5C5 15.5 4.5 23 10 23C15.5 23 15.5 15.5 10.5 12.5",
    "M14.5 8.5C13 2.5 5.5 3.5 6 9C6.4 13.5 12.5 13 14.5 8.5C14.6 13.5 13.5 19 11 23.5",
).map { PathParser().parsePathString(it).toPath() }

/** How far the tape reaches above and below the bar. */
fun reach(max: Int) = (max - 1) * PITCH + TAIL - BAR / 2f

/** Mask-filter paint is reused; older hardware gets a small layered falloff approximation. */
internal class PaperTapeQuantityPaint {
    private data class Key(val color: Int, val sigma: Float, val stroke: Float)
    private val paints = mutableMapOf<Key, Paint>()
    private fun paint(color: Color, sigma: Float, stroke: Float = 0f): Paint = paints.getOrPut(Key(color.toArgb(), sigma, stroke)) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.copy(alpha = 1f).toArgb()
            style = if (stroke > 0f) Paint.Style.STROKE else Paint.Style.FILL
            strokeWidth = stroke
            if (sigma > 0f && Build.VERSION.SDK_INT >= 28) maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
        }
    }
    private inline fun draw(color: Color, sigma: Float, alpha: Float, stroke: Float = 0f, block: (Paint, Float) -> Unit) {
        val paint = paint(color, sigma, stroke)
        if (Build.VERSION.SDK_INT >= 28 || sigma == 0f) {
            paint.alpha = (color.alpha * alpha * 255f).toInt().coerceIn(0, 255); block(paint, 0f)
        } else for (layer in 6 downTo 1) {
            val outer = layer * 0.5f; val inner = (layer - 1) * 0.5f
            val weight = 0.5f * (exp(-inner * inner / 2f) - exp(-outer * outer / 2f))
            paint.alpha = (color.alpha * alpha * weight * 255f).toInt().coerceIn(0, 255)
            if (stroke > 0f) paint.strokeWidth = stroke + outer * sigma * 2f
            block(paint, if (stroke > 0f) 0f else outer * sigma)
        }
    }
    fun round(canvas: android.graphics.Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, color: Color, sigma: Float, alpha: Float = 1f, stroke: Float = 0f) =
        draw(color, sigma, alpha, stroke) { p, spread -> canvas.drawRoundRect(x - spread, y - spread, x + w + spread, y + h + spread, r + spread, r + spread, p) }
    fun circle(canvas: android.graphics.Canvas, x: Float, y: Float, r: Float, color: Color, sigma: Float, alpha: Float = 1f) =
        draw(color, sigma, alpha) { p, spread -> canvas.drawCircle(x, y, r + spread, p) }
    fun oval(canvas: android.graphics.Canvas, x: Float, y: Float, w: Float, h: Float, color: Color, sigma: Float) =
        draw(color, sigma, 1f) { p, spread -> canvas.drawOval(x - spread, y - spread, x + w + spread, y + h + spread, p) }
    fun inset(canvas: android.graphics.Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, dx: Float, dy: Float, color: Color, sigma: Float) {
        val clip = android.graphics.Path().apply { addRoundRect(x, y, x + w, y + h, r, r, android.graphics.Path.Direction.CW) }
        val hole = android.graphics.Path().apply {
            fillType = android.graphics.Path.FillType.EVEN_ODD
            addRect(x - 40f, y - 40f, x + w + 40f, y + h + 40f, android.graphics.Path.Direction.CW)
            addRoundRect(x + dx, y + dy, x + w + dx, y + h + dy, r, r, android.graphics.Path.Direction.CW)
        }
        val p = paint(color, sigma)
        if (Build.VERSION.SDK_INT >= 28) {
            val saved = canvas.save(); canvas.clipPath(clip)
            p.alpha = (color.alpha * 255f).roundToInt()
            canvas.drawPath(hole, p); canvas.restoreToCount(saved)
        } else {
            // Dilating and contracting the hole samples the same soft edge along its normal.
            val saved = canvas.saveLayer(x - 1f, y - 1f, x + w + 1f, y + h + 1f, null)
            canvas.clipPath(clip)
            val weights = FloatArray(13) { exp(-((it - 6) * 0.5f) * ((it - 6) * 0.5f) / 2f) }
            val total = weights.sum()
            p.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
            for (sample in weights.indices) {
                val spread = (sample - 6) * 0.5f * sigma
                hole.reset(); hole.fillType = android.graphics.Path.FillType.EVEN_ODD
                hole.addRect(x - 40f, y - 40f, x + w + 40f, y + h + 40f, android.graphics.Path.Direction.CW)
                hole.addRoundRect(x + dx + spread, y + dy + spread, x + w + dx - spread, y + h + dy - spread, r - spread, r - spread, android.graphics.Path.Direction.CW)
                p.alpha = (color.alpha * weights[sample] / total * 255f).roundToInt()
                canvas.drawPath(hole, p)
            }
            p.xfermode = null; canvas.restoreToCount(saved)
        }
    }
}

/** A paper label and a constant-width marker numeral, tilted a little differently on each label. */
private fun DrawScope.label(value: Int) {
    drawRoundRect(Color.White.copy(alpha = 0.55f), Offset(-LABEL_WIDTH / 2f, -LABEL_HEIGHT / 2f), Size(LABEL_WIDTH, LABEL_HEIGHT), CornerRadius(5f))
    drawRoundRect(Color.Black.copy(alpha = 0.11f), Offset(-LABEL_WIDTH / 2f, -LABEL_HEIGHT / 2f), Size(LABEL_WIDTH, LABEL_HEIGHT), CornerRadius(5f), style = Stroke(1f))
    val digits = value.toString()
    val scale = if (digits.length > 1) PAIR_SCALE else DIGIT_SCALE
    val advance = 15f * scale
    withTransform({ rotate(Lean[value % Lean.size], Offset.Zero) }) {
        for ((index, digit) in digits.withIndex()) withTransform({
            translate((index - (digits.length - 1) / 2f) * advance - 10f * scale, -13f * scale)
            scale(scale, scale, pivot = Offset.Zero)
        }) { drawPath(Digits[digit.digitToInt()], Ink, style = Stroke(2.5f / scale, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
    }
}

/** A quantity roller that only draws; its caller owns the arrow and drum gestures. */
@Composable
fun Roller(width: Float, margin: Float, max: Int, place: State<Float>, upHeld: State<Float>, downHeld: State<Float>, canUp: Boolean, canDown: Boolean, still: Boolean, modifier: Modifier = Modifier) {
    val above = reach(max) + BLEED
    val cy = above + BAR / 2f
    val drumX = margin + DRUM_AT
    val drumLeft = drumX - DRUM_WIDTH / 2f
    val drumTop = cy - DRUM_HEIGHT / 2f
    val tapeX = drumX - TAPE / 2f
    val tileX = margin + TILE_AT
    val tileY = cy - TILE_HEIGHT / 2f
    val smear = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(place) {
        var before: Float? = null
        var last = 0L
        while (true) {
            val now = withFrameNanos { it }
            val p = place.value
            if (last != 0L && before != null) smear.floatValue = min(SMEAR_MAX, SMEAR * abs(p - before) / ((now - last) / 1e9f))
            before = p; last = now
        }
    }
    val paint = remember { PaperTapeQuantityPaint() }
    val drumClip = remember(drumLeft, drumTop) { Path().apply { addRoundRect(RoundRect(drumLeft, drumTop, drumLeft + DRUM_WIDTH, drumTop + DRUM_HEIGHT, CornerRadius(DRUM_RADIUS))) } }
    val wellClip = remember(drumLeft, drumTop) { Path().apply { addRoundRect(RoundRect(drumLeft - WELL, drumTop - WELL + 1f, drumLeft + DRUM_WIDTH + WELL, drumTop + DRUM_HEIGHT + WELL - 1f, CornerRadius(DRUM_RADIUS + 4f))) } }
    val tileClip = remember(tileX, tileY) { Path().apply { addRoundRect(RoundRect(tileX, tileY, tileX + TILE_WIDTH, tileY + TILE_HEIGHT, CornerRadius(8f))) } }
    fun Modifier.smeared() = graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
        val sigma = if (still) 0f else smear.floatValue
        renderEffect = if (Build.VERSION.SDK_INT >= 31 && sigma > 0.001f) BlurEffect(0.01f, sigmaToRadius(sigma.dp.toPx()), TileMode.Decal) else null
    }
    // Old Android uses vertical samples; Android 12 applies the same anisotropic image blur as Skia.
    val samplePaint = remember { Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) } }
    fun DrawScope.smear(block: DrawScope.() -> Unit) {
        val sigma = if (still) 0f else smear.floatValue
        if (Build.VERSION.SDK_INT >= 31 || sigma < 0.05f) { block(); return }
        val weights = floatArrayOf(0.006f, 0.032f, 0.098f, 0.207f, 0.314f, 0.207f, 0.098f, 0.032f, 0.006f)
        val total = weights.sum()
        for (sample in weights.indices) {
            samplePaint.alpha = (weights[sample] / total * 255f).toInt()
            drawIntoCanvas { it.nativeCanvas.saveLayer(0f, 0f, width, above * 2f + BAR, samplePaint) }
            withTransform({ translate(0f, (sample - 4) * 0.75f * sigma) }) { block() }
            drawIntoCanvas { it.nativeCanvas.restore() }
        }
    }
    Box(modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(width.dp, (above * 2f + BAR).dp).clearAndSetSemantics {}) {
        Canvas(Modifier.matchParentSize().smeared()) {
            surface(0.dp) { _, _, _, _ -> smear {
                withTransform({ translate(0f, -(place.value - 1f) * PITCH) }) {
                    val height = (max - 1) * PITCH + TAIL * 2f
                    drawIntoCanvas { paint.round(it.nativeCanvas, tapeX + 5f, cy - TAIL + 7f, TAPE, height, 4f, Color.Black.copy(alpha = 0.2f), 8f) }
                    drawRoundRect(Brush.horizontalGradient(Paper, startX = tapeX, endX = tapeX + TAPE), Offset(tapeX, cy - TAIL), Size(TAPE, height), CornerRadius(3f))
                    for (value in 1..max) withTransform({ translate(drumX, cy + (value - 1) * PITCH) }) { label(value) }
                }
            } }
        }
        Canvas(Modifier.matchParentSize()) {
            surface(0.dp) { _, _, _, _ ->
                drawIntoCanvas { paint.round(it.nativeCanvas, margin, above + 12f, width - margin * 2f, BAR, BAR / 2f, rgba(60, 50, 40, 0.26f), 14f) }
                drawRoundRect(Brush.verticalGradient(0f to rgba(240, 239, 237, 0.93f), 0.3f to rgba(226, 224, 222, 0.9f), 0.62f to rgba(217, 214, 212, 0.9f), 1f to rgba(234, 233, 231, 0.93f), startY = above, endY = above + BAR), Offset(margin, above), Size(width - margin * 2f, BAR), CornerRadius(BAR / 2f))
                drawRoundRect(Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.95f)), startY = above, endY = above + BAR), Offset(margin + 1f, above + 1f), Size(width - margin * 2f - 2f, BAR - 2f), CornerRadius(BAR / 2f - 1f), style = Stroke(2f))
                drawPath(wellClip, Color(0xFFCFCCCA))
                clipPath(wellClip) { drawIntoCanvas { paint.round(it.nativeCanvas, drumLeft - WELL, drumTop - WELL - 3f, DRUM_WIDTH + WELL * 2f, DRUM_HEIGHT + WELL * 2f, DRUM_RADIUS + 4f, Color.Black.copy(alpha = 0.3f), 3f, stroke = 5f) } }
                drawPath(wellClip, Color.White.copy(alpha = 0.8f), style = Stroke(1f))
                for (x in listOf(drumLeft - 5f, drumLeft + DRUM_WIDTH - 2f)) drawRoundRect(Brush.verticalGradient(0f to Color(0xFF7D8482), 0.35f to Color(0xFFC9CFCD), 0.7f to Color(0xFF9BA19F), 1f to Color(0xFF6F7573), startY = cy - 13f, endY = cy + 13f), Offset(x, cy - 13f), Size(7f, 26f), CornerRadius(2f))
                drawIntoCanvas { paint.round(it.nativeCanvas, drumLeft + 1f, drumTop + 4f, DRUM_WIDTH - 2f, DRUM_HEIGHT, DRUM_RADIUS, Color.Black.copy(alpha = 0.3f), 4f) }
                drawPath(drumClip, Color(0xFFFBFAF9))
            }
        }
        // The outer clip is applied after the wrapped labels' image blur.
        Box(Modifier.matchParentSize().graphicsLayer { clip = true; shape = object : androidx.compose.ui.graphics.Shape {
            override fun createOutline(size: Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density) = androidx.compose.ui.graphics.Outline.Generic(Path().apply {
                addRoundRect(RoundRect(drumLeft * density.density, drumTop * density.density, (drumLeft + DRUM_WIDTH) * density.density, (drumTop + DRUM_HEIGHT) * density.density, CornerRadius(DRUM_RADIUS * density.density)))
            })
        } }) {
            Canvas(Modifier.matchParentSize().smeared()) { surface(0.dp) { _, _, _, _ -> smear {
                for (value in 1..max) {
                    val along = (value - place.value) * PITCH
                    if (abs(along) < ARC) {
                        val turn = along.coerceIn(-ARC, ARC) / WRAP
                        withTransform({ translate(drumX, cy + WRAP * sin(turn)); scale(1f, max(0.02f, cos(turn)), pivot = Offset.Zero) }) { label(value) }
                    }
                }
            } } }
        }
        Canvas(Modifier.matchParentSize()) {
            surface(0.dp) { _, _, _, _ ->
                clipPath(drumClip) {
                    for ((side, x) in listOf(drumLeft, drumLeft + DRUM_WIDTH - CAP).withIndex()) {
                        drawRect(Brush.verticalGradient(0f to Orange[0], 0.2f to Orange[1], 0.42f to Orange[2], 0.72f to Orange[3], 1f to Orange[4], startY = drumTop, endY = drumTop + DRUM_HEIGHT), Offset(x, drumTop), Size(CAP, DRUM_HEIGHT))
                        for (rib in listOf(2.4f, 5f, 7.6f, 10.2f)) drawLine(rgba(255, 214, 170, 0.5f), Offset(x + rib, drumTop), Offset(x + rib, drumTop + DRUM_HEIGHT), 1f)
                        drawRect(rgba(90, 30, 0, 0.55f), Offset(if (side == 0) x + CAP - 1f else x, drumTop), Size(1f, DRUM_HEIGHT))
                    }
                    drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.42f), 0.2f to Color.Black.copy(alpha = 0.06f), 0.4f to Color.White.copy(alpha = 0.14f), 0.74f to Color.Black.copy(alpha = 0.05f), 1f to Color.Black.copy(alpha = 0.4f), startY = drumTop, endY = drumTop + DRUM_HEIGHT), Offset(drumLeft, drumTop), Size(DRUM_WIDTH, DRUM_HEIGHT))
                }
                drawIntoCanvas { paint.round(it.nativeCanvas, tileX, tileY + 3f, TILE_WIDTH, TILE_HEIGHT, 8f, Color.Black.copy(alpha = 0.2f), 4f) }
                withTransform({ translate(0f, -0.6f * upHeld.value + 0.6f * downHeld.value) }) {
                    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFF7F6F4), Color(0xFFECEAE8)), startY = tileY, endY = tileY + TILE_HEIGHT), Offset(tileX, tileY), Size(TILE_WIDTH, TILE_HEIGHT), CornerRadius(8f))
                    drawRoundRect(Color.White.copy(alpha = 0.9f), Offset(tileX + 0.5f, tileY + 0.5f), Size(TILE_WIDTH - 1f, TILE_HEIGHT - 1f), CornerRadius(7.5f), style = Stroke(1f))
                    clipPath(tileClip) {
                        drawRect(Color.Black, Offset(tileX, tileY), Size(TILE_WIDTH, TILE_HEIGHT / 2f), alpha = 0.1f * upHeld.value)
                        drawRect(Color.Black, Offset(tileX, cy), Size(TILE_WIDTH, TILE_HEIGHT / 2f), alpha = 0.1f * downHeld.value)
                    }
                    drawRect(Color.Black.copy(alpha = 0.08f), Offset(tileX + 8f, cy - 0.5f), Size(TILE_WIDTH - 16f, 1f))
                    withTransform({ translate(tileX + TILE_WIDTH / 2f, cy - 11.5f) }) { drawPath(Up, Ink, alpha = if (canUp) 0.9f else 0.25f) }
                    withTransform({ translate(tileX + TILE_WIDTH / 2f, cy + 11.5f) }) { drawPath(Down, Ink, alpha = if (canDown) 0.9f else 0.25f) }
                }
            }
        }
    }
}
