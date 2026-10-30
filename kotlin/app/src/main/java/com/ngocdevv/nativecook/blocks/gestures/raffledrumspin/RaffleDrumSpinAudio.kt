package com.ngocdevv.nativecook.blocks.gestures.raffledrumspin

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.SystemClock

internal class RaffleDrumSpinAudio(private val context: Context) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val sounds = hashMapOf<String, IntArray>()
    private val next = hashMapOf<String, Int>()
    private val last = hashMapOf<String, Long>()

    private val volumes = mapOf(
        "select" to .30f,
        "land" to .46f,
        "ratchet" to .14f,
        "spin" to .12f,
        "pop" to .42f,
        "stitches" to .24f,
        "morph" to .30f,
        "burst" to .28f,
        "tick" to .13f,
        "tear" to .36f,
        "landStub" to .42f,
        "dust" to .22f,
    )

    init {
        val files = mapOf(
            "select" to listOf("select"),
            "land" to listOf("land1", "land2", "land3"),
            "ratchet" to listOf("ratchet", "ratchet", "ratchet"),
            "spin" to listOf("spin"),
            "pop" to listOf("pop"),
            "stitches" to listOf("stitches"),
            "morph" to listOf("morph"),
            "burst" to listOf("burst"),
            "tick" to listOf("tick", "tick", "tick"),
            "tear" to listOf("tear"),
            "landStub" to listOf("land-stub"),
            "dust" to listOf("dust"),
        )
        files.forEach { (cue, names) ->
            sounds[cue] = names.map { name ->
                context.assets.openFd("raffledrumspin/raffledrumspin_$name.wav").use { descriptor ->
                    soundPool.load(descriptor, 1)
                }
            }.toIntArray()
        }
    }

    fun play(name: String) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) return
        val pool = sounds[name] ?: return
        if (pool.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        if ((name == "ratchet" || name == "tick") && now - (last[name] ?: Long.MIN_VALUE / 2) < 50L) return
        last[name] = now
        val index = next[name] ?: 0
        next[name] = (index + 1) % pool.size
        val volume = volumes[name] ?: .55f
        soundPool.play(pool[index % pool.size], volume, volume, 1, 0, 1f)
    }

    fun release() = soundPool.release()
}
