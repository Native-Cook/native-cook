import Foundation

// Pure transcription of cct-motion.ts. Times are seconds, not animation durations.
enum ClayCategoryTabsMotion {
  static let loop = 9.0
  static func clamp01(_ v: Double) -> Double { min(1, max(0, v)) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t-a)/(b-a)) }
  static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
    let u = seg(x, a, b); return u*u*(3-2*u)
  }
  static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2,3)/2 }
  static func easeOut(_ x: Double) -> Double { 1-pow(1-x,3) }
  static func landSquash(_ u: Double) -> Double {
    u <= 0 || u >= 0.9 ? 0 : 0.6*exp(-u*8)*sin(u*22)
  }
  static func ringKnob(_ u: Double) -> Double { u <= 0 || u >= 0.3 ? 0 : sin(.pi*u/0.3) }
  static func ringWobble(_ u: Double) -> Double { u <= 0 || u >= 1.2 ? 0 : 0.16*exp(-u*4.5)*sin(u*30) }
  static func rAt(_ t: Double) -> Double {
    if t < 1.05 { return 0 }
    if t < 3.2 { return easeInOut(seg(t,1.05,2.15)) }
    if t < 4.3 { return 1+0.5*easeInOut(seg(t,3.2,3.9)) }
    if t < 4.7 { return 1.5+0.32*easeInOut(seg(t,4.3,4.7)) }
    if t < 6.65 { return 1.82+0.18*easeOut(seg(t,4.7,5.2)) }
    return 2-2*easeInOut(seg(t,6.65,8.15))
  }
  struct Frame {
    var r = 0.0, squash = 0.0, knob = 0.0, wobble = 0.0
    var finger = 0.0, onPage = 0.0, tab = 0, dragX = 0.0, press = 0.0
  }
  static func frameAt(_ t: Double) -> Frame {
    func near(_ a: Double, _ b: Double) -> Double { easeOut(seg(t,a-0.25,a))*(1-easeOut(seg(t,b,b+0.25))) }
    func tapPress(_ a: Double) -> Double { easeOut(seg(t,a-0.08,a))*(1-easeOut(seg(t,a+0.06,a+0.2))) }
    let onTap = near(0.95,1.2)+near(6.55,6.8), onDrag = near(3.2,4.7)
    return Frame(r:rAt(t), squash:landSquash(t-2.15)+landSquash(t-5.2)+landSquash(t-8.15),
      knob:ringKnob(t-5.2), wobble:ringWobble(t-5.2), finger:clamp01(onTap+onDrag),
      onPage:onDrag > 0.001 ? 1 : 0, tab:t < 4 ? 1 : 0, dragX:(rAt(t)-1)/0.82,
      press:clamp01(tapPress(1)+tapPress(6.6)))
  }
  // RN's touch timing curves differ from the cubic autoPlay functions above.
  static func bezier(_ u: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
    let u = clamp01(u)
    func curve(_ t: Double, _ a: Double, _ b: Double) -> Double { 3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*b+t*t*t }
    var lo = 0.0, hi = 1.0
    for _ in 0..<32 { let mid = (lo+hi)/2; if curve(mid,x1,x2) < u { lo = mid } else { hi = mid } }
    return curve((lo+hi)/2,y1,y2)
  }
  static func defaultEase(_ u: Double) -> Double { u < 0.5 ? 2*u*u : 1-pow(-2*u+2,2)/2 }
  static func touchEase(_ u: Double) -> Double { bezier(u,0.77,0,0.175,1) }
  static func touchOut(_ u: Double) -> Double { bezier(u,0.23,1,0.32,1) }
}

// Reanimated 4 duration spring: mass 4, energyThreshold 6e-9, duration × 1.5.
// Solve stiffness by the same bisection, then evaluate the underdamped solution.
struct ClayCategoryTabsSpring {
  let from: Double, to: Double, velocity: Double, duration: Double, damping: Double
  let stiffness: Double
  init(from: Double, to: Double, velocity: Double = 0, duration: Double, damping: Double) {
    let v = (to > from && velocity < 0) || (to < from && velocity > 0) ? 0 : velocity
    self.from = from; self.to = to; self.velocity = v; self.duration = duration; self.damping = damping
    let x = from-to, d = duration*1.5
    func fraction(_ k: Double) -> Double {
      let om = sqrt(k/4)*damping, envelope = exp(-om*d)
      let xt = (x+(v+x*om)*d)*envelope
      let vt = xt*(-om)+(v+x*om)*envelope
      return (k*xt*xt+4*vt*vt)/max(1e-30,k*x*x+4*v*v)-6e-9
    }
    var lo = Double.ulpOfOne, hi = 8000.0
    for _ in 0..<100 {
      let mid = (lo+hi)/2, f = fraction(mid)
      if abs(f) < 6e-12 { break }
      if f > 0 { lo = mid } else { hi = mid }
    }
    stiffness = (lo+hi)/2
  }
  func sample(_ elapsed: Double) -> (value: Double, done: Bool) {
    let t = max(0, elapsed), om = sqrt(stiffness/4), w = om*sqrt(1-damping*damping)
    let x = from-to, a = (velocity+damping*om*x)/w
    let e = exp(-damping*om*t), wave = x*cos(w*t)+a*sin(w*t)
    let displacement = e*wave
    let v = -damping*om*displacement+e*(-x*w*sin(w*t)+a*w*cos(w*t))
    let initial = stiffness*x*x+4*velocity*velocity
    let done = initial == 0 || (stiffness*displacement*displacement+4*v*v)/initial <= 6e-9
    return (done ? to : to+displacement, done)
  }
}
