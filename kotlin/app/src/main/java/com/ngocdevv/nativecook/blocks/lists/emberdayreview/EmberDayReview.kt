package com.ngocdevv.nativecook.blocks.lists.emberdayreview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.max

/*
 Ember Day Review: a warm journal for the end of a day, ported value for value from the React Native block
 (react-native/src/blocks/lists/ember-day-review/EmberDayReview.tsx). Three glossy four-point stars open in a peach
 glow over a big "Yesterday", a summary card with laurels and a three-row timeline; the day can be lowered to reveal an
 iOS-style Review page behind it, or the page can slide to Today's checklist with big day tabs, round checkboxes, an
 Add Action field and Commit.

 autoPlay (EMBER_DAY_REVIEW_LOOP = 10 s), one clock `time` in 0..10, every frame a function of it:
   hero opens 0.25-0.8 · stars turn 0.48-1.65 (small ones 0.12 and 0.2 behind) · summary 0.85-1.4 · rows 0.72 + 0.18 i
   day drops 4.55-4.95, comes back 6.03-6.35 · page to Today 7-7.5, back 9.25-9.85 · everything folds away 9.6-10.
 A touch (tap, drag, fling) freezes the loop; 2 s after the last one (never while typing) the loop restarts from 0.
 Reduced motion (animator duration scale 0): no loop, the settled screen; page and Review switch without motion.
 previewTime freezes the loop at that second and turns touches off.
*/

/**
 * Ember Day Review on the whole screen. [autoPlay] plays the loop by itself; [previewTime] (seconds, wrapped into the
 * loop) freezes any frame of it and disables touch-driven changes; [onCommit] receives the number of ticked actions
 * when Commit is pressed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmberDayReview(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    previewTime: Double? = null,
    onCommit: ((Int) -> Unit)? = null,
) {
    val reduced = rememberReducedMotion()
    val haptics: HapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val preview = emberDayReviewNormalizedPreview(previewTime)
    val controller = remember { EmberDayReviewController(scope, haptics, autoPlay, preview, reduced) }
    SideEffect { controller.update(autoPlay, preview, reduced, onCommit) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        controller.foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                controller.foreground = true
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                controller.foreground = false
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(autoPlay, controller.automatic, controller.foreground, preview, reduced) {
        controller.syncTime()
    }

    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBarsIgnoringVisibility.getTop(density)
    val cutoutTop = WindowInsets.displayCutout.getTop(density)
    val navBottom = WindowInsets.navigationBarsIgnoringVisibility.getBottom(density)
    val cutoutBottom = WindowInsets.displayCutout.getBottom(density)
    val imeBottom = WindowInsets.ime.getBottom(density)
    val star = rememberEmberDayReviewStar()

    BoxWithConstraints(modifier.fillMaxSize().background(EmberDayReviewPalette.Screen)) {
        // The window shrinks above the keyboard (adjustResize) and the safe area at the foot goes with it.
        val keyboardUp = imeBottom > 0
        val metrics = EmberDayReviewMetrics(
            width = maxWidth,
            height = maxHeight,
            insetTop = with(density) { max(statusTop, cutoutTop).toDp() },
            insetBottom = if (keyboardUp) 0.dp else with(density) { max(navBottom, cutoutBottom).toDp() },
            keyboardLift = with(density) { imeBottom.toDp() },
            fontScale = density.fontScale,
            reduced = reduced,
        )
        // React Native keys the whole screen by the text scale, so a change of it rebuilds the screen (scroll resets).
        key(metrics.fontScale) {
            Box(Modifier.fillMaxSize()) {
                EmberDayReviewReviewPage(controller, metrics)
                EmberDayReviewDaySurface(controller, metrics, star)
            }
        }
    }
}

/** `previewTime` wrapped into `0..LOOP` (negative values count back from the end), or null when it is not a finite number. */
private fun emberDayReviewNormalizedPreview(previewTime: Double?): Float? {
    if (previewTime == null || !previewTime.isFinite()) {
        return null
    }
    val loop = EMBER_DAY_REVIEW_LOOP.toDouble()
    return (((previewTime % loop) + loop) % loop).toFloat()
}
