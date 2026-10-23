package com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.features.cards.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.features.cards.theme.CollectionCardBackdropColors
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.shared.graphics.rememberReduceMotion
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

private const val ROTATION_DURATION = 38_000
private const val ENTRY_ANIMATION_DURATION = 2_600
private const val START_ANGLE = 0f
private const val ENTRY_SCALE = 1.5f
private const val RESTING_SCALE = 1f
private val BACKDROP_BLUR = 16.dp
private val ENTRY_EASING = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/**
 * Slowly rotating, blurred card-back artwork that fills the detail sheet, with rising particles.
 */
@Composable
fun CollectionCardBackdrop(height: Dp, width: Dp, modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    val planeSize = ceil(hypot(width.value, height.value) * 1.08f).dp
    val artSize = max(width.value * 1.55f, height.value * 0.82f)
    val artOffset = (planeSize.value - artSize) / 2f
    val artScaleX = artSize / COLLECTION_CARD_ART_WIDTH
    val artScaleY = artSize / COLLECTION_CARD_ART_HEIGHT

    val rotation = if (reduceMotion) {
        START_ANGLE
    } else {
        val transition = rememberInfiniteTransition(label = "backdrop-rotation")
        val animated by transition.animateFloat(
            initialValue = START_ANGLE,
            targetValue = START_ANGLE - 360f,
            animationSpec = infiniteRepeatable(
                tween(ROTATION_DURATION, easing = LinearEasing),
                RepeatMode.Restart,
            ),
            label = "rotation",
        )
        animated
    }

    val zoom = remember { Animatable(if (reduceMotion) RESTING_SCALE else ENTRY_SCALE) }
    LaunchedEffect(reduceMotion) {
        zoom.snapTo(if (reduceMotion) RESTING_SCALE else ENTRY_SCALE)
        if (!reduceMotion) {
            zoom.animateTo(RESTING_SCALE, tween(ENTRY_ANIMATION_DURATION, easing = ENTRY_EASING))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(CollectionCardBackdropColors.background),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .requiredSize(planeSize)
                .graphicsLayer {
                    rotationZ = rotation
                    scaleX = zoom.value
                    scaleY = zoom.value
                },
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(BACKDROP_BLUR, BlurredEdgeTreatment.Unbounded),
            ) {
                drawRect(CollectionCardBackdropColors.background)
                val density = this.density
                translate(artOffset * density, artOffset * density) {
                    scale(artScaleX * density, artScaleY * density, pivot = Offset.Zero) {
                        drawCollectionCardArtwork(
                            backgroundColor = CollectionCardBackdropColors.background,
                            patternColor = CollectionCardBackdropColors.pattern,
                            sealColor = CollectionCardBackdropColors.seal,
                            showCollectorMark = false,
                        )
                    }
                }
            }
        }
        RisingParticles(height = height, width = width)
    }
}
