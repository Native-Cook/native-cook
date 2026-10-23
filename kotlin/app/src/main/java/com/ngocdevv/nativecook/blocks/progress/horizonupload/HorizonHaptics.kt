// Cached Android equivalents of Expo's light/soft/medium impacts and success notification.
package com.ngocdevv.nativecook.blocks.progress.horizonupload

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

internal class HorizonHaptics(context: Context) {
    @Suppress("DEPRECATION")
    private val vibrator = if (Build.VERSION.SDK_INT >= 31)
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    else context.getSystemService(Vibrator::class.java)
    private val effects = arrayOf(
        VibrationEffect.createOneShot(35, 45),
        VibrationEffect.createOneShot(25, 25),
        VibrationEffect.createOneShot(45, 75),
        VibrationEffect.createWaveform(longArrayOf(0, 40, 100, 40), intArrayOf(0, 50, 0, 60), -1),
    )
    fun play(kind: Int) { try { vibrator?.vibrate(effects[kind]) } catch (_: SecurityException) { /* Optional hardware. */ } }
    companion object { const val LIGHT = 0; const val SOFT = 1; const val MEDIUM = 2; const val SUCCESS = 3 }
}
