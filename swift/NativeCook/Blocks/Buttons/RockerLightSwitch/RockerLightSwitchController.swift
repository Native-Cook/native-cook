import Foundation
import QuartzCore

/// The block's one source of truth: the film's clock and whatever a finger set. `frame(at:)` is a function of the
/// stored fields and a media time (CACurrentMediaTime), read both by SceneKit's render loop (to pose the 3D stage)
/// and by SwiftUI's TimelineView (the dusk, the halo, the touch dot, the sheet), so the two never disagree and
/// nothing writes SwiftUI state per frame.
///
/// React Native's modes: the film (auto), a finger's values (manual), and the way home 2 s after the last touch
/// (return: the rocker snaps off, the slider and the camera ease home, and 1.1 s later the film restarts).
final class RockerLightSwitchController {
  typealias M = RockerLightSwitchMotion

  enum Curve {
    case linear, inQuad, outCubic, inOut
    func at(_ x: Double) -> Double {
      switch self {
      case .linear: return x
      case .inQuad: return x * x
      case .outCubic: return 1 - pow(1 - x, 3)
      case .inOut: return RockerLightSwitchController.bezier(0.65, 0, 0.35, 1, x)
      }
    }
  }

  /// cubic-bezier(x1, y1, x2, y2) at x (Newton on the x polynomial).
  static func bezier(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double, _ x: Double) -> Double {
    if x <= 0 { return 0 }
    if x >= 1 { return 1 }
    func f(_ a: Double, _ b: Double, _ s: Double) -> Double { 3 * a * s * (1 - s) * (1 - s) + 3 * b * s * s * (1 - s) + s * s * s }
    var lo = 0.0, hi = 1.0, s = x
    for _ in 0..<24 {
      let v = f(x1, x2, s)
      if abs(v - x) < 1e-6 { break }
      if v < x { lo = s } else { hi = s }
      s = (lo + hi) / 2
    }
    return f(y1, y2, s)
  }

  /// A value that waits until `t0`, then eases from `from` to `to` over `dur` seconds.
  struct Ease {
    var from: Double, to: Double, t0: Double, dur: Double
    var curve = Curve.linear
    func at(_ now: Double) -> Double {
      if dur <= 0 { return now >= t0 ? to : from }
      return from + (to - from) * curve.at(M.clamp01((now - t0) / dur))
    }
    static func still(_ v: Double) -> Ease { Ease(from: v, to: v, t0: 0, dur: 0) }
  }

  /// The rocker: an optional timed lead-in (a press to the tipping point, or the little give of a half already in)
  /// and then the snap spring to `target` (stiffness 1096, damping 28, mass 1), or a value held still.
  struct Rocker {
    var from = 0.0, tip = 0.0, t0 = 0.0, dur = 0.0
    var curve = Curve.inQuad
    var target = 0.0
    var velocity = 0.0
    var held: Double?

    func at(_ now: Double) -> Double {
      if let held { return held }
      let tau = now - t0
      if tau < dur { return from + (tip - from) * curve.at(M.clamp01(tau / dur)) }
      let s = tau - dur
      let x0 = tip - target
      return target + exp(-M.snapZ * s) * (x0 * cos(M.snapW * s) + (velocity + M.snapZ * x0) / M.snapW * sin(M.snapW * s))
    }
    static func still(_ v: Double) -> Rocker { Rocker(held: v) }
  }

  enum Mode { case auto, manual, home }

  struct Frame {
    var pose: RockerLightSwitchPose
    /// The film's finger: opacity, half (+1 top, -1 bottom), pressed.
    var ghost: (opacity: Double, half: Double, pressed: Double)
    var lit: Bool
    /// The slider's value on show (the film moves it in steps of 2 %).
    var slider: Double
    /// How many times the rocker has clicked across the middle (for the haptic).
    var clicks: Int
  }

  private let lock = NSLock()
  let film: Bool
  let reduced: Bool

  private var mode: Mode
  private var start: Double?
  private var rocker: Rocker
  private var level: Ease
  private var bright = Ease.still(1)
  private var orbit = Ease.still(0)
  private var drift = Ease.still(1)
  private var heldT = 0.0
  private var latched: Bool
  private var clicks = 0
  private var lastActivity = 0.0
  private var returnAt = 0.0
  private var dragging = false
  private var sliding = false
  private var dragMode = false
  private var fromX = 0.0, fromY = 0.0

  init(autoPlay: Bool, reduced: Bool) {
    self.reduced = reduced
    film = autoPlay && !reduced
    mode = film ? .auto : .manual
    // Reduced motion rests with the light on.
    rocker = .still(reduced ? 1 : 0)
    level = .still(reduced ? 1 : 0)
    latched = reduced
  }

  private func locked<T>(_ f: () -> T) -> T { lock.lock(); defer { lock.unlock() }; return f() }

  // MARK: reading

  func frame(at now: Double) -> Frame { locked { frameLocked(now) } }

