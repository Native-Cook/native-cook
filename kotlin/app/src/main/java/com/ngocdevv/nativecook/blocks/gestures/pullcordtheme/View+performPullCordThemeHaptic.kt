package com.ngocdevv.nativecook.blocks.gestures.pullcordtheme

import android.view.HapticFeedbackConstants
import android.view.View

/** Maps the source's selection and heavy and soft impact feedback to Android. */
internal fun View.performPullCordThemeHaptic(kind: Int) {
    val feedback = when (kind) {
        // Haptics.selectionAsync: grabbing the pull and a tick every 12 pt of travel.
        PullCordThemeMotion.Haptic.GRAB, PullCordThemeMotion.Haptic.TICK -> HapticFeedbackConstants.CLOCK_TICK
        // ImpactFeedbackStyle.Heavy at the detent's click.
        PullCordThemeMotion.Haptic.DETENT -> HapticFeedbackConstants.LONG_PRESS
        // ImpactFeedbackStyle.Soft when the switch springs home.
        PullCordThemeMotion.Haptic.HOME -> HapticFeedbackConstants.VIRTUAL_KEY
        else -> return
    }
    performHapticFeedback(feedback)
}
