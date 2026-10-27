package com.ngocdevv.nativecook.blocks.modals.confettigiftoffer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/**
 * Confetti Gift Offer: a paywall story with an original raccoon mascot. "We have a gift!" over a
 * checked box that rocks, squats and kicks its lid; the box gives way to a "-70%" ticket in a burst
 * of capsule confetti; closing it raises an "Are you sure to skip -70% offer?" sheet; either way the
 * story ends on "Now let's create account". Prices are illustrative, nothing is charged.
 *
 * Ported value for value from ConfettiGiftOffer.tsx. [autoPlay] runs a 9.5 s loop
 * ([CONFETTI_GIFT_OFFER_LOOP]) in which every frame is a function of the loop time:
 *
 *  - 0 to 0.583 s: the gift rocks (from 0.333 s), squats and kicks its lid; Open now waits.
 *  - 0.583 to 1.1 s: the ticket grows from 64% and fades in on measured luminance; the confetti
 *    starts at 0.583 s and the countdown at 59:59 once it has settled.
 *  - 3.167 to 3.433 s: the skip sheet rises over a light scrim (measured positions).
 *  - 4.05 s: a second wave of confetti. 4.5 to 4.9 s: the account screen pushes in from the right
 *    carrying the offer and the sheet out.
 *  - 8.4 to 8.75 s: the account screen fades; the gift is back by 9.1 s.
 *
 * A touch takes over; the loop resumes 2 s after the last touch from the gift or the offer.
 * [previewTime] pins the loop at that second (and switches touch off), for still frames.
 * Under Reduce Motion (animator duration scale 0) the loop never runs: the settled offer screen
 * shows, with no travel and no confetti.
 *
 * [onContinue] reports the chosen sign-in and whether the offer was claimed. [onDismiss] exists
 * in the React Native props but nothing there calls it either.
 */
