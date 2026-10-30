import Foundation

// Events stay on loop time until a finger takes over; then they use the touch clock.
enum FrostCardControlsMotion {
  static let LOOP = 9.0
  static let RESUME = 2.0
  static let GROW = 1.25
  static let MELT = 1.15
  static let DRIPS = 1.3
  static let SHRED = 1.15
  static let REISSUE = 0.9
  struct Events {
    var freeze = -99.0
    var cx = 46.0
    var cy = 186.0
    var melt = -99.0
    var shred = -99.0
    var reissue = -99.0
    var out = -99.0
  }
  static let AUTO = Events(freeze: 1.12, melt: 3.72, shred: 5.45, reissue: 6.75)
  static let PRESSES: [(Double, Double, Int)] = [(1, 1.12, 0), (3.6, 3.72, 0), (5.3, 5.42, 2)]
  struct Frame {
    var frost: Double
    var melt: Double
    var drips: Double
    var cx: Double
    var cy: Double
    var frozenLabel: Double
    var spin: Double
    var shred: Double
    var card: Double
    var drop: Double
    var shine: Double
    var out: Double
    var press: [Double]
  }
  struct Driver {
    var t = 0.0
    var mode = 0
    var last = -10.0
    var amb = 0.0
    var ev = Events()
    var frozen = false
    var busy = -1.0
    var press = [0.0, 0, 0]
    var pressAim = [0.0, 0, 0]
    var tiltX = 0.0
    var tiltY = 0.0
    var aimX = 0.0
    var aimY = 0.0
  }
  static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
  static func mix(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }
  static func easeOut(_ x: Double) -> Double { 1 - pow(1 - clamp01(x), 5) }
  static func easeIn(_ x: Double) -> Double { pow(clamp01(x), 2) }
  static func easeInOut(_ x: Double) -> Double {
    let p = clamp01(x)
    return p < 0.5 ? 16 * pow(p, 5) : 1 - pow(-2 * p + 2, 5) / 2
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
  static func frameFor(_ t: Double, _ e: Events, _ presses: [Double]) -> Frame {
    let fz = t - e.freeze
    let ml = t - e.melt
    let meltOn = e.melt > e.freeze && ml >= 0
    let frozen = fz >= 0 && !(meltOn && ml >= MELT)
    let frost = fz >= 0 ? 1.2 * easeOut(fz / GROW) : 0
    let melt = meltOn ? easeInOut(ml / MELT) : 0
    let drips = meltOn && ml > 0.15 && ml < 0.15 + DRIPS ? (ml - 0.15) / DRIPS : -1
    let sh = t - e.shred
    let shred = e.shred > -98 && sh >= 0 && sh < SHRED ? sh / SHRED : -1
    let re = t - e.reissue
    let reOn = e.reissue > -98 && re >= 0
    let gone = e.shred > -98 && sh >= SHRED && !(reOn && e.reissue > e.shred)
    let flyOut = e.out > -98 ? easeIn(seg(t, e.out, e.out + 0.4)) : 0
    let shine = reOn && re > 0.55 && re < 1.15 ? (re - 0.55) / 0.6 : -1
    return Frame(
      frost: frozen || (meltOn && ml < MELT) ? frost : 0, melt: melt, drips: drips,
      cx: e.cx, cy: e.cy,
      frozenLabel: frozen ? smooth(seg(fz, 0.2, 0.5)) * (meltOn ? 1 - smooth(ml / 0.3) : 1) : 0,
      spin: fz >= 0 ? 360 * easeOut(fz / 0.8) - (meltOn ? 360 * easeOut(ml / 0.8) : 0) : 0,
      shred: shred, card: gone || (reOn && re < 0) ? 0 : 1,
      drop: reOn ? spring(re, 0.55, 0.7) : 1, shine: shine, out: flyOut, press: presses)
  }
  static func frameAt(_ t: Double) -> Frame {
    var press = [0.0, 0, 0]
    for (a, b, index) in PRESSES {
      let k = seg(t, a, b + 0.14)
      if k > 0 && k < 1 { press[index] = sin(Double.pi * k) }
    }
    return frameFor(t, AUTO, press)
  }
  static func follow(_ cur: Double, _ aim: Double, _ k: Double, _ dt: Double) -> Double {
    cur + (aim - cur) * (1 - exp(-dt * k))
  }
  static func stepDriver(_ d: inout Driver, _ dt: Double, _ now: Double, _ auto: Bool) {
    d.amb = (d.amb + dt).truncatingRemainder(dividingBy: LOOP)
    for i in 0..<3 {
      d.press[i] = follow(d.press[i], d.pressAim[i], d.pressAim[i] > 0 ? 40 : 16, dt)
    }
    d.tiltX = follow(d.tiltX, d.aimX, 10, dt)
    d.tiltY = follow(d.tiltY, d.aimY, 10, dt)
    if d.mode == 0 {
      if auto { d.t = (d.t + dt).truncatingRemainder(dividingBy: LOOP) }
      return
    }
    if auto && now - d.last >= RESUME && now > d.busy {
      d.mode = 0
      d.t = d.frozen ? 2.6 : 8.2
    }
  }
  static func takeOver(_ d: inout Driver, _ now: Double) {
    d.last = now
    if d.mode == 2 { return }
    let t = d.t
    d.mode = 2
    let off = now - t
    d.frozen = t >= AUTO.freeze && t < AUTO.melt + MELT * 0.5
    d.ev = Events(freeze: d.frozen ? min(AUTO.freeze, t - GROW) + off : -99)
    if t >= AUTO.shred && t < AUTO.reissue + REISSUE + 0.4 {
      d.ev.shred = AUTO.shred + off
      d.ev.reissue = AUTO.reissue + off
      d.busy = d.ev.reissue + REISSUE + 0.4
    }
  }
  static func freezeAt(_ d: inout Driver, _ now: Double, _ cx: Double, _ cy: Double) {
    d.ev.freeze = now
    d.ev.cx = cx
    d.ev.cy = cy
    d.ev.melt = -99
    d.frozen = true
  }
  static func meltNow(_ d: inout Driver, _ now: Double) {
    d.ev.melt = now
    d.frozen = false
  }
  static func shredNow(_ d: inout Driver, _ now: Double) {
    d.ev.shred = now
    d.ev.reissue = now + SHRED + 0.2
    d.ev.out = -99
    d.ev.freeze = -99
    d.ev.melt = -99
    d.frozen = false
    d.busy = now + SHRED + 0.2 + REISSUE + 0.4
  }
  static func reissueNow(_ d: inout Driver, _ now: Double) {
    d.ev.out = now
    d.ev.reissue = now + 0.42
    d.ev.shred = -99
    d.ev.freeze = -99
    d.ev.melt = -99
    d.frozen = false
    d.busy = now + 0.42 + REISSUE + 0.4
  }
  static func liveFrame(_ d: Driver, _ now: Double) -> Frame {
    if d.mode == 0 {
      var f = frameAt(d.t)
      for i in 0..<3 { f.press[i] = max(f.press[i], d.press[i]) }
      return f
    }
    var f = frameFor(now, d.ev, d.press)
    if d.ev.out > -98 && now >= d.ev.reissue { f.out = 0 }
    return f
  }
}
