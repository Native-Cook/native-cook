import UIKit

/// Two display frames let the native landing view mount its initial glyphs before rolling.
@MainActor final class EmberLandingFrames: NSObject {

  /// Display callbacks observed since the landing view mounted.
  private var frames = 0

  /// Retains this waiter until the two-frame gate completes.
  private var link: CADisplayLink?

  /// Resumed exactly once after invalidating the display link.
  private var continuation: CheckedContinuation<Void, Never>?

  static func wait() async {
    let waiter = EmberLandingFrames()
    await withCheckedContinuation { continuation in
      waiter.continuation = continuation
      let displayLink = CADisplayLink(target: waiter, selector: #selector(tick))
      waiter.link = displayLink
      displayLink.add(to: .main, forMode: .common)
    }
  }

  @objc private func tick() {
    frames += 1
    if frames >= EmberMotionConstants.count.landingFrames {
      link?.invalidate()
      link = nil
      continuation?.resume()
      continuation = nil
    }
  }
}
