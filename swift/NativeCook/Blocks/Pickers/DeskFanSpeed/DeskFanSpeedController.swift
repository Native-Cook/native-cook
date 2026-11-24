import Foundation
import QuartzCore
import UIKit

/// The block's one source of truth: the film's clock and whatever a finger set. `frame(at:)` is read by SceneKit's
/// render loop (the clock for the 3D) and by SwiftUI's TimelineView (the sheet, the airflow and the film's finger), so
/// the two never disagree and nothing writes SwiftUI state per frame.
///
/// React Native's modes: the film (auto), a finger's values (manual: the motor chases the chosen level with inertia),
/// and the way home 2 s after the last touch (return: Off, the blades coast to a stop on a quarter turn, the head comes
/// home, and 1.3 s later the film restarts). Every value is a closed-form function of time, so the blades' angle is
/// exact whichever thread asks.
final class DeskFanSpeedController {
  typealias M = DeskFanSpeedMotion

  struct Frame {
    var pose: DeskFanSpeedPose
    var t: Double
    var level: Int
    var filming: Bool
  }

  /// A damped spring towards `target`, from the offset `x0` with velocity `v0` at `t0` (mass 1).
  private struct Spring {
    var target: Double
    var x0 = 0.0, v0 = 0.0, t0 = 0.0
    let z: Double, w: Double

    init(from x: Double, to target: Double, velocity: Double, at now: Double, stiffness: Double = 150, damping: Double = 18) {
      self.target = target
      x0 = x - target
      v0 = velocity
      t0 = now
      z = damping / 2
      w = (stiffness - z * z).squareRoot()
    }

    func at(_ now: Double) -> Double {
      let s = max(0, now - t0), b = (v0 + z * x0) / w
      return target + exp(-z * s) * (x0 * cos(w * s) + b * sin(w * s))
    }
  }

  private struct Run {
    var from: Double, to: Double, t0: Double, dur: Double
    var ease: (Double) -> Double
    func at(_ now: Double) -> Double {
      if dur <= 0 || now >= t0 + dur { return to }
      if now <= t0 { return from }
      return from + (to - from) * ease((now - t0) / dur)
    }
  }

  private enum Value {
    case held(Double)
    case spring(Spring)
    case run(Run)

    func at(_ now: Double) -> Double {
      switch self {
      case .held(let v): return v
      case .spring(let s): return s.at(now)
      case .run(let r): return r.at(now)
      }
    }
  }

  /// The motor: from speed `s0` and angle `turns0` at `t0`, the speed chases `target` exponentially with `tau`, and the
  /// angle is that speed's exact integral.
  private struct Motor {
    var t0: Double, s0: Double, turns0: Double, target: Double, tau: Double
    var still = false

    func speed(_ now: Double) -> Double {
      if still { return s0 }
      return target + (s0 - target) * exp(-max(0, now - t0) / tau)
    }

    func turns(_ now: Double) -> Double {
      if still { return turns0 }
      let dt = max(0, now - t0)
      return turns0 + target * dt + (s0 - target) * tau * (1 - exp(-dt / tau))
    }
  }

  private enum Mode { case auto, manual, home }

  // React Native's numbers
  private static let idle = 2.0
  private static let returnTime = 1.3
  private static let stall = 0.1
  private static let frameTime = 1.0 / 60
  private static let keyRate = 11.0 * 3
  /// Reduced motion's still: level 2, the head at its rest angle.
  private static let reducedLevel = 2

