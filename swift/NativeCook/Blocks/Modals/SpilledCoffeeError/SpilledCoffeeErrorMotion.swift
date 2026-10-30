import Foundation

// Analytic retry and the touch integrator, kept separate so pinned frames use the same script.
enum SpilledCoffeeErrorMotion {
  static let LOOP = 9.0
  static let RESUME = 2.0
  enum T {
    static let press = 1.0
    static let rewind = [1.15, 2.35]
    static let rise = 1.9
    static let lid = [2.35, 2.85]
    static let text = [1.2, 1.5]
    static let steam = [3.0, 5.6]
    static let fail = 5.65
    static let land = 6.2
    static let lidOff = [6.15, 6.7]
    static let spill = [6.2, 7.4]
    static let textBack = [5.8, 6.1]
  }
  struct Pose {
    var amb: Double
    var theta: Double
    var spill: Double
    var pour: Double
    var lid: Double
    var lidHop = -1.0
    var text = 0.0
    var steam = 0.0
    var dots = 0.0
    var rewind = 0.0
    var press = 0.0
    var splash = -1.0
    var drip = -1.0
  }
  struct Driver {
    var t = 0.0
    var mode = 0
    var last = -10.0
    var amb = 0.0
    var theta = 90.0
    var thetaV = 0.0
    var dragging = 0
    var dragTheta = 90.0
    var spill = 1.0
    var lid = 1.0
    var lidFrom = 1.0
    var lidAt = -10.0
    var lidTo = 1.0
    var hopAt = -10.0
    var retryAt = -10.0
    var r0 = [90.0, 1, 1]
    var splashAt = -10.0
    var pressAt = -10.0
    var pressing = 0
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
    return 1 - exp(-zw * tau) * (cos(wd * tau) + (zw / wd) * sin(wd * tau))
  }
  static func retryPose(_ tau: Double, _ th0: Double, _ s0: Double, _ lid0: Double, _ amb: Double)
    -> Pose
  {
    let t = T.press + tau
    let rw = seg(t, T.rewind[0], T.rewind[1])
    let theta: Double
    var spill = s0 * (1 - easeInOut(rw))
    var lid = lid0 * (1 - easeInOut(seg(t, T.lid[0], T.lid[1])))
    var pour = 0.0
    var splash = -1.0
    if t < T.fail {
      theta = th0 * (1 - spring(t - T.rise, 0.55, 0.5))
    } else if t < T.land {
      let p = seg(t, T.fail, T.land)
      theta = 90 * p * p
    } else {
      let q = t - T.land
      theta = 90 - 9 * exp(-q * 7) * abs(sin(q * 14))
      lid = easeOut(seg(t, T.lidOff[0], T.lidOff[1]))
      spill = easeOut(seg(t, T.spill[0], T.spill[1]))
      pour = 1 - smooth(seg(t, T.spill[1] - 0.5, T.spill[1]))
      splash = t - T.land
    }
    if t >= T.lidOff[0] && t < T.land { lid = easeOut(seg(t, T.lidOff[0], T.lidOff[1])) }
    let text =
      smooth(seg(t, T.text[0], T.text[1])) * (1 - smooth(seg(t, T.textBack[0], T.textBack[1])))
    let steam =
      smooth(seg(t, T.steam[0], T.steam[0] + 0.6))
      * (1 - smooth(seg(t, T.steam[1] - 0.4, T.steam[1])))
    let dots = smooth(seg(t, 1.25, 1.45)) * (1 - smooth(seg(t, 5.7, 5.9)))
    let press = max(0, 1 - abs(t - T.press - 0.06) / 0.12)
    return Pose(
      amb: amb, theta: theta, spill: spill, pour: pour, lid: lid, text: text, steam: steam,
      dots: dots, rewind: rw > 0 && rw < 1 ? sin(Double.pi * rw) : 0, press: press, splash: splash)
  }
  static func poseAt(_ t: Double) -> Pose {
    if t < T.press {
      var p = retryPose(LOOP - T.press + t, 90, 1, 1, t)
      p.drip = t >= 0.35 ? t - 0.35 : -1
      p.pour = 0
      p.splash = -1
      return p
    }
    var p = retryPose(t - T.press, 90, 1, 1, t)
    if t >= 7.9 { p.drip = t - 7.9 }
    return p
  }
  static func takeOver(_ d: inout Driver, _ now: Double) {
    d.last = now
    if d.mode == 2 { return }
    d.mode = 2
    if d.t >= T.press && d.t < LOOP - 0.3 {
      d.retryAt = now - (d.t - T.press)
      d.r0 = [90, 1, 1]
    } else {
      d.retryAt = -10
    }
    let p = poseAt(d.t)
    d.theta = p.theta
    d.thetaV = 0
    d.spill = p.spill
    d.lid = p.lid
    d.lidTo = p.lid > 0.5 ? 1 : 0
    d.lidFrom = d.lid
    d.lidAt = now - 1
  }
  static func freeze(_ d: inout Driver, _ now: Double) {
    if d.retryAt < 0 { return }
    let p = retryPose(now - d.retryAt, d.r0[0], d.r0[1], d.r0[2], d.amb)
    d.theta = p.theta
    d.spill = p.spill
    d.lid = p.lid
    d.lidFrom = p.lid
    d.lidTo = p.lid > 0.5 ? 1 : 0
    d.lidAt = now
    d.retryAt = -10
  }
  static func grabCup(_ d: inout Driver, _ now: Double) {
    freeze(&d, now)
    d.dragging = 1
    d.dragTheta = d.theta
  }
  static func retry(_ d: inout Driver, _ now: Double) {
    freeze(&d, now)
    d.retryAt = now - 0.02
    d.r0 = [d.theta, d.spill, d.lid]
  }
  static func stepDriver(_ d: inout Driver, _ dt: Double, _ now: Double, _ auto: Bool) {
    d.amb = (d.amb + dt).truncatingRemainder(dividingBy: LOOP)
    if d.mode == 0 {
      if auto { d.t = (d.t + dt).truncatingRemainder(dividingBy: LOOP) }
      return
    }
    if d.retryAt > 0 {
      let tau = now - d.retryAt
      if tau >= LOOP - T.press - 0.2 {
        let p = retryPose(tau, d.r0[0], d.r0[1], d.r0[2], d.amb)
        d.theta = p.theta
        d.spill = p.spill
        d.lid = 1
        d.lidTo = 1
        d.lidFrom = 1
        d.retryAt = -10
      }
      return
    }
    if d.dragging == 1 {
      d.theta += (d.dragTheta - d.theta) * min(1, dt * 20)
      d.thetaV = 0
    } else {
      let target = d.theta > 45 ? 90.0 : 0.0
      let k = target == 90 ? 110.0 : 90.0
      let c = 2 * (target == 90 ? 0.35 : 0.5) * sqrt(k)
      d.thetaV += (-k * (d.theta - target) - c * d.thetaV) * dt
      d.theta += d.thetaV * dt
      if d.theta > 90 {
        d.theta = 90
        d.thetaV = -abs(d.thetaV) * 0.3
      }
    }
    if d.theta > 55 && d.spill < 1 {
      let before = d.spill
      d.spill = min(1, d.spill + 1.1 * ((d.theta - 55) / 35) * dt)
      if before == 0 && d.spill > 0 { d.splashAt = now }
    }
    if d.theta > 60 && d.lidTo == 0 {
      d.lidFrom = d.lid
      d.lidTo = 1
      d.lidAt = now
    }
    d.lid = mix(d.lidFrom, d.lidTo, easeOut((now - d.lidAt) / 0.55))
    if auto && d.dragging == 0 && now - d.last >= RESUME {
      if d.theta < 89.5 && d.theta <= 45 { d.thetaV += 260 * dt }
      if d.theta > 89.4 && abs(d.thetaV) < 2 && d.spill > 0.999 && d.lid > 0.999
        && now - d.splashAt > 1.2
      {
        d.mode = 0
        d.t = 0
      }
    }
  }
  static func poseOf(_ d: Driver, _ now: Double) -> Pose {
    if d.mode == 0 { return poseAt(d.t) }
    if d.retryAt > 0 { return retryPose(now - d.retryAt, d.r0[0], d.r0[1], d.r0[2], d.amb) }
    let pour = d.theta > 55 && d.spill < 1 ? clamp01((d.theta - 55) / 20) : 0
    return Pose(
      amb: d.amb, theta: d.theta, spill: d.spill, pour: pour, lid: d.lid,
      lidHop: now - d.hopAt < 0.7 ? now - d.hopAt : -1,
      press: d.pressing == 1 ? smooth((now - d.pressAt) / 0.1) : 0,
      splash: now - d.splashAt < 1.4 ? now - d.splashAt : -1)
  }
}
