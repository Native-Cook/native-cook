import Foundation

enum JellyScaleExchangeMotion {
  static let loop = 10.0
  static let beans = 16.0
  static let pourRate = 6.0
  static let pointsPerBean = 5
  static let tilt = 0.55
  static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t-a)/(b-a)) }
  static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
    let u = seg(x,a,b); return u*u*(3-2*u)
  }
  static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2,3)/2 }
  static func easeOut(_ x: Double) -> Double { 1-pow(1-x,3) }
  static func wobbleAt(_ u: Double) -> Double { u <= 0 || u >= 1 ? 0 : exp(-u*5)*sin(u*26) }
  static func sinkOf(_ pour: Double, _ coin: Double) -> Double {
    let land = pour-floor(pour)
    let nudge = pour > 0.5 ? 0.012*exp(-land*9)*sin(land*30) : 0
    return 0.03*min(pour,beans)/beans*(1-smooth(0.3,0.8,coin))+nudge*(1-coin)
  }
  struct Frame {
    var pour = 0.0, melt = 0.0, coin = 0.0, spin = 0.0, fly = 0.0
    var wobbleU = 1.0, plus = 1.0
    var pressPour = 0.0, pressSwap = 0.0, finger = 0.0, fingerAt = 0.0
  }
  static func frameAt(_ t: Double) -> Frame {
    let gone = t >= 7.65
    func press(_ a: Double, _ b: Double) -> Double { easeOut(seg(t,a,a+0.1))*(1-easeOut(seg(t,b,b+0.15))) }
    func tap(_ a: Double) -> Double { easeOut(seg(t,a-0.08,a))*(1-easeOut(seg(t,a+0.06,a+0.2))) }
    func near(_ a: Double, _ b: Double) -> Double { easeOut(seg(t,a-0.25,a))*(1-easeOut(seg(t,b,b+0.2))) }
    let onPour = near(0.8,3.65), onSwap = near(4.25,4.45)
    return Frame(pour: gone ? 0 : beans*seg(t,0.9,0.9+beans/pourRate),
      melt: gone ? 0 : easeInOut(seg(t,4.4,5.5)), coin: gone ? 0 : easeInOut(seg(t,5.5,6.3)),
      spin: gone ? 0 : 2 * .pi * easeInOut(seg(t,5.8,6.8)), fly: gone ? 0 : easeInOut(seg(t,6.9,7.6)),
      wobbleU: t < 7.6 ? 1 : seg(t,7.6,8.3), plus: t < 7.6 ? 1 : seg(t,7.6,8.5),
      pressPour: press(0.8,3.65), pressSwap: tap(4.3), finger: clamp01(onPour+onSwap), fingerAt: onPour > 0.001 ? 0 : 1)
  }
  // Reanimated's interactive exchange uses cubic Beziers, unlike the scripted cubic loop.
  static func bezier(_ x: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
    func curve(_ t: Double, _ a: Double, _ b: Double) -> Double { 3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*b+t*t*t }
    var lo = 0.0, hi = 1.0
    for _ in 0..<24 { let mid = (lo+hi)/2; if curve(mid,x1,x2) < x { lo = mid } else { hi = mid } }
    return curve((lo+hi)/2,y1,y2)
  }
}
