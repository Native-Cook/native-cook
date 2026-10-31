import Foundation

// Pure reference channels. In particular, k is deliberately not clamped after the back easing.
enum ChromeMatchRevealMotion {
  static let loop = 8.0
  static let cardWidth = 176.0
  static let cardHeight = 236.0
  struct Frame {
    var k, squash, star, look, copy, buttons, glint: Double
  }
  static func clamp(_ x: Double) -> Double { max(0, min(1, x)) }
  static func smooth(_ k: Double) -> Double {
    let x = clamp(k)
    return x * x * (3 - 2 * x)
  }
  static func back(_ k: Double) -> Double {
    let x = clamp(k) - 1
    return 1 + 2.3 * pow(x, 3) + 1.3 * pow(x, 2)
  }
  static func channels(_ a: Double, _ b: Double) -> Frame {
    let inK = back((a - 0.25) / 0.8)
    let outK = b >= 0 ? pow(clamp(b / 0.7), 3) : 0
    let meet = a - 0.95
    let squash = meet >= 0 && meet < 0.5 ? sin(meet * 26) * exp(-meet * 10) * 0.06 : 0
    return Frame(
      k: inK * (1 - outK), squash: squash,
      star: meet >= 0 ? back(meet / 0.4) * (1 - smooth(b / 0.3)) : 0,
      look: smooth((a - 1.2) / 0.6) * (1 - smooth(b / 0.4)),
      copy: smooth((a - 1.2) / 0.5) * (1 - smooth(b / 0.3)),
      buttons: smooth((a - 1.5) / 0.4) * (1 - smooth(b / 0.3)), glint: meet)
  }
  static func at(_ t: Double) -> Frame { channels(t, t >= 6.6 ? t - 6.6 : -1) }
}
