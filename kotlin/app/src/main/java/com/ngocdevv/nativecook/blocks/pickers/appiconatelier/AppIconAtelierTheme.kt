package com.ngocdevv.nativecook.blocks.pickers.appiconatelier

import kotlin.math.*

internal object AppIconAtelierTheme {
    data class Glass(val name:String,val note:String,val tint:DoubleArray,val density:Double,val frost:Double,val film:Double,val glow:Double,val light:Int,val deep:Int)
    val glasses=listOf(
        Glass("Lilac Glass","The original. Clear, cool, a touch of violet.", doubleArrayOf(.58,.4,.98),1.1,0.0,.55,0.0,0xE4DAFB,0x9B7BEA),
        Glass("Pearl","Milky glass with a mother-of-pearl sheen.",doubleArrayOf(.97,.95,.99),.6,.75,1.0,0.0,0xFFFFFF,0xD9D3E6),
        Glass("Mint Frost","Sandblasted glass the colour of sea mist.",doubleArrayOf(.6,.92,.8),1.0,.6,.3,0.0,0xE2F7EE,0x7FCDB0),
        Glass("Amber","Warm honey glass, poured thick.",doubleArrayOf(1.0,.72,.32),1.5,.05,.25,0.0,0xFCE6C4,0xE39A3B),
        Glass("Smoke","Dark glass, with the heart lit from within.",doubleArrayOf(.36,.33,.46),2.0,.1,.6,.8,0x8C849E,0x2B2638)
    )
    data class Layout(val w:Double,val h:Double,val top:Double,val bottom:Double) {
        val ctaTop=h-max(bottom,16.0)-8-56
        val cardY=ctaTop-24-168
        val homeLabelTop=cardY-30
        val chipsTop=homeLabelTop-32-52
        val noteTop=chipsTop-44
        val nameTop=noteTop-46
        val eyebrowTop=nameTop-20
        val room0=top+44
        val room=eyebrowTop-room0
        val heroX=w/2
        val heroS=minOf(w*.25,room*.36,104.0)
        val heroY=room0+room*.44
        val cardX=16.0
        val cardW=w-32
        val appsY=cardY+86
        val appX=DoubleArray(4) { 74+it*(60+(cardW-56-240)/3) }
        val chipsLeft=(w-324)/2
        fun chip(i:Double)=chipsLeft+26+i*68
        fun flight(p:Double):Pair<Double,Double> { val u=1-p; return Pair(u*u*heroX+2*u*p*(heroX+w*.3)+p*p*appX[2],u*u*heroY+u*p*(heroY+appsY)+p*p*appsY) }
    }
}
