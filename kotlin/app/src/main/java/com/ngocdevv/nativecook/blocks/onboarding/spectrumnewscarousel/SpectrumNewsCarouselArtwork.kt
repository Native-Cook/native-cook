package com.ngocdevv.nativecook.blocks.onboarding.spectrumnewscarousel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAU = 2.0 * PI

/** The five illustrations of `SpectrumArtwork.tsx`: each is drawn on a 300 × 285 board. */
private const val BOARD_WIDTH = 300f
private const val BOARD_HEIGHT = 285f

/**
 * `SpectrumIllustration`: the board for [page], drawn at 300 × 285 and scaled from its top left
 * corner to 300 × 285 times [boardScale], the room the layout gives it.
 */
@Composable
internal fun SpectrumNewsCarouselIllustration(page: Int, drive: SpectrumNewsCarouselDrive, boardScale: Float, photo: ImageBitmap) {
    Box(Modifier.size((BOARD_WIDTH * boardScale).dp, (BOARD_HEIGHT * boardScale).dp)) {
        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .size(BOARD_WIDTH.dp, BOARD_HEIGHT.dp)
                .graphicsLayer {
                    scaleX = boardScale
                    scaleY = boardScale
                    transformOrigin = TransformOrigin(0f, 0f)
                },
        ) {
            when (page) {
                0 -> SpectrumNewsCarouselWelcomeMark()
                1 -> SpectrumNewsCarouselSources(drive)
                2 -> SpectrumNewsCarouselTopics(boardScale)
                3 -> SpectrumNewsCarouselSummary(drive)
                else -> SpectrumNewsCarouselQuestions(photo)
            }
        }
    }
}

/** `SpectrumMark`: a black disc with 23 pale dots laid out as a sunflower spiral, on a 72 unit board. */
@Composable
internal fun SpectrumNewsCarouselMark(markSize: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(markSize)) {
        val unit = size.width / 72f
        drawCircle(Color(0xFF17171B), radius = 35f * unit, center = Offset(36f * unit, 36f * unit))
        for (i in 0 until 23) {
            val angle = i * 2.4
            val radius = 5.0 + sqrt(i / 22.0) * 19.0
            val x = 36.0 + cos(angle) * radius
            val y = 36.0 + sin(angle) * radius
            drawCircle(
                Color(0xFFF7F6F2),
                radius = (if (i % 4 == 0) 2.2f else 1.5f) * unit,
                center = Offset(x.toFloat() * unit, y.toFloat() * unit),
            )
        }
    }
}

/** Page 1 (`welcomeMark`): the mark and the wordmark, 135 down the board, 12 apart. */
@Composable
private fun SpectrumNewsCarouselWelcomeMark() {
    Column(
        Modifier.fillMaxWidth().padding(top = 135.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SpectrumNewsCarouselMark(90.dp)
        SpectrumNewsCarouselArtText("Spectrum", size = 27f, color = Color(0xFF111111), weight = FontWeight.ExtraBold, letterSpacing = -0.6f)
    }
}

private class SpectrumNewsCarouselBadge(val name: String, val color: Color, val x: Float, val y: Float, val size: Float)

/** `BADGES`: ten invented publications around the news orb. */
private val SpectrumNewsCarouselBadges = listOf(
    SpectrumNewsCarouselBadge("DAILY", Color(0xFF2163D2), 50f, 78f, 30f),
    SpectrumNewsCarouselBadge("N", Color(0xFF18171C), 129f, 54f, 33f),
    SpectrumNewsCarouselBadge("POST", Color(0xFF8B1B40), 209f, 69f, 29f),
    SpectrumNewsCarouselBadge("S", Color(0xFFDB5839), 28f, 113f, 22f),
    SpectrumNewsCarouselBadge("LOOP", Color(0xFF143D36), 242f, 114f, 29f),
    SpectrumNewsCarouselBadge("F", Color(0xFF1E2228), 69f, 129f, 26f),
    SpectrumNewsCarouselBadge("ECHO", Color(0xFFE2B589), 186f, 105f, 32f),
    SpectrumNewsCarouselBadge("R", Color(0xFF5B92B7), 99f, 87f, 22f),
    SpectrumNewsCarouselBadge("M", Color(0xFF3D725B), 170f, 43f, 23f),
    SpectrumNewsCarouselBadge("O", Color(0xFFAA629F), 235f, 63f, 21f),
)

/** One badge: a disc with its letters that drifts a few dp on the loop's clock (`Badge`). */
@Composable
private fun SpectrumNewsCarouselBadgeView(index: Int, drive: SpectrumNewsCarouselDrive) {
    val badge = SpectrumNewsCarouselBadges[index]
    Box(
        Modifier
            .offset(badge.x.dp, (badge.y - 34f).dp)
            .size(badge.size.dp)
            .graphicsLayer {
                val phase = drive.time / SPECTRUM_NEWS_CAROUSEL_LOOP * TAU
                translationX = (sin(phase * 2.0 + index) * 3.0 * this.density).toFloat()
                translationY = (cos(phase * 3.0 + index) * 4.0 * this.density).toFloat()
            }
            .cssBoxShadow(spectrumNewsCarouselHex(0x55556630L), 3.dp, 5.dp, (badge.size / 2f).dp)
            .background(badge.color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        SpectrumNewsCarouselArtText(
            text = badge.name,
            size = if (badge.name.length > 1) 6f else 14f,
            color = if (index == 6) Color(0xFF7D4930) else Color.White,
            weight = FontWeight.ExtraBold,
        )
    }
}

/** Page 2 (`NewsSources`): two pale halos, ten drifting badges and the blue news orb. */
@Composable
private fun SpectrumNewsCarouselSources(drive: SpectrumNewsCarouselDrive) {
    val glyph = remember {
        PathParser().parsePathString("M7 13H5v18q0 3 3 3h23M16 14h12M16 18h12M16 22h4v5h-4zM24 23h5M24 27h5").toPath()
    }
    Box(Modifier.size(BOARD_WIDTH.dp, BOARD_HEIGHT.dp)) {
        Box(Modifier.offset(35.dp, 26.dp).size(230.dp).background(spectrumNewsCarouselHex(0xEAF2F84DL), CircleShape))
        Box(Modifier.offset(62.5f.dp, 53.5f.dp).size(175.dp).background(spectrumNewsCarouselHex(0xC4E6F15EL), CircleShape))
        for (index in SpectrumNewsCarouselBadges.indices) SpectrumNewsCarouselBadgeView(index, drive)
        Box(
            Modifier
                .offset(99.dp, 79.dp)
                .size(102.dp)
                .cssBoxShadow(spectrumNewsCarouselHex(0x1B659950L), 2.dp, 3.dp, 51.dp)
                .cssBoxShadow(spectrumNewsCarouselHex(0x287A9E55L), 9.dp, 14.dp, 51.dp)
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF24BDD8), Color(0xFF137BCB), Color(0xFF1962B4))),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(40.dp)) {
                scale(density, density, pivot = Offset.Zero) {
                    val ink = Color(0xFFF2FDFF)
                    drawRoundRect(ink, Offset(11f, 8f), Size(23f, 26f), CornerRadius(2f), style = Stroke(width = 2f))
                    drawPath(glyph, ink, style = Stroke(width = 2f))
                }
            }
        }
    }
}

