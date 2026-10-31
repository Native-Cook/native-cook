import Foundation

enum ClayRelightSettingsMotion {
  static let loop = 10.0
  static let themeUp = 0.62, themeDown = 0.54, themeFade = 0.3
  struct Frame {
    var m = 0.0, theme = 0.0, on = 0.0, knob = 0.0, press = 0.0
    var finger = 0.0, fingerAt = 0.0, fingerX = 0.0, grab = 0.0
  }
  static func clamp01(_ v: Double) -> Double { min(1, max(0, v)) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t-a)/(b-a)) }
  static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
    let u = seg(x,a,b); return u*u*(3-2*u)
  }
  static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2,3)/2 }
  static func easeOut(_ x: Double) -> Double { 1-pow(1-x,3) }
  static func settleStep(_ x: Double) -> Double {
    if x <= 0 { return 0 }; if x >= 1 { return 1 }
    return (1-(1+7*x)*exp(-7*x))/(1-8*exp(-7))
  }
  static func evening(_ m: Double) -> Double { let x = clamp01(m); return x+0.13*sin(2 * .pi*x) }
  static func sunAngle(_ m: Double) -> Double { 2.12-3.6*evening(m) }
  static func moonAngle(_ m: Double) -> Double { .pi+0.16-1.18*smooth(0.42,1,evening(m)) }
  static func night(_ m: Double) -> Double { smooth(0.42,0.86,evening(m)) }
  static func dusk(_ m: Double) -> Double {
    let e = evening(m); return smooth(0.12,0.44,e)*(1-smooth(0.5,0.8,e))
  }
  static func lamp(_ m: Double, _ row: Int) -> Double {
    let r = Double(row); return smooth(0.5+0.045*r,0.6+0.045*r,evening(m))
  }
  static func windows(_ m: Double) -> Double { smooth(0.44,0.56,evening(m)) }
  static func mAt(_ t: Double) -> Double {
    if t < 0.9 { return 0 }
    if t < 4.7 { return smooth(0,1,seg(t,0.9,3.9)) }
    if t < 6 { return 1-0.48*easeInOut(seg(t,4.7,5.6)) }
    if t < 7.7 { return 0.52+0.48*settleStep(seg(t,6,7.3)) }
    return 1-smooth(0,1,seg(t,7.7,9.8))
  }
  static let steps: [Double] = {
    var out = [Double](), state = false, t = 0.0
    while t < loop {
      let m = mAt(t)
      if !state && m > themeUp { state = true; out.append(t) }
      else if state && m < themeDown { state = false; out.append(t) }
      t += 0.001
    }
    while out.count < 4 { out.append(loop+10) }
    return out
  }()
  static func themeAt(_ t: Double) -> Double {
    func up(_ a: Double) -> Double { easeInOut(seg(t,a,a+themeFade)) }
    return clamp01(up(steps[0])-up(steps[1])+up(steps[2])-up(steps[3]))
  }
  static func frameAt(_ t: Double) -> Frame {
    func tapPress(_ a: Double) -> Double {
      easeOut(seg(t,a-0.08,a))*(1-easeOut(seg(t,a+0.06,a+0.2)))
    }
    func near(_ a: Double, _ b: Double) -> Double {
      easeOut(seg(t,a-0.25,a))*(1-easeOut(seg(t,b,b+0.25)))
    }
    let onSky = near(4.7,6)
    return Frame(m: mAt(t), theme: themeAt(t), on: t >= 0.9 && t < 7.7 ? 1 : 0,
      knob: easeOut(seg(t,0.9,1.18))*(1-easeOut(seg(t,7.7,7.98))),
      press: clamp01(tapPress(0.9)+tapPress(7.7)),
      finger: clamp01(near(0.85,1.1)+near(7.65,7.9)+onSky),
      fingerAt: onSky > 0.001 ? 1 : 0, fingerX: easeInOut(seg(t,4.7,5.6)))
  }
  static func clockText(_ m: Double) -> String {
    let mins = Int(((880+evening(m)*540)/5).rounded())*5
    let hour = (mins/60)%24, h = hour%12
    return String(format: "%d:%02d %@", h == 0 ? 12 : h, mins%60, hour < 12 ? "AM" : "PM")
  }
  // Reanimated's cubic Bézier curves, inverted on the x axis.
  static func bezier(_ x: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
    func axis(_ t: Double, _ a: Double, _ b: Double) -> Double {
      3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*b+t*t*t
    }
    var lo = 0.0, hi = 1.0
    for _ in 0..<24 {
      let mid = (lo+hi)/2
      if axis(mid,x1,x2) < x { lo = mid } else { hi = mid }
    }
    return axis((lo+hi)/2,y1,y2)
  }
}
