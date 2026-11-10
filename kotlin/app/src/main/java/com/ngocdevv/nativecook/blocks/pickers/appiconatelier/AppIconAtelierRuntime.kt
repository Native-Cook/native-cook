package com.ngocdevv.nativecook.blocks.pickers.appiconatelier

import android.graphics.*
import androidx.annotation.RequiresApi

@RequiresApi(33)
internal class AppIconAtelierRuntime(wall:Bitmap) {
    val shader=RuntimeShader(AppIconAtelierShader.source)
    private val image=BitmapShader(wall,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP)
    private val width=wall.width.toDouble()
    private val height=wall.height.toDouble()
    private val matrix=Matrix()
    fun update(L:AppIconAtelierTheme.Layout,f:AppIconAtelierMotion.Frame,density:Double):Shader {
        val M=AppIconAtelierMotion
        fun u(name:String,vararg v:Double){shader.setFloatUniform(name,v.map{it.toFloat()}.toFloatArray())}
        val A=AppIconAtelierTheme.glasses[f.a];val B=AppIconAtelierTheme.glasses[f.b];val S=AppIconAtelierTheme.glasses[f.applied]
        fun g(prefix:String,g:AppIconAtelierTheme.Glass){u(prefix+"0",*g.tint,g.density);u(prefix+"1",g.frost,g.film,g.glow,0.0)}
        val scale=maxOf(L.cardW/width,168/height)
        matrix.setScale(scale.toFloat(),scale.toFloat());matrix.postTranslate((L.cardX+(L.cardW-width*scale)/2).toFloat(),(L.cardY+(168-height*scale)/2).toFloat());image.setLocalMatrix(matrix);shader.setInputShader("uWall",image)
        val pk=M.clamp(f.peek);val at=L.flight(f.peek);val size=(L.heroS+(31.5-L.heroS)*f.peek)*(1-.04*f.press)
        val k=M.progress(f.front,f.dir);val tint=DoubleArray(3){A.tint[it]+(B.tint[it]-A.tint[it])*k}
        val smooth=M.seg(f.peek,.7,.95)
        u("uRes",L.w,L.h);u("uCard",L.cardX,L.cardY,L.cardW,168.0);u("uCardR",28.0)
        u("uC",at.first,at.second-f.bob*size);u("uS",size);u("uPose",-.42+f.lean+(-.06+.42-f.lean)*pk,-.26+.21*pk,-.12+.12*pk);u("uSquash",1+f.land,1-f.land)
        g("uA",A);g("uB",B);u("uFront",f.front);u("uDir",f.dir);u("uMen",M.meniscus(f.front)*(if(f.a==f.b).6 else 1.0));u("uPhase",f.phase)
        u("uFloor",at.first,at.second+size*(1.32+(1.14-1.32)*pk));u("uShadow",(1-.3*pk)*(1-3*f.bob));u("uShadowTint",*tint)
        u("uSC",L.appX[2],L.appsY);u("uSS",31.5);u("uSOn",1-smooth*smooth*(3-2*smooth));u("uSPop",f.pop);g("uS",S);u("uPx",1/density)
        return shader
    }
}
