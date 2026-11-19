package com.ngocdevv.nativecook.blocks.navigation.courierzoomtracker

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/** The block's glyphs (in place of SF Symbols), drawn in a 24-unit box. */
internal enum class CourierGlyph { House, Box, Phone, Message, Check, Locate, LocateOutline }

private val housePath: Path by lazy { PathParser().parsePathString("M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z").toPath() }
private val phonePath: Path by lazy {
    PathParser().parsePathString(
        "M6.62,10.79c1.44,2.83 3.76,5.14 6.59,6.59l2.2,-2.2c0.27,-0.27 0.67,-0.36 1.02,-0.24 1.12,0.37 2.33,0.57 3.57,0.57 " +
            "0.55,0 1,0.45 1,1V20c0,0.55 -0.45,1 -1,1 -9.39,0 -17,-7.61 -17,-17 0,-0.55 0.45,-1 1,-1h3.5c0.55,0 1,0.45 1,1 " +
            "0,1.25 0.2,2.45 0.57,3.57 0.11,0.35 0.03,0.74 -0.25,1.02l-2.2,2.2z",
    ).toPath()
}
private val bubblePath: Path by lazy {
    Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(1.5f, 2.5f, 22.5f, 19.5f))
        moveTo(5f, 15f)
        lineTo(3.2f, 21.6f)
        lineTo(10.5f, 18.4f)
        close()
    }
}
private val arrowPath: Path by lazy {
    Path().apply {
        moveTo(20.6f, 3.4f)
        lineTo(3.2f, 10.9f)
        lineTo(11.3f, 12.7f)
        lineTo(13.1f, 20.8f)
        close()
    }
}

@Composable
internal fun CourierIcon(glyph: CourierGlyph, color: Color, size: Dp, modifier: Modifier = Modifier, under: Color = Color.Black) {
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        scale(k, k, pivot = Offset.Zero) {
            when (glyph) {
                CourierGlyph.House -> drawPath(housePath, color)
                CourierGlyph.Phone -> drawPath(phonePath, color)
                CourierGlyph.Message -> drawPath(bubblePath, color)
                CourierGlyph.Box -> {
                    val p = Path().apply {
                        moveTo(12f, 1.8f); lineTo(21.2f, 6.8f); lineTo(21.2f, 17.2f); lineTo(12f, 22.2f); lineTo(2.8f, 17.2f); lineTo(2.8f, 6.8f); close()
                    }
                    drawPath(p, color)
                    val s = Stroke(width = 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    val l = Path().apply {
                        moveTo(2.8f, 6.8f); lineTo(12f, 11.8f); lineTo(21.2f, 6.8f)
                        moveTo(12f, 11.8f); lineTo(12f, 22.2f)
                        moveTo(7.4f, 4.3f); lineTo(16.6f, 9.3f)
                    }
                    drawPath(l, under, style = s)
                }
                CourierGlyph.Check -> {
                    drawCircle(color, 11f, Offset(12f, 12f))
                    val c = Path().apply { moveTo(7f, 12.4f); lineTo(10.6f, 16f); lineTo(17.2f, 8.6f) }
                    drawPath(c, under, style = Stroke(width = 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                CourierGlyph.Locate -> drawPath(arrowPath, color)
                CourierGlyph.LocateOutline -> translate(0f, 0f) {
                    drawPath(arrowPath, color, style = Stroke(width = 1.7f, join = StrokeJoin.Round))
                }
            }
        }
    }
}

/** A soft drop shadow under a rounded shape (the SwiftUI `.shadow(color:radius:x:y:)` look), drawn behind content. */
internal fun Modifier.courierShadow(color: Color, radius: Dp, dy: Dp, corner: Dp, topOnly: Boolean = false): Modifier = drawBehind {
    drawIntoCanvas { c ->
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.color = color.toArgb()
        val sigma = radius.toPx() / 2f
        if (sigma > 0.5f) p.maskFilter = BlurMaskFilter((sigma - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
        val r = corner.toPx()
        val bottom = if (topOnly) size.height + r else size.height
        c.nativeCanvas.drawRoundRect(0f, dy.toPx(), size.width, bottom + dy.toPx(), r, r, p)
    }
}
