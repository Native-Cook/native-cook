package com.ngocdevv.nativecook.blocks.modals.glasspourmenu

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The block's cues: haptics (touch only, autoplay is silent) and, when `sound` is on, three procedural glass sounds (a tap, a pinch "plip"
 * and its reversed "merge") played through a [SoundPool] on the media stream, without touching the host's audio session.
 *   grab the drop soft, the bead pinches off medium, merges back light, the bar crosses a row selection, a row is chosen success (Delete: warning).
 */
internal class GlassPourMenuHaptics private constructor(
    private val vibrator: Vibrator?,
    private val pool: SoundPool?,
    private val ids: IntArray,
) {
    companion object {
        fun create(context: Context, sound: Boolean): GlassPourMenuHaptics {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            var pool: SoundPool? = null
            val ids = IntArray(3)
            if (sound) {
                try {
                    val sp = SoundPool.Builder()
                        .setMaxStreams(3)
                        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                        .build()
                    val names = arrayOf("tap", "plip", "merge")
                    for (i in names.indices) {
                        context.assets.openFd("glasspourmenu/glasspourmenu_${names[i]}.wav").use { ids[i] = sp.load(it, 1) }
                    }
                    pool = sp
                } catch (error: Exception) {
                    pool = null
                }
            }
            return GlassPourMenuHaptics(vibrator, pool, ids)
        }
    }

    fun release() {
        pool?.release()
    }

    private fun play(i: Int) {
        pool?.play(ids[i], 0.8f, 0.8f, 1, 0, 1f)
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

    /** `beat` is a [GlassPourMenuBeat]; `haptics` / `sound` are the component's switches. */
    fun emit(beat: Int, haptics: Boolean, sound: Boolean) {
        if (haptics) {
            when (beat) {
                GlassPourMenuBeat.GRAB_TAP, GlassPourMenuBeat.GRAB -> buzz(predefined(VibrationEffect.EFFECT_TICK, 30, 30))
                GlassPourMenuBeat.PINCH -> buzz(predefined(VibrationEffect.EFFECT_CLICK, 43, 50))
                GlassPourMenuBeat.MERGE -> buzz(predefined(VibrationEffect.EFFECT_TICK, 50, 30))
                GlassPourMenuBeat.ROW, GlassPourMenuBeat.ROW_TAP -> buzz(predefined(VibrationEffect.EFFECT_TICK, 12, 40))
                GlassPourMenuBeat.PICK -> buzz(VibrationEffect.createWaveform(longArrayOf(0, 40, 100, 40), intArrayOf(0, 50, 0, 60), -1))
                GlassPourMenuBeat.PICK_DANGER -> buzz(VibrationEffect.createWaveform(longArrayOf(0, 60, 60, 60), intArrayOf(0, 90, 0, 90), -1))
            }
        }
        if (sound && pool != null) {
            when (beat) {
                GlassPourMenuBeat.GRAB_TAP, GlassPourMenuBeat.ROW_TAP -> play(0)
                GlassPourMenuBeat.PINCH -> play(1)
                GlassPourMenuBeat.MERGE -> play(2)
            }
        }
    }
}
