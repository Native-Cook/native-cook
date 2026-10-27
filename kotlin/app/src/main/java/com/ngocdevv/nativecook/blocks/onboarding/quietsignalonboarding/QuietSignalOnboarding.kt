package com.ngocdevv.nativecook.blocks.onboarding.quietsignalonboarding

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** `root` background, and the colour the progressive blur fades to below API 31. */
internal val QuietSignalOnboardingBackground = Color(0xFF070709)

/**
 * Quiet Signal Onboarding: a restrained five-page introduction for the invented Quiet Signal on
 * near-black. Each page is a monochrome line diagram drawn from one clock (a busy mind, two waves
 * crossing round a lit profile, a phone carrying sound to both sides, sound rings round a
 * listening head, a circular emblem that draws itself), a white heading, grey body copy and a dark
 * Continue button at the foot. The last Continue opens a local, silent session preview. Port of
 * `QuietSignalOnboarding.tsx`.
 *
 * `autoPlay` loop ([QUIET_SIGNAL_ONBOARDING_LOOP] = 34 s): page 1 from 0 s (anchor 3), page 2 from
 * 9 s (anchor 11), page 3 from 15.25 s (anchor 18), page 4 from 22 s (anchor 25), page 5 from
 * 28.25 s (anchor 31.9: the arc travels 28.3 to 29.5 s, becomes a ring at 29.9 s and the inner
 * line draws 30.5 to 31.8 s); each page begins to leave at 8.15, 14.4, 21.4, 27.5 and 32.5 s and is
 * gone by 8.5, 14.8, 21.8, 27.9 and 33.25 s. The loop begins and ends on an empty screen.
 *
 * Touch: a finger on the page area pauses the loop and it resumes in place two seconds after the
 * last touch; a sideways swipe or the Continue button changes the page (spring of 400 ms, damping
 * ratio 0.8, carrying the finger's speed) and the loop restarts from that page's anchor after the
 * same two seconds. Reduce Motion (animator duration scale 0): the diagrams are still, the pages
 * change without a spring and the loop does not run.
 *
 * @param autoPlay plays the loop by itself.
 * @param previewTime freezes the screen at this loop time, in seconds, and turns touch off; for
 *   looking at one frame.
 * @param onBeginSession called when the last Continue opens the session preview.
 * @param onPageChange called with the page (0 to 4) whenever the nearest page changes.
 */
@Composable
fun QuietSignalOnboarding(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    previewTime: Double? = null,
    onBeginSession: (() -> Unit)? = null,
    onPageChange: ((Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val reducedState = rememberQuietSignalOnboardingReducedMotion()
    val previewPhase = remember(previewTime) { QuietSignalOnboardingTimeline.previewPhase(previewTime) }
    val playback = remember(previewPhase) {
        QuietSignalOnboardingPlayback(scope, reducedState, previewPhase, autoPlay)
    }
    val controller = remember(playback) {
        QuietSignalOnboardingController(scope, playback, timelineDriven = autoPlay || previewPhase != null)
    }
    SideEffect {
        playback.autoPlay = autoPlay
        controller.haptic = haptic
        controller.onPageChange = onPageChange
        controller.onBeginSession = onBeginSession
    }
    val reduced = reducedState.value
    val running = playback.running

    // `<StatusBar style="light" />`: light icons on the dark page, put back when the block closes.
    DisposableEffect(view) {
        val window = (context as? Activity)?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = bars?.isAppearanceLightStatusBars ?: false
        bars?.isAppearanceLightStatusBars = false
        onDispose { bars?.isAppearanceLightStatusBars = previous }
    }

    // `AppState`: the loop pauses in the background and resumes when the screen returns.
    DisposableEffect(owner, playback) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) playback.onForeground(true)
            if (event == Lifecycle.Event.ON_PAUSE) playback.onForeground(false)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    // The clock runs while the loop plays, and the pages follow it rather than a finger.
    LaunchedEffect(running) {
        if (running) {
            controller.manualMode = false
            playback.runClock()
        }
    }
    LaunchedEffect(reduced) { playback.onReducedChanged() }
    LaunchedEffect(autoPlay, playback.previewing) { playback.onAutoPlayChanged() }

    // `useAnimatedReaction`: the page is announced when the nearest page to the position changes.
    LaunchedEffect(controller) {
        snapshotFlow {
            val timeline = QuietSignalOnboardingTimeline
            timeline.clamp(timeline.round(controller.position()), 0.0, timeline.LAST_PAGE.toDouble()).toInt()
        }.collect { controller.announce(it) }
    }

    val density = LocalDensity.current
    // `useSafeAreaInsets`: the system bars and the display cutout, in dp.
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val top = insets.getTop(density) / density.density
    val bottom = insets.getBottom(density) / density.density

    BoxWithConstraints(modifier.fillMaxSize().background(QuietSignalOnboardingBackground)) {
        if (controller.session && !playback.previewing) {
            QuietSignalOnboardingSession(controller, top, bottom, reduced)
        } else {
            QuietSignalOnboardingTour(controller, maxWidth.value, maxHeight.value, top, bottom, reduced)
        }
    }
}
