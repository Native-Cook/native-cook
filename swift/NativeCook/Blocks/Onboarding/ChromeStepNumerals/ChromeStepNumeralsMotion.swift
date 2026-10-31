import Foundation

enum ChromeStepNumeralsMotion {
  static let loop = 9.0
  static let steps = 3
  static func clamp01(_ v: Double) -> Double { min(1, max(0, v)) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t-a)/(b-a)) }
  static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2, 3)/2 }
  static func easeOut(_ x: Double) -> Double { 1-pow(1-x, 3) }
  static func yawOf(_ r: Double) -> Double { 0.3 + 2 * .pi * r }
  enum T {
    static let next = 1.2
    static let go1 = (1.3, 2.5), drag = (3.4, 4.4), more = (4.9, 5.4)
    static let settle = (5.4, 5.9), back = (7.0, 8.6)
  }
  static func rAt(_ t: Double) -> Double {
    if t < T.go1.0 { return 0 }
    if t < T.drag.0 { return easeInOut(seg(t, T.go1.0, T.go1.1)) }
    if t < T.more.0 { return 1 + 0.5 * easeInOut(seg(t, T.drag.0, T.drag.1)) }
    if t < T.settle.0 { return 1.5 + 0.3 * easeInOut(seg(t, T.more.0, T.more.1)) }
    if t < T.back.0 { return 1.8 + 0.2 * easeOut(seg(t, T.settle.0, T.settle.1)) }
    return 2 - 2 * easeInOut(seg(t, T.back.0, T.back.1))
  }
  struct Frame: Equatable {
    var r = 0.0, bob = 0.0, press = 0.0, finger = 0.0, fingerX = 0.0
    var fingerAt = 1
  }
  static func frameAt(_ t: Double) -> Frame {
    let r = rAt(t)
    func tap(_ a: Double) -> Double { easeOut(seg(t, a-0.08, a)) * (1-easeOut(seg(t, a+0.06, a+0.2))) }
    func near(_ a: Double, _ b: Double) -> Double { easeOut(seg(t, a-0.25, a)) * (1-easeOut(seg(t, b, b+0.2))) }
    let onNext = near(T.next-0.05, T.next+0.15)
    let onDrag = near(T.drag.0, T.more.1), onBack = near(T.back.0, T.back.1)
    return Frame(r: r, bob: 0.04*sin(2 * .pi * 2*t/loop), press: tap(T.next),
      finger: clamp01(onNext+onDrag+onBack),
      fingerX: onBack > 0.001 ? 0.2+0.6*easeInOut(seg(t,T.back.0,T.back.1)) : 0.75-(r-1)*0.6,
      fingerAt: onNext > 0.001 ? 0 : 1)
  }
  // The timing animation uses RN's cubic-bezier(0.77, 0, 0.175, 1), not the loop's cubic.
  static func timing(_ x: Double) -> Double {
    func curve(_ t: Double, _ a: Double, _ b: Double) -> Double {
      3*(1-t)*(1-t)*t*a + 3*(1-t)*t*t*b + t*t*t
    }
    var lo = 0.0, hi = 1.0
    for _ in 0..<40 {
      let m = (lo+hi)/2
      if curve(m,0.77,0.175) < clamp01(x) { lo=m } else { hi=m }
    }
    return curve((lo+hi)/2,0,1)
  }
  static func rubber(_ v: Double) -> Double {
    if v < 0 { return -0.1*(1-exp(v/0.1)) }
    if v > 2 { return 2+0.1*(1-exp(-(v-2)/0.1)) }
    return v
  }
  struct Move {
    var start: Double, from: Double, to: Double, velocity: Double, duration: Double
    var omega = 0.0
    init(start: Double, from: Double, to: Double, velocity: Double, reduced: Bool) {
      self.start=start; self.from=from; self.to=to
      self.velocity = (to-from)*velocity < 0 ? 0 : velocity
      duration = reduced ? 0.3 : velocity == 0 ? 1.1+0.3*abs(to-from) : 0.9
      if !reduced && velocity != 0 {
        // Reanimated's duration spring: mass 4, dampingRatio .9, energyThreshold 6e-9.
        let x=from-to, v=self.velocity
        func diff(_ stiffness: Double) -> Double {
          let w=sqrt(stiffness/4)*0.9, t=1.35, envelope=exp(-w*t)
          let xt=(x+(v+x*w)*t)*envelope
          let vt = -w*xt+(v+x*w)*envelope
          return (stiffness*xt*xt+4*vt*vt)/max(1e-300,stiffness*x*x+4*v*v)-6e-9
        }
        var lo=Double.ulpOfOne, hi=8000.0
        let direction=diff(hi) >= diff(lo) ? 1.0 : -1.0
        for _ in 0..<100 {
          let m=(lo+hi)/2, d=diff(m)
          if abs(d) <= 6e-12 { lo=m; hi=m; break }
          if d*direction < 0 { lo=m } else { hi=m }
        }
        omega=sqrt(((lo+hi)/2)/4)
      }
    }
    func sample(_ now: Double) -> (r: Double, done: Bool) {
      let t=max(0,now-start)
      if omega == 0 {
        return (from+(to-from)*ChromeStepNumeralsMotion.timing(t/duration),t >= duration)
      }
      let x=from-to, wd=omega*sqrt(1-0.9*0.9), e=exp(-0.9*omega*t)
      let a=(velocity+0.9*omega*x)/wd
      let displacement=e*(x*cos(wd*t)+a*sin(wd*t))
      let v = -0.9*omega*displacement+e*(-x*wd*sin(wd*t)+a*wd*cos(wd*t))
      let energy=(omega*omega*displacement*displacement+v*v)/max(1e-300,omega*omega*x*x+velocity*velocity)
      return (to+displacement,energy <= 6e-9)
    }
  }
}
