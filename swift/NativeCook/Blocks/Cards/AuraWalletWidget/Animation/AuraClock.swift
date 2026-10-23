import QuartzCore
import UIKit

/// Only timestamp/preference context is shared; the card owns its values and display link.
@MainActor final class AuraUIRuntime {
  static let shared = AuraUIRuntime()
  var isReduceMotion = false
  var timestamp: Double?
  func now() -> Double { timestamp ?? CACurrentMediaTime() * 1000 }
}

/// A retained animation slot. No requestAnimationFrame closure/array is made per frame.
@MainActor final class AuraValue {
  private(set) var value: Double
  private var animation: AuraAnimationObject?
  private var dirty = true
  init(_ value: Double) { self.value = value }
  var active: Bool { animation.map { !$0.finished && !$0.cancelled } ?? false }
  func set(_ value: Double) {
    cancel()
    dirty = dirty || self.value != value
    self.value = value
  }
  func cancel() {
    if let animation, !animation.finished {
      animation.cancelled = true
      animation.callback?(false)
    }
    animation = nil
  }
  func set(_ next: AuraAnimationObject) {
    let previous = animation
    previous?.cancelled = true
    if let previous, !previous.finished { previous.callback?(false) }
    if next.current == value && !next.isHigherOrder {
      animation = nil
      next.callback?(true)
      return
    }
    next.start(value: value, now: AuraUIRuntime.shared.now(), previous: previous)
    animation = next
    step(AuraUIRuntime.shared.now())
  }
  @discardableResult func tick(_ now: Double) -> Bool {
    step(now)
    let changed = dirty
    dirty = false
    return changed
  }
  private func step(_ now: Double) {
    guard let animation, !animation.finished, !animation.cancelled else { return }
    let old = value
    let done = animation.frame(now)
    value = animation.current ?? value
    animation.timestamp = now
    if done {
      animation.finished = true
      animation.callback?(true)
    }
    dirty = dirty || old != value
  }
}

@MainActor enum AuraAnimation {
  static let easeOut = AuraEasing.bezier(0.23, 1, 0.32, 1)
  static let easeInOut = AuraEasing.bezier(0.77, 0, 0.175, 1)
  static func timing(_ to: Double, _ ms: Double, linear: Bool = false, inOut: Bool = false)
    -> AuraAnimationObject
  {
    auraWithTiming(
      to, duration: ms, easing: linear ? AuraEasing.linear : inOut ? easeInOut : easeOut,
      reduceMotion: .never)
  }
  static func spring(
    _ to: Double, _ ms: Double, _ ratio: Double, velocity: Double = 0, clamp: Bool = false
  ) -> AuraAnimationObject {
    auraWithSpring(
      to,
      .init(
        duration: ms, dampingRatio: ratio, velocity: velocity, overshootClamping: clamp,
        reduceMotion: .never))
  }
  static func delay(_ ms: Double, _ next: AuraAnimationObject)
    -> AuraAnimationObject
  {
    auraWithDelay(ms, next, .never)
  }
  static func sequence(_ values: AuraAnimationObject...) -> AuraAnimationObject {
    AuraSequenceAnimation(values, reduceMotion: .never)
  }
}
