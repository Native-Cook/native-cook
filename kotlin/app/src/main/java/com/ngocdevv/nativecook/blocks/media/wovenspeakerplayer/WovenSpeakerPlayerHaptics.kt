package com.ngocdevv.nativecook.blocks.media.wovenspeakerplayer

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The haptics map of the RN block (no sound; autoplay is silent). Finger lands on the mesh light, play: the first ring leaves the display medium,
 * every 5 s of track crossed while turning selection, start or end of the track rigid, the position snaps to the second light, pause: the last ring
 * reaches the rim soft, track change light.
 */
internal class WovenSpeakerPlayerHaptics private constructor(private val vibrator: Vibrator?) {
    companion object {
        fun create(context: Context): WovenSpeakerPlayerHaptics {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            return WovenSpeakerPlayerHaptics(vibrator)
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

    fun emit(beat: Int) {
        when (beat) {
            WovenSpeakerPlayerBeat.ONSET, WovenSpeakerPlayerBeat.SETTLE, WovenSpeakerPlayerBeat.TRACK -> buzz(predefined(VibrationEffect.EFFECT_TICK, 12, 40))
            WovenSpeakerPlayerBeat.FIRST_NOTE -> buzz(predefined(VibrationEffect.EFFECT_CLICK, 43, 50))
            WovenSpeakerPlayerBeat.TICK -> buzz(predefined(VibrationEffect.EFFECT_TICK, 10, 40))
            WovenSpeakerPlayerBeat.LIMIT -> buzz(predefined(VibrationEffect.EFFECT_HEAVY_CLICK, 40, 120))
            WovenSpeakerPlayerBeat.PAUSE -> buzz(predefined(VibrationEffect.EFFECT_TICK, 14, 30))
        }
    }
}
