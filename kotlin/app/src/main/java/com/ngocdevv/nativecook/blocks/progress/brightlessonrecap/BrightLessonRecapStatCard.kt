package com.ngocdevv.nativecook.blocks.progress.brightlessonrecap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.blocks.progress.brightlessonrecap.BrightLessonRecapMotion.enter
import com.ngocdevv.nativecook.blocks.progress.brightlessonrecap.BrightLessonRecapMotion.fraction
import com.ngocdevv.nativecook.blocks.progress.brightlessonrecap.BrightLessonRecapMotion.phase
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val ONE_FRAME = 1.0 / 60

/**
 * A counter whose text is a function of loop time. The text is a derived state, so only this text
 * recomposes, and only when the digits change.
 */
@Composable
private fun BrightLessonRecapCounter(
    controller: BrightLessonRecapController,
    index: Int,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val text by remember(controller, index) { derivedStateOf { BrightLessonRecapMotion.counterText(index, controller.time) } }
    BasicText(text, style = style, modifier = modifier, maxLines = 1, softWrap = false)
}

/**
 * The compact result pill (`StatCard`, BrightLessonRecap.tsx:54-72 and 79-86): a thin outlined body
 * that grows, a coloured cap that rises into its top, and an icon with a counting number that
 * lands with a small kick, under eight tiny sparkles.
 */
@Composable
internal fun BrightLessonRecapStatPill(
    index: Int,
    controller: BrightLessonRecapController,
    metrics: BrightLessonRecapMetrics,
    type: BrightLessonRecapTypography,
    modifier: Modifier = Modifier,
) {
    val stat = BrightLessonRecapStats[index]
    val density = LocalDensity.current
    val bodyHeight = metrics.compactHeight * 0.76f
    val capHeight = metrics.compactHeight * 0.27f
    val counterStyle = type.style(BrightLessonRecapFace.Black, 18f, 24f, stat.color, tabular = true)
    // The width of the final text plus 8, as RN measures it from the hidden line (BrightLessonRecap.tsx:57 and 80).
    val measurer = rememberTextMeasurer()
    val finalText = when (stat.kind) {
        BrightLessonRecapStatKind.Time -> "3:40"
        BrightLessonRecapStatKind.Accuracy -> "100%"
        BrightLessonRecapStatKind.Points -> "42"
    }
    val counterWidth = remember(finalText, counterStyle, density) {
        ceil(measurer.measure(finalText, counterStyle).size.width / density.density) + 8f
    }
    Box(modifier.height(metrics.compactHeight.dp).semantics { contentDescription = stat.spoken }) {
        repeat(8) { spark -> BrightLessonRecapStatSpark(spark, index, metrics.compactHeight, controller) }
        // The outlined body, bottom-aligned, growing from 56% x 50% of its size.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(bodyHeight.dp)
                .graphicsLayer {
                    val t = controller.time
                    val p = enter(fraction(t, stat.body, stat.body + 0.2))
                    alpha = fraction(t, stat.body - ONE_FRAME, stat.body).toFloat()
                    scaleX = (0.56 + 0.44 * p).toFloat()
                    scaleY = (0.5 + 0.5 * p).toFloat()
                }
                .background(Color.White, RoundedCornerShape(12.dp))
                .border(2.5.dp, stat.border, RoundedCornerShape(12.dp)),
        )
        // The cap slides up out of the bottom of a mask that is cut to the pill's rounded top.
        Box(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(capHeight.dp)
                .clip(RoundedCornerShape(topStart = 11.dp, topEnd = 11.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = enter(fraction(controller.time, stat.cap, stat.capEnd))
                        alpha = p.toFloat()
                        translationY = ((1 - p) * capHeight.dp.toPx()).toFloat()
                    }
                    .background(stat.border),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    stat.label,
                    style = type.style(
                        BrightLessonRecapFace.Black, 9f, 12f, Color.White,
                        letterSpacing = 0.6f, align = TextAlign.Center,
                    ),
                )
            }
        }
        // Icon and number, centred in the body, with the kick: +10% for 90 ms, then settling over 220 ms.
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(bodyHeight.dp)
                .graphicsLayer {
                    val t = controller.time
                    val p = enter(fraction(t, stat.body, stat.body + 0.2))
                    val kick = if (controller.reduced) {
                        0.0
                    } else {
                        fraction(t, stat.land, stat.land + 0.09) * (1 - enter(fraction(t, stat.land + 0.09, stat.land + 0.31)))
                    }
                    alpha = fraction(t, stat.body - ONE_FRAME, stat.body).toFloat()
                    val s = ((0.8 + 0.2 * p) * (1 + 0.1 * kick)).toFloat()
                    scaleX = s
                    scaleY = s
                },
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrightLessonRecapStatIcon(stat.kind, stat.color, 18.dp)
            Box(
                Modifier.width(counterWidth.dp).heightIn(min = (24 * metrics.fontScale + 4).dp),
                contentAlignment = Alignment.CenterStart,
            ) { BrightLessonRecapCounter(controller, index, counterStyle) }
        }
    }
}

