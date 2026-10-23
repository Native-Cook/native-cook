package com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.features.cards.components

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.shared.graphics.interpolateClamped
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.shared.graphics.rememberReduceMotion
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private data class ParticleSpec(
    val drift: Float,
    val duration: Float,
    val id: Int,
    val left: Float,
    val maxOpacity: Float,
    val phase: Float,
    val size: Float,
    val waveOffset: Float,
)

// Stable inputs keep the ambient field deterministic across renders.
private const val PARTICLE_COUNT = 26
private val PARTICLE_SIZES = floatArrayOf(3f, 4f, 6f, 3f, 8f, 5f, 11f, 4f, 7f, 3f, 5f, 9f)
private const val TWO_PI = (PI * 2).toFloat()

private fun fraction(index: Int, multiplier: Int, offset: Int): Float =
    ((index * multiplier + offset) % 997) / 997f

private val particles: List<ParticleSpec> = List(PARTICLE_COUNT) { index ->
    val size = PARTICLE_SIZES[index % PARTICLE_SIZES.size]
    ParticleSpec(
        drift = (if (index % 2 == 0) -1f else 1f) * (4f + fraction(index, 179, 41) * 10f),
        duration = 11_500f + fraction(index, 263, 73) * 6_000f,
        id = index,
        left = 0.03f + fraction(index, 619, 17) * 0.94f,
        maxOpacity = min(0.54f, 0.22f + size * 0.022f + fraction(index, 131, 59) * 0.08f),
        phase = fraction(index, 397, 29),
        size = size,
        waveOffset = fraction(index, 449, 97) * TWO_PI,
    )
}

private val OPACITY_INPUTS = floatArrayOf(0f, 0.12f, 0.58f, 1f)
private val PARTICLE_COLOR = Color(1f, 1f, 1f, 0.96f)
private val GLOW_COLOR = Color(1f, 1f, 1f, 0.58f)

/**
 * The 26 rising particles rendered in a single canvas driven by the frame clock. `height` and
 * `width` describe the sheet area the particles travel through.
 */
@Composable
fun RisingParticles(height: Dp, width: Dp, modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    var frameMillis by remember { mutableLongStateOf(0L) }

    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        val start = withInfiniteAnimationFrameMillis { it }
        while (true) {
            withInfiniteAnimationFrameMillis { frameMillis = it - start }
        }
    }

    val glowPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val density = this.density
        val heightPx = height.toPx()
        val widthPx = width.toPx()

        drawIntoCanvas { canvas ->
            particles.forEach { particle ->
                val sizePx = particle.size * density
                val cycle = if (reduceMotion) {
                    particle.phase
                } else {
                    ((particle.phase + frameMillis / particle.duration) % 1f)
                }
                val translateY = interpolateClamped(
                    cycle,
                    floatArrayOf(0f, 1f),
                    floatArrayOf(heightPx + sizePx, -sizePx),
                )
                val opacity = interpolateClamped(
                    cycle,
                    OPACITY_INPUTS,
                    if (reduceMotion) {
                        floatArrayOf(0f, particle.maxOpacity * 0.34f, particle.maxOpacity * 0.24f, 0f)
                    } else {
                        floatArrayOf(0f, particle.maxOpacity, particle.maxOpacity * 0.68f, 0f)
                    },
                )
                val translateX = if (reduceMotion) {
                    0f
                } else {
                    sin(cycle * TWO_PI + particle.waveOffset) * particle.drift * density
                }
                if (opacity <= 0.001f) return@forEach

                val left = particle.left * widthPx - sizePx / 2f + translateX
                val center = Offset(left + sizePx / 2f, translateY + sizePx / 2f)
                val radius = sizePx / 2f

                // boxShadow: 0 0 max(4, size)px rgba(255,255,255,0.58)
                glowPaint.color = GLOW_COLOR.copy(alpha = GLOW_COLOR.alpha * opacity).toArgb()
                glowPaint.maskFilter = BlurMaskFilter(max(4f, particle.size) * density, BlurMaskFilter.Blur.NORMAL)
                canvas.nativeCanvas.drawCircle(center.x, center.y, radius, glowPaint)

                drawCircle(
                    color = PARTICLE_COLOR.copy(alpha = PARTICLE_COLOR.alpha * opacity),
                    radius = radius,
                    center = center,
                )
            }
        }
    }
}
