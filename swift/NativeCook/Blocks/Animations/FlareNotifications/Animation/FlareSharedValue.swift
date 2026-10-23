import Foundation

/// A numeric shared value (`useSharedValue<number>`): settable directly or to
/// an animation, with Reanimated's `valueSetter` semantics.
@MainActor
final class FlareAnimatedValue {
  private var value: Double
  private var animation: FlareAnimationObject?

  init(_ initial: Double) { value = initial }

  func get() -> Double { value }

  /// Plain set: cancels a running animation (its callback gets `false`).
  func set(_ newValue: Double) {
    cancelRunning()
    write(newValue)
  }

  /// Animated set (`sv.value = withTiming(...)`).
  func set(_ next: FlareAnimationObject) {
    let previous = cancelRunning()
    // prevent setting again to the same value and triggering the mappers that
    // treat this value as an input (built-in animations hold their target in
    // .current until onStart is called)
    if let target = next.current, target == value, !next.isHigherOrder {
      next.callback?(true)
      return
    }
    let runtime = FlareUIRuntime.shared
    let now = runtime.now()
    next.start(value: value, now: now, previous: previous)
    animation = next
    step(next, now)
  }

  /// `cancelAnimation(sv)`: on the UI runtime this is `sv.value = sv.value`.
  func cancel() { set(value) }

  var isAnimating: Bool { animation.map { !$0.finished } ?? false }

  @discardableResult
  private func cancelRunning() -> FlareAnimationObject? {
    guard let previous = animation else { return nil }
    animation = nil
    if !previous.finished {
      previous.cancelled = true
      previous.callback?(false)
    }
    return previous
  }

  private func step(_ running: FlareAnimationObject, _ timestamp: Double) {
    if running.cancelled { return }
    let finished = running.frame(timestamp)
    running.timestamp = timestamp
    write(running.current ?? value)
    if finished {
      // Like Reanimated, the finished animation stays attached until the next
      // set, which receives it as `previousAnimation`.
      running.finished = true
      running.callback?(true)
    } else {
      FlareUIRuntime.shared.requestAnimationFrame { [weak self] ts in self?.step(running, ts) }
    }
  }

  private func write(_ newValue: Double) {
    guard newValue != value || newValue.isNaN != value.isNaN else { return }
    value = newValue
    FlareUIRuntime.shared.valueChanged()
  }
}
