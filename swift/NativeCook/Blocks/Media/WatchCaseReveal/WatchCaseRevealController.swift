import Foundation
import QuartzCore

/// The block's one source of truth: the film's clock and whatever a finger set. `pose(at:)` is a pure function of
/// the stored fields and a media time (CACurrentMediaTime), read both by SceneKit's render loop (to pose the 3D
/// stage) and by SwiftUI's TimelineView (the backdrop, the specs, the picker, the button), so the two never disagree
/// and nothing writes SwiftUI state per frame. A touch freezes the current pose into the manual fields.
///
/// React Native's modes: the film (auto), a finger's values (manual), and the ease back 2 s after the last touch
/// (return: the pillow sinks, the lid closes in 2 s, then the film restarts in the chosen dial).
final class WatchCaseRevealController {
  typealias M = WatchCaseRevealMotion

  /// A value easing from `from` to `to` over `dur` seconds from media time `t0`.
  struct Ease {
    var from: Double, to: Double, t0: Double, dur: Double
    var curve: (Double) -> Double = { $0 }
    func at(_ now: Double) -> Double {
      if dur <= 0 { return to }
      return from + (to - from) * curve(M.clamp01((now - t0) / dur))
    }
    func done(_ now: Double) -> Bool { now >= t0 + dur }
    static func still(_ v: Double) -> Ease { Ease(from: v, to: v, t0: 0, dur: 0) }
  }

  /// A damped spring from `x0` (with velocity `v0`) to 0, released at `t0`: damping 11, stiffness 55, mass 1.
  struct Spring {
    var x0: Double, v0: Double, t0: Double
    func at(_ now: Double) -> Double {
      let w = sqrt(55.0), z = 11 / (2 * w), wd = w * sqrt(1 - z * z)
      let t = max(0, now - t0)
      return exp(-z * w * t) * (x0 * cos(wd * t) + (v0 + z * w * x0) / wd * sin(wd * t))
    }
    func done(_ now: Double) -> Bool { now - t0 > 2.5 }
  }

  enum Mode { case auto, manual }

  /// Reanimated's default withTiming curve.
  static func inOutQuad(_ x: Double) -> Double { x < 0.5 ? 2 * x * x : 1 - pow(-2 * x + 2, 2) / 2 }

  private let lock = NSLock()
  let film: Bool
  let reduced: Bool

  private(set) var mode: Mode
  /// Media time of the film's t = 0 (nil until the first frame asks).
  private var start: Double?
  private var home = 0
  // manual fields
  private var open: Ease
  private var yawEase = Ease.still(0)
  private var yawSpring: Spring?
  private var macro = Ease.still(0)
  private var dialFrom = 0, dialTo = 0
  private var dialK = Ease.still(0)
  private var secsAt = 0.0, secsMs = 0.0
  private var lastActivity = 0.0
  private var reservedUntil = -1.0
  var dragging = false

  init(autoPlay: Bool, reduced: Bool) {
    self.reduced = reduced
    film = autoPlay && !reduced
    mode = film ? .auto : .manual
    open = .still(reduced ? 2 : 0)
  }

  private func locked<T>(_ f: () -> T) -> T { lock.lock(); defer { lock.unlock() }; return f() }

  // MARK: reading

  struct Frame {
    var pose: WatchCaseRevealPose
    /// The dial the native picker shows (it switches as a blend starts) and the button's confirmation.
    var pickerDial: Int
    var reserved: Bool
    var filmPlaying: Bool
  }

  func frame(at now: Double) -> Frame { locked { frameLocked(now) } }

  private func endOfManual(_ now: Double) -> Double {
    var e = lastActivity
    e = max(e, open.t0 + open.dur, macro.t0 + macro.dur, dialK.t0 + dialK.dur, yawEase.t0 + yawEase.dur)
    if let s = yawSpring { e = max(e, s.t0 + 2.5) }
    return e
  }

  private func manualPose(_ now: Double) -> WatchCaseRevealPose {
    let yaw = yawSpring?.at(now) ?? yawEase.at(now)
    return WatchCaseRevealPose(t: 0, open: open.at(now), yaw: yaw, macro: macro.at(now),
                               dialFrom: dialFrom, dialTo: dialTo, dialK: dialK.at(now), secs: secsAt + (now - secsMs))
  }

