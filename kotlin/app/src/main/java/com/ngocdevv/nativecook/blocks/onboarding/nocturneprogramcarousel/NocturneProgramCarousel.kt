package com.ngocdevv.nativecook.blocks.onboarding.nocturneprogramcarousel

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** The height of the cards' window, remembered for the window and text size it was measured at (`cardMeasurement`). */
private class CardMeasurement(val key: String, val height: Float)

/** The page behind everything: `#0A1A3A` at the top, `#03101F` at 30% and `#010A16` at the bottom. */
private val PageBrush = Brush.verticalGradient(
    0f to Color(0xFF0A1A3A),
    0.3f to Color(0xFF03101F),
    1f to Color(0xFF010A16),
)

/**
 * Nocturne Program Carousel: a navy evening-routine introduction for the invented Nocturne app. A
 * starry welcome ("Welcome to a quieter evening" under a crescent mark with a halo) hands over to
 * three blue cards on a curved shelf: a guide portrait over numbered plan strips, a miniature phone
 * counting a routine score up to 78, and a hologram figure under a blueprint grid with corner
 * brackets, a scan line and pulsing points. A full-width blue Build my evening plan button sits over
 * the app's tab bar, with its raised Sleep button, and opens a local evening plan. Port of
 * `NocturneProgramCarousel.tsx`.
 *
 * Loop of [NOCTURNE_PROGRAM_CAROUSEL_LOOP] s, every frame a function of one clock:
 *   0.00-0.50  the welcome settles in; sparks drift out 0.2-1.85
 *   1.50-2.25  the welcome gives way to the program (header, cards, button, tab bar)
 *   2.25-4.10  the guide card: plan strips 2.82, portrait 2.25-3.1, bookmark 3.8
 *   2.40-3.05  a sheen crosses the button
 *   4.82-5.50  the second card comes in (measured edge positions), its copy out of a soft focus from 5.0
 *   5.25-8.00  the score digits roll and the ring fills to 78, then the score swells (7.25-8)
 *   8.90-9.55  a second sheen
 *   9.83-10.52 the third card comes in; the scan line passes down 10.5-11.15 and up 12.6-13.25
 *  11.60-12.50 the three points light up and pulse
 *  15.50-16.10 the program fades out; 16.10-17 the loop is back at the welcome
 *
 * A finger anywhere on the tour pauses it; two seconds after the last touch it plays on from where it
 * stopped (reading) or from the anchor of the page that was settled (a swipe or a button). A swipe
 * follows the finger with resistance at the ends and settles on a spring (400 ms, damping ratio
 * 0.8) with the finger's speed: 18% of a card's stride or 450 dp/s turns the page. Without [autoPlay]
 * the tour is the program alone, moved by touch. [previewTime] freezes the clock at that loop time
 * and turns every touch off, to look at any single frame. Under Reduce Motion (animations removed)
 * everything is in place and cards change place without moving.
 *
 * [onStartPlan] is called when the plan opens and [onPageChange] with the page the cards settle on
 * (also once with the first page when the block appears).
 */
