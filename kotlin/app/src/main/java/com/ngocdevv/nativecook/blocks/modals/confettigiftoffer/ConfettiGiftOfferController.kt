package com.ngocdevv.nativecook.blocks.modals.confettigiftoffer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * The story's state, line for line the logic of ConfettiGiftOffer.tsx.
 *
 * While nothing has been touched ([manual] is false) every frame is a function of the loop time
 * [time]. A touch calls [own], which snapshots what the loop shows into the manual values and from
 * then on those drive the screen until the loop is allowed to resume ([armQuiet], two seconds after
 * the last touch on the gift or the offer).
 */
internal class ConfettiGiftOfferController(
    val scope: CoroutineScope,
    val previewTime: Double?,
    initialReduced: Boolean,
    private val autoPlay: () -> Boolean,
    private val reduced: () -> Boolean,
    private val foreground: () -> Boolean,
    private val lightHaptic: () -> Unit,
    private val successHaptic: () -> Unit,
    private val onContinue: (ConfettiGiftOfferProvider, Boolean) -> Unit,
) {
    /** Where the clock starts: a pinned preview time, the settled offer under Reduce Motion, else the loop's start. */
    val initialTime: Double = when {
        previewTime != null -> confettiGiftOfferNormalize(previewTime, CONFETTI_GIFT_OFFER_LOOP)
        initialReduced -> CONFETTI_GIFT_OFFER_SETTLED
        else -> 0.0
    }

    /** Loop time in seconds: the one clock every automatic frame is derived from. */
    var time: Double by mutableDoubleStateOf(initialTime)

    /** True once a touch owns the screen (`manualSV` / `manual`). */
    var manual: Boolean by mutableStateOf(false)
        private set
    private var manualStage: ConfettiGiftOfferStage by mutableStateOf(ConfettiGiftOfferStage.Gift)
    var accepted: Boolean by mutableStateOf(false)
        private set
    var manualExitVisible: Boolean by mutableStateOf(false)
        private set

    /** Touch is switched off while a still frame is pinned with `previewTime`. */
    val touchEnabled: Boolean = previewTime == null

    val giftAlpha = ConfettiGiftOfferValue(1f)
    val offerAlpha = ConfettiGiftOfferValue(0f)
    val signupAlpha = ConfettiGiftOfferValue(0f)
    val lidOpen = ConfettiGiftOfferValue(0f)
    val burst = ConfettiGiftOfferValue(-1f)
    val openingTime = ConfettiGiftOfferValue(0f)
    val manualPush = ConfettiGiftOfferValue(0f)
    private val values = listOf(giftAlpha, offerAlpha, signupAlpha, lidOpen, burst, openingTime, manualPush)

    /** The screen on show: the loop's while automatic, the touch's once owned. Changes only at stage borders. */
    val stageState = derivedStateOf { if (manual) manualStage else confettiGiftOfferStageAt(time) }
    val stage: ConfettiGiftOfferStage get() = stageState.value

    private var anchorMs = -1.0
    private var quiet: Job? = null
    private var heldControls = 0
    private var readingDragging = false
    private var readingMomentum = false
    private var ownedStage: ConfettiGiftOfferStage? = null

    // ---- clock (useSceneClock, motion.ts:21) ----

    /** Advances [time] each frame while [running]; returns at once when the clock must not run. */
    suspend fun runClock(running: Boolean, foreground: Boolean, reduced: Boolean) {
        anchorMs = -1.0
        if (previewTime != null) {
            time = confettiGiftOfferNormalize(previewTime, CONFETTI_GIFT_OFFER_LOOP)
        } else if (reduced) {
            time = CONFETTI_GIFT_OFFER_SETTLED
        }
        if (!(running && foreground && !reduced && previewTime == null)) return
        while (true) {
            withFrameNanos { nanos ->
                // Touch ownership freezes the clock at once, before the next composition.
                if (manual) {
                    anchorMs = -1.0
                    return@withFrameNanos
                }
                val ms = nanos / 1_000_000.0
                if (anchorMs < 0) anchorMs = ms - time * 1000.0
                time = ((ms - anchorMs) / 1000.0) % CONFETTI_GIFT_OFFER_LOOP
            }
        }
    }

    // ---- ownership and the quiet timer ----

    private fun clearQuiet() {
        quiet?.cancel()
        quiet = null
    }

    /** Starts the two-second wait after which the loop takes over again (ConfettiGiftOffer.tsx:142). */
    fun armQuiet(next: ConfettiGiftOfferStage) {
        clearQuiet()
        val resumable = next == ConfettiGiftOfferStage.Gift || next == ConfettiGiftOfferStage.Offer
        if (heldControls > 0 || readingDragging || readingMomentum || !autoPlay() || reduced() || !foreground() || !resumable) return
        quiet = scope.launch {
            delay(2000)
            resumeFrom(next)
        }
    }

    private fun resumeFrom(next: ConfettiGiftOfferStage) {
        if (next != ConfettiGiftOfferStage.Gift && next != ConfettiGiftOfferStage.Offer) return
        time = if (next == ConfettiGiftOfferStage.Gift) 0.0 else CONFETTI_GIFT_OFFER_SETTLED
        anchorMs = -1.0
        ownedStage = null
        manual = false
    }

    /** A touch takes the screen: freeze what the loop shows into the manual values (ConfettiGiftOffer.tsx:143). */
    fun own(next: ConfettiGiftOfferStage) {
        clearQuiet()
        if (!manual) {
            val t = time
            giftAlpha.set(confettiGiftOfferSourceGiftAlpha(t).toFloat())
            offerAlpha.set(confettiGiftOfferSourceOfferAlpha(t).toFloat())
            signupAlpha.set(confettiGiftOfferSourceSignupAlpha(t).toFloat())
            manualPush.set(confettiGiftOfferSourcePush(t).toFloat())
        }
        ownedStage = next
        manual = true
        manualStage = next
    }

    fun pressIn() {
        heldControls += 1
        clearQuiet()
        // Existing manual opening/push animations retain ownership and keep running.
        if (!manual) own(stage)
    }

    fun pressOut() {
        if (heldControls == 0) return
        heldControls -= 1
        if (heldControls == 0) armQuiet(ownedStage ?: confettiGiftOfferStageAt(time))
    }

    fun go(next: ConfettiGiftOfferStage) {
        own(next)
        if (next == ConfettiGiftOfferStage.Gift || next == ConfettiGiftOfferStage.Offer) {
            manualExitVisible = false
            if (reduced() || next == ConfettiGiftOfferStage.Gift) {
                manualPush.set(0f)
            } else {
                manualPush.timing(scope, 0f, 220.0, ConfettiGiftOfferEaseOut)
            }
        }
        armQuiet(next)
    }

    private fun finishOpening() {
        giftAlpha.set(0f)
        offerAlpha.set(1f)
        burst.set(if (reduced()) -1f else .52f)
        if (!reduced()) burst.timing(scope, 1.7f, 1180.0, LinearEasing)
        ownedStage = ConfettiGiftOfferStage.Offer
        manualStage = ConfettiGiftOfferStage.Offer
        armQuiet(ConfettiGiftOfferStage.Offer)
    }

    private fun finishPush() {
        manualExitVisible = false
    }

    /** The effect that runs a manually owned stage's transitions (ConfettiGiftOffer.tsx:163). Returns its cleanup. */
    fun applyStage(foreground: Boolean, manual: Boolean, stage: ConfettiGiftOfferStage, reduced: Boolean): () -> Unit {
        if (!foreground) {
            heldControls = 0
            readingDragging = false
            readingMomentum = false
            clearQuiet()
            values.forEach { it.stop() }
            return {}
        }
        if (!manual) return {}
        val duration = if (reduced) 0.0 else 220.0
        if (stage == ConfettiGiftOfferStage.Opening) {
            val remaining = if (reduced) 0.0 else max(0.0, .74 - openingTime.value) * 1000
            openingTime.timing(scope, .74f, remaining, LinearEasing) { finishOpening() }
            return { openingTime.stop() }
        }
        giftAlpha.timing(scope, if (stage == ConfettiGiftOfferStage.Gift) 1f else 0f, duration, ConfettiGiftOfferEaseOut)
        if (stage == ConfettiGiftOfferStage.Signup) {
            signupAlpha.set(1f)
            offerAlpha.delayedSet(scope, 0f, if (reduced) 0 else 400)
            val push = if (reduced) 0.0 else max(0.0, 1 - manualPush.value.toDouble()) * 400
            manualPush.timing(scope, 1f, push, ConfettiGiftOfferEaseOut) { finishPush() }
        } else {
            val offerTarget = if (stage == ConfettiGiftOfferStage.Offer || stage == ConfettiGiftOfferStage.Exit) 1f else 0f
            offerAlpha.timing(scope, offerTarget, duration, ConfettiGiftOfferEaseOut)
            signupAlpha.timing(scope, 0f, duration, ConfettiGiftOfferEaseOut)
        }
        if (stage == ConfettiGiftOfferStage.Gift) lidOpen.set(0f)
        return {}
    }

    /** Back in the foreground with a touch in charge: wait for quiet again (ConfettiGiftOffer.tsx:184). */
    fun foregroundReturned() {
        if (manual) armQuiet(stage)
    }

    // ---- actions ----

    fun open() {
        own(ConfettiGiftOfferStage.Opening)
        openingTime.set(0f)
        manualPush.set(0f)
        lightHaptic()
    }

    fun stay() = go(ConfettiGiftOfferStage.Offer)

    private fun pushToSignup(acceptedOffer: Boolean, retainSheet: Boolean) {
        accepted = acceptedOffer
        manualExitVisible = retainSheet
        own(ConfettiGiftOfferStage.Signup)
    }

    fun claim() = pushToSignup(true, false)

    fun skip() = pushToSignup(false, true)

    fun ownSheet() = own(ConfettiGiftOfferStage.Exit)

    fun reset() {
        accepted = false
        go(ConfettiGiftOfferStage.Gift)
    }

    fun finish(provider: ConfettiGiftOfferProvider) {
        own(ConfettiGiftOfferStage.Complete)
        onContinue(provider, accepted)
        successHaptic()
    }

    // ---- reading events of the scroll views (ConfettiGiftOffer.tsx:212) ----

    fun scrollBeginDrag() {
        readingDragging = true
        own(stage)
    }

    fun scrollEndDrag() {
        readingDragging = false
        armQuiet(stage)
    }

    fun momentumBegin() {
        readingMomentum = true
        clearQuiet()
    }

    fun momentumEnd() {
        readingMomentum = false
        armQuiet(stage)
    }

    // ---- derived values read inside graphicsLayer / draw lambdas ----

    /** `pushProgress` (ConfettiGiftOffer.tsx:191). */
    fun pushProgress(): Float = if (manual) manualPush.value else confettiGiftOfferSourcePush(time).toFloat()

    /** `openProgress` (ConfettiGiftOffer.tsx:192): how far the gift's lid is open. */
    fun openProgress(): Float {
        if (!manual) {
            return if (time >= 8.75) 0f else confettiGiftOfferFraction(time, .333, .583).toFloat()
        }
        return if (stage == ConfettiGiftOfferStage.Opening) {
            confettiGiftOfferFraction(openingTime.value.toDouble(), 0.0, .22).toFloat()
        } else {
            lidOpen.value
        }
    }

    /** `confettiAge` (ConfettiGiftOffer.tsx:193): seconds since the burst, negative before it. */
    fun confettiAge(): Double {
        if (manual) {
            return if (stage == ConfettiGiftOfferStage.Opening) openingTime.value - .22 else burst.value.toDouble()
        }
        return if (time >= 4.05 && time < 4.9) time - 4.05 else time - .583
    }

    /** `giftStyle` opacity (ConfettiGiftOffer.tsx:198). */
    fun giftOpacity(): Float {
        if (!manual) return confettiGiftOfferSourceGiftAlpha(time).toFloat()
        if (stage == ConfettiGiftOfferStage.Opening) return if (openingTime.value < .22f) 1f else 0f
        return giftAlpha.value
    }

    /** The ticket's reveal progress behind its scale from 64% (ConfettiGiftOffer.tsx:200). */
    fun offerReveal(): Float {
        if (!manual) return confettiGiftOfferEnter(confettiGiftOfferFraction(time, .617, 1.1)).toFloat()
        if (stage != ConfettiGiftOfferStage.Opening) return 1f
        return confettiGiftOfferEnter(confettiGiftOfferFraction(openingTime.value + .363, .617, 1.1)).toFloat()
    }

    /** `offerStyle` opacity (ConfettiGiftOffer.tsx:201). */
    fun offerOpacity(): Float {
        if (!manual) return confettiGiftOfferSourceOfferAlpha(time).toFloat()
        if (stage == ConfettiGiftOfferStage.Opening) return confettiGiftOfferSourceOfferAlpha(openingTime.value + .363).toFloat()
        return offerAlpha.value
    }

    /** `offerWashStyle` opacity (ConfettiGiftOffer.tsx:205): the white behind the ticket. */
    fun washOpacity(): Float {
        if (!manual) {
            val fade = 1 - confettiGiftOfferPhase(time, 4.9, 4.92)
            return (confettiGiftOfferFraction(time, .583, .667) * fade).toFloat()
        }
        return when (stage) {
            ConfettiGiftOfferStage.Opening -> confettiGiftOfferFraction(openingTime.value.toDouble(), .22, .304).toFloat()
            ConfettiGiftOfferStage.Offer, ConfettiGiftOfferStage.Exit, ConfettiGiftOfferStage.Signup -> 1f
            else -> 0f
        }
    }

    /** `signupStyle` opacity (ConfettiGiftOffer.tsx:208). */
    fun signupOpacity(): Float = if (manual) signupAlpha.value else confettiGiftOfferSourceSignupAlpha(time).toFloat()
}
