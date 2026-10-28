package com.ngocdevv.nativecook.blocks.onboarding.nocturneprogramcarousel

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/** One card of the tour: its title and its two-line body (`PAGES`). */
internal class NocturneProgramCarouselPage(val title: String, val body: String)

internal val NocturneProgramCarouselPages = listOf(
    NocturneProgramCarouselPage("An evening that fits you", "A small, thoughtful routine.\nMake it yours, one night at a time."),
    NocturneProgramCarouselPage("Notice your rhythm", "Check in with your evenings.\nSee the habits you are building."),
    NocturneProgramCarouselPage("Make space to unwind", "Slow down and notice how you feel.\nA little attention goes a long way."),
)

private val CopyXs = doubleArrayOf(5.0, 5.05, 5.1, 5.15, 5.2, 5.25, 5.3, 5.4)
private val CopyYs = doubleArrayOf(0.0, 0.117, 0.224, 0.384, 0.581, 0.733, 0.905, 1.0)

private val FrameShape = RoundedCornerShape(22.dp)
private val CardShape = RoundedCornerShape(18.dp)
private val CardBrush = Brush.verticalGradient(
    0f to Color(0xFF0B2A70),
    0.45f to Color(0xFF0A2560),
    0.75f to Color(0xFF071C48),
    1f to Color(0xFF061840),
)

/**
 * The three cards on their curved shelf, in a window [cardHeight] dp tall that clips them, with the
 * pan that swipes between them.
 *
 * The track moves so the card at `position` is centred; neighbours stand back (scale down to 0.86),
 * sink into the night (a `#050C1A` veil up to 58%) and turn up to 9 degrees toward the centre, in a
 * perspective of 900 dp. Each card's artwork lags its frame by a fifth of a card width and its copy
 * comes out of a soft focus once the timeline reaches it. Under Reduce Motion the cards only
 * change place, and the copy is simply there.
 *
 * [interactive] is false while the tour is hidden or frozen on a preview pose. [onCardHeight]
 * reports each card's own height (dp) for the window's measurement; [onGo] is the accessibility
 * actions' way to another page; [onLanded] settles the clock on the page a swipe turned to.
 */
@Composable
internal fun NocturneProgramCarouselCards(
    playback: NocturneProgramCarouselPlayback,
    metrics: NocturneProgramCarouselMetrics,
    images: NocturneProgramCarouselImages,
    reduced: Boolean,
    page: Int,
    cardHeight: Float,
    interactive: Boolean,
    onCardHeight: (Float) -> Unit,
    onGo: (Int) -> Unit,
    onLanded: (Int) -> Unit,
) {
    val current = NocturneProgramCarouselPages[page]
    Box(
        Modifier
            .fillMaxWidth()
            .height(cardHeight.dp)
            .clipToBounds()
            .nocturneProgramCarouselPan(playback, metrics.stride, reduced, interactive, onLanded)
            .clearAndSetSemantics {
                contentDescription =
                    "Nocturne tour, page ${page + 1} of ${NocturneProgramCarouselPages.size}. ${current.title}. ${current.body}"
                customActions = listOf(
                    CustomAccessibilityAction("Next page") {
                        onGo(page + 1)
                        true
                    },
                    CustomAccessibilityAction("Previous page") {
                        onGo(page - 1)
                        true
                    },
                )
            },
    ) {
        // The track: card `index` sits `index * stride` along it, and it slides by `position`.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val slide = (metrics.width - metrics.cardWidth) / 2f - playback.position() * metrics.stride
                    translationX = slide * density
                },
        ) {
            for (index in NocturneProgramCarouselPages.indices) {
                CardFrame(index, playback, metrics, images, reduced, onCardHeight)
            }
        }
    }
}

/** `CardFrame`: a 22 dp-rounded window the card turns and shrinks in, under a veil that darkens it when it is not the one in focus. */
@Composable
private fun CardFrame(
    index: Int,
    playback: NocturneProgramCarouselPlayback,
    metrics: NocturneProgramCarouselMetrics,
    images: NocturneProgramCarouselImages,
    reduced: Boolean,
    onCardHeight: (Float) -> Unit,
) {
    Box(
        Modifier
            .requiredWidth(metrics.cardWidth.dp)
            .fillMaxHeight()
            .offset(x = (index * metrics.stride).dp)
            .graphicsLayer {
                val distance = index - playback.position()
                val shrink = if (reduced) 1f else 1f - 0.14f * abs(distance).coerceAtMost(1f)
                scaleX = shrink
                scaleY = shrink
                rotationY = if (reduced) 0f else distance.coerceIn(-1f, 1f) * -9f
                cameraDistance = 900f * density
                shape = FrameShape
                clip = true
            },
    ) {
        TourCard(index, playback, metrics, images, reduced, onCardHeight)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = 0.58f * abs(index - playback.position()).coerceAtMost(1f)
                }
                .background(Color(0xFF050C1A)),
        )
    }
}

