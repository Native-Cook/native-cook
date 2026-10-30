import Foundation

// Analytic functions and driver are kept independent of the display clock, as in sfs-motion.ts.
enum SavingsFutureSimMotion {
  static let LOOP = 9.0
  static let RESUME = 2.0
  static let MORPH = 1.0
  enum T {
    static let press = 1.0
    static let open = 1.15
    static let scrub = [2.4, 5.4]
    static let back = [5.7, 6.5]
    static let close = 7.0
  }
  static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
  static func mix(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }
  static func smooth(_ x: Double) -> Double {
    let p = clamp01(x)
    return p * p * (3 - 2 * p)
  }
  static func easeInOut(_ x: Double) -> Double {
    let p = clamp01(x)
    return p < 0.5 ? 4 * p * p * p : 1 - pow(-2 * p + 2, 3) / 2
  }
  static func spring(_ tau: Double, _ response: Double, _ damping: Double) -> Double {
    if tau <= 0 { return 0 }
    let w = 2 * Double.pi / response
    let zw = damping * w
    let wd = w * sqrt(1 - damping * damping)
    return 1 - exp(-zw * tau) * (cos(wd * tau) + (zw / wd) * sin(wd * tau))
  }
  static func bump(_ t: Double, _ at: Double, _ dur: Double) -> Double {
    let p = (t - at) / dur
    return p <= 0 || p >= 1 ? 0 : sin(Double.pi * p)
  }
  static func balance(_ years: Double, _ rate: Double) -> Double {
    let r = rate / 100
    let g = pow(1 + r, years)
    return 10000 * g + 200 * 12 * ((g - 1) / r)
  }
  struct Frame {
    var amb: Double
    var morph: Double
    var open: Bool
    var morphAt: Double
    var year: Double
    var rate: Double
    var press: Double
    var knob: Double
  }
  static func frameAt(_ t: Double) -> Frame {
    let open = t >= T.open && t < T.close
    let at = t >= T.close ? T.close : t >= T.open ? T.open : -10
    let morph =
      open
      ? seg(t, T.open, T.open + MORPH) : t >= T.close ? 1 - seg(t, T.close, T.close + MORPH) : 0
    var year = 1.0
    if t >= T.scrub[0] { year = mix(1, 30, easeInOut(seg(t, T.scrub[0], T.scrub[1]))) }
    if t >= T.back[0] { year = mix(30, 10, easeInOut(seg(t, T.back[0], T.back[1]))) }
    return Frame(
      amb: t, morph: morph, open: open, morphAt: t - at, year: year, rate: 6,
      press: bump(t, T.press - 0.02, 0.3), knob: 0)
  }
  struct Driver {
    var t = 0.0, last = -10.0, amb = 0.0
    var mode = 0
    var open = false
    var morphAt = -10.0, morphFrom = 0.0, year = 1.0, rate = 6.0
    var pressing = 0
    var pressAt = -10.0, releaseAt = -10.0, closeAt = -10.0
    func morphOf(_ now: Double) -> Double {
      let k = seg(now, morphAt, morphAt + MORPH)
      return open ? mix(morphFrom, 1, k) : mix(morphFrom, 0, k)
    }
    mutating func setOpen(_ value: Bool, _ now: Double) {
      if open == value { return }
      morphFrom = morphOf(now)
      open = value
      morphAt = now
    }
    mutating func takeOver(_ now: Double) {
      last = now
      if mode == 2 { return }
      let f = frameAt(t)
      mode = 2
      open = f.open
      morphFrom = f.open ? 0 : 1
      morphAt = now - f.morphAt
      if !f.open && f.morph <= 0 {
        morphFrom = 0
        morphAt = now - 10
      }
      year = f.year
      rate = f.rate
      closeAt = -10
    }
    mutating func step(_ dt: Double, _ now: Double, _ auto: Bool) {
      amb = (amb + dt).truncatingRemainder(dividingBy: LOOP)
      if mode == 0 {
        if auto { t = (t + dt).truncatingRemainder(dividingBy: LOOP) }
        return
      }
      if closeAt > 0 && now >= closeAt && pressing == 0 {
        closeAt = -10
        setOpen(false, now)
      }
      if !auto || pressing > 0 || now - last < RESUME { return }
      if open { setOpen(false, now) }
      rate += (6 - rate) * min(1, dt * 6)
      if abs(rate - 6) < 0.005 { rate = 6 }
      if !open && morphOf(now) <= 0 && rate == 6 {
        mode = 0
        t = 0
      }
      year = 1
    }
    func frameOf(_ now: Double) -> Frame {
      if mode == 0 { return frameAt(t) }
      let press =
        pressing == 1 ? smooth((now - pressAt) / 0.15) : 1 - smooth((now - releaseAt) / 0.15)
      return Frame(
        amb: amb, morph: morphOf(now), open: open, morphAt: now - morphAt, year: year, rate: rate,
        press: pressing == 1 || now - releaseAt < 0.15 ? press : 0, knob: pressing == 3 ? 1 : 0)
    }
  }
}
