package com.ngocdevv.nativecook.blocks.modals.gemcutpaywall

import android.graphics.Color
import kotlin.math.min

internal object GemCutPaywallTheme {
    const val PAGE = 0xFF07060C.toInt()
    const val SURFACE = 0xFF15131F.toInt()
    const val LINE = 0xFF262236.toInt()
    const val INK = 0xFFF5F3FA.toInt()
    const val MUTE = 0xFF8F8AA3.toInt()
    const val THUMB = 0xFFE9E4FF.toInt()
    const val THUMB_INK = 0xFF140F26.toInt()
    const val CTA_INK = 0xFF0B0914.toInt()
    val names=listOf("Free","Plus","Pro")
    val prices=listOf("0.00","4.99","9.99")
    val notes=listOf("Five edits a day, forever.","Then $4.99 a month. Cancel anytime.","Then $9.99 a month. Cancel anytime.")
    val features=listOf("Unlimited edits","4K and RAW export","AI relight and retouch")
    data class Layout(val w: Double,val h: Double,val barTop: Double,val gemX: Double,val gemY: Double,val gemR: Double,val floorY: Double,val titleTop: Double,val priceTop: Double,val planTop: Double,val featTop: Double,val noteTop: Double,val ctaTop: Double) {
        val planX=16.0;val planW get()=w-32;val segW get()=(planW-8)/3;val thumbRun get()=planW-8-segW
    }
    fun layout(w: Double,h: Double,top: Double,bottom: Double): Layout {
        val cta=h-bottom-8-56;val note=cta-12-18;val feat=note-24-96;val plan=feat-24-48;val price=plan-24-22;val title=price-4-34
        val bar=top+8;val radius=min(w*0.36,160.0);val room=bar+44;val gy=room+(title-room)*0.42
        return Layout(w,h,bar,w/2,gy,radius,gy+radius*0.86,title,price,plan,feat,note,cta)
    }
}
