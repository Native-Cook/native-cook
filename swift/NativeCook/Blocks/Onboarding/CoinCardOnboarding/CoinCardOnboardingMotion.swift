import Foundation

enum CoinCardOnboardingMotion {
  static let loop = 9.0
  static let front = [[18.0, 0, 0], [6.0, -4, 34], [-14.0, -6, 0], [-20.0, 14, 0], [10.0, 6, 46]]
  static let back = [[-52.0, -74, 0], [40.0, 30, 56], [-21.0, -28, 0], [-24.0, 8, 0], [10.0, 4, 46]]
  static let euro = [[64.0, -86, 74], [-74.0, -96, -34], [36.0, 34, 30], [0.0, 360, 720]]
  static let dollar = [[22.0, 92, -70], [-18.0, -18, -26], [31.0, 30, 30], [0.0, -360, -720]]
  static let titles = ["The Card That\nWorks Anywhere", "Swap Between\nCoins in a Tap", "Your Keys,\nYour Money"]
  static let bodies = [
    "Create your account to trade, store,\nand grow your digital assets securely",
    "Move between 40 currencies at the\nrate you see, with no hidden spread",
    "Biometric approvals and cold storage\nkeep every coin in your hands"
  ]
  static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
  static func mix(_ a: Double, _ b: Double, _ x: Double) -> Double { a + (b - a) * x }
  static func smooth(_ x: Double) -> Double { let x = clamp(x); return x * x * (3 - 2 * x) }
  static func script(_ t: Double) -> Double { t >= 6.8 ? 0 : t >= 4.2 ? 2 : t >= 1.6 ? 1 : 0 }
  static func key(_ values: [Double], _ p: Double) -> Double {
    let q = min(2, max(0, p))
    let i = min(1, Int(floor(q)))
    let base = mix(values[i], values[i + 1], smooth(q - Double(i)))
    if p < 0 { return base + (values[0] - values[1]) * 0.25 * -p }
    if p > 2 { return base + (values[2] - values[1]) * 0.25 * (p - 2) }
    return base
  }
  struct Pager {
    var p = 0.0, v = 0.0, target = 0.0
    var drag = false
    var finger = 0.0, start = 0.0
    mutating func spring(_ dt: Double, _ k: Double) {
      let c = 2 * sqrt(k) * 0.92
      let n = max(1, Int(ceil(dt / (1.0 / 240))))
      let h = dt / Double(n)
      for _ in 0..<n {
        v += (k * (target - p) - c * v) * h
        p += v * h
      }
    }
  }
  // Identical to __coinCard.at(t): replay, including a partial final 1/120 s step.
  static func at(_ t: Double) -> Pager {
    var sim = Pager()
    var st = 0.0
    while st < t {
      let d = min(1.0 / 120, t - st)
      st += d
      sim.target = script(st)
      sim.spring(d, 60)
    }
    return sim
  }
}
