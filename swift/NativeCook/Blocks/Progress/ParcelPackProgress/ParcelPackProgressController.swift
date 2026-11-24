import Foundation
import QuartzCore

/// The block's one source of truth: the film's clock and what a finger set. `frame(at:)` is read by SceneKit's render
/// loop (the 3D stage) and by SwiftUI's TimelineView (the sheet and the touch dot), so the two never disagree and
/// nothing writes SwiftUI state per frame.
final class ParcelPackProgressController {
  typealias M = ParcelPackProgressMotion

  struct Frame {
    var t: Double
    var orbit: Double
    var filming: Bool
  }

  enum Curve {
    case inOutQuad, outQuad
    func at(_ x: Double) -> Double {
      switch self {
      case .inOutQuad: return x < 0.5 ? 2 * x * x : 1 - pow(-2 * x + 2, 2) / 2
      case .outQuad: return 1 - (1 - x) * (1 - x)
      }
    }
  }

  /// One stretch of a tap's run of the clock.
  private struct Run {
    var from: Double, to: Double, t0: Double, dur: Double, curve: Curve
    var end: Double { t0 + dur }
    func at(_ now: Double) -> Double {
      dur <= 0 ? to : from + (to - from) * curve.at(M.clamp01((now - t0) / dur))
    }
  }

  private static let idle = 2.0
  private static let stall = 0.1
  private static let frameTime = 1.0 / 60
  /// The swing back: stiffness 140, damping 17, mass 1.
  private static let springZ = 8.5
  private static let springW = (140.0 - 8.5 * 8.5).squareRoot()

  private let lock = NSLock()
  let film: Bool
  let reduced: Bool
  private var auto: Bool
  private var ready = false
  private var start: Double?
  private var lastNow: Double?
  private var held: Double
  private var runs: [Run] = []
  private var lastTouch = 0.0
  private var dragging = false
  private var dragFrom = 0.0
  private var orbitHeld: Double? = 0
  private var orbitX = 0.0, orbitV = 0.0, orbitT = 0.0

  init(autoPlay: Bool, reduced: Bool) {
    self.reduced = reduced
    film = autoPlay && !reduced
    auto = film
    held = reduced ? M.reducedT : 0
  }

  private func locked<T>(_ f: () -> T) -> T { lock.lock(); defer { lock.unlock() }; return f() }

  /// The model is drawn and its shaders are compiled: the film may start.
  func markReady() { locked { ready = true } }

  func frame(at now: Double) -> Frame { locked { frameLocked(now) } }

  private func orbitAt(_ now: Double) -> Double {
    if let orbitHeld { return orbitHeld }
    let s = max(0, now - orbitT)
    if s > 3 { return 0 }
    return exp(-Self.springZ * s) * (orbitX * cos(Self.springW * s) + (orbitV + Self.springZ * orbitX) / Self.springW * sin(Self.springW * s))
  }

  private func clockAt(_ now: Double) -> Double {
    for r in runs where now < r.end { return now < r.t0 ? r.from : r.at(now) }
    return runs.last?.to ?? held
  }

  private func frameLocked(_ now: Double) -> Frame {
    // a frame that arrives late is a stall: the film picks up where it was instead of skipping what it covered
    if let last = lastNow, now - last > Self.stall, auto, start != nil { start! += now - last - Self.frameTime }
    lastNow = max(lastNow ?? now, now)

    if !auto, film, !dragging, now - lastTouch >= Self.idle {
      // 2 s after the last touch the film carries on from the time on show
      let at = clockAt(now).truncatingRemainder(dividingBy: M.loop)
      held = at < 0 ? at + M.loop : at
      runs = []
      start = nil
      auto = true
    }
    if auto {
      guard ready else { return Frame(t: held, orbit: orbitAt(now), filming: true) }
      if start == nil { start = now - held }
      var el = now - start!
      if el >= M.loop {
        let n = floor(el / M.loop)
        start! += n * M.loop
        el -= n * M.loop
      }
      return Frame(t: max(0, el), orbit: orbitAt(now), filming: true)
    }
    return Frame(t: clockAt(now), orbit: orbitAt(now), filming: false)
  }

  // MARK: touch

  /// A touch stops the film where it is.
  private func pause(_ now: Double) {
    let f = frameLocked(now)
    if auto {
      held = f.t
      runs = []
      auto = false
      start = nil
    }
    lastTouch = now
  }

  private static func jumpTime(_ span: Double) -> Double { min(1.4, max(0.3, abs(span) * 0.28)) }

  /// A tap on step i: the clock runs (forward or back) to the moment that step is done.
  func jump(to i: Int, now: Double) {
    locked {
      pause(now)
      let to = M.jump[i]
      if reduced {
        held = to
        runs = []
        return
      }
      var from = clockAt(now).truncatingRemainder(dividingBy: M.loop)
      if from < 0 { from += M.loop }
      if from >= M.rest { from = 0 } // the rest at the loop's end is the same frame as t = 0
      if from >= M.swap {
        // a new carton is on its way in: let it arrive, then run forward from the start
        let first = Self.jumpTime(M.rest - from)
        runs = [
          Run(from: from, to: M.rest, t0: now, dur: first, curve: .outQuad),
          Run(from: 0, to: to, t0: now + first, dur: Self.jumpTime(to), curve: .inOutQuad),
        ]
      } else {
        runs = [Run(from: from, to: to, t0: now, dur: Self.jumpTime(to - from), curve: .inOutQuad)]
      }
    }
  }

  func dragBegan(now: Double, width: Double) {
    locked {
      pause(now)
      dragging = true
      let cur = orbitAt(now)
      orbitHeld = cur
      // pick the carton up from wherever its swing back had got to
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
      orbitX = orbitAt(now)
      orbitV = (-vx / width) * M.orbitSpan
      orbitT = now
      orbitHeld = nil
      dragging = false
      lastTouch = now
    }
  }
}
