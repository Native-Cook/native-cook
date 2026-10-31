package com.ngocdevv.nativecook.blocks.cards.parcellockerpickup

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import kotlin.math.*

/** Prepared bundled cues and stream handles; playback follows the system's normal ringer mode. */
internal class ParcelLockerPickupAudio(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val soundPool =
        SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    private val loadedSounds = mutableSetOf<Int>()
    private val volumes =
        mapOf(
            "beep" to 0.3f,
            "latch" to 0.7f,
            "creak" to 0.35f,
            "hum" to 0.25f,
            "lift" to 0.4f,
            "thud" to 0.8f,
            "done" to 0.6f,
            "fold" to 0.4f,
        )
    private val soundIds: Map<String, Int>
    private val streams = mutableMapOf<String, Int>()

    init {
        soundPool.setOnLoadCompleteListener { _, id, status ->
            if (status == 0) loadedSounds.add(id)
        }
        soundIds =
            volumes.keys.associateWith { name ->
                context.assets.openFd("parcellockerpickup/parcellockerpickup_plp-$name.wav").use {
                    soundPool.load(it, 1)
                }
            }
    }

    /** Replaces a cue's current stream only once its sound has loaded. */
    fun play(name: String) {
        val id = soundIds[name] ?: return
        if (id !in loadedSounds || audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL)
            return
        streams[name]?.let { soundPool.stop(it) }
        val volume = volumes[name] ?: 1f
        streams[name] = soundPool.play(id, volume, volume, 1, 0, 1f)
    }

    fun close() {
        soundPool.release()
    }
}

/**
 * Event-driven pickup state. Event ages and frame deltas are seconds; door angles are degrees.
 * Autoplay samples the deterministic script, while touch integrates the same hinge springs. Modes
 * are 0 rest, 1 opening, 2 taking and 3 folding the receipt.
 */
