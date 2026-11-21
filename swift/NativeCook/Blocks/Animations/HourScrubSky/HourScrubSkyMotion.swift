import Foundation

enum HourScrubSkyMotion {
 static let loop=12.0
 static func clamp(_ x: Double)->Double { min(max(x,0),1) }
 static func seg(_ t: Double,_ a: Double,_ b: Double)->Double { clamp((t-a)/(b-a)) }
 static func mix(_ a: Double,_ b: Double,_ k: Double)->Double { a+(b-a)*k }
 static func smooth(_ a: Double,_ b: Double,_ x: Double)->Double { let k=seg(x,a,b);return k*k*(3-2*k) }
 static func out(_ k: Double)->Double { 1-pow(1-k,3) }
 static func inside(_ k: Double)->Double { k*k*k }
 static func inOut(_ k: Double)->Double { k<0.5 ? 4*k*k*k : 1-pow(-2*k+2,3)/2 }
 static func spring(_ t: Double,_ from: Double,_ to: Double,_ v: Double,_ d: Double,_ k: Double)->Double {
  if t<=0 { return from };let x=from-to,w=sqrt(k),z=d/(2*w)
  if z<1 { let wd=w*sqrt(1-z*z);return to+exp(-z*w*t)*(x*cos(wd*t)+(v+z*w*x)/wd*sin(wd*t)) }
  let s=sqrt(max(z*z-1,1e-6)),r1 = -w*(z-s),r2 = -w*(z+s),c2=(v-r1*x)/(r2-r1);return to+(x-c2)*exp(r1*t)+c2*exp(r2*t)
 }
 static let knots=[[0.3,0,0],[0.6,0.4,2.6],[2.4,5,2.4],[3,5.75,1.2],[4,6.5,0.7],[4.6,7.25,2],[5.55,12.25,6.5],[6.6,17.25,1.4],[8.3,19,1],[8.7,19.8,2.9],[10.1,23.6,2.9],[10.4,24,0]]
 static func hour(_ t: Double)->(h:Double,rate:Double) {
  if t<=0.3 { return (0,0) };if t>=10.4 { return (24,0) };var i=0;while t>knots[i+1][0] { i+=1 }
  let a=knots[i],b=knots[i+1],dt=b[0]-a[0],s=(t-a[0])/dt,s2=s*s,s3=s2*s
  let h=(2*s3-3*s2+1)*a[1]+(s3-2*s2+s)*dt*a[2]+(-2*s3+3*s2)*b[1]+(s3-s2)*dt*b[2]
  let v=((6*s2-6*s)*a[1]+(3*s2-4*s+1)*dt*a[2]+(-6*s2+6*s)*b[1]+(3*s2-2*s)*dt*b[2])/dt
  return (h,v)
 }
 static func timeForHour(_ h: Double)->Double { if h<=0 { return 0.2 };var a=0.3,b=10.4;for _ in 0..<30 { let m=(a+b)/2;if hour(m).h<h { a=m } else { b=m } };return (a+b)/2 }
 static func frame(_ input: Double)->HourScrubSkyFrame {
  let t=(input.truncatingRemainder(dividingBy:12)+12).truncatingRemainder(dividingBy:12),hr=hour(t)
  let hp=t<11.2 ? hr.h : t<11.8 ? max(0,spring(t-11.2,24,0,0,22.1,157.9)) : 0
  let cs=t<0.3 ? 0 : min(t-0.3,0.5),rs=t<10.6 ? 0 : min(t-10.6,0.8)
  var pill=0.0,disc=1.0
  if t>=0.3 && t<11.2 { pill=spring(t-0.3,0,1,0,19,195) } else if t>=11.2 && t<11.8 { pill=max(0,spring(t-11.2,1,0,0,22.1,157.9)) }
  let dark=t<11.2 ? seg(t,0.3,0.46) : 1-seg(t,11.2,11.36)
  if t>=0.2 && t<0.3 { disc=mix(1,0.9,inside(seg(t,0.2,0.3))) } else if t>=0.3 && t<1.2 { disc=spring(t-0.3,0.9,1,0,19,195) } else if t>=10.4 && t<10.7 { disc=1+0.06*sin(.pi*seg(t,10.4,10.7)) }
  let play=t<0.2 ? 1 : t<0.3 ? 1-inside(seg(t,0.2,0.3)) : t<11.3 ? 0 : clamp(spring(t-11.3,0,1,0,19,195))
  let pause=t<0.3 ? 0 : t<10.4 ? clamp(spring(t-0.3,0,1,0,19,195)) : t<10.5 ? 1-inside(seg(t,10.4,10.5)) : 0
  let reset=t<10.4 ? 0 : t<11.2 ? clamp(spring(t-10.4,0,1,0,30.5,322.3)) : t<11.3 ? 1-inside(seg(t,11.2,11.3)) : 0
  let rot=t<10.4 ? -90 : t<11.3 ? spring(t-10.4,-90,0,0,30.5,322.3) : 0
  func pulse(_ a:Double)->Double { t>=a && t<a+0.36 ? sin(.pi*seg(t,a,a+0.36)) : 0 }
  return HourScrubSkyFrame(h:hr.h,rate:hr.rate,hp:hp,cs:cs,rs:rs,pill:pill,pillDark:dark,disc:disc,gPlay:play,gPause:pause,gReset:reset,resetRot:rot,flare:max(pulse(1.4),pulse(9.45)),sway:t)
 }
 static func sun(_ h: Double)->(x:Double,y:Double) { let a=(h-0.5)/12 * .pi;return (201+135*sin(a),540-450*cos(a)) }
 static func front(_ h: Double)->Double {
  if h<5.5 { return -210 };if h<6.5 { let s=h-5.5,s2=s*s,s3=s2*s;return (2*s3-3*s2+1)*(-210)+(s3-2*s2+s)*1300+(-2*s3+3*s2)*536+(s3-s2)*250 }
  if h<7 { return 536+250*(h-6.5) };if h<7.25 { return 661+960*(h-7) };if h<17.25 { return 1400 };if h<19 { return mix(1100,-210,(h-17.25)/1.75) };return -210
 }
 static let temps=[[0.0,29],[3,30],[6.6,28],[7.4,27],[9,26],[12,24],[15,22],[18,22],[20,24],[22,26],[24,29]]
 static func temp(_ h:Double)->Double { let h=min(max(h,0),24);var i=0;while i<temps.count-2 && h>temps[i+1][0] { i+=1 };let a=temps[i],b=temps[i+1];return mix(a[1],b[1],seg(h,a[0],b[0])) }
 static let conditions=[[0.0,0],[3.25,1],[5.25,0],[9.25,1],[14.25,0],[17.25,2],[19.25,0]]
 static func conditionRoll(_ h:Double)->[Double] { var out=[9.0,9,9],cur=0;for i in 1..<conditions.count { if h>=conditions[i][0]-0.15 { cur=Int(conditions[i][1]) } };for i in 1..<conditions.count { let hk=conditions[i][0];if abs(h-hk)<0.15 { let k=inOut(seg(h,hk-0.15,hk+0.15));out[Int(conditions[i-1][1])] = -k;out[Int(conditions[i][1])]=1-k;return out } };out[cur]=0;return out }
 static func pillLabel(_ i:Int)->String { let clock=(12+i)%24,hr=clock%12==0 ? 12 : clock%12;return (12+i>=24 ? "Tom " : "")+String(hr)+(clock<12 ? " AM" : " PM") }
}
