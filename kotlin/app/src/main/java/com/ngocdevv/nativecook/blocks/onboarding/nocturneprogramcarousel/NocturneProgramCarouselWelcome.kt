package com.ngocdevv.nativecook.blocks.onboarding.nocturneprogramcarousel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import kotlin.math.cos
import kotlin.math.sin

/** One star of `SPARKS`: where it drifts from (dp from the mark's centre), how big it is, when it starts and its colour. */
private class Spark(
    val x: Float,
    val y: Float,
    val size: Float,
    val delay: Double,
    val color: Color,
)

/** Faint stars drift around the mark while the welcome holds, the way the reference's sparks do. */
private val Sparks = List(11) { i ->
    val angle = i * 2.399 + 0.4
    val radius = 58 + (i * 37) % 64
    Spark(
        x = (cos(angle) * radius).toFloat(),
        y = (sin(angle) * radius * 0.72 - 22).toFloat(),
        size = if (i % 4 == 0) 3f else 2f,
        delay = (i * 0.13) % 0.6,
        color = if (i % 3 == 0) Color(0xFF7BE3D8) else Color(0xFF8FB1FF),
    )
}

/**
 * The starry welcome: the crescent mark under a blue halo with eleven drifting sparks, and
 * "Welcome to a quieter evening" below it, centred 45 dp above the middle of the screen
 * (`paddingBottom: 90`). It settles in from 106% scale over the first half second, holds, and fades out
 * between 1.5 and 2 s as the tour comes in. It is only there while the tour plays by itself:
 * [timelineDriven] false, a touch on the tour ([previewing] aside) and Reduce Motion all hide it.
 * Hidden from accessibility once the tour is visible.
 */
@Composable
internal fun NocturneProgramCarouselWelcome(
    playback: NocturneProgramCarouselPlayback,
    reduced: Boolean,
    timelineDriven: Boolean,
    previewing: Boolean,
    programVisible: Boolean,
) {
    val motion = NocturneProgramCarouselMotion
    Box(
        Modifier
            .fillMaxSize()
            .then(if (programVisible) Modifier.clearAndSetSemantics { } else Modifier)
            .graphicsLayer {
                val t = playback.time
                val hidden = !timelineDriven || (!previewing && playback.touching) || reduced
                val arrival = motion.enter(motion.segment(t, 0.0, 0.5))
                alpha = if (hidden) 0f else (arrival * (1.0 - motion.enter(motion.segment(t, 1.5, 2.0)))).toFloat()
                val grow = if (reduced) 1.0 else 1.0 + 0.06 * (1.0 - arrival)
                scaleX = grow.toFloat()
                scaleY = grow.toFloat()
            }
            .padding(bottom = 90.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Halo()
                for (spark in Sparks) {
                    SparkDot(spark, playback, reduced)
                }
                NocturneProgramCarouselMark(44f)
            }
            NocturneProgramCarouselText(
                text = "Welcome to\na quieter evening",
                color = Color(0xFFF2F5FD),
                size = 19f,
                lineHeight = 25f,
                weight = FontWeight.Bold,
                align = TextAlign.Center,
                maxScale = 1.6f,
            )
        }
    }
}

/** The halo: a 180 dp radial gradient, `#3E7BFF` at 34% in the middle, 10% at 45% of the radius, clear at the edge, centred on the mark. */
@Composable
private fun Halo() {
    Canvas(Modifier.requiredSize(180.dp)) {
        val blue = Color(0xFF3E7BFF)
        val radius = size.width / 2f
        drawCircle(
            brush = Brush.radialGradient(
                0f to blue.copy(alpha = 0.34f),
                0.45f to blue.copy(alpha = 0.1f),
                1f to blue.copy(alpha = 0f),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}

/**
 * One spark: a 2 or 3 dp dot with a 6 dp glow, centred on the mark and drifting outward (and 6 dp
 * up per second). It fades in over 0.6 s from `0.2 + delay`, twinkles, and is gone by 1.85 s.
 */
@Composable
private fun SparkDot(spark: Spark, playback: NocturneProgramCarouselPlayback, reduced: Boolean) {
    val motion = NocturneProgramCarouselMotion
    Box(
        Modifier
            .graphicsLayer {
                val t = playback.time
                val shown = motion.enter(motion.segment(t, 0.2 + spark.delay, 0.8 + spark.delay)) *
                    (1.0 - motion.enter(motion.segment(t, 1.35, 1.85)))
                val twinkle = 0.55 + 0.45 * sin(t * 5.1 + spark.delay * 9.0)
                alpha = if (reduced) 0f else (shown * twinkle).toFloat()
                translationX = (spark.x * (1.0 + 0.06 * t) * density).toFloat()
                translationY = ((spark.y * (1.0 + 0.06 * t) - 6.0 * t) * density).toFloat()
            }
            .size(spark.size.dp)
            .cssBoxShadow(spark.color, 0.dp, 6.dp, (spark.size / 2f).dp)
            .background(spark.color, CircleShape),
    )
}
