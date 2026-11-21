package com.ngocdevv.nativecook.blocks.animations.hourscrubsky
import kotlin.math.*

internal class HourScrubSkyEngine {
 private val m=HourScrubSkyMotion
 private data class Channel(val from:Double,val to:Double,val at:Double,val d:Double,val k:Double,val v:Double,val duration:Double,val curve:Int)
 var autoPlay=true;var reduced=false;var auto=true;var pin:Double?=null;var tl=0;var time=0.0;var now=0.0;var height=874.0
 var buzz:(String)->Unit={};var onHourChange:((Int)->Unit)?=null
 private val l=mutableMapOf("h" to 0.0,"hp" to 0.0,"hq" to 0.0,"cs" to 0.0,"rs" to 0.0,"pill" to 0.0,"pillDark" to 0.0,"disc" to 1.0,"gPlay" to 1.0,"gPause" to 0.0,"unit" to 0.0,"hold" to 0.0,"press0" to 1.0,"press1" to 1.0,"press2" to 1.0)
 private val channels=mutableMapOf<String,Channel>();var drag=0;private set
 private var touching=false;val ink=DoubleArray(8);private val targets=DoubleArray(8)
 private var trail=0.0;private var hPrev=0.0;private var hTarget=0.0;private var h0=0.0;private var x0=0.0;private var velocity=0.0;private var decaying=false;private var lastTouch = -10.0;private var homing=0;private var homeAt=0.0;private var bound=false;private var lastPill = -1;private var lastHalf=0;private var lastHaptic = -10.0
 var fingerX=0.0;var fingerY=0.0
 val unit get()=value("unit");val hold get()=value("hold");val press get()=DoubleArray(3) { value("press$it") }
 private fun value(key:String)=l[key]?:0.0
 private fun set(key:String,value:Double) { channels.remove(key);l[key]=value }
 private fun timing(key:String,to:Double,duration:Double,curve:Int=0) { if(reduced && key!="hq") { set(key,to);return };channels[key]=Channel(value(key),to,now,0.0,0.0,0.0,duration,curve) }
 private fun spring(key:String,to:Double,d:Double,k:Double,v:Double=0.0) { if(reduced) { set(key,to);return };val old=channels[key];if(old?.duration==0.0 && old.to==to)return;channels[key]=Channel(value(key),to,now,d/4,k/4,v,0.0,0) }
 private fun animate() { for((key,c)in channels.toMap()) { val t=now-c.at;if(c.duration>0) { val u=m.clamp(t/c.duration);val e=if(c.curve==1)u else if(c.curve==2)m.inOut(u) else if(u<.5)2*u*u else 1-(-2*u+2).pow(2)/2;l[key]=m.mix(c.from,c.to,e);if(u>=1)channels.remove(key) } else { l[key]=m.spring(t,c.from,c.to,c.v,c.d,c.k);if(t>3) { l[key]=c.to;channels.remove(key) } } } }
 fun frame():HourScrubSkyFrame { if(auto || tl>0)return m.frame(pin?:time);return HourScrubSkyFrame(h=if(reduced)value("hq") else value("h"),rate=if(reduced)0.0 else trail,hp=if(reduced)value("hq") else value("hp"),cs=value("cs"),rs=value("rs"),pill=value("pill"),pillDark=value("pillDark"),disc=value("disc"),gPlay=value("gPlay"),gPause=value("gPause"),gReset=0.0,resetRot=0.0,flare=0.0,sway=if(reduced)0.0 else now) }
 fun advance(dt:Double) {
  now+=dt;if(pin!=null) { inks(dt,true);return };animate()
  if(auto)time=(time+dt)%12
  else if(tl==1) { time+=dt;if(time>=10.6) { time=10.6;tl=2 } }
  else if(tl==3) { time+=dt;if(time>=11.8)goRest() }
  else if(tl==0) {
   if(drag==2) { set("h",value("h")+(hTarget-value("h"))*(1-exp(-dt/.03)));set("hp",value("h")) }
   if(decaying) { velocity*=.995.pow(dt*1000);var h=value("h")+velocity*dt
    if(h<0 || h>24) { h=if(h<0)0.0 else 24.0;decaying=false;buzz("rigid") }
    else if(abs(velocity)<.4) { decaying=false;val target=floor(h*2+.5)/2;spring("h",target,40.4,503.5,velocity);spring("hp",target,40.4,503.5,velocity) }
    if(decaying || h==0.0 || h==24.0) { set("h",h);set("hp",h) }
   }
   if((touching || decaying || homing==1) && dt>0) { val r=(value("h")-hPrev)/dt;trail+=(r.coerceIn(-8.0,8.0)-trail)*.3 } else { trail*=exp(-dt/.12);if(abs(trail)<.01)trail=0.0 };hPrev=value("h")
   if(reduced && touching) { val q=floor(value("h")*2+.5)/2;if(abs(q-value("hq"))>.01 && channels["hq"]?.to!=q)timing("hq",q,.2) }
   if(!touching && !decaying && now-lastTouch>2 && autoPlay && !reduced) {
    if(homing==0) { if(value("cs")==0.0 && value("pill")<.001 && (value("h")<.001 || value("h")>23.999)) { goRest();time=0.0;auto=true } else { homing=1;val target=if(value("h")>12)24.0 else 0.0;val duration=.5+.09*abs(target-value("h"));timing("h",target,duration,2);timing("hp",target,duration,2) } }
    else if(homing==1 && (value("h")<.001 || value("h")>23.999)) { homing=2;homeAt=now;if(value("cs")>0)timing("rs",.8,.8,1);spring("pill",0.0,22.1,157.9);timing("pillDark",0.0,.16);spring("hp",0.0,22.1,157.9) }
    else if(homing==2 && now-homeAt>1) { goRest();time=0.0;auto=true }
   }
  }
  inks(dt,false);val index=floor(frame().hp+.5).toInt().coerceIn(0,23);if(index!=lastPill) { lastPill=index;onHourChange?.invoke(index) }
  if(!auto && touching) { val half=floor(frame().h*2).toInt();if(half!=lastHalf && now-lastHaptic>.1) { buzz("selection");lastHaptic=now };lastHalf=half }
 }
 private fun inks(dt:Double,snap:Boolean) { val c=HourScrubSkyColors(frame().h);val ys=doubleArrayOf(30.0,84.0,605.0,651.0,675.0,705.0,745.0,825.0);for(i in 0..7) { val v=HourScrubSkyColors.lightness(c.sky(if(reduced)450.0 else ys[i],height));if(snap)targets[i]=if(v<.46)1.0 else 0.0 else if(v<.42)targets[i]=1.0 else if(v>.5)targets[i]=0.0;val step=if(snap)1.0 else dt/.12;ink[i]+=(targets[i]-ink[i]).coerceIn(-step,step) } }
 private fun goRest() { channels.clear();tl=0;homing=0;for(k in listOf("h","hp","hq","cs","rs","pill","pillDark","gPause","hold"))l[k]=0.0;l["disc"]=1.0;l["gPlay"]=1.0;hTarget=0.0;trail=0.0;lastTouch=now }
 private fun takeover() {
  val f=frame();for(k in listOf("h","hp","rs","pill","pillDark"))channels.remove(k)
  if(auto || tl>0) { set("h",f.h);set("hp",f.hp);set("hq",floor(f.h*2+.5)/2);hTarget=f.h;hPrev=f.h;trail=f.rate;val restored=f.rs>=.8 || f.cs==0.0;set("cs",if(restored)0.0 else f.cs);set("rs",if(restored)0.0 else f.rs);set("pill",max(f.pill,0.0));set("pillDark",f.pillDark);set("disc",f.disc);set("gPlay",f.gPlay);set("gPause",f.gPause);spring("disc",1.0,19.0,195.0);spring("gPlay",1.0,19.0,195.0);timing("gPause",0.0,.1);auto=false;tl=0 }
  pin=null;homing=0;decaying=false;touching=true;lastTouch=now
 }
 private fun engage() { if(value("cs")==0.0 || value("rs")>0) { set("rs",0.0);set("cs",0.0);timing("cs",.5,.5,1) };spring("pill",1.0,19.0,195.0);timing("pillDark",1.0,.16) }
 fun beginDrag(x:Double,y:Double):Boolean { val sun=m.sun(frame().h)
  if(hypot(x-sun.first,y-sun.second)<44 && sun.second<520) { takeover();drag=2;hTarget=value("h");fingerX=x;fingerY=y;spring("hold",1.0,19.0,195.0);buzz("soft") }
  else if(y>700 && y<775 && x>60 && x<390) { takeover();drag=1;h0=value("hp");x0=x;set("h",h0);buzz("light") } else return false
  engage();return true
 }
 fun move(x:Double,y:Double) {
  if(drag==1 || drag==3) { var h=h0+(x-x0)/(289.0/24)
   if(h>24) { val over=(h-24)*(289.0/24);h=24+over/(1+over/120)/(289.0/24) } else if(h<0) { val over=-h*(289.0/24);h=-over/(1+over/120)/(289.0/24) }
   set("h",h);set("hp",h);val out=h<0 || h>24;if(out && !bound)buzz("rigid");bound=out
  } else if(drag==2) { fingerX=x;fingerY=y;var best=hTarget;var dist=Double.MAX_VALUE;for(i in -30..30) { val h=(hTarget+i*.05).coerceIn(0.0,24.0);val s=m.sun(h);val d=(s.first-x).pow(2)+(s.second-y).pow(2);if(d<dist) { best=h;dist=d } };hTarget=best
   if(y>540 || m.sun(best).second>520) { drag=3;set("h",best);set("hp",best);h0=best;x0=x;spring("hold",0.0,22.1,157.9) }
  }
 }
 fun endDrag(vx:Double,success:Boolean=true) { if(drag==0)return;val v=if(success)if(drag==2)trail else vx/(289.0/24) else 0.0;val h=value("h")
  if(h<0 || h>24) { val target=if(h<0)0.0 else 24.0;spring("h",target,40.4,503.5);spring("hp",target,40.4,503.5) }
  else if(abs(v)>.4 && !reduced && success) { velocity=v.coerceIn(-8.0,8.0);decaying=true }
  else { val target=floor(h*2+.5)/2;spring("h",target,40.4,503.5);spring("hp",target,40.4,503.5) }
  drag=0;bound=false;spring("hold",0.0,22.1,157.9);touching=false;lastTouch=now
 }
 fun pressAt(x:Double,y:Double) { if(x>=11 && x<=103 && y>=59 && y<=109)timing("press0",.97,.09) else if(x>=309 && x<=383 && y>=579 && y<=631)timing("press1",.97,.09) else if(x>=12 && x<=390 && y>=792 && y<=858)timing("press2",.97,.09) }
 fun releasePress() { repeat(3) { spring("press$it",1.0,30.5,322.3) } }
 fun tap(x:Double,y:Double) {
  if(abs(x-39)<=26 && abs(y-745)<=26) { if(reduced) { takeover();goRest();touching=false } else if(tl==1) { takeover();touching=false } else if(tl==2)tl=3 else { takeover();touching=false;val h=if(value("h")>=23.95)0.0 else value("h");time=if(h<=.001 && value("cs")==0.0).2 else max(m.timeForHour(h),.62);tl=1 };buzz("selection") }
  else { takeover();touching=false;if(x>10 && x<220 && y>535 && y<640) { spring("unit",if(unit>.5)0.0 else 1.0,30.5,322.3);buzz("selection") } };lastTouch=now;releasePress()
 }
 fun adjust(direction:Double) { takeover();engage();val h=(value("h")+direction*.5).coerceIn(0.0,24.0);set("h",h);set("hp",h);set("hq",h);touching=false;lastTouch=now }
}