  private static func bezier(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> (Double) -> Double {
    { x in
      if x <= 0 { return 0 }
      if x >= 1 { return 1 }
      func f(_ a: Double, _ b: Double, _ s: Double) -> Double { 3 * a * s * (1 - s) * (1 - s) + 3 * b * s * s * (1 - s) + s * s * s }
      var lo = 0.0, hi = 1.0, s = x
      for _ in 0..<30 {
        let v = f(x1, x2, s)
        if abs(v - x) < 1e-7 { break }
        if v < x { lo = s } else { hi = s }
        s = (lo + hi) / 2
      }
      return f(y1, y2, s)
    }
  }

  private static let easeOut = bezier(0.23, 1, 0.32, 1)
  private static let easeInOut = bezier(0.65, 0, 0.35, 1)

  private let lock = NSLock()
  let film: Bool
  let reduced: Bool

  private var mode: Mode
  private var ready = false
  private var start: Double?
  private var lastNow: Double?
  private var filmT = 0.0

  private var level: Int
  private var motor: Motor
  /// When set (the way home), the blades and the speed follow these runs instead of the motor.
  private var coast: (turns: Run, speed: Run)?
  private var keysFrom: [Double]
  private var keysAt = 0.0
  private var yaw: Value
  private var dragging = false
  private var dragFrom = 0.0
  private var lastTouch: Double?
  private var returnAt = 0.0

  init(autoPlay: Bool, reduced: Bool) {
    self.reduced = reduced
    film = autoPlay && !reduced
    mode = film ? .auto : .manual
    // reduced motion: a still, the level shown, the blades standing
    level = reduced ? Self.reducedLevel : 0
    motor = Motor(t0: 0, s0: 0, turns0: 0, target: 0, tau: M.spinUpTau, still: reduced)
    keysFrom = Self.oneHot(level)
    yaw = .held(M.yawRest)
  }

  private static func oneHot(_ v: Int) -> [Double] { (0..<4).map { $0 == v ? 1 : 0 } }

  private func locked<T>(_ f: () -> T) -> T { lock.lock(); defer { lock.unlock() }; return f() }

  /// The model is drawn and its shaders are compiled: the film may start.
  func markReady() { locked { ready = true } }

  func frame(at now: Double) -> Frame { locked { frameLocked(now) } }

  private func keysValue(_ now: Double) -> [Double] {
    if reduced { return Self.oneHot(level) }
    let k = exp(-Self.keyRate * max(0, now - keysAt))
    let to = Self.oneHot(level)
    return (0..<4).map { to[$0] + (keysFrom[$0] - to[$0]) * k }
  }

  private func turnsValue(_ now: Double) -> Double { coast?.turns.at(now) ?? motor.turns(now) }
  private func speedValue(_ now: Double) -> Double { coast?.speed.at(now) ?? motor.speed(now) }

  private func frameLocked(_ now: Double) -> Frame {
    // a frame that arrives late is a stall: the film picks up where it was instead of skipping what it covered
    if let last = lastNow, now - last > Self.stall, mode == .auto, start != nil { start! += now - last - Self.frameTime }
    lastNow = max(lastNow ?? now, now)

    if mode == .auto {
      guard ready else { return Frame(pose: DeskFanSpeedPose(), t: 0, level: 0, filming: true) }
      if start == nil { start = now }
      var el = now - start!
      if el >= M.loop {
        let n = floor(el / M.loop)
        start! += n * M.loop
        el -= n * M.loop
      }
      el = max(0, el)
      filmT = el
      let pose = DeskFanSpeedPose(turns: M.filmTurns(el), speed: M.filmSpeed(el), yaw: M.filmYaw(el), keys: M.filmKeys(el))
      return Frame(pose: pose, t: el, level: M.filmLevel(el), filming: true)
    }

    if mode == .home {
      if now - returnAt >= Self.returnTime {
        // Off, the blades on a quarter turn (the four blades look the same), the head home: the film starts over
        coast = nil
        motor = Motor(t0: now, s0: 0, turns0: 0, target: 0, tau: M.spinDownTau)
        yaw = .held(M.yawRest)
        start = now
        mode = .auto
        return frameLocked(now)
      }
    } else if lastTouch == nil || dragging {
      lastTouch = now
    } else if film, now - lastTouch! >= Self.idle {
      // 2 s after the last touch: Off, the blades coast to a stop on a quarter turn, the head comes home
      mode = .home
      returnAt = now
      let tr = turnsValue(now), sp = speedValue(now)
      level = 0
      keysFrom = Self.oneHot(0)
      keysAt = now
      let stop = ceil((tr + sp * 0.42) * 4) / 4
      let dur = Self.returnTime - 0.06
      coast = (Run(from: tr, to: stop, t0: now, dur: dur, ease: Self.easeOut), Run(from: sp, to: 0, t0: now, dur: dur, ease: Self.easeOut))
      yaw = .run(Run(from: yaw.at(now), to: M.yawRest, t0: now, dur: dur, ease: Self.easeInOut))
    }
    let pose = DeskFanSpeedPose(turns: turnsValue(now), speed: speedValue(now), yaw: yaw.at(now), keys: keysValue(now))
    return Frame(pose: pose, t: filmT, level: level, filming: false)
  }

  /// The motor chases the current level from where it is now.
  private func rebase(_ now: Double, s: Double, turns: Double) {
    let target = M.speeds[level]
    motor = Motor(t0: now, s0: s, turns0: turns, target: target, tau: target > s ? M.spinUpTau : M.spinDownTau, still: reduced)
  }

  /// A touch takes over from wherever the film (or the return) is.
  private func pause(_ now: Double) {
    let f = frameLocked(now)
    if mode == .auto {
      level = f.level
      keysFrom = f.pose.keys
      keysAt = now
      yaw = .held(f.pose.yaw)
      coast = nil
      rebase(now, s: f.pose.speed, turns: f.pose.turns)
      start = nil
    } else if mode == .home {
      // the way home stops where it is; the motor takes over from there
      coast = nil
      yaw = .held(f.pose.yaw)
      rebase(now, s: f.pose.speed, turns: f.pose.turns)
    }
    mode = .manual
    lastTouch = nil
  }

  /// A finger picked a level on the segmented control.
  func userLevel(_ v: Int, now: Double) {
    locked {
      pause(now)
      let s = speedValue(now), tr = turnsValue(now)
      keysFrom = keysValue(now)
      keysAt = now
      level = v
      rebase(now, s: s, turns: tr)
    }
  }

  func dragBegan(now: Double) {
    locked {
      pause(now)
      dragging = true
      let y = yaw.at(now)
      yaw = .held(y)
      dragFrom = y
    }
  }

  func dragMoved(dx: Double, width: Double, now: Double) {
    locked {
      guard dragging else { return }
      yaw = .held(M.aimFromDrag(dragFrom, dx, width))
    }
  }

  func dragEnded(velocity vx: Double, width: Double, now: Double) {
    locked {
      guard dragging else { return }
      dragging = false
      let y = yaw.at(now)
      let v = vx / width * M.aimSpan
      let to = max(-M.aimMax, min(M.aimMax, y + v * 0.12))
      yaw = reduced ? .held(to) : .spring(Spring(from: y, to: to, velocity: v, at: now))
    }
  }
}
