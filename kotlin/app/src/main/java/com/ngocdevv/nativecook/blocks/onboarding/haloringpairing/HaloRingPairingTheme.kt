package com.ngocdevv.nativecook.blocks.onboarding.haloringpairing

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface as ComposeTypeface
import kotlin.math.*

internal object HaloRingPairingTheme {
    val page = Color(0xFF06080A)
    val ink = Color(0xFFF2F4F3)
    val mute = Color(0xFF8A918E)
    val card = Color(0xFF121618)
    val line = Color(0xFF20262A)
    val green = Color(0xFF3BE38A)
    val greenInk = Color(0xFF04140B)
    val stats = listOf(Triple("Readiness","86",""),Triple("Sleep","7h 42m",""),Triple("Resting heart","58","bpm"),Triple("Steps","4,210",""))
    fun font(context: Context,weight: String): FontFamily = FontFamily(ComposeTypeface(
        Typeface.createFromAsset(context.assets,"haloringpairing/haloringpairing_Inter-$weight.ttf")))
}

internal data class HaloRingPairingLayout(val w: Double,val h: Double,val top: Double,val bottom: Double) {
    val stageY get() = top+floor((h-top-bottom)*0.33+0.5)
    val titleTop get() = stageY+168
    val sizesTop get() = titleTop+84
    val buttonTop get() = h-max(bottom,16.0)-8-56
    val chipX get() = 42.0
    val chipY get() = top+32
}
