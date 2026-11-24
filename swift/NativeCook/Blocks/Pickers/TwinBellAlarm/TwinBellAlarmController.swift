import Foundation
import QuartzCore

/// The block's one source of truth: the film's clock and whatever a finger set. `frame(at:)` is read by SceneKit's
/// render loop (the clock) and by SwiftUI's TimelineView (the touch dots and the native controls' values), so the two
/// never disagree and nothing writes SwiftUI state per frame.
///
/// React Native's modes: the film (auto), a finger's values (manual), and the way home 2 s after the last touch
/// (return: the wheel and the hands go back to 7:00, the switch goes off, the camera eases home, and 1 s later the film
/// restarts).
final class TwinBellAlarmController {
  typealias M = TwinBellAlarmMotion

  struct Frame {
    var pose: TwinBellAlarmPose
    var t: Double
    var wheel: Int
    var armed: Bool
    var filming: Bool
  }

  /// A damped spring towards `target`, from the offset `x0` with velocity `v0` at `t0`.
  private struct Spring {
    var target: Double
    var x0 = 0.0, v0 = 0.0, t0 = 0.0
    var z: Double, w: Double

    init(_ v: Double, stiffness: Double, damping: Double) {
      target = v
      z = damping / 2
      w = (stiffness - z * z).squareRoot()
    }

    func at(_ now: Double) -> Double {
      let s = max(0, now - t0), b = (v0 + z * x0) / w
      return target + exp(-z * s) * (x0 * cos(w * s) + b * sin(w * s))
    }

    func velocity(_ now: Double) -> Double {
      let s = max(0, now - t0), b = (v0 + z * x0) / w, c = cos(w * s), n = sin(w * s)
      return exp(-z * s) * (-z * (x0 * c + b * n) + w * (b * c - x0 * n))
    }

    mutating func go(to v: Double, now: Double) {
      let x = at(now), u = velocity(now)
      target = v
      x0 = x - v
      v0 = u
      t0 = now
    }

    mutating func set(_ v: Double) {
      target = v
      x0 = 0
      v0 = 0
    }
  }

  /// cubic-bezier(0.65, 0, 0.35, 1).
  private static func easeInOut(_ x: Double) -> Double {
    if x <= 0 { return 0 }
    if x >= 1 { return 1 }
    func f(_ a: Double, _ b: Double, _ s: Double) -> Double { 3 * a * s * (1 - s) * (1 - s) + 3 * b * s * s * (1 - s) + s * s * s }
    var lo = 0.0, hi = 1.0, s = x
    for _ in 0..<24 {
      let v = f(0.65, 0.35, s)
      if abs(v - x) < 1e-6 { break }
      if v < x { lo = s } else { hi = s }
      s = (lo + hi) / 2
    }
    return f(0, 1, s)
  }

  private struct Run {
    var from: Double, to: Double, t0: Double, dur: Double
    var end: Double { t0 + dur }
    func at(_ now: Double) -> Double {
      if now <= t0 { return from }
      return from + (to - from) * TwinBellAlarmController.easeInOut(M.clamp01((now - t0) / dur))
    }
  }

  private enum Mode { case auto, manual, home }

  private static let idle = 2.0
  private static let returnTime = 1.0
  private static let stall = 0.1
  private static let frameTime = 1.0 / 60
  private static let pushIn = 0.65, pushHold = 0.9, pushBack = 1.1

  private let lock = NSLock()
  let film: Bool
  let reduced: Bool
  private var mode: Mode
  private var ready = false
  private var start: Double?
  private var lastNow: Double?
  private var lastTouch = 0.0
  private var returnAt = 0.0
  private var dragging = false
  private var dragFrom = 0.0

  private var wheel: Int
  private var armed: Bool
  private var hour: Spring
  private var minute: Spring
  private var ringStart: Double?
  private var push: [Run] = []
  private var pushStill = 0.0
  private var orbitHeld: Double? = 0
  private var orbitSpring = Spring(0, stiffness: 140, damping: 17)
  private var orbitRun: Run?

  init(autoPlay: Bool, reduced: Bool) {
    self.reduced = reduced
    film = autoPlay && !reduced
    mode = film ? .auto : .manual
    // reduced motion rests on the film's result: 6:30, alarm on, nothing moving
    let minutes = reduced ? M.filmMin : M.restMin
    wheel = minutes
    armed = reduced
    hour = Spring(M.hourDeg(minutes), stiffness: 150, damping: 15)
    minute = Spring(M.minuteDeg(minutes), stiffness: 150, damping: 15)
  }

  private func locked<T>(_ f: () -> T) -> T { lock.lock(); defer { lock.unlock() }; return f() }

  /// The model is drawn and its shaders are compiled: the film may start.
  func markReady() { locked { ready = true } }

  func frame(at now: Double) -> Frame { locked { frameLocked(now) } }

  private func pushAt(_ now: Double) -> Double {
    for r in push where now < r.end { return r.at(now) }
    return push.last?.to ?? pushStill
  }

  private func orbitAt(_ now: Double) -> Double {
    if let orbitHeld { return orbitHeld }
    if let orbitRun { return now >= orbitRun.end ? orbitRun.to : orbitRun.at(now) }
    return now - orbitSpring.t0 > 3 ? 0 : orbitSpring.at(now)
  }

  private func ringAt(_ now: Double) -> Double {
    guard let s = ringStart else { return -1 }
    let tau = now - s
    if tau <= M.ring { return max(0, tau) }
    ringStart = nil
    return -1
  }

