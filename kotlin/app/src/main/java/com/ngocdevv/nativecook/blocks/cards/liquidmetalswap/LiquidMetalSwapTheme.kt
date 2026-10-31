package com.ngocdevv.nativecook.blocks.cards.liquidmetalswap

import kotlin.math.max

internal data class LiquidMetalSwapToken(val symbol: String,val name: String,val amount: String,val fiat: String,val balance: String,val tint: FloatArray,val colour: Int)
internal object LiquidMetalSwapTheme {
    const val PAGE=0xFF0B0C10.toInt(); const val CARD=0xFF15171D.toInt()
    const val INK=0xFFF2F3F5.toInt(); const val MUTE=0xFF8A8F9C.toInt(); const val FAINT=0xFF5C616D.toInt()
    const val SILVER=0xFFE6E9EF.toInt(); const val GOLD=0xFFE8C27A.toInt(); const val EDGE=0x0FFFFFFF
    const val CARD_H=112.0; const val CARD_GAP=8.0; const val ROW_H=36.0; const val BUTTON_H=56.0
    val tokens=listOf(
        LiquidMetalSwapToken("PRSM","Prism","250","$412.50","Balance 1,820",floatArrayOf(0.8f,0.8f,0.79f),SILVER),
        LiquidMetalSwapToken("SPRK","Spark","1,204.83","$412.17","Balance 96.4",floatArrayOf(1f,0.74f,0.36f),GOLD))
    val rates=listOf("1 PRSM = 4.8193 SPRK","1 SPRK = 0.2075 PRSM")
}
internal data class LiquidMetalSwapLayout(val w: Double,val h: Double,val top: Double,val bottom: Double) {
    val side=16.0; val cardW=w-2*side
    val barTop=top+4; val stageTop=barTop+44
    val buttonTop=h-max(bottom,16.0)-8-56
    val rowsTop=buttonTop-16-3*36
    val cardsTop=rowsTop-16-(2*112+8)
    val stageH=max(200.0,cardsTop-8-stageTop)
}
