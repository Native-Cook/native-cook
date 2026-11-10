package com.ngocdevv.nativecook.blocks.gestures.serumdropperroutine

import android.content.Context
import android.graphics.*
import android.os.Build
import androidx.core.graphics.PathParser
import kotlin.math.*

/** Static text, paths and decoded products are retained outside the frame callback. */
internal class SerumDropperRoutineArt(context: Context) {
    private val products=listOf("bottle","dropper","tube","jar","white").associateWith { name ->
        context.assets.open("serumdropperroutine/serumdropperroutine_sdr-$name.png").use { requireNotNull(BitmapFactory.decodeStream(it)) }
    }
    private val faces=listOf("Regular","Medium","SemiBold").associateWith { Typeface.createFromAsset(context.assets,"serumdropperroutine/serumdropperroutine_Poppins-$it.ttf") }
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val check=path("M1.5 5.2 L4.8 8.4 L11.5 1.6")
    private val back=path("M7.5 1.5 L1.8 7.5 L7.5 13.5")
    private val nav=listOf(
        path("M11 2 L19.5 7 V16 A3 3 0 0 1 16.5 19 H5.5 A3 3 0 0 1 2.5 16 V7 Z"),
        path("M3 4 H19 V15 H9 L5 19 V15 H3 Z"),
        path("M11 2 L18.8 6.5 V15.5 L11 20 L3.2 15.5 V6.5 Z"),
        path("M11 18.2 C4 13.4 1.6 10.2 1.6 6.6 A4.8 4.8 0 0 1 11 4.6 A4.8 4.8 0 0 1 20.4 6.6 C20.4 10.2 18 13.4 11 18.2 Z")
    )
    private val scan=path("M2 8 V5 A3 3 0 0 1 5 2 H8 M18 2 H21 A3 3 0 0 1 24 5 V8 M24 18 V21 A3 3 0 0 1 21 24 H18 M8 24 H5 A3 3 0 0 1 2 21 V18")
    private val smile=path("M9 16 Q13 19 17 16")
    private val runtime=if(Build.VERSION.SDK_INT>=33) runCatching { SerumDropperRoutineShader() }.getOrElse { throw IllegalStateException("Serum oil AGSL failed",it) } else null
    private var cached: SerumDropperRoutineLayout?=null
    private var density=1f
    private var background: Bitmap?=null
    private var foreground: Bitmap?=null
    companion object {
        val names=arrayOf("100% Organic\nFruit Oil","Mineral UV\nFilters SPF","Niacinamide\n10% + Zinc 1%","Hyaluronic\nAcid Powder")
        val colors=intArrayOf(0xE7EDF8,0xFBEDE3,0xEEEAF8,0xFBEDE3)
        private fun path(data: String)=requireNotNull(PathParser.createPathFromPathData(data))
        private fun color(hex: Int,a: Double=1.0)=(a.coerceIn(0.0,1.0).times(255).roundToInt() shl 24) or hex
    }
    private fun fill(hex: Int,a: Double=1.0): Paint {
        paint.reset(); paint.flags=Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG; paint.color=color(hex,a); return paint
    }
    private fun stroke(hex: Int,w: Double,a: Double=1.0): Paint = fill(hex,a).apply { style=Paint.Style.STROKE; strokeWidth=w.toFloat(); strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND }
    private fun rect(x: Double,y: Double,w: Double,h: Double)=RectF(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat())
    private fun rr(c: Canvas,x: Double,y: Double,w: Double,h: Double,r: Double,hex: Int,a: Double=1.0) { c.drawRoundRect(rect(x,y,w,h),r.toFloat(),r.toFloat(),fill(hex,a)) }
    private fun text(c: Canvas,s: String,x: Double,y: Double,size: Double,weight: String="Regular",hex: Int=0x151518,lineHeight: Double?=null,center: Boolean=false) {
        textPaint.typeface=faces.getValue(weight); textPaint.textSize=size.toFloat(); textPaint.color=color(hex)
        textPaint.textAlign=if(center) Paint.Align.CENTER else Paint.Align.LEFT
        val metrics=textPaint.fontMetrics
        s.split('\n').forEachIndexed { i,line ->
            c.drawText(line,x.toFloat(),(y-metrics.ascent+i*(lineHeight?:ceil((metrics.descent-metrics.ascent).toDouble()))).toFloat(),textPaint)
        }
    }
    private fun image(c: Canvas,name: String,x: Double,y: Double,w: Double,h: Double) {
        c.drawBitmap(products.getValue(name),null,rect(x,y,w,h),fill(0xffffff))
    }
    private fun layer(l: SerumDropperRoutineLayout,draw: (Canvas)->Unit): Bitmap {
        val b=Bitmap.createBitmap((l.w*density).roundToInt(),(l.h*density).roundToInt(),Bitmap.Config.ARGB_8888)
        val c=Canvas(b); c.scale(density,density); draw(c); return b
    }
    private fun cache(l: SerumDropperRoutineLayout,d: Float) {
        if(cached==l && density==d) return
        background?.recycle(); foreground?.recycle(); cached=l; density=d
        background=layer(l) { c ->
            c.drawColor(Color.WHITE)
            // Top panel has only its bottom corners rounded.
            rr(c,0.0,0.0,l.w,l.y(270.0),28.0,0xF6F6F7)
            c.drawRect(0f,0f,l.w.toFloat(),28f,fill(0xF6F6F7))
            for(x in doubleArrayOf(46.0,352.0)) rr(c,x*l.sx-22,l.y(98.0)-22,44.0,44.0,12.0,0xffffff)
            c.save(); c.translate((46*l.sx-4.5).toFloat(),(l.y(98.0)-7.5).toFloat()); c.drawPath(back,stroke(0x151518,1.8)); c.restore()
            for(x in doubleArrayOf(3.0,11.0)) c.drawCircle((352*l.sx-7+x).toFloat(),l.y(98.0).toFloat(),1.8f,fill(0x151518))
            text(c,"Daily routine",80*l.sx,l.y(100.0)-12,16.0,"Medium")
            val xs=doubleArrayOf(25.0,105.0,185.0,264.0,345.0)
            val days=arrayOf("Mon","Tue","Wed","Thu","Fri")
            for(i in 0..4) {
                if(i==1) rr(c,xs[i]*l.sx,l.y(150.0),75*l.sx,100*l.k,22.0,0xffffff)
                val cx=(xs[i]+37.5)*l.sx
                val cy=l.y(150.0)+50*l.k
                text(c,"${i+5}",cx,cy-28,24.0,"Medium",center=true)
                text(c,days[i],cx,cy+10,12.0,if(i==1)"Medium" else "Regular",if(i==1)0x151518 else 0xB3B3B8,center=true)
            }
            SerumDropperRoutineLayout.cards.forEachIndexed { i,b -> rr(c,b[0]*l.sx,l.y(b[1]),b[2]*l.sx,b[3]*l.k,22.0,colors[i]) }
            image(c,"tube",243*l.sx,l.y(483.0)-75*l.sx,100*l.sx,150*l.sx)
            image(c,"jar",47*l.sx,l.y(712.0)-45.75*l.sx,122*l.sx,91.5*l.sx)
            image(c,"white",258*l.sx,l.y(782.0)-70*l.sx,70*l.sx,140*l.sx)
        }
        foreground=layer(l) { c ->
            SerumDropperRoutineLayout.labels.forEachIndexed { i,p ->
                text(c,names[i],p.first*l.sx,l.y(p.second)-10,15.0,lineHeight=20.0)
                text(c,"Step ${i+1}",p.first*l.sx,l.y(p.second)+34,11.0,hex=0xB3B3B8)
            }
            rr(c,0.0,l.y(780.0),l.w,l.h-l.y(780.0),28.0,0xffffff)
            c.drawRect(0f,(l.y(780.0)+28).toFloat(),l.w.toFloat(),l.h.toFloat(),fill(0xffffff))
            doubleArrayOf(66.0,122.0,276.0,326.0).forEachIndexed { i,x ->
                c.save(); c.translate((x*l.sx-11).toFloat(),(l.y(780.0)+40-(if(i==3)10 else 11)).toFloat())
                c.drawPath(nav[i],stroke(0x9A9AA4,1.6))
                when(i) {
                    0->rr(c,9.5,12.0,3.0,4.0,1.0,0x9A9AA4)
                    1->for(dx in floatArrayOf(7f,11f,15f)) c.drawCircle(dx,9.5f,1f,fill(0x9A9AA4))
                    2->c.drawCircle(11f,11f,3f,stroke(0x9A9AA4,1.6))
                }
                c.restore()
            }
            c.drawCircle((200*l.sx).toFloat(),l.y(795.0).toFloat(),36f,fill(0xffffff))
            c.drawCircle((200*l.sx).toFloat(),l.y(795.0).toFloat(),32f,fill(0x151518))
            c.save(); c.translate((200*l.sx-13).toFloat(),(l.y(795.0)-13).toFloat())
            c.drawPath(scan,stroke(0xffffff,1.8)); c.drawPath(smile,stroke(0xffffff,1.6))
            for(x in floatArrayOf(9.5f,16.5f)) c.drawCircle(x,10.5f,1.3f,fill(0xffffff))
            c.restore()
        }
    }
    fun draw(c: Canvas,l: SerumDropperRoutineLayout,f: SerumDropperRoutineMotion.Frame,ticks: BooleanArray,d: Float) {
        cache(l,d)
        c.drawBitmap(background!!,0f,0f,fill(0xffffff))
        c.save(); c.scale(d,d)
        val tip=l.tip(f.lift); val bulb=l.dh*0.3
        c.save(); c.translate((tip.first-l.dw/2).toFloat(),(l.dtop+tip.second-l.seatTipY).toFloat())
        c.save(); c.translate((l.dw/2).toFloat(),(bulb/2+bulb*0.2*f.squeeze/2).toFloat()); c.scale((1+0.1*f.squeeze).toFloat(),(1-0.2*f.squeeze).toFloat()); c.translate((-l.dw/2).toFloat(),(-bulb/2).toFloat())
        c.clipRect(rect(0.0,0.0,l.dw,bulb)); image(c,"dropper",0.0,0.0,l.dw,l.dh); c.restore()
        c.save(); c.clipRect(rect(0.0,bulb,l.dw,l.dh-bulb)); image(c,"dropper",0.0,0.0,l.dw,l.dh); c.restore(); c.restore()
        image(c,"bottle",l.bx-l.bw/2,l.btop,l.bw,l.bh)
        if(Build.VERSION.SDK_INT>=33 && runtime!=null) {
            runtime.update(l,f); val p=fill(0xffffff); p.shader=runtime.shader; c.drawRect(0f,0f,l.w.toFloat(),l.h.toFloat(),p); p.shader=null
        } else fallback(c,l,f)
        SerumDropperRoutineLayout.cards.forEachIndexed { i,b ->
            val x=(b[0]+134)*l.sx; val y=l.y(b[1])+32
            val checked=if(i==0) f.done>0.5 else ticks[i]
            c.drawCircle(x.toFloat(),y.toFloat(),if(checked)15f else 14.3f,if(checked)fill(0xffffff,0.95) else stroke(0xffffff,1.4,0.95))
            if(i==0 || checked) {
                val amount=if(i==0) f.done else 1.0; val s=0.6+0.4*amount
                c.save(); c.translate(x.toFloat(),y.toFloat()); c.scale(s.toFloat(),s.toFloat()); c.translate(-6.5f,-5f)
                c.drawPath(check,stroke(0x151518,1.8,amount)); c.restore()
            }
        }
        c.restore()
        c.drawBitmap(foreground!!,0f,0f,fill(0xffffff))
        if(f.finger>0) {
            c.save(); c.scale(d,d)
            val x=if(f.fingerAt==0)160*l.sx else l.bx+6
            val y=if(f.fingerAt==0)l.y(308.0)+32 else l.btop+l.bh*0.55
            val s=(1-0.12*f.press).toFloat()
            c.translate(x.toFloat(),y.toFloat()); c.scale(s,s)
            c.drawCircle(0f,0f,22f,fill(0x141414,0.06*f.finger))
            c.drawCircle(0f,0f,21.25f,stroke(0x141414,1.5,0.35*f.finger)); c.restore()
        }
    }
    private fun fallback(c: Canvas,l: SerumDropperRoutineLayout,f: SerumDropperRoutineMotion.Frame) {
        val tip=l.tip(f.lift)
        f.drops.forEachIndexed { i,a ->
            if(a<0)return@forEachIndexed
            val r=4.8*l.sx
            val tau=max(0.0,a-0.71)
            val y0=tip.second+r+3.5*l.sx
            val land=sqrt(max(0.0,2*(l.floor-r*0.5-y0)/(1500*l.sx)))
            val x=l.liftX+doubleArrayOf(0.0,7.0,-6.0)[i]*l.sx
            if(a<0.71) c.drawCircle(tip.first.toFloat(),(tip.second+r).toFloat(),(r*(0.35+0.65*min(1.0,a/0.55))).toFloat(),fill(0xE9A23B,0.85))
            else if(tau<land) c.drawCircle(tip.first.toFloat(),(y0+750*l.sx*tau*tau).toFloat(),r.toFloat(),fill(0xE9A23B,0.85))
            else { val rr=(6+12*(1-exp(-4.5*(tau-land))))*l.sx; c.drawOval(rect(x-rr,l.floor-rr*0.3,2*rr,0.6*rr),fill(0xE9A23B,0.55*(1-f.soak))) }
        }
    }
    fun close() { background?.recycle(); foreground?.recycle(); products.values.forEach { it.recycle() } }
}
