package com.ngocdevv.nativecook.blocks.cards.glassorbshelf

import kotlin.math.min

internal class GlassOrbShelfLayout(val w: Float, top: Float) {
    val headerTop=top+12f
    val orbR=min(172f,w*0.42f)
    val orbX=w/2
    val orbY=headerTop+44+24+orbR
    val cardW=min(300f,w-102)
    val cardLeft=(w-cardW)/2
    val cardTop=orbY+orbR+44
    val cardH=196f
    val radius=26f
    val step=cardW+14
    val dotsTop=cardTop+cardH+18
}
