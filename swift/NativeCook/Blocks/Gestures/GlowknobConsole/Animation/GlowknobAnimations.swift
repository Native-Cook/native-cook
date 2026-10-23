import Foundation
import SwiftUI

// Ports of Reanimated 4.5.1's animation objects
// (react-native-reanimated/src/animation/{timing,spring/*,decay/*,delay,util}.ts).
// Each keeps the same fields and the same onStart/onFrame logic, including the
// interactions with the animation it replaces (timing continuity, spring
// velocity carry-over, delay running the previous animation meanwhile), and
// the reduce-motion decoration from `defineAnimation`.
//
// The curve math is Apple's wherever a native type reproduces Reanimated's:
// timing easings are SwiftUI `UnitCurve`s (`GlowknobEasing.swift`) and springs are
// stepped with SwiftUI's `Spring` (`value`/`velocity(target:initialVelocity:
// time:)`). What stays ported is what has no native counterpart: the
// duration → stiffness solve, the energy rest rule and overshoot clamping
// around the spring, and glowknobWithDecay (rigid and rubber band).

enum GlowknobReduceMotion {
  case system, always, never
}

/// `getReduceMotionForAnimation(config)`: nil when not configured, so a parent
/// (withDelay) can hand down its own setting.
@MainActor
func glowknobReduceMotionFor(_ config: GlowknobReduceMotion?) -> Bool? {
  guard let config else { return nil }
  switch config {
  case .system: return GlowknobUIRuntime.shared.isReduceMotion
  case .always: return true
  case .never: return false
  }
}

@MainActor
class GlowknobAnimationObject {
  /// Before onStart this holds the target (built-in animations), so setting a
  /// value to an animation of its current value is a no-op (valueSetter).
  var current: Double?
  var callback: ((Bool) -> Void)?
  var finished = false
  var cancelled = false
  var timestamp: Double = 0
  var reduceMotion: Bool?
  /// Set by the reduce-motion decoration: every frame finishes at once.
  private var skipFrames = false

  var isHigherOrder: Bool { false }
  /// `animation.toValue` (nil for decay and delay).
  var toValue: Double? { nil }
  /// `animation.velocity` as another animation reads it (undefined → nil).
  var carriedVelocity: Double? { nil }

  init(callback: ((Bool) -> Void)?) {
    self.callback = callback
  }

  /// The decorated `onStart` from `defineAnimation` (numbers only).
  final func start(value: Double, now: Double, previous: GlowknobAnimationObject?) {
    if isHigherOrder {
      if reduceMotion == nil { reduceMotion = GlowknobUIRuntime.shared.isReduceMotion }
      onStart(value: value, now: now, previous: previous)
      return
    }
    if reduceMotion == nil { reduceMotion = GlowknobUIRuntime.shared.isReduceMotion }
    if reduceMotion == true {
      if let toValue { current = toValue } else { onStart(value: value, now: now, previous: previous) }
      resetStartTime()
      skipFrames = true
      return
    }
    onStart(value: value, now: now, previous: previous)
  }

  final func frame(_ now: Double) -> Bool {
    if skipFrames { return true }
    return onFrame(now)
  }

  func onStart(value: Double, now: Double, previous: GlowknobAnimationObject?) {}
  func onFrame(_ now: Double) -> Bool { true }
  func resetStartTime() {}
}

// MARK: - withTiming

final class GlowknobTimingAnimation: GlowknobAnimationObject {
  let target: Double
  let duration: Double
  let easingFn: GlowknobEasingFunction
  var startTime: Double = 0
  var startValue: Double = 0

  init(_ toValue: Double, duration: Double = 300, easing: @escaping GlowknobEasingFunction = GlowknobEasing.inOutQuad,
       reduceMotion: GlowknobReduceMotion? = nil, callback: ((Bool) -> Void)? = nil) {
    target = toValue
    self.duration = duration
    easingFn = easing
    super.init(callback: callback)
    current = toValue
    self.reduceMotion = glowknobReduceMotionFor(reduceMotion)
  }

