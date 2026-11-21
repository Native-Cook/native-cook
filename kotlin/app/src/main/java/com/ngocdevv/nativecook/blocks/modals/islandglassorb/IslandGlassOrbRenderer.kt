package com.ngocdevv.nativecook.blocks.modals.islandglassorb

import android.graphics.*
import android.os.Build
import androidx.core.graphics.PathParser
import kotlin.math.*

/** Static page plus the reference's moving row, rims, light spill, status, one SDF, spinner and six words. */
internal class IslandGlassOrbRenderer(private val page: IslandGlassOrbPage,reply: String) {
    private val m=IslandGlassOrbMotion
    private val white=Color.rgb(247,247,245)
    private val bitmapPaint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val runtime=if(Build.VERSION.SDK_INT>=33)IslandGlassOrbRuntime(page) else null
    private val relight=if(Build.VERSION.SDK_INT>=33)IslandGlassOrbRelight(page.assets) else null
    private val fallback=IslandGlassOrbFallback(page)
    private val wifi=requireNotNull(PathParser.createPathFromPathData("M324.64 25.64A9 9 0 0 1 337.36 25.64M326.97 27.97A5.7 5.7 0 0 1 335.03 27.97"))
    private val spinner=IslandGlassOrbLayer(80,80)
    private val spill=IslandGlassOrbLayer(440,360)
    private val words=reply.split(Regex("\\s+")).filter { it.isNotBlank() }
    private val wordXs=mutableListOf<Double>()
    private val wordYs=mutableListOf<Double>()
    private val wordLayers=words.map { word ->
        IslandGlassOrbLayer(ceil(page.text.width(word,24.0,track=-.013)+64).toInt(),80).apply {
            record { c -> page.text.draw(c,word,32.0,40.0,24.0,track=-.013) }
        }
    }
    private var spillColor=0
    init {
        val space=page.text.width("a a",24.0,track=-.013)-2*page.text.width("a",24.0,track=-.013)
        var x=45.0; var y=66.0
        words.forEach { word -> val width=page.text.width(word,24.0,track=-.013); if(x+width>357) { x=45.0; y+=29 }; wordXs.add(x); wordYs.add(y); x+=width+space }
    }
    fun draw(c: Canvas,f: IslandGlassOrbFrame,oy: Double,time: Double) {
        c.drawBitmap(page.image,null,RectF(0f,0f,402f,page.height.toFloat()),bitmapPaint)
        if(Build.VERSION.SDK_INT>=33 && c.isHardwareAccelerated)relight?.draw(c,f,time,page.artScale)
        rim(c,28.0,f.rimL); rim(c,212.0,f.rimR)
        val rowSave=c.save(); val scale=1+.04*f.row; c.scale(scale.toFloat(),scale.toFloat(),232f,190f)
        val row=m.clamp(f.row); val bar=colorMix(white,Color.rgb(100,244,249),row)
        c.drawRoundRect(RectF(232f,172f,234f,207f),1f,1f,paint(bar))
        page.text.draw(c,"2 Events",240.0,181.0,15.0,600,track=-.01)
        val rangeAlpha=m.mix(.6,1.0,row)
        page.text.draw(c,"6:00 – 7:30",240.0,200.0,13.0,alpha=rangeAlpha,tnum=true)
        val suffixY=200+page.text.baselineOffset(13.0)-page.text.baselineOffset(13.0,500)
        page.text.draw(c,"PM",240+page.text.width("6:00 – 7:30",13.0,tnum=true),suffixY,13.0,500,rangeAlpha,.02)
        c.restoreToCount(rowSave)
        if(f.spillA>0) {
            val color=spectrum(f.arcHue)
            if(spillColor!=color) { spill.record { sc -> sc.drawOval(RectF(100f,100f,340f,260f),paint(color)) }; spillColor=color }
            val layer=c.saveLayer(null,Paint().apply { xfermode=PorterDuffXfermode(PorterDuff.Mode.SCREEN) })
            spill.draw(c,-20.0,f.spillY-180,f.spillA,34.0); c.restoreToCount(layer)
        }
        page.text.draw(c,"12:01",36.0,26.0,17.0,600,f.clock,-.013,tnum=true)
        status(c)
        c.drawCircle(390f,26f,3f,paint(Color.rgb(255,122,26),f.mic))
        if(Build.VERSION.SDK_INT>=33 && c.isHardwareAccelerated)requireNotNull(runtime).draw(c,f,oy,time)
        else fallback.draw(c,f,oy,time)
        if(f.spinA>0) {
            spinner.record { sc ->
                val head=(((f.spinT%1)+1)%1)*6
                for(i in 0..5) {
                    val angle=i/6.0*2*PI-PI/2; val behind=(((head-i)%6)+6)%6
                    sc.drawCircle((40+10.5*cos(angle)).toFloat(),(40+10.5*sin(angle)).toFloat(),2.5f,paint(white,.3+.7*max(0.0,1-behind/3.2)))
                }
            }
            spinner.draw(c,m.shapes(f,oy).x+30-40,oy, f.spinA,f.spinBlur)
        }
        for(i in words.indices) {
            val k=m.out(m.seg(f.textT,.09*i,.09*i+.3)); val opacity=k*(1-f.textOut)
            if(opacity>0)wordLayers[i].draw(c,wordXs[i]-32,wordYs[i]+oy-40+4*(1-k),opacity,8*(1-k)+8*f.textOut)
        }
    }
    private fun rim(c: Canvas,x: Double,a: Double) {
        if(a<=0)return
        val p=paint(white,a).apply { style=Paint.Style.STROKE; strokeWidth=1f; shader=LinearGradient(0f,79f,0f,175.6f,intArrayOf(Color.argb(64,255,255,255),Color.TRANSPARENT),null,Shader.TileMode.CLAMP) }
        c.drawRoundRect(RectF((x+.5).toFloat(),79.5f,(x+161.5).toFloat(),239.5f),23.5f,23.5f,p)
    }
    private fun status(c: Canvas) {
        val p=paint(white)
        doubleArrayOf(4.0,6.0,8.5,11.0).forEachIndexed { i,h -> c.drawRoundRect(RectF((298+i*4.6).toFloat(),(31.5-h).toFloat(),(301+i*4.6).toFloat(),31.5f),1f,1f,p) }
        c.drawPath(wifi,paint(white).apply { style=Paint.Style.STROKE; strokeWidth=2.1f; strokeCap=Paint.Cap.ROUND })
        c.drawCircle(331f,30.4f,1.5f,p)
        c.drawRoundRect(RectF(346f,20.5f,371f,32f),3.6f,3.6f,paint(white,.4).apply { style=Paint.Style.STROKE; strokeWidth=1f })
        c.drawRoundRect(RectF(348f,22.5f,356f,30f),1.8f,1.8f,p)
        c.drawRoundRect(RectF(372.2f,24.3f,373.8f,28.3f),.8f,.8f,paint(white,.4))
    }
    private fun paint(color: Int,alpha: Double=1.0) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=color; this.alpha=(Color.alpha(color)*alpha.coerceIn(0.0,1.0)).roundToInt() }
    private fun colorMix(a: Int,b: Int,k: Double) = Color.rgb(m.mix(Color.red(a).toDouble(),Color.red(b).toDouble(),k).roundToInt(),m.mix(Color.green(a).toDouble(),Color.green(b).toDouble(),k).roundToInt(),m.mix(Color.blue(a).toDouble(),Color.blue(b).toDouble(),k).roundToInt())
    private fun spectrum(x: Double): Int {
        val colors=intArrayOf(Color.rgb(255,90,31),Color.rgb(245,205,150),Color.rgb(100,244,249),Color.rgb(43,91,255),Color.rgb(255,45,138))
        val u=(((x%1)+1)%1)*5; val i=floor(u).toInt(); val f=u-i
        return colorMix(colors[i%5],colors[(i+1)%5],f*f*(3-2*f))
    }
}
