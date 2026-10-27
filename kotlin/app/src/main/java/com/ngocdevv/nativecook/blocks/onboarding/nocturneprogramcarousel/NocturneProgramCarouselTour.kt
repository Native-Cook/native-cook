package com.ngocdevv.nativecook.blocks.onboarding.nocturneprogramcarousel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

/**
 * The tour: the starry welcome, the program (header, cards, the Build my evening plan button) and the
 * tab bar of the app around it, in that order.
 *
 * The program fades in between 1.5 and 2.25 s and out between 15.5 and 16.1 s when the loop runs
 * (it is simply there otherwise, and while a finger holds it). Hidden, it ignores touches and is
 * hidden from accessibility. The tab bar belongs to the app, so it stays put while the welcome hands
 * over to the program; a copy of it under the button, invisible, keeps the room it takes.
 */
@Composable
internal fun BoxScope.NocturneProgramCarouselTour(
    playback: NocturneProgramCarouselPlayback,
    metrics: NocturneProgramCarouselMetrics,
    images: NocturneProgramCarouselImages,
    reduced: Boolean,
    timelineDriven: Boolean,
    previewing: Boolean,
    programVisible: Boolean,
    page: Int,
    cardHeight: Float,
    onCardHeight: (Float) -> Unit,
    onGo: (Int) -> Unit,
    onLanded: (Int) -> Unit,
    onPressIn: () -> Unit,
    onPressOut: () -> Unit,
    onStartPlan: () -> Unit,
) {
    val motion = NocturneProgramCarouselMotion
    val density = LocalDensity.current
    val scroll = key(metrics.layoutKey) { rememberScrollState() }
    LaunchedEffect(scroll, playback) {
        // A scroll starting or stopping is `onScrollBeginDrag` / `onScrollEndDrag`; the first value is not one.
        snapshotFlow { scroll.isScrollInProgress }
            .distinctUntilChanged()
            .drop(1)
            .collect { playback.readingScroll(it) }
    }

    NocturneProgramCarouselWelcome(playback, reduced, timelineDriven, previewing, programVisible)

    Column(
        Modifier
            .fillMaxSize()
            .then(if (programVisible) Modifier else Modifier.clearAndSetSemantics { })
            .graphicsLayer {
                val shown = !timelineDriven || (!previewing && playback.touching) || reduced
                val t = playback.time
                alpha = if (shown) 1f else (motion.enter(motion.segment(t, 1.5, 2.25)) * (1.0 - motion.enter(motion.segment(t, 15.5, 16.1)))).toFloat()
            }
            .padding(top = (metrics.insetTop + 18f).dp),
    ) {
        if (!metrics.largeText) {
            TourHeader()
        }
        NocturneProgramCarouselBlurViewport(
            enabled = programVisible,
            overflowing = { scroll.maxValue > 4f * density.density },
            fadeTop = Color(0xFF061528),
            fadeBottom = Color(0xFF020B18),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .nocturneProgramCarouselReading(playback, programVisible),
            ) {
                val viewport = maxHeight
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll),
                ) {
                    // The scroll view's content grows to the viewport and centres the cards in it
                    // (flex-start with a 24 dp gap for large text, where the header scrolls too).
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = viewport)
                            .padding(
                                top = (if (metrics.largeText) 8f else 20f).dp,
                                bottom = (if (metrics.largeText) 8f else 20f + metrics.height * 0.028f).dp,
                            ),
                        verticalArrangement = if (metrics.largeText) Arrangement.spacedBy(24.dp, Alignment.Top) else Arrangement.Center,
                    ) {
                        if (metrics.largeText) {
                            TourHeader()
                        }
                        NocturneProgramCarouselCards(
                            playback = playback,
                            metrics = metrics,
                            images = images,
                            reduced = reduced,
                            page = page,
                            cardHeight = cardHeight,
                            interactive = programVisible && !previewing,
                            onCardHeight = onCardHeight,
                            onGo = onGo,
                            onLanded = onLanded,
                        )
                    }
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 30.dp, end = 30.dp, bottom = metrics.footerBottom.dp),
            verticalArrangement = Arrangement.spacedBy(34.dp),
        ) {
            BuildPlanButton(playback, metrics, reduced, timelineDriven, programVisible, onPressIn, onPressOut, onStartPlan)
            NocturneProgramCarouselTabBar(Modifier.graphicsLayer { alpha = 0f })
        }
    }
    // The tab bar belongs to the app around the tour, so it stays put while the welcome hands over.
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(start = 30.dp, end = 30.dp, bottom = metrics.footerBottom.dp),
    ) {
        NocturneProgramCarouselTabBar()
    }
}

/** The header above the cards: the mark, "The Nocturne program" and a line under it. */
@Composable
private fun TourHeader() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        NocturneProgramCarouselMark(34f)
        NocturneProgramCarouselText(
            text = "The Nocturne program",
            color = Color(0xFFF2F5FD),
            size = 22f,
            lineHeight = 28f,
            weight = FontWeight.Bold,
            letterSpacing = -0.5f,
            align = TextAlign.Center,
            maxScale = 1.5f,
            modifier = Modifier.padding(top = 8.dp),
        )
        NocturneProgramCarouselText(
            text = "A few good habits. A little more calm.",
            color = Color(0xFF9AA8C6),
            size = 14f,
            lineHeight = 19f,
            align = TextAlign.Center,
            maxScale = 1.8f,
        )
    }
}

/**
 * The Build my evening plan button: the blue `#005EFE` button in a 28 dp-rounded window, with a
 * light sheen that sweeps across it once per page (2.4 to 3.05 s and 8.9 to 9.55 s of the loop). The sheen is a
 * 64 dp-wide band, twice the window's height, tilted 18 degrees; it travels `window width + 64` dp
 * (the window's width, not the button's) and shows at 45%.
 */
@Composable
private fun BuildPlanButton(
    playback: NocturneProgramCarouselPlayback,
    metrics: NocturneProgramCarouselMetrics,
    reduced: Boolean,
    timelineDriven: Boolean,
    programVisible: Boolean,
    onPressIn: () -> Unit,
    onPressOut: () -> Unit,
    onStartPlan: () -> Unit,
) {
    val motion = NocturneProgramCarouselMotion
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp)),
    ) {
        NocturneProgramCarouselButton(
            label = "Build my evening plan",
            reduced = reduced,
            onPress = onStartPlan,
            modifier = Modifier.fillMaxWidth(),
            background = Color(0xFF005EFE),
            minHeight = 55.dp,
            enabled = programVisible,
            onPressIn = onPressIn,
            onPressOut = onPressOut,
        )
        Canvas(Modifier.matchParentSize()) {
            val t = playback.time
            val start = if (t < 6.0) 2.4 else 8.9
            if (reduced || !timelineDriven || t < start || t > start + 0.65) return@Canvas
            val travel = motion.segment(t, start, start + 0.65).toFloat()
            val band = 64f * density
            withTransform({
                translate(left = (metrics.width + 64f) * travel * density, top = 0f)
                rotate(degrees = 18f, pivot = Offset(-band / 2f, size.height / 2f))
            }) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color(0x00FFFFFF),
                        0.5f to Color(0xAAFFFFFF),
                        1f to Color(0x00FFFFFF),
                        startX = -band,
                        endX = 0f,
                    ),
                    topLeft = Offset(-band, -size.height / 2f),
                    size = Size(band, size.height * 2f),
                    alpha = 0.45f,
                )
            }
        }
    }
}