  override var toValue: Double? { target }

  override func onStart(value: Double, now: Double, previous: GlowknobAnimationObject?) {
    if let previous = previous as? GlowknobTimingAnimation, previous.target == target, previous.startTime != 0 {
      // to maintain continuity of timing animations we check if we are starting
      // new timing over the old one with the same parameters. If so, we want
      // to copy animation timeline properties
      startTime = previous.startTime
      startValue = previous.startValue
    } else {
      startTime = now
      startValue = value
    }
    current = value
  }

  override func onFrame(_ now: Double) -> Bool {
    let runtime = now - startTime
    if runtime >= duration {
      // reset startTime to avoid reusing finished animation config in `start` method
      startTime = 0
      current = target
      return true
    }
    let progress = easingFn(runtime / duration)
    current = startValue + (target - startValue) * progress
    return false
  }

  override func resetStartTime() { startTime = 0 }
}

// MARK: - withSpring

struct GlowknobSpringConfig {
  var stiffness: Double?
  var damping: Double?
  var mass: Double?
  var duration: Double?
  var dampingRatio: Double?
  var velocity: Double?
  var overshootClamping: Bool?
  var energyThreshold: Double?
  var clamp: (min: Double?, max: Double?)?
  var reduceMotion: GlowknobReduceMotion?

  static func physics(stiffness: Double, damping: Double, mass: Double,
                      velocity: Double? = nil, overshootClamping: Bool? = nil) -> GlowknobSpringConfig {
    GlowknobSpringConfig(stiffness: stiffness, damping: damping, mass: mass, velocity: velocity,
                 overshootClamping: overshootClamping)
  }

  static func duration(_ duration: Double, dampingRatio: Double) -> GlowknobSpringConfig {
    GlowknobSpringConfig(duration: duration, dampingRatio: dampingRatio)
  }
}

/// The merged config (`DefaultSpringConfig & SpringConfigInner`).
struct GlowknobResolvedSpringConfig {
  // GentleSpringConfig + GentleSpringConfigWithDuration defaults.
  var stiffness = 900.0
  var damping = 120.0
  var mass = 4.0
  var duration = 550.0
  var dampingRatio = 1.0
  var overshootClamping = false
  var energyThreshold = 6e-9
  var velocity = 0.0
  var clamp: (min: Double?, max: Double?)?
  var useDuration = false
  var skipAnimation = false

  init(_ user: GlowknobSpringConfig?) {
    useDuration = (user?.duration ?? 0) != 0 || (user?.dampingRatio ?? 0) != 0
    if let user {
      if let v = user.stiffness { stiffness = v }
      if let v = user.damping { damping = v }
      if let v = user.mass { mass = v }
      if let v = user.duration { duration = v }
      if let v = user.dampingRatio { dampingRatio = v }
      if let v = user.overshootClamping { overshootClamping = v }
      if let v = user.energyThreshold { energyThreshold = v }
      if let v = user.velocity { velocity = v }
      clamp = user.clamp
    }
    skipAnimation = !GlowknobSpringMath.checkIfConfigIsValid(self)
    if duration == 0 { skipAnimation = true }
  }
}

enum GlowknobSpringMath {
  static func checkIfConfigIsValid(_ c: GlowknobResolvedSpringConfig) -> Bool {
    var ok = true
    for value in [c.stiffness, c.damping, c.dampingRatio, c.mass, c.energyThreshold] where value <= 0 { ok = false }
    if c.duration < 0 { ok = false }
    if let clamp = c.clamp, let lo = clamp.min, let hi = clamp.max, lo != 0, hi != 0, lo > hi { ok = false }
    return ok
  }

  static func bisectRoot(min lo: Double, max hi: Double, func f: (Double) -> Double,
                         precision: Double, maxIterations: Int = 20) -> Double {
    var min = lo, max = hi
    let direction: Double = f(max) >= f(min) ? 1 : -1
    var idx = maxIterations
    var current = (max + min) / 2
    while abs(f(current)) > precision && idx > 0 {
      idx -= 1
      if f(current) * direction < 0 { min = current } else { max = current }
      current = (min + max) / 2
    }
    return current
  }

