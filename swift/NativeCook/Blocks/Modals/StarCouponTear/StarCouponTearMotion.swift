import Foundation

enum StarCouponTearMotion {
  static let loop = 9.0
  static let pullPT = 150.0
  static let claimAt = 0.6
  struct Frame {
    var pull = 0.0, tear = 0.0, fly = 0.0, pop = 1.0, chip = 1.0, finger = 0.0
  }
  static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
  static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
    let u = seg(x, a, b)
    return u * u * (3 - 2 * u)
  }
  static func easeInOut(_ x: Double) -> Double {
    x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
  }
  static func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
  static func backOut(_ x: Double) -> Double {
    let u = clamp01(x) - 1
    return 1 + 2.6 * u * u * u + 1.6 * u * u
  }
  static func tearOf(_ pull: Double) -> Double { smooth(0.12, 1, pull) }
  static func frameAt(_ t: Double) -> Frame {
    var pull = 0.0
    if t >= 1.1 && t < 2.9 {
      pull = 0.5 * easeInOut(seg(t, 1.1, 2)) + 0.35 * easeInOut(seg(t, 2.4, 2.8))
    }
    let after = t >= 2.9 && t < 4.5 ? 0.85 * (1 - easeOut(seg(t, 2.9, 3.3))) : 0
    let tear =
      t < 2.9
      ? tearOf(pull) : t < 4.5 ? tearOf(0.85) + (1 - tearOf(0.85)) * easeOut(seg(t, 2.9, 3.3)) : 0
    return Frame(
      pull: t < 2.9 ? pull : after, tear: tear,
      fly: t < 4.5 ? easeInOut(seg(t, 3.8, 4.5)) : 0,
      pop: t < 4.5 ? 1 : seg(t, 5.2, 5.8),
      chip: t < 4.5 ? 1 : seg(t, 4.5, 5.3),
      finger: easeOut(seg(t, 0.75, 1)) * (1 - easeOut(seg(t, 2.9, 3.1))))
  }
  // Solve the same cubic Beziers used by Reanimated, including their x coordinates.
  static func bezier(_ x: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double
  {
    let x = clamp01(x)
    func b(_ t: Double, _ a: Double, _ c: Double) -> Double {
      3 * (1 - t) * (1 - t) * t * a + 3 * (1 - t) * t * t * c + t * t * t
    }
    var lo = 0.0
    var hi = 1.0
    for _ in 0..<24 {
      let m = (lo + hi) / 2
      if b(m, x1, x2) < x { lo = m } else { hi = m }
    }
    return b((lo + hi) / 2, y1, y2)
  }
  static func defaultEase(_ x: Double) -> Double {
    let x = clamp01(x)
    return x < 0.5 ? 2 * x * x : 1 - pow(-2 * x + 2, 2) / 2
  }
  // Reanimated 4's duration spring: mass=4, energyThreshold=6e-9.
  // Frequencies are its stiffness bisection results for these four configurations.
  static func spring(_ t: Double, _ duration: Double, _ damping: Double) -> Double {
    if t <= 0 { return 0 }
    let w: Double
    if duration == 0.4 {
      w = damping == 0.6 ? 33.82731306762333 : 25.56132523278576
    } else {
      w = damping == 0.55 ? 32.74919488514183 : 22.720813180044853
    }
    let wd = w * sqrt(1 - damping * damping)
    let envelope = exp(-damping * w * t)
    let displacement = envelope * (cos(wd * t) + damping * w / wd * sin(wd * t))
    let velocity =
      -damping * w * displacement + envelope * (damping * w * cos(wd * t) - wd * sin(wd * t))
    if displacement * displacement + velocity * velocity / (w * w) <= 6e-9 { return 1 }
    return 1 - displacement
  }
}
