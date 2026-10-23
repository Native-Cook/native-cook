package com.ngocdevv.nativecook.blocks.forms.lacedbootstudio

import kotlin.math.*

internal data class LacedBootStudioMaterialChange(val stamp:Double,val materials:DoubleArray,val soles:DoubleArray,val targetMaterials:DoubleArray,val targetSoles:DoubleArray)
internal data class LacedBootStudioConfirmation(val stamp:Double,val ageAtAnchor:Double,val startMix:Double,val anchorMix:Double){
 val endTime get()=stamp+max(0.0,1.38-ageAtAnchor)
 fun age(now:Double)=ageAtAnchor+max(0.0,now-stamp)
 fun mix(now:Double):Double{if(now<=stamp)return anchorMix;val a=age(now);val rise=startMix+(1-startMix)*LacedBootStudioMath.smooth(a/.12);return rise*(1-LacedBootStudioMath.smooth((a-1.2)/.18))}
}
internal data class LacedBootStudioPresentationHeld(val scene:LacedBootStudioScene,val saveAge:Double?,val saveStartMix:Double)
internal class LacedBootStudioPresentation{
 var materials=doubleArrayOf(1.0,0.0,0.0);var soles=doubleArrayOf(1.0,0.0);var change:LacedBootStudioMaterialChange?=null;var confirmation:LacedBootStudioConfirmation?=null;var held:LacedBootStudioPresentationHeld?=null
 val moving get()=held==null&&(change!=null||confirmation!=null)
 fun apply(scene:LacedBootStudioScene,now:Double){val h=held;if(h!=null){scene.materials=h.scene.materials.copyOf();scene.soles=h.scene.soles.copyOf();scene.savedMix=h.scene.savedMix;return}
  scene.materials=materials.copyOf();scene.soles=soles.copyOf();scene.savedMix=confirmation?.mix(now)?:0.0
  change?.let{c->val u=LacedBootStudioMath.smooth((now-c.stamp)/.18);scene.materials=DoubleArray(3){c.materials[it]+(c.targetMaterials[it]-c.materials[it])*u};scene.soles=DoubleArray(2){c.soles[it]+(c.targetSoles[it]-c.soles[it])*u}}
 }
 fun capture(s:LacedBootStudioScene,displayedAt:Double){held=LacedBootStudioPresentationHeld(s.snapshot(),confirmation?.let{min(1.38,it.age(displayedAt))},confirmation?.startMix?:0.0);materials=s.materials.copyOf();soles=s.soles.copyOf();change=null;confirmation=null}
 fun release(leather:Int,sole:Int,now:Double){val h=held?:return;materials=h.scene.materials.copyOf();soles=h.scene.soles.copyOf();held=null;val tm=DoubleArray(3){if(it==leather)1.0 else 0.0};val ts=DoubleArray(2){if(it==sole)1.0 else 0.0};if(!materials.contentEquals(tm)||!soles.contentEquals(ts))change=LacedBootStudioMaterialChange(now,materials,soles,tm,ts);h.saveAge?.let{confirmation=LacedBootStudioConfirmation(now,it,h.saveStartMix,h.scene.savedMix)}}
 fun select(s:LacedBootStudioScene,leather:Int,sole:Int,now:Double){materials=s.materials.copyOf();soles=s.soles.copyOf();change=LacedBootStudioMaterialChange(now,materials,soles,DoubleArray(3){if(it==leather)1.0 else 0.0},DoubleArray(2){if(it==sole)1.0 else 0.0})}
 fun save(mix:Double,now:Double){confirmation=LacedBootStudioConfirmation(now,0.0,mix,mix)}
 fun complete(now:Double){change?.let{if(now>=it.stamp+.18){materials=it.targetMaterials;soles=it.targetSoles;change=null}};confirmation?.let{if(now>=it.endTime)confirmation=null}}
 fun reset(leather:Int,sole:Int){materials=DoubleArray(3){if(it==leather)1.0 else 0.0};soles=DoubleArray(2){if(it==sole)1.0 else 0.0};change=null;confirmation=null;held=null}
}
internal data class LacedBootStudioSession(val id:Long,val from:LacedBootStudioScene,val downDate:Double,val anchor:LacedBootStudioPoint,
 var last:LacedBootStudioPoint,var lastDate:Double,var lagDate:Double,var progress:Double,var tensions:DoubleArray,var stage:Int,
 var pullAnchor:LacedBootStudioPoint?,var pullTensions:DoubleArray,var lag:Double,var overpull:LacedBootStudioPoint,
 var lastSigned:Double=Double.NEGATIVE_INFINITY,var passageDate:Double?=null,var supportActive:Boolean=true,var terminal:LacedBootStudioScene?=null)
