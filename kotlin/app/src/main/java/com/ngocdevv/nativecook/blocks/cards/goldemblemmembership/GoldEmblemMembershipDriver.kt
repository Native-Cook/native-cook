package com.ngocdevv.nativecook.blocks.cards.goldemblemmembership

import kotlin.math.*

internal class GoldEmblemMembershipDriver(private val auto: Boolean, private val reduced: Boolean,
                                        private val cue: (String) -> Unit, private val buzz: (Boolean) -> Unit) {
    var frame=GoldEmblemMembershipMotion.Frame()
    var now=0.0
    private var autoStart=0.0
    private var lastTouch=-100.0
    private var mode=if(auto && !reduced)0 else 1
    private var dragging=false
    private var resumeAsked=false
    private var stamped=false
    private var castAt: Double?=null
    private var castStart=0.0
    private var castDuration=1.7
    private var fingerAt=-1.0
    private var fingerFrom=0.0
    private var dragAt=-1.0
    private var dragX=0.0
    private var dragV=0.0
    private var dragEnergy=0.0
    private var stiffness=0.0
    private var damping=0.55
    fun moving()=(auto && !reduced)||dragging||castAt!=null||dragAt>=0||fingerAt>=0
    fun step(dt: Double) {
        now+=dt
        if(fingerAt>=0) {
            val p=((now-fingerAt)/0.15).coerceIn(0.0,1.0)
            frame.finger=fingerFrom*(1-GoldEmblemMembershipMotion.timing(p))
            if(p>=1)fingerAt=-1.0
        }
        if(dragAt>=0) {
            val h=min(dt,0.064); val w=sqrt(stiffness/4); val wd=w*sqrt(1-damping*damping)
            val env=exp(-damping*w*h); val x=dragX; val v=dragV
            val frag=env*(sin(wd*h)*(v+damping*w*x)/wd+x*cos(wd*h))
            dragV=-damping*w*frag+env*(cos(wd*h)*(v+damping*w*x)-wd*x*sin(wd*h))
            dragX=frag; frame.drag=frag
            if(dragEnergy==0.0 || (0.5*stiffness*frag*frag+2*dragV*dragV)/dragEnergy<=6e-9) {
                frame.drag=0.0; dragAt=-1.0
            }
        }
        castAt?.let { at ->
            frame.cast=castStart+(1-castStart)*((now-at)/castDuration).coerceIn(0.0,1.0)
            if(frame.cast>0.86 && !stamped) { stamped=true; buzz(true); if(!reduced)cue("stamp") }
            if(frame.cast>=1) { frame.from=frame.to; frame.cast=0.0; castAt=null }
        }
        if(mode==0 && auto && !reduced) { frame=GoldEmblemMembershipMotion.frameAt((now-autoStart)%10); return }
        if(auto && !reduced && !dragging && frame.cast==0.0 && now-lastTouch>=2) {
            if(!resumeAsked) { resumeAsked=true; if(frame.to!=0)castTo(0,-1.0) }
            else if(frame.from==0 && frame.to==0 && abs(frame.drag)<0.3 && now-lastTouch>2.4) {
                resumeAsked=false; autoStart=now; mode=0
            }
        }
    }
    private fun spring(duration: Double, dampingRatio: Double, velocity: Double=0.0) {
        val x=frame.drag
        var v=(if(dragAt>=0)dragV else 0.0)+velocity
        if(x*v>0)v=0.0
        if(x==0.0 && v==0.0) { dragAt=-1.0; return }
        damping=dampingRatio
        fun diff(k: Double): Double {
            val w=sqrt(k/4)*damping; val t=duration*1.5
            val xt=(x+(v+x*w)*t)*exp(-w*t); val vt=xt*(-w)+(v+x*w)*exp(-w*t)
            return (k*xt*xt+4*vt*vt)/(k*x*x+4*v*v)-6e-9
        }
        var lo=2.220446049250313e-16; var hi=8000.0; var k=4000.0
        val direction=if(diff(hi)>=diff(lo))1 else -1
        repeat(100) { if(abs(diff(k))>6e-12) { if(diff(k)*direction<0)lo=k else hi=k; k=(lo+hi)/2 } }
        stiffness=k; dragX=x; dragV=v; dragEnergy=0.5*k*x*x+2*velocity*velocity; dragAt=now
    }
    private fun takeOver() {
        lastTouch=now; resumeAsked=false
        if(mode!=0)return
        mode=1; fingerAt=now; fingerFrom=frame.finger; frame.press=0.0; spring(0.4,0.7)
        if(frame.cast>0) { castStart=frame.cast; castAt=now; castDuration=(1-frame.cast)*1.7 }
    }
    private fun castTo(next: Int, dir: Double) {
        if(next==frame.to && frame.cast==0.0 && frame.from==next)return
        frame.from=if(frame.cast>0 && frame.cast<0.5)frame.from else frame.to
        frame.to=next; frame.side=dir; frame.cast=0.0; stamped=false
        castAt=now; castStart=0.0; castDuration=if(reduced)0.4 else 1.7
        buzz(false); if(!reduced)cue("melt")
    }
    fun begin() { takeOver() }
    fun start() { dragging=true }
    fun drag(dx: Double) { lastTouch=now; dragAt=-1.0; frame.drag=34*tanh(dx/110) }
    fun release(dx: Double, vx: Double, cancelled: Boolean=false) {
        val cur=frame.to; val far=abs(vx)>1800
        val next=when { dx < -60 || vx < -600 -> if(far)2 else min(2,cur+1)
            dx>60 || vx>600 -> if(far)0 else max(0,cur-1); else -> cur }
        if(!cancelled && next!=cur && frame.cast==0.0)castTo(next,if(next>cur)1.0 else -1.0)
        val sech=1/cosh(dx/110)
        lastTouch=now; dragging=false; spring(0.6,0.55,34*sech*sech/110*vx)
    }
    fun adjust(dir: Int) { takeOver(); val n=(frame.to+dir).coerceIn(0,2); if(n!=frame.to)castTo(n,if(n>frame.to)1.0 else -1.0) }
}
