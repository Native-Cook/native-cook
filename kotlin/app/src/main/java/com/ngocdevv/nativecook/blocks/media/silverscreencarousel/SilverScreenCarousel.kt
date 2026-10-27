package com.ngocdevv.nativecook.blocks.media.silverscreencarousel

import android.content.Context
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/** `LOOP`: the length of one `autoPlay` loop, in seconds (SilverScreenCarousel.tsx:18). */
const val SILVER_SCREEN_CAROUSEL_LOOP = 29

/**
 * Silver Screen Carousel: an editorial black welcome for an invented film-profile camera app.
 * "Aesthetic shots without editing" in Source Serif 4 over grey copy, and a row of tall cards, each an
 * original photograph with a two-letter profile code, its name and description and a colour bar,
 * drifting across the screen. Step dots and a round arrow sit at the foot; the arrow applies the profile
 * in the gate. Port of `SilverScreenCarousel.tsx`.
 *
 * Loop of [SILVER_SCREEN_CAROUSEL_LOOP] s, every frame a function of one clock:
 *   0 - 27.983   the row drifts left at a constant 25.1675 px a second (9.457 s a card), no hold or easing
 *   27.983 - 28.35  the row fades out where it is; at 28.35 it is back at the first card, still hidden
 *   28.35 - 28.7   it fades in; it rests until 29, which is the same picture as 0
 *
 * A drag moves the row 1:1 (rubber-banding past the ends) and settles on a spring (400 ms, damping ratio
 * 0.8) with the finger's speed and a selection haptic; two seconds after the last touch the row springs
 * home and the loop starts again. Each photograph moves 22 dp slower than its frame and frames off the
 * gate are a step darker. Applying fires one shutter flash (420 ms) over the photograph in the gate with
 * a success haptic, and the arrow becomes a check. Under Reduce Motion (animations removed) the row
 * does not drift and changes in place.
 *
 * [previewTime] freezes the clock at that loop time and turns every touch off, to look at any single
 * frame. [onSelect] and [onApply] get the id of the profile the user chose or applied.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SilverScreenCarousel(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    previewTime: Double? = null,
    onSelect: ((String) -> Unit)? = null,
    onApply: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = LocalActivity.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val fontScale = density.fontScale

    // `AppState` and `useMotionPreference`: whether the screen is in the foreground and whether
    // animations are turned off, checked again whenever the lifecycle moves.
    var foreground by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var reduced by remember { mutableStateOf(animationsRemoved(context)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            reduced = animationsRemoved(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    // `<StatusBar style="light" />`: light status bar icons on the black screen.
    DisposableEffect(activity, view) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars ?: false
        controller?.isAppearanceLightStatusBars = false
        onDispose { controller?.isAppearanceLightStatusBars = previous }
    }

    val motion = remember(scope) {
        val start = if (previewTime != null) silverScreenCarouselNormalize(previewTime, SILVER_SCREEN_CAROUSEL_LOOP.toDouble()) else 0.0
        SilverScreenCarouselMotion(scope, start)
    }
    // The flags and callbacks the motion reads when a touch or a timer fires, kept current after every composition.
    SideEffect {
        motion.reduced = reduced
        motion.onSelect = onSelect
        motion.onApply = onApply
        motion.selectionHaptic = { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick) }
        motion.successHaptic = { haptic.performHapticFeedback(HapticFeedbackType.Confirm) }
    }
    val touchEnabled = previewTime == null

    // `useSceneClock`: one clock. It runs only while the loop may play by itself (`autoPlay`, no touch has taken
    // over, nothing applied), the screen is in the foreground and motion is allowed. A pause keeps the phase;
    // Reduce Motion rests the clock at 0; a preview time freezes it.
    val running = autoPlay && !motion.manual && motion.applied == null
    LaunchedEffect(running, foreground, reduced, previewTime) {
        if (previewTime != null) {
            motion.time = silverScreenCarouselNormalize(previewTime, SILVER_SCREEN_CAROUSEL_LOOP.toDouble())
            return@LaunchedEffect
        }
        if (reduced) {
            motion.time = 0.0
            return@LaunchedEffect
        }
        if (!running || !foreground) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (last != 0L) motion.advance((nanos - last) / 1_000_000_000.0)
                last = nanos
            }
        }
    }
    // `useAnimatedReaction`: the profile in the gate follows the row's position, whoever moves it.
    LaunchedEffect(motion) {
        snapshotFlow { silverScreenCarouselIndexAt(motion.position()) }.collect { next ->
            motion.selected = next
        }
    }
    // Two quiet seconds after a touch, the row springs home and the loop restarts (lines 79-85).
    val waiting = motion.waiting
    val manual = motion.manual
    val applied = motion.applied
    val quietGeneration = motion.quietGeneration
    LaunchedEffect(waiting, manual, applied, quietGeneration, autoPlay, reduced, foreground, previewTime) {
        if (previewTime != null || !waiting || !manual || !autoPlay || reduced || applied != null || !foreground) {
            return@LaunchedEffect
        }
        delay(SILVER_SCREEN_CAROUSEL_QUIET_MS)
        motion.springHome()
    }
    // A spring in flight stops when the app leaves the foreground (line 89).
    DisposableEffect(foreground) {
        if (!foreground) motion.cancelSpring()
        onDispose { motion.cancelSpring() }
    }

    val serif = remember(context) { loadSilverScreenCarouselSerif(context) }
    val photos by produceState<List<ImageBitmap?>>(
        initialValue = List(SilverScreenCarouselProfiles.size) { null },
        key1 = context,
    ) {
        value = withContext(Dispatchers.IO) {
            SilverScreenCarouselProfiles.map { loadSilverScreenCarouselPhoto(context, it.photo) }
        }
    }
    val scroll = rememberScrollState()
    // `onScrollBeginDrag` / `onMomentumScrollBegin` pause the row, the end of the scroll starts the quiet period.
    LaunchedEffect(scroll, touchEnabled) {
        if (!touchEnabled) return@LaunchedEffect
        var scrolled = false
        snapshotFlow { scroll.isScrollInProgress }.collect { scrolling ->
            if (scrolling) {
                scrolled = true
                motion.pauseReading()
            } else if (scrolled) {
                motion.armResume()
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds(),
    ) {
        // `useWindowDimensions` and `useSafeAreaInsets`, in dp.
        val width = maxWidth.value
        val height = maxHeight.value
        val insetTop = max(
            WindowInsets.statusBarsIgnoringVisibility.getTop(density),
            WindowInsets.displayCutout.getTop(density),
        ) / density.density
        val insetBottom = WindowInsets.navigationBarsIgnoringVisibility.getBottom(density) / density.density

        // Lines 60-68: the reference screen is 402 x 874 pt; large text or a narrow window falls back to a
        // plain flow layout.
        val referenceLayout = fontScale <= 1.3f && width >= 350f
        val unit = height / 874f
        val cardWidth = min(380f, width * 342f / 402f)
        val gap = width * 10f / 402f
        val step = cardWidth + gap
        val cardHeight = cardWidth * 364f / 342f
        val headingTop = 191f * unit
        val cardTop = 353f * unit
        val headingMargin = if (referenceLayout) max(24f, headingTop - insetTop - 16f) else 27f
        val cardMargin = if (referenceLayout) max(18f, cardTop - headingTop - 116f * fontScale) else 24f
        val footerMargin = if (referenceLayout) max(18f, 795f * unit - cardTop - cardHeight - 29f) else 16f
        SideEffect { motion.stepDp = step }

        SilverScreenCarouselBlurViewport(
            overflowing = { scroll.maxValue != Int.MAX_VALUE && scroll.maxValue > 4f * density.density },
            fade = Color.Black,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = height.dp)
                        .padding(top = (insetTop + 16f).dp, bottom = (insetBottom + 16f).dp),
                ) {
                    SilverScreenCarouselHeading(serif = serif, marginTop = headingMargin, marginBottom = cardMargin)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = cardHeight.dp)
                            .clipToBounds()
                            .silverScreenCarouselPan(motion, touchEnabled)
                            .semantics {
                                contentDescription = "Film profile: ${SilverScreenCarouselProfiles[motion.selected].name}"
                                stateDescription = "${motion.selected + 1} of 4"
                                customActions = listOf(
                                    CustomAccessibilityAction("Next profile") {
                                        if (touchEnabled) motion.select(min(3, max(0, motion.selected + 1)))
                                        touchEnabled
                                    },
                                    CustomAccessibilityAction("Previous profile") {
                                        if (touchEnabled) motion.select(min(3, max(0, motion.selected - 1)))
                                        touchEnabled
                                    },
                                )
                            },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        SilverScreenCarouselTrack(
                            cardWidth = cardWidth.dp,
                            gap = gap.dp,
                            startPadding = ((width - cardWidth) / 2f).dp,
                            modifier = Modifier.graphicsLayer {
                                translationX = (-motion.position() * step * this.density).toFloat()
                                alpha = motion.trackAlpha(reduced)
                            },
                        ) {
                            for (index in SilverScreenCarouselProfiles.indices) {
                                SilverScreenCarouselCard(
                                    index = index,
                                    cardWidth = cardWidth,
                                    photo = photos[index],
                                    motion = motion,
                                    active = motion.selected == index,
                                    reduced = reduced,
                                )
                            }
                        }
                    }
                    SilverScreenCarouselFooter(
                        motion = motion,
                        referenceLayout = referenceLayout,
                        reduced = reduced,
                        enabled = touchEnabled,
                        marginTop = footerMargin,
                    )
                }
            }
        }
    }
}

/** True when the user turned animations off in system settings ("Remove animations"). */
private fun animationsRemoved(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
