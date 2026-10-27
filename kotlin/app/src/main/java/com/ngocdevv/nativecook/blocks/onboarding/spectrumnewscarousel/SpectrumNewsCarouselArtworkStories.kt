package com.ngocdevv.nativecook.blocks.onboarding.spectrumnewscarousel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import kotlin.math.PI
import kotlin.math.sin

private const val STORIES_TAU = 2.0 * PI

/** The heights of the five bars of the audio chip (`AudioWave`). */
private val SpectrumNewsCarouselBarHeights = listOf(7, 13, 18, 11, 6)

/** The widths of the four lines in the summary card. */
private val SpectrumNewsCarouselSummaryLines = listOf(125, 104, 116, 87)

/** `AudioWave`: five bars that breathe in height and opacity on the loop's clock. */
@Composable
private fun SpectrumNewsCarouselAudioWave(drive: SpectrumNewsCarouselDrive) {
    Row(
        Modifier.graphicsLayer {
            val phase = drive.time / SPECTRUM_NEWS_CAROUSEL_LOOP * STORIES_TAU
            alpha = (0.7 + sin(phase * 9.0) * 0.25).toFloat()
            scaleY = (0.8 + sin(phase * 11.0) * 0.2).toFloat()
        },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpectrumNewsCarouselBarHeights.forEach { barHeight ->
            Box(Modifier.width(2.dp).height(barHeight.dp).background(Color(0xFFE4F8FF), RoundedCornerShape(1.dp)))
        }
    }
}

/** Page 4 (`StorySummary`): an article behind, the blue summary card, a count pill and an audio chip. */
@Composable
internal fun SpectrumNewsCarouselSummary(drive: SpectrumNewsCarouselDrive) {
    val play = remember { PathParser().parsePathString("M2 1 12 8 2 15Z").toPath() }
    Box(Modifier.size(300.dp, 285.dp)) {
        // articleBack: 176 × 166 with a 1 dp line and 17 dp of padding, turned 6 degrees to the left.
        Box(
            Modifier
                .offset(49.dp, 90.dp)
                .size(176.dp, 166.dp)
                .graphicsLayer { rotationZ = -6f }
                .cssBoxShadow(spectrumNewsCarouselHex(0x3C3F4A20L), 7.dp, 16.dp, 10.dp)
                .background(Color(0xFFFDFCFA), RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFFE7E5E0), RoundedCornerShape(10.dp)),
        ) {
            // The line takes 1 dp of the box, so the padding starts inside it: 1 + 17.
            Column(Modifier.padding(18.dp)) {
                SpectrumNewsCarouselArtText("THE DAILY FIELD", size = 6f, color = Color(0xFF8D8B85), weight = FontWeight.Bold, letterSpacing = 0.9f)
                SpectrumNewsCarouselArtText(
                    "A greener way\nthrough the city",
                    size = 13f,
                    color = Color(0xFF807F79),
                    weight = FontWeight.Bold,
                    lineHeight = 17f,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                )
                for (line in 1..5) {
                    Box(
                        Modifier
                            .padding(top = 5.dp)
                            .width(if (line == 5) 85.dp else 134.dp)
                            .height(3.dp)
                            .background(Color(0xFFD9D7D0), RoundedCornerShape(1.5f.dp)),
                    )
                }
            }
            // storyDot: left 17, bottom 14 of the padding box.
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(18.dp, (-15).dp)
                    .size(22.dp)
                    .background(Color(0xFF354145), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                SpectrumNewsCarouselArtText("F", size = 11f, color = Color.White, weight = FontWeight.Bold)
            }
        }
        // summaryCard: right 20, top 61, 172 × 128.
        Box(
            Modifier
                .offset(108.dp, 61.dp)
                .size(172.dp, 128.dp)
                .cssBoxShadow(spectrumNewsCarouselHex(0x2F77962FL), 2.dp, 3.dp, 17.dp)
                .cssBoxShadow(spectrumNewsCarouselHex(0x1E75943FL), 12.dp, 18.dp, 17.dp)
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF28BADB), Color(0xFF168ACB), Color(0xFF1B68B9))),
                    RoundedCornerShape(17.dp),
                )
                .padding(horizontal = 14.dp, vertical = 18.dp),
        ) {
            Column {
                SpectrumNewsCarouselArtText(
                    "THE SHORT VERSION",
                    size = 7f,
                    color = Color(0xFFCCF4FE),
                    weight = FontWeight.Bold,
                    letterSpacing = 0.8f,
                    modifier = Modifier.padding(bottom = 13.dp),
                )
                SpectrumNewsCarouselSummaryLines.forEach { lineWidth ->
                    Row(
                        Modifier.padding(bottom = 11.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(5.dp).background(Color(0xFFC5F2FF), CircleShape))
                        Box(Modifier.width(lineWidth.dp).height(5.dp).background(Color(0xFFB7EBFA), RoundedCornerShape(3.dp)))
                    }
                }
            }
        }
        // countPill: left 19, top 196, 28 tall; the small source tag hangs 15 dp off its right edge.
        Box(
            Modifier
                .offset(19.dp, 196.dp)
                .height(28.dp)
                .cssBoxShadow(spectrumNewsCarouselHex(0x237B9D40L), 4.dp, 8.dp, 14.dp)
                .background(Color(0xFF26A8D1), RoundedCornerShape(14.dp)),
        ) {
            SpectrumNewsCarouselArtText(
                "16 perspectives",
                size = 10f,
                color = Color(0xFFF5FEFF),
                weight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 12.dp),
            )
            Box(Modifier.matchParentSize()) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(15.dp, 18.dp)
                        .background(Color(0xFF4D6E80), RoundedCornerShape(8.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    SpectrumNewsCarouselArtText("N  F", size = 6f, color = Color(0xFFD3E1EB))
                }
            }
        }
        // audioChip: right 37, top 225, 78 × 32.
        Box(
            Modifier
                .offset(185.dp, 225.dp)
                .size(78.dp, 32.dp)
                .cssBoxShadow(spectrumNewsCarouselHex(0x21749340L), 5.dp, 8.dp, 16.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFF30C2DD), Color(0xFF1988C7))), RoundedCornerShape(16.dp)),
        ) {
            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpectrumNewsCarouselAudioWave(drive)
                Canvas(Modifier.size(13.dp, 16.dp)) {
                    scale(density, density, pivot = Offset.Zero) { drawPath(play, Color(0xFFEAFBFF)) }
                }
            }
        }
    }
}

