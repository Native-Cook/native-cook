package com.ngocdevv.nativecook.blocks.cards.glasspiggysavings

import android.graphics.*
import android.os.Build
import com.ngocdevv.nativecook.blocks.cards.glasspiggysavings.GlassPiggySavingsMotion as M
import kotlin.math.*

internal class GlassPiggySavingsArt(private val resources:GlassPiggySavingsResources) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bounds=RectF()
    private val lip=Path()
    private var glow:Shader?=null
    private var glowX=Double.NaN
    private var glowY=Double.NaN
    private val shadow=Bitmap.createBitmap(330,100,Bitmap.Config.ARGB_8888).also { bitmap ->
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.BLACK; alpha=128; maskFilter=BlurMaskFilter(14f,BlurMaskFilter.Blur.NORMAL) }
        Canvas(bitmap).drawOval(40f,37f,290f,63f,paint)
    }
    private val highlight=Bitmap.createBitmap(90,90,Bitmap.Config.ARGB_8888).also { bitmap ->
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE; alpha=115; maskFilter=BlurMaskFilter(1.8f,BlurMaskFilter.Blur.NORMAL) }
        Canvas(bitmap).drawOval(45f-30f*0.62f,45f-30f*0.74f,45f,45f-30f*0.44f,paint)
    }
    private fun color(hex:Int,alpha:Double=1.0)=Color.argb((alpha.coerceIn(0.0,1.0)*255).roundToInt(),hex shr 16 and 255,hex shr 8 and 255,hex and 255)
    private fun paint(hex:Int,alpha:Double=1.0) {
        p.reset(); p.isAntiAlias=true; p.isFilterBitmap=true; p.color=color(hex,alpha)
    }
    private fun oval(c:Canvas,x:Double,y:Double,w:Double,h:Double) { c.drawOval(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),p) }
    private fun rect(c:Canvas,x:Double,y:Double,w:Double,h:Double,r:Double=0.0) {
        bounds.set(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat()); c.drawRoundRect(bounds,r.toFloat(),r.toFloat(),p)
    }
    private fun label(c:Canvas,text:String,x:Double,top:Double,size:Double,weight:String,hex:Int,alpha:Double=1.0,tracking:Double=0.0,center:Boolean=false):Double {
        paint(hex,alpha); p.typeface=resources.faces[weight]; p.textSize=size.toFloat()
        val width=p.measureText(text)+tracking*max(0,text.length-1)
        var left=if(center) x-width/2 else x
        val fm=p.fontMetrics; val baseline=(top-fm.ascent).toFloat()
        if(tracking==0.0) c.drawText(text,left.toFloat(),baseline,p)
        else for(ch in text) { val s=ch.toString(); c.drawText(s,left.toFloat(),baseline,p); left+=p.measureText(s)+tracking }
        return (fm.descent-fm.ascent).toDouble()
    }
    fun draw(c:Canvas,l:M.Layout,f:M.Frame,density:Float) {
        c.drawColor(color(0x03110b))
        if(glow==null || glowX!=l.w/2 || glowY!=l.pigY+140) {
            glowX=l.w/2; glowY=l.pigY+140
            glow=RadialGradient(glowX.toFloat(),glowY.toFloat(),330f,
                intArrayOf(color(0x20d780,0.78),color(0x0e824e,0.42),color(0x052818,0.12),color(0x03110b,0.0)),floatArrayOf(0f,0.38f,0.72f,1f),Shader.TileMode.CLAMP)
        }
        val pulse=if(f.t>=2.5) exp(-max(0.0,f.t-2.5)*3) else 0.0
        paint(0xffffff,0.8+0.2*pulse); p.shader=glow; c.drawRect(0f,0f,l.w.toFloat(),l.h.toFloat(),p)
        paint(0xffffff); c.drawBitmap(shadow,(l.pigX+10).toFloat(),(l.pigY+253).toFloat(),p)
        val coin=M.coin(f,l)
        c.save()
        val fx=l.pigX+170; val fy=l.pigY+800*M.SCALE-20
        c.translate(fx.toFloat(),fy.toFloat()); c.rotate((f.pig.wob*6).toFloat(),0f,-120f)
        c.scale((1+f.pig.sq*0.06).toFloat(),(1-f.pig.sq*0.08).toFloat())
        val breathe=(1+sin(2*PI*2*f.t/M.LOOP)*0.006).toFloat(); c.scale(breathe,breathe)
        c.translate(-fx.toFloat(),-fy.toFloat())
        paint(0xffffff)
        if(Build.VERSION.SDK_INT>=33 && resources.runtime!=null) {
            val sweep=M.seg(f.t,2.6,3.7); val pos=M.mix(l.pigX-200,l.pigX+640,sweep)
            val strength=if(sweep>0 && sweep<1) 0.22*sin(sweep*PI) else 0.0
            p.shader=resources.runtime.paint(l.pigX.toFloat(),l.pigY.toFloat(),pos.toFloat(),strength.toFloat())
            rect(c,l.pigX,l.pigY,340.0,800*M.SCALE)
        } else {
            bounds.set(l.pigX.toFloat(),l.pigY.toFloat(),(l.pigX+340).toFloat(),(l.pigY+800*M.SCALE).toFloat())
            c.drawBitmap(resources.pig,null,bounds,p)
        }
        if(coin.alpha>0 && coin.clipped) {
            val y0=l.slotY+6*M.SCALE*2.6
            lip.rewind(); lip.moveTo((l.slotX-80).toFloat(),-50f); lip.lineTo((l.slotX+80).toFloat(),-50f)
            lip.lineTo((l.slotX+80).toFloat(),(y0+16).toFloat()); lip.lineTo((l.slotX-80).toFloat(),(y0-16).toFloat()); lip.close()
            c.save(); c.clipPath(lip); drawCoin(c,coin); c.restore()
        }
        c.restore()
        if(coin.alpha>0 && !coin.clipped) drawCoin(c,coin)
        val since=max(if(f.t>=2.5) f.t-2.5 else -1.0,if(f.held.dropAt>0) f.now-f.held.dropAt-0.23 else -1.0)
        if(since>=0 && since<1) repeat(16) { i ->
            val a= -PI/2+(M.hash(i*3.3)-0.5)*2.4; val v=70+M.hash(i*1.7)*110; val d=(1-exp(-since*4))/4
            paint(if(i%3==0) 0xfff4c2 else 0xffd25c,1-M.smooth(M.seg(since,0.35,0.9)))
            c.drawCircle((l.slotX+cos(a)*v*d).toFloat(),(l.slotY+sin(a)*v*d+80*since*since).toFloat(),(1.4+M.hash(i.toDouble())*1.6).toFloat(),p)
        }
        drawUI(c,l,f,density)
    }
    private fun drawCoin(c:Canvas,coin:M.Coin) {
        if(coin.alpha<=0.01) return
        c.save(); c.translate(coin.x.toFloat(),coin.y.toFloat())
        val r=coin.radius; val sx=max(0.06,abs(coin.flip)); val side=sqrt(max(0.0,1-sx*sx))*r*0.2*if(coin.flip<0) -1 else 1
        paint(0x9a6516,coin.alpha); oval(c,-r*sx+side,-r,2*r*sx,2*r)
        if(abs(side)>0.5) rect(c,min(0.0,side),-r,abs(side),2*r)
        c.scale(sx.toFloat(),1f)
        paint(0xffffff,coin.alpha)
        p.shader=LinearGradient(-r.toFloat(),-r.toFloat(),(r*0.7).toFloat(),r.toFloat(),intArrayOf(color(0xfff3b8),color(0xf0bf45),color(0xb57a1d)),floatArrayOf(0f,0.45f,1f),Shader.TileMode.CLAMP)
        c.drawCircle(0f,0f,r.toFloat(),p)
        paint(0xffffff,coin.alpha)
        p.shader=LinearGradient((r*0.8).toFloat(),(r*0.8).toFloat(),(-r*0.8).toFloat(),(-r*0.8).toFloat(),
            if(coin.flip<0) intArrayOf(color(0xf2c457),color(0xc98f27)) else intArrayOf(color(0xffe48f),color(0xdca031)),null,Shader.TileMode.CLAMP)
        c.drawCircle(0f,0f,(r*0.82).toFloat(),p)
        paint(0xb98222,0.7*coin.alpha); p.style=Paint.Style.STROKE; p.strokeWidth=max(0.8,r*0.05).toFloat()
        c.drawCircle(0f,0f,(r*0.66).toFloat(),p)
        paint(0xffffff,coin.alpha); c.save(); c.scale((r/30).toFloat(),(r/30).toFloat()); c.drawBitmap(highlight,-45f,-45f,p); c.restore()
        c.restore()
    }
    private fun drawUI(c:Canvas,l:M.Layout,f:M.Frame,density:Float) {
        var y=l.top+18
        y+=label(c,"LISBON TRIP · SAVED SO FAR",24.0,y,12.0,"ExtraBold",0x96f0be,0.72,1.4)+2
        val totalHeight=label(c,money(f.total),24.0,y+f.dip*6,54.0,"Black",0xffffff,1-f.dip,-1.0)
        y+=totalHeight+14
        paint(0xffffff,0.1); rect(c,24.0,y,l.w-48,8.0,4.0)
        paint(0x3fe08f); rect(c,24.0,y,(l.w-48)*M.clamp(f.total/2000),8.0,4.0)
        label(c,"Goal $2,000 · by June 14",24.0,y+16,14.0,"SemiBold",0xffffff,0.58)
        val k=f.floatK
        if(k>=0) label(c,"+$${f.plus}",l.slotX,l.slotY-50-M.easeOut(k)*60,22.0,"Black",0xffd25c,sin(min(k*1.4,1.0)*PI),center=true)
        repeat(3) { i ->
            c.save(); val cx=l.chipX(i); val cy=l.chipY; c.translate(cx.toFloat(),cy.toFloat())
            val scale=if(f.held.on && f.held.chip==i) 0.96f else 1f; c.scale(scale,scale)
            paint(if(i==2) 0x3fe08f else 0xffffff,if(i==2) 0.16 else 0.07); rect(c,-50.0,-22.0,100.0,44.0,22.0)
            paint(if(i==2) 0x3fe08f else 0xffffff,if(i==2) 0.6 else 0.16); p.style=Paint.Style.STROKE; p.strokeWidth=1/density
            val inset=0.5/density; rect(c,-50+inset,-22+inset,100-2*inset,44-2*inset,22-inset)
            val s="+ $${M.CHIPS[i]}"; paint(0xffffff); p.typeface=resources.faces["ExtraBold"]; p.textSize=16f
            val textW=p.measureText(s).toDouble(); val left= -(16+8+textW)/2
            c.save(); c.translate((left+8).toFloat(),0f); val ds=f.dot(i).toFloat(); c.scale(ds,ds)
            paint(0xf2bf45); c.drawCircle(0f,0f,8f,p); paint(0xc98f27); p.style=Paint.Style.STROKE; p.strokeWidth=2f; c.drawCircle(0f,0f,7f,p); c.restore()
            paint(0xffffff); p.typeface=resources.faces["ExtraBold"]; p.textSize=16f; val fm=p.fontMetrics
            label(c,s,left+24,-(fm.descent-fm.ascent)/2.0,16.0,"ExtraBold",0xffffff)
            c.restore()
        }
        label(c,"Drag a coin into the piggy",l.w/2,l.chipY+34,13.0,"SemiBold",0xffffff,0.45,center=true)
        val receipt=f.receipt()
        if(receipt.first>0) {
            val top=l.h-l.bottom-64+receipt.second
            c.saveLayerAlpha(0f,(top-4).toFloat(),l.w.toFloat(),(top+62).toFloat(),(receipt.first*255).roundToInt().coerceIn(0,255))
            paint(0x0d2a1d); rect(c,16.0,top,l.w-32,58.0,18.0)
            paint(0x3fe08f,0.45); p.style=Paint.Style.STROKE; p.strokeWidth=1/density
            val inset=0.5/density; rect(c,16+inset,top+inset,l.w-32-2*inset,58-2*inset,18-inset)
            paint(0x3fe08f); c.drawCircle(44f,(top+29).toFloat(),16f,p)
            paint(0x03110b); p.typeface=resources.faces["Black"]; p.textSize=17f
            val fm=p.fontMetrics; label(c,"✓",44.0,top+29-(fm.descent-fm.ascent)/2.0,17.0,"Black",0x03110b,center=true)
            val titleH=15*1.364; val subH=12*1.364; val textTop=top+(58-titleH-subH-1)/2
            label(c,"$${f.plus} saved to Lisbon trip",72.0,textTop,15.0,"ExtraBold",0xffffff)
            label(c,"Just now · from Everyday account",72.0,textTop+titleH+1,12.0,"SemiBold",0xffffff,0.55)
            c.restore()
        }
    }
    companion object {
        fun money(v:Double):String {
            val s=floor(v+0.5).toInt().toString()
            return "$"+s.mapIndexed { i,ch -> (if(i>0 && (s.length-i)%3==0) "," else "")+ch }.joinToString("")
        }
    }
}
