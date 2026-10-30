import Foundation

// Analytic timeline and touch driver, ported value for value from ltk-motion.ts.
enum LuckyTaskHubMotion {
  static let LOOP = 9.0
  static let RESUME = 2.0
  static let BILLS = 5
  static let BILL_FLIGHT = 0.78
  static let BILL_GAP = 0.09
  static let FLIP_DUR = 0.9
  static let HOP_DUR = 1.0
  static let BALANCE = 12480.0
  static let BILL_VALUE = 100.0
  static let FLIPS = 30.0
  enum T {
    static let spin = [0.8, 2.6]
    static let bills = 2.62
    static let prog = [3.0, 3.7]
    static let flip = 4.2
    static let hop = 5.4
    static let flipBack = 7.0
    static let tierBack = [7.2, 7.7]
    static let progBack = [7.4, 8.2]
    static let balBack = [7.7, 8.2]
  }
  static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
  static func mix(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }
  static func easeOut(_ x: Double) -> Double { 1 - pow(1 - clamp01(x), 3) }
  static func easeOutQuint(_ x: Double) -> Double { 1 - pow(1 - clamp01(x), 5) }
  static func easeInOut(_ x: Double) -> Double {
    let p = clamp01(x)
    return p < 0.5 ? 4 * p * p * p : 1 - pow(-2 * p + 2, 3) / 2
  }
  static func smooth(_ x: Double) -> Double {
    let p = clamp01(x)
    return p * p * (3 - 2 * p)
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
  struct Frame {
    var amb, wheel, wheelSpeed, pop, billsT, balance, balFade, bump: Double
    var prog, flipAng, flipFrom, flipTo, remaining, hop: Double
    var tierFrom, tierTo, tierK, chest, press, pressK: Double
  }
  static func landed(_ tau: Double) -> Double {
    var n = 0.0
    for i in 0..<BILLS { n += smooth((tau - BILL_FLIGHT - Double(i) * BILL_GAP) / 0.22) }
    return n
  }
  static func landBump(_ tau: Double) -> Double {
    var b = 0.0
    for i in 0..<BILLS { b = max(b, bump(tau, BILL_FLIGHT + Double(i) * BILL_GAP, 0.24)) }
    return b
  }
  static func frameAt(_ t: Double) -> Frame {
    let sp = seg(t, T.spin[0], T.spin[1])
    let wheel = 810 * easeOutQuint(sp)
    let speed = sp > 0 && sp < 1 ? pow(1 - sp, 4) : 0
    let billsT = t >= T.bills && t < T.balBack[0] ? t - T.bills : -1
    let balUp = billsT >= 0 ? landed(billsT) : t >= T.balBack[0] ? Double(BILLS) : 0
    let balFade = smooth(seg(t, T.balBack[0], T.balBack[1]))
    let prog =
      3 + easeInOut(seg(t, T.prog[0], T.prog[1])) - easeInOut(seg(t, T.progBack[0], T.progBack[1]))
    var flipAng = 0.0
    var flipFrom = 0.0
    var flipTo = 0.0
    if t >= T.flip && t < T.flipBack {
      flipAng = 360 * easeInOut(seg(t, T.flip, T.flip + FLIP_DUR))
      flipTo = 1
    } else if t >= T.flipBack {
      flipAng = 360 * easeInOut(seg(t, T.flipBack, T.flipBack + FLIP_DUR))
      flipFrom = 1
    }
    let remaining =
      FLIPS - 4 - smooth(seg(t, T.flip + 0.35, T.flip + 0.75))
      + smooth(seg(t, T.flipBack + 0.35, T.flipBack + 0.75))
    let hop = t >= T.hop && t < T.hop + HOP_DUR + 0.6 ? t - T.hop : -1
    var tierFrom = 0.0
    var tierTo = 0.0
    var tierK = 0.0
    if t >= T.hop && t < T.tierBack[0] {
      tierTo = 1
      tierK = smooth(seg(t, T.hop + 0.32, T.hop + 0.44))
    } else if t >= T.tierBack[0] {
      tierFrom = 1
      tierK = smooth(seg(t, T.tierBack[0], T.tierBack[1]))
    }
    return Frame(
      amb: t, wheel: wheel, wheelSpeed: speed, pop: bump(t, T.spin[1] - 0.05, 0.4), billsT: billsT,
      balance: BALANCE + BILL_VALUE * balUp, balFade: balFade,
      bump: billsT >= 0 ? landBump(billsT) : 0,
      prog: prog, flipAng: flipAng, flipFrom: flipFrom, flipTo: flipTo, remaining: remaining,
      hop: hop, tierFrom: tierFrom, tierTo: tierTo, tierK: tierK,
      chest: t >= 6.7 && t < 7.6 ? t - 6.7 : -1, press: -1, pressK: 0)
  }
  struct Driver {
    var t = 0.0
    var mode = 0
    var last = -10.0
    var amb = 0.0
    var wheel = 0.0, wheelV = 0.0
    var dragging = 0, spun = 0.0, spinning = 0
    var billsAt = -10.0, balFrom = BALANCE, balance = BALANCE
    var progFrom = 3.0, progTo = 3.0, progAt = -10.0
    var flipAt = -10.0, flipFrom = 0.0, flipTo = 0.0
    var remFrom = FLIPS - 4, remTo = FLIPS - 4
    var hopAt = -10.0, tierFrom = 0.0, tierTo = 0.0
    var chestAt = -10.0, press = -1.0, pressLast = 0.0, pressAt = -10.0, releaseAt = -10.0
    var homeAt = -10.0, wheelFrom = 0.0, wheelTo = 0.0
  }
  static func takeOver(_ d: inout Driver, _ now: Double) {
    d.last = now
    if d.mode == 2 {
      if d.homeAt > 0 {
        d.homeAt = -10
        d.balance = d.balance.rounded()
        d.wheelV = 0
      }
      return
    }
    let f = frameAt(d.t)
    d.mode = 2
    d.wheel = f.wheel.truncatingRemainder(dividingBy: 360)
    d.wheelV = 0
    d.spinning = 0
    d.billsAt = -10
    d.balance = f.balFade > 0.5 ? BALANCE : f.balance.rounded()
    d.balFrom = d.balance
    d.progFrom = f.prog.rounded()
    d.progTo = d.progFrom
    d.flipFrom = f.flipAng > 180 ? f.flipTo : f.flipFrom
    d.flipTo = d.flipFrom
    d.flipAt = -10
    d.remFrom = f.remaining.rounded()
    d.remTo = d.remFrom
    d.tierFrom = f.tierK > 0.5 ? f.tierTo : f.tierFrom
    d.tierTo = d.tierFrom
    d.hopAt = -10
    d.chestAt = -10
  }
  static func stepDriver(_ d: inout Driver, _ dt: Double, _ now: Double, _ auto: Bool) {
    d.amb = (d.amb + dt).truncatingRemainder(dividingBy: LOOP)
    if d.mode == 0 {
      if auto { d.t = (d.t + dt).truncatingRemainder(dividingBy: LOOP) }
      return
    }
    if d.dragging == 0 && d.spinning == 1 {
      let sign = d.wheelV >= 0 ? 1.0 : -1.0
      let speed = abs(d.wheelV)
      let next = max(0, speed - (90 + speed * 0.9) * dt)
      d.wheelV = sign * next
      d.wheel += d.wheelV * dt
      d.spun += abs(d.wheelV * dt)
      if next < 40 {
        let target = jsRound(d.wheel / 45) * 45
        d.wheel += (target - d.wheel) * min(1, dt * 10)
        if abs(target - d.wheel) < 0.3 {
          d.wheel = target
          d.wheelV = 0
          d.spinning = 0
          if d.spun > 300 {
            d.billsAt = now
            d.balFrom = d.balance
            d.progFrom = d.progTo
            d.progTo = min(14, d.progTo + 1)
            d.progAt = now + 0.4
          }
        }
      }
    }
    if d.billsAt > 0 {
      d.balance = d.balFrom + BILL_VALUE * landed(now - d.billsAt)
      if now - d.billsAt > BILL_FLIGHT + Double(BILLS) * BILL_GAP + 0.4 {
        d.balance = d.balFrom + BILL_VALUE * Double(BILLS)
        d.billsAt = -10
      }
    }
    if d.homeAt > 0 {
      let k = easeInOut((now - d.homeAt) / 0.8)
      d.wheel = mix(d.wheelFrom, d.wheelTo, k)
      d.balance = mix(d.balFrom, BALANCE, k)
      if now - d.homeAt > 0.9 && now - d.hopAt > HOP_DUR + 0.6 && now - d.flipAt > FLIP_DUR
        && now - d.progAt > 0.8
      {
        d.homeAt = -10
        d.mode = 0
        d.t = 0
      }
      return
    }
    if auto && d.dragging == 0 && d.spinning == 0 && d.billsAt < 0 && d.press < 0
      && now - d.last >= RESUME
    {
      d.homeAt = now
      d.wheelFrom = d.wheel
      d.wheelTo = jsRound(d.wheel / 90) * 90
      d.balFrom = d.balance
      d.progFrom = mix(d.progFrom, d.progTo, easeInOut((now - d.progAt) / 0.7))
      d.progTo = 3
      d.progAt = now
      if d.flipTo != 0 {
        _ = flipCard(&d, now)
        d.remTo = FLIPS - 4
      } else if d.remTo != FLIPS - 4 {
        d.remFrom = d.remTo
        d.remTo = FLIPS - 4
        d.flipAt = now - FLIP_DUR
      }
      if d.tierTo != 0 {
        d.tierFrom = d.tierTo
        d.tierTo = 0
        d.hopAt = now
      }
    }
  }
  // JS rounds negative half-way values toward +infinity; this matters for reverse wheel flings.
  static func jsRound(_ value: Double) -> Double { floor(value + 0.5) }
  static func frameOf(_ d: Driver, _ now: Double) -> Frame {
    if d.mode == 0 { return frameAt(d.t) }
    let billsT = d.billsAt > 0 ? now - d.billsAt : -1
    let flipP = easeInOut((now - d.flipAt) / FLIP_DUR)
    let hopT = now - d.hopAt
    let pressK =
      d.press >= 0 ? smooth((now - d.pressAt) / 0.1) : 1 - smooth((now - d.releaseAt) / 0.16)
    return Frame(
      amb: d.amb, wheel: d.wheel, wheelSpeed: min(1, abs(d.wheelV) / 900),
      pop: billsT >= 0 ? bump(billsT, -0.05, 0.4) : 0, billsT: billsT, balance: d.balance,
      balFade: 0,
      bump: billsT >= 0 ? landBump(billsT) : 0,
      prog: mix(d.progFrom, d.progTo, easeInOut((now - d.progAt) / 0.7)),
      flipAng: flipP >= 1 ? 0 : 360 * flipP, flipFrom: flipP >= 1 ? d.flipTo : d.flipFrom,
      flipTo: d.flipTo, remaining: mix(d.remFrom, d.remTo, smooth((now - d.flipAt - 0.35) / 0.4)),
      hop: hopT < HOP_DUR + 0.6 ? hopT : -1, tierFrom: d.tierFrom, tierTo: d.tierTo,
      tierK: smooth((hopT - 0.32) / 0.12), chest: now - d.chestAt < 0.9 ? now - d.chestAt : -1,
      press: d.press >= 0 ? d.press : d.pressLast, pressK: pressK)
  }
  @discardableResult static func flipCard(_ d: inout Driver, _ now: Double) -> Bool {
    if now - d.flipAt < FLIP_DUR { return false }
    d.flipFrom = d.flipTo
    d.flipTo = 1 - d.flipTo
    d.flipAt = now
    d.remFrom = d.remTo
    d.remTo = d.remTo > 0 ? d.remTo - 1 : FLIPS
    return true
  }
  @discardableResult static func hopTrophy(_ d: inout Driver, _ now: Double) -> Bool {
    if now - d.hopAt < HOP_DUR { return false }
    d.tierFrom = d.tierTo
    d.tierTo = (d.tierTo + 1).truncatingRemainder(dividingBy: 3)
    d.hopAt = now
    return true
  }
  @discardableResult static func kickWheel(_ d: inout Driver) -> Bool {
    if d.spinning == 1 || d.billsAt > 0 { return false }
    d.wheelV = 1500 + Double.random(in: 0..<1) * 400
    d.spinning = 1
    d.spun = 0
    return true
  }
}
