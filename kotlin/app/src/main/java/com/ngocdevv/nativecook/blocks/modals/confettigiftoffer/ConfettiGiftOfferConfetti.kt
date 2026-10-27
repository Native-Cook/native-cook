package com.ngocdevv.nativecook.blocks.modals.confettigiftoffer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * Paper confetti (ConfettiGiftOffer.tsx:61–84): each strip falls toward a terminal speed, sways
 * on its own period and keeps turning over, so its width breathes as the face tips away from the
 * light. One canvas draws all 56 strips from [age] (seconds since the burst, read per frame);
 * the whole layer slides left with the account screen's push, as `confettiPresence` does.
 *
 * Note: React Native sets a static opacity of .85 on the two smaller depths, but the animated
 * style's opacity replaces it, so every strip is drawn at the animated opacity alone.
 */
@Composable
internal fun ConfettiGiftOfferConfetti(
    age: () -> Double,
    push: () -> Float,
    reduced: Boolean,
    width: Float,
    height: Float,
) {
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationX = if (reduced) 0f else -width * push() * density },
    ) {
        if (reduced) return@Canvas
        val t = age()
        if (t < 0 || t > 1.7) return@Canvas
        val life = max(0.0, t)
        // The same opacity for every strip: it depends only on the age.
        val alpha = (confettiGiftOfferEnter(confettiGiftOfferFraction(t, 0.0, .08)) * (1 - confettiGiftOfferPhase(t, 1.2, 1.7))).toFloat()
        scale(density, density, pivot = Offset.Zero) {
            for (strip in ConfettiGiftOfferStrips) {
                val index = strip.index
                val seed = strip.seed
                val fall = height * (1.05 * life - .32 * (1 - exp(-life * 2.4))) * strip.depth
                val spin = life * (5 + seed * 6) + index
                val sway = sin(life * (2.2 + seed * 1.6) + index) * (14 + seed * 22)
                val drop = height * (strip.verticalSeed * 1.2 - .25) + fall
                val turn = index * 31 + life * (if (seed > .5) 210 else -190)
                val breathe = .3 + .7 * abs(cos(spin))
                val w = strip.width.toFloat()
                val h = strip.height.toFloat()
                val centreX = (seed * width + w / 2).toFloat()
                val centreY = h / 2
                translate(centreX + sway.toFloat(), centreY + drop.toFloat()) {
                    rotate(turn.toFloat(), pivot = Offset.Zero) {
                        scale(breathe.toFloat(), 1f, pivot = Offset.Zero) {
                            drawRoundRect(
                                color = strip.color,
                                topLeft = Offset(-w / 2, -h / 2),
                                size = Size(w, h),
                                cornerRadius = CornerRadius(w / 2),
                                alpha = alpha,
                            )
                        }
                    }
                }
            }
        }
    }
}
