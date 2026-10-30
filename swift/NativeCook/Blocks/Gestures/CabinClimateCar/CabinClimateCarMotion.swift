import Foundation

// Analytic timeline copied from ccc-motion.ts; no platform animation approximations.
enum CabinClimateCarMotion {
  static let LOOP = 9.0
  static let RESUME = 2.0
  static let ROLL = 0.18
  static let steps: [(Double, Double)] = [
    (1, 69), (1.3, 68), (1.6, 67), (1.9, 66), (3, 67), (3.25, 68), (3.5, 69), (3.75, 70),
    (4, 71), (4.25, 72), (4.5, 73), (4.75, 74), (7.2, 73), (7.45, 72), (7.7, 71), (7.95, 70),
  ]
  struct Frame {
    var amb: Double
    var temp: Double
    var from: Double
    var tempAt: Double
    var heat: Double
    var plusK: Double
    var minusK: Double
    var leg: Double
    var legOn: Bool
    var legAt: Double
    var fan: Int
    var fanAt: Double
    var cardK: Double
    var card: Int
  }
  static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
  static func mix(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }
  static func smooth(_ x: Double) -> Double {
    let p = clamp01(x)
    return p * p * (3 - 2 * p)
  }
  static func easeOut(_ x: Double) -> Double { 1 - pow(1 - clamp01(x), 3) }
  static func spring(_ tau: Double, _ response: Double = 0.55, _ damping: Double = 0.8) -> Double {
    if tau <= 0 { return 0 }
    let w = 2 * Double.pi / response
    let zw = damping * w
    let wd = w * sqrt(1 - damping * damping)
    return 1 - exp(-zw * tau) * (cos(wd * tau) + (zw / wd) * sin(wd * tau))
  }
  static func bump(_ t: Double, _ at: Double, _ dur: Double) -> Double {
    let p = (t - at) / dur
    return p <= 0 || p >= 1 ? 0 : sin(Double.pi * p)
  }
  static func frameAt(_ t: Double) -> Frame {
    var temp = 70.0
    var from = 70.0
    var at = -10.0
    var plus = 0.0
    var minus = 0.0
    for (i, step) in steps.enumerated() {
      let (st, v) = step
      let k = bump(t, st - 0.04, 0.2)
      if v > (i > 0 ? steps[i - 1].1 : 70) { plus = max(plus, k) } else { minus = max(minus, k) }
      if t >= st {
        from = temp
        temp = v
        at = st
      }
    }
    let on = t >= 5.4 && t < 6.6
    let legAt = t >= 6.6 ? 6.6 : t >= 5.4 ? 5.4 : -10
    let lk = legAt < 0 ? 0 : spring(t - legAt)
    return Frame(
      amb: t, temp: temp, from: from, tempAt: t - at,
      heat: mix(from, temp, easeOut((t - at) / 0.9)), plusK: plus, minusK: minus,
      leg: on ? lk : t >= 6.6 ? 1 - lk : 0, legOn: on, legAt: t - legAt,
      fan: 0, fanAt: 10, cardK: max(bump(t, 5.35, 0.25), bump(t, 6.55, 0.25)), card: 3)
  }
}