/** The card itself: a blue gradient with a faint 1 dp border, its artwork on top and its copy under it. */
@Composable
private fun TourCard(
    index: Int,
    playback: NocturneProgramCarouselPlayback,
    metrics: NocturneProgramCarouselMetrics,
    images: NocturneProgramCarouselImages,
    reduced: Boolean,
    onCardHeight: (Float) -> Unit,
) {
    val density = LocalDensity.current
    val time: () -> Double = remember(playback) { { playback.time } }
    Column(
        Modifier
            .wrapContentHeight(Alignment.Top, unbounded = true)
            .width(metrics.cardWidth.dp)
            .heightIn(min = metrics.minimumCardHeight.dp)
            .onSizeChanged { onCardHeight(it.height / density.density) }
            .clip(CardShape)
            .background(CardBrush)
            .border(1.dp, Color(0x402A4C94), CardShape)
            .padding(start = 1.dp, top = 15.dp, end = 1.dp, bottom = 1.dp),
    ) {
        // The artwork lags its card by a fifth of a card width, a window onto a deeper scene.
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val lag = (index - playback.position()).coerceIn(-1f, 1f) * -(metrics.cardWidth * 0.22f)
                    translationX = if (reduced) 0f else lag * this.density
                }
                .clearAndSetSemantics { },
        ) {
            when (index) {
                0 -> NocturneProgramCarouselGuideArt(time, reduced, metrics.artHeight, images.guide)
                1 -> NocturneProgramCarouselDashboardArt(time, reduced, metrics.artHeight)
                else -> NocturneProgramCarouselScanArt(time, reduced, metrics.artHeight, images.hologram)
            }
        }
        CardCopy(index, playback, reduced)
    }
}

/** `CardCopy`: title, body and page dots, coming out of a soft focus (opacity, a 6 dp rise and a blur of up to 5) once the timeline reaches the card. */
@Composable
private fun CardCopy(index: Int, playback: NocturneProgramCarouselPlayback, reduced: Boolean) {
    val motion = NocturneProgramCarouselMotion
    val page = NocturneProgramCarouselPages[index]
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val local = playback.time - (if (index == 2) 5.017 else 0.0)
                val shown = if (reduced || index == 0) 1.0 else motion.interpolate(local, CopyXs, CopyYs)
                alpha = shown.toFloat()
                translationY = ((1.0 - shown) * 6.0 * density).toFloat()
                val blur = ((1.0 - shown) * 500.0).roundToInt() / 100.0
                renderEffect = softFocus((blur * density).toFloat())
            }
            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NocturneProgramCarouselText(
            text = page.title,
            color = Color.White,
            size = 20f,
            lineHeight = 26f,
            weight = FontWeight.Bold,
            letterSpacing = -0.4f,
            align = TextAlign.Center,
            maxScale = 1.5f,
        )
        NocturneProgramCarouselText(
            text = page.body,
            color = Color(0xFFA3B3D4),
            size = 14f,
            lineHeight = 19f,
            align = TextAlign.Center,
            maxScale = 1.7f,
            modifier = Modifier.padding(top = 12.dp),
        )
        Row(
            Modifier
                .padding(top = 22.dp)
                .clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (dot in NocturneProgramCarouselPages.indices) {
                val active = dot == index
                Box(
                    Modifier
                        .size(if (active) 14.dp else 4.dp, 4.dp)
                        .background(if (active) Color.White else Color(0xFF5D6F99), RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

/**
 * `filter: blur(Npx)`: a Gaussian blur of [sigmaPx] device pixels, or null when there is nothing to
 * blur or the device cannot (render effects need API 31).
 */
private fun softFocus(sigmaPx: Float): RenderEffect? {
    if (sigmaPx < 0.005f || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    return BlurEffect(sigmaPx, sigmaPx, TileMode.Decal)
}
