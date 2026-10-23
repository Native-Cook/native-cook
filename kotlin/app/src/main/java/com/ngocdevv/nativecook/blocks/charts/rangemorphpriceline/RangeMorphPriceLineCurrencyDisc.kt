package com.ngocdevv.nativecook.blocks.charts.rangemorphpriceline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

private const val SIZE = 28f
private const val R = SIZE / 2

// A blue disc with a ring of twelve gold dots.
private const val RING = 8.5f
private const val DOT = 1.5f

// Thirteen red and white stripes under a blue corner.
private const val STRIPE = SIZE / 13

private val Clip = Path().apply { addOval(Rect(0f, 0f, SIZE, SIZE)) }

internal enum class CurrencyKind { Eur, Usd }

/** A flat round badge for a currency: a simplified flag, no detail that needs to be read. */
@Composable
internal fun RangeMorphPriceLineCurrencyDisc(kind: CurrencyKind) {
    Canvas(Modifier.size(SIZE.dp)) {
        scale(density, density, pivot = Offset.Zero) {
            clipPath(Clip) {
                if (kind == CurrencyKind.Eur) {
                    drawRect(Color(0xFF1F4FBF), size = Size(SIZE, SIZE))
                    for (i in 0 until 12) {
                        val a = i / 12.0 * Math.PI * 2
                        val center = Offset(R + cos(a).toFloat() * RING, R + sin(a).toFloat() * RING)
                        drawCircle(Color(0xFFFFD43A), radius = DOT, center = center)
                    }
                } else {
                    drawRect(Color.White, size = Size(SIZE, SIZE))
                    for (i in 0 until 7) {
                        drawRect(Color(0xFFD4313F), topLeft = Offset(0f, i * 2 * STRIPE), size = Size(SIZE, STRIPE))
                    }
                    drawRect(Color(0xFF2B3F8C), size = Size(SIZE * 0.5f, STRIPE * 7))
                }
            }
            drawCircle(Color.White.copy(alpha = 0.35f), radius = R - 0.5f, center = Offset(R, R), style = Stroke(1f))
        }
    }
}
