import Observation
import QuartzCore
import UIKit

/// The "UI thread" of the port: one CADisplayLink (120 Hz capable) that plays
/// the role of Reanimated's frame loop. Each frame, in Reanimated's order:
///   1. `requestAnimationFrame` callbacks (running animations step),
///   2. frame callbacks (`useFrameCallback`),
///   3. mappers — here reactions, evaluated once per frame in which any shared
///      value changed (derived values are pulled when read, so they always
///      see this frame's inputs),
///   4. after-frame hooks (the 3D lift layer renders and composites),
///   5. one redraw of every view that reads `clock.frame`.
/// The link pauses itself when nothing is animating and nothing changed.
@MainActor
final class GlowknobUIRuntime {
  static let shared = GlowknobUIRuntime()

  /// Reanimated's `isReduceMotionOnUI`: the system setting, read at launch
  /// (`GlowknobReduceMotion.System` animations jump to their end when it is on).
  var isReduceMotion: Bool = UIAccessibility.isReduceMotionEnabled

  /// Redraw signal for SwiftUI: views that draw shared values read `frame`.
  let clock = GlowknobFrameClock()

  /// The current frame's timestamp (ms, CADisplayLink `targetTimestamp`), or
  /// nil outside a frame — then animations start at `CACurrentMediaTime()`,
  /// as Reanimated's `_getAnimationTimestamp()` does for gesture events.
  private(set) var frameTimestamp: Double?
  private(set) var previousFrameTimestamp: Double?

  private var rafQueue: [(Double) -> Void] = []
  private var frameCallbacks: [GlowknobFrameCallback] = []
  private var reactions: [GlowknobAnyReaction] = []
  private var afterFrame: [(Double) -> Void] = []
  private var version: UInt64 = 0
  private var reactedVersion: UInt64 = .max
  private var drawnVersion: UInt64 = .max
  private var link: CADisplayLink?
  private var idleFrames = 0

  func now() -> Double { frameTimestamp ?? CACurrentMediaTime() * 1000 }

  func requestAnimationFrame(_ callback: @escaping (Double) -> Void) {
    rafQueue.append(callback)
    wake()
  }

  /// Any shared value changed: mappers must run and views redraw.
  func valueChanged() {
    version &+= 1
    wake()
  }

  func register(_ reaction: GlowknobAnyReaction) {
    reactions.append(reaction)
    reactedVersion = .max
    wake()
  }

  func unregister(_ reaction: GlowknobAnyReaction) {
    reactions.removeAll { $0 === reaction }
  }

  func add(_ callback: GlowknobFrameCallback) {
    if !frameCallbacks.contains(where: { $0 === callback }) { frameCallbacks.append(callback) }
    wake()
  }

  func remove(_ callback: GlowknobFrameCallback) {
    frameCallbacks.removeAll { $0 === callback }
  }

  /// Work that runs after the mappers of every frame (3D render + composite).
  func onAfterFrame(_ hook: @escaping (Double) -> Void) {
    afterFrame.append(hook)
  }

  /// Keeps the link alive for hooks that poll (the lift layer while it is up).
  var keepAlive = 0 { didSet { if keepAlive > 0 { wake() } } }

