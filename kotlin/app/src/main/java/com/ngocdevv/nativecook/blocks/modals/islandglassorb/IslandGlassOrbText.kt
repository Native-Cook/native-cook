package com.ngocdevv.nativecook.blocks.modals.islandglassorb

import android.content.res.AssetManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/** Font layout at 16× avoids Android's small-size glyph advance rounding. */
internal class IslandGlassOrbText(assets: AssetManager) {
    private val regular=Typeface.createFromAsset(assets,"islandglassorb/islandglassorb_Inter-Regular.ttf")
    private val medium=Typeface.createFromAsset(assets,"islandglassorb/islandglassorb_Inter-Medium.ttf")
    private val semibold=Typeface.createFromAsset(assets,"islandglassorb/islandglassorb_Inter-SemiBold.ttf")
    private val display=Typeface.createFromAsset(assets,"islandglassorb/islandglassorb_Inter-Display-Light.ttf")
    private val paints=mutableMapOf<String,Paint>()
    private fun paint(size: Double,weight: Int,displayCut: Boolean,tnum: Boolean): Paint {
        return paints.getOrPut("$size/$weight/$displayCut/$tnum") {
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
                typeface=if(displayCut)display else if(weight>=600)semibold else if(weight>=500)medium else regular
                textSize=(size*16).toFloat(); color=Color.rgb(247,247,245)
                hinting=Paint.HINTING_OFF
                if(tnum)fontFeatureSettings="tnum"
            }
        }
    }
    fun width(text: String,size: Double,weight: Int=400,track: Double=0.0,display: Boolean=false,tnum: Boolean=false): Double {
        return paint(size,weight,display,tnum).measureText(text)/16.0+(text.length-1).coerceAtLeast(0)*track*size
    }
    fun baselineOffset(size: Double,weight: Int=400): Double {
        val fm=paint(size,weight,false,false).fontMetrics
        return -(fm.ascent+fm.descent)/32.0
    }
    fun draw(c: Canvas,text: String,x: Double,cy: Double,size: Double,weight: Int=400,alpha: Double=1.0,track: Double=0.0,display: Boolean=false,tnum: Boolean=false,center: Boolean=false) {
        if(alpha<=0)return
        val p=paint(size,weight,display,tnum); p.alpha=(alpha.coerceIn(0.0,1.0)*255).toInt()
        val fm=p.fontMetrics; val baseline=cy*16-(fm.ascent+fm.descent)/2
        var left=x*16-(if(center)width(text,size,weight,track,display,tnum)*8 else 0.0)
        val save=c.save(); c.scale(1/16f,1/16f)
        if(track==0.0)c.drawText(text,left.toFloat(),baseline.toFloat(),p)
        else {
            val advances=FloatArray(text.length); p.getTextWidths(text,advances)
            for(i in text.indices) { c.drawText(text,i,i+1,left.toFloat(),baseline.toFloat(),p); left+=advances[i]+track*size*16 }
        }
        c.restoreToCount(save)
    }
}
