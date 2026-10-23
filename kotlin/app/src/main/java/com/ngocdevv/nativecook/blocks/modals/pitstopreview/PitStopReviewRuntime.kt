package com.ngocdevv.nativecook.blocks.modals.pitstopreview

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

/** Drives only active scene phases; state reads remain inside draw and layer scopes. */
internal class PitStopReviewRuntime(
    val controller: PitStopReviewController,
) {
    data class Snapshot(
        val scene: Double,
        val panel: Double,
        val panelVisible: Boolean,
        val quiet: Boolean,
    )

    val snapshot = mutableStateOf(read(nowSeconds()))
    private val events = Channel<Unit>(Channel.CONFLATED)

    suspend fun runClock() {
        while (currentCoroutineContext().isActive) {
            val now = nowSeconds()
            snapshot.value = read(now)
            if (controller.cadence(now) == PitStopReviewController.Cadence.ANIMATION) {
                withFrameNanos { }
                continue
            }
            val delay = controller.nextWakeDelay(now)
            if (delay == null) {
                events.receive()
            } else {
                withTimeoutOrNull((delay * 1_000.0).toLong().coerceAtLeast(1L)) {
                    events.receive()
                }
            }
        }
    }

    fun publish() {
        snapshot.value = read(nowSeconds())
        events.trySend(Unit)
    }

    private fun read(now: Double): Snapshot = Snapshot(
        scene = controller.scene(now),
        panel = controller.panel(now),
        panelVisible = controller.panelVisible(now),
        quiet = controller.quiet,
    )
}

internal fun nowSeconds(): Double = System.nanoTime() / 1_000_000_000.0
