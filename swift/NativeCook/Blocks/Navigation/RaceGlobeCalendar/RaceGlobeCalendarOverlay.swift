import SwiftUI

final class RaceGlobeCalendarOverlay {
 private let art=RaceGlobeCalendarArtwork.shared
 private typealias A=RaceGlobeCalendarArtwork
 private typealias M=RaceGlobeCalendarMotion
 private typealias D=RaceGlobeCalendarData
 private func circle(_ c:inout GraphicsContext,_ x:Double,_ y:Double,_ r:Double,_ color:UInt32,_ a:Double=1) { c.fill(Path(ellipseIn:CGRect(x:x-r,y:y-r,width:r*2,height:r*2)),with:.color(A.color(color,a))) }
 private func path(_ points:[CGPoint],_ closed:Bool=false)->Path { var p=Path();p.addLines(points);if closed { p.closeSubpath() };return p }
 private func glass(_ c:inout GraphicsContext,_ rect:CGRect,_ radius:Double,_ blur:Double,_ top:UInt32,_ at:Double,_ bottom:UInt32,_ ab:Double,_ alpha:Double,_ globe:Shader) {
  var g=c;g.opacity*=alpha;var clip=g;clip.clip(to:Path(roundedRect:rect,cornerRadius:radius));clip.drawLayer { layer in layer.addFilter(.blur(radius:blur));layer.fill(Path(CGRect(x:0,y:0,width:402,height:1000)),with:.shader(globe)) }
  g.fill(Path(roundedRect:rect,cornerRadius:radius),with:.linearGradient(Gradient(colors:[A.color(top,at),A.color(bottom,ab)]),startPoint:CGPoint(x:0,y:rect.minY),endPoint:CGPoint(x:0,y:rect.maxY)))
  g.stroke(Path(roundedRect:rect.insetBy(dx:0.5,dy:0.5),cornerRadius:radius-0.5),with:.linearGradient(Gradient(colors:[.white.opacity(0.2),.white.opacity(0.05)]),startPoint:CGPoint(x:0,y:rect.minY),endPoint:CGPoint(x:0,y:rect.maxY)),lineWidth:1)
 }
 func stars(_ c:inout GraphicsContext,_ t:Double) { for s in art.stars { let a=s[3]*(0.8+0.2*sin(2 * .pi*4*t/10.5+(s[4]==0 ? 0 : 2.1)));circle(&c,s[0],s[1],s[2],0xFFFFFF,a) } }
 func draw(_ c:inout GraphicsContext,_ f:RaceGlobeCalendarFrame,_ top:Double,_ globe:Shader) {
  let rows=M.rows(f.lat,f.lon),r=300*f.zoom,projections=D.W.map { M.project(rows,$0,r) }
  arcs(&c,f,rows,r)
  let lnd=M.landing(f.land)
  if lnd.ripple>=0 && f.from != f.to && projections[f.to].vis>0.5 {
   let p=ring(D.W[f.to],34*(1-pow(1-lnd.ripple,2))/r,rows,r)
   c.stroke(p,with:.color(.white.opacity(0.5*(1-lnd.ripple))),lineWidth:2)
   if lnd.ripple>0.15 { c.stroke(ring(D.W[f.to],22*(1-pow(1-(lnd.ripple-0.15)/0.85,2))/r,rows,r),with:.color(A.color(0xB9BCFF,0.35*(1-lnd.ripple))),lineWidth:1.5) }
  }
  var pins:[RaceGlobeCalendarPin]=[]
  for i in 0..<24 {
   let q=projections[i],a=M.smoothstep(0.25,0.48,q.z);if q.vis<0.5 || a<=0.01 { continue }
   let num=M.clamp((2.5-abs(Double(i)-f.selF))*14)*f.numbered
   var sel=0.0;if i==f.to && f.land>=0 { sel=1 };if i==f.from && f.from != f.to { sel=max(sel,1-f.leave) }
   if num<=0.001 && sel<=0.001 { circle(&c,q.x,q.y,3.6,0x7F83FF,0.9);circle(&c,q.x,q.y,3,0x6E72F2);continue }
   var lift=0.0,grow=sel
   if i==f.to && f.from != f.to { lift=f.land<0 ? 10*M.smooth((f.land+0.3)/0.3) : -lnd.drop;grow=f.land<0 ? 0 : lnd.scale>0 ? (lnd.scale-0.6)/0.4 : 0 }
   let k=i==f.to && f.from != f.to && f.land>=0 ? max(num,min(grow,1)) : max(num,sel>0 ? 1 : 0)
   lift*=k;let radius=M.mix(3,M.mix(15,17,grow),k)
   pins.append(RaceGlobeCalendarPin(i:i,x:q.x,y:q.y,hx:q.x,hy:q.y-6*k-radius-lift,r:radius,z:q.z,sel:grow,num:k,lift:lift,a:a))
  }
  for _ in 0..<4 { for p in pins.indices { for q in pins.indices where q>p {
   let a=pins[p],b=pins[q],dx=b.hx-a.hx,dy=b.hy-a.hy,dd=max(hypot(dx,dy),0.01),need=a.r+b.r+4;if dd>=need { continue };let push=need-dd,wa=a.sel>b.sel ? 0.0 : b.sel>a.sel ? 1 : 0.5
   pins[p].hx-=dx/dd*push*wa;pins[p].hy-=dy/dd*push*wa;pins[q].hx+=dx/dd*push*(1-wa);pins[q].hy+=dy/dd*push*(1-wa)
  } } }
  pins.sort { $0.sel==$1.sel ? $0.z<$1.z : $0.sel<$1.sel }
  for p in pins {
   c.drawLayer { sh in sh.addFilter(.blur(radius:2.2));sh.fill(Path(ellipseIn:CGRect(x:p.x-6-p.lift*0.3,y:p.y-2,width:12+p.lift*0.6,height:4)),with:.color(.black.opacity(0.35*p.a*M.clamp(1-p.lift/16)*p.num))) }
   c.fill(path([CGPoint(x:p.hx-p.r*0.5,y:p.hy+p.r*0.55),CGPoint(x:p.x,y:p.y-p.lift),CGPoint(x:p.hx+p.r*0.5,y:p.hy+p.r*0.55)],true),with:.color(A.color(0x4B4FE0,p.a)))
   if p.sel>0.01 { circle(&c,p.hx,p.hy,p.r+2.4,0xFFFFFF,p.a*p.sel) }
   c.fill(Path(ellipseIn:CGRect(x:p.hx-p.r,y:p.hy-p.r,width:p.r*2,height:p.r*2)),with:.linearGradient(Gradient(colors:[A.color(0x5C60F0,p.a),A.color(0x3E42CF,p.a)]),startPoint:CGPoint(x:p.hx,y:p.hy-p.r),endPoint:CGPoint(x:p.hx,y:p.hy+p.r)))
   c.stroke(Path(ellipseIn:CGRect(x:p.hx-p.r+0.5,y:p.hy-p.r+0.5,width:p.r*2-1,height:p.r*2-1)),with:.color(A.color(0x7F83FF,p.a*0.95)),lineWidth:1)
   if p.r>10.5 { var text=c;text.translateBy(x:p.hx,y:p.hy);text.scaleBy(x:p.r/15,y:p.r/15);let str=String(p.i+1);art.text(&text,str,-art.run(str,13,"bold").1/2,4.7,13,"bold",p.a) }
  }
  let k=lnd.callout*(1-f.calloutOut)
  if k>0.005,let pin=pins.first(where:{$0.i==f.to}) { callout(&c,f,pin,k,lnd.circuit,globe) }
  var button=c;let scale=1-0.1*f.shuffle;button.translateBy(x:41,y:90);button.scaleBy(x:scale,y:scale);button.translateBy(x:-41,y:-90)
  glass(&button,CGRect(x:19,y:68,width:44,height:44),22,20,0x22262E,0.55,0x16191F,0.62,1,globe)
  button.translateBy(x:41,y:90);button.rotate(by:.degrees(f.shuffleRot));button.scaleBy(x:20/24,y:20/24);button.translateBy(x:-12,y:-12)
  button.stroke(art.icons["SHUFFLE"]!,with:.color(.white),style:StrokeStyle(lineWidth:2.4,lineCap:.round,lineJoin:.round))
  let first=Int(floor(f.cardPos))-1,focus=Int(floor(f.cardPos+0.5))
  for slot in first...(first+3) { let i=f.track==0 ? M.autoTrack[((slot%8)+8)%8] : slot;if i<0 || i>=24 { continue };let x=24+(Double(slot)-f.cardPos)*366;if x < -358 || x>406 { continue };let sheen=f.track==0 ? slot==f.sheenSlot ? f.sheen : -1 : slot==focus ? f.sheen : -1;card(&c,f,x,top,i,slot==focus ? 1 : 0,sheen,globe) }
 }
 private func callout(_ c:inout GraphicsContext,_ f:RaceGlobeCalendarFrame,_ p:RaceGlobeCalendarPin,_ k:Double,_ circuit:Double,_ globe:Shader) {
  let i=f.to,w=12+max(art.run(art.titles[i],15,"semibold").1,art.run(art.wheres[i],13).1)+54,x=min(max(p.hx-w/2,16),386-w),bottom=p.hy-p.r-2.4-9,sink=f.calloutOut*6,s=M.mix(0.72,1,k),a=M.clamp(k*2.2)*p.a,y=bottom-54+sink
  var g=c;g.translateBy(x:p.hx,y:bottom+sink);g.scaleBy(x:s,y:s);g.translateBy(x:-p.hx,y:-(bottom+sink))
  glass(&g,CGRect(x:x,y:y,width:w,height:54),12,16,0x14161A,0.72,0x0E1014,0.8,a,globe)
  let px=min(max(p.hx,x+16),x+w-16);g.fill(path([CGPoint(x:px-6,y:y+53.5),CGPoint(x:px,y:y+60),CGPoint(x:px+6,y:y+53.5)],true),with:.color(A.color(0x14161A,0.8*a)))
  art.text(&g,art.titles[i],x+12,y+23+(1-k)*4,15,"semibold",a);art.text(&g,art.wheres[i],x+12,y+42+(1-k)*4,13,"regular",0.58*a)
  let cx=x+w-42,cy=y+12;g.fill(Path(roundedRect:CGRect(x:cx,y:cy,width:30,height:30),cornerRadius:8),with:.color(A.color(0x2A2D33,a)))
  var cir=g;cir.translateBy(x:cx+3,y:cy+3);cir.scaleBy(x:24/28,y:24/28);let progress=f.pulse>=0 ? M.out(f.pulse/0.45) : circuit
  cir.stroke(art.circuits[i].trimmedPath(from:0,to:max(0.001,progress)),with:.color(.white.opacity(0.92*a)),style:StrokeStyle(lineWidth:2.3,lineCap:.round,lineJoin:.round))
 }
 private func card(_ c:inout GraphicsContext,_ f:RaceGlobeCalendarFrame,_ x:Double,_ y:Double,_ i:Int,_ focus:Double,_ sheen:Double,_ globe:Shader) {
  let scale=1-0.02*f.press*focus;var g=c;g.translateBy(x:x+177,y:y+64);g.scaleBy(x:scale,y:scale);g.translateBy(x:-x-177,y:-y-64)
  let rect=CGRect(x:x,y:y,width:354,height:128);glass(&g,rect,28,28,0x10141A,0.6,0x0B0E13,0.78,1,globe);g.clip(to:Path(roundedRect:rect,cornerRadius:28))
  if sheen>=0 && sheen<=1 && i>14 { let bx=x+M.mix(-90,444,sheen),color=D.hours[i]>=19 ? A.color(0xFFB25A,0.22*sin(.pi*sheen)) : A.color(0x3E7BFF,0.22*sin(.pi*sheen));var glow=g;glow.blendMode = .plusLighter;glow.fill(Path(rect),with:.linearGradient(Gradient(colors:[.clear,color,.clear]),startPoint:CGPoint(x:bx-70,y:y),endPoint:CGPoint(x:bx+70,y:y+50))) }
  let cw=43+art.run(art.chips[i],13,"semibold").1;g.fill(Path(roundedRect:CGRect(x:x+17,y:y+17,width:cw,height:26),cornerRadius:13),with:.color(.white.opacity(0.1)))
  var icon=g;icon.translateBy(x:x+27,y:y+22.5);icon.scaleBy(x:15/24,y:15/24);icon.stroke(art.icons[i<=14 ? "TROPHY" : "FLAG"]!,with:.color(.white.opacity(0.95)),style:StrokeStyle(lineWidth:3,lineCap:.round,lineJoin:.round))
  art.text(&g,art.chips[i],x+48,y+34.6,13,"semibold",0.95);art.text(&g,art.titles[i],x+17,y+82,24,"display-semibold")
  art.text(&g,art.pre[i],x+17,y+108,14,"regular",0.62)
  let minutes=(D.hours[i]+f.hour*focus)*60,st=minutes/5,si=floor(st+1e-6),roll=M.smoothstep(0.6,1,st-si)
  func hhmm(_ m:Double)->[Character] { let v=((Int(floor(m+0.5))%1440)+1440)%1440;return Array(String(format:"%02d:%02d",v/60,v%60)) }
  let cur=hhmm(si*5),next=hhmm(si*5+5),digitWidth=(0...9).map { art.run(String($0),14).1 }.max()!,colon=art.run(":",14).1
  var cx=x+17+art.run(art.pre[i],14).1;var digits=g;digits.clip(to:Path(CGRect(x:x,y:y+93,width:354,height:20)))
  for p in 0..<5 { let width=p==2 ? colon : digitWidth,a=String(cur[p]),b=String(next[p]),wa=art.run(a,14).1
   if roll>0.001 && a != b { art.text(&digits,a,cx+(width-wa)/2,y+108-roll*17,14,"regular",0.62);art.text(&digits,b,cx+(width-art.run(b,14).1)/2,y+108+(1-roll)*17,14,"regular",0.62) } else { art.text(&digits,a,cx+(width-wa)/2,y+108,14,"regular",0.62) };cx+=width
  };art.text(&g,art.suf[i],cx,y+108,14,"regular",0.62)
 }
 private func ring(_ w:[Double],_ a:Double,_ rows:[Double],_ r:Double)->Path {
  let l=max(hypot(w[2],w[0]),1e-9),e1=[w[2]/l,0,-w[0]/l],e2=[w[1]*e1[2],w[2]*e1[0]-w[0]*e1[2],-w[1]*e1[0]]
  var points:[CGPoint]=[];for k in 0...48 { let theta=Double(k)/48 * .pi*2,ct=cos(theta)*sin(a),st=sin(theta)*sin(a),p=(0..<3).map { w[$0]*cos(a)+e1[$0]*ct+e2[$0]*st },q=M.project(rows,p,r);points.append(CGPoint(x:q.x,y:q.y)) };return path(points)
 }
 private func arcs(_ c:inout GraphicsContext,_ f:RaceGlobeCalendarFrame,_ rows:[Double],_ r:Double) {
  let first=max(0,Int(floor(f.arcPos))-9),last=min(22,Int(floor(f.arcPos)));if first>last { return }
  for j in first...last {
   let progress=M.clamp(f.arcPos-Double(j));if progress<=0 { continue };let alpha=M.mix(0.35,0.92,1-M.clamp((f.arcPos-Double(j+1))*2.5)),end=progress*40,kEnd=Int(ceil(end));var points:[CGPoint]=[],vis:[Double]=[]
   for k in 0...kEnd { var p=art.arcs[j][k];if Double(k)==Double(kEnd) && Double(kEnd)>end { let fr=end-Double(k-1);p=(0..<3).map { M.mix(art.arcs[j][k-1][$0],p[$0],fr) } };let q=M.project(rows,p,r);points.append(CGPoint(x:q.x,y:q.y));vis.append(q.vis*M.smoothstep(0.25,0.48,q.z/sqrt(M.dot(p,p)))) }
   var run=Path(),runA = -1.0,open=false,first=true
   func flush(_ ctx:inout GraphicsContext) { if open { ctx.stroke(run,with:.color(.white.opacity(alpha*runA)),style:StrokeStyle(lineWidth:3,lineCap:first ? .round : .butt,lineJoin:.round));first=false } }
   for k in 0..<kEnd { let a=min(vis[k],vis[k+1]),qa=a<=0.001 ? 0 : floor(a*20+0.5)/20;if open && qa != runA { flush(&c);run=Path();open=false };if qa<=0 { continue };if !open { run.move(to:points[k]);open=true;runA=qa };run.addLine(to:points[k+1]) };flush(&c)
   if progress>=0.6 && vis[22]>0.001 && vis[23]>0.001 { let dx=points[23].x-points[21].x,dy=points[23].y-points[21].y,l=max(hypot(dx,dy),1e-9),ux=dx/l,uy=dy/l,h=points[22];c.fill(path([CGPoint(x:h.x+ux*5,y:h.y+uy*5),CGPoint(x:h.x-ux*5-uy*4.5,y:h.y-uy*5+ux*4.5),CGPoint(x:h.x-ux*2.6,y:h.y-uy*2.6),CGPoint(x:h.x-ux*5+uy*4.5,y:h.y-uy*5-ux*4.5)],true),with:.color(.white.opacity(alpha*vis[22]))) }
   if f.comet>0.5 && progress>0 && progress<1 && vis[kEnd]>0.001 {
    let head=points[kEnd];var tail=Path(),length=0.0,endPoint=head;tail.move(to:head)
    if kEnd>0 { for k in stride(from:kEnd-1,through:0,by:-1) { if vis[k]<=0.001 || length>=60 { break };let dx=points[k].x-endPoint.x,dy=points[k].y-endPoint.y,l=hypot(dx,dy),ratio=min(1,(60-length)/max(l,1e-9));endPoint.x+=dx*ratio;endPoint.y+=dy*ratio;length+=l*ratio;tail.addLine(to:endPoint) } }
    let a=progress<0.06 ? progress/0.06 : 1
    if length>2 { c.drawLayer { glow in glow.opacity=a;glow.addFilter(.blur(radius:2.2));glow.stroke(tail,with:.linearGradient(Gradient(colors:[.white.opacity(0.85),A.color(0xBED7FF,0)]),startPoint:head,endPoint:endPoint),style:StrokeStyle(lineWidth:6,lineCap:.round)) } }
    c.fill(Path(ellipseIn:CGRect(x:head.x-11,y:head.y-11,width:22,height:22)),with:.radialGradient(Gradient(colors:[.white,A.color(0xCDE2FF,0.55),A.color(0x96BEFF,0)]),center:head,startRadius:0,endRadius:11));circle(&c,head.x,head.y,3.4,0xFFFFFF,a)
   }
  }
 }
}
