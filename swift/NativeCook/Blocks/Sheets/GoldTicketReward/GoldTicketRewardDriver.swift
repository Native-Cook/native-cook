import Foundation

final class GoldTicketRewardDriver {
  typealias M = GoldTicketRewardMotion
  typealias T = M.T
  static let SCRUB_DIST = 210.0
  static let SCRUB_END = 2.45
  static let COMMIT = 0.4
  static let FLICK = 600.0
  var t = 0.0
  // 0 autoplay, 1 plays to stop, 2 parked, 3 scrubbing.
  var mode = 0
  var stop = 0.0
  var rate = 1.0
  var last = -10.0
  var ctaAt = -1.0
  var ctaAim = 0.0
  var ctaP = 0.0
  var tiltX = 0.0
  var tiltY = 0.0
  var aimX = 0.0
  var aimY = 0.0
  var lift = 0.0
  var liftAim = 0.0
  var spinPos = 0.0
  var spinVel = 0.0
  var settling = 0
  var spinTarget = 0.0
  var hopAt = -1.0
  var hopAmp = 0.0

  static func inRestA(_ t: Double) -> Bool { t < T.press[0] || t >= T.dividerIn[1] }
  static func inRewardB(_ t: Double) -> Bool { t >= T.closeIn[1] && t < T.hold[1] }
  static func bAmountAt(_ t: Double) -> Double {
    t < 5 ? M.seg(t, T.hold[0] - 0.3, T.hold[0]) : 1 - M.seg(t, T.hold[1], T.hold[1] + 0.3)
  }
  private func follow(_ cur: Double, _ aim: Double, _ k: Double, _ dt: Double) -> Double {
    cur + (aim - cur) * (1 - exp(-dt * k))
  }
  private func stepCoin(_ dt: Double) {
    if settling == 0 && spinVel == 0 { return }
    let h = dt / 2
    for _ in 0..<2 {
      if settling == 0 {
        spinPos += spinVel * h
        spinVel *= exp(-2.1 * h)
        if abs(spinVel) < 26 {
          settling = 1
          spinTarget =
            (spinVel >= 0 ? ceil(spinPos / M.COIN_FRAMES) : floor(spinPos / M.COIN_FRAMES))
            * M.COIN_FRAMES
        }
      } else {
        let k = 230.0
        let c = 2 * 0.32 * sqrt(k)
        spinVel += (-k * (spinPos - spinTarget) - c * spinVel) * h
        spinPos += spinVel * h
        if abs(spinPos - spinTarget) < 0.03 && abs(spinVel) < 0.4 {
          spinPos = 0
          spinVel = 0
          settling = 0
        }
      }
    }
  }
  @discardableResult func step(_ dt: Double, _ now: Double, _ auto: Bool) -> Double {
    if mode == 1 {
      t += dt * rate
      if rate >= 0 && t >= stop {
        t = stop >= M.LOOP ? 0 : stop
        mode = 2
      } else if rate < 0 && t <= stop {
        t = 0
        mode = 2
        rate = 1
      }
    } else if mode == 2 {
      if auto && now - last >= M.RESUME {
        mode = 0
        rate = 1
      }
    } else if mode == 0 && auto {
      t = (t + dt).truncatingRemainder(dividingBy: M.LOOP)
    }
    tiltX = follow(tiltX, aimX, 12, dt)
    tiltY = follow(tiltY, aimY, 12, dt)
    lift = follow(lift, liftAim, mode == 3 ? 30 : 9, dt)
    ctaP = follow(ctaP, ctaAim, ctaAim > 0 ? 40 : 18, dt)
    stepCoin(dt)
    return t
  }
  func touched(_ now: Double) {
    last = now
    if mode != 0 { return }
    rate = 1
    if t >= T.press[0] && t < M.PEAK {
      mode = 1
      stop = M.PEAK
    } else if t >= T.hold[1] && t < T.dividerIn[1] {
      mode = 1
      stop = M.LOOP
    } else {
      mode = 2
    }
  }
  @discardableResult func pressA() -> Bool {
    if !Self.inRestA(t) { return false }
    t = T.press[0]
    mode = 1
    rate = 1
    stop = T.commit - 0.001
    return true
  }
  static func scrubShare(_ dy: Double) -> Double {
    let raw = -dy / SCRUB_DIST
    if raw <= 0 { return 0 }
    return raw <= 1 ? raw : 1 + (raw - 1) / (1 + (raw - 1) * 4) * 0.25
  }
  func scrubTo(_ dy: Double) -> Double {
    let p = Self.scrubShare(dy)
    mode = 3
    t = T.commit + min(p, 1) * (Self.SCRUB_END - T.commit)
    liftAim = -min(max(-dy, 0), Self.SCRUB_DIST * 1.4) * 0.12
    return p
  }
  func scrubEnd(_ dy: Double, _ vy: Double) -> Bool {
    let p = Self.scrubShare(dy)
    liftAim = 0
    mode = 1
    if p >= Self.COMMIT || vy < -Self.FLICK {
      rate = 1 + min(0.6, max(0, -vy) / 3000)
      stop = M.PEAK
      return true
    }
    rate = -2.2
    stop = T.press[0]
    return false
  }
  func releaseA(_ onTarget: Bool) {
    if onTarget {
      mode = 1
      rate = 1
      stop = M.PEAK
    } else {
      t = 0
      mode = 2
    }
  }
  @discardableResult func closeB() -> Bool {
    if !Self.inRewardB(t) { return false }
    t = T.hold[1]
    mode = 1
    rate = 1.6
    stop = M.LOOP
    return true
  }
  func flickCoin(_ vx: Double, _ now: Double) {
    let v = abs(vx) < 60 ? 110 : max(-300, min(300, vx * 0.14))
    spinVel = settling == 1 ? v : spinVel + v
    settling = 0
    hopAt = now
    hopAmp = min(16, 6 + abs(v) / 25)
  }
  func coinExtra(_ now: Double) -> (spin: Double, hop: Double) {
    let p = hopAt < 0 ? 1 : (now - hopAt) / 0.55
    return (spinPos, p < 1 ? hopAmp * sin(Double.pi * p) : 0)
  }
  func ctaTwinkle(_ now: Double) -> Double {
    if ctaAt < 0 { return 0 }
    let p = (now - ctaAt) / 0.6
    if p >= 1 {
      ctaAt = -1
      return 0
    }
    return p
  }
}
