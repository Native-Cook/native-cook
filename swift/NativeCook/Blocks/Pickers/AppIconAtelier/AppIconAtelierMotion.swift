import Foundation

enum AppIconAtelierMotion {
  static let loop = 10.0, end = 1.45, tau = Double.pi*2, phaseSpeed = Double.pi*6/10
  static func clamp(_ v: Double) -> Double { min(1,max(0,v)) }
  static func seg(_ t: Double,_ a: Double,_ b: Double) -> Double { clamp((t-a)/(b-a)) }
  static func ease(_ x: Double) -> Double { x<0.5 ? 4*x*x*x : 1-pow(-2*x+2,3)/2 }
  static func out(_ x: Double) -> Double { 1-pow(1-x,3) }
  static func fling(_ x: Double) -> Double { x*x*(2-x) }
  static func arrive(_ x: Double) -> Double {
    if x<=0 { return 0 }; if x>=1 { return 1 }
    return 1-exp(-5.2*x)*cos(6.4*x)*(1-x)
  }
  static func progress(_ f: Double,_ d: Double) -> Double { clamp(d>0 ? (end-f)/(2*end) : (f+end)/(2*end)) }
  static func meniscus(_ f: Double) -> Double { 1-clamp((abs(f)-1)/0.4) }
  static func tap(_ t: Double,_ at: Double) -> Double { out(seg(t,at-0.02,at+0.08))*(1-out(seg(t,at+0.12,at+0.26))) }
  struct Frame {
    var a=0, b=1
    var dir=1.0, front=1.45, lean=0.0, bob=0.0, press=0.0, peek=0.0, land=0.0
    var finger=0.0, fingerAt=0, fingerX=0.0, fingerPress=0.0
    var phase=0.0, applied=0, pop=0.0
    var shown: Int { progress(front,dir)>0.5 ? b : a }
    var sel: Double { Double(a)+Double(b-a)*ease(progress(front,dir)) }
  }
  static func at(_ t: Double) -> Frame {
    var f=Frame()
    if t<3.11 {
      let fx=0.85-1.7*fling(seg(t,0.95,2))
      f.front=t<2 ? end+(fx-end)*out(seg(t,0.95,1.15)) : -0.85-(end-0.85)*out(seg(t,2,3.11))
      f.lean = -0.16*sin(.pi*seg(t,0.95,2))+0.05*sin(.pi*seg(t,2,2.8))
      f.finger=out(seg(t,0.8,0.95))*(1-out(seg(t,2,2.2))); f.fingerX=fx
      f.press=out(seg(t,0.9,1.05))*(1-out(seg(t,1.95,2.15))); f.fingerPress=f.press
    } else if t<4.35 {
      f.a=1; f.b=3; f.front=end-2*end*ease(seg(t,3.45,4.35))
      f.finger=out(seg(t,3.2,3.33))*(1-out(seg(t,3.49,3.65)))
      f.fingerAt=1; f.fingerX=3; f.fingerPress=tap(t,3.35)
    } else if t<8.05 {
      f.a=3; f.b=3
      f.press=out(seg(t,4.95,5.25))*(1-out(seg(t,5.3,5.55)))
      f.peek=arrive(seg(t,5.3,5.95))*(1-arrive(seg(t,6.95,7.65)))
      f.land=0.07*sin(.pi*seg(t,5.83,6.23))*(1-seg(t,5.83,6.23))
      f.land += 0.05*sin(.pi*seg(t,7.51,7.91))*(1-seg(t,7.51,7.91))
      f.finger=out(seg(t,4.8,4.95))*(1-out(seg(t,6.9,7.06)))
      f.fingerPress=out(seg(t,4.95,5.07))*(1-out(seg(t,6.9,7.02)))
    } else {
      f.a=3; f.b=0; f.dir = -1; f.front = -end+2*end*ease(seg(t,8.35,9.25))
      if t>=9.25 { f.a=0; f.b=1; f.dir=1; f.front=end }
      f.finger=out(seg(t,8.1,8.23))*(1-out(seg(t,8.39,8.55)))
      f.fingerAt=1; f.fingerX=0; f.fingerPress=tap(t,8.25)
    }
    f.bob=0.025*sin(tau*t/loop)*(1-f.peek)
    f.lean += 0.04*sin(2*tau*t/loop)
    f.phase=(phaseSpeed*t).truncatingRemainder(dividingBy:tau)
    return f
  }
  // Reanimated's cubic-bezier timing solver (not the cubic autoplay ease).
  static func bezier(_ x: Double,_ x1: Double,_ y1: Double,_ x2: Double,_ y2: Double) -> Double {
    func value(_ t: Double,_ a: Double,_ b: Double) -> Double { 3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*b+t*t*t }
    var lo=0.0, hi=1.0
    for _ in 0..<24 { let mid=(lo+hi)/2; if value(mid,x1,x2)<x { lo=mid } else { hi=mid } }
    return value((lo+hi)/2,y1,y2)
  }
}
