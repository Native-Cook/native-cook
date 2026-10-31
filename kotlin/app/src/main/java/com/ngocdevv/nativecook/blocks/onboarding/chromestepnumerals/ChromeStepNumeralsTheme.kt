package com.ngocdevv.nativecook.blocks.onboarding.chromestepnumerals

import android.content.Context
import android.graphics.Typeface
import kotlin.math.*

internal object ChromeStepNumeralsTheme {
    const val page=0xFFEEEDEA.toInt()
    const val ink=0xFF1E1C1A.toInt()
    const val mute=0xFF8A847C.toInt()
    const val accent=0xFFC08A57.toInt()
    const val dot=0xFFD4CFC8.toInt()
    val titles=listOf("Link your bank","Pick a goal","Watch it grow")
    val lines=listOf("Connect an account in a minute. We only ever read balances.",
        "A trip, a bike, a rainy-day fund — name it and set a date.",
        "Round-ups and payday transfers fill it without thinking.")
    fun font(context: Context,face: String): Typeface = Typeface.createFromAsset(context.assets,
        "chromestepnumerals/chromestepnumerals_Inter-$face.ttf")
    data class Layout(val w: Double,val h: Double,val top: Double,val bottom: Double) {
        val unit=floor(w*0.3+0.5)
        val cy=top+floor((h-top-bottom)*0.33+0.5)
        val floorY=cy+unit*1.15
        val titleTop=cy+unit*1.45
        val buttonTop=h-max(bottom,16.0)-8-52
        val dotsTop=buttonTop-40
    }
}
