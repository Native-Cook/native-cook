package com.ngocdevv.nativecook.blocks.cards.frostheartrewards

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import kotlin.math.exp
import kotlin.math.min

/** The block's synthesised cues (use-fhr-audio.ts), at the reference volumes. */
internal class FrostHeartRewardsAudio(context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
    ).build()
    private val ready = mutableSetOf<Int>()
    private val streams = mutableMapOf<String, Int>()
    private val sounds = mutableMapOf<String, Int>()
    private val volume = mapOf(
        "claim0" to 0.75f, "claim1" to 0.75f, "claim2" to 0.75f, "claim3" to 0.75f,
        "frost" to 0.36f, "tinkle" to 0.55f, "level" to 0.65f, "cutTing" to 0.52f,
    )
    private val files = mapOf(
        "claim0" to "fhr-claim0", "claim1" to "fhr-claim1", "claim2" to "fhr-claim2", "claim3" to "fhr-claim3",
        "frost" to "fhr-frost", "tinkle" to "fhr-tinkle", "level" to "fhr-level", "cutTing" to "fhr-cut-ting",
    )

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) ready.add(id) }
        for ((name, file) in files) {
            runCatching {
                context.assets.openFd("frostheartrewards/frostheartrewards_$file.wav").use { sounds[name] = pool.load(it, 1) }
            }
        }
    }

    fun play(name: String) {
        val id = sounds[name] ?: return
        if (id !in ready || manager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        streams[name]?.let { pool.stop(it) }
        val v = volume[name] ?: 0.6f
        streams[name] = pool.play(id, v, v, 1, 0, 1f)
    }
    fun stop() { streams.values.forEach { pool.stop(it) }; streams.clear() }
    fun close() = pool.release()
}

/** Device attitude relative to a slowly following baseline: radians of roll and pitch. */
internal class FrostHeartRewardsTilt(context: Context) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val sensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val matrix = FloatArray(9)
    private val angles = FloatArray(3)
    private var pitch = 0.0
    private var roll = 0.0
    private var basePitch = 0.0
    private var baseRoll = 0.0
    private var initialized = false
    var roll0 = 0.0
        private set
    var pitch0 = 0.0
        private set

    fun start() { sensor?.let { manager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) } }
    fun stop() = manager?.unregisterListener(this)
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        SensorManager.getOrientation(matrix, angles)
        pitch = angles[1].toDouble()
        roll = angles[2].toDouble()
    }

    fun step(dt: Double) {
        if (!initialized && (pitch != 0.0 || roll != 0.0)) { initialized = true; basePitch = pitch; baseRoll = roll }
        val kb = min(1.0, dt * 0.5)
        basePitch += (pitch - basePitch) * kb
        baseRoll += (roll - baseRoll) * kb
        val ks = 1 - exp(-dt * 10)
        roll0 += ((roll - baseRoll).coerceIn(-0.6, 0.6) - roll0) * ks
        pitch0 += ((pitch - basePitch).coerceIn(-0.6, 0.6) - pitch0) * ks
    }
}
