import Foundation

/// Shared clock functions: the nine chunk arrivals, completion spans, and drag rubber band.
enum HorizonMath {
  static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
  static func part(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
  static func smooth(_ x: Double) -> Double { x * x * (3 - 2 * x) }
  static let chunks: [(at: Double, span: Double, to: Double)] = [
    (0, 300, 0.12), (400, 400, 0.27), (900, 450, 0.40), (1850, 550, 0.53),
    (2450, 650, 0.68), (3150, 550, 0.81), (3800, 400, 0.90), (4650, 250, 0.96), (4950, 250, 1)
  ]
  static func upload(_ clock: Double) -> (progress: Double, pulse: Double, chunk: Int) {
    let ms = clamp(clock) * 5200
    var progress = 0.0, arrival = -450.0, index = -1
    for chunk in chunks {
      if ms < chunk.at { break }
      index += 1
      let t = part(ms, chunk.at, chunk.at + chunk.span)
      progress += (chunk.to - progress) * (1 - (1 - t) * (1 - t))
      arrival = chunk.at
      if t < 1 { break }
    }
    return (progress, max(0, 1 - (ms - arrival) / 450), index)
  }
  static func cardScale(_ crest: Double, _ press: Double) -> Double {
    let inhale = part(crest, 0, 0.12), release = part(crest, 0.3, 0.7)
    let squeeze = 1 - (1 - inhale) * (1 - inhale)
    let back = 1 - exp(-6 * release) * cos(9 * release)
    return 1 - 0.018 * squeeze * (1 - back) - 0.008 * press
  }
  static func rubber(_ pull: Double) -> Double { 0.75 * pull / (1 + 0.75 * abs(pull) / 110) }
  static func pull(_ shift: Double) -> Double {
    let b = min(109.999, max(-109.999, shift))
    return b / (0.75 * (1 - abs(b) / 110))
  }
  static func velocity(_ pull: Double, _ speed: Double) -> Double {
    speed * 0.75 / pow(1 + 0.75 * abs(pull) / 110, 2)
  }
}