  func wake() {
    idleFrames = 0
    if let link {
      if link.isPaused { link.isPaused = false }
      return
    }
    let link = CADisplayLink(target: LinkProxy(self), selector: #selector(LinkProxy.step(_:)))
    link.preferredFrameRateRange = CAFrameRateRange(minimum: 80, maximum: 120, preferred: 120)
    link.add(to: .main, forMode: .common)
    self.link = link
  }

  /// Runs the mappers now (tests, and flushing state before a snapshot).
  func flushReactions() {
    guard reactedVersion != version else { return }
    // Reactions may set values read by later reactions; a second pass picks
    // up those, like Reanimated's topologically sorted mappers.
    for _ in 0..<3 {
      let before = version
      reactedVersion = version
      for reaction in reactions { reaction.run() }
      if version == before { break }
    }
  }

  fileprivate func step(_ link: CADisplayLink) {
    let ts = link.targetTimestamp * 1000
    GlowknobPerf.frameBegan(timestamp: link.timestamp, target: link.targetTimestamp)
    tick(timestamp: ts)
    let busy = !rafQueue.isEmpty || !frameCallbacks.isEmpty || keepAlive > 0
    if busy || version != drawnVersion { idleFrames = 0 } else { idleFrames += 1 }
    if idleFrames > 2 {
      link.isPaused = true
      GlowknobPerf.idle()
    }
  }

  /// One frame. Public for tests, which drive the clock by hand.
  func tick(timestamp ts: Double) {
    frameTimestamp = ts
    defer {
      previousFrameTimestamp = ts
      frameTimestamp = nil
    }
    let queue = rafQueue
    rafQueue = []
    GlowknobPerf.measure("raf", \.raf) { for callback in queue { callback(ts) } }
    GlowknobPerf.measure("frameCallbacks", \.callbacks) {
      for callback in frameCallbacks where callback.active { callback.run(ts) }
    }
    GlowknobPerf.measure("reactions", \.reactions) { flushReactions() }
    GlowknobPerf.measure("afterFrame", \.after) { for hook in afterFrame { hook(ts) } }
    if version != drawnVersion {
      drawnVersion = version
      clock.frame &+= 1
    }
  }
}

@MainActor
private final class LinkProxy: NSObject {
  weak var runtime: GlowknobUIRuntime?
  init(_ runtime: GlowknobUIRuntime) { self.runtime = runtime }
  @objc func step(_ link: CADisplayLink) {
    MainActor.assumeIsolated { runtime?.step(link) }
  }
}

/// Bumped once per frame in which something changed; SwiftUI views that draw
/// the animated state read it so they redraw exactly then.
@MainActor
@Observable
final class GlowknobFrameClock {
  var frame: UInt64 = 0
}

/// `useFrameCallback(cb, autostart)`: runs every frame while active, with the
/// time since its previous frame (nil on its first frame), in ms.
@MainActor
final class GlowknobFrameCallback {
  let body: (_ timeSincePreviousFrame: Double?) -> Void
  fileprivate(set) var lastTimestamp: Double?
  private(set) var active = false

  init(_ body: @escaping (_ timeSincePreviousFrame: Double?) -> Void) {
    self.body = body
  }

  func setActive(_ on: Bool) {
    guard on != active else { return }
    active = on
    lastTimestamp = nil
    if on { GlowknobUIRuntime.shared.add(self) } else { GlowknobUIRuntime.shared.remove(self) }
  }

  fileprivate func run(_ ts: Double) {
    let delta = lastTimestamp.map { ts - $0 }
    lastTimestamp = ts
    body(delta)
  }
}

/// Type-erased mapper.
@MainActor
class GlowknobAnyReaction {
  func run() {}
}

/// `useAnimatedReaction(prepare, react)`: `react(current, previous)` runs on
/// the first frame (previous = nil) and whenever the prepared value changes.
@MainActor
final class GlowknobReaction<T: Equatable>: GlowknobAnyReaction {
  private let prepare: () -> T
  private let react: (T, T?) -> Void
  private var previous: T?
  private var started = false

  init(_ prepare: @escaping () -> T, _ react: @escaping (T, T?) -> Void) {
    self.prepare = prepare
    self.react = react
  }

  override func run() {
    let value = prepare()
    if started && value == previous { return }
    let before = started ? previous : nil
    started = true
    previous = value
    react(value, before)
  }
}

extension GlowknobUIRuntime {
  @discardableResult
  func reaction<T: Equatable>(
    _ prepare: @escaping () -> T,
    _ react: @escaping (T, T?) -> Void
  ) -> GlowknobReaction<T> {
    let r = GlowknobReaction(prepare, react)
    register(r)
    return r
  }
}
