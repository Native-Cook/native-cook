package com.ngocdevv.nativecook.blocks.gestures.tiltposterparallax

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalView

@Composable
internal fun TiltPosterParallaxClock(engine: TiltPosterParallaxEngine) {
    val view = LocalView.current
    LaunchedEffect(engine, view) {
        while (true) {
            val frameNanos = withFrameNanos { it }
            engine.frame(frameNanos).forEach { haptic ->
                val constant = when (haptic) {
                    TiltPosterParallaxHaptic.Tap -> HapticFeedbackConstants.VIRTUAL_KEY
                    TiltPosterParallaxHaptic.Face -> HapticFeedbackConstants.LONG_PRESS
                    TiltPosterParallaxHaptic.Edge -> HapticFeedbackConstants.CLOCK_TICK
                    TiltPosterParallaxHaptic.Save -> HapticFeedbackConstants.LONG_PRESS
                    TiltPosterParallaxHaptic.Remove -> HapticFeedbackConstants.VIRTUAL_KEY
                }
                view.performHapticFeedback(constant)
            }
        }
    }
}
