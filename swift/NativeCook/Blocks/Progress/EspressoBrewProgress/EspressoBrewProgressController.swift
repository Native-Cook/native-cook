import Foundation
import QuartzCore

/// The block's one source of truth: the film's clock and what a finger set. `frame(at:)` is read by SceneKit's render
/// loop (the 3D stage) and by SwiftUI's TimelineView (the sheet and the film's finger), so the two never disagree and
/// nothing writes SwiftUI state per frame.
///
/// React Native's modes: the film (auto), a finger's shot or settings (manual), and the way home 2 s after the last
/// touch (return: a full cup is changed for an empty one, the orbit springs to 0, and 0.9 s later the film restarts).
final class EspressoBrewProgressController {
  typealias M = EspressoBrewProgressMotion
  typealias P = EspressoBrewProgressMotion.Phase

  struct Frame {
    var pose: EspressoBrewProgressPose
    /// The selected dose on the segmented control (0 Single, 1 Double).
    var doseSel: Int
    var filming: Bool
  }

  /// A damped spring towards `target`, from the offset `x0` with velocity `v0` at `t0`.
  private struct Spring {
    var target = 0.0
    var x0 = 0.0, v0 = 0.0, t0 = 0.0
    let z: Double, w: Double

    init(stiffness: Double, damping: Double) {
      z = damping / 2
      w = (stiffness - z * z).squareRoot()
    }

    func at(_ now: Double) -> Double {
      let s = max(0, now - t0)
      if s > 4 { return target }
      return target + exp(-z * s) * (x0 * cos(w * s) + (v0 + z * x0) / w * sin(w * s))
    }
  }

  /// A timed ease (Reanimated's default withTiming: in-out quad).
  private struct Ease {
    var from: Double, to: Double, t0: Double, dur: Double
    func at(_ now: Double) -> Double {
      if dur <= 0 || now >= t0 + dur { return to }
      let x = M.clamp01((now - t0) / dur)
      let k = x < 0.5 ? 2 * x * x : 1 - pow(-2 * x + 2, 2) / 2
      return from + (to - from) * k
    }
  }

  private enum Mode { case auto, manual, home }

  private static let idle = 2.0
  private static let returnTime = 0.9
  private static let stall = 0.1
  private static let frameTime = 1.0 / 60
  private static let doseTime = 0.42

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

  /// A finger's place on the film's timeline: held, or running in real time from `runFrom` (at `runT0`) to `runTo`;
  /// `chain` starts a shot when a cup change gets there.
  private var held: Double
  private var runFrom = 0.0, runT0 = 0.0
  private var runTo: Double?
  private var chain = false

  private var doseSel: Int
  private var dose: Ease
  private var orbitHeld: Double? = 0
  private var orbitSpring = Spring(stiffness: 140, damping: 18)
  private var dragFrom = 0.0

  init(autoPlay: Bool, reduced: Bool) {
    self.reduced = reduced
    film = autoPlay && !reduced
    mode = film ? .auto : .manual
    // reduced motion rests on the film's result: a double, poured, the cup full
    held = reduced ? P.full : 0
    doseSel = reduced ? 1 : 0
    dose = Ease(from: 0, to: Double(doseSel), t0: 0, dur: 0)
  }

  private func locked<T>(_ f: () -> T) -> T { lock.lock(); defer { lock.unlock() }; return f() }

  /// The model is drawn and its shaders are compiled: the film may start.
  func markReady() { locked { ready = true } }

  func frame(at now: Double) -> Frame { locked { frameLocked(now) } }

  private func orbitAt(_ now: Double) -> Double { orbitHeld ?? orbitSpring.at(now) }

  /// Plays a run forward to `now`: a finished run is held (and a chained shot starts from Brew where it ended).
  private func settle(_ now: Double) {
    while let to = runTo {
      let end = runT0 + (to - runFrom)
      if now < end { return }
      if chain {
        chain = false
        runFrom = P.brew
        runT0 = end
        runTo = P.full
      } else {
        held = to
        runTo = nil
        // the idle time counts from the moment the pour (or the cup change) is done
        lastTouch = max(lastTouch, end)
      }
    }
  }

  private func clockAt(_ now: Double) -> Double {
    guard let to = runTo else { return held }
    return min(to, runFrom + max(0, now - runT0))
  }

  private func run(from: Double, to: Double, now: Double) {
    runFrom = from
    runT0 = now
    runTo = to
  }

