package com.ngocdevv.nativecook.blocks.lists.luckytaskhub
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.ngocdevv.nativecook.R
/** Touch cues only; reduced motion and silent/vibrate modes suppress playback. */
internal class LuckyTaskHubAudio(context: Context, private val enabled: Boolean) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val loaded = mutableSetOf<Int>()
    private val sounds: Map<String, Int>
    init {
        pool.setOnLoadCompleteListener {
            _, id, status -> if (status == 0)loaded.add(id)
        }
        sounds = if (enabled)mapOf("spin" to pool.load(context, R.raw.luckytaskhub_ltk_spin, 1),
        "cash" to pool.load(context, R.raw.luckytaskhub_ltk_cash, 1), "flip" to pool.load(context, R.raw.luckytaskhub_ltk_flip, 1))else emptyMap()
    }
    fun play(cue: String) {
        if (!enabled || manager.ringerMode != AudioManager.RINGER_MODE_NORMAL)return
        val sound = sounds[cue]?: return
        if (sound !in loaded)return
        val volume = if (cue == "spin")0.6f else 0.85f
        pool.play(sound, volume, volume, 1, 0, 1f)
    }
    fun release() = pool.release()
}
