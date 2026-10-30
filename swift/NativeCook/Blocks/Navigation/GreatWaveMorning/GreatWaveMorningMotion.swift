import Foundation

// Analytic loop and touch physics from gwm-motion.ts, in the pin's 736-pixel coordinates.
enum GreatWaveMorningMotion {
  static let LOOP = 9.0
  static let RESUME = 2.0
  static let PULL = 150.0
  enum T {
    static let pull = [1.2, 1.9]
    static let release = 2.1
    static let collapse = [3.0, 4.1]
    static let unfold = [5.6, 6.8]
  }
  struct Frame {
    var amb: Double
    var scroll: Double
    var foam: Double
    var foamPow: Double
    var done: [Double]
    var tab: Double
    var pressRow: Int
    var pressK: Double
    var tiltX = 0.0
    var tiltY = 0.0
  }
  struct Driver {
    var t = 0.0
    var mode = 0
    var last = -10.0
    var amb = 0.0
    var s = 0.0
    var v = 0.0
    var dragging = 0
    var raw = 0.0
    var releaseAt = -10.0
    var foamPow = 0.0
    var done = Array(repeating: false, count: 5)
    var doneAt = Array(repeating: -10.0, count: 5)
    var tab = 0
    var tabPos = 0.0
    var tabV = 0.0
    var pressRow = -1
    var pressAt = -10.0
    var releaseRowAt = -10.0
    var pressLast = 0
  }
  static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
  static func mix(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }
  static func smooth(_ x: Double) -> Double {
    let p = clamp01(x)
    return p * p * (3 - 2 * p)
  }
  static func easeOut(_ x: Double) -> Double { 1 - pow(1 - clamp01(x), 3) }
  static func easeInOut(_ x: Double) -> Double {
    let p = clamp01(x)
    return p < 0.5 ? 4 * p * p * p : 1 - pow(-2 * p + 2, 3) / 2
  }
  static func spring(_ tau: Double, _ response: Double, _ damping: Double) -> Double {
    if tau <= 0 { return 0 }
    let w = 2 * Double.pi / response
    let zw = damping * w
    let wd = w * sqrt(1 - damping * damping)
    return 1 - exp(-zw * tau) * (cos(wd * tau) + zw / wd * sin(wd * tau))
  }
  static func rubber(_ d: Double) -> Double { (1 - 1 / (d * 0.55 / 900 + 1)) * 900 }
  static func loopScroll(_ t: Double) -> Double {
    if t < T.release { return -PULL * easeOut(seg(t, T.pull[0], T.pull[1])) }
    if t < T.collapse[0] { return -PULL * (1 - spring(t - T.release, 0.55, 0.62)) }
    if t < T.unfold[0] {
      return (GreatWaveMorningTheme.COLLAPSE + 40) * easeInOut(seg(t, T.collapse[0], T.collapse[1]))
    }
    return (GreatWaveMorningTheme.COLLAPSE + 40) * (1 - easeInOut(seg(t, T.unfold[0], T.unfold[1])))
  }
  static func frameAt(_ t: Double) -> Frame {
    let foam = t >= T.release && t < T.release + 1.6 ? t - T.release : -1
    return Frame(
      amb: t, scroll: loopScroll(t), foam: foam, foamPow: 1,
      done: [0, 0, 0, 0, 0], tab: 0, pressRow: -1, pressK: 0)
  }
  static func takeOver(_ d: inout Driver, _ now: Double) {
    d.last = now
    if d.mode == 2 { return }
    d.mode = 2
    d.s = loopScroll(d.t)
    d.v = 0
    let f = frameAt(d.t)
    if f.foam >= 0 {
      d.releaseAt = now - f.foam
      d.foamPow = 1
    }
  }
  static func stepDriver(_ d: inout Driver, _ dt: Double, _ now: Double, _ auto: Bool) {
    d.amb = (d.amb + dt).truncatingRemainder(dividingBy: LOOP)
    if d.mode == 0 {
      if auto { d.t = (d.t + dt).truncatingRemainder(dividingBy: LOOP) }
      return
    }
    let idle = now - d.last >= RESUME && d.dragging == 0 && d.pressRow < 0
    if auto && idle {
      for i in 0..<5 where d.done[i] {
        d.done[i] = false
        d.doneAt[i] = now
      }
      d.tab = 0
    }
    if d.dragging == 0 {
      if d.s < 0 || d.s > GreatWaveMorningTheme.MAX_SCROLL || (auto && idle) {
        let target = auto && idle ? 0 : d.s < 0 ? 0 : GreatWaveMorningTheme.MAX_SCROLL
        let k = 170.0
        let c = 2 * 0.86 * sqrt(k)
        d.v += (-k * (d.s - target) - c * d.v) * dt
        d.s += d.v * dt
      } else {
        d.s += d.v * dt
        d.v *= exp(-dt * 3.2)
        if abs(d.v) < 4 { d.v = 0 }
      }
    }
    let k = 260.0
    let c = 2 * 0.8 * sqrt(k)
    d.tabV += (-k * (d.tabPos - Double(d.tab)) - c * d.tabV) * dt
    d.tabPos += d.tabV * dt
    let lastDone = d.doneAt.reduce(-10, max)
    if auto && idle && abs(d.s) < 0.5 && abs(d.v) < 5 && abs(d.tabPos) < 0.01
      && now - lastDone > 0.4
    {
      d.mode = 0
      d.t = 0
    }
  }
  static func frameOf(_ d: Driver, _ now: Double) -> Frame {
    if d.mode == 0 { return frameAt(d.t) }
    let done = (0..<5).map { i in
      let k = smooth((now - d.doneAt[i]) / 0.35)
      return d.done[i] ? k : 1 - k
    }
    let pressK =
      d.pressRow >= 0 ? smooth((now - d.pressAt) / 0.1) : 1 - smooth((now - d.releaseRowAt) / 0.18)
    let foam = now - d.releaseAt < 1.6 ? now - d.releaseAt : -1
    return Frame(
      amb: d.amb, scroll: d.s, foam: foam, foamPow: d.foamPow, done: done,
      tab: d.tabPos, pressRow: d.pressRow >= 0 ? d.pressRow : d.pressLast, pressK: pressK)
  }
}