  static func scaleZetaToMatchClamps(zeta: Double, toValue: Double, startValue: Double,
                                     clamp: (min: Double?, max: Double?)) -> Double {
    if startValue == 0 { return zeta }
    let (firstBound, secondBound) = startValue <= 0 ? (clamp.min, clamp.max) : (clamp.max, clamp.min)
    let relativeExtremum1 = secondBound.map { abs(($0 - toValue) / startValue) }
    let relativeExtremum2 = firstBound.map { abs(($0 - toValue) / startValue) }
    let newZeta1 = relativeExtremum1.map { abs(log($0) / Double.pi) }
    let newZeta2 = relativeExtremum2.map { abs(log($0) / (2 * Double.pi)) }
    return ([newZeta1, newZeta2].compactMap { $0 } + [zeta]).max()!
  }

  static func getEnergy(_ displacement: Double, _ velocity: Double, _ stiffness: Double, _ mass: Double) -> Double {
    0.5 * stiffness * displacement * displacement + 0.5 * mass * velocity * velocity
  }

  static func calculateNewStiffnessToMatchDuration(x0: Double, config c: GlowknobResolvedSpringConfig, v0: Double) -> Double {
    if c.skipAnimation { return 0 }
    let zeta = c.dampingRatio, threshold = c.energyThreshold, m = c.mass, targetDuration = c.duration
    func energyDiffForStiffness(_ stiffness: Double) -> Double {
      let perceptualCoefficient = 1.5
      let millisecondsInSecond = 1000.0
      let settlingDuration = (targetDuration * perceptualCoefficient) / millisecondsInSecond
      let omega0 = (stiffness / m).squareRoot() * zeta
      let xtk = (x0 + (v0 + x0 * omega0) * settlingDuration) * exp(-omega0 * settlingDuration)
      let vtk = (x0 + (v0 + x0 * omega0) * settlingDuration) * exp(-omega0 * settlingDuration) * -omega0
        + (v0 + x0 * omega0) * exp(-omega0 * settlingDuration)
      let e0 = getEnergy(x0, v0, stiffness, m)
      let etk = getEnergy(xtk, vtk, stiffness, m)
      let energyFraction = etk / e0
      return energyFraction - threshold
    }
    let precision = c.energyThreshold * 1e-3 // Experimentally seems to be good enough.
    // Bisection turns out to be much faster than Newton's method in our case
    return bisectRoot(min: Double.ulpOfOne, max: 8e3, func: energyDiffForStiffness,
                      precision: precision, maxIterations: 100)
  }

  /// Reanimated's `initialCalculations` and its choice of solution, handed to
  /// SwiftUI's `Spring`, which then does the per-frame math. Reanimated solves
  /// ζ < 1 as an under-damped oscillator with ω0 = √(k/m), and anything with
  /// ζ ≥ 1 with the *critically* damped solution at that ω0 — it never
  /// over-damps. `Spring(response:dampingRatio: 1)` is the only native
  /// initializer that lands on SwiftUI's exact critical branch (a damping of
  /// 2·√(k·m) leaves a rounding-error frequency and a cancelling over/under-
  /// damped solution), and a critically damped spring's motion depends on ω0
  /// alone, so mass and stiffness do not need to be carried separately.
  static func nativeSpring(stiffness k: Double, mass m: Double, zeta: Double, damping: Double) -> SwiftUI.Spring {
    if zeta >= 1 { return SwiftUI.Spring(response: 2 * Double.pi / (k / m).squareRoot(), dampingRatio: 1) }
    return SwiftUI.Spring(mass: m, stiffness: k, damping: damping, allowOverDamping: true)
  }
}

