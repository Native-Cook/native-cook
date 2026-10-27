package com.ngocdevv.nativecook.blocks.onboarding.nocturneprogramcarousel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.min
import kotlin.math.roundToInt

private val StripTitles = listOf("Unwind gently", "Make room for rest", "Find your rhythm")
private val StripOpacity = floatArrayOf(1f, 0.42f, 0.1f)

private val BookmarkPath = PathParser().parsePathString("M2 0h18v30l-9-6-9 6Z").toPath()
private val BookmarkStarPath = PathParser()
    .parsePathString("m11 7 1.4 3 3.6.4-2.6 2.5.6 3.5-3-1.6L8 16.4l.6-3.5L6 10.4l3.6-.4Z")
    .toPath()

/**
 * `GuidePortrait`: the first card's artwork, [height] dp tall. Three numbered plan strips slide in
 * one after the other (2.82 s, 100 ms apart), the evening guide rises in front of them (2.25 to
 * 3.1 s) over a fade into the card's colour, and a bookmark drops in at 3.8 s. Under Reduce Motion
 * everything is in place.
 */
@Composable
internal fun NocturneProgramCarouselGuideArt(
    time: () -> Double,
    reduced: Boolean,
    height: Float,
    guide: ImageBitmap,
) {
    val motion = NocturneProgramCarouselMotion
    Box(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clipToBounds(),
    ) {
        for (index in 0 until 3) {
            PlanStrip(index, time, reduced)
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = time()
                    alpha = if (reduced) 1f else motion.enter(motion.segment(t, 2.25, 2.9)).toFloat()
                    translationY = if (reduced) 0f else (18.0 * (1.0 - motion.enter(motion.segment(t, 2.25, 3.1))) * density).toFloat()
                },
        ) {
            drawGuide(guide, height)
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset(y = 1.dp)
                .fillMaxWidth()
                .height((height * 0.32f).dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x0010275A),
                        0.78f to Color(0xFF10234C),
                        1f to Color(0xFF0F2043),
                    ),
                ),
        )
        Canvas(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-10).dp)
                .size(22.dp, 32.dp)
                .graphicsLayer {
                    val progress = motion.segment(time(), 3.8, 4.1)
                    alpha = if (reduced) 1f else progress.toFloat()
                    translationY = if (reduced) 0f else (-8.0 * (1.0 - progress) * density).toFloat()
                },
        ) {
            val factor = size.width / 22f
            scale(factor, factor, pivot = Offset.Zero) {
                drawPath(BookmarkPath, Color(0xFFEAD899))
                drawPath(BookmarkStarPath, Color(0xFFAC8C46))
            }
        }
    }
}

/**
 * The portrait image in a frame `height * 0.86` wide and `height + 26` tall whose right edge is 10 dp
 * outside the card and whose top is 8 dp down (`contentFit="contain"`: scaled to fit, centred).
 */
private fun DrawScope.drawGuide(guide: ImageBitmap, height: Float) {
    val frameWidth = height * 0.86f * density
    val frameHeight = (height + 26f) * density
    val frameLeft = size.width + 10f * density - frameWidth
    val frameTop = 8f * density
    val fit = min(frameWidth / guide.width, frameHeight / guide.height)
    val width = guide.width * fit
    val tall = guide.height * fit
    drawImage(
        image = guide,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(guide.width, guide.height),
        dstOffset = IntOffset((frameLeft + (frameWidth - width) / 2f).roundToInt(), (frameTop + (frameHeight - tall) / 2f).roundToInt()),
        dstSize = IntSize(width.roundToInt(), tall.roundToInt()),
        filterQuality = FilterQuality.High,
    )
}

/** One numbered strip: `220 - 13 * index` dp wide, 35 tall, 42 + 47 * index down, sliding in from 18 dp to the left. */
@Composable
private fun PlanStrip(index: Int, time: () -> Double, reduced: Boolean) {
    val motion = NocturneProgramCarouselMotion
    Row(
        Modifier
            .offset(x = 8.dp, y = (42 + index * 47).dp)
            .graphicsLayer {
                val progress = if (reduced) 1.0 else motion.enter(motion.segment(time(), 2.82 + index * 0.1, 3.25 + index * 0.1))
                alpha = (progress * StripOpacity[index]).toFloat()
                translationX = (-18.0 * (1.0 - progress) * density).toFloat()
            }
            .width((220 - index * 13).dp)
            .height(35.dp)
            .drawBehind { drawStrip() }
            .padding(start = 10.dp, top = 1.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NocturneProgramCarouselText(
            text = "0${index + 1}",
            color = Color(0xFFA5C4FF),
            size = 11f,
            weight = FontWeight.Bold,
            scales = false,
        )
        NocturneProgramCarouselText(
            text = StripTitles[index],
            color = Color(0xFFBDD0F2),
            size = 11f,
            scales = false,
        )
    }
}

/** The strip's surface: `#2F62C6` at 55%, radius 7, and a 1 dp line along the top edge (`#5C89D9` at 30%) that follows the corners. */
private fun DrawScope.drawStrip() {
    val radius = 7.dp.toPx()
    val line = 1.dp.toPx()
    drawRoundRect(Color(0x8C2F62C6), cornerRadius = CornerRadius(radius))
    val top = Path().apply {
        fillType = PathFillType.EvenOdd
        addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
        addRoundRect(
            RoundRect(
                left = 0f,
                top = line,
                right = size.width,
                bottom = size.height,
                topLeftCornerRadius = CornerRadius(radius, radius - line),
                topRightCornerRadius = CornerRadius(radius, radius - line),
                bottomRightCornerRadius = CornerRadius(radius),
                bottomLeftCornerRadius = CornerRadius(radius),
            ),
        )
    }
    drawPath(top, Color(0x4D5C89D9))
}
