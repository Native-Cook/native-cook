import Foundation
import CoreGraphics

final class HourScrubSkyEngine {
 private typealias M=HourScrubSkyMotion
 private struct Channel { var from:Double,to:Double,at:Double,d:Double,k:Double,v:Double,duration:Double,curve:Int }
 var autoPlay=true,reduced=false,auto=true,pin:Double?,tl=0,time=0.0,now=0.0,height=874.0
 var buzz:(String)->Void={ _ in },onHourChange:((Int)->Void)?
 private var options:(Bool,Bool)?,previous:Double?,l:[String:Double]=["h":0,"hp":0,"hq":0,"cs":0,"rs":0,"pill":0,"pillDark":0,"disc":1,"gPlay":1,"gPause":0,"unit":0,"hold":0,"press0":1,"press1":1,"press2":1],channels:[String:Channel]=[:]
 private(set) var drag=0,touching=false,ink=[Double](repeating:0,count:8)
 private var inkTarget=[Double](repeating:0,count:8),trail=0.0,hPrev=0.0,hTarget=0.0,h0=0.0,x0=0.0,velocity=0.0,decaying=false,lastTouch = -10.0,homing=0,homeAt=0.0,bound=false,lastPill = -1,lastHalf=0,lastHaptic = -10.0
 var finger=CGPoint.zero
 var unit:Double { self.get("unit") };var hold:Double { self.get("hold") };var press:[Double] { (0..<3).map { get("press\($0)") } }
 init() {
  #if DEBUG
  if let raw=UserDefaults.standard.object(forKey:"hourScrubSkyTime") { pin=(raw as? NSNumber)?.doubleValue ?? (raw as? String).flatMap(Double.init);time=pin ?? 0 }
  #endif
 }
 private func get(_ key:String)->Double { l[key] ?? 0 }
 private func set(_ key:String,_ value:Double) { channels[key]=nil;l[key]=value }
 private func timing(_ key:String,_ target:Double,_ duration:Double,_ curve:Int=0) { if reduced && key != "hq" { set(key,target);return };channels[key]=Channel(from:get(key),to:target,at:now,d:0,k:0,v:0,duration:duration,curve:curve) }
 private func spring(_ key:String,_ target:Double,_ d:Double,_ k:Double,_ velocity:Double=0) {
  if reduced { set(key,target);return };let current=get(key)
  if let c=channels[key],c.duration==0 && c.to==target { return }
  channels[key]=Channel(from:current,to:target,at:now,d:d/4,k:k/4,v:velocity,duration:0,curve:0)
 }
 private func animate() { for (key,c) in channels {
  let t=now-c.at
  if c.duration>0 { let u=M.clamp(t/c.duration),e=c.curve==1 ? u : c.curve==2 ? M.inOut(u) : u<0.5 ? 2*u*u : 1-pow(-2*u+2,2)/2;l[key]=M.mix(c.from,c.to,e);if u>=1 { channels[key]=nil } }
  else { l[key]=M.spring(t,c.from,c.to,c.v,c.d,c.k);if t>3 { l[key]=c.to;channels[key]=nil } }
 } }
 var frame:HourScrubSkyFrame {
  if auto || tl>0 { return M.frame(pin ?? time) }
  return HourScrubSkyFrame(h:reduced ? get("hq") : get("h"),rate:reduced ? 0 : trail,hp:reduced ? get("hq") : get("hp"),cs:get("cs"),rs:get("rs"),pill:get("pill"),pillDark:get("pillDark"),disc:get("disc"),gPlay:get("gPlay"),gPause:get("gPause"),gReset:0,resetRot:0,flare:0,sway:reduced ? 0 : now)
 }
 func configure(_ play:Bool,_ reduce:Bool) { autoPlay=play;reduced=reduce;if options==nil || options!.0 != play || options!.1 != reduce { auto=play && !reduce;options=(play,reduce) };if pin != nil { auto=true } }
 func suspend() { previous=nil }
 func step(_ stamp:Double) {
  let dt=min(max(0,previous.map { stamp-$0 } ?? 0.016),0.05);previous=stamp;advance(dt)
 }
 func advance(_ dt:Double) {
  now+=dt
  if pin != nil { inks(dt,true);return };animate()
  if auto { time=(time+dt).truncatingRemainder(dividingBy:12) }
  else if tl==1 { time+=dt;if time>=10.6 { time=10.6;tl=2 } }
  else if tl==3 { time+=dt;if time>=11.8 { goRest() } }
  else if tl==0 {
   if drag==2 { set("h",get("h")+(hTarget-get("h"))*(1-exp(-dt/0.03)));set("hp",get("h")) }
   if decaying {
    velocity*=pow(0.995,dt*1000);var h=get("h")+velocity*dt
    if h<0 || h>24 { h=h<0 ? 0 : 24;decaying=false;buzz("rigid") }
    else if abs(velocity)<0.4 { decaying=false;let target=floor(h*2+0.5)/2;spring("h",target,40.4,503.5,velocity);spring("hp",target,40.4,503.5,velocity) }
    if decaying || h==0 || h==24 { set("h",h);set("hp",h) }
   }
   if (touching || decaying || homing==1) && dt>0 { let r=(get("h")-hPrev)/dt;trail+=(min(max(r,-8),8)-trail)*0.3 }
   else { trail*=exp(-dt/0.12);if abs(trail)<0.01 { trail=0 } };hPrev=get("h")
   if reduced && touching { let q=floor(get("h")*2+0.5)/2;if abs(q-get("hq"))>0.01 && channels["hq"]?.to != q { timing("hq",q,0.2) } }
   if !touching && !decaying && now-lastTouch>2 && autoPlay && !reduced {
    if homing==0 {
     if get("cs")==0 && get("pill")<0.001 && (get("h")<0.001 || get("h")>23.999) { goRest();time=0;auto=true }
     else { homing=1;let target=get("h")>12 ? 24.0 : 0,duration=0.5+0.09*abs(target-get("h"));timing("h",target,duration,2);timing("hp",target,duration,2) }
    } else if homing==1 && (get("h")<0.001 || get("h")>23.999) { homing=2;homeAt=now;if get("cs")>0 { timing("rs",0.8,0.8,1) };spring("pill",0,22.1,157.9);timing("pillDark",0,0.16);spring("hp",0,22.1,157.9) }
    else if homing==2 && now-homeAt>1 { goRest();time=0;auto=true }
   }
  }
  inks(dt,false)
  let i=min(max(Int(floor(frame.hp+0.5)),0),23);if i != lastPill { lastPill=i;onHourChange?(i) }
  if !auto && touching { let half=Int(floor(frame.h*2));if half != lastHalf && now-lastHaptic>0.1 { buzz("selection");lastHaptic=now };lastHalf=half }
 }
 private func inks(_ dt:Double,_ snap:Bool) {
  let colors=HourScrubSkyColors(frame.h),ys=[30.0,84,605,651,675,705,745,825]
  for i in 0..<8 { let v=HourScrubSkyColors.lightness(colors.sky(reduced ? 450 : ys[i],height));if snap { inkTarget[i]=v<0.46 ? 1 : 0 } else if v<0.42 { inkTarget[i]=1 } else if v>0.5 { inkTarget[i]=0 };let step=snap ? 1 : dt/0.12;ink[i]+=min(max(inkTarget[i]-ink[i],-step),step) }
 }
 private func goRest() { channels=[:];tl=0;homing=0;for k in ["h","hp","hq","cs","rs","pill","pillDark","gPause","hold"] { l[k]=0 };l["disc"]=1;l["gPlay"]=1;hTarget=0;trail=0;lastTouch=now }
 private func takeover() {
  let f=frame
  for k in ["h","hp","rs","pill","pillDark"] { channels[k]=nil }
  if auto || tl>0 {
   set("h",f.h);set("hp",f.hp);set("hq",floor(f.h*2+0.5)/2);hTarget=f.h;hPrev=f.h;trail=f.rate
   let restored=f.rs>=0.8 || f.cs==0;set("cs",restored ? 0 : f.cs);set("rs",restored ? 0 : f.rs);set("pill",max(f.pill,0));set("pillDark",f.pillDark);set("disc",f.disc);set("gPlay",f.gPlay);set("gPause",f.gPause)
   spring("disc",1,19,195);spring("gPlay",1,19,195);timing("gPause",0,0.1);auto=false;tl=0
  };pin=nil;homing=0;decaying=false;touching=true;lastTouch=now
 }
 private func engage() { if get("cs")==0 || get("rs")>0 { set("rs",0);set("cs",0);timing("cs",0.5,0.5,1) };spring("pill",1,19,195);timing("pillDark",1,0.16) }
 func beginDrag(_ x:Double,_ y:Double)->Bool {
  let sun=M.sun(frame.h)
  if hypot(x-sun.x,y-sun.y)<44 && sun.y<520 { takeover();drag=2;hTarget=get("h");finger=CGPoint(x:x,y:y);spring("hold",1,19,195);buzz("soft") }
  else if y>700 && y<775 && x>60 && x<390 { takeover();drag=1;h0=get("hp");x0=x;set("h",h0);buzz("light") }
  else { return false };engage();return true
 }
 func move(_ x:Double,_ y:Double) {
  if drag==1 || drag==3 {
   var h=h0+(x-x0)/(289/24)
   if h>24 { let over=(h-24)*(289/24);h=24+over/(1+over/120)/(289/24) } else if h<0 { let over = -h*(289/24);h = -over/(1+over/120)/(289/24) }
   set("h",h);set("hp",h);let out=h<0 || h>24;if out && !bound { buzz("rigid") };bound=out
  } else if drag==2 {
   finger=CGPoint(x:x,y:y);var best=hTarget,dist=Double.greatestFiniteMagnitude
   for i in -30...30 { let h=min(max(hTarget+Double(i)*0.05,0),24),s=M.sun(h),d=pow(s.x-x,2)+pow(s.y-y,2);if d<dist { best=h;dist=d } };hTarget=best
   if y>540 || M.sun(best).y>520 { drag=3;set("h",best);set("hp",best);h0=best;x0=x;spring("hold",0,22.1,157.9) }
  }
 }
 func endDrag(_ vx:Double,_ success:Bool=true) {
  guard drag != 0 else { return };let v=success ? drag==2 ? trail : vx/(289/24) : 0,h=get("h")
  if h<0 || h>24 { let t=h<0 ? 0.0 : 24;spring("h",t,40.4,503.5);spring("hp",t,40.4,503.5) }
  else if abs(v)>0.4 && !reduced && success { velocity=min(max(v,-8),8);decaying=true }
  else { let t=floor(h*2+0.5)/2;spring("h",t,40.4,503.5);spring("hp",t,40.4,503.5) }
  drag=0;bound=false;spring("hold",0,22.1,157.9);touching=false;lastTouch=now
 }
 func pressAt(_ x:Double,_ y:Double) { if x>=11 && x<=103 && y>=59 && y<=109 { timing("press0",0.97,0.09) } else if x>=309 && x<=383 && y>=579 && y<=631 { timing("press1",0.97,0.09) } else if x>=12 && x<=390 && y>=792 && y<=858 { timing("press2",0.97,0.09) } }
 func releasePress() { for i in 0..<3 { spring("press\(i)",1,30.5,322.3) } }
 func tap(_ x:Double,_ y:Double) {
  if abs(x-39)<=26 && abs(y-745)<=26 {
   if reduced { takeover();goRest();touching=false }
   else if tl==1 { takeover();touching=false }
   else if tl==2 { tl=3 }
   else { takeover();touching=false;let h=get("h")>=23.95 ? 0 : get("h");time=h<=0.001 && get("cs")==0 ? 0.2 : max(M.timeForHour(h),0.62);tl=1 };buzz("selection")
  } else { takeover();touching=false;if x>10 && x<220 && y>535 && y<640 { spring("unit",unit>0.5 ? 0 : 1,30.5,322.3);buzz("selection") } }
  lastTouch=now;releasePress()
 }
 func adjust(_ direction:Double) { takeover();engage();let h=min(max(get("h")+direction*0.5,0),24);set("h",h);set("hp",h);set("hq",h);touching=false;lastTouch=now }
}