/// `withSpring`: Reanimated's spring.ts lifecycle (config merge, velocity
/// carry-over and clipping, "triggered twice" continuation, duration →
/// stiffness solve, clamp → ζ scaling, energy rest rule, overshoot clamping)
/// around SwiftUI's `Spring`. Like Reanimated, each frame restarts the closed-
/// form solution from the current position and velocity over the elapsed
/// time (capped at 64 ms), so a value can be read, handed off or interrupted
/// mid-flight exactly as on the UI runtime.
final class GlowknobSpringAnimation: GlowknobAnimationObject {
  let target: Double
  private var config: GlowknobResolvedSpringConfig
  var velocity: Double
  var lastTimestamp: Double = 0
  var startTimestamp: Double = 0
  var startValue: Double = 0
  /// The native spring this animation integrates (nil when the config is
  /// invalid and the animation jumps to its target).
  private(set) var spring: SwiftUI.Spring?
  var initialEnergy: Double = 0

  init(_ toValue: Double, _ userConfig: GlowknobSpringConfig? = nil, callback: ((Bool) -> Void)? = nil) {
    target = toValue
    config = GlowknobResolvedSpringConfig(userConfig)
    velocity = config.velocity
    super.init(callback: callback)
    current = toValue
    reduceMotion = glowknobReduceMotionFor(userConfig?.reduceMotion)
  }

  override var toValue: Double? { target }
  override var carriedVelocity: Double? { velocity }

  override func onFrame(_ now: Double) -> Bool {
    let cur = current ?? target
    guard !config.skipAnimation, let spring else {
      current = target
      lastTimestamp = 0
      return true
    }
    let deltaTime = min(max(now - lastTimestamp, 0), 64)
    lastTimestamp = now
    let t = deltaTime / 1000
    // SwiftUI measures the motion from 0 toward `target` (the change still to
    // go); velocities are in value units per second on both sides.
    let toGo = target - cur
    let v0 = velocity
    current = cur + spring.value(target: toGo, initialVelocity: v0, time: t)
    velocity = spring.velocity(target: toGo, initialVelocity: v0, time: t)
    if isTerminating() {
      velocity = 0
      current = target
      // clear lastTimestamp to avoid using stale value by the next spring animation that starts after this one
      lastTimestamp = 0
      return true
    }
    return false
  }

  /// `isAnimationTerminatingCalculation`. Overshoot clamping is a hard stop:
  /// the first frame outside the start…target span ends the animation, which
  /// then snaps to its target (the `clamp` option, not this flag, is what
  /// raises ζ through `scaleZetaToMatchClamps`).
  private func isTerminating() -> Bool {
    let cur = current ?? target
    if config.overshootClamping {
      let leftBound = startValue >= 0 ? target : target + startValue
      let rightBound = leftBound + abs(startValue)
      if cur < leftBound || cur > rightBound { return true }
    }
    let currentEnergy = GlowknobSpringMath.getEnergy(target - cur, velocity, config.stiffness, config.mass)
    return initialEnergy == 0 || currentEnergy / initialEnergy <= config.energyThreshold
  }

  private func isTriggeredTwice(_ previous: GlowknobAnimationObject?) -> GlowknobSpringAnimation? {
    // `duration` and `dampingRatio` are not fields of the animation object, so
    // that part of Reanimated's check always compares undefined to undefined.
    guard let previous = previous as? GlowknobSpringAnimation, previous.lastTimestamp != 0,
          previous.startTimestamp != 0, previous.target == target else { return nil }
    return previous
  }

