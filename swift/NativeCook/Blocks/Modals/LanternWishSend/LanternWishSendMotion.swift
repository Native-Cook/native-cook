import Foundation

enum LanternWishSendMotion {
  static let loop = 9.0
  static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
  static func smoother(_ k: Double) -> Double {
    let x = clamp(k)
    return x * x * x * (x * (x * 6 - 15) + 10)
  }
  static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
  struct Frame {
    var fold = 0.0, light = 0.0, rise = 0.0, t = 0.0, flick = 0.0, back = 1.0
    var sending = false
    var count: Int { rise > 0.9 && sending ? 1249 : 1248 }
  }
  static func sendAt(_ u: Double) -> Frame {
    Frame(fold: smoother(u / 0.8), light: smoother((u - 0.8) / 0.8),
          rise: u > 1.6 ? clamp((u - 1.6) / 3.8) : 0)
  }
  static func at(_ time: Double) -> Frame {
    let t = time.truncatingRemainder(dividingBy: loop)
    var f = sendAt(t - 1)
    f.sending = t >= 1 && t < 6.8
    f.back = f.sending ? 0 : t < 1 ? 1 : smoother((t - 6.8) / 1.6)
    f.t = time
    return f
  }
}
