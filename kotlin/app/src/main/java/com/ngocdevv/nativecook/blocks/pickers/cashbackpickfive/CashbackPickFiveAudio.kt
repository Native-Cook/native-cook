package com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.ngocdevv.nativecook.R

/** Five bundled touch-only cues; autoplay never calls this owner. */
internal class CashbackPickFiveAudio(context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val pool = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val ready = mutableSetOf<Int>()
    private val sounds: Map<String, Int>
    init {
        pool.setOnLoadCompleteListener {
            _, id, status -> if (status == 0) ready.add(id)
        }
        sounds = mapOf("pop" to R.raw.cashbackpickfive_cpf_pop, "drop" to R.raw.cashbackpickfive_cpf_drop, "back" to R.raw.cashbackpickfive_cpf_back, "nope" to R.raw.cashbackpickfive_cpf_nope, "sparkle" to R.raw.cashbackpickfive_cpf_sparkle)
        .mapValues {
            pool.load(context, it.value, 1)
        }
    }

    fun play(cue: String) {
        if (manager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
            return
        }
        val id = sounds[cue] ?: return
        if (id !in ready) {
            return
        }
        val volume = if (cue == "sparkle") 0.6f else 0.85f
        pool.play(id, volume, volume, 1, 0, 1f)
    }

    fun release() {
        pool.release()
    }
}