/**
 * One of the eight tiny squares that twinkle around a pill (`StatSpark`, BrightLessonRecap.tsx:46-53):
 * they appear at the pill's sparkle time and are gone shortly after its cap has risen.
 */
@Composable
private fun BrightLessonRecapStatSpark(spark: Int, card: Int, slotHeight: Float, controller: BrightLessonRecapController) {
    val stat = BrightLessonRecapStats[card]
    val angle = spark * PI / 4
    Box(
        Modifier
            .brightLessonRecapPinned(width = 4.dp, height = 4.dp) { parentWidth, _ ->
                IntOffset(
                    (parentWidth * (0.5 + cos(angle) * 0.47)).roundToInt(),
                    (slotHeight * (0.62 + sin(angle) * 0.29)).dp.roundToPx(),
                )
            }
            .graphicsLayer {
                val t = controller.time
                val show = phase(t, stat.sparkle, stat.sparkle + 0.08) * (1 - phase(t, stat.cap + 0.1, stat.cap + 0.5))
                alpha = if (controller.reduced) 0f else (show * (0.55 + 0.45 * sin(t * 18 + spark).pow(2))).toFloat()
                rotationZ = 45f
            }
            .background(stat.border, RoundedCornerShape(0.7.dp)),
    )
}

/**
 * The full-width result card of the flow layout (`StatCard` with `stacked`, BrightLessonRecap.tsx:74-78):
 * a tinted label band, an icon and a large centred counter, fading in at the pill's body time.
 */
@Composable
internal fun BrightLessonRecapStatCard(
    index: Int,
    controller: BrightLessonRecapController,
    metrics: BrightLessonRecapMetrics,
    type: BrightLessonRecapTypography,
    modifier: Modifier = Modifier,
) {
    val stat = BrightLessonRecapStats[index]
    val shape = RoundedCornerShape(17.dp)
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = fraction(controller.time, stat.body - ONE_FRAME, stat.body).toFloat() }
            .heightIn(min = 128.dp)
            .clip(shape)
            .background(Color(0xFFFFFDF9))
            .border(1.5.dp, stat.border, shape)
            .semantics { contentDescription = stat.spoken },
    ) {
        Box(
            Modifier.fillMaxWidth().background(stat.light).padding(horizontal = 3.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                stat.label,
                style = type.style(
                    BrightLessonRecapFace.Black, 10f, null, stat.color,
                    letterSpacing = 0.3f, align = TextAlign.Center,
                ),
            )
        }
        Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp)) {
            BrightLessonRecapStatIcon(stat.kind, stat.color, 23.dp)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 5.dp)
                .heightIn(min = (ceil(34 * metrics.fontScale) + 16).dp)
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
        ) {
            BrightLessonRecapCounter(
                controller, index,
                type.style(BrightLessonRecapFace.Black, 27f, 34f, stat.color, align = TextAlign.Center, tabular = true),
                Modifier.fillMaxWidth(),
            )
        }
    }
}
