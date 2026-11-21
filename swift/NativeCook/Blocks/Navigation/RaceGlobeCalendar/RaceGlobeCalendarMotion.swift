import Foundation

enum RaceGlobeCalendarMotion {
 private typealias D=RaceGlobeCalendarData
 static let loop=10.5,spinV=1/(0.2+0.5+0.7/3)
 static let autoTrack=[14,15,16,21,8,2,18,10,14]
 static func clamp(_ x:Double)->Double { min(max(x,0),1) }
 static func seg(_ t:Double,_ a:Double,_ b:Double)->Double { clamp((t-a)/(b-a)) }
 static func mix(_ a:Double,_ b:Double,_ k:Double)->Double { a+(b-a)*k }
 static func smooth(_ k:Double)->Double { let x=clamp(k);return x*x*(3-2*x) }
 static func smoothstep(_ a:Double,_ b:Double,_ v:Double)->Double { smooth((v-a)/(b-a)) }
 static func out(_ k:Double)->Double { 1-pow(1-clamp(k),3) }
 static func inOut(_ k:Double)->Double { let x=clamp(k);return x<0.5 ? 4*x*x*x : 1-pow(-2*x+2,3)/2 }
 static func sine(_ k:Double)->Double { (1-cos(.pi*clamp(k)))/2 }
 static func spring(_ t:Double,_ from:Double,_ to:Double,_ v:Double,_ d:Double,_ k:Double)->Double {
  if t<=0 { return from };let x=from-to,w=sqrt(k),z=d/(2*w)
  if z<1 { let wd=w*sqrt(1-z*z);return to+exp(-z*w*t)*(x*cos(wd*t)+(v+z*w*x)/wd*sin(wd*t)) }
  return to+exp(-w*t)*(x+(v+w*x)*t)
 }
 static func dot(_ a:[Double],_ b:[Double])->Double { a[0]*b[0]+a[1]*b[1]+a[2]*b[2] }
 static func norm(_ a:[Double])->[Double] { let l=sqrt(dot(a,a));return a.map { $0/(l==0 ? 1 : l) } }
 static func slerp(_ a:[Double],_ b:[Double],_ k:Double)->[Double] {
  let om=acos(min(max(dot(a,b),-1),1));if om<1e-5 { return a };let ka=sin((1-k)*om)/sin(om),kb=sin(k*om)/sin(om);return (0..<3).map { a[$0]*ka+b[$0]*kb }
 }
 static func angle(_ a:[Double],_ b:[Double])->Double { acos(min(max(dot(a,b),-1),1))*180 / .pi }
 static func lat(_ v:[Double])->Double { asin(min(max(v[1],-1),1))*180 / .pi }
 static func lon(_ v:[Double])->Double { atan2(v[0],v[2])*180 / .pi }
 static func wrap(_ d:Double)->Double { d-360*floor((d+180)/360) }
 static func rows(_ lat:Double,_ lon:Double)->[Double] { let l=lon * .pi/180,b=lat * .pi/180-D.beta,cl=cos(l),sl=sin(l),cb=cos(b),sb=sin(b);return [cl,0,-sl,-sb*sl,cb,-sb*cl,cb*sl,sb,cb*cl] }
 static func project(_ m:[Double],_ p:[Double],_ radius:Double)->(x:Double,y:Double,z:Double,vis:Double) {
  let vx=m[0]*p[0]+m[1]*p[1]+m[2]*p[2],vy=m[3]*p[0]+m[4]*p[1]+m[5]*p[2],vz=m[6]*p[0]+m[7]*p[1]+m[8]*p[2]
  let a=vx*vx+vy*vy+pow(vz-4,2),b=8*(vz-4),disc=b*b-4*a*15
  var vis=1.0;if disc>0 { let t=(-b-sqrt(disc))/(2*a);if t>0 && t<0.9999 { vis=0 } }
  let f=radius*sqrt(15);return (201+f*vx/(4-vz),372-f*vy/(4-vz),vz,vis)
 }
 static func hopDip(_ hop:Double)->Double { smoothstep(20,110,hop) }
 static func zoom(_ e:Double,_ hop:Double)->Double { 1-0.4*sin(.pi*clamp(e))*hopDip(hop) }
 static func landing(_ l:Double)->(drop:Double,scale:Double,ripple:Double,callout:Double,circuit:Double) {
  if l<0 { return (0,0,-1,0,0) };let p=spring(l,0,1,0,19,195),c=l<0.12 ? 0 : spring(l-0.12,0,1,0,19,195);return (-10*(1-p),mix(0.6,1,p),l<0.5 ? l/0.5 : -1,c,out(seg(l,0.22,0.6)))
 }
 static func spinAt(_ tau:Double)->(p:Double,v:Double) {
  let v=spinV;if tau<=0 { return (0,0) };if tau<0.4 { return (v*tau*tau/0.8,v*tau/0.4) };if tau<0.9 { return (v*0.2+v*(tau-0.4),v) };if tau<1.6 { let s=(tau-0.9)/0.7;return (v*0.7+v*0.7*(1-pow(1-s,3))/3,v*pow(1-s,2)) };return (1,0)
 }
 static func base(_ t:Double,_ a:Double,_ b:Double,_ lean:Double)->Double { sine(pow(seg(t,a,b),lean)) }
 static func flight(_ t:Double,_ a:Double,_ b:Double,_ hop:Double,_ lean:Double)->Double { let e=base(t,a,b,lean),s=seg(t,b-0.3,b+0.25);return e+1.4/hop*sin(.pi*s)*(1-s) }
 static func arc(_ t:Double,_ a:Double,_ b:Double,_ lean:Double)->Double { let e=base(t,a,b,lean);return clamp(e+0.14*sin(.pi*e)) }
 static func page(_ t:Double,_ a:Double)->Double { let k=seg(t,a,a+0.5);return t<a+0.5 ? 0.45*k*k : spring(t-a-0.5,0.45,1,1.8,22.1,157.9) }
 static func frame(_ input:Double)->RaceGlobeCalendarFrame {
  let t=(input.truncatingRemainder(dividingBy:loop)+loop).truncatingRemainder(dividingBy:loop),drift=1.2*sin(2 * .pi*3*t/loop)
  var f=RaceGlobeCalendarFrame();f.lat=D.LAT[14];f.lon=D.LON[14];f.sun=D.SUN[14];f.t=t
  if t<4.3 {
   let e=flight(t,0.7,2.75,D.hop1,1.45),c=slerp(D.W[14],D.W[15],e)
   f.lat=lat(c);f.lon=lon(c);f.zoom=zoom(e,D.hop1);f.sun=slerp(D.SUN[14],D.SUN[15],out(seg(t,0.7,2.5)))
   f.cardPos=t<0.7 ? 0 : page(t,0.7);f.arcPos=14+arc(t,0.7,2.75,1.45);f.comet=1;f.selF=14+smooth(seg(t,0.8,2.8));f.from=14;f.to=t<0.7 ? 14 : 15;f.leave=seg(t,0.7,1);f.land=t<0.7 ? 9 : t-2.8;f.calloutOut=t<0.7 ? seg(t,0.6,0.7) : seg(t,4.2,4.3)
   f.press=t<1.2 ? seg(t,0.6,0.7) : 1-seg(t,1.2,1.32);f.press=max(f.press,seg(t,4.2,4.3));f.sheen=t>=2.9 ? inOut(seg(t,2.9,3.6)) : -1;f.sheenSlot=1
  } else if t<7.1 {
   let e=flight(t,4.3,5.6,D.hop2,1.18),c=slerp(D.W[15],D.W[16],e)
   f.lat=lat(c);f.lon=lon(c);f.zoom=zoom(e,D.hop2);f.sun=slerp(D.SUN[15],D.SUN[16],seg(t,4.3,5.6));f.cardPos=1+page(t,4.3);f.arcPos=15+arc(t,4.3,5.6,1.18);f.comet=1;f.selF=15+smooth(seg(t,4.4,5.7));f.from=15;f.to=16;f.leave=seg(t,4.3,4.6);f.land=t-5.7;f.calloutOut=seg(t,7,7.1);f.press=t<4.8 ? 1 : 1-seg(t,4.8,4.92);f.sheen=t>=5.8 ? inOut(seg(t,5.8,6.5)) : -1;f.sheenSlot=2;f.shuffle=seg(t,7,7.06);f.shuffleRot=360*clamp(spring(t-7,0,1,0,30.5,322.3))
  } else {
   let tau=t-7.1,sp=spinAt(tau),sc=spinAt(min(1.6,tau*1.6/1.4))
   f.lat=mix(D.LAT[16],D.LAT[14],smooth(sp.p));f.lon=D.LON[16]+D.spinDLon*sp.p;f.spin=D.spinDLon*sp.v;f.zoom=1-0.3*sp.v/spinV;f.sun=slerp(D.SUN[16],D.SUN[14],sp.p);f.cardPos=2+6*sc.p;f.arcPos=16-2*smooth(seg(t,7.15,7.6));f.comet=0;f.selF=t<7.3 ? 16 : 14;f.numbered=t<8.5 ? 1-seg(t,7.1,7.3) : seg(t,8.5,8.8);f.from=16;f.to=14;f.leave=seg(t,7.05,7.3);f.land=t-8.62;f.shuffle=1-seg(t,7.1,7.25);f.shuffleRot=360*clamp(spring(t-7,0,1,0,30.5,322.3))
  };f.lon+=drift;return f
 }
}
