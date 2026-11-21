package com.ngocdevv.nativecook.blocks.modals.islandglassorb

import kotlinx.coroutines.channels.Channel
import kotlin.math.*

/** UI-thread owner of the RN AUTO / LIVE / SEQ state machine and the finger's handover. */
internal class IslandGlassOrbPlayback(var autoPlay: Boolean, var reduced: Boolean) {
    private val motion=IslandGlassOrbMotion
    private val m=IslandGlassOrbSpring(); private val pill=IslandGlassOrbSpring(); private val ban=IslandGlassOrbSpring()
    private val dx=IslandGlassOrbSpring(); private val dy=IslandGlassOrbSpring()
    private val mic=IslandGlassOrbSpring(); private val spin=IslandGlassOrbSpring()
    private val springs=listOf(m,pill,ban,dx,dy,mic,spin)
    var mode=if(autoPlay && !reduced)0 else 1; private set
    var t=0.0; private set
    var now=0.0; private set
    var clockT=0.0; private set
    var seqT=0.0; private set
    private var seqStart=0.0; private var seqFrom=motion.frame(0.0); private var seqBlend=DoubleArray(43)
    private var replyAt=-1.0; private var lastTouch=-10.0
    private var listening=false; private var amp=0.0; private var silentFor=0.0
    private var pluckX=0.0; private var pluckD=0.0; private var pluckRel=-1.0; private var pluckD0=0.0
    var height=874.0
    var pin: Double?=null
    private var broken=false; private var pinchAt=-10.0; private var mergeAt=-10.0
    private var e=0.0
    private fun autoFrame(t: Double): IslandGlassOrbFrame {
        val f=motion.frame(t); val a=min(1.0,max(.35,(762+min(0.0,height-874)-16-276)/434))
        val cy=58+f.dy; val x=(200+f.dx-201)*(a-1)*motion.smooth(250.0,300.0,cy); val y=max(0.0,cy-276)*(a-1); val mc=motion.clamp(f.m)
        return f.copy(dx=f.dx+x,dy=f.dy+y,lineX=f.lineX+x*mc,lineY=f.lineY+y*mc)
    }
    var level: Double?=null
    var holdSeconds=6.0
    var onListen: ()->Unit={}; var onSubmit: ()->Unit={}; var onCancel: ()->Unit={}; var onReplyEnd: ()->Unit={}
    var buzz: (String)->Unit={}
    val wake=Channel<Unit>(Channel.CONFLATED)
    private var touching=false; private var downT=0.0; private var moved=0.0
    private var region=0; private var fired=false; private var grab=false; private var plucking=false
    private var scrub=false; private var atLimit=false; private var lineSide=0; private var lastSelection=0.0
    private var grabX=0.0; private var grabY=0.0; private var grabTX=0.0; private var grabTY=0.0
    private var lastTX=0.0; private var lastTY=0.0
    fun requestFrame() { wake.trySend(Unit) }
    fun configure(auto: Boolean,reduce: Boolean) {
        autoPlay=auto; reduced=reduce
        if((!auto || reduce) && mode==0) { setLiveFrom(motion.frame(0.0)); mode=1 }
        requestFrame()
    }
    private fun spring(s: IslandGlassOrbSpring,to: Double,d: Double,k: Double,v: Double?=null) = s.animate(to,now,d,k,reduced,v)
    private fun live(): IslandGlassOrbFrame {
        val f=motion.live(m.value,pill.value,ban.value,dx.value,dy.value,amp,mic.value,spin.value,pluckX,pluckD,clockT)
        val shape=motion.dropShape(hypot(dx.value,dy.value),if(broken)1.0 else 0.0,now-pinchAt,0.0)
        val tm=now-mergeAt
        return f.copy(e=e,causticAmp=amp,free=shape.free,cap=shape.cap,neck=shape.neck,kJoin=shape.kJoin,iw=shape.iw,ih=shape.ih+if(tm>=0 && tm<.3)10*sin(PI*tm/.3) else 0.0)
    }
    private fun sequence(): IslandGlassOrbFrame {
        val s=seqT
        if(reduced) {
            val think=motion.thinkRest(); val rep=motion.replyRest()
            return when {
                s<4.878 -> motion.mixFrames(seqFrom,think,motion.seg(s,4.678,4.878))
                s<5.95 -> think
                s<6.15 -> motion.mixFrames(think,rep,motion.seg(s,5.95,6.15))
                s<8.95 -> rep
                s<9.15 -> motion.mixFrames(rep,motion.frame(0.0),motion.seg(s,8.95,9.15))
                else -> motion.frame(0.0)
            }
        }
        val f=motion.frame(s); val k=1-motion.out(motion.seg(s-seqStart,0.0,.35))
        if(k<=0)return f
        val v=f.values(); for(i in motion.blendKeys)v[i]+=seqBlend[i]*k
        return IslandGlassOrbFrame.from(v)
    }
    fun frame() = when(mode) { 0->autoFrame(pin?:t); 2->sequence(); else->live() }
    fun lobeTime() = when(mode) { 0->pin?:t; 2->seqT; else->clockT }
    private fun setLiveFrom(f: IslandGlassOrbFrame) {
        m.set(f.m); pill.set(f.pill); ban.set(f.ban); dx.set(f.dx); dy.set(f.dy)
        broken=f.free>.9 && hypot(f.dx,f.dy)>160; pinchAt=if(broken)now-.16 else -10.0; mergeAt=-10.0; e=f.e
        amp=f.amp; mic.set(f.mic); spin.set(f.spinA); pluckX=f.pluckX; pluckD=0.0
    }
    private fun toIdle() {
        spring(m,0.0,30.5,322.3); spring(pill,0.0,30.5,322.3); spring(ban,0.0,22.1,157.9)
        spring(dx,0.0,22.1,157.9); spring(dy,0.0,22.1,157.9)
        mic.timing(0.0,now,.15,reduced); spin.timing(0.0,now,.15,reduced); listening=false
    }
    private fun startListening() {
        spring(m,1.0,19.0,195.0); spring(pill,0.0,30.5,322.3); spring(ban,0.0,19.0,195.0)
        mic.timing(1.0,now,.15,reduced); spin.timing(0.0,now,.15,reduced); listening=true; silentFor=0.0; mode=1
        buzz("soft"); onListen()
    }
    private fun submit() {
        val lf=live(); val v=lf.values(); val f0=motion.frame(4.678).values()
        seqBlend=DoubleArray(v.size) { v[it]-f0[it] }; seqFrom=lf; seqT=4.678; seqStart=4.678
        replyAt=-1.0; listening=false; mode=2; onSubmit()
    }
    private fun cancel() { setLiveFrom(sequence()); mode=1; toIdle(); buzz("light"); onCancel() }
    private fun collapse() { spring(ban,0.0,22.1,157.9); pill.set(0.0); m.set(0.0); onReplyEnd() }
    private fun takeOver() {
        pin?.let { t=it }; pin=null
        if(mode!=0)return
        val f=autoFrame(t)
        if(t>=4.678 && t<9.55) {
            seqT=t; seqStart=t; seqBlend=DoubleArray(43); seqFrom=f
            replyAt=if(t>=6.25)now-(t-6.25) else -1.0; mode=2; return
        }
        setLiveFrom(f); clockT=t; mode=1
        if(t>=.62 && t<4.678) {
            listening=true; silentFor=0.0; spring(m,1.0,19.0,195.0)
            spring(dx,0.0,10.0,220.0); spring(dy,0.0,10.0,220.0); mic.timing(1.0,now,.15,reduced)
        } else toIdle()
    }
    fun needsFrames() = mode==0 || mode==2 || listening || touching || springs.any { it.active } || pluckRel>=0 || (autoPlay && !reduced && now-lastTouch<=2.05) || (ban.value>.5)
    fun advance(raw: Double) {
        val dt=min(raw,.05); now+=dt
        if(pin!=null)return
        springs.forEach { it.update(now) }
        if(mode==0) { t=(t+dt)%motion.LOOP; return }
        clockT=(clockT+dt)%motion.LOOP
        if(touching && !fired && moved<10 && now-downT>=.22) {
            if(region==1 || region==3) {
                fired=true; if(region==3)m.set(0.0); startListening()
                if(region==1) { grab=true; grabX=0.0; grabY=0.0; grabTX=lastTX; grabTY=lastTY; dx.set(0.0); dy.set(0.0) }
            } else if(region==2 && !plucking && !grab) {
                fired=true; grab=true; grabX=dx.value; grabY=dy.value; grabTX=lastTX; grabTY=lastTY; dx.set(dx.value); dy.set(dy.value)
            }
        }
        if(mode==1 && m.value>.5) {
            val travel=hypot(dx.value,dy.value)
            if(!broken && travel>=250) { broken=true; pinchAt=now; mergeAt=-10.0; e=-.07; buzz("rigid") }
            else if(broken && !grab && travel<30) { broken=false; mergeAt=now; buzz("medium") }
        }
        if(listening) {
            val target=if(plucking).6 else level?.coerceIn(0.0,1.0)?:0.0
            val tau=if(target>amp).06 else .18; amp+=(target-amp)*(1-exp(-dt/tau))
            silentFor=if(amp<.08 && !touching)silentFor+dt else 0.0
            if(silentFor>=1.2 && m.value>.9 && !broken && hypot(dx.value,dy.value)<20)submit()
        } else if(mode==1)amp*=exp(-dt/.18)
        if(pluckRel>=0) {
            val tau=now-pluckRel
            if(tau>.5) { pluckRel=-1.0; pluckD=0.0 } else pluckD=pluckD0*cos(2*PI*6*tau)*exp(-tau/.11)
        }
        if(mode==2) {
            var s=seqT+dt
            if(s>=6.25 && replyAt<0)replyAt=now
            if(s>8.9 && seqT<=8.9 && now-max(replyAt,lastTouch)<holdSeconds)s=8.9
            seqT=s
            if(s>=9.95) { setLiveFrom(motion.frame(0.0)); mode=1; lastTouch=now; onReplyEnd() }
            return
        }
        if(!listening && !touching && ban.value>.5 && now-max(lastTouch,replyAt)>holdSeconds) { collapse(); lastTouch=now }
        val idle=abs(m.value)<.01 && abs(ban.value)<.01 && abs(pill.value)<.01 && abs(dx.value)+abs(dy.value)<.5 && spin.value<.01
        if(autoPlay && !reduced && !touching && !listening && now-lastTouch>2) {
            if(idle) { t=0.0; mode=0 } else { toIdle(); lastTouch=now-1.4 }
        }
    }
    private fun regionAt(x: Double,y: Double,oy: Double): Int {
        val f=frame(); val s=motion.shapes(f,oy)
        val inBox=x>s.x-10 && x<s.x+s.w+10 && y>s.y-10 && y<s.y+s.h+10
        val inIsland=abs(x-201)<85 && y<11+oy+60
        val qx=(x-s.cx)/(s.a+12); val qy=(y-s.cy)/(s.b+12)
        val inDrop=f.m>.4 && qx*qx+qy*qy<1
        if(mode==2) {
            if(seqT>=4.678 && seqT<5.95 && inBox)return 4
            if(seqT>=7.15 && seqT<8.95 && inBox)return 3
            return 0
        }
        if(listening && (inDrop || (inIsland && !broken)))return 2
        if(ban.value>.5 && inBox)return 3
        if(m.value<.3 && ban.value<.3 && inIsland)return 1
        return 0
    }
    fun down(x: Double,y: Double,oy: Double) {
        takeOver(); touching=true; lastTouch=now; downT=now; moved=0.0; fired=false; plucking=false; scrub=false; atLimit=false
        lastTX=0.0; lastTY=0.0; region=regionAt(x,y,oy)
        if(region==3 && mode==2) { setLiveFrom(sequence()); pill.set(0.0); m.set(0.0); mode=1 }
        if(region==2 && abs(dx.value)+abs(dy.value)>3) {
            grab=true; fired=true; grabX=dx.value; grabY=dy.value; grabTX=0.0; grabTY=0.0; dx.set(dx.value); dy.set(dy.value)
        }
        lineSide=if(y>frame().lineY)1 else -1; requestFrame()
    }
    fun move(x: Double,y: Double,tx: Double,ty: Double,pixelDistance: Double) {
        lastTX=tx; lastTY=ty; moved=max(moved,pixelDistance)
        if(grab) {
            val ox=(200+grabX+tx-grabTX).coerceIn(40.0,362.0)-200
            val oy=(58+grabY+ty-grabTY).coerceIn(48.0,700.0)-58
            e=if(broken)0.0 else -.07*motion.clamp(hypot(ox,oy)/250)
            dx.set(ox); dy.set(oy); return
        }
        if(region==2 && moved>6) {
            plucking=true; fired=true; val f=frame(); pluckX=x; pluckD=(y-f.lineY).coerceIn(-18.0,18.0); pluckRel=-1.0
            val side=if(y>f.lineY)1 else -1
            if(side!=lineSide && now-lastSelection>.06) { lastSelection=now; buzz("sel") }; lineSide=side; return
        }
        if(region==3 && moved>6) {
            if(ty>24 && !fired) { fired=true; m.set(0.0); startListening(); return }
            if(!fired && ty<0) { scrub=true; ban.set((1+ty/83).coerceIn(0.0,1.0)) }
        }
    }
    fun up(vx: Double,vy: Double,success: Boolean=true) {
        val tap=success && !fired && now-downT<.25 && moved<10
        if(grab) { grab=false; spring(dx,0.0,if(broken)16.0 else 10.0,if(broken)210.0 else 220.0,if(success)vx else 0.0); spring(dy,0.0,if(broken)16.0 else 10.0,if(broken)210.0 else 220.0,if(success)vy else 0.0); e=0.0; if(success && !broken)buzz("soft") }
        else if(plucking) { plucking=false; pluckD0=pluckD; pluckRel=now }
        else if(region==2 && tap && listening && !broken) { buzz("light"); submit() }
        else if(region==4 && tap)cancel()
        else if(region==3 && !fired) {
            if(scrub) {
                if(!success || ban.value+vy/83*.12>=.5) { spring(ban,1.0,22.1,157.9); replyAt=now }
                else { collapse(); buzz("light") }
            } else if(tap) { collapse(); buzz("light") }
        }
        touching=false; lastTouch=now; region=0; requestFrame()
    }
}
