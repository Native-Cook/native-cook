package com.ngocdevv.nativecook.blocks.buttons.tapearncoin

internal data class TapEarnCoinLayout(val w: Double,val h: Double,val top: Double,val bottom: Double) {
    val s=w/720
    val titleTop=top+52
    private fun at(y: Double)=titleTop+(y-70)*s
    val subtitleTop=at(212.0)-5
    val coinX=w/2;val coinY=at(560.0);val coinR=220*s
    val digitsTop=at(777.0);val digitW=84*s;val digitH=100*s;val digitGap=15*s
    val ctaH=56.0;val ctaTop=h-bottom-12-ctaH;val side=36*s;val labelTop=ctaTop-40
    val sheetH=398+bottom;val sheetTop=h-sheetH;val bellSize=99*s
    val bellX=50*s+bellSize/2;val bellY=sheetTop+64*s+bellSize/2
    val digitLeft=w/2-(3*digitW+3*digitGap+18)/2
    fun digitX(i: Int)=digitLeft+digitW/2+i*(digitW+digitGap)
}
internal object TapEarnCoinColor {
    const val TITLE=0xffec6e0c.toInt();const val INK=0xff191b21.toInt()
    const val UNIT=0xff3f444c.toInt();const val DIGIT=0xfff28d0e.toInt();const val BOX=0xfffef2dc.toInt()
    const val LABEL=0xffbf5700.toInt();const val CTA=0xfff56d01.toInt();const val CTA_DOWN=0xffde6200.toInt()
    const val SHEET_TITLE=0xff191f28.toInt();const val BODY=0xff6b7684.toInt();const val ROW=0xff4e5968.toInt()
    const val BLUE=0xff3182f6.toInt();const val BLUE_DOWN=0xff2272eb.toInt()
}
