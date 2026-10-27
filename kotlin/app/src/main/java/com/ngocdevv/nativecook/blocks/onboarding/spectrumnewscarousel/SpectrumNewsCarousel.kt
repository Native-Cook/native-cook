package com.ngocdevv.nativecook.blocks.onboarding.spectrumnewscarousel

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlin.math.max
import kotlin.math.min

private val SpectrumNewsCarouselRoot = Color(0xFFFCFBF8)

/** `interpolate(rect.width, [.9, 1], [28, 0])`: the window's corners open out to the screen's own as it fills it. */
private val SpectrumNewsCarouselCornerWidths = doubleArrayOf(0.9, 1.0)
private val SpectrumNewsCarouselCornerRadii = doubleArrayOf(28.0, 0.0)

/**
 * Spectrum News Carousel: a warm welcome for the invented Spectrum news app that grows from a rounded
 * window to the full screen and becomes a five-page introduction over a continuous field of seeded
 * particles: fictional source badges around a blue news orb, a person choosing topics, a layered
 * summary with count and audio chips, and a photograph beside a question card. A black Continue with
 * Apple button and underlined Terms and Privacy links stay at the foot; Continue opens a daily briefing.
 * Port of `SpectrumNewsCarousel.tsx`.
 *
 * Loop of [SPECTRUM_NEWS_CAROUSEL_LOOP] s, every frame a function of one clock:
 *   0.00-1.75  the welcome grows from a window (22% by 13% of the screen) to the whole screen
 *   0.60-0.75  the warm field fades in; the welcome page itself fades in over 0.5 s
 *   6.45, 8.70, 11.12, 12.83  the four forward swipes, each 0.38 s on the reference's finger positions
 *   15.40-20.40  the four swipes back, with their slow finger-drag preludes
 *   21.40-22.00  the window closes again and everything fades, back at rest at the start
 *
 * A swipe settles on a spring (400 ms, damping ratio 0.8) with the finger's speed, and a touch pauses
 * the tour; two seconds after the last touch it plays again from the page that rests. [previewTime]
 * freezes the clock at that loop time and turns every touch off, to look at any single frame.
 * Under Reduce Motion (animations removed) the pages change in place and the particles stand still.
 *
 * @param autoPlay Plays the tour by itself, every [SPECTRUM_NEWS_CAROUSEL_LOOP] seconds.
 * @param previewTime Loop time in seconds to freeze the clock at.
 * @param onOpenBriefing Called when Continue with Apple opens the briefing.
 * @param onPageChange Called with the page's index (0 to 4) when the page nearest the position changes.
 */
