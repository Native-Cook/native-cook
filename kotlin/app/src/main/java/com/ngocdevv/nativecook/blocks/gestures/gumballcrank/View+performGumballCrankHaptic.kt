package com.ngocdevv.nativecook.blocks.gestures.gumballcrank

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/** Maps the source's selection, rigid, soft, heavy, success and light feedback to Android. */
internal fun View.performGumballCrankHaptic(haptic: GumballCrankHaptic) {
    val feedback = when (haptic) {
        GumballCrankHaptic.GRAB -> HapticFeedbackConstants.VIRTUAL_KEY
        GumballCrankHaptic.DETENT -> HapticFeedbackConstants.CLOCK_TICK
        GumballCrankHaptic.RATCHET -> HapticFeedbackConstants.KEYBOARD_TAP
        GumballCrankHaptic.DISPENSE -> HapticFeedbackConstants.LONG_PRESS
        GumballCrankHaptic.OPEN -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
        GumballCrankHaptic.COLLECT -> HapticFeedbackConstants.TEXT_HANDLE_MOVE
    }
    performHapticFeedback(feedback)
}