private val SpectrumNewsCarouselLessLabels = listOf("Less noise", "Celebrity", "Hot takes")
private val SpectrumNewsCarouselMoreLabels = listOf("Design", "Our planet", "Technology", "Culture")
private val SpectrumNewsCarouselLessColors = listOf(Color(0xFFB52C48), Color(0xFFCF4057), Color(0xFFF0C7CE))
private val SpectrumNewsCarouselMoreColors = listOf(Color(0xFF7EBC52), Color(0xFF96CB6F), Color(0xFFAFD694), Color(0xFFD0E7BA))

/** A topic chip (`topic`): 17 dp tall, as wide as [width], its label 8 dp type. */
@Composable
private fun SpectrumNewsCarouselTopic(label: String, width: Dp, fill: Color, ink: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.width(width).height(17.dp).background(fill, RoundedCornerShape(9.dp)).padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        SpectrumNewsCarouselArtText(label, size = 8f, color = ink, weight = FontWeight.SemiBold)
    }
}

/** Page 3 (`TopicSelection`): a grey silhouette above the topics to see less (red) and more (green) of. */
@Composable
private fun SpectrumNewsCarouselTopics(boardScale: Float) {
    val person = remember { PathParser().parsePathString("M5 78c0-23 14-35 33-35s33 12 33 35q-33 9-66 0Z").toPath() }
    // `translateY: -26 / scale` on the board, which comes out as 26 dp up on the screen.
    Box(Modifier.offset(y = (-26f / boardScale).dp).size(BOARD_WIDTH.dp, BOARD_HEIGHT.dp)) {
        Canvas(Modifier.offset(112.dp, 56.dp).size(76.dp, 91.dp)) {
            scale(density, density, pivot = Offset.Zero) {
                drawCircle(Color(0xFF8C8C8C), radius = 17f, center = Offset(38f, 22f))
                drawPath(person, Color(0xFF969696))
            }
        }
        Row(
            Modifier.offset(22.dp, 160.dp).width(256.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp), horizontalAlignment = Alignment.End) {
                SpectrumNewsCarouselLessLabels.forEachIndexed { i, label ->
                    SpectrumNewsCarouselTopic(
                        label = "$label −",
                        width = (107 - i * 13).dp,
                        fill = SpectrumNewsCarouselLessColors[i],
                        ink = if (i == 2) Color(0xFF9E5E68) else Color(0xFFFFF8F6),
                    )
                }
            }
            Box(Modifier.width(2.dp).height(94.dp).background(Color(0xFFD4D2CF)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                SpectrumNewsCarouselMoreLabels.forEachIndexed { i, label ->
                    SpectrumNewsCarouselTopic(
                        label = "+ $label",
                        width = (103 - i * 11).dp,
                        fill = SpectrumNewsCarouselMoreColors[i],
                        ink = Color(0xFF3B6C2B),
                    )
                }
            }
        }
    }
}
