import Foundation

// Direct translation of gpf-motion.ts. Target/stagger tests use the frame's end time,
// including inside all 1/240 s integration substeps.
enum GlassPlaylistFolderMotion {
  static let loop = 8.0
  static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
  static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
  static func ease(_ k: Double) -> Double {
    let x = clamp(k)
    return x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
  }
  struct Springs {
    var open: Double
    var ov = 0.0
    var covers: [Double]
    var cv = [Double](repeating: 0, count: 4)
    var target: Double
    var changedAt = -10.0
    var lift = 0.0
    var lv = 0.0
    var liftTarget = 0.0
    var hovered = 1
    init(_ open: Double = 0) {
      self.open = open
      covers = Array(repeating: open, count: 4)
      target = open
    }
    mutating func setTarget(_ v: Double, _ now: Double) {
      if target == v { return }
      target = v
      changedAt = now
    }
    mutating func step(_ dt: Double, _ now: Double) {
      let n = max(1, Int(ceil(dt / (1 / 240.0))))
      let h = dt / Double(n)
      for _ in 0..<n {
        ov += ((target - open) * 190 - ov * 19) * h
        open += ov * h
        for i in 0..<4 {
          let order = target == 1 ? i : 3 - i
          let live = now - changedAt > 0.05 + Double(order) * 0.06 ? target : 1 - target
          cv[i] += ((live - covers[i]) * 150 - cv[i] * 13.5) * h
          covers[i] += cv[i] * h
        }
        lv += ((liftTarget - lift) * 230 - lv * 20) * h
        lift += lv * h
      }
    }
    var moving: Bool {
      abs(open - target) + abs(ov) + abs(lift - liftTarget) + abs(lv)
        + zip(covers, cv).reduce(0) { $0 + abs($1.0 - target) + abs($1.1) } > 0.00001
    }
  }
  struct Frame {
    var t = 0.0
    var s = Springs()
    var dragging = false
    var press = 0.0
  }
  // The RN debug hook pins script time, while stepSprings keeps running during
  // the capture wait. Match that settled pose without changing live integration.
  static func at(_ time: Double) -> Frame {
    let t = max(0, time.truncatingRemainder(dividingBy: loop))
    var s = Springs(t >= 2 && t < 5.35 ? 1 : 0)
    s.lift = t >= 3.4 && t < 4.7 ? 1 : 0
    s.liftTarget = s.lift
    return Frame(t: t, s: s)
  }
}
