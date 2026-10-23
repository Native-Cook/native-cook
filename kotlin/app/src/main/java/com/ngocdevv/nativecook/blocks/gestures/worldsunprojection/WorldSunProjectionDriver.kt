package com.ngocdevv.nativecook.blocks.gestures.worldsunprojection

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.*

internal class WorldSunProjectionDriver(private val wake:()->Unit,private val announce:(String)->Unit,private val haptic:()->Unit){
    private val m=WorldSunProjectionMath
    private val handler=Handler(Looper.getMainLooper())
    private var generation=0;private var epoch=now()
    var auto=false;private set
    var preview:Double?=null;private set
    var reduced=false;private set
    var visible=false;private set
    var grid=false;private set
    var frozen=false;private set
    var information=false;private set
    private var frozenQ=0.0
    private var reducedPose=m.reduced
    private var displayed:WorldSunProjectionPose?=null
    private data class Session(val id:Int,val from:WorldSunProjectionPose,val down:WorldSunProjectionPoint,val timeHit:Boolean,var pose:WorldSunProjectionPose,var last:WorldSunProjectionPoint,var stamp:Double,var mode:Int=0,var vy:Double=0.0,var vp:Double=0.0,var hour:Int,var hapticStamp:Double= -1e10)
    private data class Event(val id:Int,val stamp:Double,val from:WorldSunProjectionPose,val target:WorldSunProjectionPose?,val vy:Double=0.0,val vp:Double=0.0,var phase:Int=0)
    private var session:Session?=null;private var event:Event?=null
    val owned get()=session!=null
    fun props(a:Boolean,p:Double?,r:Boolean){if(auto==a&&preview==p&&reduced==r)return;val reducedChanged=reduced!=r;auto=a;preview=p;reduced=r;if(reducedChanged)reducedPose=m.reduced;clear()}
    fun active(v:Boolean){if(visible==v)return;visible=v;clear()}
    private fun clear(){generation++;session=null;event=null;displayed=null;epoch=now();handler.removeCallbacksAndMessages(null);wake()}
    fun close(){visible=false;clear()}
    fun ticks()=visible&&preview==null&&!owned&&(event?.let{it.phase!=1}?:(!reduced&&auto))
    fun frame(t:Double=now()):WorldSunProjectionPose{
        var p=preview?.let{m.authored(it)}?:session?.pose?:event?.let{e->
            val age=if(e.phase==1)if(e.target==null).6 else .35 else max(0.0,t-e.stamp)
            var out=if(e.target!=null)if(reduced)e.target else m.mix(e.from,e.target,m.smooth(age/.35)) else m.released(e.from,e.vy,e.vp,age,reduced)
            if(!reduced&&age>=2)out=if(age<2.6)m.mix(e.target?:e.from,m.rest,m.smooth((age-2)/.6))else if(auto)m.authored(age-2.6)else m.rest
            out
        }?:if(reduced)reducedPose else if(auto)m.authored(max(0.0,t-epoch))else m.rest
        if(frozen)p=p.copy(q=frozenQ)
        return p
    }
    fun drawn(p:WorldSunProjectionPose){displayed=p}
    fun snapshot()=displayed?:frame()
    fun markerAlpha():Double{val e=event;return if(reduced&&e?.target!=null)m.smooth((now()-e.stamp)/.12)else 1.0}
    fun begin(id:Int,point:WorldSunProjectionPoint):Boolean{
        if(!visible||preview!=null||owned)return false
        val p=snapshot();val projected=WorldSunProjectionCamera(p).project(m.sun(p.q));val hit=projected?.let{hypot(point.x-it.x,point.y-it.y)<=44}?:false
        generation++;handler.removeCallbacksAndMessages(null);event=null;session=Session(id,p,point,point.x>=331||hit,p,point,now(),hour=floor((309+p.q*350)/60).toInt());wake();return true
    }
    fun move(id:Int,point:WorldSunProjectionPoint){val s=session?:return;if(s.id!=id||s.last==point)return;val t=now();val dx=point.x-s.down.x;val dy=point.y-s.down.y
        if(s.mode==0&&hypot(dx,dy)>=8)s.mode=if(s.timeHit)2 else 1
        val old=s.pose
        if(s.mode==2){s.pose=s.pose.copy(q=m.clamp(s.from.q-dy/430));if(frozen)frozenQ=s.pose.q}
        else if(s.mode==1)s.pose=m.bounded(s.pose.copy(yaw=s.from.yaw-dx*.1,pitch=s.from.pitch-dy*.12))
        val dt=max(1.0/240,t-s.stamp);s.vy=m.clamp((s.pose.yaw-old.yaw)/dt,-180.0,180.0);s.vp=m.clamp((s.pose.pitch-old.pitch)/dt,-180.0,180.0)
        val hour=floor((309+s.pose.q*350)/60).toInt();if(hour!=s.hour&&t-s.hapticStamp>=1.0/6){haptic();s.hapticStamp=t};s.hour=hour;s.last=point;s.stamp=t;wake()
    }
    fun end(id:Int,point:WorldSunProjectionPoint?,cancelled:Boolean=false){val old=session?:return;if(old.id!=id)return;if(!cancelled&&point!=null)move(id,point);val s=session?:return;val t=now();val fresh=!cancelled&&t-s.stamp<=.12;session=null
        if(reduced){reducedPose=s.pose;displayed=null;wake()}else start(s.pose,null,if(fresh)s.vy else 0.0,if(fresh)s.vp else 0.0)
        announce(description(s.pose))
    }
    fun action(kind:String,value:Double=0.0){if(!visible||preview!=null||owned)return;val p=snapshot();var target:WorldSunProjectionPose?=null
        when(kind){"time"->{target=p.copy(q=m.clamp(value));if(frozen)frozenQ=target.q};"reset"->target=WorldSunProjectionPose(0.0,-8.5,p.q);"grid"->grid=!grid;"freeze"->{frozenQ=p.q;frozen=!frozen};"information"->information=!information}
        if(reduced&&target==null){generation++;handler.removeCallbacksAndMessages(null);event=null;reducedPose=p;displayed=null;wake()}else start(p,target)
        haptic();if(kind=="time")announce(m.time(value))
    }
    private fun start(from:WorldSunProjectionPose,target:WorldSunProjectionPose?,vy:Double=0.0,vp:Double=0.0){generation++;val id=generation;handler.removeCallbacksAndMessages(null);val e=Event(id,now(),from,target,vy,vp);event=e
        val duration=if(reduced).12 else if(target==null).6 else .35
        handler.postDelayed({if(generation==id&&event?.id==id){if(reduced){reducedPose=target?:from;event=null;displayed=null}else e.phase=1;wake();announce(description(frame()))}},(duration*1000).roundToLong())
        if(!reduced){handler.postDelayed({if(generation==id&&event?.id==id){e.phase=2;wake()}},2000);handler.postDelayed({if(generation==id&&event?.id==id){epoch=e.stamp+2.6;event=null;displayed=null;wake();announce(description(frame()))}},2600)};wake()
    }
    fun description(p:WorldSunProjectionPose=snapshot())="${m.time(p.q)}, heading ${degrees(p.yaw)} degrees, pitch ${degrees(p.pitch)} degrees"
    private fun degrees(v:Double)=if(v<0)-floor(-v+.5).toInt()else floor(v+.5).toInt()
    companion object{fun now()=SystemClock.uptimeMillis()/1000.0}
}
