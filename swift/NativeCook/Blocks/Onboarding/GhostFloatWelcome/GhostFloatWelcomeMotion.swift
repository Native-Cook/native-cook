import Foundation

/// Literal analytic timeline from gfw-motion.ts; no native spring substitutes.
enum GhostFloatWelcomeMotion {
  typealias Theme = GhostFloatWelcomeTheme
  static let LOOP = 9.0
  static let RESUME = 2.0
  static let tau = Double.pi * 2
  enum T {
    static let blinks = [0.9, 3.95, 7.6]
    static let inhale = [1.4, 1.75], puff = 1.75, ring = [1.75, 2.4]
    static let tickPress = [4.45, 4.56], tick = [4.56, 4.86]
    static let button = [4.66, 5.0], sheen = [5.0, 5.55]
    static let happy = [4.7, 6.75], roll = [4.8, 5.55], lean = [5.0, 5.9]
    static let untick = [6.95, 7.25], buttonOff = [7.0, 7.3]
  }
  static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
  static func mix(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }
  static func easeOut(_ x: Double) -> Double { 1 - pow(1 - clamp01(x), 5) }
  static func easeInOut(_ x: Double) -> Double {
    let p = clamp01(x)
    return p < 0.5 ? 16 * pow(p, 5) : 1 - pow(-2 * p + 2, 5) / 2
  }
  static func smooth(_ x: Double) -> Double {
    let p = clamp01(x)
    return p * p * (3 - 2 * p)
  }
  static func kick(_ t: Double, _ period: Double, _ decay: Double) -> Double {
    t <= 0 ? 0 : 1.9 * exp(-t / decay) * sin(tau * t / period)
  }
  static func wobble(_ t: Double, _ period: Double, _ decay: Double) -> Double {
    t <= 0 ? 0 : exp(-t / decay) * cos(tau * t / period)
  }
  static let floats:
    [(nx: Double, ny: Double, px: Double, py: Double, ax: Double, ay: Double, rot: Double)] = [
      (1, 2, 0.1, 0.3, 4, 5.5, 4), (2, 1, 0.55, 0.05, 5, 6, 7), (1, 2, 0.3, 0.8, 6, 5, 9),
      (2, 1, 0.85, 0.4, 4.5, 6.5, 10), (1, 1, 0.2, 0.65, 5.5, 7, 8),
      (2, 2, 0.7, 0.15, 4, 5, 9), (1, 2, 0.45, 0.95, 6, 5, 6),
    ]
  struct Pose { var x: Double, y: Double, rot: Double }
  static func booOffset(_ x: Double, _ y: Double, _ t: Double, _ gain: Double) -> (
    x: Double, y: Double, k: Double
  ) {
    let ghost = Theme.bodies[0]
    let dx = x - ghost.x
    let dy = y - ghost.y
    let dist = hypot(dx, dy) == 0 ? 1 : hypot(dx, dy)
    if dist < 1 { return (0, 0, 0) }
    let k = kick(t - (T.puff + 0.03 + dist / 700), 1.3, 0.55)
    let amp = gain * max(18, 74 - dist * 0.16)
    return (dx / dist * amp * k, dy / dist * amp * k, k)
  }
  static func leanOffset(_ x: Double, _ y: Double, _ t: Double) -> (x: Double, y: Double) {
    let lean = sin(Double.pi * seg(t, T.lean[0], T.lean[1]))
    if lean <= 0 { return (0, 0) }
    let button = Theme.button
    let dx = button.x + button.w / 2 - x
    let dy = button.y - y
    let dist = hypot(dx, dy) == 0 ? 1 : hypot(dx, dy)
    let amp = 9 * lean * (1 - min(0.6, dist / 900))
    return (dx / dist * amp, dy / dist * amp)
  }
  static func bodyPose(_ i: Int, _ t: Double, _ amb: Double) -> Pose {
    let b = Theme.bodies[i]
    let f = floats[i]
    let phase = amb / LOOP
    var p = Pose(
      x: b.x + f.ax * sin(tau * (f.nx * phase + f.px)),
      y: b.y + f.ay * sin(tau * (f.ny * phase + f.py)),
      rot: f.rot * sin(tau * (phase + f.px + f.py)))
    if i > 0 {
      let o = booOffset(b.x, b.y, t, 1)
      let lean = leanOffset(b.x, b.y, t)
      p.x += o.x + lean.x
      p.y += o.y + lean.y
      p.rot += o.k * 26 * (i % 2 == 0 ? 1 : -1)
    }
    return p
  }
  static func starPose(_ i: Int, _ t: Double, _ amb: Double) -> Pose {
    let s = Theme.stars[i]
    let phase = amb / LOOP
    let index = Double(i)
    let o = booOffset(s.x, s.y, t, 0.55)
    return Pose(
      x: s.x + 3 * sin(tau * (phase + index * 0.17)) + o.x,
      y: s.y + 4 * sin(tau * (phase * 2 + index * 0.29)) + o.y,
      rot: 18 * sin(tau * (phase + index * 0.13)) + o.k * 40)
  }
  struct Frame {
    var t: Double, inhale: Double, puff: Double, ring: Double, wide: Double, blink: Double
    var happy: Double, roll: Double, hop: Double, press: Double, tick: Double, button: Double,
      sheen: Double
  }
  static func frameAt(_ t: Double) -> Frame {
    var blink = 0.0
    for start in T.blinks {
      let p = seg(t, start, start + 0.16)
      if p > 0 && p < 1 { blink = sin(Double.pi * p) }
    }
    let inhale = t >= T.inhale[0] && t < T.puff ? easeInOut(seg(t, T.inhale[0], T.puff)) : 0
    let puff = t >= T.puff ? wobble(t - T.puff, 0.34, 0.24) : 0
    let rp = seg(t, T.ring[0], T.ring[1])
    let happy =
      t < T.happy[1]
      ? smooth(seg(t, T.happy[0], T.happy[0] + 0.18))
      : 1 - smooth(seg(t, T.happy[1], T.happy[1] + 0.2))
    let rr = seg(t, T.roll[0], T.roll[1])
    let pp = seg(t, T.tickPress[0], T.tick[0] + 0.12)
    let sp = seg(t, T.sheen[0], T.sheen[1])
    return Frame(
      t: t, inhale: inhale, puff: puff, ring: rp > 0 && rp < 1 ? rp : -1,
      wide: t >= T.puff ? max(0, 1 - seg(t, T.puff + 0.3, T.puff + 0.9)) : 0, blink: blink,
      happy: happy,
      roll: 360 * easeInOut(rr), hop: 22 * sin(Double.pi * rr),
      press: pp > 0 && pp < 1 ? sin(Double.pi * pp) : 0,
      tick: t < T.untick[0]
        ? easeOut(seg(t, T.tick[0], T.tick[1])) : 1 - easeOut(seg(t, T.untick[0], T.untick[1])),
      button: t < T.buttonOff[0]
        ? easeInOut(seg(t, T.button[0], T.button[1]))
        : 1 - easeInOut(seg(t, T.buttonOff[0], T.buttonOff[1])),
      sheen: sp > 0 && sp < 1 ? sp : -1)
  }
  static func liveHome(_ j: Int, _ t: Double, _ amb: Double) -> (
    x: Double, y: Double, r: Double, mass: Double
  ) {
    if j < Theme.bodies.count {
      let p = bodyPose(j, t, amb)
      return (p.x, p.y, Theme.bodies[j].r, 1)
    }
    let i = j - Theme.bodies.count
    let p = starPose(i, t, amb)
    return (p.x, p.y, Theme.stars[i].s, 0.4)
  }
  static func follow(_ cur: Double, _ aim: Double, _ k: Double, _ dt: Double) -> Double {
    cur + (aim - cur) * (1 - exp(-dt * k))
  }
}
