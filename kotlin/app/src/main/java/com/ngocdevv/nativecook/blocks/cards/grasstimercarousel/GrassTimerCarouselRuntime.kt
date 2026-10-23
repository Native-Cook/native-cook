package com.ngocdevv.nativecook.blocks.cards.grasstimercarousel

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference

internal enum class GrassTimerAction { Hidden, Stop, Resume }

/** Bridges the pure model to draw-phase invalidation and two tiny native text overlays. */
internal class GrassTimerCarouselRuntime(
    val controller: GrassTimerCarouselController,
) {
    val frame = AtomicReference(GrassTimerCarouselController.demoFrame(0.0))
    val drawRevision: MutableIntState = mutableIntStateOf(0)
    val remainingSeconds: MutableIntState = mutableIntStateOf(GrassTimerTiming.GrassDuration)
    val timerCard: MutableIntState = mutableIntStateOf(0)
    val action: MutableState<GrassTimerAction> = mutableStateOf(GrassTimerAction.Hidden)
    val selectedCard: MutableIntState = mutableIntStateOf(0)
    val leadCopyAccessible: MutableState<Boolean> = mutableStateOf(true)
    val neighborCopyAccessible: MutableState<Boolean> = mutableStateOf(true)
    val timerAccessible: MutableState<Boolean> = mutableStateOf(false)

    private val wakeEvents = Channel<Unit>(Channel.CONFLATED)
    private var autoPlay = false
    private var reducedMotion = false
    private var handledActivationSequence = 0
    private var handledSelectionSequence = 0
    private var pendingHaptics = 0

    fun configure(autoPlay: Boolean, reducedMotion: Boolean, at: Double) {
        this.autoPlay = autoPlay
        this.reducedMotion = reducedMotion
        controller.appear(at)
        controller.autoPlayChanged(autoPlay, at)
        controller.setQuiet(reducedMotion, autoPlay, at)
        publish(at)
        wake()
    }

    fun scenePhaseChanged(isActive: Boolean, at: Double) {
        controller.scenePhaseChanged(isActive, at)
        publish(at)
        wake()
    }

    fun mutate(block: GrassTimerCarouselController.(Double, Boolean) -> Unit) {
        val at = nowSeconds()
        controller.block(at, reducedMotion)
        publish(at)
        wake()
    }

    fun carouselMutation(block: GrassTimerCarouselController.(Double) -> Unit) {
        val at = nowSeconds()
        controller.block(at)
        publish(at)
        wake()
    }

    fun updateCarousel(translation: Double) {
        controller.updateCarousel(translation)
        publish(nowSeconds())
        wake()
    }

    fun endCarousel(translation: Double, predictedTranslation: Double) {
        val at = nowSeconds()
        controller.endCarousel(translation, predictedTranslation, at)
        publish(at)
        wake()
    }

    suspend fun runClock(onHaptic: () -> Unit) {
        while (currentCoroutineContext().isActive) {
            val at = nowSeconds()
            publish(at)
            repeat(takePendingHaptics()) { onHaptic() }
            when (controller.clockCadence(at, autoPlay, reducedMotion)) {
                GrassTimerCadence.Animation -> withFrameNanos { }
                GrassTimerCadence.Seconds -> withTimeoutOrNull(1_000) { wakeEvents.receive() }
                GrassTimerCadence.Stopped -> wakeEvents.receive()
            }
        }
    }

    fun wake() {
        wakeEvents.trySend(Unit)
    }

    private fun publish(at: Double) {
        val next = controller.frame(at, autoPlay, reducedMotion)
        frame.set(next)
        drawRevision.intValue += 1
        if (remainingSeconds.intValue != next.remainingSeconds) remainingSeconds.intValue = next.remainingSeconds
        if (timerCard.intValue != next.selectedCard) timerCard.intValue = next.selectedCard
        if (selectedCard.intValue != next.selectedCard) selectedCard.intValue = next.selectedCard
        val nextLeadAccessible = next.copyOpacity >= 0.5
        val nextNeighborAccessible = next.secondCardOpacity >= 0.5
        val nextTimerAccessible = next.timerOpacity >= 0.5
        if (leadCopyAccessible.value != nextLeadAccessible) {
            leadCopyAccessible.value = nextLeadAccessible
        }
        if (neighborCopyAccessible.value != nextNeighborAccessible) {
            neighborCopyAccessible.value = nextNeighborAccessible
        }
        if (timerAccessible.value != nextTimerAccessible) {
            timerAccessible.value = nextTimerAccessible
        }
        val nextAction = when {
            next.timerOpacity <= 0.10 -> GrassTimerAction.Hidden
            next.phase == GrassTimerPhase.Returning -> GrassTimerAction.Resume
            next.phase == GrassTimerPhase.Active || next.phase == GrassTimerPhase.Morphing -> GrassTimerAction.Stop
            else -> GrassTimerAction.Hidden
        }
        if (action.value != nextAction) action.value = nextAction

        pendingHaptics += (next.activationSequence - handledActivationSequence).coerceAtLeast(0) +
            (next.selectionSequence - handledSelectionSequence).coerceAtLeast(0)
        handledActivationSequence = maxOf(handledActivationSequence, next.activationSequence)
        handledSelectionSequence = maxOf(handledSelectionSequence, next.selectionSequence)
    }

    private fun takePendingHaptics(): Int {
        val count = pendingHaptics
        pendingHaptics = 0
        return count
    }
}

internal fun nowSeconds(): Double = System.nanoTime() / 1_000_000_000.0
