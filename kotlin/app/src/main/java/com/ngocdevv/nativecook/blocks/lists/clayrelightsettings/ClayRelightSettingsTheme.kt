package com.ngocdevv.nativecook.blocks.lists.clayrelightsettings

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import kotlin.math.pow

internal object ClayRelightSettingsTheme {
    const val SCENE_H = 232f
    const val BAND_H = 76f
    const val ROW_H = 60f
    const val TILE = 40f
    const val ICON_COL = 64f
    val rows = listOf(
        Triple("Notifications","Allowed","Silenced"), Triple("Focus","Off","Sleep"),
        Triple("Display & Brightness","Light","Dark"), Triple("Battery","86%","Charging"),
        Triple("Privacy & Security","",""),
    )
    private val palette = mapOf(
        "page" to listOf(0xEEF0F4,0xF4E6DC,0x0B0E19), "card" to listOf(0xFFFFFF,0xFFF8F2,0x161B2B),
        "ink" to listOf(0x14161C,0x2B1E1A,0xEEF0F7), "mute" to listOf(0x646A79,0x7A6058,0x8F95AA),
        "line" to listOf(0xE7E9EE,0xF1E2D8,0x232A3E), "trackOff" to listOf(0xD9DCE3,0xE9D6CB,0x2B3247),
        "pill" to listOf(0xFFFFFF,0xFFF4EB,0x0E1222), "pillInk" to listOf(0x1E2230,0x3A2620,0xE6E9F5),
    )
    fun tone(key: String, m: Double, theme: Double): Color {
        val p = palette.getValue(key)
        val k = (m*2).coerceIn(0.0,1.0)
        val th = theme.coerceIn(0.0,1.0)
        fun channel(shift: Int): Float {
            val a = ((p[0] shr shift) and 255)/255.0
            val b = ((p[1] shr shift) and 255)/255.0
            val c = ((p[2] shr shift) and 255)/255.0
            val lit = a.pow(2.2)*(1-k)+b.pow(2.2)*k
            return (lit*(1-th)+c.pow(2.2)*th).pow(1/2.2).toFloat()
        }
        return Color(channel(16),channel(8),channel(0),if (key == "pill") (0.72*(1-th)+0.62*th).toFloat() else 1f)
    }
    fun fonts(context: Context) = listOf("Regular","Medium","SemiBold").map {
        Typeface.createFromAsset(context.assets,"clayrelightsettings/clayrelightsettings_Inter-$it.ttf")
    }
    fun track(off: Color, x: Double): Color {
        fun c(a: Float, b: Float) = (a.toDouble().pow(2.2)*(1-x)+b.toDouble().pow(2.2)*x).pow(1/2.2).toFloat()
        return Color(c(off.red,91/255f),c(off.green,91/255f),c(off.blue,214/255f))
    }
}
