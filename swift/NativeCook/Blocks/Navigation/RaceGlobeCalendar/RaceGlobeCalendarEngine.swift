import Foundation

final class RaceGlobeCalendarEngine {
 private typealias M=RaceGlobeCalendarMotion
 private typealias D=RaceGlobeCalendarData
 private struct Flight { var a:[Double],b:[Double],sa:[Double],sb:[Double],za:Double,hop:Double,from:Int,to:Int }
 private struct Channel { var from:Double,to:Double,at:Double,d:Double,k:Double,v:Double,duration:Double,delay:Double }
 var autoPlay=true,reduced=false,auto=true,pin:Double?,time=0.0,now=0.0,cardTop=650.0
 var buzz:(String)->Void={ _ in }
 private var options:(Bool,Bool)?,previous:Double?,touching=false,region=0,lastTouch = -10.0,homing=false
 private(set) var drag=0
 private var flight=Flight(a:D.W[14],b:D.W[14],sa:D.SUN[14],sb:D.SUN[14],za:1,hop:0,from:14,to:14)
 private var l:[String:Double]=["fu":1,"card":14,"press":0,"hide":0,"hour":0,"shuffle":0,"rotation":0],channels:[String:Channel]=[:]
 private var land=9.0,leave=9.0,pulse = -1.0,free=false,freeLat=0.0,freeLon=0.0,freeZ=1.0,freeSun=D.SUN[14],vLat=0.0,vLon=0.0,coasting=false
 private var spin = -1.0,spinFrom=[0.0,0,0,0,0],spinTo=14,spinDLon=0.0,spinCard=14.0
 private var p0=14.0,target = -1,grabC=D.W[14],grabSun=D.SUN[14],grabZ=1.0,grabFrom=14,gLat=0.0,gLon=0.0,hour0=0.0
 init() {
  #if DEBUG
  if let raw=UserDefaults.standard.object(forKey:"raceGlobeCalendarTime") { pin=(raw as? NSNumber)?.doubleValue ?? (raw as? String).flatMap(Double.init);time=pin ?? 0 }
  #endif
 }
 private func get(_ key:String)->Double { l[key] ?? 0 }
 private func set(_ key:String,_ value:Double) { channels[key]=nil;l[key]=value }
 private func spring(_ key:String,_ to:Double,_ d:Double=22.1,_ k:Double=157.9,_ v:Double=0) { if reduced { set(key,to);return };if let old=channels[key],old.to==to && old.duration==0 { return };channels[key]=Channel(from:get(key),to:to,at:now,d:d/4,k:k/4,v:v,duration:0,delay:0) }
 private func timing(_ key:String,_ to:Double,_ duration:Double,_ delay:Double=0) { if reduced && delay==0 { set(key,to);return };channels[key]=Channel(from:get(key),to:to,at:now,d:0,k:0,v:0,duration:duration,delay:delay) }
 private func animate() { for (key,c) in channels { let t=now-c.at-c.delay;if t<0 { continue };if c.duration>0 { let u=M.clamp(t/c.duration),e=u<0.5 ? 2*u*u : 1-pow(-2*u+2,2)/2;l[key]=M.mix(c.from,c.to,e);if u>=1 { channels[key]=nil } } else { l[key]=M.spring(t,c.from,c.to,c.v,c.d,c.k);if t>3 { l[key]=c.to;channels[key]=nil } } } }
 private func yaw(_ v:[Double],_ deg:Double)->[Double] { let a=deg * .pi/180,c=cos(a),s=sin(a);return [v[0]*c+v[2]*s,v[1],-v[0]*s+v[2]*c] }
 var frame:RaceGlobeCalendarFrame {
  if auto { return M.frame(pin ?? time) }
  var f=RaceGlobeCalendarFrame();f.track=1;f.t=now.truncatingRemainder(dividingBy:10.5)
  if spin>=0 { let s=M.spinAt(spin);f.lat=M.mix(spinFrom[0],D.LAT[spinTo],M.smooth(s.p));f.lon=spinFrom[1]+spinDLon*s.p;f.spin=spinDLon*s.v;f.zoom=1-0.3*s.v/M.spinV;f.sun=M.slerp(Array(spinFrom[2...4]),D.SUN[spinTo],s.p);f.numbered=spin<1.4 ? 1-M.seg(spin,0,0.2) : M.seg(spin,1.4,1.65) }
  else if free { f.lat=freeLat;f.lon=freeLon;f.zoom=freeZ;f.sun=freeSun }
  else { let u=get("fu"),uc=M.clamp(u),c=M.slerp(flight.a,flight.b,u);f.lat=M.lat(c);f.lon=M.lon(c);f.zoom=M.mix(flight.za,1,uc)-0.4*sin(.pi*uc)*M.hopDip(flight.hop);f.sun=M.slerp(flight.sa,flight.sb,uc) }
  f.hour=get("hour");if abs(f.hour)>1e-4 { f.sun=yaw(f.sun,-15*f.hour) };f.cardPos=get("card");f.press=get("press");f.arcPos=min(max(f.cardPos,0),23);f.comet=1;f.selF=spin>=0 ? Double(spin<0.3 ? flight.from : flight.to) : f.cardPos;f.from=flight.from;f.leave=M.clamp(leave/0.3);f.to=flight.to;f.land=land>=0 ? land : -1;f.calloutOut=get("hide");f.sheen=land>=0.1 && land<=0.8 ? M.inOut(M.seg(land,0.1,0.8)) : -1;f.hour=get("hour");f.shuffle=get("shuffle");f.shuffleRot=get("rotation");f.pulse=pulse;return f
 }
 func configure(_ play:Bool,_ reduce:Bool) { autoPlay=play;reduced=reduce;if options==nil || options!.0 != play || options!.1 != reduce { auto=play && !reduce;options=(play,reduce) };if pin != nil { auto=true } }
 func suspend() { previous=nil }
 func step(_ stamp:Double) { let dt=min(max(0,previous.map { stamp-$0 } ?? 0.016),0.05);previous=stamp;advance(dt) }
 private func snapshot()->(c:[Double],sun:[Double],zoom:Double,f:RaceGlobeCalendarFrame) { let f=frame;return (D.vec(f.lat,f.lon),abs(f.hour)>1e-4 ? yaw(f.sun,15*f.hour) : f.sun,f.zoom,f) }
 private func takeover() {
  homing=false;guard auto else { pin=nil;return };let s=snapshot(),f=s.f;auto=false;pin=nil
  flight=Flight(a:s.c,b:D.W[f.to],sa:s.sun,sb:D.SUN[f.to],za:s.zoom,hop:M.angle(s.c,D.W[f.to]),from:f.from,to:f.to);set("fu",0)
  let k=Int(floor(f.cardPos)),fr=f.cardPos-Double(k),a=M.autoTrack[min(max(k,0),8)],b=M.autoTrack[min(max(k+1,0),8)]
  set("card",abs(b-a)==1 ? Double(a)+fr*Double(b-a) : Double(fr<0.5 ? a : b));land=f.land>=0 ? f.land : -1;leave=f.leave*0.3;set("press",f.press);set("hide",f.calloutOut);spin = -1;free=false;pulse = -1
 }
 func flyTo(_ target:Int,_ velocity:Double=0,_ d:Double=15.1,_ k:Double=93.4) {
  let target=min(max(target,0),23),s=snapshot(),from=land>=0 ? flight.to : flight.from;spin = -1;free=false
  flight=Flight(a:s.c,b:D.W[target],sa:s.sun,sb:D.SUN[target],za:s.zoom,hop:M.angle(s.c,D.W[target]),from:from,to:target);land = -1;if from==target { leave=9 } else { leave=0 };timing("hide",0,0.001);set("fu",0);spring("fu",1,d,k,velocity);spring("card",Double(target))
 }
 func advance(_ dt:Double) {
  now+=dt;if pin != nil { return };animate();if auto { time=(time+dt).truncatingRemainder(dividingBy:10.5);return };if land>=0 { land+=dt };leave+=dt;if pulse>=0 { pulse=pulse>1 ? -1 : pulse+dt }
  if spin>=0 {
   spin+=dt;let sc=M.spinAt(min(1.6,spin*1.6/1.4));set("card",M.mix(spinCard,Double(spinTo),sc.p))
   if spin>=1.6 { spin = -1;flight=Flight(a:D.W[spinTo],b:D.W[spinTo],sa:D.SUN[spinTo],sb:D.SUN[spinTo],za:1,hop:0,from:flight.from,to:spinTo);set("fu",1);set("card",Double(spinTo));land=0;set("hide",0);buzz("medium");lastTouch=now };return
  }
  if free && coasting && drag != 2 { let k=pow(0.998,dt*1000);vLat*=k;vLon*=k;freeLon+=vLon*dt;freeLat=min(max(freeLat+vLat*dt,-70),70)
   if hypot(vLat,vLon)<8 { let v=D.vec(freeLat,freeLon);var best = -1,a=12.0;for i in 0..<24 { let d=M.angle(v,D.W[i]);if d<a { a=d;best=i } };vLat=0;vLon=0;coasting=false;if best>=0 { flyTo(best,0,14,48.7);buzz("selection") };lastTouch=now }
  }
  if !free && land<0 && !touching && get("fu")>=0.985 { land=0;set("hide",0);buzz("medium") }
  if !reduced && autoPlay && !touching && now-lastTouch>2 {
   let parked=free && vLat==0 && vLon==0,settled=(parked || (!free && get("fu")>0.999)) && abs(get("hour"))<0.005 && abs(get("card")-get("card").rounded())<0.004
   if settled { let home = !parked && flight.to==14 && abs(get("card")-14)<0.004 && M.angle(D.vec(frame.lat,frame.lon),D.W[14])<0.05
    if home && land>1.6 { time=0;auto=true;homing=false } else if !home && !homing { homing=true;flyTo(14,0,22.1,157.9) }
   }
  }
 }
 func down(_ x:Double,_ y:Double) {
  takeover();touching=true;lastTouch=now;region=y>cardTop-6 && y<cardTop+134 ? 1 : hypot(x-41,y-90)<30 ? 2 : 0;drag=0
  if region==1 { spring("press",1,30.5,322.3);buzz("soft") };if region==2 { spring("shuffle",1,30.5,322.3) }
  if spin>=0 && region != 2 { let s=snapshot();flight=Flight(a:s.c,b:D.W[flight.to],sa:s.sun,sb:D.SUN[flight.to],za:s.zoom,hop:M.angle(s.c,D.W[flight.to]),from:flight.from,to:flight.to);set("fu",0);spin = -1 }
  if free { vLat=0;vLon=0;coasting=false };if get("fu")<0.999 && region != 2 { channels["fu"]=nil;channels["card"]=nil }
 }
 func startDrag() {
  let s=snapshot()
  if region==1 { drag=1;grabC=s.c;grabSun=s.sun;grabZ=s.zoom;grabFrom=land>=0 ? flight.to : flight.from;p0=get("card");target = -1;timing("hide",1,0.1) }
  else if region==0 { drag=2;freeLat=M.lat(s.c);freeLon=M.lon(s.c);freeZ=s.zoom;freeSun=s.sun;gLat=freeLat;gLon=freeLon;free=true;vLat=0;vLon=0;timing("hide",1,0.12) }
 }
 func longPress() { if region==0 { drag=3;channels["hour"]=nil;hour0=get("hour");buzz("soft") } }
 func move(_ dx:Double,_ dy:Double) {
  if drag==1 {
   var raw=p0-dx/366;if raw<0 { raw = -(-raw/(1 + -raw/(120/366))) };if raw>23 { let over=raw-23;raw=23+over/(1+over/(120/366)) };set("card",raw)
   let t=min(max(raw>=p0 ? Int(floor(p0+1e-4))+1 : Int(ceil(p0-1e-4))-1,0),23),span=abs(Double(t)-p0),u=span<1e-4 ? 0 : M.clamp(abs(raw-p0)/span)
   if t != target { target=t;if land>=0 { leave=0 };flight=Flight(a:grabC,b:D.W[t],sa:grabSun,sb:D.SUN[t],za:grabZ,hop:M.angle(grabC,D.W[t]),from:grabFrom,to:t);land = -1 };set("fu",u)
  } else if drag==2 { let z=max(freeZ,0.4);freeLon=gLon-dx*0.19/z;freeLat=min(max(gLat+dy*0.19/z,-70),70) }
  else if drag==3 { set("hour",min(max(hour0+dx/12,-12),12)) }
 }
 func up(_ vx:Double=0,_ vy:Double=0,_ success:Bool=true,_ tap:Bool=false) {
  if tap && region==2 { shuffle() }
  else if drag==1 { let v=success ? -vx/366 : 0
   if target<0 { flyTo(Int(floor(get("card")+0.5))) } else { let back=min(max(target>Int(floor(p0)) ? Int(ceil(p0-1e-4-1))+1 : Int(floor(p0+1e-4+1))-1,0),23),projected=get("card")+v*0.12,final=abs(projected-Double(target))<=abs(projected-Double(back)) ? target : back
    if final==target { spring("fu",1,15.1,93.4,v*(Double(target)>p0 ? 1 : -1)/max(abs(Double(target)-p0),0.05));spring("card",Double(target),22.1,157.9,v) } else { flyTo(final) }
   }
  } else if drag==2 { let z=max(freeZ,0.4);vLon=success && !reduced ? -vx*0.19/z : 0;vLat=success && !reduced ? vy*0.19/z : 0;coasting=true }
  else if drag==3 { timing("hour",0,0.9,2) }
  else if tap && region==0 { let f=frame,m=M.rows(f.lat,f.lon),sel=M.project(m,D.W[f.to],300*f.zoom)
   if f.land>=0.4 && abs(xTap-sel.x)<110 && yTap<sel.y-38 && yTap>sel.y-110 { pulse=0;buzz("light") }
   else { var best = -1,d=24.0;for i in 0..<24 { let q=M.project(m,D.W[i],300*f.zoom),dist=hypot(q.x-xTap,q.y-20-yTap);if q.vis>=0.5 && q.z>=0.3 && dist<d { d=dist;best=i } };if best>=0 && best != f.to { flyTo(best);buzz("light") } }
  }
  if drag==0 && !free && spin<0 { if abs(get("card")-get("card").rounded())>0.004 { flyTo(Int(floor(get("card")+0.5))) } else if get("fu")<1 { spring("fu",1,15.1,93.4) } }
  drag=0;touching=false;lastTouch=now;spring("press",0,30.5,322.3);spring("shuffle",0,30.5,322.3)
 }
 private var xTap=0.0,yTap=0.0
 func tapPosition(_ x:Double,_ y:Double) { xTap=x;yTap=y }
 func shuffle() {
  takeover();let s=snapshot(),cur=flight.to,r=now*7919.37;var to=min(22,Int(floor((r-floor(r))*23)));if to>=cur { to+=1 };spring("rotation",get("rotation")+360,30.5,322.3)
  if reduced { flyTo(to,0,22.1,157.9);return };let lat=M.lat(s.c),lon=M.lon(s.c),west=((lon-D.LON[to]).truncatingRemainder(dividingBy:360)+360).truncatingRemainder(dividingBy:360)
  spinFrom=[lat,lon]+s.sun;spinTo=to;spinDLon = -(360+west);spinCard=get("card");channels["card"]=nil;channels["fu"]=nil;free=false;timing("hide",1,0.1);flight.from=land>=0 ? flight.to : flight.from;flight.to=to;if land>=0 { leave=0 };land = -1;spin=0
 }
 func select(_ delta:Int) { takeover();flyTo(min(max(frame.to+delta,0),23));lastTouch=now }
}
