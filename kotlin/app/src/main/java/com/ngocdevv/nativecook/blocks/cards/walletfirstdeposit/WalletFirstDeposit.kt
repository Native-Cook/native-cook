package com.ngocdevv.nativecook.blocks.cards.walletfirstdeposit

import android.app.Activity
import android.view.HapticFeedbackConstants
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.max

private enum class WalletSemanticPhase { EMPTY, ACTIVATING, FUNDED, RETURNING }

private fun semanticPhaseFor(phase: Float): WalletSemanticPhase = when {
    phase < FILL_AT -> WalletSemanticPhase.EMPTY
    phase < FUNDED_AT -> WalletSemanticPhase.ACTIVATING
    phase < RETURN_AT -> WalletSemanticPhase.FUNDED
    else -> WalletSemanticPhase.RETURNING
}

/**
 * A wallet moving from verification to its first deposit. The backdrop and hero are painted from
 * the 1206-reference canvas while semantic text and actions stay available to TalkBack.
 */
@Composable
fun WalletFirstDeposit(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
) {
    val reducedMotion = rememberReducedMotion()
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val topInset = insets.getTop(density).toFloat()
    val bottomInset = insets.getBottom(density).toFloat()
    val view = LocalView.current
    val accessibility = remember(view) { view.context.getSystemService(AccessibilityManager::class.java) }
    var touchExploration by remember(accessibility) { mutableStateOf(accessibility.isTouchExplorationEnabled) }
    DisposableEffect(accessibility) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { touchExploration = it }
        accessibility.addTouchExplorationStateChangeListener(listener)
        onDispose { accessibility.removeTouchExplorationStateChangeListener(listener) }
    }
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            if (previousStatus != null) controller.isAppearanceLightStatusBars = previousStatus
            if (previousNavigation != null) controller.isAppearanceLightNavigationBars = previousNavigation
        }
    }
    val quiet = reducedMotion || touchExploration
    val motion = remember(quiet) { WalletFirstDepositMotion(if (quiet) FUNDED_AT else 0f, autoPlay && !quiet) }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Page)) {
        val widthPx = with(density) { maxWidth.toPx() }
        val viewportPx = with(density) { maxHeight.toPx() }
        val unit = widthPx / REF_W
        val y0 = topInset - REF_INSET * unit
        val fontScale = (widthPx / density.density) / REF_PT
        val contentHeightPx = max(viewportPx, y0 + CONTENT_BOTTOM * unit + bottomInset + with(density) { 16.dp.toPx() })
        val geo = WalletGeo(unit, y0, fontScale, density.density)
        val scroll = rememberScrollState()

        WalletFirstDepositStage(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll),
            contentHeightPx = contentHeightPx,
            geo = geo,
            motion = motion,
            autoPlay = autoPlay && !quiet,
            reducedMotion = reducedMotion,
            view = view,
        )
    }
}

