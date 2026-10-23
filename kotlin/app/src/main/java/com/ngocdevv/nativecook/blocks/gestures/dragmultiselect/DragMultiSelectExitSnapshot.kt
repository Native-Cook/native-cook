package com.ngocdevv.nativecook.blocks.gestures.dragmultiselect

import androidx.compose.runtime.State

/** A deleted row stays at its pre-layout position while the active list closes underneath it. */
internal class DragMultiSelectExitSnapshot(
    val task: DragMultiSelectTask,
    val row: Int,
    val order: Int,
    reducedMotion: Boolean,
) {
    private val progressAnimation = DragMultiSelectAnimatedFloat(0f)

    val progress: State<Float> = progressAnimation

    init {
        progressAnimation.timingTo(
            next = 1f,
            durationMillis = if (reducedMotion) {
                DragMultiSelectMotion.timing.reduced
            } else {
                DragMultiSelectMotion.timing.streamOut
            },
            easing = DragMultiSelectEasing.QUAD_IN_OUT,
            delayMillis = if (reducedMotion) {
                0L
            } else {
                order * DragMultiSelectMotion.timing.streamStagger
            },
        )
    }

    fun step(deltaSeconds: Float) {
        progressAnimation.step(deltaSeconds)
    }
}
