package com.ngocdevv.nativecook.blocks.gestures.diceboardrewards

import android.content.Context
import android.graphics.*
import kotlin.math.*

internal class DiceBoardRewardsArt(context: Context) {
    private val m=DiceBoardRewardsMotion
    private val black=Typeface.createFromAsset(context.assets,"diceboardrewards/diceboardrewards_Nunito-Black.ttf")
    private val title=Typeface.createFromAsset(context.assets,"diceboardrewards/diceboardrewards_TitanOne-Regular.ttf")
    private val icons=Typeface.createFromAsset(context.assets,"diceboardrewards/diceboardrewards_Ionicons.ttf")
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private val shape=Path()
    private var cachedGeo: Geo?=null
    private var tilePictures: List<Picture> = emptyList()
    private var controlPictures: List<Picture> = emptyList()
    private var background: Shader?=null
    private fun prepare(g: Geo) {
        if(cachedGeo==g)return
        background=LinearGradient(0f,0f,0f,g.h.toFloat(),intArrayOf(color("FFE9D6"),color("FFF6EC"),color("F0EAFF")),null,Shader.TileMode.CLAMP)
        tilePictures=(0..11).map { i -> Picture().apply {
            val canvas=beginRecording(ceil(g.w).toInt(),ceil(g.h).toInt())
            tile(canvas,g,i,0.0);endRecording()
        } }
        controlPictures=(0..7).map { i -> Picture().apply {
            val canvas=beginRecording(ceil(g.w).toInt(),ceil(g.h).toInt())
            controls(canvas,g,i/2,i%2==1);endRecording()
        } }
        cachedGeo=g
    }
    data class Geo(val w: Double,val h: Double,val topInset: Double,val bottomInset: Double) {
        val tw=floor((w-20)/4+0.5); val th=tw/2; val cx=w/2; val top=floor(h*0.27+0.5); val trayY=h-bottomInset-175
        fun iso(x: Double,y: Double)=cx+(x-y)*tw*0.5 to top+(x+y)*th*0.5
        fun tileAt(index: Double): Pair<Double,Double> {
            val i=(floor(index).toInt()%12+12)%12; val f=index-floor(index)
            val a=DiceBoardRewardsMotion.path[i]; val b=DiceBoardRewardsMotion.path[(i+1)%12]
            val q=iso(a.first+(b.first-a.first)*f,a.second+(b.second-a.second)*f)
            return q.first to q.second+th*0.5
        }
    }
    fun color(hex: String,alpha: Double=1.0,shade: Double=1.0): Int {
        val n=hex.toLong(16).toInt()
        fun channel(s: Int)=min(255,(((n shr s) and 255)*shade).roundToInt())
        return Color.argb((alpha*255).roundToInt().coerceIn(0,255),channel(16),channel(8),channel(0))
    }
    private fun paint(hex: String,alpha: Double=1.0,shade: Double=1.0): Paint {
        p.shader=null; p.clearShadowLayer(); p.color=color(hex,alpha,shade); p.style=Paint.Style.FILL
        return p
    }
    private fun oval(c: Canvas,x: Double,y: Double,w: Double,h: Double,hex: String,a: Double=1.0) { c.drawOval(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),paint(hex,a)) }
    private fun rect(c: Canvas,x: Double,y: Double,w: Double,h: Double,hex: String,r: Double=0.0) { c.drawRoundRect(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),r.toFloat(),r.toFloat(),paint(hex)) }
    private fun polygon(c: Canvas,pts: List<Pair<Double,Double>>,hex: String,shade: Double=1.0) {
        shape.reset(); pts.forEachIndexed { i,q -> if(i==0)shape.moveTo(q.first.toFloat(),q.second.toFloat()) else shape.lineTo(q.first.toFloat(),q.second.toFloat()) }; shape.close()
        c.drawPath(shape,paint(hex,shade=shade))
    }
    private fun textPaint(size: Double,hex: String,face: Typeface=black): Paint { paint(hex); p.typeface=face; p.textSize=size.toFloat(); return p }
    fun width(s: String,size: Double,face: Typeface=black)=textPaint(size,"FFFFFF",face).measureText(s).toDouble()
    private fun text(c: Canvas,s: String,size: Double,hex: String,x: Double,y: Double,center: Boolean=false,baseline: Boolean=false,face: Typeface=black,alpha: Double=1.0) {
        textPaint(size,hex,face); p.alpha=(alpha*255).roundToInt(); val xx=if(center)x-p.measureText(s)/2 else x
        c.drawText(s,xx.toFloat(),(if(baseline)y else y-p.fontMetrics.ascent).toFloat(),p)
    }
    private fun tile(c: Canvas,g: Geo,i: Int,flip: Double) {
        val a=m.path[i]; val q=g.iso(a.first.toDouble(),a.second.toDouble()); val x=q.first; val cy=q.second+g.th*0.5
        val hw=g.tw*0.46; val hh=g.th*0.46
        polygon(c,listOf(x-hw to cy,x to cy+hh,x+hw to cy,x+hw to cy+12,x to cy+hh+12,x-hw to cy+12),m.colors[i],0.78)
        val sx=if(flip>0)abs(cos(flip*PI)) else 1.0; val revealed=flip>0.5 && flip<1.5
        polygon(c,listOf(x-hw*sx to cy,x to cy-hh,x+hw*sx to cy,x to cy+hh),if(revealed)"FFFFFF" else m.colors[i])
        if(sx<0.2)return
        c.save();c.translate(x.toFloat(),cy.toFloat());c.scale(sx.toFloat(),0.55f)
        if(revealed) { oval(c,-15.0,-15.0,30.0,30.0,"FF6B5A");oval(c,-7.0,-7.0,14.0,14.0,"FFFFFF") }
        else when(m.kinds[i]) {
            "coin" -> { oval(c,-14.0,-16.0,28.0,28.0,"F5B731");oval(c,-9.0,-11.0,18.0,18.0,"FFD766") }
            "gift" -> { rect(c,-13.0,-12.0,26.0,22.0,"FF6B8B");rect(c,-3.0,-12.0,6.0,22.0,"FFE066");rect(c,-15.0,-16.0,30.0,7.0,"FF8FA6") }
            "star" -> polygon(c,(0..9).map { val a=-PI/2+it*PI/5; val r=if(it%2==1)7.0 else 16.0; cos(a)*r to sin(a)*r },"9B6BFF")
            "coupon" -> rect(c,-18.0,-11.0,36.0,22.0,"FF8A3D",5.0)
            else -> rect(c,-22.0,-10.0,44.0,20.0,"FF8A3D",10.0)
        }
        c.restore()
        if(!revealed && m.labels[i].isNotEmpty()) {
            text(c,m.labels[i],11.0,if(m.kinds[i]=="start")"FFFFFF" else "3B3350",x,cy+if(m.kinds[i]=="start")4.0 else hh*0.75,true,true,alpha=if(m.kinds[i]=="start")1.0 else 0.8)
        }
    }
    private fun pawn(c: Canvas,x: Double,y: Double,hop: Double) {
        val h=sin(PI*hop)*26; val sq=1+0.12*sin(PI*hop)-0.14*max(0.0,1-hop*6)*(if(hop>0)1 else 0)
        oval(c,x-13+h*0.1,y-4,26-h*0.2,9.0,"3B3350",0.18-h*0.003)
        c.save();c.translate(x.toFloat(),(y-h).toFloat());c.scale(1f,sq.toFloat())
        shape.reset();shape.moveTo(-14f,0f);shape.quadTo(-12f,-22f,-6f,-30f);shape.lineTo(6f,-30f);shape.quadTo(12f,-22f,14f,0f);shape.close();c.drawPath(shape,paint("FF5C5C"))
        oval(c,-15.0,-5.0,30.0,9.0,"E84545");oval(c,-11.0,-49.0,22.0,22.0,"FF6E6E");oval(c,-7.5,-45.5,7.0,7.0,"FFFFFF",0.7);c.restore()
    }
    private val faces=arrayOf(doubleArrayOf(0.0,1.0,0.0,1.0,1.0,0.0,0.0,0.0,0.0,1.0),doubleArrayOf(0.0,-1.0,0.0,6.0,1.0,0.0,0.0,0.0,0.0,-1.0),doubleArrayOf(0.0,0.0,1.0,2.0,1.0,0.0,0.0,0.0,1.0,0.0),doubleArrayOf(0.0,0.0,-1.0,5.0,-1.0,0.0,0.0,0.0,1.0,0.0),doubleArrayOf(1.0,0.0,0.0,3.0,0.0,0.0,-1.0,0.0,1.0,0.0),doubleArrayOf(-1.0,0.0,0.0,4.0,0.0,0.0,1.0,0.0,1.0,0.0))
    private val pips=arrayOf(emptyList(),listOf(0.0 to 0.0),listOf(-0.5 to -0.5,0.5 to 0.5),listOf(-0.5 to -0.5,0.0 to 0.0,0.5 to 0.5),listOf(-0.5 to -0.5,0.5 to -0.5,-0.5 to 0.5,0.5 to 0.5),listOf(-0.5 to -0.5,0.5 to -0.5,0.0 to 0.0,-0.5 to 0.5,0.5 to 0.5),listOf(-0.5 to -0.55,0.5 to -0.55,-0.5 to 0.0,0.5 to 0.0,-0.5 to 0.55,0.5 to 0.55))
    private fun proj(x: Double,y: Double,s: Double,rx: Double,rz: Double,vx: Double,vy: Double,vz: Double): Triple<Double,Double,Double> {
        val b=vy*cos(rx)-vz*sin(rx);val z=vy*sin(rx)+vz*cos(rx)
        val a2=vx*cos(rz)-b*sin(rz);val b2=vx*sin(rz)+b*cos(rz)
        val a3=a2*cos(PI/4)+z*sin(PI/4);val c3=-a2*sin(PI/4)+z*cos(PI/4)
        return Triple(x+a3*s,y-(b2*cos(0.62)-c3*sin(0.62))*s,b2*sin(0.62)+c3*cos(0.62))
    }
    private fun die(c: Canvas,x: Double,y: Double,s: Double,rx: Double,rz: Double) {
        val depth=faces.map { proj(x,y,s,rx,rz,it[0],it[1],it[2]).third }
        val order=(0..5).toMutableList()
        for(i in 0..5) for(j in i+1..5)if(depth[order[j]]<depth[order[i]]) { val a=order[i];order[i]=order[j];order[j]=a }
        for(f in order) {
            if(depth[f]<=0.001)continue
            val a=faces[f]
            fun point(u: Double,v: Double,n: Double=1.0)=proj(x,y,s,rx,rz,a[0]*n+a[4]*u+a[7]*v,a[1]*n+a[5]*u+a[8]*v,a[2]*n+a[6]*u+a[9]*v)
            polygon(c,listOf(-0.92 to -0.92,0.92 to -0.92,0.92 to 0.92,-0.92 to 0.92).map { val q=point(it.first,it.second);q.first to q.second },"FFFFFF",0.72+0.28*depth[f])
            val v=a[3].toInt();val r=s*0.15*(0.6+0.4*depth[f])
            for(pip in pips[v]) { val q=point(pip.first,pip.second,1.001);oval(c,q.first-r,q.second-r,r*2,r*2,if(v==1)"FF4D5E" else "2E2A3D") }
        }
    }
    fun draw(c: Canvas,g: Geo,o: DiceBoardRewardsMotion.Out,reduced: Boolean,rolls: Int,pressed: Boolean) {
        prepare(g)
        val r=m.rollAt(o.th,o.u);val landed=(o.th.from+o.th.steps)%12
        val landedPrev=(o.prev.from+o.prev.steps)%12
        val flipPrev=if(o.uPrev>0)m.rollAt(o.prev,o.uPrev).flip else 0.0
        p.shader=background;p.alpha=255
        c.drawRect(0f,0f,g.w.toFloat(),g.h.toFloat(),p);p.shader=null
        val c0=g.iso(0.0,0.0);val c3=g.iso(4.0,4.0)
        oval(c,g.cx-g.tw*2.4,(c0.second+c3.second)/2-g.th*1.7+10,g.tw*4.8,g.th*4.4,"2D2550",0.07)
        for(i in 0..11) {
            val flip=if(i==landed && o.u>0)r.flip else if(i==landedPrev && flipPrev>0 && flipPrev<2)flipPrev else 0.0
            if(flip>0)tile(c,g,i,flip)
            else c.drawPicture(tilePictures[i])
        }
        val midC=g.iso(1.5,1.5);val mx=midC.first;val my=midC.second+g.th*0.5
        oval(c,mx-g.tw*0.8,my-g.th*0.55,g.tw*1.6,g.th*1.1,"FFFFFF",0.8)
        val pp=g.tileAt(if(o.u<0)o.th.from.toDouble() else r.pawn);val hop=if(o.u>1.15)r.pawn-floor(r.pawn) else 0.0
        val s=g.tw*0.3;var dx=g.cx;var dy=g.trayY;var rx=m.rotations[o.th.value].first;var rz=m.rotations[o.th.value].second
        if(o.u>=0 && r.home<1) {
            val f=r.fl;val ey=my-6
            dx=g.cx+(mx-g.cx)*m.easeOut(f);dy=g.trayY+(ey-g.trayY)*f-sin(PI*f)*170-r.bounce*60
            if(r.home>0) { val h=m.smoother(r.home);dx=mx+(g.cx-mx)*h;dy=ey+(g.trayY-ey)*h-sin(PI*h)*80 }
            if(!reduced) { rx=r.rx;rz=r.rz }
        }
        oval(c,dx-s*1.3,(if(o.u>=0 && r.home<1)my else g.trayY)+s*0.9,s*2.6,s*0.7,"2D2550",0.16)
        if(dy+s<pp.second) { die(c,dx,dy,s,rx,rz);pawn(c,pp.first,pp.second-4,hop) } else { pawn(c,pp.first,pp.second-4,hop);die(c,dx,dy,s,rx,rz) }
        c.drawPicture(controlPictures[rolls*2+(if(pressed)1 else 0)])
        val previousToast=if(o.uPrev>0)m.rollAt(o.prev,o.uPrev).toast else 0.0
        val v=max(r.toast,previousToast)
        if(v>0) {
            val index=m.toastIndex(o)
            val fallback=if(r.toast>0)landed else landedPrev
            val label=m.prizes[if(index>=0)index else fallback];val w=36+18+8+width(label,15.0)
            c.saveLayerAlpha(null,(v*255).roundToInt());c.translate(g.cx.toFloat(),(g.topInset+118+(1-v)*-16).toFloat());c.scale((0.94+0.06*v).toFloat(),(0.94+0.06*v).toFloat());c.translate((-w/2).toFloat(),-22f)
            rect(c,0.0,0.0,w,44.0,"FFFFFF",22.0);text(c,62698.toChar().toString(),18.0,"FF6B5A",18.0,13.0,face=icons);text(c,label,15.0,"2E2A3D",44.0,12.0);c.restore()
        }
    }
    fun rollWidth()=56+20+8+width("Roll",18.0)
    private fun controls(c: Canvas,g: Geo,rolls: Int,pressed: Boolean) {
        val y=g.topInset+8;oval(c,16.0,y,44.0,44.0,"FFFFFF");text(c,62026.toChar().toString(),22.0,"2E2A3D",38.0,y+11,true,face=icons)
        val coinW=24+16+6+width("2,480",14.0);val titleW=width("Lucky Lap",26.0,title);val gap=(g.w-32-44-coinW-titleW)/2
        text(c,"Lucky Lap",26.0,"2E2A3D",16+44+gap,y+5,face=title)
        val coinX=g.w-16-coinW;rect(c,coinX,y+5,coinW,34.0,"FFFFFF",17.0);oval(c,coinX+12,y+14,16.0,16.0,"FFD766");oval(c,coinX+15,y+17,10.0,10.0,"F5B731");text(c,"2,480",14.0,"2E2A3D",coinX+34,y+12)
        text(c,"Land on a coupon to win it for your basket",14.0,"8A8098",g.cx,g.topInset+62,true)
        text(c,"Flick the die up to roll",13.0,"A196B0",g.cx,g.trayY+52,true)
        val by=g.h-g.bottomInset-72;val bw=rollWidth();val bx=g.w-20-bw
        text(c,"$rolls rolls left today",15.0,"5B5270",20.0,by+17)
        c.save();if(pressed) { c.translate((bx+bw/2).toFloat(),(by+28).toFloat());c.scale(0.96f,0.96f);c.translate((-bx-bw/2).toFloat(),(-by-28).toFloat()) }
        rect(c,bx,by,bw,56.0,"D94A3A",28.0);c.save();c.clipRect(bx.toFloat(),by.toFloat(),(bx+bw).toFloat(),(by+52).toFloat());rect(c,bx,by,bw,56.0,"FF6B5A",28.0);c.restore()
        text(c,62116.toChar().toString(),20.0,"FFFFFF",bx+28,by+16,face=icons);text(c,"Roll",18.0,"FFFFFF",bx+56,by+13);c.restore()
    }
}