  override func onStart(value: Double, now: Double, previous: GlowknobAnimationObject?) {
    current = value
    var stiffness = config.stiffness
    let twice = isTriggeredTwice(previous)
    let duration = config.duration
    // If animation is triggered twice we want to continue the previous animation
    // form the previous starting point
    let x0 = twice?.startValue ?? (value - target)
    startValue = x0

    if let previous {
      if let twice {
        velocity = twice.velocity
      } else {
        // `previousAnimation.velocity + config.velocity || 0`: undefined + n is NaN.
        let sum = (previous.carriedVelocity ?? .nan) + config.velocity
        velocity = sum.isNaN || sum == 0 ? 0 : sum
      }
    } else {
      velocity = config.velocity
    }

    // Clip velocity that would drive the first frame away from the new target.
    // Inertia is only preserved when it already points toward toValue.
    if (target > value && velocity < 0) || (target < value && velocity > 0) {
      velocity = 0
    }

    if let twice {
      spring = twice.spring
    } else if !config.skipAnimation {
      if config.useDuration {
        config.duration = duration
        stiffness = GlowknobSpringMath.calculateNewStiffnessToMatchDuration(x0: x0, config: config, v0: velocity)
        config.stiffness = stiffness
      }
      // initialCalculations: a duration config fixes ζ and solves k; a physics
      // config gives k, c, m.
      let m = config.mass
      var zeta = config.useDuration ? config.dampingRatio : config.damping / (2 * (stiffness * m).squareRoot())
      var damping = config.useDuration ? 2 * zeta * (stiffness * m).squareRoot() : config.damping
      if let clamp = config.clamp {
        let scaled = GlowknobSpringMath.scaleZetaToMatchClamps(zeta: zeta, toValue: target, startValue: startValue, clamp: clamp)
        if scaled != zeta {
          zeta = scaled
          damping = 2 * zeta * (stiffness * m).squareRoot()
        }
      }
      spring = GlowknobSpringMath.nativeSpring(stiffness: stiffness, mass: m, zeta: zeta, damping: damping)
    }

    initialEnergy = GlowknobSpringMath.getEnergy(x0, config.velocity, config.stiffness, config.mass)
    let prevLast = (previous as? GlowknobSpringAnimation)?.lastTimestamp ?? (previous as? GlowknobDecayAnimation)?.lastTimestamp ?? 0
    lastTimestamp = prevLast != 0 ? prevLast : now
    startTimestamp = twice.map { $0.startTimestamp != 0 ? $0.startTimestamp : now } ?? now
  }
}

// MARK: - withDecay

struct GlowknobDecayConfig {
  var deceleration = 0.998
  var velocityFactor = 1.0
  var velocity = 0.0
  var clamp: (Double, Double)?
  var rubberBandEffect = false
  var rubberBandFactor = 0.6
  var reduceMotion: GlowknobReduceMotion?
}

final class GlowknobDecayAnimation: GlowknobAnimationObject {
  static let velocityEps = 1.0 // VELOCITY_EPS on native
  static let slopeFactor = 0.1
  static let derivativeEps = 0.1

  let config: GlowknobDecayConfig
  var lastTimestamp: Double = 0
  var startTimestamp: Double = 0
  var initialVelocity: Double = 0
  var velocity: Double
  var springActive = false

  init(_ config: GlowknobDecayConfig, callback: ((Bool) -> Void)? = nil) {
    self.config = config
    velocity = config.velocity
    super.init(callback: callback)
    // current stays nil so the "same value" shortcut never applies.
    reduceMotion = glowknobReduceMotionFor(config.reduceMotion)
  }

  override var carriedVelocity: Double? { velocity }

  override func onStart(value: Double, now: Double, previous: GlowknobAnimationObject?) {
    current = value
    lastTimestamp = now
    startTimestamp = now
    initialVelocity = config.velocity
    velocity = config.velocity
    if reduceMotion == true, let clamp = config.clamp {
      if value < clamp.0 { current = clamp.0 } else if value > clamp.1 { current = clamp.1 }
    }
  }

  override func onFrame(_ now: Double) -> Bool {
    if config.rubberBandEffect, let clamp = config.clamp { return rubberBandDecay(now, clamp) }
    return rigidDecay(now)
  }

  private func rigidDecay(_ now: Double) -> Bool {
    let cur = current ?? 0
    let deltaTime = min(max(now - lastTimestamp, 0), 64)
    let v = velocity * exp(-(1 - config.deceleration) * (now - startTimestamp) * Self.slopeFactor)
    current = cur + (v * config.velocityFactor * deltaTime) / 1000
    velocity = v
    lastTimestamp = now
    if let clamp = config.clamp {
      if initialVelocity < 0 && current! <= clamp.0 {
        current = clamp.0
        return true
      } else if initialVelocity > 0 && current! >= clamp.1 {
        current = clamp.1
        return true
      }
    }
    return abs(v) < Self.velocityEps
  }

