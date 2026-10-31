import Foundation

enum SwipeToOrderOnboardingMotion {
  static let loop = 8.0
  static let pile: [[Double]] = [[52, 70, -9, 1.06], [-36, 196, -13, 1], [18, 290, 8, 0.96]]
  static let listY = [60.0, 156, 252]
  struct Body {
    var p: [Double]
    var v = [Double](repeating: 0, count: 4)
  }
  struct Frame {
    var cards: [Body]
    var s = 0.0
    var t = 0.0
    var spin = 0.0
    var done = false
    var since = -1.0
  }
  static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
  static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b-a)*k }
  static func smooth(_ k: Double) -> Double { let x = clamp(k); return x*x*(3-2*x) }
  static func ease(_ k: Double) -> Double {
    let x = clamp(k)
    return x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2, 3)/2
  }
  static func knob(_ t: Double) -> Double {
    if t < 1.2 { return 0 }
    if t < 2.6 { return 0.55*ease((t-1.2)/1.4) }
    if t < 3 { return mix(0.55, 0.42, smooth((t-2.6)/0.4)) }
    if t < 3.8 { return mix(0.42, 1, ease((t-3)/0.8)) }
    if t < 5.9 { return 1 }
    if t < 6.5 { return 1-ease((t-5.9)/0.6) }
    return 0
  }
  static func step(_ cards: inout [Body], _ s: Double, _ dt: Double, _ reduced: Bool) {
    let k = reduced ? 400.0 : 180.0
    let c = 2*sqrt(k)*(reduced ? 1 : 0.62)
    let n = max(1, Int(ceil(dt/(1.0/240))))
    let h = dt/Double(n)
    for i in 0..<3 {
      let q = smooth((s-Double(i)*0.12)/0.76)
      let target = [0.0, listY[i], 0, 1]
      for j in 0..<n {
        _ = j
        for axis in 0..<4 {
          let aim = mix(pile[i][axis], target[axis], q)
          cards[i].v[axis] += (k*(aim-cards[i].p[axis])-c*cards[i].v[axis])*h
          cards[i].p[axis] += cards[i].v[axis]*h
        }
      }
    }
  }
  static func rest() -> Frame { Frame(cards: pile.map { Body(p: $0) }) }
  // Identical replay to RN __swipeOrder.at(t), including its semi-implicit Euler substeps.
  static func at(_ t: Double, _ reduced: Bool = false) -> Frame {
    var f = rest()
    var st = 0.0
    while st < t {
      let d = min(1.0/120, t-st)
      st += d
      step(&f.cards, knob(st), d, reduced)
    }
    f.s = knob(t)
    f.t = t
    f.spin = 4*Double.pi*t/loop
    f.done = f.s >= 0.985 || (t >= 3.8 && f.s >= 0.6)
    f.since = t >= 3.8 && t < 5.9 ? t-3.8 : -1
    return f
  }
}