@Composable
fun NocturneProgramCarousel(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    previewTime: Double? = null,
    onStartPlan: (() -> Unit)? = null,
    onPageChange: ((Int) -> Unit)? = null,
) {
    val motion = NocturneProgramCarouselMotion
    val anchors = motion.Anchors
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val owner = LocalLifecycleOwner.current
    val reduced = rememberNocturneProgramCarouselReducedMotion()
    val currentOnPageChange by rememberUpdatedState(onPageChange)

    var foreground by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    // Light status bar icons on the dark page (`StatusBar style="light"`).
    val window = LocalActivity.current?.window
    DisposableEffect(window, view) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars ?: false
        controller?.isAppearanceLightStatusBars = false
        onDispose { controller?.isAppearanceLightStatusBars = previous }
    }

    val previewing = previewTime != null
    val timelineDriven = autoPlay || previewing
    val playback = remember { NocturneProgramCarouselPlayback(autoPlay, reduced, foreground, previewTime) }
    SideEffect { playback.update(autoPlay, previewTime, reduced, foreground) }
    LaunchedEffect(playback) {
        while (true) {
            withFrameNanos { nanos -> playback.advance(nanos) }
        }
    }
    val images = remember(context) { loadNocturneProgramCarouselImages(context) }

    var page by remember { mutableIntStateOf(playback.previewPhase?.let { motion.timelinePage(it).roundToInt() } ?: 0) }
    var plan by remember { mutableStateOf(false) }
    var programVisible by remember {
        mutableStateOf(!timelineDriven || reduced || (playback.previewPhase?.let { it >= 2.25 && it < 15.5 } == true))
    }
    var cardMeasurement by remember { mutableStateOf(CardMeasurement("", 0f)) }

    // The program is on screen while the loop is in its 2.25 to 15.5 s, or a finger or a preview holds it.
    LaunchedEffect(playback, timelineDriven, previewing, reduced) {
        snapshotFlow {
            !timelineDriven || (!previewing && playback.touching) || reduced || (playback.time >= 2.25 && playback.time < 15.5)
        }
            .distinctUntilChanged()
            .collect { programVisible = it }
    }
    // The page the cards are on, wherever the position comes from.
    LaunchedEffect(playback) {
        snapshotFlow { playback.position().roundToInt().coerceIn(0, 2) }
            .distinctUntilChanged()
            .collect { next ->
                page = next
                playback.rememberAnchor(anchors[next])
                currentOnPageChange?.invoke(next)
            }
    }

    /** `landed`: the swipe turned the page; settle the clock on its anchor and tick. */
    val landed: (Int) -> Unit = { next ->
        playback.settle(anchors[next])
        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }
    /** `go`: the accessibility actions' way to another page, springing like a swipe from rest. */
    val go: (Int) -> Unit = { next ->
        if (!previewing) {
            val target = next.coerceIn(0, 2)
            playback.pause()
            playback.snapManual(playback.position())
            playback.setTouching(true)
            if (reduced) {
                playback.snapManual(target.toFloat())
            } else {
                playback.springManual(target.toFloat(), 0f)
            }
            landed(target)
        }
    }
    val startPlan: () -> Unit = {
        if (!previewing) {
            playback.stop()
            plan = true
            onStartPlan?.invoke()
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }
    // The `controls` of both buttons: a press holds the tour, letting go settles it on the page.
    val pressIn: () -> Unit = { playback.pause() }
    val pressOut: () -> Unit = { playback.settle(anchors[page]) }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color(0xFF010A16)),
    ) {
        // `useSafeAreaInsets`: the system bars and the display cutout, in dp.
        val insets = WindowInsets.statusBarsIgnoringVisibility
            .union(WindowInsets.navigationBarsIgnoringVisibility)
            .union(WindowInsets.displayCutout)
        val metrics = NocturneProgramCarouselMetrics(
            width = maxWidth.value,
            height = maxHeight.value,
            fontScale = density.fontScale,
            insetTop = insets.getTop(density) / density.density,
            insetBottom = insets.getBottom(density) / density.density,
        )
        val measured = cardMeasurement.key == metrics.layoutKey
        val cardHeight = if (measured) cardMeasurement.height else metrics.artHeight + 250f * max(1f, metrics.fontScale)
        val measureCard: (Float) -> Unit = { height ->
            val next = ceil(height)
            val current = cardMeasurement
            val sameLayout = current.key == metrics.layoutKey
            if (!(sameLayout && current.height >= next)) {
                cardMeasurement = CardMeasurement(metrics.layoutKey, max(if (sameLayout) current.height else 0f, next))
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(PageBrush),
        )
        if (plan && !previewing) {
            NocturneProgramCarouselPlan(
                metrics = metrics,
                reduced = reduced,
                onPressIn = pressIn,
                onPressOut = pressOut,
                onBack = {
                    plan = false
                    playback.setTouching(true)
                    playback.snapManual(page.toFloat())
                    playback.restart(anchors[page])
                },
            )
        } else {
            NocturneProgramCarouselTour(
                playback = playback,
                metrics = metrics,
                images = images,
                reduced = reduced,
                timelineDriven = timelineDriven,
                previewing = previewing,
                programVisible = programVisible,
                page = page,
                cardHeight = cardHeight,
                onCardHeight = measureCard,
                onGo = go,
                onLanded = landed,
                onPressIn = pressIn,
                onPressOut = pressOut,
                onStartPlan = startPlan,
            )
        }
    }
}
