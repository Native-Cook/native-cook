package com.ngocdevv.nativecook.blocks.cards.goldemblemmembership

import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi

@RequiresApi(33)
internal class GoldEmblemMembershipRuntime {
    val shader=RuntimeShader(GoldEmblemMembershipShader.source)
    fun update(l: GoldEmblemMembershipTheme.Layout, f: GoldEmblemMembershipMotion.Frame, now: Double, reduced: Boolean) {
        shader.setFloatUniform("uView",l.w,l.cardH+60,1f,(now%600).toFloat())
        shader.setFloatUniform("uCard",l.cardX,10f,l.cardW,l.cardH)
        shader.setFloatUniform("uTier",f.from.toFloat(),f.to.toFloat(),if(reduced)0f else f.cast.toFloat(),f.side.toFloat())
        shader.setFloatUniform("uLight",(f.drag/60).toFloat(),0f,0f,0f)
    }
}
