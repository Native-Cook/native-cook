import Foundation

/// The easing curves the block uses.
enum BoosterPackEasing {
  case linear
  /// `1 - (1 - t)²`.
  case outQuad
  /// A CSS-style cubic Bézier through (x1, y1) and (x2, y2).
  case bezier(Double, Double, Double, Double)

  static let easeOut = BoosterPackEasing.bezier(0.23, 1, 0.32, 1)

  func callAsFunction(_ t: Double) -> Double {
    switch self {
    case .linear:
      return t
    case .outQuad:
      return 1 - (1 - t) * (1 - t)
    case let .bezier(x1, y1, x2, y2):
      if t <= 0 { return 0 }
      if t >= 1 { return 1 }
      func curve(_ s: Double, _ a: Double, _ b: Double) -> Double {
        let inverse = 1 - s
        return 3 * inverse * inverse * s * a + 3 * inverse * s * s * b + s * s * s
      }
      var low = 0.0
      var high = 1.0
      var s = t
      for _ in 0..<24 {
        let x = curve(s, x1, x2)
        if abs(x - t) < 1e-6 { break }
        if x < t { low = s } else { high = s }
        s = (low + high) / 2
      }
      return curve(s, y1, y2)
    }
  }
}

/// One animated number on the block's clock: set it, or run a timed or spring animation on
/// it, as the shared values of the React Native source do. `step` is called once per frame
/// with the frame's time in milliseconds; a completion runs only when its animation finished.
final class BoosterPackAnimatedValue {
  private(set) var value: Double

  private enum Motion {
    case timing(from: Double, to: Double, start: Double, duration: Double, easing: BoosterPackEasing)
    case spring(BoosterPackSpring)
  }

  private var motion: Motion?
  private var completion: (() -> Void)?

  init(_ value: Double) { self.value = value }

  var isAnimating: Bool { motion != nil }

  /// Stops any animation and holds the current value.
  func cancel() {
    motion = nil
    completion = nil
  }

  func set(_ next: Double) {
    cancel()
    value = next
  }

  func timing(
    to target: Double, duration: Double, easing: BoosterPackEasing = .linear, delay: Double = 0,
    now: Double, completion: (() -> Void)? = nil
  ) {
    motion = .timing(from: value, to: target, start: now + delay, duration: duration, easing: easing)
    self.completion = completion
  }

  /// A spring described by how long it appears to take and its damping ratio.
  func spring(
    to target: Double, duration: Double, dampingRatio: Double, velocity: Double = 0,
    overshootClamping: Bool = false, now: Double, completion: (() -> Void)? = nil
  ) {
    var carried = velocity
    if case let .spring(previous)? = motion { carried += previous.velocity }
    // Inertia is only preserved when it already points toward the target.
    if (target > value && carried < 0) || (target < value && carried > 0) { carried = 0 }
    motion = .spring(BoosterPackSpring(
      from: value, to: target, velocity: carried, configuredVelocity: velocity, duration: duration,
      dampingRatio: dampingRatio, overshootClamping: overshootClamping, now: now))
    self.completion = completion
  }

  func step(_ now: Double) {
    guard let motion else { return }
    var finished = false
    switch motion {
    case let .timing(from, to, start, duration, easing):
      if now < start { return }
      let progress = duration <= 0 ? 1 : (now - start) / duration
      if progress >= 1 {
        value = to
        finished = true
      } else {
        value = from + (to - from) * easing(progress)
      }
    case var .spring(spring):
      finished = spring.step(now)
      value = spring.current
      if !finished { self.motion = .spring(spring) }
    }
    if finished {
      self.motion = nil
      let done = completion
      completion = nil
      done?()
    }
  }
}

/// A damped spring whose stiffness is solved from a perceived duration: it settles in one and
/// a half times that, to within a small fraction of its starting energy.
struct BoosterPackSpring {
  private(set) var current: Double
  private(set) var velocity: Double
  private let toValue: Double
  private let startValue: Double
  private let zeta: Double
  private let omega0: Double
  private let omega1: Double
  private let stiffness: Double
  private let initialEnergy: Double
  private let overshootClamping: Bool
  private var lastTimestamp: Double

