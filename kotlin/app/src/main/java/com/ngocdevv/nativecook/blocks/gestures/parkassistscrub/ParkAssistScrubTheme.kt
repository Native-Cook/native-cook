package com.ngocdevv.nativecook.blocks.gestures.parkassistscrub

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import kotlin.math.pow

internal object ParkAssistScrubTheme {
    val page = Color(0xFF0E0F12)
    val ink = Color(0xFFF2F3F5)
    val mute = Color(0xFF8B8E98)
    val card = Color(0xFF1A1C21)
    val line = Color(0xFF2A2D33)
    val accent = Color(0xFFFF8A3D)
    val green = Color(0xFF4BDC86)
    val amber = Color(0xFFFFC23D)
    val red = Color(0xFFFF4D45)
    fun font(context: Context, style: String) = Typeface.createFromAsset(context.assets,
        "parkassistscrub/parkassistscrub_Inter-$style.ttf")
    fun gapColor(gap: Double): Color {
        val warn = ParkAssistScrubMotion.clamp(1-gap/1.2)
        val stops = doubleArrayOf(0.0,0.35,0.7,1.0)
        val colors = arrayOf(doubleArrayOf(242.0,243.0,245.0),doubleArrayOf(75.0,220.0,134.0),
            doubleArrayOf(255.0,194.0,61.0),doubleArrayOf(255.0,77.0,69.0))
        for (i in 1..3) if (warn <= stops[i]) {
            val u = (warn-stops[i-1])/(stops[i]-stops[i-1])
            val c = (0..2).map { ((colors[i-1][it]/255).pow(2.2)*(1-u)+(colors[i][it]/255).pow(2.2)*u).pow(1/2.2).toFloat() }
            return Color(c[0],c[1],c[2])
        }
        return red
    }
}
