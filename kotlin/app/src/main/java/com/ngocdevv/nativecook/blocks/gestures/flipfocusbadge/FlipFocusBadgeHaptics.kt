package com.ngocdevv.nativecook.blocks.gestures.flipfocusbadge

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The haptics map of the RN block (no sound: a focus app is silent while face down). Only touches play them; the autoplay loop and a device turn
 * between detents are silent.
 *
 *   finger lands on the badge ............ light impact (onset)        the badge passes a face-on angle ... selection (detent, at most every 60 ms)
 *   it lands on its back / front ......... medium impact (land)        an hour is reached ................. success, once the numeral has punched out
 *   the numeral pressed into the plate ... soft impact (and again when it pops back)                         a session shorter than a minute ... light impact
 *   the satellite completes a lap ........ selection
 */
internal class FlipFocusBadgeHaptics private constructor(private val vibrator: Vibrator?) {
    companion object {
        fun create(context: Context): FlipFocusBadgeHaptics {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            return FlipFocusBadgeHaptics(vibrator)
        }
    }

    private fun buzz(effect: VibrationEffect) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        // A failed request must never stop rendering; hosts copying the block must declare android.permission.VIBRATE.
        try {
            v.vibrate(effect)
        } catch (_: SecurityException) {
        }
    }

    private fun predefined(id: Int, fallbackMs: Long, amp: Int): VibrationEffect =
        if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(id) else VibrationEffect.createWaveform(longArrayOf(0, fallbackMs), intArrayOf(0, amp), -1)

    fun onset() = buzz(predefined(VibrationEffect.EFFECT_TICK, 12, 40))
    fun detent() = buzz(predefined(VibrationEffect.EFFECT_TICK, 10, 40))
    fun land() = buzz(predefined(VibrationEffect.EFFECT_CLICK, 24, 90))
    fun success() = buzz(VibrationEffect.createWaveform(longArrayOf(0, 40, 100, 40), intArrayOf(0, 50, 0, 60), -1))
    fun press() = buzz(predefined(VibrationEffect.EFFECT_TICK, 14, 30))
    fun discard() = buzz(predefined(VibrationEffect.EFFECT_TICK, 12, 40))
    fun lap() = buzz(predefined(VibrationEffect.EFFECT_TICK, 10, 40))
}
