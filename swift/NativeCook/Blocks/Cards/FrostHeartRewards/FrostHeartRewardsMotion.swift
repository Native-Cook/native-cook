import Foundation

/// fhr-motion.ts, value for value: every pose of the autoplay loop is a pure function of seconds.
enum FrostHeartRewardsMotion {
  static let LOOP = 10.0
  /// Only 25 and 50 are claimed by the demonstration; the other three remain touchable.
  static let CLAIMS: [Double] = [1000, 1000, 1000, 0.6, 0.95]
  static let VALUES: [Double] = [10, 15, 20, 25, 50]
  static let HEART = (x: 175.0, y: 278.0)

  static func clamp(_ x: Double, _ a: Double = 0, _ b: Double = 1) -> Double { max(a, min(b, x)) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
  /// Invert Bezier x with bounded bisection (12 steps, as in React Native).
  static func bezier(_ xIn: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
    let x = clamp(xIn)
    if x == 0 || x == 1 { return x }
    var lo = 0.0, hi = 1.0, u = x
    for _ in 0..<12 {
      let v = 1 - u
      let bx = 3 * v * v * u * x1 + 3 * v * u * u * x2 + u * u * u
      if bx < x { lo = u } else { hi = u }
      u = (lo + hi) * 0.5
    }
    return 3 * (1 - u) * (1 - u) * u * y1 + 3 * (1 - u) * u * u * y2 + u * u * u
  }
  static func ease(_ x: Double) -> Double { bezier(x, 0.77, 0, 0.175, 1) }
  static func out(_ x: Double) -> Double { bezier(x, 0.23, 1, 0.32, 1) }
  static func hash(_ i: Double) -> Double { let n = sin(i * 127.1 + 311.7) * 43758.5453; return n - floor(n) }
  /// 0.82 damping ratio: a small, single magnetic overshoot, followed by a clean lock.
  static func magnetic(_ age: Double) -> Double {
    if age <= 0 { return 1 }
    return exp(-11.5 * age) * (cos(8 * age) + 11.5 / 8 * sin(8 * age))
  }
  /// Easing.out(Easing.cubic) used by the breath's withTiming.
  static func cubicOut(_ x: Double) -> Double { let u = 1 - clamp(x); return 1 - u * u * u }

  struct Scene {
    var t = 0.0, charge = 0.0, broken = 0.0, gem = 0.0, hue = 0.0, flip = 0.0, pulse = 0.0
    var reset = 0.0, balance = 1650.0, balanceFrom = 1650.0, balanceTo = 1650.0, numberFade = 1.0
    var remaining = 65.0, level = 3, wink = 0.0, spotlight = 0.0, push = 0.0, cut = 0.0
    var display = 0.0, polish = 0.0, polishX = 0.0, breath = 0.0, breathX = 133.0
    var breathY = 258.0, core = 0.0
  }

  static func sceneAt(_ seconds: Double) -> Scene {
    let loop = (seconds.truncatingRemainder(dividingBy: LOOP) + LOOP).truncatingRemainder(dividingBy: LOOP)
    let t = min(loop, 8.9)
    var balance = 1650.0
    for i in 3..<5 { balance += VALUES[i] * out(seg(t - CLAIMS[i] - 0.65, 0, 0.1)) }
    let cut = ease(seg(t, 2.4, 3.6))
    let display = t < 5.4 ? out(seg(t, 3.9, 5.05)) : magnetic(t - 5.4)
    var s = Scene()
    s.t = t
    s.charge = clamp((balance - 1650) / 75)
    s.broken = t >= 3.9 && t < 6.4 ? max(0, display) : 0
    s.gem = cut
    s.hue = ease(seg(t, 6.4, 7.4))
    s.flip = ease(seg(t, 6.65, 7.25))
    s.reset = ease(seg(loop, 8.9, 10))
    s.balance = balance
    s.balanceFrom = balance
    s.balanceTo = balance
    s.remaining = max(0, 65 - (balance - 1650))
    s.level = t >= 6.95 ? 4 : 3
    s.spotlight = ease(seg(t, 1.7, 2.4))
    s.cut = cut
    s.display = t >= 3.9 && t < 6.4 ? display : 0
    s.breath = ease(seg(t, 1.75, 1.98)) * (1 - ease(seg(t, 2.06, 2.3)))
    var core = 0.0
    for i in 0..<12 { core += out(seg(t, 2.48 + Double(i) * 0.105, 2.60 + Double(i) * 0.105)) }
    s.core = core / 12
    return s
  }
}
