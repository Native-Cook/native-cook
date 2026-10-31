import Foundation

// Reanimated duration-spring stiffness search and energy cutoff (mass 4, threshold 6e-9).
// Keeping the solver avoids mapping the reference's springs onto different SwiftUI defaults.
struct PuffyStickerAnimation {
  enum Curve {
    case quad
    case timing(Double, Double, Double, Double)
    case spring(Double, Double, Double)
  }
  struct Step {
    let end: Double, duration: Double, curve: Curve
    static func timing(_ end: Double, _ duration: Double, _ curve: Curve = .quad) -> Step {
      Step(end: end, duration: duration, curve: curve)
    }
    static func spring(_ end: Double, _ duration: Double, _ ratio: Double, _ velocity: Double = 0)
      -> Step
    { Step(end: end, duration: duration, curve: .spring(duration, ratio, velocity)) }
  }
  static let ease: Curve = .timing(0.77, 0, 0.175, 1)
  static let out: Curve = .timing(0.23, 1, 0.32, 1)
  var steps: [Step], from: Double, start: Double
  var stiffness = 0.0, springVelocity = 0.0, lastVelocity = 0.0
  init(_ from: Double, _ start: Double, _ steps: [Step]) {
    self.from = from
    self.start = start
    self.steps = steps
    configure()
  }
  mutating func configure() {
    guard let step = steps.first, case .spring(let duration, let zeta, let velocity) = step.curve
    else { return }
    let x0 = from - step.end
    springVelocity =
      ((step.end > from && velocity < 0) || (step.end < from && velocity > 0)) ? 0 : velocity
    let v0 = springVelocity
    if x0 == 0 && v0 == 0 {
      stiffness = 8000
      return
    }
    func diff(_ k: Double) -> Double {
      let t = duration * 1.5
      let omega = sqrt(k / 4) * zeta
      let e = exp(-omega * t)
      let displacement = (x0 + (v0 + x0 * omega) * t) * e
      let velocity = displacement * -omega + (v0 + x0 * omega) * e
      return (k * displacement * displacement + 4 * velocity * velocity)
        / (k * x0 * x0 + 4 * v0 * v0) - 6e-9
    }
    var lo = Double.ulpOfOne
    var hi = 8000.0
    let direction = diff(hi) >= diff(lo) ? 1.0 : -1.0
    var k = (hi + lo) / 2
    for _ in 0..<100 {
      if abs(diff(k)) <= 6e-12 { break }
      if diff(k) * direction < 0 { lo = k } else { hi = k }
      k = (hi + lo) / 2
    }
    stiffness = k
  }
  static func bezier(_ x: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double
  {
    func cubic(_ t: Double, _ a: Double, _ b: Double) -> Double {
      3 * (1 - t) * (1 - t) * t * a + 3 * (1 - t) * t * t * b + t * t * t
    }
    var lo = 0.0
    var hi = 1.0
    for _ in 0..<30 {
      let m = (lo + hi) / 2
      if cubic(m, x1, x2) < x { lo = m } else { hi = m }
    }
    return cubic((lo + hi) / 2, y1, y2)
  }
  mutating func sample(_ now: Double) -> (Double, Bool) {
    guard let step = steps.first else { return (from, true) }
    let t = max(0, now - start)
    var value = step.end
    var finished = false
    switch step.curve {
    case .quad:
      let u = min(1, t / step.duration)
      let progress = u < 0.5 ? 2 * u * u : 1 - pow(-2 * u + 2, 2) / 2
      value = from + (step.end - from) * progress
      finished = u >= 1
    case .timing(let x1, let y1, let x2, let y2):
      let u = min(1, t / step.duration)
      value = from + (step.end - from) * Self.bezier(u, x1, y1, x2, y2)
      finished = u >= 1
    case .spring(_, let zeta, _):
      let x0 = from - step.end
      let v0 = springVelocity
      let omega = sqrt(stiffness / 4)
      let omega1 = omega * sqrt(max(0, 1 - zeta * zeta))
      let envelope = exp(-zeta * omega * t)
      var displacement = 0.0
      var velocity = 0.0
      if zeta < 1 {
        let a = (v0 + zeta * omega * x0) / omega1
        displacement = envelope * (sin(omega1 * t) * a + x0 * cos(omega1 * t))
        velocity =
          -zeta * omega * displacement + envelope
          * (cos(omega1 * t) * (v0 + zeta * omega * x0) - omega1 * x0 * sin(omega1 * t))
      } else {
        displacement = exp(-omega * t) * (x0 + (v0 + omega * x0) * t)
        velocity = -omega * displacement + exp(-omega * t) * (v0 + omega * x0)
      }
      lastVelocity = velocity
      value = step.end + displacement
      let energy0 = stiffness * x0 * x0 + 4 * v0 * v0
      finished =
        energy0 == 0
        || (stiffness * displacement * displacement + 4 * velocity * velocity) / energy0 <= 6e-9
    }
    if finished {
      lastVelocity = 0
      from = step.end
      steps.removeFirst()
      start = now
      configure()
      return (from, steps.isEmpty)
    }
    return (value, false)
  }
}