@Composable
fun ConfettiGiftOffer(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    previewTime: Double? = null,
    onContinue: ((provider: ConfettiGiftOfferProvider, offerAccepted: Boolean) -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") onDismiss: (() -> Unit)? = null,
) {
    val reduced = rememberConfettiGiftOfferReducedMotion()
    val foreground = rememberConfettiGiftOfferForeground()
    val assets = rememberConfettiGiftOfferAssets()
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val autoPlayState = rememberUpdatedState(autoPlay)
    val reducedState = rememberUpdatedState(reduced)
    val foregroundState = rememberUpdatedState(foreground)
    val onContinueState = rememberUpdatedState(onContinue)
    val initialReduced = remember { reduced }
    val controller = remember(previewTime) {
        ConfettiGiftOfferController(
            scope = scope,
            previewTime = previewTime,
            initialReduced = initialReduced,
            autoPlay = { autoPlayState.value },
            reduced = { reducedState.value },
            foreground = { foregroundState.value },
            lightHaptic = { haptics.performHapticFeedback(HapticFeedbackType.ContextClick) },
            successHaptic = { haptics.performHapticFeedback(HapticFeedbackType.Confirm) },
            onContinue = { provider, accepted -> onContinueState.value?.invoke(provider, accepted) },
        )
    }
    val stage by controller.stageState
    val manual = controller.manual

    ConfettiGiftOfferStatusBar()

    // The scene clock (useSceneClock): runs while nothing owns the story.
    val running = autoPlay && !manual
    LaunchedEffect(controller, running, foreground, reduced) {
        controller.runClock(running, foreground, reduced)
    }
    // A manually owned stage runs its own transitions (ConfettiGiftOffer.tsx:163).
    DisposableEffect(controller, foreground, manual, stage, reduced) {
        val cleanup = controller.applyStage(foreground, manual, stage, reduced)
        onDispose(cleanup)
    }
    var previousForeground by remember { mutableStateOf(foreground) }
    LaunchedEffect(foreground) {
        if (foreground && !previousForeground && manual) controller.foregroundReturned()
        previousForeground = foreground
    }

    val density = LocalDensity.current
    val insetTop = rememberConfettiGiftOfferInsetTop()
    val insetBottom = rememberConfettiGiftOfferInsetBottom()
    val ownership = remember(controller) { ConfettiGiftOfferPressOwnership(controller::pressIn, controller::pressOut) }
    val giftScroll = rememberScrollState()
    val offerScroll = rememberScrollState()
    val signupScroll = rememberScrollState()
    val finishScroll = rememberScrollState()
    val touch = controller.touchEnabled

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFFE7EDFE))) {
        val metrics = ConfettiGiftOfferMetrics(maxWidth.value, maxHeight.value, density.fontScale, insetTop, insetBottom)

        // The white behind the ticket.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = controller.washOpacity()
                    translationX = if (reduced) 0f else -metrics.width * controller.pushProgress() * density.density
                }
                .background(Color.White),
        )
        // The gift screen.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = controller.giftOpacity() }
                .hiddenUnless(stage == ConfettiGiftOfferStage.Gift),
        ) {
            ConfettiGiftOfferGiftScene(controller, metrics, assets, reduced, ownership, stage == ConfettiGiftOfferStage.Gift, giftScroll)
        }
        // The offer screen: scales up from 64%, slides out to the left with the push.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = controller.offerOpacity()
                    translationX = if (reduced) 0f else -metrics.width * controller.pushProgress() * density.density
                    val scale = if (reduced) 1f else .64f + .36f * controller.offerReveal()
                    scaleX = scale
                    scaleY = scale
                }
                .hiddenUnless(stage == ConfettiGiftOfferStage.Offer),
        ) {
            ConfettiGiftOfferOfferScene(controller, metrics, assets, reduced, ownership, stage == ConfettiGiftOfferStage.Offer, offerScroll)
        }
        // The account screen: pushes in from the right.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = controller.signupOpacity()
                    translationX = if (reduced) 0f else metrics.width * (1f - controller.pushProgress()) * density.density
                }
                .hiddenUnless(stage == ConfettiGiftOfferStage.Signup),
        ) {
            ConfettiGiftOfferSignupScene(controller, metrics, assets, reduced, ownership, stage == ConfettiGiftOfferStage.Signup, signupScroll)
        }
        if (stage == ConfettiGiftOfferStage.Complete || stage == ConfettiGiftOfferStage.Dismissed) {
            ConfettiGiftOfferFinishScene(
                controller, metrics, assets, reduced, ownership, stage == ConfettiGiftOfferStage.Complete, finishScroll,
            )
        }
        ConfettiGiftOfferConfetti(
            age = controller::confettiAge,
            push = controller::pushProgress,
            reduced = reduced,
            width = metrics.width,
            height = metrics.height,
        )
        // The close chip rides with the offer screen's scale and slide (ConfettiGiftOffer.tsx:253).
        Box(
            Modifier
                .offset(18.dp, (if (metrics.largeText) metrics.insetTop + 8f else 62f * metrics.u).dp)
                .graphicsLayer {
                    alpha = controller.offerOpacity()
                    translationX = if (reduced) 0f else -metrics.width * controller.pushProgress() * density.density
                    val scale = if (reduced) 1f else .64f + .36f * controller.offerReveal()
                    scaleX = scale
                    scaleY = scale
                }
                .hiddenUnless(stage == ConfettiGiftOfferStage.Offer),
        ) {
            ConfettiGiftOfferPressScale(
                enabled = stage == ConfettiGiftOfferStage.Offer && touch,
                reduced = reduced,
                ownership = ownership,
                onPress = { controller.go(ConfettiGiftOfferStage.Exit) },
                label = "Leave the offer",
            ) {
                Box(
                    Modifier.size(44.dp).background(Color(0xFFF3F3F3), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { ConfettiGiftOfferCloseIcon() }
            }
        }
        ConfettiGiftOfferExitSheet(
            controller = controller,
            metrics = metrics,
            assets = assets,
            visible = stage == ConfettiGiftOfferStage.Exit || controller.manualExitVisible,
            foreground = foreground,
            reduced = reduced,
            ownership = ownership,
        )
    }
}

/** A layer that is not on show is hidden from screen readers (`accessibilityElementsHidden`). */
private fun Modifier.hiddenUnless(shown: Boolean): Modifier =
    if (shown) this else this.clearAndSetSemantics { }
