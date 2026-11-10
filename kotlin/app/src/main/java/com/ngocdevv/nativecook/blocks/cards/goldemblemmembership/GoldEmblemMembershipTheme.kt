package com.ngocdevv.nativecook.blocks.cards.goldemblemmembership

import kotlin.math.max

internal object GoldEmblemMembershipTheme {
    data class Tier(val name: String, val range: String, val next: String, val ink: Int, val sub: Int, val coupon: String, val sliver: Int)
    val tiers=listOf(
        Tier("Gold member","100,000 < assets < 300,000","Platinum is $80,889.00 away",0xff5a4426.toInt(),0xff6e5634.toInt(),"1",0xffb8b9bc.toInt()),
        Tier("Platinum member","300,000 < assets < 800,000","Black is $412,560.00 away",0xff33414a.toInt(),0xff4e5d66.toInt(),"1.5",0xffc9b08a.toInt()),
        Tier("Black member","assets > 800,000","The highest tier",0xfff0cc86.toInt(),0xffc9ae7c.toInt(),"2",0xff9fb0b8.toInt())
    )
    data class Layout(val w: Float, val h: Float, val safeTop: Float, val safeBottom: Float) {
        val top=max(safeTop,44f)
        val sx=w/402
        val k=(h-top-max(safeBottom,20f))/(874-62-34)
        fun y(y: Float)=if(y<62)y*top/62 else top+(y-62)*k
        val cardX=17*sx
        val cardY=y(103f)
        val cardW=367*sx
        val cardH=185*k
    }
}
