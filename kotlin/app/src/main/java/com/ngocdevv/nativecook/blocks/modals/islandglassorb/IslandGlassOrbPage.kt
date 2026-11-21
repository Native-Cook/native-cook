package com.ngocdevv.nativecook.blocks.modals.islandglassorb

import android.content.res.AssetManager
import android.graphics.*
import androidx.core.graphics.PathParser
import kotlin.math.*

/** Cached offline page and blurred snapshot: both are sampled by the live lens. */
internal class IslandGlassOrbPage(val assets: AssetManager,val height: Double,val scale: Double) {
    val text=IslandGlassOrbText(assets)
    val image: Bitmap
    val blurred: Bitmap
    private val white=Color.WHITE
    private val wall=assets.open("islandglassorb/islandglassorb_wallpaper.jpg").use { BitmapFactory.decodeStream(it) }
    private val mark=path("M13.094 13.344A6.4 4.9 32 0 1 3.872 5.109 M7.800 3.137A6.4 4.9 32 0 1 15.309 10.029 M20.903 15.539A5.05 6.25 -18 0 1 20.667 20.650 M17.168 23.225A5.05 6.25 -18 0 1 11.442 19.068 M11.305 14.091A5.05 6.25 -18 0 1 20.903 15.539")
    private val camera=path("M4.1 8.25h3.15l1.45-2.1h8.6l1.45 2.1h3.15c1.16 0 2.1.94 2.1 2.1v9.15c0 1.16-.94 2.1-2.1 2.1H4.1A2.1 2.1 0 0 1 2 19.5v-9.15c0-1.16.94-2.1 2.1-2.1Z")
    init {
        val low=Bitmap.createBitmap(402,ceil(height).toInt(),Bitmap.Config.ARGB_8888)
        val lc=Canvas(low); drawWallpaper(lc)
        val frost=IslandGlassOrbBlur.apply(low,12.0)
        image=Bitmap.createBitmap((402*scale).roundToInt(),(height*scale).roundToInt(),Bitmap.Config.ARGB_8888)
        val c=Canvas(image); c.scale(scale.toFloat(),scale.toFloat()); drawWallpaper(c)
        // RN's static 3% grain, sampled on the same three-cells-per-point grid.
        val px=IntArray(image.width*image.height); image.getPixels(px,0,image.width,0,0,image.width,image.height)
        for(y in 0 until image.height)for(x in 0 until image.width) {
            val p=px[y*image.width+x]; val hx=floor(x/scale*3); val hy=floor(y/scale*3)
            val raw=sin(hx*127.1+hy*311.7)*43758.5453; val n=(raw-floor(raw))*255
            px[y*image.width+x]=Color.rgb((Color.red(p)*.97+n*.03).roundToInt(),(Color.green(p)*.97+n*.03).roundToInt(),(Color.blue(p)*.97+n*.03).roundToInt())
        }
        image.setPixels(px,0,image.width,0,0,image.width,image.height)
        dock(c,frost)
        frosted(c,frost,28.0); frosted(c,frost,212.0)
        rounded(c,46.0,106.0,127.0,44.0,22.0,white,.14)
        val save=c.save(); c.translate(60f,114f); c.drawPath(mark,stroke(1.0)); c.restoreToCount(save)
        text.draw(c,"Ask",98.0,128.0,24.0,alpha=.85,track=-.013)
        for((i,x)in listOf(74.0,142.0).withIndex()) {
            rounded(c,x-29,166.0,58.0,58.0,29.0,white,.16)
            val s=c.save(); c.translate((x-13).toFloat(),182f)
            if(i==0) { c.drawPath(camera,stroke(.85)); c.drawCircle(13f,14.5f,3.45f,stroke(.85)) }
            else { val p=stroke(.85); c.drawLine(4.25f,11f,4.25f,15f,p); c.drawLine(9.75f,7.5f,9.75f,18.5f,p); c.drawLine(15.25f,4.75f,15.25f,21.25f,p); c.drawLine(20.75f,9f,20.75f,17f,p) }
            c.restoreToCount(s)
        }
        text.draw(c,"THURSDAY",232.0,104.0,13.0,600,.85,.06)
        text.draw(c,"12",230.5,128.0,32.0,300,track=-.021,display=true,tnum=true)
        text.draw(c,"TOMORROW",232.0,160.0,13.0,600,.6,.06)
        text.draw(c,"Nori",109.0,254.0,13.0,500,center=true)
        text.draw(c,"Calendar",293.0,254.0,13.0,500,center=true)
        val pageLow=Bitmap.createScaledBitmap(image,402,ceil(height).toInt(),true)
        val pageBlur=IslandGlassOrbBlur.apply(pageLow,18.0)
        blurred=Bitmap.createBitmap(pageBlur,0,0,402,min(330,pageBlur.height))
        frost.recycle(); low.recycle(); pageLow.recycle(); if(blurred!==pageBlur)pageBlur.recycle(); wall.recycle()
    }
    val artScale get()=min(1.0,max(.35,(762+min(0.0,height-874)-16-276)/434))
    private fun drawWallpaper(c: Canvas) {
        val a=artScale; val sc=1.15*a; val ox=-14.2455*a+201*(1-a); val oy=15.4295*a+276*(1-a)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val contrast=Paint().apply { colorFilter=ColorMatrixColorFilter(ColorMatrix(floatArrayOf(1.35f,0f,0f,0f,(-.569*.35*255).toFloat(),0f,1.35f,0f,0f,(-.620*.35*255).toFloat(),0f,0f,1.35f,0f,(-.565*.35*255).toFloat(),0f,0f,0f,1f,0f))) }
        // Apply contrast anchored to sage without changing backdrop registration.
        val l=c.saveLayer(0f,0f,402f,height.toFloat(),contrast)
        val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        c.drawBitmap(wall,null,RectF(0f,0f,402f,2185f),p)
        val blend=c.saveLayer(0f,0f,402f,height.toFloat(),null)
        c.drawBitmap(wall,null,RectF(ox.toFloat(),oy.toFloat(),(ox+402*sc).toFloat(),(oy+874*sc).toFloat()),p)
        p.shader=LinearGradient(0f,100f,0f,180f,Color.TRANSPARENT,Color.WHITE,Shader.TileMode.CLAMP); p.xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        c.drawRect(0f,0f,402f,height.toFloat(),p); c.restoreToCount(blend); c.restoreToCount(l)
    }
    private fun dock(c: Canvas,frost: Bitmap) {
        val y=762+min(0.0,height-874)
        val rect=RectF(16f,y.toFloat(),386f,(y+84).toFloat())
        val save=c.save(); c.clipPath(Path().apply { addRoundRect(rect,34f,34f,Path.Direction.CW) })
        c.drawBitmap(frost,null,RectF(0f,0f,402f,height.toFloat()),Paint(Paint.FILTER_BITMAP_FLAG)); c.drawColor(0x29ffffff); c.restoreToCount(save)
        c.drawRoundRect(rect,34f,34f,stroke(.4).apply { strokeWidth=2f })
        val glyphs=listOf("M27 18 C13 10 12 35 27 31 M33 29 C47 37 48 12 33 16 M22 25 L38 25","M20 17 L31 13 L43 23 L38 39 L23 43 L15 30 Z M23 22 L35 22 L38 32 L27 36 L21 29 Z","M17 18 Q17 15 20 15 L36 15 Q39 15 39 18 L39 30 L30 30 L24 36 L24 30 L17 30 Z M31 37 L43 37 L43 24","M18 39 L18 30 Q18 25 24 25 L36 25 Q42 25 42 19 M14 39 L22 39 M38 19 L42 15 L46 19 M29 22 L29 28")
        glyphs.forEachIndexed { i,g ->
            val ss=c.save(); c.translate((36+i*90).toFloat(),(y+12).toFloat())
            rounded(c,0.0,0.0,60.0,60.0,19.0,Color.rgb(174,199,174),.4)
            c.drawRoundRect(RectF(1f,1f,59f,59f),18f,18f,stroke(.6).apply { strokeWidth=2f })
            c.drawPath(path(g),stroke(1.0).apply { strokeWidth=2.5f }); c.restoreToCount(ss)
        }
    }
    private fun frosted(c: Canvas,blur: Bitmap,x: Double) {
        val clip=Path().apply { addRoundRect(RectF(x.toFloat(),79f,(x+162).toFloat(),240f),24f,24f,Path.Direction.CW) }
        val save=c.save(); c.clipPath(clip)
        c.drawBitmap(blur,null,RectF(0f,0f,402f,height.toFloat()),Paint(Paint.FILTER_BITMAP_FLAG)); c.drawColor(Color.argb(41,255,255,255)); c.restoreToCount(save)
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE; strokeWidth=2f; shader=LinearGradient(0f,79f,0f,240f,intArrayOf(Color.argb(140,255,255,255),Color.argb(41,255,255,255)),null,Shader.TileMode.CLAMP) }
        c.drawRoundRect(RectF((x+.5).toFloat(),79.5f,(x+161.5).toFloat(),239.5f),23.5f,23.5f,p)
    }
    private fun stroke(a: Double) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=white; alpha=(a*255).roundToInt(); style=Paint.Style.STROKE; strokeWidth=1.75f; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND }
    private fun path(d: String) = requireNotNull(PathParser.createPathFromPathData(d))
    private fun rounded(c: Canvas,x: Double,y: Double,w: Double,h: Double,r: Double,color: Int,a: Double) {
        c.drawRoundRect(RectF(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat()),r.toFloat(),r.toFloat(),Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=color; alpha=(a*255).roundToInt() })
    }
}
