import Foundation

struct EvolveHoldMorphFrame {
  var stage = 0
  var c = 0.0
  var m = 0.0
  var sq = 0.0
  var yaw = 0.0
  var burst = -1.0
  var blink = 0.0
  var t = 0.0
  var hop = 0.0
  var hold = false
}

enum EvolveHoldMorphMotion {
  static let loop = 9.0
  static let names = ["Egg", "Chick", "Bluebird"]
  static let cta = ["Hold to hatch", "Hold to evolve", "Hold for a new egg"]
  static func clamp(_ x: Double) -> Double { max(0, min(1, x)) }
  static func smooth(_ a: Double, _ b: Double, _ v: Double) -> Double {
    let x = clamp((v - a) / (b - a))
    return x * x * (3 - 2 * x)
  }
  static func wobble(_ tau: Double) -> Double {
    tau < 0 ? 0 : -0.08 * exp(-6 * tau) * sin(16 * tau)
  }
  static func chargeShape(_ c: Double) -> Double { -0.07 * sin(.pi * clamp(c)) }
  static func at(_ time: Double) -> EvolveHoldMorphFrame {
    let t = time.truncatingRemainder(dividingBy: loop)
    var f = EvolveHoldMorphFrame()
    var commit = -10.0
    for (stage, a, b) in [(0, 1.0, 2.4), (1, 4.4, 5.8), (2, 7.6, 9.0)] {
      if t >= b {
        f.stage = (stage + 1) % 3
        commit = b
      } else if t >= a {
        f.stage = stage
        f.c = (t - a) / (b - a)
      }
    }
    if t < 1 { commit = 0 }
    f.m = smooth(0.5, 1, f.c)
    f.sq = chargeShape(f.c) + (commit >= 0 ? wobble(t - commit) : 0)
    f.burst = commit > 0 && commit < 9 && f.stage != 0 ? t - commit : -1
    f.yaw = 0.28 * sin(time * 2 * .pi / loop)
    f.t = time
    return f
  }
}
