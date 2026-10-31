package com.ngocdevv.nativecook.blocks.onboarding.cashhandoffonboarding

import android.content.Context
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

internal class CashHandoffOnboardingResources(context: Context) {
    private val assets = context.assets
    fun typeface(name: String): Typeface = Typeface.createFromAsset(assets,"cashhandoffonboarding/cashhandoffonboarding_$name.ttf")
    val display = typeface("Anton-Regular")
    val medium = typeface("Inter-Medium")
    val semiBold = typeface("Inter-SemiBold")
    val bold = typeface("Inter-Bold")
    val displayFamily = FontFamily(Font("cashhandoffonboarding/cashhandoffonboarding_Anton-Regular.ttf", assets))
}
internal class CashHandoffOnboardingAudio(context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val ready = mutableSetOf<Int>()
    private val streams = mutableMapOf<String,Int>()
    private val sounds: Map<String,Int>
    init {
        pool.setOnLoadCompleteListener { _, id, status -> if(status==0)ready.add(id) }
        sounds = listOf("flutter","catch","return").associateWith { cue -> context.assets.openFd("cashhandoffonboarding/cashhandoffonboarding_cho-$cue.wav").use { pool.load(it,1) } }
    }
    fun play(cue: String) {
        val id=sounds[cue] ?: return
        if(id !in ready || manager?.ringerMode != AudioManager.RINGER_MODE_NORMAL)return
        streams[cue]?.let { pool.stop(it) }
        val v=if(cue=="flutter")0.55f else 0.75f
        streams[cue]=pool.play(id,v,v,1,0,1f)
    }
    fun close() { pool.release() }
}
