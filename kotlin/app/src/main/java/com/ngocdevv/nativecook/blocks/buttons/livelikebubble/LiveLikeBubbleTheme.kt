package com.ngocdevv.nativecook.blocks.buttons.livelikebubble

import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.graphics.Typeface

internal class LiveLikeBubbleTheme(context: Context) {
    val regular: Typeface = Typeface.createFromAsset(context.assets,"livelikebubble/livelikebubble_Inter-Regular.ttf")
    val medium: Typeface = Typeface.createFromAsset(context.assets,"livelikebubble/livelikebubble_Inter-Medium.ttf")
    val semibold: Typeface = Typeface.createFromAsset(context.assets,"livelikebubble/livelikebubble_Inter-SemiBold.ttf")
    companion object {
        val page = Color.rgb(233,233,238)
        val ink = Color.rgb(22,22,27)
        val mute = Color.rgb(111,111,122)
        val live = Color.rgb(255,46,85)
        val line = Color.rgb(217,217,224)
        val heart = Path().apply {
            moveTo(12f,21f)
            cubicTo(12f,21f,3.5f,15.7f,1.4f,10.6f)
            cubicTo(-0.1f,6f,3f,1.5f,7.1f,1.5f)
            cubicTo(9.3f,1.5f,10.9f,2.7f,12f,4.3f)
            cubicTo(13.1f,2.7f,14.7f,1.5f,16.9f,1.5f)
            cubicTo(21f,1.5f,24.1f,6f,22.6f,10.6f)
            cubicTo(20.5f,15.7f,12f,21f,12f,21f)
            close()
        }
    }
}
