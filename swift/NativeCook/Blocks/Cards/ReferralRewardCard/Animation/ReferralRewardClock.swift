import Observation
import QuartzCore
import UIKit

/// Only timestamp/preference context is shared; the card owns its values and display link.
@MainActor final class ReferralRewardUIRuntime {
  static let shared = ReferralRewardUIRuntime()
  var isReduceMotion = false
  var timestamp: Double?
  func now() -> Double { timestamp ?? CACurrentMediaTime() * 1000 }
}

/// A retained animation slot. No requestAnimationFrame closure/array is made per frame.
@MainActor final class ReferralRewardValue {
  private(set) var value: Double
  private var animation: ReferralRewardAnimationObject?
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
  func set(_ next: ReferralRewardAnimationObject) {
    let previous = animation
    previous?.cancelled = true
    if let previous, !previous.finished { previous.callback?(false) }
    if next.current == value && !next.isHigherOrder {
      animation = nil
      next.callback?(true)
      return
    }
    next.start(value: value, now: ReferralRewardUIRuntime.shared.now(), previous: previous)
    animation = next
    step(ReferralRewardUIRuntime.shared.now())
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

@MainActor @Observable final class ReferralRewardInvalidation { var frame = 0 }

@MainActor enum ReferralRewardAnimation {
  static let easeOut = ReferralRewardEasing.bezier(0.23, 1, 0.32, 1)
  static let easeInOut = ReferralRewardEasing.bezier(0.77, 0, 0.175, 1)
  static func timing(_ to: Double, _ ms: Double, linear: Bool = false, inOut: Bool = false)
    -> ReferralRewardAnimationObject
  {
    referralRewardWithTiming(
      to, duration: ms, easing: linear ? ReferralRewardEasing.linear : inOut ? easeInOut : easeOut,
      reduceMotion: .never)
  }
  static func spring(
    _ to: Double, _ ms: Double, _ ratio: Double, velocity: Double = 0, clamp: Bool = false
  ) -> ReferralRewardAnimationObject {
    referralRewardWithSpring(
      to,
      .init(
        duration: ms, dampingRatio: ratio, velocity: velocity, overshootClamping: clamp,
        reduceMotion: .never))
  }
  static func delay(_ ms: Double, _ next: ReferralRewardAnimationObject)
    -> ReferralRewardAnimationObject
  {
    referralRewardWithDelay(ms, next, .never)
  }
  static func sequence(_ values: ReferralRewardAnimationObject...) -> ReferralRewardAnimationObject
  {
    ReferralRewardSequenceAnimation(values, reduceMotion: .never)
  }
}
