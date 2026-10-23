package com.ngocdevv.nativecook.blocks.gestures.holddragresize

import kotlin.math.max

data class HoldDragResizeFrame(
    val top: Double,
    val left: Double,
    val width: Double,
    val height: Double,
    val radius: Double,
    val fullness: Double,
)

object HoldDragResizeGeometry {
    fun preview(height: Double, screenWidth: Double): HoldDragResizeFrame {
        val fullness = fullness(height)
        val width = along(height, Tokens.frameWidths) * screenWidth / Tokens.REFERENCE_WIDTH
        return HoldDragResizeFrame(
            top = max(along(height, Tokens.frameTops), 0.0),
            left = (screenWidth - width) / 2.0,
            width = width,
            height = height,
            radius = Tokens.Layout.previewRadius * (1.0 - fullness),
            fullness = fullness,
        )
    }

    fun fullness(height: Double): Double {
        val span = Tokens.sizes[2] - Tokens.sizes[1]
        return ((height - Tokens.sizes[1]) / span).coerceIn(0.0, 1.0)
    }

    fun squeeze(height: Double): Double {
        val span = Tokens.sizes[1] - Tokens.sizes[0]
        return ((height - Tokens.sizes[0]) / span).coerceIn(0.0, 1.0)
    }

    fun belowShift(height: Double): Double {
        return along(height, Tokens.frameTops) + height - Tokens.frameTops[0] - Tokens.sizes[0]
    }

    private fun along(height: Double, values: DoubleArray): Double {
        val index = if (height <= Tokens.sizes[1]) 0 else 1
        val progress = (height - Tokens.sizes[index]) / (Tokens.sizes[index + 1] - Tokens.sizes[index])
        return values[index] + (values[index + 1] - values[index]) * progress
    }
}
