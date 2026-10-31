import Foundation

// The approved RN loop, transcribed without changing its phases or curves.
enum PuffyStickerMotion {
  static let loop = 9.6
  static let morph = 0.65
  static let hold0 = 0.6, hold1 = 1.7, star = 2.4, cloud = 4.0, smiley = 5.6, flat = 7.0,
    heart = 7.9
  struct Frame {
    var puff = 0.0, shape = 0.0, squish = 0.0, touch = 0.0
    var tapAt = -1, tapPress = 0.0, flatPress = 0.0
  }
  static func clamp01(_ v: Double) -> Double { min(1, max(0, v)) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
  static func easeInOut(_ x: Double) -> Double {
    x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
  }
  static func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
  static func wobble(_ t: Double, _ freq: Double, _ decay: Double) -> Double {
    t <= 0 ? 0 : sin(2 * .pi * freq * t) * exp(-decay * t)
  }
  static func inflateAfter(_ held: Double) -> Double {
    held <= 0 ? 0 : 1 - (1 + 4.2 * held) * exp(-4.2 * held)
  }
  static func frameAt(_ t: Double) -> Frame {
    let up = inflateAfter(min(t, hold1) - hold0)
    let down = easeInOut(seg(t, flat + 0.1, flat + 0.75))
    var s = 0.0
    for a in [star, cloud, smiley] { s += easeInOut(seg(t, a + 0.1, a + 0.1 + morph)) }
    s -= 3 * easeInOut(seg(t, heart + 0.1, heart + 0.1 + morph * 1.6))
    var k = -0.03 * easeOut(seg(t, hold0, hold0 + 0.12)) * (1 - seg(t, hold1, hold1 + 0.05))
    k += 0.09 * wobble(t - hold1, 2.6, 4.2)
    for a in [star, cloud, smiley] { k += 0.05 * wobble(t - (a + 0.1 + morph), 3.2, 5.5) }
    k += 0.06 * wobble(t - (flat + 0.75), 3, 6)
    var f = Frame(
      puff: up * (1 - down), shape: s, squish: k,
      touch: easeOut(seg(t, hold0 - 0.12, hold0)) * (1 - easeOut(seg(t, hold1, hold1 + 0.18))))
    for (a, i) in [(star, 1), (cloud, 2), (smiley, 3), (heart, 0)]
    where t >= a - 0.15 && t < a + 0.35 {
      f.tapAt = i
      f.tapPress = easeOut(seg(t, a, a + 0.08)) * (1 - easeOut(seg(t, a + 0.12, a + 0.3)))
    }
    f.flatPress =
      easeOut(seg(t, flat, flat + 0.08)) * (1 - easeOut(seg(t, flat + 0.12, flat + 0.3)))
    return f
  }
}
