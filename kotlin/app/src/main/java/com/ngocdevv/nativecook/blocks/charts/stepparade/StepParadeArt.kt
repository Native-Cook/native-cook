package com.ngocdevv.nativecook.blocks.charts.stepparade

import android.content.Context
import android.graphics.*
import kotlin.math.*

internal class StepParadeArt(context: Context) {
    private val faces = listOf("Regular","SemiBold","Bold").associateWith {
        Typeface.createFromAsset(context.assets,"stepparade/stepparade_Inter-$it.ttf")
    } + ("Ionicons" to Typeface.createFromAsset(context.assets,"stepparade/stepparade_Ionicons.ttf"))
    private val texts = mutableMapOf<String,Paint>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val box = RectF()
    private lateinit var c: Canvas
    private var w=0.0; private var h=0.0; private var top=0.0; private var bottom=0.0
    private val gy get()=top+268
    private val gw get()=w-32
    private fun p(col: Int,a: Double=1.0,stroke: Double=0.0): Paint = paint.apply {
        shader=null; color=(a.coerceIn(0.0,1.0)*255).roundToInt().shl(24) or col
        style=if(stroke==0.0)Paint.Style.FILL else Paint.Style.STROKE
        strokeWidth=stroke.toFloat(); strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND
    }
    private fun rect(x: Double,y: Double,w: Double,h: Double): RectF { box.set(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat()); return box }
    private fun rr(x: Double,y: Double,w: Double,h: Double,r: Double,col: Int,a: Double=1.0,stroke: Double=0.0) { c.drawRoundRect(rect(x,y,w,h),r.toFloat(),r.toFloat(),p(col,a,stroke)) }
    private fun circle(x: Double,y: Double,r: Double,col: Int,a: Double=1.0,stroke: Double=0.0) { c.drawCircle(x.toFloat(),y.toFloat(),r.toFloat(),p(col,a,stroke)) }
    private fun line(x: Double,y: Double,x2: Double,y2: Double,col: Int,sw: Double,a: Double=1.0) { c.drawLine(x.toFloat(),y.toFloat(),x2.toFloat(),y2.toFloat(),p(col,a,sw)) }
    private fun text(s: String,x: Double,y: Double,size: Double,weight: String="Regular",col: Int=0x1B1E26,anchor: Double=0.0,lineH: Double?=null,tabular: Boolean=false) {
        val paint=texts.getOrPut("$weight-$size-$tabular") { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface=faces[weight]; textSize=size.toFloat(); fontFeatureSettings=if(tabular)"tnum" else null } }
        paint.color=col or 0xFF000000.toInt()
        val fm=paint.fontMetrics
        val lh=lineH?:ceil((fm.descent-fm.ascent).toDouble())
        c.drawText(s,(x-paint.measureText(s)*anchor).toFloat(),(y+(lh-fm.descent-fm.ascent)/2).toFloat(),paint)
    }
    private fun icon(g: Int,x: Double,y: Double,size: Double,col: Int=0x1B1E26) = text(String(Character.toChars(g)),x,y,size,"Ionicons",col)
    private fun avatar(i: Int,x: Double,y: Double,s: Double,ring: Boolean=false) {
        val f=StepParadeMotion.friends[i]
        c.save(); path.reset(); path.addCircle((x+s/2).toFloat(),(y+s/2).toFloat(),(s/2).toFloat(),Path.Direction.CW); c.clipPath(path)
        circle(x+s/2,y+s/2,s/2,f.coat,0.2)
        val b=if(ring)2.5 else 0.0
        rr(x+s*0.11,y+s*0.68-b,s*0.78,s*0.5,s*0.25,f.coat)
        circle(x+s/2,y+b+s*0.49,s*0.23,f.skin)
        path.reset(); val hx=x+s*0.25; val hy=y+b+s*0.14; val hw=s*0.5; val hh=s*0.24
        path.moveTo(hx.toFloat(),(hy+hh).toFloat())
        path.arcTo(rect(hx,hy,2*hh,2*hh),180f,90f,false)
        path.lineTo((hx+hw-hh).toFloat(),hy.toFloat())
        path.arcTo(rect(hx+hw-2*hh,hy,2*hh,2*hh),270f,90f,false)
        path.close();c.drawPath(path,p(f.hat))
        if(ring)circle(x+s/2,y+s/2,(s-2.5)/2,0x1B1E26,1.0,2.5)
        c.restore()
    }
    private fun walker(x: Double,y: Double,s: Double,ph: Double,i: Int,sel: Int) {
        val f=StepParadeMotion.friends[i]; val sw=sin(ph); val bob=abs(cos(ph))*2*s
        c.drawOval(rect(x-13*s,y-3*s,26*s,6*s),p(0x5C7A99,0.18))
        val hip=y-17*s-bob
        line(x,hip,x+sin(sw*0.55)*14*s,hip+cos(sw*0.55)*15*s,0x2B2F3A,6.5*s)
        line(x,hip,x-sin(sw*0.55)*14*s,hip+cos(sw*0.55)*15*s,0x3A3F4C,6.5*s)
        val sh=hip-17*s
        line(x-2*s,sh+3*s,x-2*s+sin(sw*0.6)*11*s,sh+3*s+cos(sw*0.6)*12*s,f.coat,6*s,0.8)
        rr(x-11*s,sh-4*s,22*s,24*s,10*s,f.coat)
        line(x-9*s,sh+8*s,x+9*s,sh+8*s,0xFFFFFF,1.4*s,0.35)
        line(x+2*s,sh+3*s,x+2*s-sin(sw*0.6)*11*s,sh+3*s+cos(sw*0.6)*12*s,f.coat,6*s)
        val hy=sh-12*s
        circle(x+s,hy,10*s,f.skin);circle(x+5*s,hy+s,1.4*s,0x1B1E26);circle(x+9*s,hy+s,1.4*s,0x1B1E26)
        path.reset();path.addArc(rect(x-9*s,hy-12*s,20*s,20*s),180f,180f);path.close();c.drawPath(path,p(f.hat))
        rr(x-10*s,hy-4*s,22*s,5*s,2.5*s,f.hat);circle(x+s,hy-13*s,3.4*s,0xFFFFFF)
        if(sel==i)circle(x,y-1,16*s,0x1B1E26,0.25,1.5)
    }
    private fun sky(hour: Double,isTop: Boolean): Int {
        val hs=doubleArrayOf(6.0,9.0,13.0,17.5,19.5,22.0)
        val cols=if(isTop)intArrayOf(0xFFB9A6,0xBFDBFF,0xA9CEFF,0x9EC2F7,0xFF9F7A,0x1F2A57) else intArrayOf(0xFFE9DD,0xEFF6FF,0xEAF4FF,0xF2EADF,0xFFD9BF,0x4B5B95)
        var i=0;while(i<4&&hour>hs[i+1])i++
        val k=StepParadeMotion.clamp((hour-hs[i])/(hs[i+1]-hs[i]))
        fun ch(shift: Int)=StepParadeMotion.mix(((cols[i] shr shift)and 255).toDouble(),((cols[i+1] shr shift)and 255).toDouble(),k).roundToInt()
        return Color.rgb(ch(16),ch(8),ch(0))
    }
    private fun gradient(x: Double,y: Double,rw: Double,rh: Double,y0: Double,y1: Double,c0: Int,c1: Int) {
        val p=p(0xFFFFFF);p.shader=LinearGradient(0f,y0.toFloat(),0f,y1.toFloat(),c0,c1,Shader.TileMode.CLAMP)
        c.drawRect(rect(x,y,rw,rh),p);p.shader=null
    }
    private fun scene(hour: Double,sel: Int) {
        c.save();path.reset();path.addRoundRect(rect(16.0,gy,gw,252.0),28f,28f,Path.Direction.CW);c.clipPath(path)
        gradient(16.0,gy,gw,252.0,gy,gy+126,sky(hour,true),sky(hour,false))
        val night=StepParadeMotion.clamp((hour-19)/1.5);val arc=StepParadeMotion.clamp((hour-6)/16)
        val sx=46+arc*(gw-60);val sy=gy+252*0.36-sin(arc*PI)*252*0.22
        circle(sx,sy,15.0,0xFFD36B,1-night);circle(sx,sy,24.0,0xFFD36B,0.18*(1-night))
        if(night>0) {
            circle(sx,sy,12.0,0xF4F1E1,night);circle(sx+5,sy-3,10.0,0x1F2A57,night)
            for(k in 0..13)circle(16+(k*97%360)*gw/360,gy+12+(k*53%60),1.2,0xFFFFFF,0.8*night)
        }
        val hor=gy+252*0.4
        path.reset();path.moveTo(16f,hor.toFloat())
        for(k in 0..24)path.lineTo((16+gw*k/24).toFloat(),(hor-16-12*sin(k*0.9)-8*sin(k*2.3)).toFloat())
        path.lineTo((16+gw).toFloat(),hor.toFloat());path.close();c.drawPath(path,p(if(night>0.5)0x9AA9CF else 0xD8E6F4))
        gradient(16.0,hor,gw,252.0,hor,gy+252,0xFFF3F8FD.toInt(),0xFFDCE9F5.toInt())
        for(k in 0..4) {
            val px=56+k*gw/5+(k%2)*18;val py=hor+2+(k%2)*4
            path.reset()
            for((a,b,rw) in listOf(Triple(34.0,-8.0,11.0),Triple(24.0,2.0,14.0))) {
                path.moveTo(px.toFloat(),(py-a).toFloat());path.lineTo((px+rw).toFloat(),(py+b).toFloat());path.lineTo((px-rw).toFloat(),(py+b).toFloat());path.close()
            }
            c.drawPath(path,p(0x2E6B5E));circle(px-4,py-22,2.2,0xFFFFFF,0.9)
        }
        val snx=16+gw-34
        circle(snx,hor+22,13.0,0xFFFFFF);circle(snx,hor+4,9.0,0xFFFFFF);circle(snx+3,hor+3,1.3,0x1B1E26);line(snx+6,hor+6,snx+13,hor+7,0xFF8A3D,2.4)
        for(i in 0..5) {
            val st=StepParadeMotion.steps(i,hour);val at=StepParadeMotion.place(w,top,i,st);val start=40+i*7.0
            var fx=start
            while(fx<at.x-10*at.s) {
                val odd=((fx-start)/(9*at.s)).roundToInt()%2
                c.drawOval(rect(fx,at.y-1.5*at.s+(if(odd==1)2.5 else -1.0)*at.s,4.5*at.s,2.2*at.s),p(0xB8CCE0,0.75));fx+=9*at.s
            }
            walker(at.x,at.y,at.s,st*0.006,i,sel)
        }
        if(night>0)rr(16.0,gy,gw,252.0,0.0,0x1E2A57,0.22*night)
        c.restore()
    }
    fun draw(canvas: Canvas,width: Double,height: Double,safeTop: Double,safeBottom: Double,hour: Double,sel: Int,bubbleSelected: Int) {
        c=canvas;w=width;h=height;top=safeTop;bottom=safeBottom
        c.drawColor(0xFFF7F8FA.toInt())
        rr(16.0,top+8,104.0,36.0,18.0,0xFFFFFF);rr(16.5,top+8.5,103.0,35.0,17.5,0xE6E8EE,1.0,1.0)
        text("Friends",31.0,top+17,15.0,"SemiBold");icon(0xf232,89.0,top+18,16.0);avatar(1,w-54,top+7,38.0)
        val wins=intArrayOf(0,1,3,0,2,4,0);val days=listOf("M","T","W","T","F","S","S")
        for(i in 0..6) { val x=16+i*(w-72)/6;text(days[i],x+20,top+60,12.0,"SemiBold",if(i==6)0x1B1E26 else 0x9AA0AE,0.5);avatar(wins[i],x+4,top+81,32.0,i==6) }
        val steps=StepParadeMotion.steps(1,hour)
        text("Steps today",w/2,top+140,15.0,"SemiBold",0x6B7280,0.5)
        text(StepParadeMotion.number(steps),w/2,top+159,56.0,"Bold",0x1B1E26,0.5,64.0,true)
        text(StepParadeMotion.rank(hour),w/2,top+223,14.0,"Regular",0x6B7280,0.5)
        scene(hour,sel)
        rr(30.0,gy+14,101.0,28.0,14.0,0xFFFFFF,0.86);icon(0xf5de,40.0,gy+21,14.0);text(StepParadeMotion.time(hour),59.0,gy+20,13.0,"SemiBold",tabular=true)
        for(i in 0..5) {
            val at=StepParadeMotion.place(w,top,i,StepParadeMotion.steps(i,hour));val y=at.y-60*at.s-28;val f=StepParadeMotion.friends[i]
            val namePaint=texts.getOrPut("SemiBold-11.0") { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface=faces["SemiBold"];textSize=11f } }
            val nameW=namePaint.measureText(f.name).toDouble();val bw=nameW+18+(if(bubbleSelected==i)48.0 else 0.0);val x=at.x-bw/2
            rr(x,y,bw,22.0,11.0,if(i==1)0x1B1E26 else 0xFFFFFF);rr(x+0.5,y+0.5,bw-1,21.0,10.5,if(i==1)0x1B1E26 else 0xE3E7EE,1.0,1.0)
            text(f.name,x+9,y+4,11.0,"SemiBold",if(i==1)0xFFFFFF else 0x1B1E26)
            if(bubbleSelected==i)text(StepParadeMotion.number(StepParadeMotion.steps(i,hour)),x+13+nameW,y+4,11.0,"Regular",if(i==1)0xFFFFFF else 0x1B1E26,tabular=true)
        }
        val ty=gy+268
        rr(36.0,ty+9,gw-40,4.0,2.0,0xE3E7EE)
        val tx=36+StepParadeMotion.clamp((hour-6)/16)*(gw-40)
        circle(tx,ty+11,10.0,0x1B1E26);circle(tx,ty+11,5.0,0xFFFFFF)
        for((i,l)in listOf("6 AM","10 AM","2 PM","6 PM","10 PM").withIndex())text(l,36+i*(gw-40)/4,ty+24,11.0,"Regular",0x9AA0AE,0.5)
        val sy=gy+322;val cw=(gw-12)/2;val mins=(steps/110).roundToInt()
        for(i in 0..1) {
            val x=16+i*(cw+12);val tint=if(i==0)0x3D6BFF else 0xFF7A45
            rr(x,sy,cw,64.0,18.0,0xFFFFFF);rr(x+0.5,sy+0.5,cw-1,63.0,17.5,0xECEEF2,1.0,1.0)
            rr(x+13,sy+14,36.0,36.0,12.0,tint,26.0/255);icon(if(i==0)0xf470 else 0xf314,x+22,sy+23,18.0,tint)
            text(if(i==0)String.format(java.util.Locale.US,"%.1f km",steps*0.72/1000)else"${mins/60}h ${mins%60}m",x+59,sy+13,17.0,"Bold",tabular=true)
            text(if(i==0)"Distance"else"Active time",x+59,sy+35,12.0,"Regular",0x9AA0AE)
        }
        val tabY=h-bottom-62
        rr(0.0,tabY,w,h-tabY,0.0,0xFFFFFF);line(0.0,tabY,w,tabY,0xE3E7EE,0.33)
        val icons=intArrayOf(0xf383,0xf49f,0xf602,0xf4ac)
        for((i,label)in listOf("Home","Friends","Challenges","Me").withIndex()) { val x=w*(i+0.5)/4;val col=if(i==1)0x1B1E26 else 0x9AA0AE;icon(icons[i],x-11.5,tabY+13,23.0,col);text(label,x,tabY+38,11.0,"SemiBold",col,0.5) }
    }
}
