package com.ngocdevv.nativecook.blocks.charts.glowtipscrubber

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import com.ngocdevv.nativecook.shared.rgba

val Green = Color(0xff3ddc97)
private object C {
    val lit = Color(0xffb8ffe3); val rule = Color(0xff5a5a5e); val cursor = Color.White
    const val line = 2f; const val litLine = 2.6f; const val litReach = 26f; const val bloomLine = 7f; const val bloomBlur = 5f; const val halo = 28f; const val glowBlur = 6f; const val glowAlpha = 0.35f; const val dim = 0.55f; val dots = floatArrayOf(1.5f, 5f)
}
internal fun chartBlur(pass: Int) = if (pass == 1) C.glowBlur else C.bloomBlur

internal fun DrawScope.drawChart(range: Range, width: Float, height: Float, pad: Float, f: Film, pass: Int) {
    val low = range.prices.min(); val span = (range.prices.max() - low).let { if (it == 0.0) 1.0 else it }; val plot = height - pad * 2
    fun y(price: Double) = (pad + (1 - (price - low) / span) * plot).toFloat()
    val path = Path().apply { range.prices.forEachIndexed { i, p -> val x = i.toFloat() / (range.prices.size - 1) * width; if (i == 0) moveTo(x, y(p)) else lineTo(x, y(p)) } }
    val x = (Motion.clamp(f.tip) * width).toFloat(); val point = Offset(x, y(Motion.priceAt(range.prices, f.tip))); val base = (f.shown * (1 - C.dim * f.held)).toFloat()
    if (pass == 0) { drawLine(C.rule.copy(alpha = f.marks.toFloat()), Offset(0f, y(range.prices[0])), Offset(width, y(range.prices[0])), 1.5f, StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(C.dots)); drawLine(C.cursor.copy(alpha = f.held.toFloat()), Offset(x, 0f), Offset(x, height), 1f) }
    if (pass == 1) drawPath(path, Green.copy(alpha = base * C.glowAlpha), style = Stroke(C.line, cap = StrokeCap.Round, join = StrokeJoin.Round))
    if (pass == 2) clipRect(0f, 0f, (f.reveal * width).toFloat(), height) { drawPath(path, Green.copy(alpha = base), style = Stroke(C.line, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
    val from = Offset(x - C.litReach, 0f); val to = Offset(x + C.litReach, 0f)
    if (pass == 3) drawPath(path, Brush.linearGradient(listOf(Green.copy(alpha = 0f), Green.copy(alpha = 0.9f), Green.copy(alpha = 0f)), from, to), alpha = f.lit.toFloat(), style = Stroke(C.bloomLine, cap = StrokeCap.Round, join = StrokeJoin.Round))
    if (pass == 4) {
        clipRect(0f, 0f, (f.reveal * width).toFloat(), height) { drawPath(path, Brush.linearGradient(listOf(C.lit.copy(alpha = 0f), C.lit, C.lit.copy(alpha = 0f)), from, to), alpha = f.lit.toFloat(), style = Stroke(C.litLine, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
        drawCircle(Brush.radialGradient(0f to rgba(225, 255, 242, 0.6f), 0.45f to rgba(61, 220, 151, 0.28f), 1f to rgba(61, 220, 151, 0f), center = point, radius = C.halo), C.halo, point, alpha = f.lit.toFloat())
    }
}
