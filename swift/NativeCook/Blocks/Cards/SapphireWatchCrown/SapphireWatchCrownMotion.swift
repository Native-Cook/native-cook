import Foundation

/// Closed-form worklet timeline from swc-motion.ts; the shader normalizes hours, not the script.
enum SapphireWatchCrownMotion {
  static let loop = 10.0
  static let restHours = 10.0 + 8.0 / 60
  static func clamp(_ v: Double) -> Double { min(1, max(0, v)) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t-a)/(b-a)) }
  static func out(_ x: Double) -> Double { 1-pow(1-x, 3) }
  static func inOut(_ x: Double) -> Double { x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2, 3)/2 }
  static func modulo(_ x: Double, _ p: Double) -> Double { (x.truncatingRemainder(dividingBy: p)+p).truncatingRemainder(dividingBy: p) }
  static func bezier(_ x: Double, _ x1: Double = 0.77, _ y1: Double = 0, _ x2: Double = 0.175, _ y2: Double = 1) -> Double {
    func at(_ p: Double, _ a: Double, _ b: Double) -> Double { 3*(1-p)*(1-p)*p*a+3*(1-p)*p*p*b+p*p*p }
    var lo = 0.0, hi = 1.0
    for _ in 0..<24 { let p = (lo+hi)/2; if at(p,x1,x2) < x { lo=p } else { hi=p } }
    return at((lo+hi)/2,y1,y2)
  }
  struct Frame {
    var hours = SapphireWatchCrownMotion.restHours
    var crown = 0.0, flip = 0.0
    var metalFrom = 1, metalTo = 1
    var metalK = 0.0, finger = 0.0
    var fingerAt = 0
    var fingerU = 0.0
    var swatch = 1
    var press = 0.0
    var selectedMetal: Int { metalK > 0.5 ? metalTo : metalFrom }
  }
  static func frameAt(_ t: Double) -> Frame {
    var f = Frame()
    let w = inOut(seg(t,0.7,2.4))
    f.hours = restHours+12*w; f.crown = w*12*Double.pi
    f.flip = inOut(seg(t,3.1,3.9))*(1-inOut(seg(t,6.4,7.2)))
    if t >= 7.8 && t < 9.2 { f.metalFrom=1; f.metalTo=2; f.metalK=inOut(seg(t,7.8,8.4)) }
    else if t >= 9.2 { f.metalFrom=2; f.metalTo=1; f.metalK=inOut(seg(t,9.2,9.8)) }
    f.swatch = f.selectedMetal
    let f0 = out(seg(t,0.5,0.66))*(1-out(seg(t,2.4,2.56)))
    if f0 > f.finger { f.finger=f0; f.fingerU=w; f.press=f0 }
    let f1 = out(seg(t,3.1-0.22,3.1-0.05))*(1-out(seg(t,3.1+0.2,3.1+0.36)))
    if f1 > f.finger { f.finger=f1; f.fingerAt=1; f.fingerU=seg(t,3.0,3.35); f.press=f1 }
    let f3 = out(seg(t,6.4-0.22,6.4-0.05))*(1-out(seg(t,6.4+0.2,6.4+0.36)))
    if f3 > f.finger { f.finger=f3; f.fingerAt=1; f.fingerU=seg(t,6.3,6.65); f.press=f3 }
    let g = out(seg(t,7.8-0.2,7.8-0.04))*(1-out(seg(t,7.8+0.1,7.8+0.26)))
    if g > f.finger { f.finger=g; f.fingerAt=2; f.swatch=2; f.press=g }
    let r = out(seg(t,9.2-0.2,9.2-0.04))*(1-out(seg(t,9.2+0.1,9.2+0.26)))
    if r > f.finger { f.finger=r; f.fingerAt=3; f.press=r }
    return f
  }
}
