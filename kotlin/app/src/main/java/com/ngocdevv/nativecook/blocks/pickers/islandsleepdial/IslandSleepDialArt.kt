package com.ngocdevv.nativecook.blocks.pickers.islandsleepdial

import android.content.Context
import android.graphics.*
import kotlin.math.*

internal class IslandSleepDialArt(context: Context) {
    private val regular = Typeface.createFromAsset(context.assets, "islandsleepdial/islandsleepdial_Inter-Regular.ttf")
    private val semi = Typeface.createFromAsset(context.assets, "islandsleepdial/islandsleepdial_Inter-SemiBold.ttf")
    private val bold = Typeface.createFromAsset(context.assets, "islandsleepdial/islandsleepdial_Inter-Bold.ttf")
    private val icons = Typeface.createFromAsset(context.assets, "islandsleepdial/islandsleepdial_Ionicons.ttf")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val glows = mutableMapOf<String, Bitmap>()
    private var island: Bitmap? = null
    private var islandS = 0.0
    private var sky: Shader? = null
    private var skyH = 0.0
    private fun p(color: Int, alpha: Double = 1.0, stroke: Double = 0.0): Paint {
        paint.reset(); paint.isAntiAlias = true
        paint.color = color or (0xFF shl 24); paint.alpha = (alpha.coerceIn(0.0, 1.0) * 255).roundToInt()
        if (stroke > 0) { paint.style = Paint.Style.STROKE; paint.strokeWidth = stroke.toFloat(); paint.strokeCap = Paint.Cap.ROUND }
        return paint
    }
    private fun circle(c: Canvas, x: Double, y: Double, r: Double, color: Int, a: Double = 1.0, stroke: Double = 0.0) {
        c.drawCircle(x.toFloat(), y.toFloat(), r.toFloat(), p(color, a, stroke))
    }
    private fun rect(c: Canvas, x: Double, y: Double, w: Double, h: Double, color: Int, a: Double = 1.0) {
        c.drawRect(x.toFloat(), y.toFloat(), (x+w).toFloat(), (y+h).toFloat(), p(color, a))
    }
    private fun glow(c: Canvas, x: Double, y: Double, r: Double, sigma: Double, color: Int, a: Double) {
        val key = "$r,$sigma,$color"
        val image = glows.getOrPut(key) {
            val side = ceil((r + sigma * 4) * 6).toInt()
            Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888).also { bitmap ->
                val g = Canvas(bitmap); g.scale(3f, 3f)
                val q = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color or (0xFF shl 24); maskFilter = BlurMaskFilter(sigma.toFloat(), BlurMaskFilter.Blur.NORMAL) }
                val center = side / 6f
                g.drawCircle(center, center, r.toFloat(), q)
            }
        }
        val side = image.width / 3f
        c.drawBitmap(image, null, RectF((x-side/2).toFloat(), (y-side/2).toFloat(), (x+side/2).toFloat(), (y+side/2).toFloat()), p(0xFFFFFF, a))
    }
    private fun text(c: Canvas, text: String, size: Double, face: Typeface, color: Int, x: Double, top: Double, tracking: Float = 0f) {
        val q = p(color); q.typeface = face; q.textSize = size.toFloat(); q.textAlign = Paint.Align.CENTER
        val baseline = (top - q.fontMetrics.ascent).toFloat()
        if (tracking == 0f) c.drawText(text, x.toFloat(), baseline, q)
        else {
            val widths = FloatArray(text.length); q.getTextWidths(text, widths)
            var cursor = x.toFloat() - (widths.sum() + tracking * (text.length - 1)) / 2
            q.textAlign = Paint.Align.LEFT
            text.forEachIndexed { index, _ ->
                c.drawText(text, index, index + 1, cursor, baseline, q)
                cursor += widths[index] + tracking
            }
        }
    }
    private fun makeIsland(s: Double): Bitmap {
        val image = Bitmap.createBitmap(ceil(s * 9).toInt(), ceil(s * 9).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(image); c.scale(3f, 3f); c.translate((s * 1.5).toFloat(), s.toFloat())
        path.reset(); path.moveTo((-s*1.05).toFloat(), 0f)
        path.quadTo((-s*.6).toFloat(), (s*.9).toFloat(), (-s*.1).toFloat(), (s*1.35).toFloat())
        path.quadTo((s*.25).toFloat(), (s*.8).toFloat(), (s*1.05).toFloat(), 0f); path.close(); c.drawPath(path, p(0x5B4E8C))
        path.reset(); path.moveTo((-s*.2).toFloat(),0f)
        path.quadTo((s*.1).toFloat(),(s*.7).toFloat(),(-s*.1).toFloat(),(s*1.35).toFloat())
        path.quadTo((s*.5).toFloat(),(s*.6).toFloat(),(s*1.05).toFloat(),0f);path.close();c.drawPath(path,p(0x463C73))
        c.drawOval((-s*1.1).toFloat(),(-s*.32).toFloat(),(s*1.1).toFloat(),(s*.28).toFloat(),p(0x4F9E63))
        c.drawOval((-s).toFloat(),(-s*.36).toFloat(),s.toFloat(),(s*.14).toFloat(),p(0x62B873))
        for(k in 0..2) {
            val x=s*(.45+k*.22);val y=-s*.1-k*s*.03;val z=s*(.34-k*.06)
            path.reset();path.moveTo(x.toFloat(),(y-z*1.8).toFloat());path.lineTo((x+z*.5).toFloat(),y.toFloat());path.lineTo((x-z*.5).toFloat(),y.toFloat());path.close()
            c.drawPath(path,p(if(k%2==1)0x2E6B55 else 0x367A5F))
        }
        val x=-s*.35;val y=-s*.12
        rect(c,x-s*.32,y-s*.42,s*.64,s*.42,0x8A6A8F)
        path.reset();path.moveTo((x-s*.42).toFloat(),(y-s*.4).toFloat());path.lineTo(x.toFloat(),(y-s*.78).toFloat());path.lineTo((x+s*.42).toFloat(),(y-s*.4).toFloat());path.close();c.drawPath(path,p(0xE2667A))
        return image
    }
    fun draw(c: Canvas, w: Double, h: Double, top: Double, bottom: Double, f: IslandSleepDialMotion.Frame, reduced: Boolean, pressed: Boolean) {
        val cx=w/2;val cy=round(h*.44);val r=round(w*.4);val s=round(w*.2)
        if(sky == null || skyH != h) {
            skyH=h;sky=LinearGradient(0f,0f,0f,h.toFloat(),intArrayOf(0xFF141A3C.toInt(),0xFF26306A.toInt(),0xFF3B3F86.toInt()),null,Shader.TileMode.CLAMP)
        }
        val skyPaint=p(0xFFFFFF);skyPaint.shader=sky;c.drawRect(0f,0f,w.toFloat(),h.toFloat(),skyPaint)
        val calm=((f.h-4)/7).coerceIn(0.0,1.0)
        for(i in 0 until 46) if(i<16+calm*30) {
            val x=(i*97%389)*w/389;val y=(i*61%433)*(h*.7)/433
            val tw=if(reduced).7 else .45+.4*sin(f.t*2*PI*(1+i%3)/9+i)
            circle(c,x,y,1+(i%3)*.5,0xFFFFFF,tw)
        }
        circle(c,cx,cy,r,0xFFFFFF,.12,10.0)
        c.drawArc((cx-r).toFloat(),(cy-r).toFloat(),(cx+r).toFloat(),(cy+r).toFloat(),-90f,(f.h/12*360).toFloat(),false,p(0x9FB2FF,.9,10.0))
        for(k in 0 until 12) {
            val a=-PI/2+k/12.0*PI*2
            circle(c,cx+cos(a)*(r-22),cy+sin(a)*(r-22),if(k%3==0)2.4 else 1.4,0xFFFFFF,.4)
        }
        circle(c,cx,cy-r,15.0,0x2A3470);circle(c,cx,cy-r,15.0,0x9FB2FF,1.0,2.0)
        val bob=if(reduced)0.0 else sin(f.t*2*PI*2/9)*5-f.cloud*40;val iy=cy+bob
        if(island==null || islandS!=s) { islandS=s;island=makeIsland(s) }
        c.drawBitmap(island!!,null,RectF((cx-s*1.5).toFloat(),(iy-s).toFloat(),(cx+s*1.5).toFloat(),(iy+s*2).toFloat()),p(0xFFFFFF))
        val hx=cx-s*.35;val hy=iy-s*.12;val g=.35+.65*calm
        glow(c,hx,hy-s*.2,s*.32,10.0,0xFFD36B,.35*g)
        rect(c,hx-s*.2,hy-s*.3,s*.14,s*.14,0xFFD36B,g);rect(c,hx+s*.06,hy-s*.3,s*.14,s*.14,0xFFD36B,g)
        for(i in 0 until floor(calm*7).toInt()) {
            val ph=f.t*2*PI/9+i*1.7
            val x=cx+cos(ph*(if(i%2==1)1 else -1)+i)*s*(.8+.1*i)
            val y=iy-s*.6+sin(ph*2+i)*s*.25-i*3
            glow(c,x,y,if(reduced)2.0 else 3.0,4.0,0xFFF2A6,.8)
        }
        val a=-PI/2+f.h/12*PI*2;val mx=cx+cos(a)*r;val my=cy+sin(a)*r
        glow(c,mx,my,26+round(f.drag*60)/10,14.0,0xFFF4C9,.25+.2*f.drag)
        circle(c,mx,my,20+3*f.drag,0xFFF1BF);circle(c,mx+8,my-5,17+3*f.drag,0x2A3470)
        if(f.cloud>0) {
            val save=c.saveLayerAlpha(0f,0f,w.toFloat(),h.toFloat(),(255*.95*f.cloud).roundToInt())
            for(k in 0 until 6) {
                val side=if(k%2==1)1 else -1;val x=cx+side*(w*.75-f.cloud*w*(.45+.08*(k shr 1)));val y=cy+s*(.2+.35*(k shr 1))-s*.3
                circle(c,x,y,s*.5,0xE9ECFF);circle(c,x+s*.45,y+s*.1,s*.38,0xE9ECFF);circle(c,x-s*.45,y+s*.12,s*.36,0xE9ECFF)
            }
            c.restoreToCount(save)
        }
        text(c,"Tonight",14.0,semi,0x9FA9E0,cx,top+12,.5f)
        text(c,IslandSleepDialMotion.duration(f.h),44.0,bold,0xFFFFFF,cx,top+29)
        text(c,"Bedtime 11:00 PM  ·  Wake ${IslandSleepDialMotion.wake(f.h)}",14.0,regular,0xC9D2FF,cx,top+82)
        val save=c.saveLayerAlpha(0f,0f,w.toFloat(),h.toFloat(),(f.cloud*255).roundToInt())
        text(c,"Sleep well",28.0,bold,0x2A3470,cx,cy-20+(1-f.cloud)*10);c.restoreToCount(save)
        val chipY=h-bottom-18-56-12-34
        val tp=p(0xC9D2FF);tp.typeface=semi;tp.textSize=13f;val tw=tp.measureText("Rain on a tin roof").toDouble();val cw=tw+52
        c.drawRoundRect((cx-cw/2).toFloat(),chipY.toFloat(),(cx+cw/2).toFloat(),(chipY+34).toFloat(),17f,17f,p(0xFFFFFF,.1))
        text(c,"\uF46A",16.0,icons,0xC9D2FF,cx-cw/2+22,chipY+9)
        text(c,"Rain on a tin roof",13.0,semi,0xC9D2FF,cx+12,chipY+9)
        val by=h-bottom-18-56;val bs=c.save();if(pressed)c.scale(.96f,.96f,cx.toFloat(),(by+28).toFloat())
        c.drawRoundRect(20f,by.toFloat(),(w-20).toFloat(),(by+56).toFloat(),28f,28f,p(0xFFF1BF))
        val title=if(f.cloud>.5)"Alarm set" else "Set alarm";val bp=p(0x141A3C);bp.typeface=semi;bp.textSize=17f;val total=bp.measureText(title)+28
        text(c,"\uF10D",20.0,icons,0x141A3C,cx-total/2+10,by+18)
        text(c,title,17.0,semi,0x141A3C,cx+14,by+17.5);c.restoreToCount(bs)
    }
}
