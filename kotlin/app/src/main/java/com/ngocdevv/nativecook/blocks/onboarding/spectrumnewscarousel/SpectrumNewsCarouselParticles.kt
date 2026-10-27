package com.ngocdevv.nativecook.blocks.onboarding.spectrumnewscarousel

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val PARTICLE_TAU = 2.0 * PI
private const val PARTICLE_COUNT = 240

/** The height of the particle canvas, in dp. */
private const val PARTICLE_HEIGHT = 330f

/** `hash`: the seeded noise of the particles; the same constants as the React Native file so they land in the same places. */
private fun spectrumNewsCarouselHash(index: Int): Double {
    val value = sin(index * 127.1 + 31.7) * 43758.5453
    return value - floor(value)
}

/** `PARTICLES`: 240 seeds, each with a position, a phase and a radius. */
private object SpectrumNewsCarouselSeeds {
    val x = DoubleArray(PARTICLE_COUNT) { spectrumNewsCarouselHash(it + 1) }
    val y = DoubleArray(PARTICLE_COUNT) { spectrumNewsCarouselHash(it + 221) }
    val phase = DoubleArray(PARTICLE_COUNT) { spectrumNewsCarouselHash(it + 441) * PARTICLE_TAU }
    val radius = DoubleArray(PARTICLE_COUNT) {
        val size = spectrumNewsCarouselHash(it + 661)
        1.1 + size * 2.0 + max(0.0, (size - 0.72) / 0.28).pow(1.5) * 5.0
    }
}

/** `DENSITY`: how much of the cloud shows on each page. */
private val SpectrumNewsCarouselDensity = doubleArrayOf(0.4, 1.0, 0.52, 0.95, 0.2)

/** `EXTENT`: how far down the canvas the cloud reaches on each page, as a fraction of its height. */
private val SpectrumNewsCarouselExtent = doubleArrayOf(0.48, 0.4, 0.33, 0.4, 0.22)

/** The colour of each of the three layers on the welcome page, and the grey it fades to on page two. */
private val SpectrumNewsCarouselWarm = arrayOf(intArrayOf(212, 46, 74), intArrayOf(239, 108, 92), intArrayOf(234, 116, 172))
private val SpectrumNewsCarouselGrey = arrayOf(intArrayOf(51, 50, 57), intArrayOf(118, 116, 122), intArrayOf(197, 193, 198))

/** Each layer's own opacity (`opacity={[.82, .66, .5][group]}`). */
private val SpectrumNewsCarouselLayerOpacity = doubleArrayOf(0.82, 0.66, 0.5)

/**
 * 240 deterministic particles batched into three paths (`ParticleLayer`), drawn in dp. The paths
 * are rebuilt every frame from loop time and the carousel's position into reused objects. The
 * largest, palest layer sits out of focus (a blur of sigma 1.8; from API 28, where mask filters
 * reach a hardware canvas), so the field reads with depth.
 */
internal class SpectrumNewsCarouselParticlePainter {
    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val blur: BlurMaskFilter? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) BlurMaskFilter(sigmaToRadius(1.8f), BlurMaskFilter.Blur.NORMAL) else null

    fun draw(canvas: android.graphics.Canvas, time: Double, page: Double, width: Double) {
        val motion = SpectrumNewsCarouselMotion
        val p = motion.clamp(page, 0.0, 4.0)
        val first = floor(p).toInt()
        val second = min(4, first + 1)
        val blend = p - first
        val density = SpectrumNewsCarouselDensity[first] + (SpectrumNewsCarouselDensity[second] - SpectrumNewsCarouselDensity[first]) * blend
        val extent = SpectrumNewsCarouselExtent[first] + (SpectrumNewsCarouselExtent[second] - SpectrumNewsCarouselExtent[first]) * blend
        val phase = time / SPECTRUM_NEWS_CAROUSEL_LOOP * PARTICLE_TAU
        val mix = motion.clamp(page)
        val spread = motion.clamp(p - 3.0)
        for (group in 0 until 3) {
            path.rewind()
            var i = group
            while (i < PARTICLE_COUNT) {
                val weight = motion.clamp((density * PARTICLE_COUNT - i) / 10.0 + 1.0)
                if (weight > 0.0) {
                    val seedX = SpectrumNewsCarouselSeeds.x[i]
                    val seedY = SpectrumNewsCarouselSeeds.y[i]
                    val seedPhase = SpectrumNewsCarouselSeeds.phase[i]
                    val x = (seedX * width + sin(phase * (1 + i % 3) + seedPhase) * (8.0 + p * 2.0) - width / 2.0) * (1.0 + spread * 0.32) + width / 2.0
                    val y = 8.0 + seedY.pow(1.9) * PARTICLE_HEIGHT * extent + cos(phase * (2 + i % 3) + seedPhase) * 13.0 - spread * seedY * 60.0
                    path.addCircle(x.toFloat(), y.toFloat(), (SpectrumNewsCarouselSeeds.radius[i] * weight).toFloat(), Path.Direction.CW)
                }
                i += 3
            }
            val warm = SpectrumNewsCarouselWarm[group]
            val grey = SpectrumNewsCarouselGrey[group]
            val red = (warm[0] + (grey[0] - warm[0]) * mix).roundToInt()
            val green = (warm[1] + (grey[1] - warm[1]) * mix).roundToInt()
            val blue = (warm[2] + (grey[2] - warm[2]) * mix).roundToInt()
            paint.color = android.graphics.Color.argb((SpectrumNewsCarouselLayerOpacity[group] * 255.0).roundToInt(), red, green, blue)
            paint.maskFilter = if (group == 2) blur else null
            canvas.drawPath(path, paint)
        }
    }
}

/**
 * `SpectrumParticles`: the cloud on a canvas [width] dp wide and 330 dp tall that never takes a touch.
 * The whole cloud sits at 78% opacity on the welcome page and rises to full by page two.
 */
@Composable
internal fun SpectrumNewsCarouselParticles(drive: SpectrumNewsCarouselDrive, width: Float, modifier: Modifier = Modifier) {
    val painter = remember { SpectrumNewsCarouselParticlePainter() }
    Box(
        modifier
            .width(width.dp)
            .height(PARTICLE_HEIGHT.dp)
            .clipToBounds()
            .graphicsLayer { alpha = (0.78 + SpectrumNewsCarouselMotion.clamp(drive.position) * 0.22).toFloat() }
            .drawBehind {
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    native.save()
                    native.scale(density, density)
                    painter.draw(native, drive.time, drive.position, width.toDouble())
                    native.restore()
                }
            },
    )
}