/** Page 5 (`StoryQuestions`): a search pill, the waterfront photograph and a question card with its answer. */
@Composable
internal fun SpectrumNewsCarouselQuestions(photo: ImageBitmap) {
    Box(Modifier.size(300.dp, 285.dp)) {
        // askPill: top 63, left 73, 167 × 29.
        Row(
            Modifier
                .offset(73.dp, 63.dp)
                .size(167.dp, 29.dp)
                .cssBoxShadow(spectrumNewsCarouselHex(0x625A4D18L), 2.dp, 8.dp, 16.dp)
                .background(Color(0xFFFFFEFC), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpectrumNewsCarouselArtText("⌕", size = 12f, color = Color(0xFF389CB1))
            SpectrumNewsCarouselArtText("A little more context", size = 9f, color = Color(0xFFA6A5A0))
        }
        // photoCard: left 20, top 105, 198 × 142, turned 4 degrees to the left, its corners clipped.
        Column(
            Modifier
                .offset(20.dp, 105.dp)
                .size(198.dp, 142.dp)
                .graphicsLayer { rotationZ = -4f }
                .cssBoxShadow(spectrumNewsCarouselHex(0x3B362D28L), 8.dp, 14.dp, 9.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0xFFE5E8DB)),
        ) {
            Image(
                bitmap = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxWidth().height(119.dp),
            )
            SpectrumNewsCarouselArtText(
                "ROOM FOR THE RIVER",
                size = 6f,
                color = Color(0xFF5C675D),
                weight = FontWeight.Bold,
                letterSpacing = 1.1f,
                modifier = Modifier.padding(start = 10.dp, top = 8.dp),
            )
        }
        // questionCard: right 13, top 133, 169 wide, its corners clipped.
        Column(
            Modifier
                .offset(118.dp, 133.dp)
                .width(169.dp)
                .cssBoxShadow(spectrumNewsCarouselHex(0x3A312C15L), 1.dp, 2.dp, 12.dp)
                .cssBoxShadow(spectrumNewsCarouselHex(0x3A312C28L), 8.dp, 18.dp, 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFFFDFC)),
        ) {
            Box(Modifier.fillMaxWidth().background(Color(0xFF29292D)).padding(horizontal = 12.dp, vertical = 11.dp)) {
                SpectrumNewsCarouselArtText(
                    "What changes when a city makes room for water?",
                    size = 10f,
                    color = Color(0xFFF8F7F4),
                    weight = FontWeight.SemiBold,
                    lineHeight = 13f,
                )
            }
            Column(Modifier.padding(12.dp)) {
                SpectrumNewsCarouselArtText(
                    "More public space.\nA cooler walk home.\nA river people can reach.",
                    size = 9f,
                    color = Color(0xFF7E7A73),
                    lineHeight = 14f,
                )
                SpectrumNewsCarouselArtText(
                    "3 source perspectives ↗",
                    size = 6f,
                    color = Color(0xFFA7A19A),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
