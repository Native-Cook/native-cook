package com.ngocdevv.nativecook.blocks.sheets.eventpassshare

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicReference

internal class EventPassShareRuntime(
    val controller: EventPassShareController,
    private val animateAmbient: Boolean,
) {
    val frame = AtomicReference(EventPassFrame.Resting)
    private val ambientOutput = EventPassShareAmbientFrame()
    val ambientFrame = AtomicReference(ambientOutput)
    val drawRevision: MutableIntState = mutableIntStateOf(0)
    val ambientRevision: MutableIntState = mutableIntStateOf(0)
    val backVisible: MutableState<Boolean> = mutableStateOf(false)
    val copyConfirmed: MutableState<Boolean> = mutableStateOf(false)

    private val wakeEvents = Channel<Unit>(Channel.CONFLATED)
    private var appeared = false
    private var autoPlay = false
    private var quiet = false
    private var foreground = true
    private var ambientClock = 0.0
    private var previousAmbientSample = -1.0
    private val ambientFollower = EventPassShareAmbientFollower()
    private val spill = FloatArray(3)

    fun configure(autoPlay: Boolean, quiet: Boolean, at: Double) {
        if (!appeared) {
            appeared = true
            this.autoPlay = autoPlay
            this.quiet = quiet
            controller.appear(autoPlay, quiet, at)
        } else {
            if (this.autoPlay != autoPlay) controller.setAutoPlay(autoPlay, at)
            if (this.quiet != quiet) controller.setQuiet(quiet, at)
            this.autoPlay = autoPlay
            this.quiet = quiet
        }
        if (quiet) resetAmbient()
        publish(at)
        wake()
    }

    fun setForeground(active: Boolean) {
        val at = nowSeconds()
        foreground = active
        previousAmbientSample = -1.0
        controller.setForeground(active, at)
        publish(at)
        wake()
    }

    fun setVisible(visible: Boolean) {
        val at = nowSeconds()
        controller.setVisible(visible, at)
        publish(at)
        wake()
    }

    fun setSharePresented(presented: Boolean) {
        val at = nowSeconds()
        controller.setSharePresented(presented, at)
        publish(at)
        wake()
    }

    fun beginInteraction(): Boolean {
        val at = nowSeconds()
        val accepted = controller.beginInteraction(at)
        publish(at)
        wake()
        return accepted
    }

    fun updateInteraction(horizontal: Double, vertical: Double) {
        controller.updateInteraction(horizontal, vertical)
        publish(nowSeconds())
        wake()
    }

    fun endInteraction(
        horizontal: Double,
        vertical: Double,
        predictedHorizontal: Double,
        predictedVertical: Double,
    ): Boolean {
        val at = nowSeconds()
        val committedFlip = controller.endInteraction(
            horizontal,
            vertical,
            predictedHorizontal,
            predictedVertical,
            at,
            quiet,
        )
        publish(at)
        wake()
        return committedFlip
    }

    fun cancelInteraction() {
        val at = nowSeconds()
        controller.cancelInteraction(at)
        publish(at)
        wake()
    }

    fun toggleFace() {
        val at = nowSeconds()
        controller.toggleFace(at, quiet)
        publish(at)
        wake()
    }

    fun confirmCopy() {
        val at = nowSeconds()
        controller.confirmCopy(at)
        publish(at)
        wake()
    }

    suspend fun runClock() {
        while (currentCoroutineContext().isActive) {
            val at = nowSeconds()
            val cadence = controller.clockCadence(at)
            if (cadence == EventPassCadence.Animation) publish(at)
            if (animateAmbient && foreground && !quiet) updateAmbient(at)
            if (cadence == EventPassCadence.Animation || animateAmbient && foreground && !quiet) {
                withFrameNanos { }
            } else {
                wakeEvents.receive()
            }
        }
    }

    fun wake() {
        wakeEvents.trySend(Unit)
    }

    private fun publish(at: Double) {
        val next = controller.frame(at)
        frame.set(next)
        drawRevision.intValue += 1
        val nextBackVisible = next.backOpacity >= 0.5
        if (backVisible.value != nextBackVisible) backVisible.value = nextBackVisible
        if (copyConfirmed.value != next.copyConfirmed) copyConfirmed.value = next.copyConfirmed
    }

    private fun updateAmbient(at: Double) {
        val previous = previousAmbientSample
        val delta = if (previous >= 0.0) (at - previous).coerceAtLeast(0.0) else 0.0
        previousAmbientSample = at
        ambientClock += delta
        val current = frame.get()
        val turn = if (current.quietCrossfade) 0.0 else current.flipAngle
        eventPassShareSpillUniform(current.tiltX, current.tiltY, turn, spill)
        stepEventPassShareAmbientFollower(
            ambientFollower,
            spill[0],
            spill[1],
            spill[2],
            delta.toFloat(),
        )
        ambientFollower.writeFrame(ambientAngle(ambientClock), ambientOutput)
        ambientRevision.intValue += 1
    }

    private fun resetAmbient() {
        previousAmbientSample = -1.0
        ambientClock = 0.0
        ambientFollower.reset()
        ambientOutput.set(0f, 0f, 0f, 0f)
        ambientRevision.intValue += 1
    }
}

internal fun nowSeconds(): Double = System.nanoTime() / 1_000_000_000.0
