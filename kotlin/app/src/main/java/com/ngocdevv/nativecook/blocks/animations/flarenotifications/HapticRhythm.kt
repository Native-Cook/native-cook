package com.ngocdevv.nativecook.blocks.animations.flarenotifications

import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import com.ngocdevv.nativecook.blocks.animations.flarenotifications.platform.Haptics
import kotlin.random.Random

/**
 * A cancellable run of selection ticks (haptic-rhythm.ts). Each gap is the
 * previous one times `stretch`, plus up to `jitter` ms, so the rhythm can
 * speed up, slow down or crackle irregularly. `delay` holds the first tick
 * back, so a press can turn into a hold before anything plays. Timers run on
 * the main looper, as `setTimeout` does on the JS thread.
 */
@RequiresApi(Build.VERSION_CODES.S)
class HapticRhythm(private val haptics: Haptics) {
    private val handler = Handler(Looper.getMainLooper())
    private var timer: Runnable? = null

    fun stop() {
        timer?.let(handler::removeCallbacks)
        timer = null
    }

    fun play(first: Double, stretch: Double, jitter: Double = 0.0, total: Double, delay: Double = 0.0) {
        stop()
        var left = total
        fun step(gap: Double) {
            haptics.selection()
            val wait = gap + Random.nextDouble() * jitter
            left -= wait
            timer = if (left > 0) schedule(wait) { step(gap * stretch) } else null
        }
        if (delay > 0) timer = schedule(delay) { step(first) } else step(first)
    }

    private fun schedule(ms: Double, block: () -> Unit): Runnable {
        val r = Runnable { block() }
        handler.postDelayed(r, ms.toLong())
        return r
    }
}
