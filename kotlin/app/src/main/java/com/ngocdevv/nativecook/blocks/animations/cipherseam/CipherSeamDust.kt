package com.ngocdevv.nativecook.blocks.animations.cipherseam

import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * `DustField`: 660 fine points confined to the card's lane, as in the reference, where nothing
 * sits above or below the band. Three brightness layers, drawn dimmest first: layer 2 white at 38%,
 * layer 1 `#EDE6FF` at 62%, layer 0 white at 90%. Each point drifts along the lane
 * (`t * .004 * (1 + layer)` of the width a second), bobs and twinkles on its own phase, and the lane
 * sags a little toward the screen's edges, following the violet bowl below it.
 *
 * Under Reduce Motion ([reduced]) time is 0, so the field stands still. The points are drawn one
 * circle each (the points hardly ever touch, so this equals the one path per layer of React Native).
 */
@Composable
internal fun CipherSeamDust(
    clock: CipherSeamClock,
    width: Float,
    height: Float,
    cardTop: Float,
    cardHeight: Float,
    reduced: Boolean,
) {
    val paints = remember { CipherSeamDustPaints() }
    // `center` and `lane` of the React Native file: the lane is the card's height tall, a little less.
    val center = cardTop + cardHeight / 2f + 14f
    val lane = cardHeight * 0.66f
    Canvas(Modifier.size(width.dp, height.dp)) {
        val t = if (reduced) 0.0 else clock.time
        scale(density, density, pivot = Offset.Zero) {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                for (layer in DRAW_ORDER) {
                    val paint = paints.forLayer(layer)
                    var i = layer
                    while (i < CipherSeamParticles.DUST_COUNT) {
                        val u = (CipherSeamParticles.dustX[i] + t * 0.004 * (1 + layer)) % 1.0
                        val spread = abs(u - 0.5)
                        val phase = CipherSeamParticles.dustPhase[i]
                        // A triangular spread (two hashes) is densest on the card's centre line.
                        val y = center + CipherSeamParticles.dustY[i] * lane * (1 + 0.25 * spread) +
                            spread * 10 + sin(t * 0.6 + phase) * 1.5
                        val radius = CipherSeamParticles.dustRadius[i] * (0.85 + 0.3 * sin(t * 2.2 + phase))
                        native.drawCircle((u * width).toFloat(), y.toFloat(), radius.toFloat(), paint)
                        i += 3
                    }
                }
            }
        }
    }
}

/** The layers are drawn dimmest first (`layers[2]`, `layers[1]`, `layers[0]`). */
private val DRAW_ORDER = intArrayOf(2, 1, 0)

/** The three paints of the dust, by brightness layer: white 90%, `#EDE6FF` 62%, white 38%. */
private class CipherSeamDustPaints {
    private val layers = arrayOf(
        paint(Color.WHITE, 0.9f),
        paint(Color.rgb(0xED, 0xE6, 0xFF), 0.62f),
        paint(Color.WHITE, 0.38f),
    )

    fun forLayer(layer: Int): Paint = layers[layer]

    private fun paint(color: Int, opacity: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        alpha = (opacity * 255f).roundToInt()
    }
}