@Composable
private fun WalletFirstDepositStage(
    modifier: Modifier,
    contentHeightPx: Float,
    geo: WalletGeo,
    motion: WalletFirstDepositMotion,
    autoPlay: Boolean,
    reducedMotion: Boolean,
    view: android.view.View,
) {
    var revision by remember { mutableIntStateOf(0) }
    var wakeGeneration by remember { mutableIntStateOf(0) }
    var semanticPhase by remember(motion) { mutableStateOf(semanticPhaseFor(motion.phase)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollHeight = with(LocalDensity.current) { contentHeightPx.toDp() }

    DisposableEffect(lifecycleOwner, motion) {
        motion.setLifecycleActive(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        wakeGeneration++
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    motion.setLifecycleActive(true)
                    wakeGeneration++
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    motion.setLifecycleActive(false)
                    wakeGeneration++
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(motion, autoPlay, reducedMotion) {
        var previousSemantic = semanticPhaseFor(motion.phase)
        var observedWake = wakeGeneration
        while (true) {
            if (!motion.shouldTick(autoPlay, reducedMotion)) {
                val now = System.nanoTime()
                val wakeDelay = motion.nextWakeDelayNanos(now, autoPlay, reducedMotion)
                val beforeWait = observedWake
                val timedOut = if (wakeDelay != null) {
                    withTimeoutOrNull((wakeDelay + 999_999L) / 1_000_000L) {
                        snapshotFlow { wakeGeneration }.first { it != beforeWait }
                        false
                    } == null
                } else {
                    snapshotFlow { wakeGeneration }.first { it != beforeWait }
                    false
                }
                observedWake = wakeGeneration
                // The frame clock may have been suspended for seconds. Reset its epoch before
                // the next tick so the controller cannot jump across the sleeping interval.
                motion.resetClock()
                if (timedOut) {
                    val wasLanded = motion.landed
                    val changed = motion.tick(System.nanoTime(), autoPlay, reducedMotion)
                    if (!wasLanded && motion.landed) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    if (changed || motion.reducedDirection != 0) revision++
                }
                continue
            }
            withFrameNanos { now ->
                val wasLanded = motion.landed
                val changed = motion.tick(now, autoPlay, reducedMotion)
                if (!wasLanded && motion.landed) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                if (changed || motion.reducedDirection != 0) revision++
                val nextSemantic = semanticPhaseFor(motion.phase)
                if (nextSemantic != previousSemantic) {
                    previousSemantic = nextSemantic
                    semanticPhase = nextSemantic
                }
            }
        }
    }

    // Semantics update at the four meaningful phase boundaries, never on every animation frame.
    val isFunded = semanticPhase == WalletSemanticPhase.FUNDED
    val stateDescription = when {
        semanticPhase == WalletSemanticPhase.EMPTY -> "Verification pending"
        semanticPhase == WalletSemanticPhase.ACTIVATING -> "Completing verification"
        semanticPhase == WalletSemanticPhase.FUNDED -> "Wallet funded"
        else -> "Returning to empty wallet"
    }
    val walletDescription = if (isFunded) {
        "Wallet. Total balance 6,790 dollars and 22 cents. Transactions: Hearth Coffee, purchase 4 dollars and 85 cents. Dieter E., received 109 dollars and 25 cents. Recent payees: Konstantin, Vasya, Aleksa, Joshua. Spent this week 983 dollars."
    } else {
        "Wallet. Total balance zero dollars. Verification pending. No recent payments. Spent this week zero dollars."
    }

    Box(
        modifier = modifier
            .height(scrollHeight)
            .semantics {
                contentDescription = "$walletDescription $stateDescription"
                customActions = buildList {
                    if (semanticPhase == WalletSemanticPhase.EMPTY) {
                        add(CustomAccessibilityAction("Complete verification and preview your first deposit") {
                            val now = System.nanoTime()
                            motion.touchDown(now, 1, reducedMotion)
                            motion.touchUp(now, 1, reducedMotion)
                            wakeGeneration++
                            true
                        })
                    }
                    if (semanticPhase == WalletSemanticPhase.FUNDED) {
                        add(CustomAccessibilityAction("Return to empty wallet") {
                            val now = System.nanoTime()
                            motion.touchDown(now, 2, reducedMotion)
                            motion.touchUp(now, 2, reducedMotion)
                            wakeGeneration++
                            true
                        })
                    }
                }
            }
            .pointerInput(motion, reducedMotion, geo) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val target = targetFor(down.position.x, down.position.y, motion.phase, geo)
                    motion.touchDown(System.nanoTime(), target, reducedMotion)
                    wakeGeneration++
                    val up = waitForUpOrCancellation()
                    if (up != null) {
                        val releaseTarget = targetFor(up.position.x, up.position.y, motion.phase, geo)
                        val now = System.nanoTime()
                        if (releaseTarget == target) motion.touchUp(now, target, reducedMotion) else motion.touchCancel(now, reducedMotion)
                        wakeGeneration++
                    } else {
                        motion.touchCancel(System.nanoTime(), reducedMotion)
                        wakeGeneration++
                    }
                }
            },
    ) {
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                // Keep the reduced-motion fade on the layer; revision is observed here, so the
                // layer updates without recomposing the surrounding stage.
                alpha = motion.reducedOpacity + revision * 0f
            },
        ) {
            // A snapshot read in the draw lambda invalidates this Canvas layer without composing
            // the stage on every frame. The scalar controller remains plain, deterministic state.
            if (revision >= 0) {
                drawWalletBackdrop(motion.frame, geo, motion.bannerPress)
                drawWalletText(motion.frame, geo, motion.bannerPress)
                drawWalletHero(motion.frame, geo, motion.cardPress, motion.bannerPress)
            }
        }
    }
}

private fun targetFor(xPx: Float, yPx: Float, phase: Float, geo: WalletGeo): Int {
    val x = xPx / geo.u
    val y = (yPx - geo.y0) / geo.u
    if (phase < 2.75f || phase >= 9.7f) {
        if (x in Banner.x..(Banner.x + Banner.w) && y in Banner.y..(Banner.y + Banner.h)) return 1
    } else if (phase >= 3.3f && phase < 9f) {
        val centerX = 595.5f
        val centerY = 268.5f
        val halfW = 232f * 0.752f / 2f + 36f
        val halfH = 147f * 0.752f / 2f + 36f
        if (abs(x - centerX) <= halfW && abs(y - centerY) <= halfH) return 2
    }
    return 0
}