internal class ParcelLockerPickupDriver(
    private val cue: (String) -> Unit,
    private val buzz: (Int) -> Unit,
) {
    var pose = ParcelLockerPickupMotion.Pose()
    var automatic = false
    var loopTime = 0.0
    var elapsedTime = 0.0
    var touchAt = -10.0
    var mode = 0
    var openAt = -10.0
    var takeAt = -10.0
    var doneAt = -10.0
    var velocity = 0.0
    var dragging = false
    var dragAngle = 0.0
    var takeFrom = 0.0

    /**
     * Advances one frame, using spring substeps at 240 Hz and a 2 s quiet period before autoplay.
     */
    fun step(deltaTime: Double, autoPlay: Boolean, reducedMotion: Boolean) {
        val motion = ParcelLockerPickupMotion
        val previousTime = elapsedTime
        elapsedTime += deltaTime
        if (
            autoPlay &&
                !reducedMotion &&
                !automatic &&
                mode == 0 &&
                elapsedTime - touchAt > 2 &&
                pose.angle < 0.01 &&
                pose.sheet < 0.001
        ) {
            automatic = true
            loopTime = 0.0
        }
        if (automatic) {
            loopTime = (loopTime + deltaTime) % motion.LOOP
            pose = motion.script(loopTime)
            velocity = 0.0
            return
        }
        val openAge = elapsedTime - openAt
        val takeAge = elapsedTime - takeAt
        val doneAge = elapsedTime - doneAt
        if (mode == 1) {
            if (openAge - deltaTime < 0.72 && openAge >= 0.72) {
                buzz(1)
                cue("latch")
                velocity += 120
            }
            if (openAge - deltaTime < 1.05 && openAge >= 1.05) cue("hum")
            if (openAge - deltaTime < 0.95 && openAge >= 0.95) cue("creak")
        }
        var targetAngle = 0.0
        var duration = 0.95
        var ratio = 0.7
        if (mode == 1 && openAge >= 0.95) targetAngle = 104.0
        if (mode == 2 && takeAge < 0.62) targetAngle = takeFrom
        if (mode == 2 && takeAge >= 0.62) {
            duration = 0.55
            ratio = 0.42
        }
        if (dragging) {
            targetAngle = dragAngle
            duration = 0.18
            ratio = 0.9
        }
        val stepCount = max(1, ceil(deltaTime * 240).toInt())
        val stepSeconds = deltaTime / stepCount
        val angularFrequency = 2 * PI / duration
        repeat(stepCount) {
            velocity +=
                (angularFrequency * angularFrequency * (targetAngle - pose.angle) -
                    2 * ratio * angularFrequency * velocity) * stepSeconds
            pose.angle += velocity * stepSeconds
            if (pose.angle < 0) {
                pose.angle = -pose.angle * 0.3
                if (velocity < -40) {
                    buzz(2)
                    cue("thud")
                }
                velocity = -velocity * 0.3
            }
        }
        pose.light =
            if (mode == 1 || mode == 2)
                motion.lightAt(openAge) *
                    (if (mode == 2) 1 - motion.smooth(0.77, 1.02, takeAge) else 1.0)
            else 0.0
        pose.led =
            when (mode) {
                1 -> if (openAge < 0.72) 0 else 1
                2 -> if (takeAge > 1.32 && pose.angle < 3) 2 else 1
                3 -> 2
                else -> 0
            }
        pose.parcel =
            if (mode == 2 || mode == 3) motion.easeInOut(motion.seg(takeAge, 0.0, 0.92)) else 0.0
        pose.sheet =
            when (mode) {
                2 ->
                    if (reducedMotion) {
                        if (takeAge > 0.45) 1.0 else 0.0
                    } else motion.springStep(takeAge - 0.45, 0.6, 0.86)
                3 -> 1 - motion.easeInOut(motion.seg(doneAge, 0.0, 0.5))
                else -> 0.0
            }
        pose.digits =
            when (mode) {
                1,
                2 -> motion.digitsAt(openAge)
                3 -> 4 * (1 - motion.seg(doneAge, 0.0, 0.3))
                else -> 0.0
            }
        pose.takeAge = if (mode == 2) takeAge else -10.0
        pose.fold = if (mode == 3) motion.easeInOut(motion.seg(doneAge, 0.0, 0.5)) else 0.0
        pose.press = max(0.0, pose.press - deltaTime * 6)
        if (mode == 2 && takeAge - deltaTime < 0.45 && takeAge >= 0.45) {
            buzz(3)
            cue("done")
        }
        if (mode == 3 && doneAge > 0.55) {
            mode = 0
            openAt = -10.0
            takeAt = -10.0
        }
        if (mode == 1 && floor(pose.digits) != floor(motion.digitsAt(previousTime - openAt))) {
            buzz(0)
            cue("beep")
        }
    }

    /** Reconstructs event timestamps from the current script beat so touch takes over in place. */
    fun interrupt() {
        touchAt = elapsedTime
        if (!automatic) return
        automatic = false
        when {
            loopTime >= 8.4 && loopTime < 9.2 -> {
                mode = 3
                doneAt = elapsedTime - (loopTime - 8.4)
                takeAt = elapsedTime - (loopTime - 4)
            }
            loopTime >= 4 && loopTime < 9.2 -> {
                mode = 2
                takeAt = elapsedTime - (loopTime - 4)
                openAt = elapsedTime - (loopTime - 1.4)
                takeFrom = ParcelLockerPickupMotion.openAngle(2.6)
            }
            loopTime >= 1.4 && loopTime < 9.2 -> {
                mode = 1
                openAt = elapsedTime - (loopTime - 1.4)
            }
            else -> mode = 0
        }
    }

    /** Starts the code/latch/swing sequence from rest. */
    fun open() {
        interrupt()
        if (mode == 0) {
            mode = 1
            openAt = elapsedTime
            pose.press = 1.0
        }
    }

    /** Begins parcel flight only once the open door clears the pickup threshold. */
    fun take() {
        if (mode == 1 && elapsedTime - openAt > 1.05 && pose.angle > 60) {
            mode = 2
            takeAt = elapsedTime
            takeFrom = pose.angle
            cue("lift")
        }
    }

    /** Starts the receipt fold and plays the closing cue. */
    fun done() {
        interrupt()
        if (mode == 2) {
            mode = 3
            doneAt = elapsedTime
        }
        cue("fold")
    }
}
