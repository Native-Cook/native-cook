import Foundation

enum SunUvBuddyMotion {
  static let LOOP = 9.0, RESUME = 2.0
  static let REST = (day: 2, hour: 3.0)
  enum T {
    static let toEvening = [1.0, 2.4], toNoon = [3.0, 3.9]
    static let today = 4.4, hourUp = [4.45, 5.0], thu = 6.6, hourDown = [6.6, 7.15]
  }
  static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
  static func mix(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }
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
  static func loopState(_ t: Double) -> (hour: Double, from: Int, to: Int, at: Double) {
    let hour: Double
    if t < T.toNoon[0] {
      hour = mix(3, 9, easeInOut(seg(t, T.toEvening[0], T.toEvening[1])))
    } else if t < T.today {
      hour = mix(9, 3, easeInOut(seg(t, T.toNoon[0], T.toNoon[1])))
    } else if t < T.thu {
      hour = mix(3, 4, easeInOut(seg(t, T.hourUp[0], T.hourUp[1])))
    } else {
      hour = mix(4, 3, easeInOut(seg(t, T.hourDown[0], T.hourDown[1])))
    }
    if t >= T.today && t < T.thu { return (hour, 2, 0, T.today) }
    if t >= T.thu { return (hour, 0, 2, T.thu) }
    return (hour, 2, 2, -10)
  }
  static func barValue(_ i: Int, _ from: Int, _ to: Int, _ tau: Double) -> Double {
    if from == to { return SunUvBuddyTheme.forecast[to][i] }
    return mix(
      SunUvBuddyTheme.forecast[from][i], SunUvBuddyTheme.forecast[to][i],
      spring(tau - Double(i) * 0.035, 0.42, 0.62))
  }
  static func uvAt(_ hour: Double, _ from: Int, _ to: Int, _ tau: Double) -> Double {
    let h = max(0, min(10, hour))
    let i = Int(floor(h))
    let j = min(10, i + 1)
    return mix(barValue(i, from, to, tau), barValue(j, from, to, tau), h - Double(i))
  }
  static func sizeFor(_ uv: Double) -> Double {
    if uv <= 3 { return mix(0.62, 1, smooth(uv / 3) * 0.6 + (uv / 3) * 0.4) }
    return 1 + 0.06 * min(2, uv - 3)
  }
  struct Driver {
    var t = 0.0, mode = 0, last = -10.0, amb = 0.0
    var hour = 3.0, hourV = 0.0, hourAim = 3.0, dragging = 0
    var from = 2, to = 2, at = -10.0
    var size = 1.0, sizeV = 0.0, squishAt = -1.0, lookX = 0.0
  }
  struct Live {
    var amb = 0.0, hour = 3.0, hourV = 0.0, from = 2, to = 2, tau = 10.0
    var size = 1.0, squish = -1.0, dayPress = 0.0
  }
  static func stepDriver(_ d: inout Driver, _ dt: Double, _ now: Double, _ auto: Bool) -> Double {
    d.amb = (d.amb + dt).truncatingRemainder(dividingBy: LOOP)
    var base = now
    if d.mode == 0 {
      if auto { d.t = (d.t + dt).truncatingRemainder(dividingBy: LOOP) }
      let s = loopState(d.t)
      d.hourV = dt > 0 ? mix(d.hourV, (s.hour - d.hour) / dt, 0.5) : 0
      d.hour = s.hour
      d.from = s.from
      d.to = s.to
      d.at = s.at
      base = d.t
    } else {
      if d.dragging == 0 {
        let k = 120.0
        let c = 2 * 0.8 * sqrt(k)
        d.hourV += (-k * (d.hour - d.hourAim) - c * d.hourV) * dt
        d.hour += d.hourV * dt
      }
      if auto && d.dragging == 0 && now - d.last >= RESUME {
        if d.to != 2 {
          d.from = d.to
          d.to = 2
          d.at = now
        }
        d.hourAim = REST.hour
        if abs(d.hour - REST.hour) < 0.01 && now - d.at > 0.9 {
          d.mode = 0
          d.t = 7.6
        }
      }
    }
    let uv = uvAt(d.hour, d.from, d.to, base - d.at)
    let k = 170.0
    let c = 2 * 0.32 * sqrt(k)
    d.sizeV += (-k * (d.size - sizeFor(uv)) - c * d.sizeV) * dt
    d.size += d.sizeV * dt
    return base
  }
  static func takeOver(_ d: inout Driver, _ now: Double) {
    d.last = now
    if d.mode == 2 { return }
    d.mode = 2
    if d.at > -9 { d.at = now - (d.t - d.at) }
    d.hourAim = d.hour.rounded()
    d.hourV = 0
  }
  @discardableResult static func pickDay(_ d: inout Driver, _ day: Int, _ now: Double) -> Bool {
    if day == d.to { return false }
    d.from = d.to
    d.to = day
    d.at = now
    return true
  }
}