  private func frameLocked(_ now: Double) -> Frame {
    if mode == .auto {
      if start == nil { start = now }
      let t = max(0, now - start!).truncatingRemainder(dividingBy: M.loop)
      let p = RockerLightSwitchPose(t: t, drift: 1, press: M.filmPress(t), level: M.filmLevel(t), bright: M.filmBright(t), orbit: M.filmOrbit(t))
      let on = p.press >= 0.5
      if on != latched { latched = on; clicks += 1 }
      return Frame(pose: p, ghost: M.filmGhost(t), lit: on, slider: (p.bright * 50).rounded() / 50, clicks: clicks)
    }
    if mode == .home, now - returnAt >= M.returnTime {
      // the switch is off again and the camera home: the film starts over
      mode = .auto
      start = returnAt + M.returnTime
      latched = false
      return frameLocked(now)
    }
    if mode == .manual, film, !dragging, !sliding, now - lastActivity >= M.idle {
      // 2 s after the last touch: the rocker snaps off, the slider and the camera ease home
      mode = .home
      returnAt = now
      let cur = rocker.at(now)
      if cur >= 0.5 {
        rocker = Rocker(from: cur, tip: 1 - M.pressDepth, t0: now, dur: 0.13, curve: .inQuad, target: 0)
      } else {
        rocker = Rocker(from: cur, tip: cur, t0: now, dur: 0, target: 0)
      }
      bright = Ease(from: bright.at(now), to: 1, t0: now, dur: 0.5, curve: .inOut)
      orbit = Ease(from: orbit.at(now), to: 0, t0: now, dur: 0.8, curve: .inOut)
      drift = Ease(from: drift.at(now), to: 0, t0: now, dur: 0.8, curve: .inOut)
    }
    let press = rocker.at(now)
    let on = press >= 0.5
    if on != latched {
      // the latch: a click, and the filament warming up or dying
      latched = on
      clicks += 1
      let cur = level.at(now)
      if reduced {
        level = Ease(from: cur, to: on ? 1 : 0, t0: now, dur: 0.2)
      } else if on {
        level = Ease(from: cur, to: 1, t0: now + M.lag, dur: M.warm, curve: .outCubic)
      } else {
        level = Ease(from: cur, to: 0, t0: now + M.lagOff, dur: M.cool, curve: .outCubic)
      }
    }
    let b = bright.at(now)
    let p = RockerLightSwitchPose(t: heldT, drift: drift.at(now), press: press, level: level.at(now), bright: b, orbit: orbit.at(now))
    return Frame(pose: p, ghost: (0, 1, 0), lit: on, slider: sliding ? b : (b * 50).rounded() / 50, clicks: clicks)
  }

  // MARK: touch

  /// Leaves the film (or the way home) where it is.
  private func pause(_ now: Double) {
    let f = frameLocked(now)
    if mode == .auto {
      heldT = f.pose.t
      rocker = .still(f.pose.press)
      // the light settles to what the latch says
      level = Ease(from: f.pose.level, to: f.pose.press >= 0.5 ? 1 : 0, t0: now, dur: 0.3, curve: .outCubic)
      bright = .still(f.pose.bright)
      orbit = .still(f.pose.orbit)
      drift = .still(1)
    } else if mode == .home {
      bright = .still(f.pose.bright)
      orbit = .still(f.pose.orbit)
      drift = .still(f.pose.drift)
    }
    mode = .manual
    lastActivity = now
  }

  /// A finger lands on the plate. `top`: on the half that turns the light on.
  func pressBegan(_ now: Double, top wantOn: Bool) {
    locked {
      pause(now)
      dragging = true
      dragMode = false
      let cur = rocker.at(now)
      let isOn = cur >= 0.5
      let target = wantOn ? 1.0 : 0.0
      if wantOn != isOn {
        // a press on the raised half tips the rocker
        rocker = Rocker(from: cur, tip: wantOn ? M.pressDepth : 1 - M.pressDepth, t0: now, dur: reduced ? 0.06 : 0.13, curve: .inQuad, target: target)
      } else {
        // on the half already in, it only gives a little
        rocker = Rocker(from: cur, tip: wantOn ? 1.05 : -0.05, t0: now, dur: 0.09, curve: .outCubic, target: target)
      }
    }
  }

  static let dragPt = 84.0

  func pressMoved(_ now: Double, dy: Double) {
    locked {
      lastActivity = now
      if !dragMode {
        if abs(dy) < 8 { return }
        // a drag takes the rocker over from wherever the press left it
        dragMode = true
        fromX = M.detentInverse(min(1, max(0, rocker.at(now))))
        fromY = dy
      }
      // dragging up tips it on; rubber-banded past either rest
      var x = fromX - (dy - fromY) / Self.dragPt
      if x > 1 { x = 1 + (x - 1) / (1 + (x - 1) * 6) * 0.25 } else if x < 0 { x = x / (1 - x * 6) * 0.25 }
      rocker = .still(x > 1 || x < 0 ? x : M.detent(x))
    }
  }

  /// `vy`: the finger's vertical velocity, pt/s.
  func pressEnded(_ now: Double, vy: Double) {
    locked {
      lastActivity = now
      dragging = false
      guard dragMode else { return }
      dragMode = false
      // released: it falls to the side it is on (a flick counts), with the finger's velocity
      let v = -vy / Self.dragPt
      let cur = rocker.at(now)
      rocker = Rocker(from: cur, tip: cur, t0: now, dur: 0, target: cur + v * 0.08 >= 0.5 ? 1 : 0, velocity: v)
    }
  }

  func setBright(_ v: Double, now: Double) {
    locked {
      pause(now)
      bright = .still(min(1, max(M.brightMin, v)))
    }
  }

  func setSliding(_ active: Bool, now: Double) {
    locked {
      if active { pause(now) }
      sliding = active
      lastActivity = now
    }
  }
}
