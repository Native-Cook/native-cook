package com.ngocdevv.nativecook.blocks.gestures.cassettedeck

import android.view.HapticFeedbackConstants
import android.view.View

internal fun View.performCassetteDeckHaptic(haptic: CassetteDeckHaptic) {
    val feedback = when (haptic) {
        CassetteDeckHaptic.Latch -> HapticFeedbackConstants.LONG_PRESS
        CassetteDeckHaptic.Key,
        CassetteDeckHaptic.Memory,
        -> HapticFeedbackConstants.KEYBOARD_TAP
    }
    performHapticFeedback(feedback)
}
