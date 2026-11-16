package com.ngocdevv.nativecook.blocks.onboarding.solidmorphonboarding

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Autoplay is silent. Contact fires once at the 4 pt cushion stop (and at the rubber's end): rigid; the glyph reveal: selection; a page's arrival after a
 * real interaction: light. At most one cue every 100 ms.
 */
internal class SolidMorphOnboardingHaptics private constructor(private val vibrator: Vibrator?) {
    companion object {
        fun create(context: Context): SolidMorphOnboardingHaptics {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            return SolidMorphOnboardingHaptics(vibrator)
        }
    }

    private var last = -1e9

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

    fun emit(beat: Int) {
        val now = System.nanoTime() / 1e6
        if (now - last < 100) return
        last = now
        when (beat) {
            SolidMorphOnboardingBeat.CONTACT -> buzz(predefined(VibrationEffect.EFFECT_HEAVY_CLICK, 30, 120))
            SolidMorphOnboardingBeat.TICK -> buzz(predefined(VibrationEffect.EFFECT_TICK, 10, 40))
            SolidMorphOnboardingBeat.ARRIVAL -> buzz(predefined(VibrationEffect.EFFECT_TICK, 14, 50))
        }
    }
}
