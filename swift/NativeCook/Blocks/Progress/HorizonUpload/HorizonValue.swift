import Foundation
import SwiftUI

/// A presentation value, stepped by the owner's display link. Duration springs use
/// Reanimated's stiffness solve and energy threshold (as in the referral port).
final class HorizonValue {
  enum Curve { case linear, quad, out }
  var value: Double
  private var from = 0.0, target = 0.0, elapsed = 0.0, duration = 0.0
  private var curve = Curve.quad
  private var spring: Spring?
  private var velocity = 0.0, stiffness = 0.0, initialEnergy = 0.0
  private var running = false
  init(_ value: Double = 0) { self.value = value }
  func set(_ x: Double) { value = x; cancel() }
  func cancel() { running = false; spring = nil; velocity = 0 }
  func timing(_ x: Double, _ ms: Double, _ curve: Curve = .quad) {
    from = value; target = x; duration = max(0, ms); elapsed = 0
    self.curve = curve; spring = nil; velocity = 0; running = true
    if ms <= 0 { set(x) }
  }
  func spring(_ x: Double, _ ms: Double, _ ratio: Double, velocity input: Double = 0, bounded: Bool = false) {
    var v = input + velocity
    if (x > value && v < 0) || (x < value && v > 0) { v = 0 }
    let x0 = value - x, mass = 4.0, threshold = 6e-9
    if x0 == 0 && v == 0 { set(x); return }
    let t = ms * 1.5 / 1000
    func difference(_ k: Double) -> Double {
      let w = sqrt(k / mass) * ratio
      let a = x0 + (v + x0 * w) * t
      let e = exp(-w * t)
      let xt = a * e, vt = a * e * -w + (v + x0 * w) * e
      return (k * xt * xt + mass * vt * vt) / (k * x0 * x0 + mass * v * v) - threshold
    }
    var lo = Double.ulpOfOne, hi = 8000.0
    let direction = difference(hi) >= difference(lo) ? 1.0 : -1.0
    var k = (lo + hi) / 2
    for _ in 0..<100 {
      let d = difference(k)
      if abs(d) <= threshold * 1e-3 { break }
      if d * direction < 0 { lo = k } else { hi = k }
      k = (lo + hi) / 2
    }
    var zeta = ratio
    if bounded && x0 != 0 {
      zeta = max(zeta, abs(log(abs((110 - x) / x0)) / .pi), abs(log(abs((-110 - x) / x0)) / (2 * .pi)))
    }
    spring = zeta >= 1 ? Spring(response: 2 * .pi / sqrt(k / mass), dampingRatio: 1)
      : Spring(mass: mass, stiffness: k, damping: 2 * zeta * sqrt(k * mass), allowOverDamping: true)
    target = x; velocity = v; stiffness = k
    initialEnergy = 0.5 * k * x0 * x0 + 0.5 * mass * input * input
    running = true
  }
  func advance(_ ms: Double) {
    guard running else { return }
    if let spring {
      let t = min(64, max(0, ms)) / 1000, delta = target - value, v = velocity
      value += spring.value(target: delta, initialVelocity: v, time: t)
      velocity = spring.velocity(target: delta, initialVelocity: v, time: t)
      let d = target - value
      let energy = 0.5 * stiffness * d * d + 2 * velocity * velocity
      if initialEnergy == 0 || energy / initialEnergy <= 6e-9 { set(target) }
    } else {
      elapsed += ms
      let t = HorizonMath.clamp(elapsed / max(duration, 0.001))
      let eased: Double
      switch curve {
      case .linear: eased = t
      case .quad: eased = t < 0.5 ? 2 * t * t : 1 - 2 * (1 - t) * (1 - t)
      case .out: eased = 1 - (1 - t) * (1 - t)
      }
      value = from + (target - from) * eased
      if t == 1 { set(target) }
    }
  }
}
