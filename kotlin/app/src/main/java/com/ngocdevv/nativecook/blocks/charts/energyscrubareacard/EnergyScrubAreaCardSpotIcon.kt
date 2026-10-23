package com.ngocdevv.nativecook.blocks.charts.energyscrubareacard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate

internal fun DrawScope.drawSpotIcon(x: Float, y: Float) = translate(x, y) {
    val size = 22f; val colors = listOf(Color(0xff7c3aed), Color(0xffe0457b), Color(0xfff97316))
    val brush = Brush.linearGradient(colors, Offset(2f, 3f), Offset(20f, 21f))
    drawCircle(brush, 6.5f, Offset(9.5f, 12f), style = Stroke(2.2f))
    drawLine(brush, Offset(14.3f, 16.8f), Offset(19f, size - 0.5f), 2.2f, StrokeCap.Round)
    val sparkle = Path().apply {
        moveTo(16.5f, 1.5f); quadraticTo(17f, 5f, 20.5f, 5.5f); quadraticTo(17f, 6f, 16.5f, 9.5f)
        quadraticTo(16f, 6f, 12.5f, 5.5f); quadraticTo(16f, 5f, 16.5f, 1.5f); close()
    }
    drawPath(sparkle, brush)
}
