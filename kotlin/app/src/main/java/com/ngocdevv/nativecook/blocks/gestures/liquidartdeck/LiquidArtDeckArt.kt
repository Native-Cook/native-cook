package com.ngocdevv.nativecook.blocks.gestures.liquidartdeck

import android.content.Context
import android.graphics.*
import android.os.Build
import androidx.core.graphics.PathParser
import kotlin.math.*

internal class LiquidArtDeckArt(context: Context) {
    private val title=Typeface.createFromAsset(context.assets,"liquidartdeck/liquidartdeck_Outfit-Medium.ttf")
    private val medium=Typeface.createFromAsset(context.assets,"liquidartdeck/liquidartdeck_Inter-Medium.ttf")
    private val semi=Typeface.createFromAsset(context.assets,"liquidartdeck/liquidartdeck_Inter-SemiBold.ttf")
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val shader=if(Build.VERSION.SDK_INT>=33)RuntimeShader(LiquidArtDeckShader.SOURCE) else null
    private val heart=requireNotNull(PathParser.createPathFromPathData("M12 20.5s-7.5-4.6-7.5-10.1A4.3 4.3 0 0 1 12 7.7a4.3 4.3 0 0 1 7.5 2.7c0 5.5-7.5 10.1-7.5 10.1z"))
    private val home=requireNotNull(PathParser.createPathFromPathData("M4 11 12 4l8 7v8a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z"))
    private val search=requireNotNull(PathParser.createPathFromPathData("M11 4a7 7 0 1 0 0.01 0 M16 16l4.5 4.5"))
    private val user=requireNotNull(PathParser.createPathFromPathData("M8.2 8.5a3.8 3.8 0 1 0 7.6 0a3.8 3.8 0 1 0 -7.6 0 M4.8 20.5c1.1-3.8 3.9-5.8 7.2-5.8s6.1 2 7.2 5.8"))
    private val clips=Array(6) { Path().apply { addRoundRect(RectF(-131f,-182f,131f,108f),18f,18f,Path.Direction.CW) } }
    private val cards=Array(6) { i ->
        Bitmap.createBitmap(382*2,484*2,Bitmap.Config.ARGB_8888).also { b ->
            val c=Canvas(b);c.scale(2f,2f);c.translate(191f,242f)
            val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.BLACK;maskFilter=BlurMaskFilter(31f,BlurMaskFilter.Blur.NORMAL);alpha=128 }
            c.drawRoundRect(RectF(-131f,-170f,131f,204f),26f,26f,p)
            rect(c,-141.0,-192.0,282.0,384.0,26.0,0x1c1c22)
            text(c,LiquidArtDeckMotion.titles[i],-127.0,138.0,19.0,title,0xffffff)
            text(c,LiquidArtDeckMotion.authors[i],-127.0,160.0,12.5,medium,0x9a9aa6)
            val tw=measure(LiquidArtDeckMotion.prices[i],12.5,medium)
            rect(c,131-tw-22,136.0,tw+18,26.0,13.0,0x2b2b33)
            text(c,LiquidArtDeckMotion.prices[i],131-tw-13,153.0,12.5,medium,0xe8ff8a)
        }
    }
    private fun rgb(hex: Int,a: Double=1.0)=((a.coerceIn(0.0,1.0)*255).roundToInt() shl 24) or hex
    private fun rect(c: Canvas,x: Double,y: Double,w: Double,h: Double,r: Double,hex: Int,a: Double=1.0) {
        paint.shader=null;paint.style=Paint.Style.FILL;paint.color=rgb(hex,a)
        c.drawRoundRect(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),r.toFloat(),r.toFloat(),paint)
    }
    private fun measure(s: String,size: Double,face: Typeface): Double { textPaint.textSize=size.toFloat();textPaint.typeface=face;return textPaint.measureText(s).toDouble() }
    private fun text(c: Canvas,s: String,x: Double,y: Double,size: Double,face: Typeface,hex: Int,a: Double=1.0) {
        textPaint.textSize=size.toFloat();textPaint.typeface=face;textPaint.color=rgb(hex,a);c.drawText(s,x.toFloat(),y.toFloat(),textPaint)
    }
    private fun art(c: Canvas,box: RectF,ox: Double,oy: Double,aw: Double,ah: Double,seed: Double,sx: Double,sy: Double,ph: Double) {
        paint.style=Paint.Style.FILL;paint.alpha=255
        if(Build.VERSION.SDK_INT>=33 && shader!=null) {
            shader.setFloatUniform("o",ox.toFloat(),oy.toFloat());shader.setFloatUniform("sz",aw.toFloat(),ah.toFloat())
            shader.setFloatUniform("seed",seed.toFloat());shader.setFloatUniform("smear",sx.toFloat(),sy.toFloat());shader.setFloatUniform("ph",ph.toFloat());paint.shader=shader
        } else {
            paint.shader=LinearGradient(box.left,box.top,box.right,box.bottom,intArrayOf(rgb(0x120e29),rgb(0x6774af),rgb(0xd48bcb),rgb(0x191325)),null,Shader.TileMode.CLAMP)
        }
        c.drawRect(box,paint);paint.shader=null
    }
    private fun icon(c: Canvas,path: Path,x: Double,y: Double,fill: Boolean=false,hex: Int=0xffffff,a: Double=.5) {
        c.save();c.translate(x.toFloat(),y.toFloat());c.scale(26f/24,26f/24)
        paint.shader=null;paint.style=if(fill)Paint.Style.FILL else Paint.Style.STROKE;paint.strokeWidth=1.9f;paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND;paint.color=rgb(hex,a)
        c.drawPath(path,paint);c.restore();paint.style=Paint.Style.FILL
    }
    fun draw(c: Canvas,w: Double,h: Double,top: Double,bottom: Double,d: LiquidArtDeckMotion.Deck,now: Double,phase: Double,reduced: Boolean) {
        val m=LiquidArtDeckMotion;val cy=top+340;val tab=h-bottom-34;val ph=2*PI*phase/9
        c.drawColor(rgb(0x0f0f12))
        text(c,"Favorites",22.0,top+100,22.0,title,0xffffff)
        text(c,"See all ›",w-22-measure("See all ›",14.0,medium),top+96,14.0,medium,0xffffff,.55)
        repeat(6) { i ->
            val x=45+i*(w-90)/5;val y=top+35
            c.save();val clip=Path().apply { addCircle(x.toFloat(),y.toFloat(),21f,Path.Direction.CW) };c.clipPath(clip)
            art(c,RectF((x-21).toFloat(),(y-21).toFloat(),(x+21).toFloat(),(y+21).toFloat()),x,y,46.0,46.0,m.seeds[i],0.0,0.0,ph+i);c.restore()
            paint.color=if(i==0)rgb(0xe8ff8a) else rgb(0xffffff,.18);paint.style=Paint.Style.STROKE;paint.strokeWidth=2f;c.drawCircle(x.toFloat(),y.toFloat(),25f,paint);paint.style=Paint.Style.FILL
        }
        val order=mutableListOf<Int>()
        for(s in 3 downTo 0) for(i in 0..5) if(i!=d.fly && i>=d.cur && floor(d.ds[i]+.5).toInt()==s)order.add(i)
        if(d.fly>=0)order.add(d.fly)
        for(i in order) {
            var x=0.0;var y=0.0;var rot=0.0;var sc=1.0;var alpha=1.0;var dim=0.0
            if(i==d.fly) {
                var k=((now-d.start)/(if(d.kind==1).6 else .42)).coerceIn(0.0,1.0);if(d.back==1)k=1-k
                val e=if(d.back==1)m.ease(k) else k*k*(3-2*k)
                if(d.kind==1) {
                    val tx=w*.625-w/2;val ty=tab-cy;val kx=(d.fx+tx)*.5;val ky=min(d.fy,ty)-170;val u=1-e
                    x=u*u*d.fx+2*u*e*kx+e*e*tx;y=u*u*d.fy+2*u*e*ky+e*e*ty
                    sc=m.mix(1.0,.06,e*e);rot=m.mix(d.fx*.06,8.0,e);alpha=1-m.smooth((e-.88)/.12)
                } else { x=m.mix(d.fx,-w*1.25,e*e);y=m.mix(d.fy,d.fy+50,e);rot=m.mix(d.fx*.06,-22.0,e) }
            } else {
                val ds=d.ds[i];val a=min(abs(ds),1.0)
                x=sign(ds)*(211.8*a+max(0.0,abs(ds)-1)*140);sc=1-.2*a;dim=.55*a;alpha=1-m.smooth(abs(ds)-1.4)
                if(i==d.cur) { x+=d.dx;y+=d.dy;rot=d.dx*.06 }
            }
            if(alpha<=.01)continue
            c.save();c.translate((w/2+x).toFloat(),(cy+y).toFloat());c.rotate(rot.toFloat());c.scale(sc.toFloat(),sc.toFloat())
            val layer=c.saveLayerAlpha(-240f,-300f,240f,300f,(alpha*255).roundToInt())
            paint.shader=null;paint.alpha=255;paint.color=Color.WHITE;c.drawBitmap(cards[i],null,RectF(-191f,-242f,191f,242f),paint)
            c.save();c.clipPath(clips[i]);val live=(i==d.fly || i==d.cur) && !reduced
            art(c,RectF(-131f,-182f,131f,108f),0.0,-37.0,262.0,290.0,m.seeds[i],if(live)d.sx else 0.0,if(live)d.sy else 0.0,ph+i);c.restore()
            if(dim>.01)rect(c,-141.0,-192.0,282.0,384.0,26.0,0x0f0f12,dim)
            c.restoreToCount(layer);c.restore()
        }
        val ay=cy+214
        rect(c,w/2-103,ay,58.0,58.0,29.0,0x22222a);rect(c,w/2-23,ay+6,46.0,46.0,23.0,0x22222a);rect(c,w/2+45,ay,58.0,58.0,29.0,0xe8ff8a)
        text(c,"✕",w/2-74-measure("✕",20.0,semi)/2,ay+36,20.0,semi,0xffffff)
        text(c,"↺",w/2-measure("↺",20.0,semi)/2,ay+36,20.0,semi,0xffffff)
        icon(c,heart,w/2+61,ay+16,true,0x0f0f12,1.0)
        rect(c,18.0,h-bottom-68,w-36,62.0,31.0,0x1b1b21)
        icon(c,home,48.0,h-bottom-50);icon(c,search,126.0,h-bottom-50);icon(c,user,w-74,h-bottom-50)
        val s=now-d.beat
        val k=if(s>=0 && s<.6)sin(min(1.0,s/.12)*PI)*.35*exp(-s*4)+sin(s*18)*.08*exp(-s*7) else 0.0
        c.save();c.translate((w*.625).toFloat(),(h-bottom-37).toFloat());c.scale((1+k).toFloat(),(1+k).toFloat())
        icon(c,heart,-13.0,-13.0,true,0xff6ad0,1.0)
        rect(c,8.0,-22.0,22.0,18.0,9.0,0xff6ad0)
        text(c,d.count.toString(),19-measure(d.count.toString(),11.0,semi)/2,-9.0,11.0,semi,0x0f0f12);c.restore()
    }
}
