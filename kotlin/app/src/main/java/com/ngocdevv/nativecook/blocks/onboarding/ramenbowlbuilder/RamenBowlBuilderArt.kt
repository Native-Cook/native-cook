package com.ngocdevv.nativecook.blocks.onboarding.ramenbowlbuilder

import android.content.Context
import android.graphics.*
import kotlin.math.*

/** Static broth, body and topping pictures are recorded only when screen geometry changes. */
internal class RamenBowlBuilderArt(context: Context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val title = Typeface.createFromAsset(context.assets,"ramenbowlbuilder/ramenbowlbuilder_TitanOne-Regular.ttf")
    private val black = Typeface.createFromAsset(context.assets,"ramenbowlbuilder/ramenbowlbuilder_Nunito-Black.ttf")
    private val bold = Typeface.createFromAsset(context.assets,"ramenbowlbuilder/ramenbowlbuilder_Nunito-ExtraBold.ttf")
    private val buttonWidths = List(6) { count ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface=black; textSize=16f }.measureText(if(count>=5) "Next" else "${5-count} to go")+44.0
    }
    fun ctaWidth(count: Int) = buttonWidths[count.coerceIn(0,5)]
    private var width = 0.0
    private var height = 0.0
    private var density = 0f
    private var tops = emptyList<Bitmap>()
    private var broth: Bitmap? = null
    private var body: Bitmap? = null
    private var topPad = 0.0
    var buttonWidth = 0.0
        private set
    private fun color(hex: Int) = hex or 0xff000000.toInt()
    private fun ink(hex: Int,alpha: Double=1.0,stroke: Double=0.0): Paint {
        paint.reset(); paint.isAntiAlias=true; paint.color=color(hex); paint.alpha=(alpha*255).roundToInt()
        paint.style=if(stroke>0) Paint.Style.STROKE else Paint.Style.FILL
        paint.strokeWidth=stroke.toFloat(); paint.strokeCap=Paint.Cap.ROUND; paint.strokeJoin=Paint.Join.MITER
        return paint
    }
    private fun oval(c: Canvas,x: Double,y: Double,w: Double,h: Double,hex: Int) { c.drawOval(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),ink(hex)) }
    private fun circle(c: Canvas,x: Double,y: Double,r: Double,hex: Int) { c.drawCircle(x.toFloat(),y.toFloat(),r.toFloat(),ink(hex)) }
    private fun line(c: Canvas,x: Double,y: Double,xx: Double,yy: Double,hex: Int,stroke: Double,alpha: Double=1.0) { c.drawLine(x.toFloat(),y.toFloat(),xx.toFloat(),yy.toFloat(),ink(hex,alpha,stroke)) }
    private fun topping(c: Canvas,kind: Int,u: Double) {
        when(kind) {
            0 -> { oval(c,-u*0.55,-u*0.42,u*1.1,u*0.84,0xffffff); circle(c,0.0,0.0,u*0.26,0xffaa1f); circle(c,-u*0.06,-u*0.06,u*0.08,0xffd66b) }
            1 -> {
                circle(c,0.0,0.0,u*0.45,0xffffff); path.reset()
                for(k in 0..39) { val a=k*0.42; val r=u*0.04+k*u*0.0085; if(k==0) path.moveTo((cos(a)*r).toFloat(),(sin(a)*r).toFloat()) else path.lineTo((cos(a)*r).toFloat(),(sin(a)*r).toFloat()) }
                c.drawPath(path,ink(0xff7a9c,1.0,u*0.07))
                for(k in 0..11) { val a=k/12.0*PI*2; circle(c,cos(a)*u*0.45,sin(a)*u*0.45,u*0.06,0xffffff) }
            }
            2 -> {
                c.drawRoundRect((-u*0.32).toFloat(),(-u*0.55).toFloat(),(u*0.32).toFloat(),(u*0.55).toFloat(),(u*0.05).toFloat(),(u*0.05).toFloat(),ink(0x1f3b2e))
                line(c,-u*0.2,-u*0.4,-u*0.2,u*0.4,0x2f5442,1.5); line(c,u*0.1,-u*0.4,u*0.1,u*0.4,0x2f5442,1.5)
            }
            3 -> {
                c.drawArc((-u*0.45).toFloat(),(-u*0.45).toFloat(),(u*0.45).toFloat(),(u*0.45).toFloat(),200f,250f,false,ink(0xff7e4a,1.0,u*0.3))
                for(k in 0..3) { val a=(200+k*60)*PI/180; line(c,cos(a)*u*0.3,sin(a)*u*0.3,cos(a)*u*0.6,sin(a)*u*0.6,0xffffff,2.0,0.7) }
            }
            else -> { circle(c,0.0,0.0,u*0.45,0x1f3b2e); circle(c,0.0,0.0,u*0.36,0xffffff); circle(c,0.0,0.0,u*0.14,0xff8a4c); circle(c,u*0.1,-u*0.1,u*0.07,0x7cc46a) }
        }
    }
    private fun bitmap(w: Double,h: Double,d: Float,draw: (Canvas)->Unit): Bitmap {
        val b=Bitmap.createBitmap(ceil(w*d).toInt().coerceAtLeast(1),ceil(h*d).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
        val c=Canvas(b); c.scale(d,d); draw(c); return b
    }
    private fun prepare(w: Double,h: Double,d: Float,g: RamenBowlBuilderMotion.Geo) {
        if(width==w && height==h && density==d) return
        width=w; height=h; density=d
        val cx=g.cx; val cy=g.cy; val s=g.s; val u=s*0.56
        topPad=u*0.8
        tops=List(5) { i -> bitmap(topPad*2,topPad*2,d) { c -> c.translate(topPad.toFloat(),topPad.toFloat()); topping(c,i,u) } }
        broth=bitmap(w,h,d) { c ->
            oval(c,cx-s,cy-s*0.3,s*2,s*0.6,0xe8914a); oval(c,cx-s*0.92,cy-s*0.24,s*1.84,s*0.48,0xf6b460)
            for(k in 0..5) {
                val ny=cy-s*0.12+(k%3)*s*0.1
                path.reset(); path.moveTo((cx-s*0.7).toFloat(),ny.toFloat())
                for(q in 1..8) path.lineTo((cx-s*0.7+q*s*0.18).toFloat(),(ny+sin(q*1.7+k)*5).toFloat())
                c.drawPath(path,ink(0xffe08a,1.0,4.0))
            }
        }
        body=bitmap(w,h,d) { c ->
            path.reset(); path.moveTo((cx-s*1.08).toFloat(),cy.toFloat()); path.cubicTo((cx-s).toFloat(),(cy+s*0.95).toFloat(),(cx+s).toFloat(),(cy+s*0.95).toFloat(),(cx+s*1.08).toFloat(),cy.toFloat()); path.close()
            c.drawPath(path,ink(0xe8443a)); c.save(); c.clipPath(path)
            for(k in 0..13) circle(c,cx-s*0.95+(k%7)*s*0.32+(if(k>6) s*0.16 else 0.0),cy+s*0.22+(if(k>6) s*0.28 else 0.0),s*0.07,0xffffff)
            c.restore(); oval(c,cx-s*1.1,cy-s*0.08,s*2.2,s*0.16,0xc7342c); oval(c,cx-s*0.55,cy+s*0.68,s*1.1,s*0.16,0xb02c25)
        }
    }
    private fun image(c: Canvas,b: Bitmap,x: Double,y: Double,w: Double,h: Double) { c.drawBitmap(b,null,RectF(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat()),ink(0xffffff)) }
    private fun top(c: Canvas,i: Int) { image(c,tops[i],-topPad,-topPad,topPad*2,topPad*2) }
    fun draw(c: Canvas,w: Double,h: Double,d: Float,g: RamenBowlBuilderMotion.Geo,o: RamenBowlBuilderMotion.Frame,reduced: Boolean,topInset: Double,bottomInset: Double,pressed: Boolean) {
        prepare(w,h,d,g)
        c.drawColor(color(0xffc891))
        val cx=g.cx; val cy=g.cy; val s=g.s; val full=o.k.sum()/5
        for(k in 0..2) {
            val ph=o.t*2*PI/9+k*2.1; val x0=cx+(k-1)*s*0.35
            path.reset(); path.moveTo(x0.toFloat(),(cy-s*0.25).toFloat()); path.cubicTo((x0+sin(ph)*14).toFloat(),(cy-s*0.55).toFloat(),(x0-sin(ph)*14).toFloat(),(cy-s*0.8).toFloat(),(x0+sin(ph+1)*10).toFloat(),(cy-s*1.05).toFloat())
            c.drawPath(path,ink(0xffffff,0.25+0.45*full,7.0))
        }
        val lift=if(reduced) 0.0 else sin(o.t*2*PI*2/9)*6
        line(c,cx-s*0.1,cy-s*0.7+lift,cx+s*1.1,cy-s*1.15+lift,0xc2533c,7.0)
        line(c,cx-s*0.05,cy-s*0.58+lift,cx+s*1.15,cy-s+lift,0xd8644a,7.0)
        for(k in 0..3) { val nx=cx+s*0.05+k*7; line(c,nx,cy-s*0.62+lift,nx+sin(o.t*8*PI/9+k)*3,cy-s*0.1,0xffd878,5.0) }
        broth?.let { image(c,it,0.0,0.0,w,h) }
        for(i in 0..4) if(o.k[i]>0.999 && i!=o.dragI) { c.save(); c.translate(g.slots[i].x.toFloat(),g.slots[i].y.toFloat()); c.scale(1f,0.62f); top(c,i); c.restore() }
        body?.let { image(c,it,0.0,0.0,w,h) }
        for(i in 0..4) {
            val k=o.k[i]; if(k>0.999 && i!=o.dragI) continue
            val home=g.homes[i]; val bob=if(reduced) 0.0 else sin(o.t*2*PI*(1+i%2)/9+i*1.4)*8
            var x=home.x; var y=home.y+bob; var rot=if(reduced) 0.0 else sin(o.t*2*PI/9+i)*18; var sc=1.0
            if(i==o.dragI) { x=o.dx; y=o.dy; sc=1.12 }
            else if(o.back[i]>0) { x=RamenBowlBuilderMotion.mix(o.bx[i],home.x,RamenBowlBuilderMotion.smoother(o.back[i])); y=RamenBowlBuilderMotion.mix(o.by[i],home.y+bob,RamenBowlBuilderMotion.smoother(o.back[i])) }
            if(k>0 && i!=o.dragI) {
                val sx=if(o.back[i]<0) o.bx[i] else x; val sy=if(o.back[i]<0) o.by[i] else y
                x=RamenBowlBuilderMotion.mix(sx,g.slots[i].x,k); y=RamenBowlBuilderMotion.mix(sy,g.slots[i].y,k)-sin(PI*k)*s*0.6
                rot*=1-k; sc=RamenBowlBuilderMotion.mix(1.0,0.9,k)
            }
            c.save(); c.translate(x.toFloat(),y.toFloat()); c.rotate(rot.toFloat()); c.scale(sc.toFloat(),sc.toFloat()); top(c,i); c.restore()
            if(k>0.8 && k<1) { val sk=(k-0.8)/0.2; for(j in 0..5) { val a=PI+j/5.0*PI; circle(c,g.slots[i].x+cos(a)*s*0.3*sk,g.slots[i].y+sin(a)*s*0.25*sk,4*(1-sk),0xf6b460) } }
        }
        ui(c,w,h,topInset,bottomInset,o.count,pressed)
    }
    private fun text(c: Canvas,str: String,font: Typeface,size: Double,x: Double,y: Double,hex: Int,tracking: Double=0.0) {
        ink(hex); paint.typeface=font; paint.textSize=size.toFloat()
        if(tracking==0.0) c.drawText(str,x.toFloat(),(y-paint.fontMetrics.ascent).toFloat(),paint)
        else { var xx=x.toFloat(); for(ch in str) { val z=ch.toString(); c.drawText(z,xx,(y-paint.fontMetrics.ascent).toFloat(),paint); xx+=paint.measureText(z)+tracking.toFloat() } }
    }
    private fun ui(c: Canvas,w: Double,h: Double,top: Double,bottom: Double,count: Int,pressed: Boolean) {
        text(c,"STEP 2 OF 4",black,12.0,24.0,top+10,0xb4532b,1.4)
        text(c,"Top your bowl",title,30.0,24.0,top+30,0x3a1a10)
        text(c,"Drag the toppings into the broth",bold,15.0,24.0,top+68,0x8a4a2e)
        val y=h-bottom-94
        c.drawRoundRect(20f,y.toFloat(),(w-20).toFloat(),(y+76).toFloat(),26f,26f,ink(0xfff3e6))
        ink(0xffffff); paint.typeface=black; paint.textSize=16f
        val label=if(count>=5) "Next" else "${5-count} to go"
        buttonWidth=ctaWidth(count)
        val bx=w-30-buttonWidth; val by=y+10
        text(c,"Toppings $count/5",black,17.0,38.0,y+15,0x3a1a10)
        ink(0x9a6a50); paint.typeface=bold; paint.textSize=13f
        val sub=if(count==0) "Pick five to finish the bowl" else RamenBowlBuilderMotion.names.take(max(1,count)).joinToString(" · ")
        val limit=(bx-10-38).toFloat()
        var short=sub
        if(paint.measureText(short)>limit) { while(short.isNotEmpty() && paint.measureText("$short…")>limit) short=short.dropLast(1); short+="…" }
        text(c,short,bold,13.0,38.0,y+40,0x9a6a50)
        c.save(); if(pressed) c.scale(0.96f,0.96f,(bx+buttonWidth/2).toFloat(),(by+28).toFloat())
        c.drawRoundRect(bx.toFloat(),by.toFloat(),(bx+buttonWidth).toFloat(),(by+56).toFloat(),28f,28f,ink(if(count<5) 0xf2a090 else 0xe8443a))
        text(c,label,black,16.0,bx+22,by+16,0xffffff); c.restore()
    }
}
