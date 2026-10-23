import Foundation
import UIKit

/// expo-haptics on iOS: each call makes a fresh generator, prepares it and
/// fires, exactly as `selectionAsync()` / `impactAsync(Light)` do.
@MainActor
enum FlareHaptics {
  static func selection() {
    let generator = UISelectionFeedbackGenerator()
    generator.prepare()
    generator.selectionChanged()
  }

  static func impactLight() {
    let generator = UIImpactFeedbackGenerator(style: .light)
    generator.prepare()
    generator.impactOccurred()
  }
}

/// `hapticRhythm()` from `haptic-rhythm.ts`: a cancellable run of selection
/// ticks. Each gap is the previous one times `stretch`, plus up to `jitter`
/// ms, so the rhythm can speed up, slow down or crackle irregularly. `delay`
/// holds the first tick back, so a press can turn into a hold before anything
/// plays. JS `setTimeout` becomes a main-queue work item.
@MainActor
final class FlareHapticRhythm {
  struct Config: Equatable {
    var first: Double
    var stretch: Double
    var jitter: Double = 0
    var total: Double
    var delay: Double = 0
  }

  /// The crackle of a held pill (flare-notifications.md §5.7).
  static let crackle = Config(first: 70, stretch: 0.97, jitter: 60, total: FlareMotion.stoke,
                              delay: FlareMotion.holdAfter)

  private var timer: DispatchWorkItem?
  private let tick: @MainActor () -> Void
  private let random: @MainActor () -> Double

  init(tick: @escaping @MainActor () -> Void = { FlareHaptics.selection() },
       random: @escaping @MainActor () -> Double = { Double.random(in: 0..<1) }) {
    self.tick = tick
    self.random = random
  }

  func stop() {
    timer?.cancel()
    timer = nil
  }

  func play(_ config: Config) {
    stop()
    left = config.total
    if config.delay > 0 {
      schedule(after: config.delay) { [weak self] in self?.step(config.first, config) }
    } else {
      step(config.first, config)
    }
  }

  /// Time still to fill with gaps (ms), as the closure's `left` in JS.
  private var left = 0.0

  private func step(_ gap: Double, _ config: Config) {
    tick()
    let wait = gap + random() * config.jitter
    left -= wait
    if left > 0 {
      schedule(after: wait) { [weak self] in self?.step(gap * config.stretch, config) }
    } else {
      timer = nil
    }
  }

  private func schedule(after ms: Double, _ body: @escaping @MainActor () -> Void) {
    let item = DispatchWorkItem { MainActor.assumeIsolated { body() } }
    timer = item
    DispatchQueue.main.asyncAfter(deadline: .now() + ms / 1000, execute: item)
  }

  /// The tick times (ms from `play`) a run would produce for these random
  /// draws; the schedule `play` follows, for tests.
  static func schedule(_ config: Config, randoms: [Double]) -> [Double] {
    var times: [Double] = []
    var left = config.total
    var t = config.delay
    var gap = config.first
    var i = 0
    while true {
      times.append(t)
      let wait = gap + (i < randoms.count ? randoms[i] : 0) * config.jitter
      i += 1
      left -= wait
      if left <= 0 { break }
      t += wait
      gap *= config.stretch
    }
    return times
  }
}
