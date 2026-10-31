package com.ngocdevv.nativecook.blocks.progress.potionvialupload

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.*

internal object PotionVialUploadMotion {
    const val LOOP = 12.0
    fun clamp(v: Double) = v.coerceIn(0.0, 1.0)
    fun seg(t: Double,a: Double,b: Double) = clamp((t-a)/(b-a))
    fun mix(a: Double,b: Double,k: Double) = a+(b-a)*k
    fun smooth(k: Double): Double { val x=clamp(k); return x*x*(3-2*x) }
    fun out(k: Double) = 1-(1-clamp(k)).pow(3)
    fun inside(k: Double) = clamp(k).pow(3)
    fun inOut(k: Double): Double { val x=clamp(k); return if(x<0.5)4*x*x*x else 1-(-2*x+2).pow(3)/2 }
    fun back(k: Double): Double { val x=clamp(k)-1; return 1+2.9*x.pow(3)+1.9*x*x }
    fun hash(i: Double): Double { val s=sin(i*12.9898+78.233)*43758.5453; return s-floor(s) }
    val keys=doubleArrayOf(2.73,1.0,2.8,3.0,2.87,5.0,2.93,7.0,3.0,8.0,3.07,10.0,3.13,12.0,3.2,14.0,3.27,15.0,3.33,17.0,3.4,18.0,3.47,20.0,3.53,21.0,4.0,27.0,4.4,34.0,4.8,40.0,5.2,48.0,5.6,58.0,6.0,60.0,6.4,67.0,6.8,78.0,7.2,82.0,7.6,86.0,8.0,94.0,8.1,96.0,8.2,97.0,8.3,99.0,8.4,100.0)
    fun progress(t: Double): Double {
        if(t<keys[0])return 0.0
        for(i in 0 until keys.size-2 step 2)if(t<=keys[i+2])return mix(keys[i+1],keys[i+3],(t-keys[i])/(keys[i+2]-keys[i]))/100
        return 1.0
    }
    // Construct from the binary Double, matching JavaScript toFixed rather than decimal ties.
    fun mbText(p:Double) = BigDecimal(p*48.2).setScale(1,RoundingMode.HALF_UP).toPlainString()+" of 48.2 MB"
    data class Point(val x:Double,val y:Double)
    data class Link(val x:Double,val y:Double,val a:Double)
    fun links(ax:Double,ay:Double,bx:Double,by:Double,length:Double): List<Link> {
        val d=max(0.01,hypot(bx-ax,by-ay)); val depth=sqrt(3*d*max(0.0,length-d)/8)
        val pts=(0..28).map { val s=it/28.0; Point(mix(ax,bx,s),mix(ay,by,s)+depth*4*s*(1-s)) }
        val links=mutableListOf<Link>(); var acc=0.0; var next=2.2
        for(i in 1 until pts.size) {
            val sx=pts[i].x-pts[i-1].x; val sy=pts[i].y-pts[i-1].y; val l=hypot(sx,sy)
            while(acc+l>=next) { val k=(next-acc)/l; links.add(Link(pts[i-1].x+sx*k,pts[i-1].y+sy*k,atan2(sy,sx))); next+=4.4 }; acc+=l
        }; return links
    }
    fun spider(t:Double): Point {
        if(t<2.85)return Point(39.0,-18.0)
        val k=smooth(seg(t,2.85,3.33)); val wander=sin(2*PI*(t-2.73)/2.4)*11
        val fx=min(mix(18.6,313.0,progress(t))-3,286.0); val tx=mix(39.0,max(fx,39.0),k); val ty=mix(-18.0,wander,k); val c=inOut(seg(t,7.6,8.15))
        return Point(mix(tx,296.0,c),mix(ty,-18.0,c))
    }
}
internal data class PotionVialUploadGeo(val w:Double,val h:Double,val top:Double,val bottom:Double) {
    val s=min(w/402,1.2); val ox=(w-402*s)/2
    private val cardTop=h-bottom-34-68*s; private val headBottom=top+128
    val cy=headBottom+(cardTop-headBottom)*0.4; val cardY=(cardTop-cy)/s
}
internal class PotionVialUploadGem(cork:Boolean) {
    var x=if(cork)39.5 else 0.0; var y=if(cork)-15.0 else 80.0; var vx=0.0; var vy=0.0
    var rot=if(cork)PI+0.5 else 0.0; var w=0.0; var corked=cork; var homeAt=-1.0
    var fromX=0.0; var fromY=0.0; var fromRot=0.0; var holdX=Double.NaN; var holdY=Double.NaN
    var lastHome=-10.0; var homeDur=0.34
    fun pop(strength:Double) { if(!corked && homeAt<0)return; corked=false; homeAt=-1.0; vx=70*strength; vy=-150*strength; w=5.5*strength }
    fun recork(now:Double,snap:Boolean=false) {
        if(corked && homeAt<0)return
        corked=true; homeAt=now; lastHome=now; homeDur=if(snap)0.07 else 0.34; fromX=x; fromY=y; fromRot=rot; vx=0.0; vy=0.0; w=0.0; holdX=Double.NaN
    }
    fun step(dt:Double,now:Double) {
        val m=PotionVialUploadMotion
        if(corked) {
            if(homeAt<0) { x=39.5; y=-15.0; rot=PI+0.5; return }
            val k=m.clamp((now-homeAt)/homeDur); val e=m.back(k); val lift=if(homeDur<0.1)0.0 else sin(PI*m.clamp(k*1.15))*26
            x=m.mix(fromX,39.5,e); y=m.mix(fromY,-15.0,e)-lift
            var d=PI+0.5-fromRot; d-=floor(d/(PI*2)+0.5)*PI*2; rot=fromRot+d*m.out(k)
            if(k>=1)homeAt=-1.0; return
        }
        val n=max(1,ceil(dt/(1.0/240)).toInt()); val h=dt/n
        repeat(n) {
            if(!holdX.isNaN()) { vx+=((holdX-x)*260-vx*26)*h; vy+=((holdY-y)*260-vy*26)*h } else vy+=980*h
            val drag=exp(-0.55*h); vx*=drag; vy*=drag; x+=vx*h; y+=vy*h
            val d=hypot(x,y); var taut=false
            if(d>80) { val nx=x/d; val ny=y/d; x=nx*80; y=ny*80; val radial=vx*nx+vy*ny; if(radial>0) { vx-=nx*radial; vy-=ny*radial }; taut=true }
            if(taut) { var diff=-atan2(x,y)-rot; diff-=floor(diff/(PI*2)+0.5)*PI*2; w+=(diff*70-w*9)*h } else w*=exp(-0.8*h)
            rot+=w*h
        }
    }
}
internal class PotionVialUploadDriver(var t:Double=0.0) {
    var mode=0; var sound=false; var last=-10.0; val gem=PotionVialUploadGem(!(t>=8.5 && t<11.55))
    var fileX=Double.NaN; var fileY=Double.NaN; var fileBack=1.0; var fileFromX=0.0; var fileFromY=0.0; var fileBackAt=-10.0
    var kickAt=-10.0; var kickDir=1.0; var dropX=Double.NaN; var dropY=Double.NaN
    fun step(dt:Double,now:Double,auto:Boolean):Int {
        var ev=0
        if(mode==3 && now-last>2) { mode=0; sound=false }
        if(mode==0 && auto) {
            val before=t; var next=before+dt
            if(next>=12) { next-=12; sound=false; dropX=Double.NaN }
            fun crossed(mark:Double)=before<mark && next>=mark
            if(crossed(1.0)) { gem.pop(0.8); ev=1 }; if(crossed(1.75))ev=2
            if(crossed(2.0)) { gem.recork(now,true); ev=3 }; if(crossed(8.5)) { gem.pop(1.0); ev=4 }
            if(crossed(11.6)) { gem.recork(now); ev=3 }; t=next
        }
        gem.step(dt,now); return ev
    }
    fun drop(now:Double) { t=1.75; mode=0; sound=true; last=now; dropX=fileX; dropY=fileY; fileX=Double.NaN }
}
