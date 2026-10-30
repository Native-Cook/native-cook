package com.ngocdevv.nativecook.blocks.cards.frostcardcontrols

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.ngocdevv.nativecook.R

/** Cues answer touch only, and respect Android's silent/vibrate ringer modes. */
internal class FrostCardControlsAudio(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val ids = mapOf("freeze" to R.raw.frostcardcontrols_freeze, "melt" to R.raw.frostcardcontrols_melt, "shred" to R.raw.frostcardcontrols_shred, "reissue" to R.raw.frostcardcontrols_reissue).mapValues {
        pool.load(context, it.value, 1)
    }
    private val streams = mutableMapOf<String, Int>()

    fun play(cue: String, kind: Int) {
        if (audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            val volume = if (cue == "reissue") 0.7f else 0.85f
            streams[cue]?.let { pool.stop(it) }
            streams[cue] = pool.play(ids.getValue(cue), volume, volume, 1, 0, 1f)
        }
        if (kind == 3 && Build.VERSION.SDK_INT >= 29) vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
        else vibrator?.vibrate(VibrationEffect.createOneShot(if (kind == 1) 20L else 12L, if (kind == 1) 100 else 45))
    }
    fun release() = pool.release()
}