  private func frameLocked(_ now: Double) -> Frame {
    // a frame that arrives late is a stall: the film picks up where it was instead of skipping what it covered
    if let last = lastNow, now - last > Self.stall, mode == .auto, start != nil { start! += now - last - Self.frameTime }
    lastNow = max(lastNow ?? now, now)

    if mode == .auto {
      guard ready else {
        return Frame(pose: TwinBellAlarmPose(hour: M.filmHour(0), minute: M.filmMinute(0)), t: 0, wheel: M.restMin, armed: false, filming: true)
      }
      if start == nil { start = now }
      var el = now - start!
      if el >= M.loop {
        let n = floor(el / M.loop)
        start! += n * M.loop
        el -= n * M.loop
      }
      el = max(0, el)
      let p = TwinBellAlarmPose(hour: M.filmHour(el), minute: M.filmMinute(el), ring: M.filmRing(el), push: M.filmPush(el), orbit: orbitAt(now))
      return Frame(pose: p, t: el, wheel: M.filmWheel(el), armed: M.filmArmed(el), filming: true)
    }
    if mode == .home, now - returnAt >= Self.returnTime {
      // the alarm is back at 7:00 and off, the camera home: the film starts over
      mode = .auto
      start = now
      orbitRun = nil
      orbitHeld = 0
      push = []
      pushStill = 0
      return frameLocked(now)
    }
    if mode == .manual, film, !dragging, now - lastTouch >= Self.idle {
      // 2 s after the last touch: the wheel and the hands go back to 7:00, the switch goes off, the camera eases home
      mode = .home
      returnAt = now
      wheel = M.restMin
      armed = false
      sweep(to: M.restMin, now: now)
      quiet(now)
      orbitRun = Run(from: orbitAt(now), to: 0, t0: now, dur: 0.7)
      orbitHeld = nil
    }
    let p = TwinBellAlarmPose(hour: hour.at(now), minute: minute.at(now), ring: ringAt(now), push: pushAt(now), orbit: orbitAt(now))
    return Frame(pose: p, t: 0, wheel: wheel, armed: armed, filming: false)
  }

  // MARK: touch

  /// The hands go to a time by the shortest way round, each on its own spring.
  private func sweep(to min: Int, now: Double) {
    let h = hour.at(now), m = minute.at(now)
    let toH = h + M.shortest(M.hourDeg(min) - h), toM = m + M.shortest(M.minuteDeg(min) - m)
    if reduced {
      hour.set(toH)
      minute.set(toM)
    } else {
      hour.go(to: toH, now: now)
      minute.go(to: toM, now: now)
    }
  }

  /// The ring stops: what is left of it is its dying away, and the camera eases back.
  private func quiet(_ now: Double) {
    if let s = ringStart, now - s < M.ring - M.ringOut { ringStart = now - (M.ring - M.ringOut) }
    let cur = pushAt(now)
    push = [Run(from: cur, to: 0, t0: now, dur: Self.pushBack)]
  }

  /// A touch takes over from wherever the film (or the return) is.
  private func pause(_ now: Double) {
    let f = frameLocked(now)
    if mode == .auto {
      // a ring in progress carries on, on its own clock; the camera eases back after it
      wheel = f.wheel
      armed = f.armed
      hour.set(f.pose.hour)
      minute.set(f.pose.minute)
      let rt = f.pose.ring
      if rt >= 0 {
        ringStart = now - rt
        push = [Run(from: f.pose.push, to: 0, t0: now + (M.ring - rt), dur: Self.pushBack)]
      } else if f.pose.push > 0 {
        push = [Run(from: f.pose.push, to: 0, t0: now, dur: Self.pushBack)]
      } else {
        push = []
        pushStill = 0
      }
      // the hands may be mid-sweep: they finish at the time the wheel shows
      sweep(to: wheel, now: now)
      start = nil
    } else if mode == .home {
      orbitHeld = orbitAt(now)
      orbitRun = nil
    }
    mode = .manual
    lastTouch = now
  }

  func userMinutes(_ min: Int, now: Double) {
    locked {
      pause(now)
      wheel = min
      sweep(to: min, now: now)
    }
  }

  /// A finger flipped the switch: on rings (the camera moves in, holds, comes back), off stops it.
  func userToggle(_ on: Bool, now: Double) {
    locked {
      pause(now)
      armed = on
      if on, !reduced {
        ringStart = now
        let cur = pushAt(now)
        push = [
          Run(from: cur, to: 1, t0: now, dur: Self.pushIn),
          Run(from: 1, to: 0, t0: now + Self.pushIn + Self.pushHold, dur: Self.pushBack),
        ]
        return
      }
      quiet(now)
    }
  }

  func dragBegan(now: Double, width: Double) {
    locked {
      pause(now)
      dragging = true
      let cur = orbitAt(now)
      orbitHeld = cur
      orbitRun = nil
      // pick the clock up from wherever its swing back had got to
      dragFrom = (-cur / M.orbitSpan) * width
    }
  }

  func dragMoved(dx: Double, width: Double, now: Double) {
    locked {
      lastTouch = now
      orbitHeld = M.orbitFromDrag(dragFrom + dx, width: width)
    }
  }

  func dragEnded(velocity vx: Double, width: Double, now: Double) {
    locked {
      let cur = orbitAt(now)
      orbitSpring = Spring(0, stiffness: 140, damping: 17)
      orbitSpring.x0 = cur
      orbitSpring.v0 = (-vx / width) * M.orbitSpan
      orbitSpring.t0 = now
      orbitHeld = nil
      orbitRun = nil
      dragging = false
      lastTouch = now
    }
  }
}
