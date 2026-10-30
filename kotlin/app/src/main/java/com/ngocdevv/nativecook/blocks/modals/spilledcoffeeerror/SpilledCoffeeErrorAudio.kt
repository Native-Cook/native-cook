package com.ngocdevv.nativecook.blocks.modals.spilledcoffeeerror

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator

/** Cues answer touch only. A grab cancels every remaining retry cue, like RN's scriptTimers. */
internal class SpilledCoffeeErrorAudio(context: Context, private val enabled: Boolean) {
    private val handler = Handler(Looper.getMainLooper())
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private val manager = context.getSystemService(AudioManager::class.java)
    private val pool = SoundPool.Builder().setMaxStreams(5).setAudioAttributes(AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val loaded = mutableSetOf<Int>()
    private val sounds = mutableMapOf<String, Int>()
    private val streams = mutableMapOf<String, Int>()
    init {
        pool.setOnLoadCompleteListener {
            _, id, status -> if (status == 0) loaded.add(id)
        }

        if (enabled) for (cue in listOf("rewind", "clack", "tip", "splash", "tap")) {
            val id = context.resources.getIdentifier("spilledcoffeeerror_sce_$cue", "raw", context.packageName)
            if (id != 0) sounds[cue] = pool.load(context, id, 1)
        }
    }

    fun play(cue: String) {
        if (!enabled || manager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val id = sounds[cue] ?: return
        if (id !in loaded) return
        val volume = if (cue == "rewind") 0.6f else 0.85f
        streams[cue]?.let { pool.stop(it) }
        streams[cue] = pool.play(id, volume, volume, 1, 0, 1f)
    }

    fun buzz(kind: Int) {
        when (kind) {
            2 -> vibrator?.vibrate(VibrationEffect.createOneShot(8, 35))
            3 -> vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 20, 45, 30), intArrayOf(0, 70, 0, 100), -1))
            else -> vibrator?.vibrate(VibrationEffect.createOneShot(if (kind == 1) 18 else 12, if (kind == 1) 90 else 45))
        }
    }

    fun cancelScript() = handler.removeCallbacksAndMessages(null)
    fun playScript() {
        cancelScript()
        play("rewind")
        handler.postDelayed({
            play("clack")
        }
        , 1150)
        handler.postDelayed({
            play("tip")
            buzz(1)
        }
        , 5150)
        handler.postDelayed({
            play("splash")
            buzz(3)
        }
        , 5300)
    }

    fun close() {
        cancelScript()
        pool.release()
    }
}
