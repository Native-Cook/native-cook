import Foundation

// Seconds and card points throughout. Polynomial loop easing deliberately
// differs from the manual cubic Bézier; these are separate in the RN source.
enum EmberMath {
  private typealias M = EmberMotionConstants

  static func clamp(_ x: Double, _ a: Double = 0, _ b: Double = 1) -> Double { min(b, max(a, x)) }

  static func seg(_ x: Double, _ a: Double, _ b: Double) -> Double { clamp((x - a) / (b - a)) }

  static func out(_ x: Double) -> Double { 1 - pow(1 - x, 4) }

  static func inOut(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }

  static func charge(_ t: Double) -> Double {
    if t < M.loop.rest { return 0 }
    if t < M.loop.charged { return inOut((t - M.loop.rest) / M.charge) }
    if t < M.loop.held { return 1 }
    return 1 - inOut(clamp((t - M.loop.held) / M.cool))
  }

  static func ignition(_ t: Double) -> Double {
    t < M.loop.charged ? 0 : min(1, (t - M.loop.charged) / M.ignite)
  }

  /// Invert the reference cubic x curve with 18 bisection steps; loop easing stays polynomial.
  static func bezier(_ x: Double, out: Bool = false) -> Double {
    let x1 = out ? 0.23 : 0.77
    let x2 = out ? 0.32 : 0.175
    var lo = 0.0
    var hi = 1.0
    for _ in 0..<18 {
      let t = (lo + hi) / 2
      let a = 1 - t
      if 3 * a * a * t * x1 + 3 * a * t * t * x2 + t * t * t < x { lo = t } else { hi = t }
    }
    let t = (lo + hi) / 2
    let a = 1 - t
    return out ? 3 * a * a * t + 3 * a * t * t + t * t * t : 3 * a * t * t + t * t * t
  }

  static func beat(_ phase: Double) -> Double {
    let ms = phase * M.beat.span
    if ms < M.beat.lubUp { return 1 + M.beat.lub * out(ms / M.beat.lubUp) }
    if ms < M.beat.lubUp + M.beat.lubDown {
      return (1 + M.beat.lub) - M.beat.lub * out((ms - M.beat.lubUp) / M.beat.lubDown)
    }
    if ms < M.beat.lubUp + M.beat.lubDown + M.beat.dubUp {
      return 1 + M.beat.dub * out((ms - (M.beat.lubUp + M.beat.lubDown)) / M.beat.dubUp)
    }
    return (1 + M.beat.dub) - M.beat.dub
      * out(min(1, (ms - (M.beat.lubUp + M.beat.lubDown + M.beat.dubUp)) / M.beat.dubDown))
  }
}
