import Foundation

// Analytic timeline and touch driver, matching lbp-motion.ts without native spring approximations.
enum ListeningBuddyPaywallMotion {
  static let LOOP = 9.0
  static let RESUME = 2.0
  static let BEAT = LOOP / 12
  static let REST_PLAN = 1
  static let picks: [(Double, Int)] = [(1.2, 0), (3.0, 2), (4.8, 1)]
  static let go = 6.5
  static let faces: [(eyes: Double, smile: Double, open: Double, blush: Double)] = [
    (1, 0.45, 0, 0.25), (0, 1, 0, 1), (2, 0.3, 1, 0.6),
  ]
  struct Frame {
    var amb: Double
    var plan: Int
    var from: Int
    var selAt: Double
    var go: Double
    var poke: Double
    var press: Double
    var closeK: Double
  }
  struct Driver {
    var t = 0.0
    var mode = 0
    var last = -10.0
    var amb = 0.0
    var plan = REST_PLAN
    var from = REST_PLAN
    var selAt = -10.0
    var goAt = -10.0
    var pokeAt = -10.0
    var pressing = 0
    var pressAt = -10.0
    var releaseAt = -10.0
    var released = 0
  }
  static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : x > 1 ? 1 : x }
  static func mix(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }
  static func smooth(_ x: Double) -> Double {
    let p = clamp01(x)
    return p * p * (3 - 2 * p)
  }
  static func easeOut(_ x: Double) -> Double { 1 - pow(1 - clamp01(x), 3) }
  static func spring(_ tau: Double, _ response: Double, _ damping: Double) -> Double {
    if tau <= 0 { return 0 }
    let w = 2 * Double.pi / response
    let zw = damping * w
    let wd = w * sqrt(1 - damping * damping)
    return 1 - exp(-zw * tau) * (cos(wd * tau) + zw / wd * sin(wd * tau))
  }
  static func bump(_ t: Double, _ at: Double, _ dur: Double) -> Double {
    let p = (t - at) / dur
    return p <= 0 || p >= 1 ? 0 : sin(Double.pi * p)
  }
  static func frameAt(_ t: Double) -> Frame {
    var plan = REST_PLAN
    var from = REST_PLAN
    var at = -10.0
    for pick in picks where t >= pick.0 {
      from = plan
      plan = pick.1
      at = pick.0
    }
    return Frame(
      amb: t, plan: plan, from: from, selAt: t - at,
      go: t >= go ? t - go : -1, poke: -1,
      press: bump(t, go - 0.02, 0.3), closeK: 0)
  }
  static func takeOver(_ d: inout Driver, _ now: Double) {
    d.last = now
    if d.mode == 2 { return }
    let f = frameAt(d.t)
    d.mode = 2
    d.plan = f.plan
    d.from = f.from
    d.selAt = now - f.selAt
    d.goAt = f.go >= 0 ? now - f.go : -10
  }
  @discardableResult static func select(_ d: inout Driver, _ plan: Int, _ now: Double) -> Bool {
    if plan == d.plan { return false }
    d.from = d.plan
    d.plan = plan
    d.selAt = now
    return true
  }
  static func stepDriver(_ d: inout Driver, _ dt: Double, _ now: Double, _ auto: Bool) {
    d.amb = (d.amb + dt).truncatingRemainder(dividingBy: LOOP)
    if d.mode == 0 {
      if auto { d.t = (d.t + dt).truncatingRemainder(dividingBy: LOOP) }
      return
    }
    if !auto || d.pressing > 0 || now - d.last < RESUME { return }
    if d.plan != REST_PLAN {
      select(&d, REST_PLAN, now)
      d.last = now - RESUME + 0.9
      return
    }
    if now - d.selAt > 1 && now - d.goAt > 2.5 && now - d.pokeAt > 1 {
      d.mode = 0
      d.t = 0
    }
  }
  static func frameOf(_ d: Driver, _ now: Double) -> Frame {
    if d.mode == 0 { return frameAt(d.t) }
    let press =
      d.pressing == 1
      ? smooth((now - d.pressAt) / 0.15)
      : d.released == 1 ? 1 - smooth((now - d.releaseAt) / 0.15) : 0
    let closeK =
      d.pressing == 2
      ? smooth((now - d.pressAt) / 0.15)
      : d.released == 2 ? 1 - smooth((now - d.releaseAt) / 0.15) : 0
    return Frame(
      amb: d.amb, plan: d.plan, from: d.from, selAt: now - d.selAt,
      go: now - d.goAt < 2.5 ? now - d.goAt : -1,
      poke: now - d.pokeAt < 1 ? now - d.pokeAt : -1, press: press, closeK: closeK)
  }
}