@Composable
fun SpectrumNewsCarousel(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    previewTime: Double? = null,
    onOpenBriefing: (() -> Unit)? = null,
    onPageChange: ((page: Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val uiDensity = LocalDensity.current
    val scope = rememberCoroutineScope()
    val autoPlayState = rememberUpdatedState(autoPlay)
    val currentOpenBriefing by rememberUpdatedState(onOpenBriefing)
    val currentPageChange by rememberUpdatedState(onPageChange)

    val playback = rememberSpectrumNewsCarouselPlayback(autoPlay, previewTime)
    val previewing = playback.previewing
    val reduced = playback.reduced
    val timelineDriven = autoPlay || previewing
    val frozenPhase = playback.previewPhase

    val drive = remember {
        SpectrumNewsCarouselDrive(
            playback = playback,
            scope = scope,
            autoPlayState = autoPlayState,
            initialManualMode = !timelineDriven,
            selectionHaptic = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) },
        )
    }
    val photo = remember(context) { loadSpectrumNewsCarouselPhoto(context) }

    var page by remember {
        mutableIntStateOf(if (frozenPhase == null) 0 else SpectrumNewsCarouselMotion.jsRound(SpectrumNewsCarouselMotion.timelinePage(frozenPhase)).toInt())
    }
    var briefing by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    // The footer takes no touch until the welcome has opened (1.5 s) and again while it closes (21.4 s).
    var introReady by remember {
        mutableStateOf(!timelineDriven || reduced || (frozenPhase != null && frozenPhase >= 1.5 && frozenPhase < 21.4))
    }

    // `StatusBar style="dark"`: dark icons on the pale screen.
    val window = LocalActivity.current?.window
    DisposableEffect(window, view) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars ?: false
        controller?.isAppearanceLightStatusBars = true
        onDispose { controller?.isAppearanceLightStatusBars = previous }
    }

    // `if (playing) manualMode.set(false)`: once the loop plays again the timeline takes the position back.
    LaunchedEffect(playback, drive) {
        snapshotFlow { playback.running() }.collect { running ->
            if (running) drive.manualMode = false
        }
    }
    // `useAnimatedReaction(... announcePage)`: the page nearest the position, whoever moves it.
    LaunchedEffect(drive) {
        snapshotFlow { drive.nearestPage() }.collect { next ->
            page = next
            playback.rememberAnchor(SpectrumNewsCarouselAnchors[next])
            currentPageChange?.invoke(next)
        }
    }
    // `useAnimatedReaction(... setIntroReady)`.
    LaunchedEffect(drive) {
        snapshotFlow {
            !drive.timelineDriven || drive.reduced || drive.renderManual || (drive.time >= 1.5 && drive.time < 21.4)
        }.collect { ready -> introReady = ready }
    }

    val pressIn: () -> Unit = { playback.pause() }
    val pressOut: () -> Unit = { playback.settle(SpectrumNewsCarouselAnchors[page]) }

    BoxWithConstraints(modifier.fillMaxSize().background(SpectrumNewsCarouselRoot)) {
        val width = maxWidth.value
        val height = maxHeight.value
        // `useSafeAreaInsets`: the system bars and the display cutout, in dp.
        val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        val safeTop = insets.getTop(uiDensity) / uiDensity.density
        val safeBottom = insets.getBottom(uiDensity) / uiDensity.density
        val largeText = uiDensity.fontScale > 1.35f
        // The artwork's scale: as wide as the screen allows, shrunk on short screens, never past 1.08.
        val boardScale = min(min((width - 64f) / 300f, max(0.7f, (height - safeTop - safeBottom - 365f) / 285f)), 1.08f)
        SideEffect { drive.width = width.toDouble() }

        if (briefing && !previewing) {
            SpectrumNewsCarouselBriefing(
                photo = photo,
                saved = saved,
                reduced = reduced,
                safeTop = safeTop,
                safeBottom = safeBottom,
                onPressIn = pressIn,
                onPressOut = pressOut,
                onToggleSaved = { saved = !saved },
                onBack = {
                    briefing = false
                    drive.showPage(page)
                    playback.restart(SpectrumNewsCarouselAnchors[page])
                },
            )
        } else {
            val scroll = rememberScrollState()
            val pixelsPerDp = uiDensity.density
            // `onScrollBeginDrag` and `onMomentumScrollBegin` pause the tour; their ends let it go.
            LaunchedEffect(scroll, playback) {
                var scrolling = false
                snapshotFlow { scroll.isScrollInProgress }.collect { active ->
                    if (active != scrolling) {
                        scrolling = active
                        if (active) playback.scrollBegan() else playback.scrollEnded()
                    }
                }
            }
            // The `MaskedView`: everything is clipped to the window that grows from the welcome's mark.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val frame = drive.welcomeFrame()
                        val left = (frame.x * size.width).toFloat()
                        val top = (frame.y * size.height).toFloat()
                        val radius = SpectrumNewsCarouselMotion.interpolate(
                            frame.width,
                            SpectrumNewsCarouselCornerWidths,
                            SpectrumNewsCarouselCornerRadii,
                        )
                        clip = true
                        shape = SpectrumNewsCarouselWindow(
                            left = left,
                            top = top,
                            right = left + (frame.width * size.width).toFloat(),
                            bottom = top + (frame.height * size.height).toFloat(),
                            radius = (radius * this.density).toFloat(),
                        )
                    },
            ) {
                SpectrumNewsCarouselBackdrop(drive)
                SpectrumNewsCarouselParticles(drive, width, Modifier.offset(y = safeTop.dp))
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(top = (safeTop + 10f).dp, bottom = (safeBottom + 4f).dp),
                ) {
                    SpectrumNewsCarouselBlurViewport(
                        overflowing = { scroll.maxValue > 4f * pixelsPerDp },
                        backgroundColor = SpectrumNewsCarouselRoot,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            // `readingHandlers`: a finger on the page area pauses the tour and, lifted, lets it go.
                            .pointerInput(playback) {
                                awaitPointerEventScope {
                                    var down = 0
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val now = event.changes.count { it.pressed }
                                        if (now > down) {
                                            playback.touchStart(now)
                                        } else if (now < down) {
                                            playback.touchEnd(now)
                                        }
                                        down = now
                                    }
                                }
                            },
                    ) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(scroll)
                                .padding(
                                    top = max(0f, (height - safeTop - safeBottom - 680f) * 0.18f).dp,
                                    bottom = (if (largeText) 16f else 0f).dp,
                                ),
                        ) {
                            SpectrumNewsCarouselScene(page, width, drive, boardScale, photo)
                        }
                    }
                    SpectrumNewsCarouselFooter(
                        page = page,
                        interactive = introReady,
                        reduced = reduced,
                        onPressIn = pressIn,
                        onPressOut = pressOut,
                        onGo = { drive.go(it) },
                        onContinue = {
                            // `openBriefing`: stop the tour for good, open the briefing, a success tap.
                            if (!playback.previewing) {
                                playback.stop()
                                briefing = true
                                currentOpenBriefing?.invoke()
                                view.performHapticFeedback(
                                    if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