  private static let mass = 4.0
  private static let energyThreshold = 6e-9

  init(
    from: Double, to: Double, velocity: Double, configuredVelocity: Double, duration: Double,
    dampingRatio: Double, overshootClamping: Bool, now: Double
  ) {
    current = from
    toValue = to
    self.velocity = velocity
    self.overshootClamping = overshootClamping
    lastTimestamp = now
    let x0 = from - to
    startValue = x0
    zeta = dampingRatio
    stiffness = Self.stiffness(x0: x0, v0: velocity, duration: duration, zeta: dampingRatio)
    omega0 = sqrt(stiffness / Self.mass)
    omega1 = dampingRatio < 1 ? omega0 * sqrt(1 - dampingRatio * dampingRatio) : 0
    initialEnergy = Self.energy(x0, configuredVelocity, stiffness)
  }

  private static func energy(_ displacement: Double, _ velocity: Double, _ stiffness: Double) -> Double {
    0.5 * stiffness * displacement * displacement + 0.5 * mass * velocity * velocity
  }

  /// The stiffness whose envelope has decayed to the energy threshold when the spring should
  /// have settled, found by bisection.
  private static func stiffness(x0: Double, v0: Double, duration: Double, zeta: Double) -> Double {
    func energyDifference(_ stiffness: Double) -> Double {
      let settlingDuration = duration * 1.5 / 1000
      let omega0 = sqrt(stiffness / mass) * zeta
      let envelope = exp(-omega0 * settlingDuration)
      let xtk = (x0 + (v0 + x0 * omega0) * settlingDuration) * envelope
      let vtk = xtk * -omega0 + (v0 + x0 * omega0) * envelope
      let e0 = energy(x0, v0, stiffness)
      return energy(xtk, vtk, stiffness) / e0 - energyThreshold
    }
    var low = Double.ulpOfOne
    var high = 8e3
    let rising: Double = energyDifference(high) >= energyDifference(low) ? 1 : -1
    let precision = energyThreshold * 1e-3
    var middle = (low + high) / 2
    var iterations = 100
    while abs(energyDifference(middle)) > precision && iterations > 0 {
      iterations -= 1
      if energyDifference(middle) * rising < 0 { low = middle } else { high = middle }
      middle = (low + high) / 2
    }
    return middle
  }

  /// Advances to `now`; true once the spring has come to rest on its target.
  mutating func step(_ now: Double) -> Bool {
    let t = min(max(now - lastTimestamp, 0), 64) / 1000
    lastTimestamp = now
    let v0 = velocity
    let x0 = current - toValue

    if zeta < 1 {
      let sin1 = sin(omega1 * t)
      let cos1 = cos(omega1 * t)
      let envelope = exp(-zeta * omega0 * t)
      let fragment = envelope * (sin1 * ((v0 + zeta * omega0 * x0) / omega1) + x0 * cos1)
      current = toValue + fragment
      velocity = -zeta * omega0 * fragment + envelope * (cos1 * (v0 + zeta * omega0 * x0) - omega1 * x0 * sin1)
    } else {
      let envelope = exp(-omega0 * t)
      current = toValue + envelope * (x0 + (v0 + omega0 * x0) * t)
      velocity = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * t) + envelope * (v0 + omega0 * x0)
    }

    var terminating = false
    if overshootClamping {
      let leftBound = startValue >= 0 ? toValue : toValue + startValue
      let rightBound = leftBound + abs(startValue)
      if current < leftBound || current > rightBound { terminating = true }
    }
    let currentEnergy = Self.energy(toValue - current, velocity, stiffness)
    if initialEnergy == 0 || currentEnergy / initialEnergy <= Self.energyThreshold { terminating = true }
    if terminating {
      velocity = 0
      current = toValue
    }
    return terminating
  }
}
