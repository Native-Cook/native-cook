package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

// Only amount steps, an explicit empty Send and landing speak to the hand.
// Platform feedback respects the user's haptics setting without a permission.
internal class AuraHaptics(private val view: View) {
    fun play(kind: Int) {
        val effect = when (kind) {
            0 -> HapticFeedbackConstants.CLOCK_TICK
            1 -> if (Build.VERSION.SDK_INT >= 27) HapticFeedbackConstants.VIRTUAL_KEY else HapticFeedbackConstants.KEYBOARD_TAP
            else -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(effect)
    }
}
