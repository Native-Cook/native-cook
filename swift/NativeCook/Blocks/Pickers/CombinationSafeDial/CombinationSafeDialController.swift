import Foundation
import QuartzCore
import UIKit

/// The block's one source of truth: the film's clock and whatever a finger set. `frame(at:)` is read by SceneKit's
/// render loop (the clock for the 3D) and by SwiftUI's TimelineView (the sheet, the glow and the film's finger), so the
/// two never disagree and nothing writes SwiftUI state per frame.
///
/// React Native's modes: the film (auto), a finger's values (manual), and the way home 2 s after the last touch
/// (return: the door shuts, the entries clear, the dial goes home by the short way, and 1.25 s later the film restarts).
final class CombinationSafeDialController {
  typealias M = CombinationSafeDialMotion

  struct Frame {
    var pose: CombinationSafeDialPose
    var t: Double
    var count: Int
    var entries: [Int]
    var wrong: Double
    var canLock: Bool
    var filming: Bool
  }

  /// A damped spring towards `target`, from the offset `x0` with velocity `v0` at `t0` (mass 1).
  private struct Spring {
    var target: Double
    var x0 = 0.0, v0 = 0.0, t0 = 0.0
    let z: Double, w: Double

    init(from x: Double, to target: Double, velocity: Double, at now: Double, stiffness: Double = 170, damping: Double = 22) {
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

  /// A timed run from `from` to `to`, with an easing.
  private struct Run {
    var from: Double, to: Double, t0: Double, dur: Double
    var ease: (Double) -> Double
    func at(_ now: Double) -> Double {
      if dur <= 0 || now >= t0 + dur { return to }
      if now <= t0 { return from }
      return from + (to - from) * ease((now - t0) / dur)
    }
  }

  private enum Dial {
    case held(Double)
    case spring(Spring)
    case run(Run)
  }

  private enum Mode { case auto, manual, home }

  // React Native's numbers
  private static let idle = 2.0
  private static let returnTime = 1.25
  private static let stall = 0.1
  private static let frameTime = 1.0 / 60
  private static let flick = 0.16
  private static let settled = 0.012
  private static let shakeM = 0.007
  private static let shakeStep = 0.055
  private static let wrongHold = 0.52
  private static let relockTime = 1.2

  /// Reanimated's withTiming default, Easing.inOut(Easing.quad).
  private static func quadInOut(_ x: Double) -> Double { x < 0.5 ? 2 * x * x : 1 - pow(-2 * x + 2, 2) / 2 }
  private static func linear(_ x: Double) -> Double { x }
  /// cubic-bezier(0.65, 0, 0.35, 1).
  private static func easeInOut(_ x: Double) -> Double {
    if x <= 0 { return 0 }
    if x >= 1 { return 1 }
    func f(_ a: Double, _ b: Double, _ s: Double) -> Double { 3 * a * s * (1 - s) * (1 - s) + 3 * b * s * s * (1 - s) + s * s * s }
    var lo = 0.0, hi = 1.0, s = x
    for _ in 0..<30 {
      let v = f(0.65, 0.35, s)
      if abs(v - x) < 1e-7 { break }
      if v < x { lo = s } else { hi = s }
      s = (lo + hi) / 2
    }
    return f(0, 1, s)
  }

  private let lock = NSLock()
  let film: Bool
  let reduced: Bool
  let code: (Int, Int, Int)
  var onUnlock: (() -> Void)?

  private var mode: Mode
  private var ready = false
  private var start: Double?
  private var lastNow: Double?
  private var filmT = 0.0

  private var dial: Dial
  private var unlock: Run
  private var wrong: Run
  private var shakeStart: Double?
  private var count: Int
  private var entries: [Int]
  private var canLock: Bool
  private var clearAt: Double?
  private var landing: Double?
  private var turned = false
  private var dragging = false
  private var lastAngle = 0.0
  private var centre = CGPoint.zero
  private var lastTouch: Double?
  private var returnAt = 0.0
  private var tickNumber: Int?

  private let tick = UISelectionFeedbackGenerator()
  private let note = UINotificationFeedbackGenerator()

  init(autoPlay: Bool, reduced: Bool, code: (Int, Int, Int) = M.demoCode) {
    self.reduced = reduced
    self.code = code
    film = autoPlay && !reduced
    mode = film ? .auto : .manual
    // reduced motion rests on the film's result: the right numbers in, the door open
    dial = .held(reduced ? Double(code.2 == 0 ? 0 : code.2 - M.dialN) : 0)
    unlock = Run(from: 0, to: reduced ? M.unlock : 0, t0: 0, dur: 0, ease: Self.linear)
    wrong = Run(from: 0, to: 0, t0: 0, dur: 0, ease: Self.quadInOut)
    count = reduced ? 3 : 0
    entries = reduced ? [code.0, code.1, code.2] : [0, 0, 0]
    canLock = reduced
  }

  private func locked<T>(_ f: () -> T) -> T { lock.lock(); defer { lock.unlock() }; return f() }

  /// The model is drawn and its shaders are compiled: the film may start.
  func markReady() { locked { ready = true } }

  func frame(at now: Double) -> Frame { locked { frameLocked(now) } }

  // MARK: values at a time

  private func dialAt(_ now: Double) -> Double {
    switch dial {
    case .held(let v): return v
    case .spring(let s): return s.at(now)
    case .run(let r): return r.at(now)
    }
  }

  private func shakeAt(_ now: Double) -> Double {
    guard let s0 = shakeStart else { return 0 }
    let S = Self.shakeM, d = Self.shakeStep
    let steps: [(Double, Double, Double)] = [(0, S, d), (S, -S, 2 * d), (-S, 0.6 * S, 2 * d), (0.6 * S, -0.3 * S, 2 * d), (-0.3 * S, 0, d)]
    var t = now - s0
    for (a, b, dur) in steps {
      if t < dur { return a + (b - a) * Self.quadInOut(max(0, t) / dur) }
      t -= dur
    }
    shakeStart = nil
    return 0
  }

  // MARK: the frame

  private func frameLocked(_ now: Double) -> Frame {
    // a frame that arrives late is a stall: the film picks up where it was instead of skipping what it covered
    if let last = lastNow, now - last > Self.stall, mode == .auto, start != nil { start! += now - last - Self.frameTime }
    lastNow = max(lastNow ?? now, now)

    if mode == .auto {
      guard ready else {
        return Frame(pose: CombinationSafeDialPose(), t: 0, count: 0, entries: [code.0, code.1, code.2], wrong: 0, canLock: false, filming: true)
      }
      if start == nil { start = now }
      var el = now - start!
      if el >= M.loop {
        let n = floor(el / M.loop)
        start! += n * M.loop
        el -= n * M.loop
      }
      el = max(0, el)
      filmT = el
      let pose = CombinationSafeDialPose(dial: M.filmDial(el, code), unlock: M.filmUnlock(el), shake: 0)
      return Frame(pose: pose, t: el, count: M.filmEntered(el), entries: [code.0, code.1, code.2], wrong: 0, canLock: false, filming: true)
    }

    var d = dialAt(now)
    // a released dial has landed on its number
    if let target = landing, !dragging, abs(d - target) < Self.settled {
      landing = nil
      if turned {
        turned = false
        commit(M.dialNumber(target), now)
      }
    }
    if let c = clearAt, now >= c { clearEntries(now) }
    if mode == .home {
      if now - returnAt >= Self.returnTime {
        // the door is shut and the dial home: the film starts over
        dial = .held(0)
        start = now
        mode = .auto
        tickNumber = nil
        return frameLocked(now)
      }
    } else if lastTouch == nil || dragging || landing != nil {
      lastTouch = now
    } else if film, now - lastTouch! >= Self.idle {
      // 2 s after the last touch: the door shuts, the entries clear and the dial goes home by the short way
      mode = .home
      returnAt = now
      canLock = false
      clearEntries(now)
      let home = M.jsRound(d / Double(M.dialN)) * Double(M.dialN)
      dial = .run(Run(from: d, to: home, t0: now, dur: 0.9, ease: Self.easeInOut))
      let u = unlock.at(now)
      unlock = Run(from: u, to: 0, t0: now, dur: u > 0 ? Self.returnTime - 0.08 : 0, ease: Self.linear)
      d = dialAt(now)
    }
    // a tick as a finger's dial passes each number (the film stays silent)
    let n = M.dialNumber(d)
    if let prev = tickNumber, prev != n, mode == .manual, !reduced { DispatchQueue.main.async { [tick] in tick.selectionChanged() } }
    tickNumber = n

    let pose = CombinationSafeDialPose(dial: d, unlock: unlock.at(now), shake: shakeAt(now))
    return Frame(pose: pose, t: filmT, count: count, entries: entries, wrong: wrong.at(now), canLock: canLock, filming: false)
  }

  private func clearEntries(_ now: Double) {
    count = 0
    wrong = Run(from: wrong.at(now), to: 0, t0: now, dur: 0.18, ease: Self.quadInOut)
    clearAt = nil
  }

  /// A number is in: the third one decides.
  private func commit(_ n: Int, _ now: Double) {
    let i = count
    if i >= 3 || canLock || clearAt != nil { return }
    entries[i] = n
    count = i + 1
    if i < 2 { return }
    if entries[0] == code.0, entries[1] == code.1, n == code.2 {
      canLock = true
      unlock = reduced ? Run(from: M.unlock, to: M.unlock, t0: now, dur: 0, ease: Self.linear)
        : Run(from: unlock.at(now), to: M.unlock, t0: now, dur: M.unlock, ease: Self.linear)
      let done = onUnlock
      DispatchQueue.main.async { [note] in
        note.notificationOccurred(.success)
        done?()
      }
      return
    }
    DispatchQueue.main.async { [note] in note.notificationOccurred(.error) }
    wrong = Run(from: wrong.at(now), to: 1, t0: now, dur: 0.12, ease: Self.quadInOut)
    clearAt = now + Self.wrongHold
    if !reduced { shakeStart = now }
  }

  // MARK: touch

  /// A touch takes over from wherever the film (or the return) is.
  private func pause(_ now: Double) {
    let f = frameLocked(now)
    if mode == .auto {
      count = f.count
      entries = f.entries
      // the film's open door finishes the way it was going, and stays unlocked until the finger locks it
      let u = f.pose.unlock
      unlock = Run(from: u, to: u, t0: now, dur: 0, ease: Self.linear)
      if u > 0.01 {
        let opening = f.t < M.shut
        canLock = opening
        let to = opening ? M.unlock : 0
        unlock = Run(from: u, to: to, t0: now, dur: abs(to - u), ease: Self.linear)
        if !opening { count = 0 }
      } else if count >= 3 {
        count = 0
      }
      // a dial caught between numbers lands on the nearest one, without entering it
      let near = M.jsRound(f.pose.dial)
      dial = .spring(Spring(from: f.pose.dial, to: near, velocity: 0, at: now))
      landing = near
      turned = false
      start = nil
      tickNumber = M.dialNumber(f.pose.dial)
    } else if mode == .home {
      let d = dialAt(now)
      landing = M.jsRound(d)
      dial = .spring(Spring(from: d, to: M.jsRound(d), velocity: 0, at: now))
      turned = false
    }
    mode = .manual
    lastTouch = nil
  }

  /// The Lock button: the door shuts and the entries clear.
  func relock(now: Double) {
    locked {
      pause(now)
      canLock = false
      count = 0
      let u = unlock.at(now)
      unlock = Run(from: u, to: 0, t0: now, dur: reduced ? 0 : Self.relockTime, ease: Self.linear)
      lastTouch = nil
    }
  }

  /// Turn the dial: the finger's angle round the dial's centre, wherever on the stage it is.
  func dragBegan(at p: CGPoint, stage: CGSize, now: Double) {
    locked {
      pause(now)
      if canLock { return }
      dragging = true
      dial = .held(dialAt(now))
      landing = nil
      let open = M.doorAt(unlock.at(now))
      centre = M.project(M.V3(0, M.dialWorldY, M.dialFaceZ), open: open, width: Double(stage.width), height: Double(stage.height))
      lastAngle = atan2(Double(p.y - centre.y), Double(p.x - centre.x))
    }
  }

  func dragMoved(to p: CGPoint, now: Double) {
    locked {
      guard dragging else { return }
      let a = atan2(Double(p.y - centre.y), Double(p.x - centre.x))
      let d = M.angleDelta(lastAngle, a)
      lastAngle = a
      // on screen a positive angle is clockwise, and a clockwise dial counts down
      dial = .held(dialAt(now) - d * 180 / .pi / M.degPerNumber)
      if abs(d) > 0.001 { turned = true }
    }
  }

  func dragEnded(at p: CGPoint, velocity: CGSize, now: Double) {
    locked {
      defer { dragging = false }
      guard dragging else { return }
      let rx = Double(p.x - centre.x), ry = Double(p.y - centre.y)
      let r2 = max(400, rx * rx + ry * ry)
      let omega = (rx * Double(velocity.height) - ry * Double(velocity.width)) / r2
      let v = -omega * 180 / .pi / M.degPerNumber
      let d = dialAt(now)
      let target = M.jsRound(d + v * Self.flick)
      landing = target
      dial = reduced ? .held(target) : .spring(Spring(from: d, to: target, velocity: v, at: now))
    }
  }
}
