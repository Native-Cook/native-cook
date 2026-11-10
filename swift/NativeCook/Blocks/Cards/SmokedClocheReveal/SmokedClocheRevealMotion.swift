import CoreGraphics

enum SmokedClocheRevealMotion {
  static let loop = 10.0
  static let seal = 0.14
  static let landAt = 0.72, landHop = 0.03
  static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t-a)/(b-a)) }
  static func out(_ x: Double) -> Double { 1-pow(1-x, 3) }
  static func io(_ x: Double) -> Double { x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2,3)/2 }
  static func setDown(_ p: Double) -> Double {
    if p <= 0 { return 1 }
    if p >= 1 { return 0 }
    if p < landAt { return 1-io(p/landAt) }
    return landHop*sin(Double.pi*(p-landAt)/(1-landAt))
  }
  static func bezier(_ x: Double, _ x1: Double = 0.77, _ y1: Double = 0, _ x2: Double = 0.175, _ y2: Double = 1) -> Double {
    func b(_ t: Double, _ a: Double, _ c: Double) -> Double { 3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*c+t*t*t }
    var lo=0.0, hi=1.0
    for _ in 0..<24 { let m=(lo+hi)/2; if b(m,x1,x2)<x { lo=m } else { hi=m } }
    return b((lo+hi)/2,y1,y2)
  }
  struct Frame {
    var lift=0.0, cart=0.0, mode=0.0, finger=0.0, fingerAt=0, fingerU=0.0, press=0.0
    var time=0.0, dt=0.0, fx = -999.0, fy = -999.0, dx=0.0, dy=0.0, down=0.0
    var demoPoint: CGPoint?
    var reduced=false
    var pinned: Double?
  }
  static func at(_ t: Double) -> Frame {
    var lift=0.55*io(seg(t,0.6,1.55))
    if t >= 1.55 { lift=0.55+0.45*pow(seg(t,1.55,2.05),3) }
    lift *= setDown(seg(t,8.1,8.9))
    let cart=io(seg(t,6.2,6.9))*(1-out(seg(t,7.4,8)))
    var f=Frame(lift:lift,cart:cart,mode:t < 0.85 || t >= 8.7 ? (t >= 8.7 ? 2 : 0) : 1,time:t)
    let knob=out(seg(t,0.35,0.5))*(1-out(seg(t,1.55,1.71)))
    if knob > f.finger { f.finger=knob;f.fingerAt=0;f.fingerU=io(seg(t,0.6,1.55));f.press=knob }
    let stir=out(seg(t,2.12,2.28))*(1-out(seg(t,3.3,3.46)))
    if stir > f.finger { f.finger=stir;f.fingerAt=1;f.fingerU=seg(t,2.3,3.3);f.press=stir }
    let c=out(seg(t,6,6.16))*(1-out(seg(t,6.34,6.5)))
    if c > f.finger {
      f.finger=c;f.fingerAt=2
      f.press=out(seg(t,6.16,6.25))*(1-out(seg(t,6.3,6.42)))
    }
    return f
  }
  static func fingerPoint(_ f: Frame, _ l: SmokedClocheRevealLayout) -> CGPoint {
    if f.fingerAt == 0 { return CGPoint(x:l.cx,y:l.cy-220*min(f.lift,0.55)*1.6) }
    if f.fingerAt == 1 { return CGPoint(x:l.cx+(f.fingerU-0.5)*260,y:l.cy+sin(f.fingerU*Double.pi*2)*50) }
    return CGPoint(x:l.ctaX,y:l.ctaY)
  }
}
