package com.ngocdevv.nativecook.blocks.cards.reededglassreveal

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The haptic cue map (RN `rgr-haptics.ts`): autoplay is silent, only a real finger or button press makes a cue. Rib ticks may come 16 times a second,
 * every other cue keeps 40 ms from the one before. onset light, rib tick selection, edge rigid, revealed success, masked soft, button press light.
 */
internal class ReededGlassRevealHaptics private constructor(private val vibrator: Vibrator?) {
    companion object {
        fun create(context: Context): ReededGlassRevealHaptics {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            return ReededGlassRevealHaptics(vibrator)
        }
    }

    private var lastCueAt = -1e9
    private var lastTickAt = -1e9

    private fun buzz(effect: VibrationEffect) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        // A cue must never break the interaction; hosts copying the block must declare android.permission.VIBRATE.
        try {
            v.vibrate(effect)
        } catch (_: SecurityException) {
        }
    }

    private fun predefined(id: Int, fallbackMs: Long, amp: Int): VibrationEffect =
        if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(id) else VibrationEffect.createWaveform(longArrayOf(0, fallbackMs), intArrayOf(0, amp), -1)

    fun emit(beat: Int) {
        val now = System.nanoTime() / 1e6
        val tick = beat == ReededGlassRevealBeat.TICK
        if (now - (if (tick) lastTickAt else lastCueAt) < (if (tick) 60 else 40)) return
        if (tick) lastTickAt = now else lastCueAt = now
        when (beat) {
            ReededGlassRevealBeat.ONSET, ReededGlassRevealBeat.PRESS -> buzz(predefined(VibrationEffect.EFFECT_TICK, 12, 40))
            ReededGlassRevealBeat.TICK -> buzz(predefined(VibrationEffect.EFFECT_TICK, 10, 40))
            ReededGlassRevealBeat.EDGE -> buzz(predefined(VibrationEffect.EFFECT_CLICK, 30, 90))
            ReededGlassRevealBeat.REVEALED -> buzz(VibrationEffect.createWaveform(longArrayOf(0, 40, 100, 40), intArrayOf(0, 50, 0, 60), -1))
            ReededGlassRevealBeat.MASKED -> buzz(predefined(VibrationEffect.EFFECT_TICK, 14, 30))
        }
    }
}
