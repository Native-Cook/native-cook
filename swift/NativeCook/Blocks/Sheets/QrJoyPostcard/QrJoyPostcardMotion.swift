import Foundation

// Analytic timeline and touch driver, translated from qjp-motion.ts.
enum QrJoyPostcardMotion {
  typealias Theme = QrJoyPostcardTheme
  static let LOOP = 9.0
  static let RESUME = 2.0
  static let PEAK = 5.35
  enum T {
    static let press = [1.25, 1.42]
    static let commit = 1.42
    static let liftAt = 1.42
    static let swirlAt = 1.52
    static let face = [1.85, 2.95]
    static let finders = [2.0, 2.55]
    static let cta = [2.15, 2.6]
    static let check = [2.55, 2.95]
    static let heartAt = 3.0
    static let burst = [3.0, 3.75]
    static let sticker = [3.15, 3.42]
    static let hop = [3.5, 3.95]
    static let bubbleAt = 3.62
    static let write = [3.75, 4.6]
    static let wave = [3.75, 5.15]
    static let sheen = [4.7, 5.35]
    static let back = 6.5
    static let backSwirlAt = 6.65
    static let lower = [7.55, 8.15]
  }
  static let SWIRL_WINDOW = 0.62
  static let SWIRL_JITTER = 0.14
  static let FLIGHT = 0.82
  static let BACK_WINDOW = 0.42
  static let BACK_FLIGHT = 0.62
  static let SWIRL_DONE = T.swirlAt + SWIRL_WINDOW + SWIRL_JITTER + FLIGHT
  static let BACK_DONE = T.backSwirlAt + BACK_WINDOW + 0.1 + BACK_FLIGHT
  static let LIFT = 34.0
  static let TURN_LIFTED = -4 * Double.pi / 180

