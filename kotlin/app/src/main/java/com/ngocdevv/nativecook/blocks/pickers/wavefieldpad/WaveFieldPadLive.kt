package com.ngocdevv.nativecook.blocks.pickers.wavefieldpad

import kotlin.math.*

/** Live state follows WaveFieldPad.tsx; raw positions are banded only when producing a frame. */
internal class WaveFieldPadLive(initial: WaveFieldPadMotion.Frame, prior: WaveFieldPadMotion.Frame, startNow: Double = 0.0, private val emit: (String) -> Unit = {}) {
    private val range=266.0; private val cap=14.0
    var u=unband(initial.puck.u); private set
    var v=unband(initial.puck.v); private set
    var vu=0.0; private set
    var vv=0.0; private set
    private var du=initial.dent.u; private var dv=initial.dent.v
    private var dvu=(initial.dent.u-prior.dent.u)*60; private var dvv=(initial.dent.v-prior.dent.v)*60
    private var beat=initial.beat; private var settleLin=inverseSettle(initial.settle); private var ring=initial.ring
    private var r0=ripBack(initial.rip0); private var r1=ripBack(initial.rip1); private var ripNext=0
    private var mode=0; private var targetU=.5; private var targetV=.5; private var offU=0.0; private var offV=0.0
    private var press=((initial.depth-16)/6).coerceIn(0.0,1.0); private var pressTo=0.0
    private var still=if(initial.settle>0) .25 else 0.0
    private var anchorX=initial.puck.u*range; private var anchorY=initial.puck.v*range
    private var formed=initial.settle>.99; private var ticks=if(initial.settle>0) 7 else 0
    private var lock=initial.lock; private var flash=if(initial.ring>0) initial.ring*.5 else -1.0; private var blip=-1.0
    private var now=startNow; private var lastTouch=-10.0; private var touching=false; private var returning=false
    private var prevU=initial.puck.u; private var prevV=initial.puck.v; private var prevHz=initial.hz
    private var velocityX=initial.velocity.u; private var velocityY=initial.velocity.v
    private var wallIn=true; private var reduced=false
    var readyForAuto=false; private set
    private val tickValues=DoubleArray(7).also { a -> var c=0.0; for(i in a.indices){c+=.09-.05*i/6;a[i]=c/.9} }

