import SwiftUI
import UIKit

struct HourScrubSky:View {
 static let loop=12.0
 var autoPlay=true
 var onHourChange:((Int)->Void)?
 @Environment(\.accessibilityReduceMotion) private var reduced
 @Environment(\.scenePhase) private var phase
 @State private var engine=HourScrubSkyEngine()
 @State private var down=false
 @State private var active=false
 @State private var attempted=false
 @GestureState private var fingerDown=false
 private let art=HourScrubSkyArtwork.shared
 private typealias M=HourScrubSkyMotion
 private typealias C=HourScrubSkyColors
 private typealias A=HourScrubSkyArtwork
 var body:some View {
  GeometryReader { geo in
   let w=Double(geo.size.width),h=Double(geo.size.height),s=min(w/402,h/870),ch=h/s,offset=(w-402*s)/2
   TimelineView(.animation(minimumInterval:1/60,paused:phase != .active)) { timeline in
    let _=update(timeline.date,ch)
    let f=engine.frame,c=C(f.h)
    let hour=(f.h+12).truncatingRemainder(dividingBy:24)
    let darkSky=hour<6 || hour>=19 || engine.ink[0]>0.5
    Canvas { ctx,_ in
     ctx.translateBy(x:offset/s,y:0)
     let sky=HourScrubSkyUniforms.shader(f,c,ch,art.low,art.high,reduced,engine.hold,engine.finger)
     ctx.fill(Path(CGRect(x:-offset/s,y:0,width:w/s,height:ch)),with:.shader(sky))
     draw(&ctx,f,c,ch,sky)
    }.frame(width:w/s,height:ch,alignment:.topLeading).drawingGroup(opaque:true,colorMode:.nonLinear).scaleEffect(s,anchor:.topLeading)
     .frame(width:w,height:h,alignment:.topLeading).contentShape(Rectangle()).gesture(finger(s,offset))
     .preferredColorScheme(darkSky ? .dark : .light)
   }
  }.ignoresSafeArea().background(Color.blue)
   .accessibilityElement(children:.ignore).accessibilityLabel("Port Ansel weather. Drag the hour ruler or sun through the day. Tap Play to run, pause or reset. Tap temperature to change units.")
   .accessibilityAdjustableAction { direction in engine.adjust(direction == .increment ? 1 : -1) }
   .accessibilityAction(named:Text("Play, pause or reset")) { engine.tap(39,745) }
   .accessibilityAction(named:Text("Change temperature unit")) { engine.tap(80,590) }
   .onChange(of:fingerDown) { _,value in if !value && down { cancel() } }
   .onChange(of:phase) { _,value in if value != .active { cancel() };engine.suspend() }
 }
 private func update(_ date:Date,_ ch:Double) { engine.height=ch;engine.configure(autoPlay,reduced);engine.onHourChange=onHourChange;engine.buzz={ cue in if cue=="selection" { UISelectionFeedbackGenerator().selectionChanged() } else { UIImpactFeedbackGenerator(style:cue=="rigid" ? .rigid : cue=="medium" ? .medium : cue=="soft" ? .soft : .light).impactOccurred() } };engine.step(date.timeIntervalSinceReferenceDate) }
 private func finger(_ s:Double,_ offset:Double)->some Gesture {
  DragGesture(minimumDistance:0).updating($fingerDown) { _,state,_ in state=true }.onChanged { v in
   let x=(v.location.x-offset)/s,y=v.location.y/s
   if !down { down=true;active=false;attempted=false;engine.pressAt((v.startLocation.x-offset)/s,v.startLocation.y/s) }
   if !attempted && hypot(v.translation.width,v.translation.height)>2 { attempted=true;active=engine.beginDrag((v.startLocation.x-offset)/s,v.startLocation.y/s) }
   if active { engine.move(x,y) }
  }.onEnded { v in
   if active { engine.endDrag(v.velocity.width/s) }
   else if !attempted && hypot(v.translation.width,v.translation.height)<=10 { engine.tap((v.location.x-offset)/s,v.location.y/s) }
   engine.releasePress();down=false;active=false;attempted=false
  }
 }
 private func cancel() { if active { engine.endDrag(0,false) };engine.releasePress();down=false;active=false;attempted=false }
 private func rect(_ c:inout GraphicsContext,_ x:Double,_ y:Double,_ w:Double,_ h:Double,_ r:Double,_ col:[Double],_ alpha:Double=1) { c.fill(Path(roundedRect:CGRect(x:x,y:y,width:w,height:h),cornerRadius:r),with:.color(A.color(col,alpha))) }
 private func fillText(_ c:inout GraphicsContext,_ text:String,_ x:Double,_ cap:Double,_ size:Double,_ weight:Int=500,_ track:Double=0,_ tnum:Bool=false,_ rgb:[Double],_ alpha:Double=1) {
  let p=art.run(text,size,weight,track,tnum).0.applying(CGAffineTransform(translationX:x,y:cap+0.7*size));c.fill(p,with:.color(A.color(rgb,alpha)))
 }
 private func gradient(_ y:Double,_ ch:Double,_ top:[Double])->GraphicsContext.Shading {
  let a=M.clamp((y-12)/ch),b=max(M.clamp((y+12)/ch),a+0.0001)
  return .linearGradient(Gradient(stops:[.init(color:A.color(top),location:0),.init(color:A.color(top),location:a),.init(color:A.color(0x0B0D12),location:b),.init(color:A.color(0x0B0D12),location:1)]),startPoint:.zero,endPoint:CGPoint(x:0,y:ch))
 }
 private func inkText(_ c:inout GraphicsContext,_ text:String,_ x:Double,_ cap:Double,_ size:Double,_ weight:Int,_ track:Double,_ tnum:Bool,_ yInk:Double,_ ch:Double,_ top:[Double]) {
  c.fill(art.run(text,size,weight,track,tnum).0.applying(CGAffineTransform(translationX:x,y:cap+0.7*size)),with:gradient(yInk,ch,top))
 }
 private func icon(_ c:inout GraphicsContext,_ name:String,_ x:Double,_ y:Double,_ rgb:[Double],_ stroke:Double=0,_ scale:Double=1,_ rotation:Double=0) {
  guard let p=art.icons[name] else { return };var ctx=c;ctx.translateBy(x:x,y:y);ctx.rotate(by:.degrees(rotation));ctx.scaleBy(x:scale,y:scale)
  if stroke>0 { ctx.stroke(p,with:.color(A.color(rgb)),style:StrokeStyle(lineWidth:stroke,lineCap:.round,lineJoin:.round)) } else { ctx.fill(p,with:.color(A.color(rgb)),style:FillStyle(eoFill:name=="MORE")) }
 }
 private func rim(_ ctx:inout GraphicsContext,_ rect:CGRect,_ r:Double,_ c:C,_ k:Double) {
  let path=Path(roundedRect:rect,cornerRadius:r),glow=M.clamp((c.h-5.5)/0.3)*(1-M.clamp((c.h-6.4)/0.3)),base=Color.white.opacity(M.mix(0.6,0.25,k))
  if glow>0.01 { let sun=M.sun(c.h),dx=sun.x-rect.midX,dy=sun.y-rect.midY,len=max(hypot(dx,dy),1)
   ctx.stroke(path,with:.linearGradient(Gradient(colors:[A.color(c.sun,0.8*glow+0.6*(1-glow)),base]),startPoint:CGPoint(x:rect.midX+dx/len*60,y:rect.midY+dy/len*60),endPoint:CGPoint(x:rect.midX-dx/len*60,y:rect.midY-dy/len*60)),lineWidth:1.5)
  } else { ctx.stroke(path,with:.color(base),lineWidth:1) }
 }
 private func draw(_ ctx:inout GraphicsContext,_ f:HourScrubSkyFrame,_ c:C,_ ch:Double,_ sky:Shader) {
  let dark=C.rgb(0x0B0D12),light=C.rgb(0xF7F8FA),white=[1.0,1,1],pill=C.rgb(0x16181D),red=C.rgb(0xE5484D),glassN=C.rgb(0x1A1D26)
  func mix(_ a:[Double],_ b:[Double],_ k:Double)->[Double] { C.mix(a,b,k) }
  func ink(_ i:Int)->[Double] { mix(dark,light,engine.ink[i]) }
  let yInk=reduced ? engine.ink[6]>0.5 ? ch+200 : -200 : c.yInk(ch)
  // Sky-tinted mode chip.
  var chip=ctx;chip.translateBy(x:57,y:84);chip.scaleBy(x:engine.press[0],y:engine.press[0]);chip.translateBy(x:-57,y:-84)
  let k=engine.ink[1],chipColor=mix(c.sky(84,ch),white,M.mix(0.45,0.22,k))
  rect(&chip,15,63,84,42,21,chipColor,0.85);chip.stroke(Path(roundedRect:CGRect(x:15,y:63,width:84,height:42),cornerRadius:21),with:.color(Color.white.opacity(M.mix(0.45,0.14,k))),lineWidth:1)
  icon(&chip,"LEAF",28,75,ink(1),2.7,0.75);icon(&chip,"SLASH",28,75,chipColor,5.5,0.75);icon(&chip,"SLASH",28,75,ink(1),2.7,0.75)
  icon(&chip,"SUN_DISC",59,74,ink(1),0,0.75);icon(&chip,"SUN_RAYS",59,74,ink(1),2.4,0.75);icon(&chip,"CLOUD",59,74,chipColor,3.4,0.75);icon(&chip,"CLOUD",59,74,ink(1),0,0.75)
  // Folding sentence and rolling weather condition, all crossed by the real ink terminator.
  var wordDY=Array(repeating:0.0,count:8),lineDY=[0.0,0,0],clip=[1.0,1,1],cond=1.0
  if f.rs>0 { cond = -M.inOut(M.clamp(f.rs/0.22));for j in 0..<8 { wordDY[j]=(1-M.out(M.clamp((f.rs-0.06*Double(j))/0.24)))*40 } }
  else if f.cs>0 { let a=M.inOut(M.clamp(f.cs/0.22));for j in 0..<3 { wordDY[j] = -46*a };cond=1-a;for i in 1..<3 { let k=M.inOut(M.clamp((f.cs-0.04*Double(i))/0.22));lineDY[i] = -14*k;clip[i]=1-k } }
  for line in 0..<3 {
   var text=ctx;let top=150+46*Double(line)-12;text.clip(to:Path(CGRect(x:0,y:top+lineDY[line],width:402,height:max(0,46*clip[line]))))
   for j in 0..<8 where A.lineOf[j]==line { inkText(&text,A.words[j],art.wordX[j],150+46*Double(line)+lineDY[line]+wordDY[j],34,500,-0.02,false,yInk,ch,light) }
   if line==0 && cond<1 { let roll=M.conditionRoll(f.h);for i in 0..<3 where roll[i]<=2 { let dy=(cond+roll[i])*46;if abs(dy)<46 { inkText(&text,A.conditions[i],22,150+dy,34,500,-0.02,false,yInk,ch,light) } } }
  }
  func roll(_ fahrenheit:Bool)->Double {
   let q=f.h*2,j=floor(q),k=M.clamp((q-j-0.3)/0.4)
   func read(_ n:Double)->Double { let c=M.temp(n/2);return floor((fahrenheit ? c*1.8+32 : c)+0.5) }
   let a=read(j);return a+(read(j+1)-a)*k*k*(3-2*k)
  }
  let position=M.mix(roll(false),roll(true),engine.unit),tens=floor(position/10)+M.clamp(position.truncatingRemainder(dividingBy:10)-9),ones=position.truncatingRemainder(dividingBy:10),pitch=0.58*106,x0=22-0.05*106,win=0.7*106+20
  for (i,p) in [tens,ones].enumerated() { let x=x0+pitch*Double(i),base=Int(floor(p));var digit=ctx;digit.clip(to:Path(CGRect(x:x-6,y:539,width:pitch+10,height:win)))
   for d in (base-1)...(base+1) { let dy=(Double(d)-p)*win;if abs(dy)<win { inkText(&digit,String(((d%10)+10)%10),x,549+dy,106,300,-0.04,true,yInk,ch,C.rgb(0xF3ECC0)) } }
  }
  ctx.stroke(Path(ellipseIn:CGRect(x:x0+pitch*2+0.02*106+2,y:551,width:18,height:18)),with:gradient(yInk,ch,C.rgb(0xF3ECC0)),lineWidth:4)
  // Arrow control and the two metadata rows.
  var arrow=ctx;arrow.translateBy(x:346,y:605);arrow.scaleBy(x:engine.press[1],y:engine.press[1]);arrow.translateBy(x:-346,y:-605)
  let ar=CGRect(x:313,y:583,width:66,height:44);arrow.drawLayer { sh in sh.addFilter(.shadow(color:A.color([0.04,0.06,0.12],M.mix(0.1,0.3,engine.ink[2])),radius:4,x:0,y:2));rect(&sh,313,583,66,44,22,mix(white,glassN,engine.ink[2]),M.mix(0.9,0.82,engine.ink[2])) };rim(&arrow,ar,22,c,engine.ink[2]);icon(&arrow,"ARROW",346,605,ink(2),2)
  icon(&ctx,"NAV",22,645,ink(3),0,0.55);let rolls=M.conditionRoll(f.h),ci=(0..<3).min { abs(rolls[$0])<abs(rolls[$1]) } ?? 0
  fillText(&ctx,"Port Ansel  ·  "+A.conditions[ci],41,646,15,500,0,false,ink(3));fillText(&ctx,"H 30° · L 22° · 56% Humidity · UV 6",22,670.5,13,400,0,true,ink(4),0.8)
  // Ruler, red cluster and the pill's dedicated lane.
  let px=80+289*f.hp/24,pitchTick=289.0/48,show=M.clamp(f.pill*4),base=mix(dark,white,engine.ink[6])
  for i in 0..<49 { let x=80+pitchTick*Double(i),d=abs(x-px)/(4*pitchTick),w=d<1 ? (0.5+0.5*cos(.pi*d))*show : 0,h=M.mix(20,28,w);rect(&ctx,x-1,756-h,2,h,1,mix(base,red,w),M.mix(M.mix(0.32,0.4,engine.ink[6]),1,w)) }
  if f.pill>0.001 {
   let k=f.pill,index=min(max(Int(floor(f.hp+0.5)),0),23),label=M.pillLabel(index),width=max(60,art.run(label,13,600,0,true).1+26)
   let x=M.mix(px-1,min(max(px-width/2,10),392-width),k),y=M.mix(728,690,min(k,1)),w=M.mix(2,width,k),h=M.mix(28,30,min(k,1)),r=min(M.mix(1,15,M.clamp(k)),h/2,w/2),kn=engine.ink[5]
   rect(&ctx,x,y,max(w,0.5),max(h,0.5),r,mix(red,mix(pill,white,kn),f.pillDark));var labelCtx=ctx;labelCtx.clip(to:Path(roundedRect:CGRect(x:x,y:y,width:max(w,0.5),height:max(h,0.5)),cornerRadius:r));fillText(&labelCtx,label,x+w/2-art.run(label,13,600,0,true).1/2,700.4,13,600,0,true,mix(white,dark,kn))
  }
  let controlK=engine.ink[6],discColor=mix(pill,white,controlK),glyphColor=mix(white,pill,controlK)
  ctx.fill(Path(ellipseIn:CGRect(x:39-16*f.disc,y:745-16*f.disc,width:32*f.disc,height:32*f.disc)),with:.color(A.color(discColor)))
  if f.gPlay>0.01 { icon(&ctx,"PLAY",39,745,glyphColor,0,f.gPlay*f.disc) };if f.gPause>0.01 { icon(&ctx,"PAUSE",39,745,glyphColor,0,f.gPause*f.disc) };if f.gReset>0.01 { icon(&ctx,"RESET",39,745,glyphColor,2,f.gReset*f.disc,f.resetRot) }
  // Native backdrop blur samples the same live sky, then receives the original translucent glass fill.
  var tab=ctx;let sc=engine.press[2];tab.translateBy(x:201,y:825);tab.scaleBy(x:sc,y:sc);tab.translateBy(x:-201,y:-825)
  let tr=CGRect(x:12,y:792,width:378,height:66),tk=engine.ink[7]
  tab.drawLayer { shadow in shadow.addFilter(.shadow(color:A.color([0.04,0.06,0.12],M.mix(0.14,0.32,tk)),radius:12,x:0,y:8));rect(&shadow,14,792,374,66,33,mix(white,glassN,tk),0.1) }
  var frost=tab;frost.clip(to:Path(roundedRect:tr,cornerRadius:33));frost.drawLayer { layer in layer.addFilter(.blur(radius:24));layer.fill(Path(CGRect(x:0,y:0,width:402,height:ch)),with:.shader(sky)) }
  rect(&tab,12,792,378,66,33,mix(white,glassN,tk),M.mix(0.72,0.7,tk));rim(&tab,tr,33,c,tk);rect(&tab,16,798,86,54,27,mix(C.rgb(0xE4E8EE),C.rgb(0x2C3140),tk),M.mix(1,0.9,tk))
  for i in 0..<4 { let x=[59.0,153,247,341][i],color=i==0 ? mix(C.rgb(0x1F6FEB),C.rgb(0x5B9BFF),tk) : ink(7);icon(&tab,["NAV","MAP","TROPHY","MORE"][i],x-12,803,color);if i==2 { icon(&tab,"TROPHY_HANDLES",x-12,803,color,2) };let lab=["Nearby","Cities","Rankings","More"][i];fillText(&tab,lab,x-art.run(lab,13).1/2,836.5,13,500,0,false,color) }
 }
}
