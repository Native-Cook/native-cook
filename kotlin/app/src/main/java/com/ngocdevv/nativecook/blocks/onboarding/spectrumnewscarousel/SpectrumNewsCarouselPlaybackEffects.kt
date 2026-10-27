package com.ngocdevv.nativecook.blocks.onboarding.spectrumnewscarousel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.collectLatest

/**
 * Creates the loop's clock and runs the `useEffect`s of `usePlayback`: the clock advances one frame
 * at a time while [SpectrumNewsCarouselPlayback.running], leaving and returning to the app pauses
 * and resumes it, and Reduce Motion or a preview time park it.
 */
@Composable
internal fun rememberSpectrumNewsCarouselPlayback(autoPlay: Boolean, previewTime: Double?): SpectrumNewsCarouselPlayback {
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    val reduced = rememberSpectrumNewsCarouselReducedMotion().value
    val previewPhase = SpectrumNewsCarouselMotion.previewPhase(previewTime)
    val previewing = previewPhase != null

    val playback = remember {
        SpectrumNewsCarouselPlayback(
            scope = scope,
            initialTime = previewPhase ?: (if (autoPlay && !reduced) 0.0 else SpectrumNewsCarouselAnchors[0]),
            autoPlay = autoPlay,
            foreground = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
            reduced = reduced,
            previewPhase = previewPhase,
        )
    }
    // What the touch handlers and timers read when they fire, kept current after every composition.
    SideEffect {
        playback.autoPlay = autoPlay
        playback.reduced = reduced
        playback.previewPhase = previewPhase
    }

    // `AppState`: leaving the screen pauses the loop, coming back lets it play again.
    DisposableEffect(owner, playback) {
        val observer = LifecycleEventObserver { _, _ ->
            playback.onForeground(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            playback.release()
        }
    }

    // `useFrameCallback`: one frame at a time while the clock runs; every change of that restarts it from the current time.
    LaunchedEffect(playback) {
        snapshotFlow { playback.running() }.collectLatest { running ->
            playback.restartClock()
            if (!running) return@collectLatest
            while (true) {
                withFrameNanos { nanos -> playback.onFrame(nanos) }
            }
        }
    }

    // A frozen frame holds its phase.
    LaunchedEffect(previewPhase) {
        if (previewPhase != null) {
            playback.clearQuietTimer()
            playback.setTime(previewPhase)
        }
    }

    LaunchedEffect(reduced, previewing) {
        if (reduced && !previewing) playback.onReducedMotionOn()
    }

    LaunchedEffect(autoPlay, previewing) {
        playback.onAutoPlayOrPreviewChanged()
    }

    val previousReduced = remember { booleanArrayOf(reduced) }
    LaunchedEffect(reduced) {
        val restoredMotion = previousReduced[0] && !reduced
        previousReduced[0] = reduced
        if (restoredMotion) playback.onReducedMotionOff()
    }
    return playback
}