  private func rubberBandDecay(_ now: Double, _ clamp: (Double, Double)) -> Bool {
    let cur = current ?? 0
    let deltaTime = min(max(now - lastTimestamp, 0), 64)
    let clampIndex = abs(cur - clamp.0) < abs(cur - clamp.1) ? 0 : 1
    let bound = clampIndex == 0 ? clamp.0 : clamp.1
    var derivative = 0.0
    if cur < clamp.0 || cur > clamp.1 { derivative = cur - bound }
    let v = velocity * exp(-(1 - config.deceleration) * (now - startTimestamp) * Self.slopeFactor)
      - derivative * config.rubberBandFactor
    if abs(derivative) > Self.derivativeEps {
      springActive = true
    } else if springActive {
      current = bound
      return true
    } else if abs(v) < Self.velocityEps {
      return true
    }
    current = cur + (v * config.velocityFactor * deltaTime) / 1000
    velocity = v
    lastTimestamp = now
    return false
  }
}

// MARK: - withDelay

final class GlowknobDelayAnimation: GlowknobAnimationObject {
  let delayMs: Double
  let next: GlowknobAnimationObject
  var startTime: Double = 0
  var started = false
  var previousAnimation: GlowknobAnimationObject?

  init(_ delayMs: Double, _ next: GlowknobAnimationObject, reduceMotion: GlowknobReduceMotion? = nil) {
    self.delayMs = delayMs
    self.next = next
    super.init(callback: nil)
    current = next.current
    self.reduceMotion = glowknobReduceMotionFor(reduceMotion)
    callback = { [next] finished in next.callback?(finished) }
  }

  override var isHigherOrder: Bool { true }

  override func onStart(value: Double, now: Double, previous: GlowknobAnimationObject?) {
    startTime = now
    started = false
    current = value
    if previous === self {
      previousAnimation = (previous as? GlowknobDelayAnimation)?.previousAnimation
    } else {
      previousAnimation = previous
    }
    // child animations inherit the setting, unless they already have it defined
    if next.reduceMotion == nil { next.reduceMotion = reduceMotion }
  }

  override func onFrame(_ now: Double) -> Bool {
    if now - startTime >= delayMs || reduceMotion == true {
      if !started {
        next.start(value: current ?? 0, now: now, previous: previousAnimation)
        previousAnimation = nil
        started = true
      }
      let finished = next.frame(now)
      current = next.current
      return finished
    } else if let previous = previousAnimation {
      let finished = previous.finished || previous.frame(now)
      current = previous.current
      if finished { previousAnimation = nil }
    }
    return false
  }
}

// MARK: - Builders, named as in Reanimated

@MainActor
func glowknobWithTiming(_ toValue: Double, duration: Double = 300, easing: @escaping GlowknobEasingFunction = GlowknobEasing.inOutQuad,
                reduceMotion: GlowknobReduceMotion? = nil, _ callback: ((Bool) -> Void)? = nil) -> GlowknobAnimationObject {
  GlowknobTimingAnimation(toValue, duration: duration, easing: easing, reduceMotion: reduceMotion, callback: callback)
}

@MainActor
func glowknobWithSpring(_ toValue: Double, _ config: GlowknobSpringConfig? = nil, _ callback: ((Bool) -> Void)? = nil) -> GlowknobAnimationObject {
  GlowknobSpringAnimation(toValue, config, callback: callback)
}

@MainActor
func glowknobWithDecay(_ config: GlowknobDecayConfig, _ callback: ((Bool) -> Void)? = nil) -> GlowknobAnimationObject {
  GlowknobDecayAnimation(config, callback: callback)
}

@MainActor
func glowknobWithDelay(_ ms: Double, _ animation: GlowknobAnimationObject, _ reduceMotion: GlowknobReduceMotion? = nil) -> GlowknobAnimationObject {
  GlowknobDelayAnimation(ms, animation, reduceMotion: reduceMotion)
}