    private fun bezier(x1:Double,y1:Double,x2:Double,y2:Double,k:Double):Double {
        if(k<=0) return 0.0; if(k>=1) return 1.0
        var s=k; for(i in 0 until 6){val x=3*(1-s)*(1-s)*s*x1+3*(1-s)*s*s*x2+s*s*s-k;val dx=3*(1-s)*(1-s)*x1+6*(1-s)*s*(x2-x1)+3*s*s*(1-x2);if(abs(dx)<1e-6) break;s=(s-x/dx).coerceIn(0.0,1.0)}
        return 3*(1-s)*(1-s)*s*y1+3*(1-s)*s*s*y2+s*s*s
    }
    private fun ease(k:Double)=bezier(.3,.35,.25,1.0,k)
    private fun out(k:Double)=bezier(.2,.8,.2,1.0,k)
    private fun inverseSettle(value:Double):Double { var lo=0.0;var hi=1.0;repeat(20){val m=(lo+hi)/2;if(ease(m)<value)lo=m else hi=m};return (lo+hi)/2 }
    private fun band(x:Double)=when { x>1 -> 1+cap*tanh((x-1)*range/cap)/range; x<0 -> -cap*tanh(-x*range/cap)/range; else -> x }
    private fun unband(x:Double):Double { if(x in 0.0..1.0)return x;val q=((if(x>1)x-1 else -x)*range/cap).coerceIn(0.0,1-1e-12);val raw=cap*.5*(ln1p(q)-ln1p(-q))/range;return if(x>1)1+raw else -raw }
    private fun none()=WaveFieldPadMotion.Ripple(0.0,0.0,9.0,0.0)
    private fun amp(r:WaveFieldPadMotion.Ripple)=if(r.age<.6)r.amplitude*(1-out(r.age/.6)) else 0.0
    private fun ripBack(r:WaveFieldPadMotion.Ripple)=r.copy(amplitude=if(r.amplitude>0)min(10.0,r.amplitude/max(1-out(min(r.age/.6,.999)),.05)) else 0.0)
    private fun addRipple(x:Double,y:Double,amp:Double) { if(reduced||amp<.3)return;val r=WaveFieldPadMotion.Ripple(x,y,0.0,amp);if(ripNext==0)r0=r else r1=r;ripNext=1-ripNext }
    private fun takeover(){returning=false;touching=true;lastTouch=now;readyForAuto=false}
    fun policy(reduce:Boolean) {
        if(reduce&&!reduced)settleLin=ease(settleLin) else if(!reduce&&reduced)settleLin=inverseSettle(settleLin)
        reduced=reduce
        if(reduce){r0=none();r1=none();ring=0.0;flash=-1.0;blip=-1.0}
    }
    fun down(x:Double,y:Double) { takeover();targetU=x;targetV=y;offU=x-u;offV=y-v;vu=0.0;vv=0.0;mode=1;pressTo=1.0;addRipple(44+x*range,44+(1-y)*range,8.0);emit("soft") }
    fun move(x:Double,y:Double) { targetU=x;targetV=y;lastTouch=now }
    fun release(xVelocity:Double,yVelocity:Double) { vu=xVelocity;vv=yVelocity;mode=2;val sp=hypot(xVelocity,yVelocity)*range;if(sp>200)addRipple(44+band(u)*range,44+(1-band(v))*range,6*min(1.0,sp/1500));finalTouch() }
    private fun finalTouch() { if(mode==1){vu=0.0;vv=0.0;mode=2};pressTo=0.0;touching=false;lastTouch=now }
    fun adjust(x:Double,y:Double) { takeover();u=(u+x).coerceIn(0.0,1.0);v=(v+y).coerceIn(0.0,1.0);vu=0.0;vv=0.0;mode=0;finalTouch();emit("sel") }
    fun setMix(value:Double) { takeover();u=value.coerceIn(0.0,1.0);vu=0.0;vv=0.0;mode=0;finalTouch();emit("sel") }
    fun reset() { takeover();val away=abs(u-.5)>.01||abs(v-.5)>.01;if(away)addRipple(44+band(u)*range,44+(1-band(v))*range,8.0);vu=0.0;vv=0.0;mode=3;blip=0.0;emit("medium");touching=false;lastTouch=now }
    fun step(dtIn:Double,allowAuto:Boolean) {
        val dt=dtIn.coerceIn(0.0,.05);now+=dt;var x=u;var y=v
        when {
            reduced&&mode!=1 -> {if(mode==3){x=.5;y=.5};x=x.coerceIn(0.0,1.0);y=y.coerceIn(0.0,1.0);vu=0.0;vv=0.0;mode=0}
            mode==1 -> {val k=if(reduced)1.0 else 1-exp(-dt/.045);offU*=1-k;offV*=1-k;val nx=targetU-offU;val ny=targetV-offV;vu+=( (nx-x)/max(dt,.001)-vu)*.5;vv+=((ny-y)/max(dt,.001)-vv)*.5;x=nx;y=ny}
            mode==2||mode==3 -> { repeat(4){val h=dt/4;fun axis(p:Double,vel:Double):Pair<Double,Double>{val a=if(mode==3)-195*(p-.5)-19*vel else if(p>1)-93.4*(p-1)-15.1*vel else if(p<0)-93.4*p-15.1*vel else 0.0;var nv=vel+a*h;if(mode==2&&p in 0.0..1.0)nv*=.994.pow(h*1000);return Pair(p+nv*h,nv)};val a=axis(x,vu);val b=axis(y,vv);x=a.first;vu=a.second;y=b.first;vv=b.second}
                if(mode==2&&x in 0.0..1.0&&y in 0.0..1.0&&abs(vu)*range<8&&abs(vv)*range<8)mode=0
                if(mode==3&&abs(x-.5)<.0015&&abs(y-.5)<.0015&&abs(vu)<.01&&abs(vv)<.01){x=.5;y=.5;vu=0.0;vv=0.0;mode=0}
            }
        }
        u=x;v=y;val outside=x<0||x>1||y<0||y>1
        if(outside&&wallIn&&mode!=1){val sp=hypot(vu,vv)*range;addRipple(if(x>1)354.0 else if(x<0)0.0 else 44+x.coerceIn(0.0,1.0)*range,if(y<0)354.0 else if(y>1)0.0 else 44+(1-y.coerceIn(0.0,1.0))*range,min(10.0,3+sp/200));emit("rigid")};wallIn=!outside
        val su=band(x);val sv=band(y)
        repeat(4){val h=dt/4;dvu+=(-110*(du-su)-11*dvu)*h;dvv+=(-110*(dv-sv)-11*dvv)*h;du+=dvu*h;dv+=dvv*h}
        if(reduced){du=su;dv=sv;dvu=0.0;dvv=0.0}
        press=if(reduced)0.0 else press+(pressTo-press)*(1-exp(-dt/.03))
        val hz=WaveFieldPadMotion.hzAt(su,sv)
        if(mode==1||mode==2){if((prevU-.5)*(su-.5)<0||(prevV-.5)*(sv-.5)<0)emit("sel");if((prevHz-1.5)*(hz-1.5)<0)emit("light")}
        val ddt=max(dt,.001);velocityX+=(((su-prevU)*range)/ddt-velocityX)*.35;velocityY+=((-(sv-prevV)*range)/ddt-velocityY)*.35
        val ptx=su*range;val pty=sv*range;val speed=hypot((su-prevU)*range,(sv-prevV)*range)/ddt
        prevU=su;prevV=sv;prevHz=hz;still=if(speed<12)still+dt else 0.0
        val s0=settleLin
        if(!returning&&still>=.25){anchorX=ptx;anchorY=pty;settleLin=min(1.0,s0+dt/(if(reduced).2 else .9))}
        else if(returning||hypot(ptx-anchorX,pty-anchorY)>6){settleLin=max(0.0,s0-dt/(if(reduced).2 else .3));if(!returning&&ticks>0&&s0>0&&settleLin<s0){ticks=0;emit("light")}}
        val s1=settleLin
        if(s1>s0&&ticks<7&&s1>=tickValues[ticks]){ticks++;emit("sel")}
        if(s1>=1&&!formed){formed=true;if(!reduced){ring=.0001;flash=0.0};emit("soft")}else if(s1<.5){formed=false;if(s1==0.0&&ticks>0&&s0==0.0)ticks=0}
        if(ring>0)ring=if(ring+dt/.5>=1)0.0 else ring+dt/.5
        if(flash>=0)flash=if(flash+dt>.5)-1.0 else flash+dt
        lock=if(formed)min(1.0,lock+dt/.08) else max(0.0,lock-dt/.15)
        beat+=hz*dt;if(r0.age<9)r0=r0.copy(age=r0.age+dt);if(r1.age<9)r1=r1.copy(age=r1.age+dt)
        if(blip>=0)blip=if(blip+dt>.36)-1.0 else blip+dt
        if(allowAuto&&!touching&&now-lastTouch>2){if(mode==0&&!returning&&(abs(u-.5)>.0015||abs(v-.5)>.0015))mode=3;returning=true
            val calm=mode==0&&settleLin<.002&&amp(r0)<.05&&amp(r1)<.05&&blip<0&&ring==0.0
            if(calm&&abs(du-.5)<.002&&abs(dv-.5)<.002&&press<.01)readyForAuto=true }
    }
    fun frame(time:Double):WaveFieldPadMotion.Frame {
        val p=WaveFieldPadMotion.P(band(u),band(v));val base=WaveFieldPadMotion.frameAt(0.0);val settle=if(reduced)settleLin else ease(settleLin)
        val hum=if(reduced)0.0 else .012*settle*sin(2*PI*3*now)
        val scale=if(reduced)1.0 else 1-.06*press+(if(blip>=0).08*sin(PI*blip/.36) else 0.0)+hum
        val halo=.38+.17*press+.07*settle+(if(reduced)0.0 else .06*(1-cos(2*PI*6*now/9))*(1-press))
        return base.copy(puck=p,dent=WaveFieldPadMotion.P(du,dv),settle=settle,beat=beat,hz=WaveFieldPadMotion.hzAt(p.u,p.v),ring=ring,lock=lock,scale=scale,depth=16+6*press,halo=halo,flash=if(flash>=0)sin(PI*flash/.5) else 0.0,rip0=r0.copy(amplitude=amp(r0)),rip1=r1.copy(amplitude=amp(r1)),wall=WaveFieldPadMotion.P((max(0.0,p.u-1)-max(0.0,-p.u))*range*.8,(max(0.0,-p.v)-max(0.0,p.v-1))*range*.8),scatter=if(reduced)WaveFieldPadMotion.P(0.0,0.0) else WaveFieldPadMotion.P(-.3*(p.u-.5)*range,.3*(p.v-.5)*range),velocity=WaveFieldPadMotion.P(velocityX,velocityY),jitter=if(reduced)0.0 else floor(now*30)%270,time=time)
    }
}
