package com.ngocdevv.nativecook.blocks.gestures.dragmultiselect

import androidx.compose.runtime.State

internal class DragMultiSelectRowMotion {
    private val bandAnimation = DragMultiSelectAnimatedFloat(0f)
    private val glossAnimation = DragMultiSelectAnimatedFloat(1f)
    private val ringAnimation = DragMultiSelectAnimatedFloat(1f)
    private val circleAnimation = DragMultiSelectAnimatedFloat(0f)
    private val previewAnimation = DragMultiSelectAnimatedFloat(0f)
    private val pressScaleAnimation = DragMultiSelectAnimatedFloat(0f)
    private val bandOriginAnimation = DragMultiSelectAnimatedFloat(0f)
    private val placementAnimation = DragMultiSelectAnimatedFloat(0f)
    private val restoreYAnimation = DragMultiSelectAnimatedFloat(0f)
    private val restoreOpacityAnimation = DragMultiSelectAnimatedFloat(1f)

    val band: State<Float> = bandAnimation
    val gloss: State<Float> = glossAnimation
    val ring: State<Float> = ringAnimation
    val circleIn: State<Float> = circleAnimation
    val pressPreview: State<Float> = previewAnimation
    val pressScale: State<Float> = pressScaleAnimation
    val bandOrigin: State<Float> = bandOriginAnimation
    val placement: State<Float> = placementAnimation
    val restoreY: State<Float> = restoreYAnimation
    val restoreOpacity: State<Float> = restoreOpacityAnimation

    fun setSelected(
        selected: Boolean,
        reducedMotion: Boolean,
        origin: Float,
    ) {
        bandOriginAnimation.snapTo(origin)
        if (selected) {
            if (reducedMotion) {
                bandAnimation.timingTo(1f, DragMultiSelectMotion.timing.reduced)
            } else {
                bandAnimation.springTo(1f, DragMultiSelectMotion.spring.band)
                glossAnimation.snapTo(0f)
                glossAnimation.timingTo(
                    next = 1f,
                    durationMillis = DragMultiSelectMotion.timing.gloss,
                    easing = DragMultiSelectEasing.EASE_IN_OUT,
                )
                ringAnimation.snapTo(0f)
                ringAnimation.timingTo(
                    next = 1f,
                    durationMillis = DragMultiSelectMotion.timing.ripple,
                    easing = DragMultiSelectEasing.EASE_OUT,
                )
            }
        } else {
            val duration = if (reducedMotion) {
                DragMultiSelectMotion.timing.reduced
            } else {
                DragMultiSelectMotion.timing.bandFade
            }
            bandAnimation.timingTo(
                next = 0f,
                durationMillis = duration,
                easing = if (reducedMotion) DragMultiSelectEasing.QUAD_IN_OUT else DragMultiSelectEasing.EASE_OUT,
            )
        }
    }

    fun setCircleVisible(
        visible: Boolean,
        index: Int,
        reducedMotion: Boolean,
    ) {
        if (reducedMotion) {
            circleAnimation.timingTo(
                next = if (visible) 1f else 0f,
                durationMillis = DragMultiSelectMotion.timing.reduced,
            )
            return
        }
        val delay = if (visible) {
            minOf(index, 12) * DragMultiSelectMotion.timing.circleStagger
        } else {
            0L
        }
        circleAnimation.springTo(
            next = if (visible) 1f else 0f,
            spring = DragMultiSelectMotion.spring.circles,
            delayMillis = delay,
        )
    }

    fun setPress(progress: Float) {
        previewAnimation.snapTo(progress)
        pressScaleAnimation.snapTo(progress)
    }

    fun moveFrom(offset: Float) {
        placementAnimation.snapTo(offset)
        // LinearTransition.springify() keeps Reanimated's installed default mass of 4.
        placementAnimation.springTo(0f, DragMultiSelectActualCollapseSpring)
    }

    fun restore(reducedMotion: Boolean) {
        restoreOpacityAnimation.snapTo(0f)
        if (reducedMotion) {
            restoreYAnimation.snapTo(0f)
            restoreOpacityAnimation.timingTo(1f, DragMultiSelectMotion.timing.reduced)
            return
        }
        // springify clears duration; FadeInDown uses the Reanimated defaults copied in Tokens.
        restoreYAnimation.snapTo(DragMultiSelectTokens.View.Delete.restoreOffsetY)
        restoreYAnimation.springTo(0f, DragMultiSelectActualRestoreSpring)
        restoreOpacityAnimation.springTo(1f, DragMultiSelectActualRestoreSpring)
    }

    fun step(deltaSeconds: Float) {
        bandAnimation.step(deltaSeconds)
        glossAnimation.step(deltaSeconds)
        ringAnimation.step(deltaSeconds)
        circleAnimation.step(deltaSeconds)
        placementAnimation.step(deltaSeconds)
        restoreYAnimation.step(deltaSeconds)
        restoreOpacityAnimation.step(deltaSeconds)
    }

    fun reset() {
        bandAnimation.snapTo(0f)
        glossAnimation.snapTo(1f)
        ringAnimation.snapTo(1f)
        circleAnimation.snapTo(0f)
        previewAnimation.snapTo(0f)
        pressScaleAnimation.snapTo(0f)
        bandOriginAnimation.snapTo(0f)
        placementAnimation.snapTo(0f)
        restoreYAnimation.snapTo(0f)
        restoreOpacityAnimation.snapTo(1f)
    }
}

private val DragMultiSelectActualCollapseSpring = DragMultiSelectTokens.SpringSpec(
    damping = DragMultiSelectTokens.Spring.collapse.damping,
    stiffness = DragMultiSelectTokens.Spring.collapse.stiffness,
    mass = DragMultiSelectTokens.View.Delete.reanimatedDefaultMass,
)

private val DragMultiSelectActualRestoreSpring = DragMultiSelectTokens.SpringSpec(
    damping = DragMultiSelectTokens.View.Delete.restoreDamping,
    stiffness = DragMultiSelectTokens.View.Delete.defaultSpringStiffness,
    mass = DragMultiSelectTokens.View.Delete.reanimatedDefaultMass,
)
