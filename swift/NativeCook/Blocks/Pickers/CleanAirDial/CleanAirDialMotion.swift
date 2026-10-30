import Foundation

// The exact cad-motion.ts timeline and integrator; time and all spring state are in seconds.
enum CleanAirDialMotion {
  static let LOOP = 9.0
  static let RESUME = 2.0
  static let F_HIGH = 0.62
  static let TAU = Double.pi * 2
  static let STEP = 0.01
  static let levels = CleanAirDialTheme.LEVELS
  enum T {
    static let up = [1.0, 1.7]
    static let pull = [1.8, 4.4]
    static let down = [5.0, 5.6]
    static let back = [5.8, 8.4]
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
  static func easeOutBack(_ x: Double) -> Double {
    let p = clamp01(x)
    return 1 + 2.4 * pow(p - 1, 3) + 1.4 * pow(p - 1, 2)
  }
  static func omegaAt(_ g: Double) -> Double {
    if g <= levels[0].ticks { return levels[0].omega * max(0, g) / levels[0].ticks }
    for i in 0..<(levels.count - 1) {
      let a = levels[i]
      let b = levels[i + 1]
      if g <= b.ticks { return mix(a.omega, b.omega, (g - a.ticks) / (b.ticks - a.ticks)) }
    }
    return levels[levels.count - 1].omega
  }
  static func levelAt(_ g: Double) -> Int {
    var best = 0
    for i in 1..<levels.count {
      if abs(g - levels[i].ticks) < abs(g - levels[best].ticks) { best = i }
    }
    return best
  }
  static func loopG(_ t: Double) -> Double {
    if t < T.down[0] {
      return mix(levels[1].ticks, levels[2].ticks, easeOutBack(seg(t, T.up[0], T.up[1])))
    }
    return mix(levels[2].ticks, levels[1].ticks, easeInOut(seg(t, T.down[0], T.down[1])))
  }
  static func loopF(_ t: Double) -> Double {
    if t < T.back[0] { return F_HIGH * easeInOut(seg(t, T.pull[0], T.pull[1])) }
    return F_HIGH * (1 - easeInOut(seg(t, T.back[0], T.back[1])))
  }
  // Midpoint integration and whole-turn normalization preserve the air phase at the loop seam.
  static let PHI: [Double] = {
    var out = [0.0]
    var acc = 0.0
    for i in 1...Int((LOOP / STEP).rounded()) {
      acc += omegaAt(loopG((Double(i) - 0.5) * STEP)) * STEP
      out.append(acc)
    }
    let turns = max(1, (acc / TAU).rounded())
    return out.map { $0 * turns * TAU / acc }
  }()
  static func loopPhi(_ t: Double) -> Double {
    let x = max(0, min(LOOP, t)) / STEP
    let i = min(PHI.count - 2, Int(floor(x)))
    return mix(PHI[i], PHI[i + 1], x - Double(i))
  }
  static func pmOf(_ f: Double) -> Double { max(0, 12 - 13 * f) }
  struct Frame {
    var amb: Double, g: Double, phi: Double, f: Double, ret: Double
    var power = 1.0, timer = 0.0, timerAt = 10.0, scenes = 1.0, auto = 0.0
    var drag = -1.0, press = -1, pressK = 0.0, hush = 0.0
  }
  static func frameAt(_ t: Double, _ phi0: Double = 0) -> Frame {
    Frame(amb: t, g: loopG(t), phi: loopPhi(t) + phi0, f: loopF(t), ret: t >= T.down[0] ? 1 : 0)
  }
  struct Driver {
    var t = 0.0, mode = 0, last = -10.0, amb = 0.0, phi0 = 0.0
    var g = levels[1].ticks, gv = 0.0, level = 1, dragging = 0, dragG = 0.0
    var f = 0.0, ret = 0.0, phi = 0.0
    var power = true, powerAt = -10.0, timer = false, timerAt = -10.0
    var scenes = true, scenesAt = -10.0, auto = false, autoAt = -10.0
    var press = -1, pressAt = -10.0, releaseAt = -10.0, pressLast = 0, homing = 0
  }
  static func takeOver(_ d: inout Driver, _ now: Double) {
    d.last = now
    d.homing = 0
    if d.mode == 2 { return }
    let f = frameAt(d.t, d.phi0)
    d.mode = 2
    d.g = f.g
    d.gv = 0
    d.level = d.t >= T.up[0] && d.t < T.down[0] ? 2 : 1
    d.f = f.f
    d.ret = f.ret
    d.phi = f.phi
    d.power = true
    d.timer = false
    d.scenes = true
    d.auto = false
  }
  static func stepDriver(_ d: inout Driver, _ dt: Double, _ now: Double, _ auto: Bool) {
    d.amb = (d.amb + dt).truncatingRemainder(dividingBy: LOOP)
    if d.mode == 0 {
      if auto { d.t = (d.t + dt).truncatingRemainder(dividingBy: LOOP) }
      return
    }
    if d.auto && d.power && d.dragging == 0 { d.level = pmOf(d.f) > 7 ? 2 : 1 }
    if auto && d.dragging == 0 && d.press < 0 && now - d.last >= RESUME {
      if d.homing == 0 {
        d.homing = 1
        d.level = 1
        if !d.power {
          d.power = true
          d.powerAt = now
        }
        if d.timer {
          d.timer = false
          d.timerAt = now
        }
        if !d.scenes {
          d.scenes = true
          d.scenesAt = now
        }
        if d.auto {
          d.auto = false
          d.autoAt = now
        }
      }
    }
    let target = d.dragging == 1 ? d.dragG : d.power ? levels[d.level].ticks : 0
    if d.dragging == 1 {
      d.g += (target - d.g) * min(1, dt * 22)
      d.gv = 0
    } else {
      let k = 140.0
      let c = 2 * 0.62 * sqrt(140.0)
      d.gv += (-k * (d.g - target) - c * d.gv) * dt
      d.g += d.gv * dt
    }
    let fTarget = d.power ? levels[levelAt(max(0, d.g))].target : -0.5
    let om = d.power ? omegaAt(max(0, d.g)) : 0
    if fTarget > d.f {
      d.f = min(fTarget, d.f + (fTarget - d.f + 0.05) * 0.24 * om * dt)
      d.ret = 0
    } else if fTarget < d.f {
      d.f = max(fTarget, d.f - (d.f - fTarget + 0.05) * 0.32 * dt)
      d.ret = 1
    }
    d.phi += om * dt
    if d.homing == 1 && abs(d.g - levels[1].ticks) < 0.05 && abs(d.gv) < 0.2 && abs(d.f) < 0.004 {
      d.mode = 0
      d.t = 0
      d.phi0 = d.phi.truncatingRemainder(dividingBy: TAU)
      d.homing = 0
    }
  }
  static func frameOf(_ d: Driver, _ now: Double) -> Frame {
    if d.mode == 0 { return frameAt(d.t, d.phi0) }
    let pressK =
      d.press >= 0 ? smooth((now - d.pressAt) / 0.1) : 1 - smooth((now - d.releaseAt) / 0.16)
    func fade(_ on: Bool, _ at: Double, _ dur: Double) -> Double {
      on ? smooth((now - at) / dur) : 1 - smooth((now - at) / dur)
    }
    return Frame(
      amb: d.amb, g: d.g, phi: d.phi, f: d.f, ret: d.ret,
      power: fade(d.power, d.powerAt, 0.35), timer: fade(d.timer, d.timerAt, 0.25),
      timerAt: now - d.timerAt, scenes: fade(d.scenes, d.scenesAt, 0.25),
      auto: fade(d.auto, d.autoAt, 0.25), drag: d.dragging == 1 ? d.dragG : -1,
      press: d.press >= 0 ? d.press : d.pressLast, pressK: pressK,
      hush: 1 - fade(d.power, d.powerAt, 0.35))
  }
}
