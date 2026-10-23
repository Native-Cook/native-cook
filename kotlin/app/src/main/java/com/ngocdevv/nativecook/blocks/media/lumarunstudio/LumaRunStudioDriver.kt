package com.ngocdevv.nativecook.blocks.media.lumarunstudio

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.*

internal enum class LumaRunStudioOwner { Image,Region,Low,High,Dial,Compare,Back,Keep }
internal class LumaRunStudioDriver(val resources:LumaRunStudioResources,val redraw:()->Unit,val accessibilityChanged:()->Unit,val haptic:()->Unit,val back:()->Unit) {
    private val m=LumaRunStudioMath
    private val handler=Handler(Looper.getMainLooper())
    private val cache=LumaRunStudioCache(resources)
    private var images=emptyList<LumaRunStudioBitmap>()
    private var generation=0;private var sequence= -1;private var eventID=0
    private var epoch=now();private var manual:LumaRunStudioScene?=null
    private var session:Session?=null;private var event:Event?=null
    private var decoration:Decoration?=null;private var comparison:Comparison?=null;private var handoff=false
    private var displayed:LumaRunStudioScene?=null;private var displayedVelocity=LumaRunStudioParams.Zero
    private val loopTimes=m.knots.map {it.time}
    private fun inputHeld()=session?.owner?.let {it!=LumaRunStudioOwner.Back&&it!=LumaRunStudioOwner.Keep}==true
    private var previous=LumaRunStudioOwner.Low
    var kept=false;private set
    var visible=true;private set
    var autoPlay=false;private set
    var reduced=false;private set
    var preview:Double?=null;private set
    var ready=false;private set
    private data class Session(val id:Int,val owner:LumaRunStudioOwner,val base:LumaRunStudioScene,val x:Double,val y:Double,var lastX:Double,var lastY:Double,var lastTime:Double,val inherited:LumaRunStudioParams,var velocity:LumaRunStudioParams=LumaRunStudioParams.Zero,var pointerY:Double=0.0,var changed:Boolean=false,var band:Int= -1,var hapticTime:Double= -1e9)
    private data class Event(val id:Int,val gen:Int,val time:Double,val from:LumaRunStudioScene,val target:LumaRunStudioParams,val velocity:LumaRunStudioParams,val duration:Double,val hold:Boolean=false,var phase:Int=0,var settled:Boolean=false,var requested:Boolean=false,var frames:List<LumaRunStudioBitmap>?=null,var offsets:Boolean=true)
    private data class Decoration(val time:Double,val from:LumaRunStudioAppearance)
    private data class Comparison(val time:Double,val from:Double,val target:Double,val duration:Double)
    init {
        cache.onImage={g,s,b->if(g==generation&&s>sequence&&visible){sequence=s;val before=snapshot();val changed=m.quantized(before.params)!=b.params
            manual=(manual?:before).copy(pair=LumaRunStudioPair(b),params=b.params)
            if(changed&&!handoff){handoff=true;val d=Decoration(now(),before.appearance);decoration=d;val gen=generation;handler.postDelayed({if(generation==gen&&decoration===d){manual=manual?.copy(appearance=(manual?:before).appearance.copy(bracketAlpha=1.0,handleScale=1.0,anticipation=0.0));decoration=null;redraw()}},120)}
            if(changed)kept=false;prepareRecovery();accessibilityChanged();redraw()}}
        cache.onRecovery={g,frames->if(g==generation){event?.frames=frames;redraw()}}
        cache.prepare {loaded->images=loaded;ready=true;epoch=now();displayed=null;if(reduced)requestReduced();accessibilityChanged();redraw()}
    }
    fun props(auto:Boolean,sample:Double?,reduce:Boolean){if(auto==autoPlay&&sample==preview&&reduce==reduced)return;autoPlay=auto;preview=sample;reduced=reduce;reset();if(ready&&reduce)requestReduced();accessibilityChanged();redraw()}
    private fun original():LumaRunStudioScene {val b=LumaRunStudioBitmap(resources.canonical,LumaRunStudioParams.Rest);return LumaRunStudioScene(LumaRunStudioPair(b),b.params,kept=kept)}
    private fun sample(frames:List<LumaRunStudioBitmap>,times:List<Double>,age:Double):Pair<LumaRunStudioScene,LumaRunStudioParams>{
        if(age<=times.first()){val b=frames.first();return LumaRunStudioScene(LumaRunStudioPair(b),b.params) to LumaRunStudioParams.Zero}
        if(age>=times.last()){val b=frames.last();return LumaRunStudioScene(LumaRunStudioPair(b),b.params) to LumaRunStudioParams.Zero}
        var i=0;while(i+1<times.size&&age>=times[i+1])i++;val span=times[i+1]-times[i];val u=m.clamp((age-times[i])/span);val a=frames[i];val b=frames[i+1];val p=m.mix(a.params,b.params,u)
        val v=LumaRunStudioParams((b.params.q-a.params.q)/span,(b.params.angle-a.params.angle)/span,(b.params.low-a.params.low)/span,(b.params.high-a.params.high)/span,(b.params.roiX-a.params.roiX)/span,(b.params.roiY-a.params.roiY)/span)
        return LumaRunStudioScene(LumaRunStudioPair(a,b,if(p.q==0.0)0.0 else u),p) to v
    }
    fun drawScene(time:Double=now()):LumaRunStudioScene {val result=current(time);displayed=result.first;displayedVelocity=result.second;return result.first}
    fun snapshot()=displayed?:current(now()).first
    private fun current(t:Double):Pair<LumaRunStudioScene,LumaRunStudioParams>{
        if(!ready)return original() to LumaRunStudioParams.Zero
        if(!reduced&&(preview!=null||(!inputHeld()&&event==null&&autoPlay))){val time=(max(0.0,preview?:t-epoch))%8;val r=sample(images,loopTimes,time);return r.first.copy(appearance=m.appearance(time),kept=kept) to r.second}
        if(!reduced&&!inputHeld()&&event==null)return original() to LumaRunStudioParams.Zero
        var scene=(manual?:original()).copy(kept=kept);val e=event
        if(!reduced&&e?.phase==2&&e.frames!=null){val age=m.clamp(t-e.time-2,0.0,.60);val r=sample(e.frames!!,listOf(0.0,.12,.24,.36,.48,.60),age);val u=m.cubic(age/.60);val a=scene.appearance
            scene=r.first.copy(appearance=a.copy(bracketAlpha=a.bracketAlpha+(1-a.bracketAlpha)*u,handleScale=a.handleScale+(1-a.handleScale)*u,anticipation=a.anticipation*(1-u),lowOffset=a.lowOffset*(1-u),highOffset=a.highOffset*(1-u)),compare=scene.compare*(1-u),kept=kept);return scene to r.second}
        decoration?.let {d->val u=m.smooth((t-d.time)/.120);val a=scene.appearance;scene=scene.copy(appearance=a.copy(bracketAlpha=d.from.bracketAlpha+(1-d.from.bracketAlpha)*u,handleScale=d.from.handleScale+(1-d.from.handleScale)*u,anticipation=d.from.anticipation*(1-u)))}
        comparison?.let {c->scene=scene.copy(compare=c.from+(c.target-c.from)*m.smooth((t-c.time)/c.duration))}
        if(e?.offsets==true){val a=scene.appearance;scene=scene.copy(appearance=a.copy(lowOffset=m.spring(e.from.appearance.lowOffset,0.0,0.0,t-e.time),highOffset=m.spring(e.from.appearance.highOffset,0.0,0.0,t-e.time)))}
        return scene to LumaRunStudioParams.Zero
    }
    fun ticks():Boolean {if(!ready||!visible||preview!=null)return false;if(decoration!=null||comparison!=null)return true;if(inputHeld())return false;event?.let{return it.phase==2||it.offsets};return !reduced&&autoPlay}
    fun hit(x:Double,y:Double):LumaRunStudioOwner? {
        fun box(a:Double,b:Double,w:Double,h:Double)=x>=a&&x<=a+w&&y>=b&&y<=b+h
        if(box(20.0,64.0,44.0,44.0))return LumaRunStudioOwner.Back
        if(!ready||preview!=null)return null
        if(box(329.0,64.0,44.0,44.0)||box(24.0,737.0,345.0,56.0))return LumaRunStudioOwner.Keep
        if(box(280.0,532.0,89.0,44.0))return LumaRunStudioOwner.Compare
        if(box(307.0,678.0,44.0,44.0))return LumaRunStudioOwner.Dial
        val s=snapshot();val a=s.appearance;val p=s.params
        if(box(2.0,609.0,389.0,44.0)){val da=abs(x-(24+345*p.low-a.anticipation+a.lowOffset));val db=abs(x-(24+345*p.high+a.anticipation+a.highOffset));return if(da==db)previous else if(da<db)LumaRunStudioOwner.Low else LumaRunStudioOwner.High}
        if(box(312+p.roiX,456+p.roiY,44.0,44.0))return LumaRunStudioOwner.Region
        return if(box(29.0,136.0,335.0,384.0))LumaRunStudioOwner.Image else null
    }
    private fun scope(s:LumaRunStudioScene){generation++;cache.invalidate(generation);sequence= -1;manual=s;event=null;decoration=null;comparison=null;handoff=false}
    fun down(id:Int,x:Double,y:Double):Boolean {if(session!=null||!visible)return false;val owner=hit(x,y)?:return false
        val s=snapshot();val inherited=displayedVelocity;if(owner!=LumaRunStudioOwner.Back&&owner!=LumaRunStudioOwner.Keep)scope(s);session=Session(id,owner,s,x,y,x,y,now(),inherited);if(owner==LumaRunStudioOwner.Low||owner==LumaRunStudioOwner.High)previous=owner
        if(owner==LumaRunStudioOwner.Compare)compare(s.compare,1.0,.12);redraw();return true}
    fun move(id:Int,x:Double,y:Double){val s=session?:return;if(s.id!=id||(s.lastX==x&&s.lastY==y))return;val time=now();val dt=max(1.0/240,time-s.lastTime);val vx=m.clamp((x-s.lastX)/dt,-1200.0,1200.0);val vy=m.clamp((y-s.lastY)/dt,-1200.0,1200.0);val dx=x-s.x;val dy=y-s.y;var p=s.base.params;var v=LumaRunStudioParams.Zero
        when(s.owner){
            LumaRunStudioOwner.Image->{p=p.copy(q=p.q-dy/220,roiX=p.roiX+dx*.35);v=v.copy(q= -vy/220,roiX=vx*.35)}
            LumaRunStudioOwner.Region->{p=p.copy(roiX=p.roiX+dx,roiY=p.roiY+dy);v=v.copy(roiX=vx,roiY=vy)}
            LumaRunStudioOwner.Low->{p=p.copy(low=m.clamp(p.low+dx/345,.08,p.high-.08));v=v.copy(low=vx/345)}
            LumaRunStudioOwner.High->{p=p.copy(high=m.clamp(p.high+dx/345,p.low+.08,.92));v=v.copy(high=vx/345)}
            LumaRunStudioOwner.Dial->{val a=atan2(s.y-700,s.x-329);val b=atan2(y-700,x-329);val old=atan2(s.lastY-700,s.lastX-329);p=p.copy(angle=m.clamp(p.angle+atan2(sin(b-a),cos(b-a))*180/PI,-90.0,90.0));v=v.copy(angle=atan2(sin(b-old),cos(b-old))*180/PI/dt)}
            else->Unit}
        if(s.owner==LumaRunStudioOwner.Low||s.owner==LumaRunStudioOwner.High){val raw=(if(s.owner==LumaRunStudioOwner.Low)s.base.params.low else s.base.params.high)+dx/345;val actual=if(s.owner==LumaRunStudioOwner.Low)p.low else p.high;val prior=if(s.owner==LumaRunStudioOwner.Low)s.base.appearance.lowOffset else s.base.appearance.highOffset;val off=m.rubber((raw-actual)*345)+prior*(1-m.smooth(abs(dx)/44));val scene=manual?:s.base
            manual=scene.copy(appearance=if(s.owner==LumaRunStudioOwner.Low)scene.appearance.copy(lowOffset=off)else scene.appearance.copy(highOffset=off));val band=floor(actual/.25).toInt();if(band!=s.band&&time-s.hapticTime>=.08){s.band=band;s.hapticTime=time;haptic()}}
        s.lastX=x;s.lastY=y;s.lastTime=time;s.velocity=v;s.pointerY=vy;s.changed=true
        if(s.owner !in listOf(LumaRunStudioOwner.Compare,LumaRunStudioOwner.Back,LumaRunStudioOwner.Keep)&&m.quantized(p)!=m.quantized(snapshot().params))cache.request(m.bounded(p),generation)
        redraw()
    }
    fun up(id:Int,x:Double,y:Double,cancelled:Boolean=false){val s=session?:return;if(s.id!=id)return;if(!cancelled)move(id,x,y)
        val captured=current(now()).first;session=null
        if(s.owner==LumaRunStudioOwner.Back){reset();cache.close();back();return}
        if(s.owner==LumaRunStudioOwner.Keep){if(!cancelled)save();return}
        val weight=1-m.smooth(max(0.0,now()-s.lastTime)/.12);val v=m.mix(LumaRunStudioParams.Zero,if(s.changed)s.velocity else s.inherited,weight);val target=if(cancelled)LumaRunStudioParams.Rest else m.settledTarget(captured.params,s.pointerY*weight)
        scope(captured);val e=Event(++eventID,generation,now(),captured,target,v,if(cancelled).18 else .60,s.owner==LumaRunStudioOwner.Compare&&!cancelled);event=e
        if(s.owner==LumaRunStudioOwner.Compare)compare(captured.compare,0.0,.16);if(!cancelled)haptic();run(e);accessibilityChanged();redraw()
    }
    private fun compare(from:Double,target:Double,duration:Double){val c=Comparison(now(),from,target,duration);comparison=c;val gen=generation;handler.postDelayed({if(generation==gen&&comparison===c){manual=manual?.copy(compare=target);comparison=null;redraw()}},(duration*1000).toLong())}
    private fun later(e:Event,age:Double,action:()->Unit){handler.postDelayed({if(event===e&&generation==e.gen&&visible){action();redraw()}},max(0.0,(e.time+age-now())*1000).toLong())}
    private fun run(e:Event){if(reduced){if(!e.hold)cache.request(e.target,generation);later(e,.12){event=null};return}
        if(!e.hold){val ages=if(e.duration<.60)listOf(.125,.18)else listOf(.125,.25,.375,.5,.6);for(age in ages)later(e,age){cache.request(if(e.duration<.60)m.mix(e.from.params,LumaRunStudioParams.Rest,m.smooth(age/.18))else m.settlement(e.from.params,e.velocity,e.target,age),e.gen)}}
        later(e,if(e.hold)0.0 else e.duration){e.settled=true;e.offsets=false;manual=manual?.let {it.copy(appearance=it.appearance.copy(lowOffset=0.0,highOffset=0.0))};prepareRecovery()}
        later(e,2.0){e.phase=2};later(e,2.60){manual=null;event=null;decoration=null;comparison=null;generation++;cache.invalidate(generation);epoch=e.time+2.60;displayed=null;accessibilityChanged()}
    }
    private fun prepareRecovery(){val e=event?:return;if(!e.settled||e.requested||reduced)return;val p=(manual?:e.from).params;if(m.quantized(p)!=(if(e.hold)m.quantized(p)else m.quantized(e.target)))return;e.requested=true;cache.recovery(listOf(0.0,.12,.24,.36,.48,.60).map {m.recovery(p,it)},generation)}
    fun toggleCompare(){if(!ready||session!=null||preview!=null)return;val s=snapshot();scope(s);val e=Event(++eventID,generation,now(),s,s.params,LumaRunStudioParams.Zero,0.0,true);event=e;compare(s.compare,if(s.compare>=.5)0.0 else 1.0,.16);run(e);accessibilityChanged();redraw()}
    fun save(){if(!ready||session!=null||preview!=null)return;val s=snapshot();scope(s);kept=true;val e=Event(++eventID,generation,now(),s,s.params,LumaRunStudioParams.Zero,0.0,true);event=e;run(e);haptic();accessibilityChanged();redraw()}
    fun adjust(owner:LumaRunStudioOwner,delta:Double){if(!ready||session!=null||preview!=null)return;val s=snapshot();val p=when(owner){LumaRunStudioOwner.Low->s.params.copy(low=m.clamp(s.params.low+delta,.08,s.params.high-.08));LumaRunStudioOwner.High->s.params.copy(high=m.clamp(s.params.high+delta,s.params.low+.08,.92));LumaRunStudioOwner.Dial->s.params.copy(angle=m.clamp(s.params.angle+delta,-90.0,90.0));LumaRunStudioOwner.Region->s.params.copy(roiX=m.clamp(s.params.roiX+delta,-30.0,30.0));else->s.params.copy(q=m.clamp(s.params.q+delta))};scope(s);val e=Event(++eventID,generation,now(),s,m.bounded(p),LumaRunStudioParams.Zero,.60);event=e;run(e);haptic();accessibilityChanged();redraw()}
    private fun requestReduced(){val s=original();scope(s);cache.request(LumaRunStudioParams.Reduced,generation)}
    fun reset(){generation++;eventID++;cache.invalidate(generation);manual=null;session=null;event=null;decoration=null;comparison=null;handoff=false;sequence= -1;displayed=null;epoch=now()}
    fun active(value:Boolean){if(visible==value)return;visible=value;reset();if(value&&ready&&reduced)requestReduced();redraw()}
    fun close(){visible=false;reset();cache.close();handler.removeCallbacksAndMessages(null)}
    companion object {fun now()=SystemClock.elapsedRealtimeNanos()/1e9}
}
