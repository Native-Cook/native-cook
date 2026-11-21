package com.ngocdevv.nativecook.blocks.modals.islandglassorb

import android.graphics.*
import androidx.annotation.RequiresApi
import kotlin.math.*

/** Android's native shader consumes the exact page snapshot in reference point coordinates. */
@RequiresApi(33)
internal class IslandGlassOrbRuntime(page: IslandGlassOrbPage) {
    private val effect=RuntimeShader(IslandGlassOrbShader.source)
    private val pageHeight=page.height
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { shader=effect }
    init {
        val clamp=Shader.TileMode.CLAMP
        val pageShader=BitmapShader(page.image,clamp,clamp).apply { setLocalMatrix(Matrix().apply { setScale((1/page.scale).toFloat(),(1/page.scale).toFloat()) }); filterMode=BitmapShader.FILTER_MODE_LINEAR }
        val blurShader=BitmapShader(page.blurred,clamp,clamp).apply { filterMode=BitmapShader.FILTER_MODE_LINEAR }
        effect.setInputShader("uPage",pageShader); effect.setInputShader("uBlur",blurShader)
        effect.setFloatUniform("uN",2.6f); effect.setFloatUniform("uRefr",1f)
    }
    fun draw(c: Canvas,f: IslandGlassOrbFrame,oy: Double,time: Double) {
        val m=IslandGlassOrbMotion; val s=m.shapes(f,oy); val mc=m.clamp(f.m)
        val ny=11+oy+26; val mid=f.neck*mc/2
        effect.setFloatUniform("uN",m.mix(2.6,2.0,f.free).toFloat())
        effect.setFloatUniform("uBox",s.x.toFloat(),s.y.toFloat(),s.w.toFloat(),s.h.toFloat())
        effect.setFloatUniform("uDrop",s.cx.toFloat(),s.cy.toFloat(),max(s.a,1.0).toFloat(),max(s.b,1.0).toFloat())
        effect.setFloatUniform("uM",max(f.m,0.0).toFloat())
        effect.setFloatUniform("uK",(f.kJoin*m.smooth(0.0,.25,f.m)).toFloat())
        effect.setFloatUniform("uNeck",201f,ny.toFloat(),(if(mid>.05)mid*1.2+1 else 0.0).toFloat(),mid.toFloat())
        effect.setFloatUniform("uCap",f.cap.toFloat()); effect.setFloatUniform("uFree",f.free.toFloat())
        effect.setFloatUniform("uBan",m.clamp(f.ban).toFloat())
        effect.setFloatUniform("uAmp",f.amp.toFloat()); effect.setFloatUniform("uAmpLag",f.ampLag.toFloat())
        effect.setFloatUniform("uLine",f.lineX.toFloat(),(f.lineY+oy).toFloat(),(f.lineLen*(1+.08*f.amp)).toFloat(),3f)
        effect.setFloatUniform("uT",time.toFloat()); effect.setFloatUniform("uPluck",f.pluckX.toFloat(),f.pluckD.toFloat())
        effect.setFloatUniform("uArc",f.arcA.toFloat(),f.arcW.toFloat(),f.arcHue.toFloat())
        effect.setFloatUniform("uRim",(.3*mc*mc).toFloat(),maxOf(.88*mc,.25*m.clamp(f.pill)*(1-m.clamp(f.ban)),.45*m.clamp(f.ban)).toFloat())
        effect.setFloatUniform("uShade",(m.mix(.25*mc,.22*mc,f.free)+.22*m.clamp(f.ban)).toFloat(),m.mix(m.mix(6.0,8.0,m.clamp(f.ban)),10.0,f.free).toFloat(),m.mix((.1+.25*m.clamp(f.causticAmp))*mc,.28*(.85+.3*m.clamp(f.causticAmp))*mc,f.free).toFloat())
        effect.setFloatUniform("uShade2",m.mix(m.mix(18.0,24.0,m.clamp(f.ban)),26.0,f.free).toFloat(),m.mix(30.0,40.0,f.free).toFloat(),m.mix(5.0,7.0,f.free).toFloat())
        c.drawRect(0f,0f,402f,pageHeight.toFloat(),paint)
    }
}
