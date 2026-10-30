package com.ngocdevv.nativecook.blocks.gestures.cabinclimatecar

import android.graphics.Color
import android.graphics.RectF
import kotlin.math.abs

internal object CabinClimateCarTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0
    const val S = 402.0/720
    const val bgTop = 0xD6DEF3; const val bg = 0xE9ECF1; const val card = 0xFFFFFF
    const val ink = 0x0C0E1A; const val label = 0x4C5361; const val mute = 0x757479
    const val pill = 0x0C0D21; const val teal = 0x22D3A0; const val green = 0x00E09A
    const val blue = 0x2563EB; const val chip = 0xE9F7C2; const val tabSel = 0xE6E8ED
    const val seat = 0x25272C; const val seatHi = 0x4A4D55
    const val cold = 0x3B82F6; const val mild = 0x22C7A9; const val warm = 0xF97316
    val temp = RectF(30f,580f,352f,1070f)
    val legroom = RectF(369f,580f,690f,815f)
    val fan = RectF(369f,833f,690f,1070f)
    fun color(rgb: Int,alpha: Double = 1.0) = Color.argb((alpha.coerceIn(0.0,1.0)*255).toInt(),
        (rgb shr 16) and 255,(rgb shr 8) and 255,rgb and 255)
    fun air(heat: Double,alpha: Double = 1.0): Int {
        val k = (heat-70)/10
        val other = if (k < 0) cold else warm
        val amount = CabinClimateCarMotion.clamp01(abs(k)*2.2)
        fun channel(shift: Int) = CabinClimateCarMotion.mix(
            ((mild shr shift) and 255).toDouble(),((other shr shift) and 255).toDouble(),amount).toInt()
        return Color.argb((alpha.coerceIn(0.0,1.0)*255).toInt(),channel(16),channel(8),channel(0))
    }
}
