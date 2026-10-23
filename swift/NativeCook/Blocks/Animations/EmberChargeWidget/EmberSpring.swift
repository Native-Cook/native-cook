import SwiftUI

/// Per-block port of the duration spring used by FlareNotifications and RN
/// Reanimated 4.5.1: solve stiffness from the energy at 1.5× the perceptual
/// duration, then let SwiftUI's physical Spring evaluate position/velocity.
struct EmberSpring {

  /// Current spring position.
  var value: Double

  /// Current velocity, carried into the next display step.
  var velocity: Double

  /// Endpoint in the same units as value.
  let target: Double

  /// Starting position, retained for overshoot clamping.
  private let initial: Double

  /// Initial total energy used by Reanimated’s relative settling threshold.
  private let energy: Double

  /// Solved physical stiffness for the requested perceptual duration.
  private let stiffness: Double

  /// Native analytic position/velocity evaluator for that physical response.
  private let spring: Spring

  /// Early release clamps overshoot; aim follows its critically damped response.
  private let clamped: Bool

  /// Latched when energy settles or the clamped spring crosses its endpoint.
  private(set) var finished = false

  init(
    from: Double, to: Double, duration: Double, damping: Double, velocity: Double = 0,
    clamped: Bool = false
  ) {
    value = from
    target = to
    initial = from
    self.clamped = clamped
    let v = (to > from && velocity < 0) || (to < from && velocity > 0) ? 0 : velocity
    self.velocity = v
    let x = from - to
    // Reanimated’s duration solve uses mass 4 and a relative energy threshold of 6e-9.
    let mass = 4.0
    let threshold = 6e-9
    let settle = duration * 1.5
    func difference(_ k: Double) -> Double {
      let w = sqrt(k / mass) * damping
      let e = exp(-w * settle)
      let p = (x + (v + x * w) * settle) * e
      let speed = -p * w + (v + x * w) * e
      let original = 0.5 * k * x * x + 0.5 * mass * v * v
      return original == 0
        ? 0 : (0.5 * k * p * p + 0.5 * mass * speed * speed) / original - threshold
    }
    var lo = Double.ulpOfOne
    var hi = 8000.0
    var k = (lo + hi) / 2
    for _ in 0..<100 {
      let diff = difference(k)
      if abs(diff) <= threshold * 1e-3 { break }
      if diff > 0 { lo = k } else { hi = k }
      k = (lo + hi) / 2
    }
    stiffness = k
    energy = 0.5 * k * x * x + 0.5 * mass * v * v
    spring =
      damping >= 1
      ? Spring(response: 2 * .pi / sqrt(k / mass), dampingRatio: 1)
      : Spring(
        mass: mass, stiffness: k, damping: 2 * damping * sqrt(k * mass), allowOverDamping: true)
  }

  mutating func step(_ dt: Double) {
    guard !finished else { return }
    let distance = target - value
    let v = velocity
    value += spring.value(target: distance, initialVelocity: v, time: min(dt, 0.064))
    velocity = spring.velocity(target: distance, initialVelocity: v, time: min(dt, 0.064))
    let overshot = clamped && (value < min(initial, target) || value > max(initial, target))
    let e = 0.5 * stiffness * pow(target - value, 2) + 2 * velocity * velocity
    if overshot || energy == 0 || e / energy <= 6e-9 {
      value = target
      velocity = 0
      finished = true
    }
  }
}
