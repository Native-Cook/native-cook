package com.ngocdevv.nativecook.blocks.onboarding.nocturneprogramcarousel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.cssBoxShadow

private val RingXs = doubleArrayOf(5.25, 5.75, 6.0, 6.25, 6.5, 6.9, 7.4)
private val RingYs = doubleArrayOf(0.0, 0.04, 0.2, 0.44, 0.64, 0.78, 0.78)
private val TensXs = doubleArrayOf(5.25, 6.25, 6.5, 6.583, 6.75, 7.0, 7.35)
private val TensYs = doubleArrayOf(0.0, 0.0, 1.4, 2.4, 4.4, 6.2, 7.0)
private val OnesXs = doubleArrayOf(5.25, 5.75, 6.0, 6.25, 6.5, 6.9, 7.1)
private val OnesYs = doubleArrayOf(0.0, 0.6, 2.2, 5.2, 7.0, 7.85, 8.0)
private val WeekDays = listOf("M", "T", "W", "T", "F", "S", "S")
private val ChartHeights = listOf(18, 31, 24, 42, 36, 53, 48)

/** The score ring's stroke: `#397AFF` at the top of the ring to `#6AD9DE` at the bottom (`scoreArc`, turned with the ring). */
private val RingBrush = Brush.linearGradient(
    colors = listOf(Color(0xFF397AFF), Color(0xFF6AD9DE)),
    start = Offset(45f, 9f),
    end = Offset(45f, 81f),
)

/**
 * `MiniDashboard`: the second card's artwork, [height] dp tall. A miniature phone rises out of the
 * bottom (5.1 to 5.8 s) showing an invented evening check-in; the routine score's two digits roll
 * in clipped columns and its ring fills (5.25 to 7.4 s, settling on 78), then the score swells by
 * 13% (7.25 to 8 s). A fade into the card's colour covers the phone's lower edge. Under Reduce
 * Motion everything is in place, the score swollen.
 */
