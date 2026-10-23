package com.ngocdevv.nativecook.blocks.cards.paddockwidgetbento

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class PaddockWidgetBentoReaction(
    private val state: PaddockWidgetBentoContentState,
    private val runtime: PaddockWidgetBentoRuntime,
    private val scope: CoroutineScope,
) {
    private var reactionStart: Double? = null
    private var reactionJob: Job? = null

    fun cancel() {
        reactionJob?.cancel()
        reactionJob = null
        reactionStart = null
        state.reactionText = "Ready"
    }

    fun act() {
        if (state.reactionText.endsWith(" ms")) {
            cancel()
            return
        }
        if (state.reactionText == "Ready" || state.reactionText.startsWith("Too soon")) {
            state.reactionText = "Get ready…"
            reactionJob?.cancel()
            reactionJob = scope.launch {
                delay(3_000)
                if (state.destination != PaddockWidgetBentoDestination.Reflex || !runtime.foreground) {
                    return@launch
                }
                reactionStart = paddockNowSeconds()
                state.reactionText = "GO"
            }
        } else if (state.reactionText == "GO" && reactionStart != null) {
            val started = requireNotNull(reactionStart)
            state.reactionText = "${((paddockNowSeconds() - started) * 1_000).toInt()} ms"
            reactionStart = null
        } else {
            cancel()
            state.reactionText = "Too soon. Try again"
        }
    }
}
