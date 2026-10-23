package com.ngocdevv.nativecook.blocks.cards.grasstimercarousel

import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.abs

@Composable
fun GrassTimerCarousel(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val reducedMotion = rememberReducedMotion()
    val accessibilityManager = remember(context) {
        context.getSystemService(AccessibilityManager::class.java)
    }
    var touchExploration by remember {
        mutableStateOf(accessibilityManager?.isTouchExplorationEnabled == true)
    }
    val quiet = reducedMotion || touchExploration
    val assets = remember(context.applicationContext) {
        GrassTimerCarouselAssets(context.applicationContext)
    }
    val runtime = remember { GrassTimerCarouselRuntime(GrassTimerCarouselController()) }

    DisposableEffect(accessibilityManager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled ->
            touchExploration = enabled
        }
        accessibilityManager?.addTouchExplorationStateChangeListener(listener)
        onDispose { accessibilityManager?.removeTouchExplorationStateChangeListener(listener) }
    }

    DisposableEffect(view) {
        val window = context.findGrassTimerActivity()?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        val oldLightStatus = bars?.isAppearanceLightStatusBars
        val oldLightNavigation = bars?.isAppearanceLightNavigationBars
        bars?.isAppearanceLightStatusBars = false
        bars?.isAppearanceLightNavigationBars = false
        onDispose {
            if (oldLightStatus != null) bars.isAppearanceLightStatusBars = oldLightStatus
            if (oldLightNavigation != null) bars.isAppearanceLightNavigationBars = oldLightNavigation
        }
    }

    DisposableEffect(lifecycleOwner, runtime) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> runtime.scenePhaseChanged(true, nowSeconds())
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    runtime.scenePhaseChanged(false, nowSeconds())
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        runtime.scenePhaseChanged(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
            nowSeconds(),
        )
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            val at = nowSeconds()
            runtime.controller.cancelPress(at)
            runtime.controller.cancelCarousel(at)
        }
    }

    LaunchedEffect(runtime) {
        runtime.runClock {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    LaunchedEffect(runtime, autoPlay, quiet) {
        runtime.configure(autoPlay, quiet, nowSeconds())
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val scale = constraints.maxWidth / 402f
        val textScale = scale / density.density
        fun point(value: Float): Dp = with(density) { (value * scale).toDp() }

        val topInsetPx = WindowInsets.statusBars.getTop(density).toFloat()
        val referenceOriginPx = topInsetPx - 62f * scale
        val referenceOrigin = with(density) { referenceOriginPx.toDp() }
        val minimumContent = referenceOrigin + point(920f) + 72.dp
        val contentHeight = maxOf(maxHeight + 160.dp, minimumContent)

        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .carouselGesture(runtime, scale)
                .semantics {
                    val selected = runtime.selectedCard.intValue
                    stateDescription = if (selected == 0) "Touch Grass timer selected" else "Quiet Day timer selected"
                    customActions = listOf(
                        CustomAccessibilityAction("Show Touch Grass timer") {
                            val changed = runtime.controller.selectCard(0, nowSeconds())
                            if (changed) runtime.wake()
                            changed
                        },
                        CustomAccessibilityAction("Show Quiet Day timer") {
                            val changed = runtime.controller.selectCard(1, nowSeconds())
                            if (changed) runtime.wake()
                            changed
                        },
                    )
                },
        ) {
            Box(Modifier.fillMaxWidth().height(contentHeight)) {
                GrassTimerEditorial(assets, textScale, referenceOrigin, ::point)
                GrassTimerHero(
                    runtime = runtime,
                    assets = assets,
                    scale = scale,
                    textScale = textScale,
                    referenceOriginPx = referenceOriginPx,
                    referenceOrigin = referenceOrigin,
                    point = ::point,
                )
            }
        }

        GrassTimerBottomNavigation(
            textScale = textScale,
            point = ::point,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -point(27f)),
        )
    }
}

private fun Modifier.carouselGesture(
    runtime: GrassTimerCarouselRuntime,
    scale: Float,
): Modifier = pointerInput(runtime, scale) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var translation = Offset.Zero
        var directionChosen = false
        var dragging = false

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            tracker.addPosition(change.uptimeMillis, change.position)
            translation = change.position - down.position
            if (!directionChosen && translation.getDistance() > 8f * scale) {
                directionChosen = true
                if (abs(translation.x) > abs(translation.y)) {
                    dragging = runtime.controller.beginCarousel(nowSeconds())
                }
            }
            if (dragging) {
                runtime.updateCarousel((translation.x / scale).toDouble())
                change.consume()
            }
            if (!change.pressed) {
                if (dragging) {
                    val velocity = tracker.calculateVelocity().x
                    runtime.endCarousel(
                        translation = (translation.x / scale).toDouble(),
                        predictedTranslation = ((translation.x + velocity * 0.20f) / scale).toDouble(),
                    )
                }
                break
            }
        }
    }
}