  static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
  static func mix(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }
  static func easeOut(_ x: Double) -> Double {
    let u = 1 - clamp01(x)
    return 1 - u * u * u * u * u
  }
  static func easeInOut(_ x: Double) -> Double {
    let p = clamp01(x)
    return p < 0.5 ? 16 * p * p * p * p * p : 1 - pow(-2 * p + 2, 5) / 2
  }
  static func easeInOutCubic(_ x: Double) -> Double {
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
    return 1 - exp(-zw * tau) * (cos(wd * tau) + zw / wd * sin(wd * tau))
  }
  static func wobble(_ tau: Double, _ period: Double, _ decay: Double) -> Double {
    tau <= 0 ? 0 : exp(-tau / decay) * cos(2 * Double.pi * tau / period)
  }
  struct Frame {
    var t, press, lift, face, finders, cta, check, heart, burst: Double
    var sticker, stickerSquash, stickerPeel, bubble, write: Double
    var hop, squash, wave, sheen, landSquash: Double
  }
  static func liftAt(_ t: Double) -> Double {
    t < T.back ? spring(t - T.liftAt, 0.62, 0.72) : 1 - easeInOut(seg(t, T.lower[0], T.lower[1]))
  }
  static func moduleU(_ m: Theme.Module, _ t: Double) -> Double {
    if t < T.back {
      let start = T.swirlAt + SWIRL_WINDOW * m.d + SWIRL_JITTER * m.h
      return easeInOutCubic(seg(t, start, start + FLIGHT))
    }
    let start = T.backSwirlAt + BACK_WINDOW * (1 - m.d) + 0.1 * m.h
    return 1 - easeInOutCubic(seg(t, start, start + BACK_FLIGHT))
  }
  static func moduleRaw(_ m: Theme.Module, _ t: Double) -> Double {
    if t < T.back {
      let start = T.swirlAt + SWIRL_WINDOW * m.d + SWIRL_JITTER * m.h
      return seg(t, start, start + FLIGHT)
    }
    let start = T.backSwirlAt + BACK_WINDOW * (1 - m.d) + 0.1 * m.h
    return 1 - seg(t, start, start + BACK_FLIGHT)
  }
  static func modulePose(_ m: Theme.Module, _ u: Double) -> (
    x: Double, y: Double, z: Double, spin: Double
  ) {
    let mid = Theme.QR.x + Theme.QR.size / 2
    let midY = Theme.QR.y + Theme.QR.size / 2
    let x0 = Theme.QR.x + (m.c + 0.5) * Theme.QR.m - mid
    let y0 = Theme.QR.y + (m.r + 0.5) * Theme.QR.m - midY
    let radius = hypot(x0, y0)
    let angle0 = atan2(y0, x0)
    let z = sin(Double.pi * u)
    let angle = angle0 + 2 * Double.pi * u
    let r = radius + (10 + 26 * m.h) * z
    let turn = min(1, u / 0.3)
    return (
      mid + r * cos(angle), midY + r * sin(angle) - 12 * z, z, (angle + Double.pi / 2) * turn * turn
    )
  }
  static func waveFrame(_ t: Double) -> Double {
    let p = seg(t, T.wave[0], T.wave[1])
    if p <= 0 || p >= 1 { return Theme.BEAR.rest }
    let envelope = min(1, min(p * 5, (1 - p) * 5))
    return Theme.BEAR.rest + 4 * sin(2 * Double.pi * 2 * p) * envelope
  }
  static func frameAt(_ t: Double) -> Frame {
    let forward = t < T.back
    let pressIn = seg(t, T.press[0], T.press[0] + 0.12)
    let pressOut = seg(t, T.commit, T.commit + 0.16)
    let press =
      t >= T.press[0] && t < T.commit + 0.16 ? easeOut(pressIn) * (1 - easeOut(pressOut)) : 0
    let face = forward ? easeInOut(seg(t, T.face[0], T.face[1])) : 1 - easeInOut(seg(t, 6.8, 7.6))
    let finders =
      forward ? easeInOut(seg(t, T.finders[0], T.finders[1])) : 1 - easeInOut(seg(t, 6.75, 7.2))
    let cta = forward ? easeInOut(seg(t, T.cta[0], T.cta[1])) : 1 - easeInOut(seg(t, 6.6, 6.95))
    let check = forward ? easeOut(seg(t, T.check[0], T.check[1])) : 1 - easeOut(seg(t, 6.6, 6.85))
    let heart = forward ? spring(t - T.heartAt, 0.5, 0.55) : 1 - easeInOut(seg(t, 6.55, 6.8))
    let burstP = seg(t, T.burst[0], T.burst[1])
    let burst = t >= T.burst[0] && t < T.burst[1] ? burstP : -1
    let sticker = forward ? seg(t, T.sticker[0], T.sticker[1]) : 1
    let stickerSquash = forward ? wobble(t - T.sticker[1], 0.24, 0.16) : 0
    let stickerPeel = forward ? 0 : easeInOut(seg(t, 6.5, 6.9))
    let bubble = forward ? spring(t - T.bubbleAt, 0.42, 0.6) : 1 - easeInOut(seg(t, 6.5, 6.68))
    let write = forward ? seg(t, T.write[0], T.write[1]) : 1 - seg(t, 6.5, 6.6)
    let hp = seg(t, T.hop[0], T.hop[1])
    let hop = hp > 0 && hp < 1 ? 13 * sin(Double.pi * hp) : 0
    let crouch =
      t > T.hop[0] - 0.12 && t < T.hop[0] ? sin(Double.pi * seg(t, T.hop[0] - 0.12, T.hop[0])) : 0
    let land = wobble(t - T.hop[1], 0.22, 0.14)
    let cardLand =
      forward
      ? wobble(t - (T.liftAt + 0.48), 0.26, 0.16) * 0.6 : wobble(t - T.lower[1], 0.26, 0.14) * 0.6
    let squash =
      0.07 * crouch - 0.05 * sin(Double.pi * hp) * (hp > 0 && hp < 1 ? 1 : 0) + 0.08 * land + 0.05
      * cardLand
    let sheen = seg(t, T.sheen[0], T.sheen[1])
    return Frame(
      t: t, press: press, lift: liftAt(t), face: face, finders: finders, cta: cta, check: check,
      heart: heart, burst: burst, sticker: sticker, stickerSquash: stickerSquash,
      stickerPeel: stickerPeel,
      bubble: bubble, write: write, hop: hop, squash: squash, wave: waveFrame(t),
      sheen: sheen > 0 && sheen < 1 ? sheen : -1, landSquash: cardLand)
  }
  static func inRestA(_ t: Double) -> Bool { t < T.press[0] || t >= T.lower[1] }
  static func inPostcard(_ t: Double) -> Bool { t >= T.heartAt && t < T.back }

