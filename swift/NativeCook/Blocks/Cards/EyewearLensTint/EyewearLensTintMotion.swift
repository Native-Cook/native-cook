import Foundation

enum EyewearLensTintMotion {
  static let loop = 10.0
  static let pour = 1.4
  static let drops: [(at: Double, tint: Int, lens: Int)] = [(0.5, 1, 1), (3.7, 3, 0), (6.7, 0, 1)]
  static func clamp(_ v: Double) -> Double { min(1, max(0, v)) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
  static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
  static func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
  static func mix(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }
  static func bezier(_ x: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
    if x <= 0 || x >= 1 { return clamp(x) }
    func at(_ t: Double, _ a: Double, _ b: Double) -> Double { 3 * (1 - t) * (1 - t) * t * a + 3 * (1 - t) * t * t * b + t * t * t }
    var lo = 0.0, hi = 1.0
    for _ in 0..<28 { let mid = (lo + hi) / 2; if at(mid, x1, x2) < x { lo = mid } else { hi = mid } }
    return at((lo + hi) / 2, y1, y2)
  }
  struct Frame {
    var from = 0, to = 0, lens = 1, carryTint = 0
    var pour = 0.0, carry = -1.0, glare = 0.0, finger = 0.0
    var carryX = -100.0, carryY = -100.0, carryOn = 0.0
    var stretch = 0.0, heading = 0.0
    var time = 0.0
    var shownTint: Int { pour > 0.5 ? to : from }
  }
  static func at(_ t: Double) -> Frame {
    var s = Frame(); s.time = t
    for (i, d) in drops.enumerated() {
      if t < d.at - 0.15 { break }
      s.from = i == 0 ? 0 : drops[i - 1].tint
      s.to = d.tint; s.lens = d.lens
      let up = d.at + 0.8
      if t < up { s.carry = easeInOut(seg(t, d.at, up)); s.carryTint = d.tint; s.pour = 0 }
      else { s.pour = seg(t, up, up + pour) }
      s.finger = max(s.finger, easeOut(seg(t, d.at - 0.15, d.at)) * (1 - easeOut(seg(t, up, up + 0.16))))
    }
    if s.pour >= 1 { s.from = s.to; s.pour = 0 }
    s.glare = sin(.pi * 2 * easeInOut(seg(t, 9, 9.9))) * 0.8
    return s
  }
  static func position(_ f: Frame, _ l: EyewearLensTintTheme.Layout) -> Frame {
    var f = f
    if f.carry >= 0 {
      let a = l.drop(f.carryTint), b = l.pour(f.lens), u = f.carry
      f.carryX = mix(a.x, b.x, u)
      f.carryY = mix(a.y, b.y, u) - sin(.pi * u) * 60
      f.carryOn = 1
    } else {
      f.carryOn = 0
      if f.finger > 0 { f.carryX = l.pour(f.lens).x; f.carryY = l.pour(f.lens).y }
    }
    return f
  }

  // A pin replays from rest on a fixed clock, independent of launch/capture delays.
  // Carry deformation stays off while pinned, as in the reference's settled debug frame.
  static func replay(_ time: Double, _ l: EyewearLensTintTheme.Layout) -> Frame {
    var previous = Frame()
    let steps = Int((max(0, time) * 60).rounded())
    for step in 0...steps {
      let t = Double(step) / 60
      var f = position(at(t.truncatingRemainder(dividingBy: loop)), l)
      if f.carry < 0 {
        f.carryX = previous.carryX; f.carryY = previous.carryY; f.carryTint = previous.carryTint
      }
      f.time = t.truncatingRemainder(dividingBy: 600)
      previous = f
    }
    return previous
  }

  // Reanimated's duration spring: mass 4, perceptual duration × 1.5, energy threshold 6e-9.
  // Resolve stiffness with the same bisection, including the release velocity.
  struct Spring {
    let target: Double, damping: Double, frequency: Double, initialEnergy: Double
    var value: Double, velocity: Double
    init(from: Double, to: Double, velocity: Double, duration: Double, damping: Double) {
      target = to; value = from; self.damping = damping
      let x = from - to
      let v = x * velocity > 0 ? 0 : velocity
      self.velocity = v
      let settling = duration * 1.5
      func fraction(_ stiffness: Double) -> Double {
        let w = sqrt(stiffness / 4) * damping
        let envelope = exp(-w * settling)
        let p = (x + (v + x * w) * settling) * envelope
        let speed = -w * p + (v + x * w) * envelope
        return (stiffness * p * p + 4 * speed * speed) / (stiffness * x * x + 4 * v * v) - 6e-9
      }
      var lo = Double.ulpOfOne, hi = 8000.0, stiffness = 4000.0
      let direction = fraction(hi) >= fraction(lo) ? 1.0 : -1.0
      for _ in 0..<100 {
        let error = fraction(stiffness)
        if !(abs(error) > 6e-12) { break }
        if error * direction < 0 { lo = stiffness } else { hi = stiffness }
        stiffness = (lo + hi) / 2
      }
      frequency = sqrt(stiffness / 4)
      initialEnergy = x * x + velocity * velocity / (frequency * frequency)
    }
    mutating func step(_ dt: Double) -> Bool {
      let t = min(0.064, max(0, dt)), w = frequency, z = damping
      let wd = w * sqrt(1 - z * z), x = value - target
      let envelope = exp(-z * w * t), sine = sin(wd * t), cosine = cos(wd * t)
      let p = envelope * (sine * (velocity + z * w * x) / wd + x * cosine)
      velocity = -z * w * p + envelope * (cosine * (velocity + z * w * x) - wd * x * sine)
      value = target + p
      let done = initialEnergy == 0 || (p * p + velocity * velocity / (w * w)) / initialEnergy <= 6e-9
      if done { value = target; velocity = 0 }
      return done
    }
  }
}
