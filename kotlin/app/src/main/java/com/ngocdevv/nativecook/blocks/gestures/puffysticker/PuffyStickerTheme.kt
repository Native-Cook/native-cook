package com.ngocdevv.nativecook.blocks.gestures.puffysticker

import kotlin.math.min

internal object PuffyStickerTheme {
    const val PAGE=0xFF0E0E12.toInt(); const val SURFACE=0xFF1B1B21.toInt(); const val ON=0xFF2A2A33.toInt()
    const val INK=0xFFF4F4F6.toInt(); const val MUTE=0xFF8C8C96.toInt(); const val ACCENT=0xFFFFD43B.toInt()
    val labels=listOf("Heart","Star","Cloud","Smiley")
    val colours=intArrayOf(0xFFFF3D6E.toInt(),0xFFFFC21A.toInt(),0xFF4FB3FF.toInt(),0xFFFF8A1F.toInt())
    data class Layout(val w: Float,val h: Float,val top: Float,val bottom: Float) {
        val sticker=min(w*.36f,150f);val trayTop=h-bottom-88f;val switchTop=trayTop-64f
        val barTop=top+8;val stageX=w/2;val stageY=(barTop+44+switchTop)/2-18
        val hintTop=stageY+sticker*1.12f+20;val trayLeft=w/2-152
    }
}