  struct Driver {
    var t = 0.0
    var mode = 0
    var stop = 0.0
    var rate = 1.0
    var last = -10.0
    var amb = 0.0
    var ctaAim = 0.0, ctaP = 0.0
    var tiltX = 0.0, tiltY = 0.0, aimX = 0.0, aimY = 0.0
    var lean = 0.0, leanV = 0.0
    var bearAt = -1.0, jiggleAt = -1.0
  }
  static func makeDriver(_ t: Double = 0) -> Driver { Driver(t: t, amb: t) }
  static func follow(_ current: Double, _ aim: Double, _ k: Double, _ dt: Double) -> Double {
    current + (aim - current) * (1 - exp(-dt * k))
  }
  @discardableResult static func stepDriver(
    _ d: inout Driver, _ dt: Double, _ now: Double, _ auto: Bool
  ) -> Double {
    if d.mode == 1 {
      d.t += dt * d.rate
      if d.t >= d.stop {
        d.t = d.stop >= LOOP ? 0 : d.stop
        d.mode = 2
        d.rate = 1
        d.last = now
      }
    } else if d.mode == 2 {
      if auto && now - d.last >= RESUME { d.mode = 0 }
    } else if auto {
      d.t = (d.t + dt).truncatingRemainder(dividingBy: LOOP)
    }
    d.amb = (d.amb + dt).truncatingRemainder(dividingBy: LOOP)
    d.tiltX = follow(d.tiltX, d.aimX, 11, dt)
    d.tiltY = follow(d.tiltY, d.aimY, 11, dt)
    let k = 120.0
    let c = 2 * 0.35 * sqrt(k)
    d.leanV += (-k * (d.lean - d.tiltX) - c * d.leanV) * dt
    d.lean += d.leanV * dt
    d.ctaP = follow(d.ctaP, d.ctaAim, d.ctaAim > 0 ? 40 : 18, dt)
    return d.t
  }
  static func touched(_ d: inout Driver, _ now: Double) {
    d.last = now
    if d.mode != 0 { return }
    d.rate = 1
    if d.t >= T.press[0] && d.t < PEAK {
      d.mode = 1
      d.stop = PEAK
    } else if d.t >= T.back && d.t < T.lower[1] {
      d.mode = 1
      d.stop = LOOP
    } else {
      d.mode = 2
    }
  }
  @discardableResult static func pressA(_ d: inout Driver) -> Bool {
    if !inRestA(d.t) { return false }
    d.t = T.press[0]
    d.mode = 1
    d.rate = 1
    d.stop = T.commit - 0.001
    return true
  }
  static func releaseA(_ d: inout Driver, _ onTarget: Bool) {
    if onTarget {
      d.mode = 1
      d.rate = 1
      d.stop = PEAK
    } else {
      d.t = 0
      d.mode = 2
    }
  }
  @discardableResult static func closeB(_ d: inout Driver) -> Bool {
    if !inPostcard(d.t) && !(d.t >= PEAK && d.t < T.back) { return false }
    d.t = T.back
    d.mode = 1
    d.rate = 1.5
    d.stop = LOOP
    return true
  }
  static func bearExtra(_ d: inout Driver, _ now: Double) -> (
    hop: Double, squash: Double, wave: Double, on: Double
  ) {
    if d.bearAt < 0 { return (0, 0, 0, 0) }
    let p = (now - d.bearAt) / 1.25
    if p >= 1 {
      d.bearAt = -1
      return (0, 0, 0, 0)
    }
    let hp = clamp01(p / 0.36)
    let hop = hp < 1 ? 11 * sin(Double.pi * hp) : 0
    let squash =
      hp < 1 ? -0.045 * sin(Double.pi * hp) : 0.07 * wobble((p - 0.36) * 1.25, 0.22, 0.14)
    let envelope = min(1, min(p * 6, (1 - p) * 4))
    return (hop, squash, 4 * sin(2 * Double.pi * 1.5 * p) * envelope, 1)
  }
  static func jiggle(_ d: inout Driver, _ now: Double) -> Double {
    if d.jiggleAt < 0 { return 0 }
    let p = now - d.jiggleAt
    if p > 0.9 {
      d.jiggleAt = -1
      return 0
    }
    return wobble(p, 0.16, 0.2)
  }
}
