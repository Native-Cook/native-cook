package com.ngocdevv.nativecook.blocks.navigation.raceglobecalendar
import kotlin.math.*

internal class RaceGlobeCalendarEngine {
 private val m=RaceGlobeCalendarMotion;private val d=RaceGlobeCalendarData
 private data class Flight(var a:DoubleArray,var b:DoubleArray,var sa:DoubleArray,var sb:DoubleArray,var za:Double,var hop:Double,var from:Int,var to:Int)
 private data class Channel(val from:Double,val to:Double,val at:Double,val d:Double,val k:Double,val v:Double,val duration:Double,val delay:Double)
 var autoPlay=true;var reduced=false;var auto=true;var pin:Double?=null;var time=0.0;var now=0.0;var cardTop=650.0;var buzz:(String)->Unit={}
 private var touching=false;private var region=0;var drag=0;private set
 private var downAt=0.0;private var distance=0.0
 private var lastTouch = -10.0;private var homing=false
 private var flight=Flight(d.W[14],d.W[14],d.SUN[14],d.SUN[14],1.0,0.0,14,14)
 private val l=mutableMapOf("fu" to 1.0,"card" to 14.0,"press" to 0.0,"hide" to 0.0,"hour" to 0.0,"shuffle" to 0.0,"rotation" to 0.0);private val channels=mutableMapOf<String,Channel>()
 private var land=9.0;private var leave=9.0;private var pulse = -1.0;private var free=false;private var freeLat=0.0;private var freeLon=0.0;private var freeZ=1.0;private var freeSun=d.SUN[14];private var vLat=0.0;private var vLon=0.0;private var coasting=false
 private var spin = -1.0;private var spinFrom=DoubleArray(5);private var spinTo=14;private var spinDLon=0.0;private var spinCard=14.0
 private var p0=14.0;private var target = -1;private var grabC=d.W[14];private var grabSun=d.SUN[14];private var grabZ=1.0;private var grabFrom=14;private var gLat=0.0;private var gLon=0.0;private var hour0=0.0;private var xTap=0.0;private var yTap=0.0
 private fun get(key:String)=l[key]?:0.0
 private fun set(key:String,value:Double) { channels.remove(key);l[key]=value }
 private fun spring(key:String,to:Double,d:Double=22.1,k:Double=157.9,v:Double=0.0) { if(reduced) { set(key,to);return };val old=channels[key];if(old?.to==to && old.duration==0.0)return;channels[key]=Channel(get(key),to,now,d/4,k/4,v,0.0,0.0) }
 private fun timing(key:String,to:Double,duration:Double,delay:Double=0.0) { if(reduced && delay==0.0) { set(key,to);return };channels[key]=Channel(get(key),to,now,0.0,0.0,0.0,duration,delay) }
 private fun animate() { for((key,c)in channels.toMap()) { val t=now-c.at-c.delay;if(t<0)continue;if(c.duration>0) { val u=m.clamp(t/c.duration);val e=if(u<.5)2*u*u else 1-(-2*u+2).pow(2)/2;l[key]=m.mix(c.from,c.to,e);if(u>=1)channels.remove(key) } else { l[key]=m.spring(t,c.from,c.to,c.v,c.d,c.k);if(t>3) { l[key]=c.to;channels.remove(key) } } } }
 private fun yaw(v:DoubleArray,deg:Double):DoubleArray { val a=deg*PI/180;val c=cos(a);val s=sin(a);return doubleArrayOf(v[0]*c+v[2]*s,v[1],-v[0]*s+v[2]*c) }
 fun frame():RaceGlobeCalendarFrame {
  if(auto)return m.frame(pin?:time);val f=RaceGlobeCalendarFrame(track=1,t=now%10.5)
  if(spin>=0) { val s=m.spinAt(spin);f.lat=m.mix(spinFrom[0],d.LAT[spinTo],m.smooth(s.first));f.lon=spinFrom[1]+spinDLon*s.first;f.spin=spinDLon*s.second;f.zoom=1-.3*s.second/m.spinV;f.sun=m.slerp(spinFrom.copyOfRange(2,5),d.SUN[spinTo],s.first);f.numbered=if(spin<1.4)1-m.seg(spin,0.0,.2) else m.seg(spin,1.4,1.65) }
  else if(free) { f.lat=freeLat;f.lon=freeLon;f.zoom=freeZ;f.sun=freeSun }
  else { val u=get("fu");val uc=m.clamp(u);val c=m.slerp(flight.a,flight.b,u);f.lat=m.lat(c);f.lon=m.lon(c);f.zoom=m.mix(flight.za,1.0,uc)-.4*sin(PI*uc)*m.hopDip(flight.hop);f.sun=m.slerp(flight.sa,flight.sb,uc) }
  f.hour=get("hour");if(abs(f.hour)>1e-4)f.sun=yaw(f.sun,-15*f.hour);f.cardPos=get("card");f.press=get("press");f.arcPos=f.cardPos.coerceIn(0.0,23.0);f.comet=1.0;f.selF=if(spin>=0)(if(spin<.3)flight.from else flight.to).toDouble() else f.cardPos;f.from=flight.from;f.leave=m.clamp(leave/.3);f.to=flight.to;f.land=if(land>=0)land else -1.0;f.calloutOut=get("hide");f.sheen=if(land>=.1 && land<=.8)m.inOut(m.seg(land,.1,.8)) else -1.0;f.shuffle=get("shuffle");f.shuffleRot=get("rotation");f.pulse=pulse;return f
 }
 private data class Snapshot(val c:DoubleArray,val sun:DoubleArray,val zoom:Double,val f:RaceGlobeCalendarFrame)
 private fun snapshot():Snapshot { val f=frame();return Snapshot(d.vec(f.lat,f.lon),if(abs(f.hour)>1e-4)yaw(f.sun,15*f.hour) else f.sun,f.zoom,f) }
 private fun takeover() {
  homing=false;if(!auto) { pin=null;return };val s=snapshot();val f=s.f;auto=false;pin=null;flight=Flight(s.c,d.W[f.to],s.sun,d.SUN[f.to],s.zoom,m.angle(s.c,d.W[f.to]),f.from,f.to);set("fu",0.0)
  val k=floor(f.cardPos).toInt();val fr=f.cardPos-k;val a=m.autoTrack[k.coerceIn(0,8)];val b=m.autoTrack[(k+1).coerceIn(0,8)];set("card",if(abs(b-a)==1)a+fr*(b-a) else (if(fr<.5)a else b).toDouble());land=if(f.land>=0)f.land else -1.0;leave=f.leave*.3;set("press",f.press);set("hide",f.calloutOut);spin = -1.0;free=false;pulse = -1.0
 }
 fun flyTo(index:Int,velocity:Double=0.0,damping:Double=15.1,stiffness:Double=93.4) {
  val target=index.coerceIn(0,23);val s=snapshot();val from=if(land>=0)flight.to else flight.from;spin = -1.0;free=false;flight=Flight(s.c,d.W[target],s.sun,d.SUN[target],s.zoom,m.angle(s.c,d.W[target]),from,target);land = -1.0;leave=if(from==target)9.0 else 0.0;timing("hide",0.0,.001);set("fu",0.0);spring("fu",1.0,damping,stiffness,velocity);spring("card",target.toDouble())
 }
 fun advance(dt:Double) {
  now+=dt;if(pin!=null)return;if(touching && drag==0 && distance<6 && now-downAt>=.38)longPress();animate();if(auto) { time=(time+dt)%10.5;return };if(land>=0)land+=dt;leave+=dt;if(pulse>=0)pulse=if(pulse>1)-1.0 else pulse+dt
  if(spin>=0) { spin+=dt;val sc=m.spinAt(min(1.6,spin*1.6/1.4));set("card",m.mix(spinCard,spinTo.toDouble(),sc.first));if(spin>=1.6) { spin = -1.0;flight=Flight(d.W[spinTo],d.W[spinTo],d.SUN[spinTo],d.SUN[spinTo],1.0,0.0,flight.from,spinTo);set("fu",1.0);set("card",spinTo.toDouble());land=0.0;set("hide",0.0);buzz("medium");lastTouch=now };return }
  if(free && coasting && drag!=2) { val k=.998.pow(dt*1000);vLat*=k;vLon*=k;freeLon+=vLon*dt;freeLat=(freeLat+vLat*dt).coerceIn(-70.0,70.0)
   if(hypot(vLat,vLon)<8) { val v=d.vec(freeLat,freeLon);var best = -1;var a=12.0;for(i in 0..23) { val distance=m.angle(v,d.W[i]);if(distance<a) { a=distance;best=i } };vLat=0.0;vLon=0.0;coasting=false;if(best>=0) { flyTo(best,0.0,14.0,48.7);buzz("selection") };lastTouch=now }
  }
  if(!free && land<0 && !touching && get("fu")>=.985) { land=0.0;set("hide",0.0);buzz("medium") }
  if(!reduced && autoPlay && !touching && now-lastTouch>2) { val parked=free && vLat==0.0 && vLon==0.0;val settled=(parked || (!free && get("fu")>.999)) && abs(get("hour"))<.005 && abs(get("card")-floor(get("card")+.5))<.004
   if(settled) { val home=!parked && flight.to==14 && abs(get("card")-14)<.004 && m.angle(d.vec(frame().lat,frame().lon),d.W[14])<.05;if(home && land>1.6) { time=0.0;auto=true;homing=false } else if(!home && !homing) { homing=true;flyTo(14,0.0,22.1,157.9) } }
  }
 }
 fun down(x:Double,y:Double) { takeover();touching=true;downAt=now;distance=0.0;lastTouch=now;region=if(y>cardTop-6 && y<cardTop+134)1 else if(hypot(x-41,y-90)<30)2 else 0;drag=0
  if(region==1) { spring("press",1.0,30.5,322.3);buzz("soft") };if(region==2)spring("shuffle",1.0,30.5,322.3)
  if(spin>=0 && region!=2) { val s=snapshot();flight=Flight(s.c,d.W[flight.to],s.sun,d.SUN[flight.to],s.zoom,m.angle(s.c,d.W[flight.to]),flight.from,flight.to);set("fu",0.0);spin = -1.0 }
  if(free) { vLat=0.0;vLon=0.0;coasting=false };if(get("fu")<.999 && region!=2) { channels.remove("fu");channels.remove("card") }
 }
 fun pointerDistance(value:Double) { distance=max(distance,value) }
 fun startDrag() { val s=snapshot();if(region==1) { drag=1;grabC=s.c;grabSun=s.sun;grabZ=s.zoom;grabFrom=if(land>=0)flight.to else flight.from;p0=get("card");target = -1;timing("hide",1.0,.1) } else if(region==0) { drag=2;freeLat=m.lat(s.c);freeLon=m.lon(s.c);freeZ=s.zoom;freeSun=s.sun;gLat=freeLat;gLon=freeLon;free=true;vLat=0.0;vLon=0.0;timing("hide",1.0,.12) } }
 fun longPress() { if(region==0) { drag=3;channels.remove("hour");hour0=get("hour");buzz("soft") } }
 fun move(dx:Double,dy:Double) {
  if(drag==1) { var raw=p0-dx/366;if(raw<0)raw=-(-raw/(1 + -raw/(120.0/366)));if(raw>23) { val over=raw-23;raw=23+over/(1+over/(120.0/366)) };set("card",raw)
   val t=(if(raw>=p0)floor(p0+1e-4).toInt()+1 else ceil(p0-1e-4).toInt()-1).coerceIn(0,23);val span=abs(t-p0);val u=if(span<1e-4)0.0 else m.clamp(abs(raw-p0)/span)
   if(t!=target) { target=t;if(land>=0)leave=0.0;flight=Flight(grabC,d.W[t],grabSun,d.SUN[t],grabZ,m.angle(grabC,d.W[t]),grabFrom,t);land = -1.0 };set("fu",u)
  } else if(drag==2) { val z=max(freeZ,.4);freeLon=gLon-dx*.19/z;freeLat=(gLat+dy*.19/z).coerceIn(-70.0,70.0) } else if(drag==3)set("hour",(hour0+dx/12).coerceIn(-12.0,12.0))
 }
 fun up(vx:Double=0.0,vy:Double=0.0,success:Boolean=true,tap:Boolean=false) {
  if(tap && region==2)shuffle()
  else if(drag==1) { val v=if(success)-vx/366 else 0.0
   if(target<0)flyTo(floor(get("card")+.5).toInt()) else { val back=(if(target>floor(p0).toInt())ceil(p0-1e-4-1).toInt()+1 else floor(p0+1e-4+1).toInt()-1).coerceIn(0,23);val projected=get("card")+v*.12;val final=if(abs(projected-target)<=abs(projected-back))target else back
    if(final==target) { spring("fu",1.0,15.1,93.4,v*(if(target>p0)1 else -1)/max(abs(target-p0),.05));spring("card",target.toDouble(),22.1,157.9,v) } else flyTo(final)
   }
  } else if(drag==2) { val z=max(freeZ,.4);vLon=if(success && !reduced)-vx*.19/z else 0.0;vLat=if(success && !reduced)vy*.19/z else 0.0;coasting=true }
  else if(drag==3)timing("hour",0.0,.9,2.0)
  else if(tap && region==0) { val f=frame();val rows=m.rows(f.lat,f.lon);val sel=m.project(rows,d.W[f.to],300*f.zoom)
   if(f.land>=.4 && abs(xTap-sel[0])<110 && yTap<sel[1]-38 && yTap>sel[1]-110) { pulse=0.0;buzz("light") } else { var best = -1;var distance=24.0;for(i in 0..23) { val q=m.project(rows,d.W[i],300*f.zoom);val delta=hypot(q[0]-xTap,q[1]-20-yTap);if(q[3]>=.5 && q[2]>=.3 && delta<distance) { distance=delta;best=i } };if(best>=0 && best!=f.to) { flyTo(best);buzz("light") } }
  }
  if(drag==0 && !free && spin<0) { if(abs(get("card")-floor(get("card")+.5))>.004)flyTo(floor(get("card")+.5).toInt()) else if(get("fu")<1)spring("fu",1.0,15.1,93.4) }
  drag=0;touching=false;lastTouch=now;spring("press",0.0,30.5,322.3);spring("shuffle",0.0,30.5,322.3)
 }
 fun tapPosition(x:Double,y:Double) { xTap=x;yTap=y }
 fun shuffle() { takeover();val s=snapshot();val cur=flight.to;val r=now*7919.37;var to=min(22,floor((r-floor(r))*23).toInt());if(to>=cur)to++;spring("rotation",get("rotation")+360,30.5,322.3)
  if(reduced) { flyTo(to,0.0,22.1,157.9);return };val lat=m.lat(s.c);val lon=m.lon(s.c);val west=((lon-d.LON[to])%360+360)%360;spinFrom=doubleArrayOf(lat,lon)+s.sun;spinTo=to;spinDLon=-(360+west);spinCard=get("card");channels.remove("card");channels.remove("fu");free=false;timing("hide",1.0,.1);flight.from=if(land>=0)flight.to else flight.from;flight.to=to;if(land>=0)leave=0.0;land = -1.0;spin=0.0
 }
 fun select(delta:Int) { takeover();flyTo((frame().to+delta).coerceIn(0,23));lastTouch=now }
}