  private func frameLocked(_ now: Double) -> Frame {
    // a frame that arrives late is a stall: the film picks up where it was instead of skipping what it covered
    if let last = lastNow, now - last > Self.stall, mode == .auto, start != nil { start! += now - last - Self.frameTime }
    lastNow = max(lastNow ?? now, now)

    if mode == .auto {
      guard ready else {
        return Frame(pose: EspressoBrewProgressPose(t: 0, dose: 0, orbit: 0), doseSel: 0, filming: true)
      }
      if start == nil { start = now }
      var el = now - start!
      if el >= M.loop {
        let n = floor(el / M.loop)
        start! += n * M.loop
        el -= n * M.loop
      }
      el = max(0, el)
      let d = M.filmDose(el)
      return Frame(pose: EspressoBrewProgressPose(t: el, dose: Double(d), orbit: 0), doseSel: d, filming: true)
    }

    settle(now)
    if mode == .home, runTo == nil, now - returnAt >= Self.returnTime {
      // the cup is empty and the camera home: the film starts over (which puts Single back)
      mode = .auto
      start = now
      held = 0
      orbitHeld = 0
      doseSel = 0
      dose = Ease(from: 0, to: 0, t0: now, dur: 0)
      return frameLocked(now)
    }
    if mode == .manual, film, !dragging, runTo == nil, now - lastTouch >= Self.idle {
      // 2 s after the last touch: a full cup is changed for an empty one and the camera comes home
      mode = .home
      returnAt = now
      let cur = held
      if cur >= P.brew, cur < P.cupInEnd { run(from: cur, to: P.cupInEnd, now: now) }
      springOrbit(to: 0, velocity: 0, now: now)
    }
    let pose = EspressoBrewProgressPose(t: clockAt(now), dose: dose.at(now), orbit: orbitAt(now))
    return Frame(pose: pose, doseSel: doseSel, filming: false)
  }

  private func springOrbit(to: Double, velocity: Double, now: Double) {
    let cur = orbitAt(now)
    orbitSpring.target = to
    orbitSpring.x0 = cur - to
    orbitSpring.v0 = velocity
    orbitSpring.t0 = now
    orbitHeld = nil
  }

  // MARK: touch

  /// A touch takes over from wherever the film (or the return) is; a shot in progress keeps pouring.
  private func pause(_ now: Double) {
    if mode == .auto {
      let f = frameLocked(now)
      let cur = f.pose.t
      chain = false
      held = cur
      runTo = nil
      doseSel = f.doseSel
      dose = Ease(from: f.pose.dose, to: f.pose.dose, t0: now, dur: 0)
      if cur >= P.cupInEnd {
        held = 0
      } else if cur >= P.brew, cur < P.full {
        run(from: cur, to: P.full, now: now)
      } else if cur >= P.cupOut {
        run(from: cur, to: P.cupInEnd, now: now)
      }
      start = nil
    } else {
      settle(now)
      if mode == .home {
        orbitHeld = orbitAt(now)
        let d = dose.at(now)
        dose = Ease(from: d, to: d, t0: now, dur: 0)
        chain = false
      }
    }
    mode = .manual
    lastTouch = now
  }

  /// Brew: the film's shot from 1.35 s to 7.75 s in real time; a full cup is changed first.
  func brew(now: Double) {
    locked {
      pause(now)
      if reduced {
        // no pour: the shot is simply there
        runTo = nil
        held = P.full
        return
      }
      if let to = runTo {
        // already pouring, or changing the cup: brew when the fresh cup is in
        if to == P.cupInEnd { chain = true }
        return
      }
      let cur = held
      if cur >= P.brew, cur < P.cupInEnd {
        run(from: cur, to: P.cupInEnd, now: now)
        chain = true
        return
      }
      run(from: P.brew, to: P.full, now: now)
    }
  }

  /// Single / Double: the liquid eases to the new dose over 0.42 s.
  func choose(dose v: Int, now: Double) {
    locked {
      pause(now)
      doseSel = v
      let cur = dose.at(now)
      dose = Ease(from: cur, to: Double(v), t0: now, dur: reduced ? 0 : Self.doseTime)
    }
  }

  func dragBegan(now: Double) {
    locked {
      pause(now)
      dragging = true
      let cur = orbitAt(now)
      orbitHeld = cur
      dragFrom = cur
    }
  }

  func dragMoved(dx: Double, width: Double, now: Double) {
    locked {
      lastTouch = now
      orbitHeld = M.orbitFromDrag(dragFrom, dx: dx, width: width)
    }
  }

  func dragEnded(velocity vx: Double, width: Double, now: Double) {
    locked {
      let cur = orbitAt(now)
      let v = vx / width * M.orbitSpan
      let to = max(-M.orbitMax, min(M.orbitMax, cur + v * 0.1))
      if reduced {
        orbitHeld = to
      } else {
        springOrbit(to: to, velocity: v, now: now)
      }
      dragging = false
      lastTouch = now
    }
  }
}
