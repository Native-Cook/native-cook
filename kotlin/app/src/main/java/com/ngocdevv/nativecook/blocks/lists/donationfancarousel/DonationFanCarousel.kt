package com.ngocdevv.nativecook.blocks.lists.donationfancarousel

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.roundToInt

/**
 * A latest-donations screen with a compact photographic fan under its header, ported value for
 * value from the React Native block (`DonationFanCarousel.tsx`, `ReactionMarks.tsx`, `motion.ts`,
 * `theme.ts`). The active donor is a square portrait with a lime keyline; the others recede
 * behind it on both sides. With [autoPlay] a coin and three hearts collect over the portrait while
 * the fan advances three donors and back; a horizontal drag moves the fan by hand.
 *
 * - [autoPlay]: runs the 5366 ms loop from rest; a touch stops it and it starts again from rest
 *   3.5 s after the last touch.
 * - [freezeAt]: holds the screen at that loop time in milliseconds, with touches off.
 * - Reduce Motion (animations turned off in system settings): the first donor stays, fully
 *   legible, and neither the loop nor touches move it.
 */
@Composable
fun DonationFanCarousel(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    freezeAt: Double? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val state = remember { DonationFanCarouselState() }
    val activity = LocalActivity.current
    val view = LocalView.current

    // `<StatusBar style="dark" />`: dark status bar icons on the white screen.
    DisposableEffect(activity, view) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose {
            if (previous != null) controller?.isAppearanceLightStatusBars = previous
        }
    }

    // The `useEffect` of the React Native block.
    LaunchedEffect(state, autoPlay, reducedMotion, freezeAt) {
        state.configure(autoPlay = autoPlay, reducedMotion = reducedMotion, freezeAt = freezeAt)
    }

    // `useFrameCallback`: runs every frame, and only writes state while something moves.
    LaunchedEffect(state, state.frameDriverActive, reducedMotion, freezeAt) {
        if (reducedMotion || freezeAt != null || !state.frameDriverActive) return@LaunchedEffect
        while (state.frameDriverActive) {
            withFrameNanos { frameNanos -> state.tick(frameNanos / 1_000_000.0) }
        }
    }

    // `useWindowDimensions()`: the whole window, drawn under both system bars.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(DonationFanCarouselTheme.Colors.Background)
            .clipToBounds()
            .clearAndSetSemantics {
                val index = state.accessibilityIndex()
                contentDescription = "Donation portraits"
                stateDescription = DonationFanCarouselAccessibility.value(index)
                progressBarRangeInfo = ProgressBarRangeInfo(index.toFloat(), 0f..3f, steps = 2)
                if (freezeAt != null) {
                    disabled()
                } else {
                    setProgress { requested ->
                        state.moveFromAccessibility(requested.roundToInt(), reducedMotion, nowMs = frameTimeMs())
                    }
                }
            },
    ) {
        val geometry = remember(maxWidth, maxHeight) {
            DonationFanCarouselGeometry(maxWidth.value, maxHeight.value)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .donationFanCarouselTouch(
                    state = state,
                    scale = geometry.scale,
                    enabled = freezeAt == null && !reducedMotion,
                ),
        ) {
            DonationFanCarouselStage(state = state, geometry = geometry)
        }
    }
}

private fun frameTimeMs(): Double = System.nanoTime() / 1_000_000.0
