package com.ngocdevv.nativecook.blocks.media.shapeshiftviewfinder
import kotlin.math.*

/** Retargets band positions/velocities and the flip destination, even before the previous transition's midpoint. */
internal class ShapeShiftViewfinderEngine {
    var f=ShapeShiftViewfinderScore.frame(0.0);var automatic=true;var target=0;var held=false;var reduced=false
    var xGoal=0.0;var yGoal=0.0;var kGoal=1.0;var rotGoal=0.0;var sheetGoal=0.0;var flipTarget=0.0
    var rowScroll=0.0;var selectedSwatch=0;var solidTarget=false
    private var origin=0.0;private var prior=0.0;private var lastTouch=0.0;private var returning=false
    private var velocity=DoubleArray(432);private val pending=IntArray(12);private val targets=IntArray(12);private val activate=DoubleArray(12)
    private var vx=0.0;private var vy=0.0;private var vk=0.0;private var vr=0.0;private var vs=0.0;private var previousX=0.0;private var previousY=0.0
    private var flipFrom=0.0;private var flipTime= -99.0;private var tapTime= -99.0;private var irisTime= -99.0;private var floodTime= -99.0
    private var oldOutline:DoubleArray?=null;private var fadeTime= -99.0
    val previousFrame:ShapeShiftViewfinderFrame? get()=oldOutline?.let{f.copy().apply{w=it}}
    val crossfade:Double get()=((prior-fadeTime)/.2).coerceIn(0.0,1.0)
    fun frame(now:Double,autoPlay:Boolean,pin:Double?,manual:Boolean,reduced:Boolean):ShapeShiftViewfinderFrame {
        this.reduced=reduced
        if(origin==0.0){origin=now;prior=now;automatic=autoPlay&&!manual&&!reduced}
        val dt=(now-prior).coerceIn(0.0,.05);prior=now
        if(automatic){f=ShapeShiftViewfinderScore.frame(pin?:now-origin);target=ShapeShiftViewfinderScore.shape(f.phase);return f}
        val m=ShapeShiftViewfinderMath
        for(q in 0..11){if(now>=activate[q])targets[q]=pending[q];for(b in 0..5){val w=2*PI/(.42/(1+.35*b));for(s in 0..5){val i=q*36+b*6+s;val goal=if(s==targets[q])1.0 else 0.0;val r=m.step(f.w[i],velocity[i],goal,dt,w,w*w);f.w[i]=if(reduced)goal else r.first;velocity[i]=if(reduced)0.0 else r.second}}}
        if(!held){val a=m.step(f.x,vx,xGoal,dt,22.1,157.9);val b=m.step(f.y,vy,yGoal,dt,22.1,157.9);val c=m.step(f.k,vk,kGoal,dt,22.1,157.9);val d=m.step(f.rot,vr,rotGoal,dt,22.1,157.9)
            f.x=if(reduced)xGoal else a.first;vx=a.second;f.y=if(reduced)yGoal else b.first;vy=b.second;f.k=if(reduced)kGoal else c.first;vk=c.second;f.rot=if(reduced)rotGoal else d.first;vr=d.second}
        val sh=m.step(f.sheet,vs,sheetGoal,dt,22.1,157.9);f.sheet=if(reduced)sheetGoal else sh.first;vs=sh.second
        val fp=m.seg(now,flipTime,flipTime+.35);f.flip=if(reduced)flipTarget else m.mix(flipFrom,flipTarget,fp)
        val age=now-tapTime;f.dent=if(reduced)0.0 else 10*max(0.0,m.spring(age,1.0,0.0,0.0,30.5,322.3));f.press=if(reduced)1.0 else 1-m.spring(age,.04,0.0)
        f.rings=doubleArrayOf(if(reduced)0.0 else .45*(1-m.seg(age,0.0,.24)),f.tapA,1.0,0.0,0.0,1.0)
        val speedX=if(dt>0)(f.x-previousX)/dt else 0.0;val speedY=if(dt>0)(f.y-previousY)/dt else 0.0;val length=hypot(speedX,speedY);previousX=f.x;previousY=f.y
        if(length>1){f.dx=speedX/length;f.dy=speedY/length}
        val tx=if(reduced)0.0 else (speedX/5600).coerceIn(-.16,.16);val ty=if(reduced)0.0 else (speedY/5600).coerceIn(-.16,.16)
        f.jx+=(tx-f.jx)*(1-exp(-dt*18));f.jy+=(ty-f.jy)*(1-exp(-dt*18));f.lx=if(reduced)0.0 else -f.dx*min(length*.06,10.0);f.ly=if(reduced)0.0 else -f.dy*min(length*.06,10.0)
        f.lift+=((if(held)2.0 else 0.0)-f.lift)*(1-exp(-dt*12));f.sx*=exp(-dt*8);f.sy*=exp(-dt*8);f.sr*=exp(-dt*8)
        val ir=m.iris(now-irisTime);f.iris=if(reduced)1.0 else ir[0];f.twist=if(reduced)0.0 else ir[1];f.flash=ir[2];f.dim=if(reduced)1.0 else ir[3];f.disc=if(reduced)1.0 else ir[4];f.blinkD=if(reduced)0.0 else ir[5];f.blinkA=if(reduced)0.0 else ir[6]
        if(floodTime>=0){val u=(now-floodTime)/.58;f.f1u=u;f.dot=m.color(f.base,f.f1c,m.seg(u,.34,.69));if(u>=1){f.base=f.f1c;f.f1u= -1.0;floodTime= -99.0}}
        f.segment=if(solidTarget)1.0 else 0.0
        if(autoPlay&&!manual&&pin==null&&!reduced&&!held&&now-lastTouch>2&&now-irisTime>.62){if(!returning){returning=true;home(now)};if(now-lastTouch>3.1&&abs(f.x)<.02&&abs(f.y)<.02&&abs(f.k-1)<.002&&abs(f.sheet)<.002){automatic=true;origin=now;returning=false;f=ShapeShiftViewfinderScore.frame(0.0)}}
        return f
    }
    fun takeOver(now:Double) {
        lastTouch=now;returning=false;if(!automatic)return
        val a=ShapeShiftViewfinderScore.frame(f.phase+.0005);val b=ShapeShiftViewfinderScore.frame(f.phase-.0005);velocity=DoubleArray(432){(a.w[it]-b.w[it])/.001}
        target=ShapeShiftViewfinderScore.shape(f.phase)
        for(q in 0..11){targets[q]=ShapeShiftViewfinderScore.shape(f.phase-q*.2/11);pending[q]=target;activate[q]=now}
        xGoal=f.x;yGoal=f.y;kGoal=f.k;rotGoal=f.rot;sheetGoal=if(f.sheet>.5)1.0 else 0.0;flipTarget=if(f.flip>=.5)1.0 else 0.0;flipFrom=f.flip;flipTime=now;previousX=f.x;previousY=f.y;solidTarget=f.segment>.5;if(f.f1u>=0)floodTime=now-f.f1u*.58;if(f.phase>=8.3&&f.phase<8.92)irisTime=now-(f.phase-8.3);automatic=false
    }
    fun morph(now:Double,angle:Double){takeOver(now);target=(target+1)%6;if(reduced){oldOutline=f.w.copyOf();fadeTime=now};for(q in 0..11){pending[q]=target;activate[q]=if(reduced)now else now+q*.2/11};tapTime=now;f.tapA=angle;f.hint=doubleArrayOf(1.0,0.0,0.0)}
    fun flip(now:Double){takeOver(now);flipFrom=f.flip;flipTarget=1-flipTarget;flipTime=now}
    fun capture(now:Double){takeOver(now);if(now-irisTime>.62)irisTime=now}
    fun sheet(now:Double,open:Boolean?=null){takeOver(now);sheetGoal=if(open?:(sheetGoal<.5))1.0 else 0.0}
    fun color(color:Int,index:Int,now:Double){takeOver(now);f.base=f.dot;f.f1c=color;f.f1u=0.0;floodTime=now;selectedSwatch=index;if(solidTarget)f.ring=index.toDouble() else f.dring=index.toDouble();if(reduced){f.base=color;f.dot=color;f.f1u= -1.0;floodTime= -99.0}}
    fun release(now:Double,cancelled:Boolean,velocityX:Double=0.0,velocityY:Double=0.0,height:Double){held=false;lastTouch=now;xGoal=(f.x+(if(cancelled)0.0 else velocityX*.12)).coerceIn(-175.0,175.0);yGoal=(f.y+(if(cancelled)0.0 else velocityY*.12)).coerceIn(-height/2+20,height/2-20);vx=if(cancelled)0.0 else velocityX;vy=if(cancelled)0.0 else velocityY;kGoal=f.k.coerceIn(.45,1.3);val near=(f.rot/90).roundToInt()*90.0;rotGoal=if(abs(f.rot-near)<=4)near else f.rot}
    fun home(now:Double){xGoal=0.0;yGoal=0.0;kGoal=1.0;rotGoal=0.0;sheetGoal=0.0;flipFrom=f.flip;flipTarget=0.0;flipTime=now;target=0;for(q in 0..11){pending[q]=0;activate[q]=now+q*.2/11};f.base=0x118169;f.dot=0x118169;f.f1u= -1.0;floodTime= -99.0;solidTarget=false}
}
