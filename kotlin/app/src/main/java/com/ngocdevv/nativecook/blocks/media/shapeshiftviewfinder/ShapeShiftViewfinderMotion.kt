package com.ngocdevv.nativecook.blocks.media.shapeshiftviewfinder
import kotlin.math.*

internal class ShapeShiftViewfinderFrame {
    var tapA=300.0*PI/180
    var dent=0.0
    var press=1.0
    var x=0.0
    var y=0.0
    var k=1.0
    var rot=0.0
    var jx=0.0
    var jy=0.0
    var dx=1.0
    var dy=0.0
    var lift=0.0
    var sx=0.0
    var sy=0.0
    var sr=0.0
    var lx=0.0
    var ly=0.0
    var shx=0.0
    var shy=12.0
    var shr=0.0
    var flip=0.0
    var iris=1.0
    var twist=0.0
    var flash=0.0
    var blinkD=0.0
    var blinkA=0.0
    var dim=1.0
    var disc=1.0
    var f1u=-1.0
    var f2u=-1.0
    var sheet=0.0
    var segment=0.0
    var ring=1.0
    var dring=0.0
    var pressIdx=-1.0
    var pressS=1.0
    var phase=0.0
    var base=0x118169
    var f1c=0x118169
    var f2c=0x118169
    var dot=0x118169
    var w=ShapeShiftViewfinderGeometry.rest(0)
    var fr=doubleArrayOf(.473,.42,615.0,0.0)
    var hint=doubleArrayOf(1.0,0.0,0.0)
    var rings=doubleArrayOf(0.0,0.0,1.0,0.0,0.0,1.0)
    val scale:Double get()=k*press*(1+.03*(lift-1).coerceIn(0.0,1.0))
    val lean:Double get()=(jx*5600*.012).coerceIn(-9.0,9.0)
    fun copy():ShapeShiftViewfinderFrame {val f=ShapeShiftViewfinderFrame();f.tapA=tapA;f.dent=dent;f.press=press;f.x=x;f.y=y;f.k=k;f.rot=rot;f.jx=jx;f.jy=jy;f.dx=dx;f.dy=dy;f.lift=lift;f.sx=sx;f.sy=sy;f.sr=sr;f.lx=lx;f.ly=ly;f.shx=shx;f.shy=shy;f.shr=shr;f.flip=flip;f.iris=iris;f.twist=twist;f.flash=flash;f.blinkD=blinkD;f.blinkA=blinkA;f.dim=dim;f.disc=disc;f.f1u=f1u;f.f2u=f2u;f.sheet=sheet;f.segment=segment;f.ring=ring;f.dring=dring;f.pressIdx=pressIdx;f.pressS=pressS;f.phase=phase;f.base=base;f.f1c=f1c;f.f2c=f2c;f.dot=dot;f.w=w.copyOf();f.fr=fr.copyOf();f.hint=hint.copyOf();f.rings=rings.copyOf();return f}
}
internal object ShapeShiftViewfinderMath {
    fun seg(t:Double,a:Double,b:Double)=((t-a)/(b-a)).coerceIn(0.0,1.0)
    fun mix(a:Double,b:Double,k:Double)=a+(b-a)*k
    fun out(k:Double)=1-(1-k).pow(3)
    fun ease(k:Double)=if(k<.5)4*k*k*k else 1-(-2*k+2).pow(3)/2
    fun spring(t:Double,from:Double,to:Double,v:Double=0.0,d:Double=19.0,k:Double=195.0):Double {
        if(t<=0)return from;val x=from-to;val w=sqrt(k);val z=d/(2*w)
        if(z<1){val wd=w*sqrt(1-z*z);return to+exp(-z*w*t)*(x*cos(wd*t)+(v+z*w*x)/wd*sin(wd*t))}
        return to+exp(-w*t)*(x+(v+w*x)*t)
    }
    fun step(x:Double,v:Double,target:Double,dt:Double,d:Double,k:Double):Pair<Double,Double> {
        val a=x-target;val w=sqrt(k);val z=min(d/(2*w),.999);val wd=w*sqrt(1-z*z);val e=exp(-z*w*dt);val c=cos(wd*dt);val s=sin(wd*dt)
        return target+e*(a*c+(v+z*w*a)/wd*s) to e*(v*c-(k*a+z*w*v)/wd*s)
    }
    fun color(a:Int,b:Int,k:Double):Int {var out=0;for(shift in intArrayOf(16,8,0)){val x=((a shr shift)and 255).toDouble();val y=((b shr shift)and 255).toDouble();out=out or (mix(x,y,k).coerceIn(0.0,255.0).roundToInt() shl shift)};return out}
    fun iris(age:Double):DoubleArray {
        if(age<0)return doubleArrayOf(1.0,0.0,0.0,1.0,1.0,0.0,0.0)
        val cu=seg(age,.11,.37);val contour=if(age<.11)0.0 else 70*out(cu);val alpha=if(age<.11||age>.37)0.0 else .85*(1-cu)
        if(age<.11){val k=(age/.11).pow(3);return doubleArrayOf(1-.76*k,40*k,0.0,1-.4*out(age/.11),mix(1.0,.86,out(age/.11)),0.0,0.0)}
        if(age<.16)return doubleArrayOf(.24,40.0,.85,.6,.86,contour,alpha)
        val o=age-.16;return doubleArrayOf(.24+.76*spring(o,0.0,1.0),spring(o,40.0,0.0),0.0,mix(.6,1.0,out(seg(o,0.0,.18))),spring(o,.86,1.0,0.0,30.5,322.3),contour,alpha)
    }
}
internal object ShapeShiftViewfinderScore {
    val morphs=doubleArrayOf(.7,1.5,2.3,6.0,7.7,8.8)
    val taps=doubleArrayOf(300.0,60.0,180.0,240.0,120.0,0.0).map{it*PI/180}.toDoubleArray()
    fun shape(t:Double):Int {var out=0;for((i,m)in morphs.withIndex())if(t>=m)out=if(i==5)0 else i+1;return out}
    private fun rebound(t:Double):DoubleArray {val length=hypot(117.0,-52.0);val c=137.9;val w=2*PI/.3;val d=.7*w;val k=w*w;val m=ShapeShiftViewfinderMath;return doubleArrayOf(m.spring(t-4.35,117/length*c,61.0,0.0,d,k),m.spring(t-4.35,-52/length*c,-42.0,0.0,d,k))}
    fun drag(t:Double):DoubleArray {
        val m=ShapeShiftViewfinderMath
        if(t<4||t>=5.55)return doubleArrayOf(0.0,0.0)
        if(t<4.35){val s=when{t<4.1-> -12*m.ease(m.seg(t,4.0,4.1));t<4.27-> -12+112.7*m.seg(t,4.1,4.27).pow(1.69);t<4.3->100.7+37.2*m.seg(t,4.27,4.3);else->137.9};val l=hypot(117.0,-52.0);return doubleArrayOf(117/l*s,-52/l*s)}
        if(t<4.85)return rebound(t)
        if(t<5.25){val p=rebound(4.85);val k=m.ease(m.seg(t,4.85,5.25));return doubleArrayOf(m.mix(p[0],-71.0,k),m.mix(p[1],88.0,k))}
        val k=m.ease(m.seg(t,5.25,5.55));return doubleArrayOf(-71*(1-k),88*(1-k))
    }
    private val jelly:DoubleArray by lazy {
        val out=ArrayList<Double>();var jx=0.0;var jy=0.0;var vx=0.0;var vy=0.0;var dx=1.0;var dy=0.0;var previous=drag(0.0)
        val dt=1/2400.0;val w=2*PI/.3;val d=.7*w;val k=w*w
        for(i in 0..24000){if(i%20==0){out+=jx;out+=jy;out+=dx;out+=dy};val p=drag(i*dt+dt);var tx=(p[0]-previous[0])/dt/5600;var ty=(p[1]-previous[1])/dt/5600;previous=p;val m=hypot(tx,ty)
            if(m>.16){tx=tx/m*.16;ty=ty/m*.16};if(m>.01){dx=tx/m;dy=ty/m};vx+=(k*(tx-jx)-d*vx)*dt;vy+=(k*(ty-jy)-d*vy)*dt;jx+=vx*dt;jy+=vy*dt
        };out.toDoubleArray()
    }
    fun frame(raw:Double):ShapeShiftViewfinderFrame {
        val m=ShapeShiftViewfinderMath;val phase=((raw%10)+10)%10;val t=if(phase>=9.6)0.0 else phase;val f=ShapeShiftViewfinderFrame();f.phase=t
        for(q in 0..11){val tt=t-q*.2/11;for((index,start)in morphs.withIndex())if(tt>=start){val from=index%6;val to=if(index==5)0 else index+1;for(b in 0..5){val w=2*PI/(.42/(1+.35*b));val s=m.spring(tt-start,0.0,1.0,0.0,w,w*w);f.w[q*36+b*6+from]-=s;f.w[q*36+b*6+to]+=s}}}
        var tap= -1
        for((i,start)in morphs.withIndex()){if(t>=start-.1)tap=i;if(t>=start-.1&&t<start)f.press-=.04*m.out(m.seg(t,start-.1,start));if(t>=start)f.press-=m.spring(t-start,.04,0.0)}
        if(tap>=0){val start=morphs[tap];f.tapA=taps[tap];f.dent=10*(if(t<start)m.out(m.seg(t,start-.1,start-.01))else max(0.0,m.spring(t-start,1.0,0.0,0.0,30.5,322.3)));f.rings[0]=if(t<start).45 else .45*(1-m.seg(t,start,start+.24));f.rings[1]=f.tapA;f.rings[2]=m.mix(.6,1.0,m.out(m.seg(t,start-.1,start-.04)))}
        if(t>=3.1&&t<4){val a=if(t<3.75).45 else .45*(1-m.seg(t,3.75,3.99));f.rings[3]=a;f.rings[4]=PI/4;f.rings[5]=m.mix(.6,1.0,m.out(m.seg(t,3.1,3.16)));if(f.rings[0]<=0){f.rings[0]=a;f.rings[1]=5*PI/4;f.rings[2]=f.rings[5]}}
        f.k=when{t>=3.1&&t<3.7->m.mix(1.0,.7,m.ease(m.seg(t,3.1,3.7)));t>=3.7&&t<5.5->m.spring(t-3.7,.7,.72);t>=5.5&&t<7.6->m.spring(t-5.5,.72,1.0);t>=7.6&&t<8.8->m.mix(1.0,1.26,m.ease(m.seg(t,7.6,7.95)));t>=8.8&&t<9.15->m.mix(1.26,1.36,m.ease(m.seg(t,8.8,8.98)));t>=9.15->m.mix(1.36,1.0,m.ease(m.seg(t,9.15,9.55)));else->1.0}
        f.lift=when{t>=3.1&&t<4->m.out(m.seg(t,3.1,3.22));t>=4&&t<5.3->1+m.out(m.seg(t,4.0,4.12));t>=5.3&&t<5.9->2*(1-m.ease(m.seg(t,5.3,5.9)));else->0.0}
        val p=drag(t);val pa=drag(t+.004);val pb=drag(t-.004);val vx=(pa[0]-pb[0])/.008;val vy=(pa[1]-pb[1])/.008;val length=hypot(vx,vy);f.x=p[0];f.y=p[1]
        if(length>1e-3){val lag=min(length*.06,10.0);f.lx= -vx/length*lag;f.ly= -vy/length*lag}
        val jf=t*120;val i=min(jf.toInt(),1199);val u=jf-i;val j=DoubleArray(4){m.mix(jelly[i*4+it],jelly[(i+1)*4+it],u)}
        val dm=max(hypot(j[2],j[3]),1e-12);val strength=(j[0]*j[2]+j[1]*j[3])/dm;val cap=min(1.0,.16/max(abs(strength),.16));j[0]*=cap;j[1]*=cap;f.jx=j[0];f.jy=j[1];f.dx=j[2]/dm;f.dy=j[3]/dm
        val a=doubleArrayOf(.473,.42,615.0,0.0);val b=doubleArrayOf(.6,.62,500.0,0.0);val c=doubleArrayOf(.43,.5,560.0,-35.0)
        val from:DoubleArray;val to:DoubleArray;val fk:Double
        if(t<1.5){from=a;to=a;fk=0.0}else if(t<4.4){from=a;to=b;fk=m.ease(m.seg(t,1.5,3.0))}else if(t<8.8){from=b;to=c;fk=m.ease(m.seg(t,4.4,5.4))}else{from=c;to=a;fk=m.ease(m.seg(t,8.8,9.6))}
        f.fr=DoubleArray(4){m.mix(from[it],to[it],fk)}
        val angle=2*PI*t/10;val envelope=1-m.ease(m.seg(t,9.4,9.6));f.sx=(3*sin(2*angle)+sin(5*angle))*envelope;f.sy=(3*sin(3*angle)+.8*sin(4*angle))*envelope;f.sr=.4*sin(2*angle)*envelope
        val sh=2*PI*phase/10;f.shx=26*sin(sh)+9*sin(2*sh+1.3);f.shy=12*cos(sh);f.shr=2.5*sin(sh+.9)
        val iris=m.iris(t-8.3);f.iris=iris[0];f.twist=iris[1];f.flash=iris[2];f.dim=iris[3];f.disc=iris[4];f.blinkD=iris[5];f.blinkA=iris[6]
        for(row in arrayOf(doubleArrayOf(2.4,0x118169.toDouble(),0x2E6E54.toDouble()),doubleArrayOf(4.27,0x2E6E54.toDouble(),0xB9836B.toDouble()),doubleArrayOf(7.15,0xB9836B.toDouble(),0xF02FA6.toDouble()),doubleArrayOf(8.95,0xF02FA6.toDouble(),0x118169.toDouble()))){val start=row[0];val from=row[1].toInt();val to=row[2].toInt();if(t<start)break;val u=(t-start)/.58;if(u<1){f.base=from;f.f1c=to;f.f1u=u}else f.base=to;f.dot=m.color(from,to,m.seg(t,start+.2,start+.4))}
        f.f2c=f.base
        f.sheet=if(t>=6.5&&t<7.6)m.spring(t-6.5,0.0,1.0,0.0,22.1,157.9)else if(t>=7.6)max(0.0,m.spring(t-7.6,1.0,0.0,0.0,22.1,157.9))else 0.0
        f.segment=if(t>=6.9&&t<8.6)m.spring(t-6.9,0.0,1.0,0.0,30.5,322.3)else 0.0
        f.ring=if(t<7.1||t>=8.6)1.0 else m.spring(t-7.1,1.0,0.0,0.0,30.5,322.3)
        f.pressS=if(t>=7.1&&t<7.18)m.mix(1.0,.92,m.out(m.seg(t,7.1,7.18)))else if(t>=7.18&&t<7.8)m.spring(t-7.18,.92,1.0,0.0,30.5,322.3)else 1.0
        f.pressIdx=if(t>=7.1&&t<7.8)0.0 else -1.0
        f.hint=doubleArrayOf(if(t<3)1.0 else if(t<3.2)1-m.out(m.seg(t,3.0,3.2))else if(t<9.3)0.0 else m.out(m.seg(t,9.3,9.5)),if(t<3)0.0 else if(t<3.9)m.out(m.seg(t,3.0,3.2))else 1-m.out(m.seg(t,3.9,4.1)),if(t<3.9)0.0 else if(t<5.5)m.out(m.seg(t,3.9,4.1))else 1-m.out(m.seg(t,5.5,5.7)))
        return f
    }
}