  private func frameLocked(_ now: Double) -> Frame {
    if mode == .auto {
      if start == nil { start = now }
      let t = (now - start!).truncatingRemainder(dividingBy: M.loop)
      let p = WatchCaseRevealPose.film(max(0, t), home: home)
      return Frame(pose: p, pickerDial: p.dialK <= 0.001 ? p.dialFrom : p.dialTo, reserved: M.filmReserved(t), filmPlaying: true)
    }
    var p = manualPose(now)
    if film && !dragging {
      // 2 s after the last touch (and after what it set off) the case closes in 2 s, then the film starts again
      let r0 = endOfManual(now) + M.idle
      if now >= r0 {
        let at = manualPose(r0)
        let k = M.easeInOutTouch(M.clamp01((now - r0) / M.returnDuration))
        p.open = at.open * (1 - k)
        p.yaw = at.yaw * (1 - k)
        p.macro = at.macro * (1 - k)
        if at.dialTo != home || at.dialK < 1 {
          p.dialFrom = at.dialK >= 0.5 ? at.dialTo : at.dialFrom
          p.dialTo = home
          p.dialK = M.easeInOutSine(M.clamp01((now - r0) / M.dialBlend))
        }
        if now >= r0 + M.returnDuration {
          mode = .auto
          start = r0 + M.returnDuration
          return frameLocked(now)
        }
      }
    }
    return Frame(pose: p, pickerDial: p.dialK <= 0.001 ? p.dialFrom : p.dialTo,
                 reserved: now < reservedUntil, filmPlaying: false)
  }

  // MARK: touch

  /// Leaves the film (or the ease back) where it is: the current pose becomes the manual values.
  private func freeze(_ now: Double) {
    let f = frameLocked(now)
    let p = f.pose
    secsAt = p.secs
    secsMs = now
    mode = .manual
    open = .still(p.open)
    yawSpring = nil
    yawEase = .still(p.yaw)
    macro = .still(p.macro)
    dialFrom = p.dialFrom
    dialTo = p.dialTo
    dialK = .still(p.dialK)
    lastActivity = now
  }

  func touch(_ now: Double) { locked { freeze(now); lastActivity = now } }

  private var dragFrom = 0.0, yawFrom = 0.0
  private(set) var dragKind = 0  // 0 none, 1 lid, 2 turn

  func dragBegan(_ now: Double) {
    locked {
      freeze(now)
      dragging = true
      dragKind = 0
      dragFrom = open.to
      yawFrom = yawEase.to
    }
  }

  static let lidPt = 150.0, yawPerPt = 0.009, yawLimit = 0.8

  func dragChanged(_ now: Double, dx: Double, dy: Double) {
    locked {
      lastActivity = now
      if dragKind == 0 {
        if abs(dx) < 8 && abs(dy) < 8 { return }
        dragKind = abs(dx) > abs(dy) && dragFrom > 1.5 ? 2 : 1
      }
      if dragKind == 1 {
        open = .still(min(2, max(0, dragFrom - dy / Self.lidPt)))
      } else {
        var v = yawFrom + dx * Self.yawPerPt
        let over = abs(v) - Self.yawLimit
        if over > 0 { v = (v < 0 ? -1 : 1) * (Self.yawLimit + over / (1 + over * 2.5)) }
        yawEase = .still(v)
      }
    }
  }

  func dragEnded(_ now: Double, vx: Double, vy: Double) {
    locked {
      dragging = false
      lastActivity = now
      if dragKind == 1 {
        let projected = open.to - vy / Self.lidPt * 0.25
        animateOpen(now, to: projected > 0.6 ? 2 : 0)
      } else if dragKind == 2 {
        yawSpring = Spring(x0: yawEase.to, v0: vx * Self.yawPerPt, t0: now)
      }
    }
  }

  private func animateOpen(_ now: Double, to target: Double) {
    let cur = open.at(now)
    let dur = reduced ? 0.22 : max(0.5, abs(target - cur) * 1.15)
    open = Ease(from: cur, to: target, t0: now, dur: dur, curve: target > cur ? M.easeHinge : M.easeInOutTouch)
  }

  func tap(_ now: Double) {
    locked {
      freeze(now)
      if open.to < 0.5 { animateOpen(now, to: 2) }
    }
  }

  func pickDial(_ i: Int, now: Double) {
    locked {
      freeze(now)
      home = i
      let k = dialK.to
      let current = k >= 0.5 ? dialTo : dialFrom
      if current == i && (k >= 1 || k == 0) { return }
      dialFrom = current
      dialTo = i
      dialK = Ease(from: 0, to: 1, t0: now, dur: reduced ? 0.2 : M.dialBlend, curve: Self.inOutQuad)
    }
  }

  func reserve(_ now: Double) {
    locked {
      freeze(now)
      reservedUntil = now + M.reservedTouch
    }
  }

  /// Whether anything still moves without a touch (the second hand always does while the film or a stage is up).
  var isTurning: Bool { locked { dragKind == 2 && dragging } }
}
