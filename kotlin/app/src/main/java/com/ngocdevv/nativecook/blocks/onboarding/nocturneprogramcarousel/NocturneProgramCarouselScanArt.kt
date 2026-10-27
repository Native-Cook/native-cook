package com.ngocdevv.nativecook.blocks.onboarding.nocturneprogramcarousel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val BracketColor = Color(0xFF2F7BFF)
private val GridColor = Color(0xFF5D8CE6)

/** The top-left bracket: a 30 dp box with a 2.5 dp border on its top and left sides and a 6 dp outer corner. */
private val CornerLeft = Path().apply {
    fillType = PathFillType.EvenOdd
    addRoundRect(RoundRect(0f, 0f, 30f, 30f, topLeftCornerRadius = CornerRadius(6f)))
    addRoundRect(RoundRect(2.5f, 2.5f, 30f, 30f, topLeftCornerRadius = CornerRadius(3.5f)))
}

/** The top-right bracket: the same with the border on its top and right sides. */
private val CornerRight = Path().apply {
    fillType = PathFillType.EvenOdd
    addRoundRect(RoundRect(0f, 0f, 30f, 30f, topRightCornerRadius = CornerRadius(6f)))
    addRoundRect(RoundRect(0f, 2.5f, 27.5f, 30f, topRightCornerRadius = CornerRadius(3.5f)))
}

/**
 * `BodyScan`: the third card's artwork, [height] dp tall. A translucent figure settles in over a
 * blueprint grid (10.2 to 11.2 s), corner brackets close in (10.9 to 11.4 s), a scan line passes
 * down (10.5 to 11.15 s) and back up (12.6 to 13.25 s), and three points of attention light up and
 * pulse (from 11.6 s, 250 ms apart). Under Reduce Motion everything is in place, the points held
 * dim, and the scan line stays away.
 */
@Composable
internal fun NocturneProgramCarouselScanArt(
    time: () -> Double,
    reduced: Boolean,
    height: Float,
    hologram: ImageBitmap,
) {
    val motion = NocturneProgramCarouselMotion
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clipToBounds()
            .background(Color(0xFF0E2A66)),
    ) {
        val areaWidth = maxWidth.value
        // The figure: 0.92 of the height wide, 1.38 of it tall, hanging from the top edge.
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = time()
                    alpha = if (reduced) 1f else motion.segment(t, 10.2, 10.8).toFloat()
                    val settle = if (reduced) 1.0 else 1.04 - 0.04 * motion.enter(motion.segment(t, 10.2, 11.2))
                    scaleX = settle.toFloat()
                    scaleY = settle.toFloat()
                },
        ) {
            val figureWidth = height * 0.92f * density
            val figureHeight = height * 1.38f * density
            drawImage(
                image = hologram,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(hologram.width, hologram.height),
                dstOffset = IntOffset(((size.width - figureWidth) / 2f).roundToInt(), 0),
                dstSize = IntSize(figureWidth.roundToInt(), figureHeight.roundToInt()),
                filterQuality = FilterQuality.High,
            )
        }
        // The blueprint grid: 18 dp apart, hairlines at 22% opacity as one group.
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = 0.22f
                    compositingStrategy = CompositingStrategy.Offscreen
                },
        ) {
            scale(density, density, pivot = Offset.Zero) {
                for (line in 0 until 18) {
                    drawLine(GridColor, Offset(0f, line * 18f), Offset(400f, line * 18f), strokeWidth = 0.5f)
                }
                for (line in 0 until 22) {
                    drawLine(GridColor, Offset(line * 18f, 0f), Offset(line * 18f, height), strokeWidth = 0.5f)
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset(y = 1.dp)
                .fillMaxWidth()
                .height((height * 0.3f).dp)
                .background(Brush.verticalGradient(listOf(Color(0x000B2A70), Color(0xFF0A2560)))),
        )
        // The corner brackets, 14 dp in from the sides and 4 dp down.
        Canvas(
            Modifier
                .padding(start = 14.dp, top = 4.dp, end = 14.dp)
                .fillMaxWidth()
                .height(60.dp)
                .graphicsLayer {
                    val t = time()
                    alpha = if (reduced) 1f else motion.segment(t, 10.9, 11.3).toFloat()
                    val close = if (reduced) 1.0 else 1.06 - 0.06 * motion.enter(motion.segment(t, 10.9, 11.4))
                    scaleX = close.toFloat()
                    scaleY = close.toFloat()
                },
        ) {
            scale(density, density, pivot = Offset.Zero) {
                drawPath(CornerLeft, BracketColor)
                translate(left = size.width / density - 30f) {
                    drawPath(CornerRight, BracketColor)
                }
            }
        }
        Hotspot(0.5f, 0.22f, 0, areaWidth, height, time, reduced)
        Hotspot(0.585f, 0.56f, 1, areaWidth, height, time, reduced)
        Hotspot(0.48f, 0.86f, 2, areaWidth, height, time, reduced)
        // The scan line: a bright 2 dp line with a 26 dp glow under it.
        Column(
            Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val t = time()
                    val down = motion.segment(t, 10.5, 11.15)
                    val up = motion.segment(t, 12.6, 13.25)
                    val travel = if (t < 12.0) down else 1.0 - up
                    val visible = if (reduced) {
                        0.0
                    } else {
                        min(
                            1.0,
                            motion.segment(t, 10.45, 10.6) * (1.0 - motion.segment(t, 11.1, 11.3)) +
                                motion.segment(t, 12.55, 12.7) * (1.0 - motion.segment(t, 13.15, 13.35)),
                        )
                    }
                    alpha = visible.toFloat()
                    translationY = (height * (0.06 + 0.62 * travel) * density).toFloat()
                },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Brush.horizontalGradient(listOf(Color(0x005FF3E0), Color(0xFF5FF3E0), Color(0x005FF3E0)))),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .background(Brush.verticalGradient(listOf(Color(0x335FF3E0), Color(0x005FF3E0)))),
            )
        }
    }
}

/**
 * One point of attention at ([x], [y]) of the artwork: a 34 dp halo and a 9 dp core with a pink glow.
 * It fades in at `11.6 + 0.25 * phase` s and then pulses (scale 0.85 to 1.15, opacity 0.7 to 1).
 */
@Composable
private fun Hotspot(
    x: Float,
    y: Float,
    phase: Int,
    areaWidth: Float,
    areaHeight: Float,
    time: () -> Double,
    reduced: Boolean,
) {
    val motion = NocturneProgramCarouselMotion
    Box(
        Modifier
            .offset(x = (x * areaWidth - 17f).dp, y = (y * areaHeight - 17f).dp)
            .size(34.dp)
            .graphicsLayer {
                val t = time()
                val shown = if (reduced) 0.75 else motion.segment(t, 11.6 + phase * 0.25, 12.2 + phase * 0.25)
                val pulse = if (reduced) 0.0 else sin(t * PI * 1.4 + phase * 2.0) * 0.5 + 0.5
                alpha = (shown * (0.7 + 0.3 * pulse)).toFloat()
                val beat = (0.85 + 0.3 * pulse).toFloat()
                scaleX = beat
                scaleY = beat
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(34.dp).background(Color(0xFFFF4FA8).copy(alpha = 0.22f), CircleShape))
        Box(
            Modifier
                .size(9.dp)
                .cssBoxShadow(Color(0xFFFF4FA8), 0.dp, 10.dp, 4.5.dp)
                .background(Color(0xFFFF8FD0), CircleShape),
        )
    }
}