@Composable
internal fun NocturneProgramCarouselDashboardArt(
    time: () -> Double,
    reduced: Boolean,
    height: Float,
) {
    val motion = NocturneProgramCarouselMotion
    Box(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clipToBounds(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val shape = RoundedCornerShape(25.dp)
        Box(
            Modifier
                .offset(y = 12.dp)
                .wrapContentSize(Alignment.TopCenter, unbounded = true)
                .size(204.dp, (height + 30f).dp)
                .graphicsLayer {
                    val t = time()
                    alpha = if (reduced) 1f else motion.segment(t, 5.1, 5.65).toFloat()
                    translationY = if (reduced) 0f else (16.0 * (1.0 - motion.enter(motion.segment(t, 5.1, 5.8))) * density).toFloat()
                }
                .cssBoxShadow(Color(0x80020915), 6.dp, 12.dp, 25.dp)
                .background(Color(0xFF071421), shape)
                .border(3.dp, Color(0xFF344357), shape),
        ) {
            // The speaker slot: 78 dp in from the inside of the border, 5 dp down.
            Box(
                Modifier
                    .offset(x = 81.dp, y = 8.dp)
                    .size(43.dp, 4.dp)
                    .background(Color(0xFF020A13), RoundedCornerShape(3.dp)),
            )
            Column(
                Modifier
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 19.dp, end = 16.dp),
            ) {
                PhoneContent(time, reduced)
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset(y = 1.dp)
                .fillMaxWidth()
                .height(45.dp)
                .background(Brush.verticalGradient(0f to Color(0x000F2043), 1f to Color(0xFF0F2043))),
        )
    }
}

@Composable
private fun PhoneContent(time: () -> Double, reduced: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        PhoneText("9:41", Color(0xFFAFC2D4), 7f, FontWeight.Bold)
        PhoneText("••• ▰", Color(0xFFAFC2D4), 7f, FontWeight.Bold)
    }
    PhoneText("Your evening", Color(0xFFE2EDFA), 14f, FontWeight.Bold)
    PhoneText("MONDAY, OCT 26", Color(0xFF7492AB), 7f, modifier = Modifier.padding(top = 5.dp), letterSpacing = 1f)
    Row(
        Modifier.padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        WeekDays.forEachIndexed { index, day ->
            Box(
                Modifier
                    .size(17.dp)
                    .border(1.dp, if (index == 0) Color(0xFF64E0CE) else Color(0xFF27435C), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                PhoneText(day, Color(0xFF85A3BA), 7f)
            }
        }
    }
    Row(
        Modifier.padding(top = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Score(time, reduced)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                PhoneText("Wind-down", Color(0xFF7897AA), 8f)
                PhoneText("22 min", Color(0xFFD0E2EA), 11f, FontWeight.SemiBold, Modifier.padding(top = 3.dp))
            }
            Column {
                PhoneText("This week", Color(0xFF7897AA), 8f)
                PhoneText("5 evenings", Color(0xFFD0E2EA), 11f, FontWeight.SemiBold, Modifier.padding(top = 3.dp))
            }
        }
    }
    PhoneText("A little more consistent", Color(0xFF86A0B3), 8f, modifier = Modifier.padding(top = 12.dp))
    Row(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .height(60.dp)
            .drawBehind {
                // The chart's bottom border: 1 dp inside its 60 dp box.
                val line = 1.dp.toPx()
                drawRect(Color(0xFF1D374B), Offset(0f, size.height - line), Size(size.width, line))
            }
            .padding(start = 3.dp, end = 3.dp, bottom = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        ChartHeights.forEachIndexed { index, bar ->
            Box(
                Modifier
                    .width(13.dp)
                    .height(bar.dp)
                    .background(
                        if (index == 6) Color(0xFF6FE8D6) else Color(0xFF28617B),
                        RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                    ),
            )
        }
    }
    PhoneText(
        "SAMPLE CHECK-IN · NOT A HEALTH SCORE",
        Color(0xFF567486),
        5f,
        modifier = Modifier.padding(top = 10.dp),
        letterSpacing = 0.25f,
    )
}

/** A phone label: fixed size (`allowFontScaling={false}`). */
@Composable
private fun PhoneText(
    text: String,
    color: Color,
    size: Float,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    letterSpacing: Float = 0f,
) {
    NocturneProgramCarouselText(
        text = text,
        color = color,
        size = size,
        modifier = modifier,
        weight = weight,
        letterSpacing = letterSpacing,
        scales = false,
    )
}

/** The routine score: a 90 dp ring with two rolling digits and a caption, swelling by 13% at 7.25 s. */
@Composable
private fun Score(time: () -> Double, reduced: Boolean) {
    val motion = NocturneProgramCarouselMotion
    Box(
        Modifier
            .size(90.dp)
            .graphicsLayer {
                val swell = if (reduced) 1.13 else 1.0 + 0.13 * motion.enter(motion.segment(time(), 7.25, 8.0))
                scaleX = swell.toFloat()
                scaleY = swell.toFloat()
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val progress = if (reduced) 0.78 else motion.interpolate(time(), RingXs, RingYs)
            scale(density, density, pivot = Offset.Zero) {
                drawCircle(Color(0xFF173044), radius = 36f, center = Offset(45f, 45f), style = Stroke(width = 4f))
                if (progress > 0.0) {
                    // A dash of `226.2 * progress` along a circle that starts at the top.
                    drawArc(
                        brush = RingBrush,
                        startAngle = -90f,
                        sweepAngle = (360.0 * progress).toFloat(),
                        useCenter = false,
                        topLeft = Offset(9f, 9f),
                        size = Size(72f, 72f),
                        style = Stroke(width = 4f, cap = StrokeCap.Round),
                    )
                }
            }
        }
        Row(
            Modifier
                .offset(x = 23.dp, y = 24.dp)
                .height(32.dp),
        ) {
            ScoreDigit(time, reduced, tens = true)
            ScoreDigit(time, reduced, tens = false)
        }
        NocturneProgramCarouselText(
            text = "ROUTINE",
            color = Color(0xFF6F96A8),
            size = 6f,
            modifier = Modifier
                .offset(x = 19.dp, y = 59.dp)
                .width(52.dp),
            letterSpacing = 1f,
            align = TextAlign.Center,
            scales = false,
        )
    }
}

/** One digit of the score: a clipped 22 by 32 window onto a column of 0 to 9 that rolls up to the 7 or the 8 of 78. */
@Composable
private fun ScoreDigit(time: () -> Double, reduced: Boolean, tens: Boolean) {
    val motion = NocturneProgramCarouselMotion
    Box(
        Modifier
            .size(22.dp, 32.dp)
            .clipToBounds(),
    ) {
        Column(
            Modifier
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .graphicsLayer {
                    val value = when {
                        reduced -> if (tens) 7.0 else 8.0
                        tens -> motion.interpolate(time(), TensXs, TensYs)
                        else -> motion.interpolate(time(), OnesXs, OnesYs)
                    }
                    translationY = (-value * 32.0 * density).toFloat()
                },
        ) {
            for (digit in 0..9) {
                Box(Modifier.size(22.dp, 32.dp), contentAlignment = Alignment.Center) {
                    NocturneProgramCarouselText(
                        text = digit.toString(),
                        color = Color(0xFFDCECF7),
                        size = 27f,
                        lineHeight = 32f,
                        weight = FontWeight.Bold,
                        align = TextAlign.Center,
                        scales = false,
                        tabular = true,
                    )
                }
            }
        }
    }
}
