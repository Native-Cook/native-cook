import Foundation
import QuartzCore

/// The block's one source of truth: the film's clock and whatever a finger set. `frame(at:)` is a pure function of
/// the stored fields and a media time (CACurrentMediaTime), read both by SceneKit's render loop (the 3D stage) and by
/// SwiftUI's TimelineView (the floor, the flat mark and the words), so the two never disagree and nothing writes
/// SwiftUI state per frame.
///
/// React Native's four modes: the film (auto); a finger turning the sculpture with the film held (manual); the fold,
/// hand-off and welcome screen on their own clock once the turn is complete (story); and the ease back to the poster
/// 2 s after an unfinished turn (return, folded into manual here as an ease that ends in auto).
final class SpinToLogoSplashController {
  typealias M = SpinToLogoSplashMotion

  enum Mode { case auto, manual, story }

  struct Frame {
    var t: Double
    var yaw: Double
    /// The ghost finger's visibility multiplier (0 once a real finger has taken over).
    var live: Double
    var mode: Mode
  }

  private let lock = NSLock()
  let film: Bool
  let reduced: Bool

  private var mode: Mode
  private var start: Double?
  // manual
  private var tHeld = 0.0
  private var yawStill = 0.0
  private var decay: (x0: Double, v0: Double, t0: Double)?
  private var liveFade: (from: Double, t0: Double)?
  private var lastActivity = 0.0
  private var returning: (t0: Double, tFrom: Double, yawFrom: Double)?
  var dragging = false
  // story
  private var storyStart = 0.0
  private var yawSpring: (from: Double, to: Double, t0: Double)?
  private var unlockedAt = -1.0

  init(autoPlay: Bool, reduced: Bool) {
    self.reduced = reduced
    film = autoPlay && !reduced
    mode = reduced ? .story : (film ? .auto : .manual)
  }

  private func locked<T>(_ f: () -> T) -> T { lock.lock(); defer { lock.unlock() }; return f() }

  func frame(at now: Double) -> Frame { locked { frameLocked(now) } }

  /// Media time of the last success haptic (the turn completed), for the render delegate.
  var unlockTime: Double { locked { unlockedAt } }

  // MARK: values

  /// Reanimated's withDecay (deceleration 0.996 per ms): position and velocity (deg/s).
  private static let decel = 0.996
  private func decayAt(_ now: Double) -> (x: Double, v: Double) {
    guard let d = decay else { return (yawStill, 0) }
    let ms = max(0, now - d.t0) * 1000
    let kv = pow(Self.decel, ms)
    let kx = Self.decel * (1 - kv) / (1 - Self.decel)
    return (d.x0 + d.v0 / 1000 * kx, d.v0 * kv)
  }

  private func manualYaw(_ now: Double) -> Double {
    if dragging { return yawStill }
    return decayAt(now).x
  }

  private func liveAt(_ now: Double) -> Double {
    guard let f = liveFade else { return mode == .auto ? 1 : 0 }
    return f.from * (1 - M.clamp01((now - f.t0) / 0.22))
  }

  /// withSpring(target, { duration: 500, dampingRatio: 0.85 }).
  private func springYaw(_ now: Double) -> Double {
    guard let s = yawSpring else { return 360 }
    let w = 2 * Double.pi / 0.5 * 0.95, z = 0.85, wd = w * sqrt(1 - z * z)
    let t = max(0, now - s.t0)
    let x0 = s.from - s.to
    let x = exp(-z * w * t) * (x0 * cos(wd * t) + z * w * x0 / wd * sin(wd * t))
    return s.to + x
  }

  private func frameLocked(_ now: Double) -> Frame {
    switch mode {
    case .auto:
      if start == nil { start = now }
      let t = max(0, (now - start!).truncatingRemainder(dividingBy: M.loop))
      return Frame(t: t, yaw: M.filmYaw(t), live: 1, mode: .auto)

    case .story:
      if reduced { return Frame(t: M.reducedT, yaw: 0, live: 0, mode: .story) }
      let t = min(M.storyT0 + (now - storyStart), M.holdEnd)
      if t >= M.holdEnd && film && now - lastActivity >= M.idle {
        // the welcome screen held; 2 s after the last touch the film's own return plays
        mode = .auto
        start = now - M.holdEnd
        liveFade = nil
        return frameLocked(now)
      }
      return Frame(t: t, yaw: springYaw(now), live: 0, mode: .story)

    case .manual:
      if let r = returning {
        let k = M.easeInOutTouch(M.clamp01((now - r.t0) / M.returnDuration))
        if k >= 1 {
          returning = nil
          mode = .auto
          start = now
          liveFade = nil
          return frameLocked(now)
        }
        return Frame(t: r.tFrom * (1 - k), yaw: r.yawFrom * (1 - k), live: 0, mode: .manual)
      }
      let d = decayAt(now)
      let moving = dragging || (decay != nil && abs(d.v) > 1)
      if moving { lastActivity = now }
      let yaw = manualYaw(now)
      if abs(yaw) >= 360 {
        unlockLocked(now, yaw: yaw)
        return frameLocked(now)
      }
      if film && now - lastActivity >= M.idle {
        returning = (now, tHeld, yaw)
        return frameLocked(now)
      }
      return Frame(t: tHeld, yaw: yaw, live: liveAt(now), mode: .manual)
    }
  }

  // MARK: touch

  private func unlockLocked(_ now: Double, yaw: Double) {
    let target: Double = yaw < 0 ? -360 : 360
    decay = nil
    dragging = false
    yawSpring = (yaw, target, now)
    storyStart = now
    lastActivity = now
    mode = .story
    unlockedAt = now
  }

  /// A touch: the film pauses where it is while the sculpture is still whole; once it has folded, the story runs on
  /// and the welcome screen holds for as long as touches come.
  private func touchLocked(_ now: Double) {
    let f = frameLocked(now)
    lastActivity = now
    switch mode {
    case .manual:
      if returning != nil {
        returning = nil
        tHeld = f.t
        yawStill = f.yaw
        decay = nil
      }
    case .auto:
      if f.t < M.turnEnd {
        tHeld = f.t
        yawStill = f.yaw
        decay = nil
        liveFade = (1, now)
        mode = .manual
      } else if f.t < M.holdEnd {
        storyStart = now - (f.t - M.storyT0)
        yawSpring = nil
        mode = .story
      }
    case .story:
      break
    }
  }

  func touch(_ now: Double) { locked { touchLocked(now) } }

  private var yawFrom = 0.0

  func dragBegan(_ now: Double) {
    locked {
      touchLocked(now)
      guard mode == .manual else { return }
      yawStill = manualYaw(now)
      decay = nil
      dragging = true
      yawFrom = yawStill
    }
  }

  func dragChanged(_ now: Double, dx: Double) {
    locked {
      guard mode == .manual, dragging else { return }
      lastActivity = now
      yawStill = yawFrom + dx * M.yawPerPt
    }
  }

  func dragEnded(_ now: Double, vx: Double) {
    locked {
      let was = dragging
      dragging = false
      lastActivity = now
      guard mode == .manual, was, !reduced else { return }
      decay = (yawStill, vx * M.yawPerPt, now)
    }
  }

  func skip(_ now: Double) {
    locked {
      touchLocked(now)
      if mode == .manual { unlockLocked(now, yaw: manualYaw(now)) }
    }
  }

  /// Whether a finger (or its momentum) is turning the sculpture, for the detent haptics.
  func isTurning(_ now: Double) -> Bool {
    locked { mode == .manual && returning == nil && (dragging || (decay != nil && abs(decayAt(now).v) > 1)) }
  }
}
