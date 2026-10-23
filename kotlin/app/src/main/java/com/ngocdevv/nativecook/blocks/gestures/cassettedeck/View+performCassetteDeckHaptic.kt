package com.ngocdevv.nativecook.blocks.gestures.cassettedeck

import android.view.HapticFeedbackConstants
import android.view.View

/** Maps the source's medium, heavy, light, rigid and selection feedback (RN `playHaptic`) to Android. */
internal fun View.performCassetteDeckHaptic(haptic: CassetteDeckHaptic) {
    val feedback = when (haptic) {
        CassetteDeckHaptic.CLICK -> HapticFeedbackConstants.KEYBOARD_TAP
        CassetteDeckHaptic.LATCH -> HapticFeedbackConstants.LONG_PRESS
        CassetteDeckHaptic.RELEASE -> HapticFeedbackConstants.VIRTUAL_KEY
        CassetteDeckHaptic.COMMIT -> HapticFeedbackConstants.CONTEXT_CLICK
        CassetteDeckHaptic.TICK -> HapticFeedbackConstants.CLOCK_TICK
    }
    performHapticFeedback(feedback)
}