internal data class LacedBootStudioEvent(val id:Long,val stamp:Double,val from:LacedBootStudioScene,val downAge:Double,val rows:Int?=null,var phase:Int=0)
internal class LacedBootStudioDriver{
 var epoch=0.0;var generation=0L;var selectedLeather=0;var selectedSole=0;var favorite=false
 var session:LacedBootStudioSession?=null;var event:LacedBootStudioEvent?=null;var reducedScene=LacedBootStudioMath.finalScene();val presentation=LacedBootStudioPresentation()
 var rendered:LacedBootStudioScene?=null;var renderedDate=0.0
 fun held(s:LacedBootStudioSession,now:Double):LacedBootStudioScene{if(!s.supportActive)s.terminal?.let{return it.snapshot()};val age=max(0.0,now-s.downDate);val lag=s.lag*(1-LacedBootStudioMath.smooth(max(0.0,now-s.lagDate)/.09));val r=LacedBootStudioMath.held(s.from,s.progress,s.tensions,age,lag,s.overpull);s.passageDate?.let{if(now-it<.035)r.tipAlpha=0.0};return r}
 fun frame(now:Double,autoPlay:Boolean,reduced:Boolean,preview:Double?):LacedBootStudioScene{
  val active=session;val e=event;val result=when{
   preview!=null->LacedBootStudioMath.authored(preview)
   active!=null->held(active,now)
   e!=null->{val age=max(0.0,now-e.stamp);if(e.rows!=null){val s=LacedBootStudioMath.resolving(e.from,e.rows,age);if(!reduced&&age>=2)LacedBootStudioMath.recovering(LacedBootStudioMath.resolving(e.from,e.rows,.12),age-2)else s}
    else if(age<2)LacedBootStudioMath.released(e.from,age,e.downAge)else if(age<2.6)LacedBootStudioMath.recovering(LacedBootStudioMath.released(e.from,2.0,e.downAge),age-2)else if(autoPlay&&!reduced)LacedBootStudioMath.authored(age-2.6)else LacedBootStudioMath.rest()}
   reduced->reducedScene.snapshot()
   autoPlay->LacedBootStudioMath.authored(max(0.0,now-epoch))
   else->LacedBootStudioMath.rest()
  };presentation.apply(result,now);return result
 }
 fun snapshot(now:Double,auto:Boolean,reduced:Boolean,preview:Double?)=rendered?.snapshot()?:frame(now,auto,reduced,preview)
 fun begin(point:LacedBootStudioPoint,now:Double,auto:Boolean,reduced:Boolean,preview:Double?):Boolean{
  if(reduced||preview!=null||session!=null||!LacedBootStudioGeometry.accepts(point))return false
  val c=snapshot(now,auto,reduced,preview);presentation.capture(c,if(rendered!=null)renderedDate else now);event=null;generation++;val p=LacedBootStudioGeometry.local(point)
  session=LacedBootStudioSession(generation,c,now,p,p,now,now,c.progress,c.tensions.copyOf(),LacedBootStudioGeometry.activeSpan(c.progress),if(c.progress>=6)p else null,c.tensions.copyOf(),c.velocityLag,c.overpull);return true
 }
 fun move(point:LacedBootStudioPoint,now:Double):Boolean{
  val s=session?:return false;val p=LacedBootStudioGeometry.local(point);if(p==s.last)return false;val dt=max(1.0/240,now-s.lastDate);s.lag=min(3.0,hypot(p.x-s.last.x,p.y-s.last.y)/dt*.003);s.lagDate=now
  val anchor=s.pullAnchor;if(anchor!=null){val delta=.85*(anchor.y-p.y)/36;s.tensions=DoubleArray(6){LacedBootStudioMath.clamp(s.pullTensions[it]+delta)};val raw=s.pullTensions[5]+delta;val outside=(raw-LacedBootStudioMath.clamp(raw))*36/.85;s.overpull=LacedBootStudioPoint(0.0,-LacedBootStudioMath.clamp(outside/(1+abs(outside)/48)*.45,-6.0,6.0))}
  else{val pointer=LacedBootStudioPoint(s.from.tip.x+p.x-s.anchor.x,s.from.tip.y+p.y-s.anchor.y);val table=LacedBootStudioGeometry.arcs[s.stage];val projection=table.nearest(pointer);var candidate=LacedBootStudioGeometry.spanProgress(s.stage,projection.first)
   if(s.stage>0){val previous=LacedBootStudioGeometry.arcs[s.stage-1].nearest(pointer);if(previous.second<projection.second)candidate=LacedBootStudioGeometry.spanProgress(s.stage-1,previous.first)}
   val end=table.points.last();val before=table.points[30];val nx=end.x-before.x;val ny=end.y-before.y;val signed=(pointer.x-end.x)*nx+(pointer.y-end.y)*ny;val distance=hypot(pointer.x-end.x,pointer.y-end.y)
   if(distance<=9&&signed>=0&&s.lastSigned<=0){candidate=LacedBootStudioGeometry.spanProgress(s.stage,1.0);s.passageDate=now;if(s.stage<10){s.stage++;s.lastSigned=Double.NEGATIVE_INFINITY}else{s.pullAnchor=p;s.pullTensions=s.tensions.copyOf()}}
   else{candidate=min(candidate,LacedBootStudioGeometry.spanProgress(s.stage,1.0)-1e-5);s.lastSigned=signed};s.progress=LacedBootStudioMath.clamp(candidate,0.0,6.0)
  };s.last=p;s.lastDate=now;s.supportActive=true;s.terminal=null;return true
 }
 fun finish(point:LacedBootStudioPoint?,now:Double,cancelled:Boolean){val s=session?:return;if(!cancelled&&point!=null)move(point,now);val c=held(s,now);presentation.release(selectedLeather,selectedSole,now);session=null;generation++;event=LacedBootStudioEvent(generation,now,c,max(0.0,now-s.downDate))}
 fun clear(now:Double){session=null;event=null;generation++;presentation.reset(selectedLeather,selectedSole);epoch=now;rendered=null;renderedDate=now}
 fun reducedChanged(now:Double){clear(now);reducedScene=LacedBootStudioMath.finalScene()}
 fun act(kind:String,index:Int,now:Double,auto:Boolean,reduced:Boolean,preview:Double?):Boolean{
  if(preview!=null||session!=null)return false;val c=snapshot(now,auto,reduced,preview);if(kind=="row"&&(c.rows+index).coerceIn(0,6)==c.rows)return false
  if(reduced){reducedScene=c;event=null}else{generation++;event=LacedBootStudioEvent(generation,now,c,0.0)}
  when(kind){"leather"->{selectedLeather=index;presentation.select(c,selectedLeather,selectedSole,now)};"sole"->{selectedSole=index;presentation.select(c,selectedLeather,selectedSole,now)};"favorite"->favorite=!favorite;"save"->presentation.save(c.savedMix,now);"row"->{generation++;event=LacedBootStudioEvent(generation,now,c,0.0,(c.rows+index).coerceIn(0,6))}}
  return true
 }
 fun complete(now:Double,reduced:Boolean){presentation.complete(now);session?.let{s->val end=max(s.downDate+.43,s.lagDate+.09);if(s.supportActive&&now>=end){s.terminal=held(s,end);s.supportActive=false}}
  event?.let{e->val age=now-e.stamp;if(reduced&&age>=.43){reducedScene=frame(e.stamp+.43,false,true,null);event=null}else if(age>=2.6){epoch=e.stamp+2.6;event=null;rendered=null}else if(age>=2)e.phase=2 else if(age>=.43)e.phase=1}
 }
 fun needsFrames(auto:Boolean,reduced:Boolean,preview:Double?):Boolean{if(preview!=null)return false;if(presentation.moving)return true;session?.let{return it.supportActive};event?.let{return it.phase!=1};return auto&&!reduced}
 fun nextDeadline():Double?{val e=event?:return null;return if(e.phase==1)e.stamp+2 else null}
}
internal fun lacedBootStudioNow()=System.nanoTime()/1_000_000_000.0
