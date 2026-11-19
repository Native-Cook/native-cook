import Foundation
import QuartzCore

/// The block's one source of truth: the film's clock and whatever a finger set. `state(at:)` is a pure function of the
/// stored fields and a media time (CACurrentMediaTime), read by SceneKit's render loop (the 3D stage) and by
/// SwiftUI's TimelineView (backdrop, HUD, copy, pager), so the layers never disagree and nothing writes SwiftUI state
/// per frame.
///
/// React Native's three modes: the film (auto), the pages under a finger (manual), and the ease back to the poster
/// page 2 s after the last touch (return), after which the film starts again from 0.
final class VisorBootOnboardingController {
  typealias M = VisorBootOnboardingMotion

  /// A value animated from media time t0: held, eased, a spring (Reanimated's duration-based spring) or a decay.
  enum Anim {
    case still(Double)
    case timing(from: Double, to: Double, t0: Double, dur: Double, curve: (Double) -> Double)
    case spring(from: Double, to: Double, v0: Double, t0: Double, omega: Double, zeta: Double)
    case decay(x0: Double, v0: Double, t0: Double)

    static let decayK = -log(0.995) * 1000

    func at(_ now: Double) -> Double {
      switch self {
      case .still(let v): return v
      case let .timing(from, to, t0, dur, curve):
        return dur <= 0 ? to : from + (to - from) * curve(M.clamp01((now - t0) / dur))
      case let .spring(from, to, v0, t0, w, z):
        let t = max(0, now - t0)
        let x0 = from - to
        if z < 1 {
          let wd = w * sqrt(1 - z * z)
          return to + exp(-z * w * t) * (x0 * cos(wd * t) + (v0 + z * w * x0) / wd * sin(wd * t))
        }
        return to + exp(-w * t) * (x0 + (v0 + w * x0) * t)
      case let .decay(x0, v0, t0):
        let t = max(0, now - t0)
        return x0 + v0 / Self.decayK * (1 - exp(-Self.decayK * t))
      }
    }

    /// Media time at which it has (visibly) come to rest.
    var end: Double {
      switch self {
      case .still: return 0
      case let .timing(_, _, t0, dur, _): return t0 + dur
      case let .spring(_, _, _, t0, w, z): return t0 + 5.5 / (max(z, 0.3) * w)
      case let .decay(_, v0, t0): return t0 + max(0, log(max(abs(v0), 1e-6) / 2) / Self.decayK)
      }
    }

    var target: Double {
      switch self {
      case .still(let v): return v
      case let .timing(_, to, _, _, _): return to
      case let .spring(_, to, _, _, _, _): return to
      case let .decay(x0, v0, _): return x0 + v0 / Self.decayK
      }
    }
  }

  enum Mode { case auto, manual, back }

  /// Page spring: Reanimated { duration: 700, dampingRatio: 0.86 }.
  static func pageSpring(from: Double, to: Double, v0: Double, now: Double) -> Anim {
    .spring(from: from, to: to, v0: v0, t0: now, omega: 2 * Double.pi / 0.7 * 0.92, zeta: 0.86)
  }

  private let lock = NSLock()
  let film: Bool
  let reduced: Bool

  private(set) var mode: Mode
  private var start: Double?
  private var page: Anim = .still(0)
  private var m: Anim
  private var yaw: Anim = .still(0)
  private var pitch: Anim = .still(0)
  private var runBase = 0.0
  private var runFrom = 0.0
  private var lastActivity = 0.0
  private(set) var dragging = false

  init(autoPlay: Bool, reduced: Bool) {
    self.reduced = reduced
    film = autoPlay && !reduced
    mode = film ? .auto : .manual
    m = .still(film ? 0 : 1)
  }

  private func locked<T>(_ f: () -> T) -> T { lock.lock(); defer { lock.unlock() }; return f() }

  // MARK: reading

  func state(at now: Double) -> VisorBootOnboardingState { locked { stateLocked(now) } }

  /// Whether the finger is turning the helmet (for the 45 deg detent haptic).
  var isOrbiting: Bool {
    locked {
      if dragging { return true }
      if case .decay = yaw { return CACurrentMediaTime() < yaw.end }
      return false
    }
  }

  var isManual: Bool { locked { mode == .manual } }

  private func stateLocked(_ now: Double) -> VisorBootOnboardingState {
    if mode == .auto {
      if start == nil { start = now }
      let t = max(0, (now - start!).truncatingRemainder(dividingBy: M.loop))
      return VisorBootOnboardingState(t: t, page: 0, m: 0, yaw: 0, pitch: 0, run: t)
    }
    if mode == .manual && film && !dragging {
      var idleFrom = max(lastActivity, page.end)
      if case .decay = yaw { idleFrom = max(idleFrom, yaw.end) }
      if now >= idleFrom + M.idle {
        // back to the poster page, then the film starts again from 0
        let r0 = idleFrom + M.idle
        let y = yaw.at(r0), p = pitch.at(r0), pg = page.at(r0)
        yaw = .timing(from: y, to: (y / 360).rounded() * 360, t0: r0, dur: M.returnDuration, curve: M.easeInOutTouch)
        pitch = .timing(from: p, to: 0, t0: r0, dur: M.returnDuration, curve: M.easeInOutTouch)
        page = .timing(from: pg, to: 0, t0: r0, dur: M.returnDuration, curve: M.easeInOutTouch)
        mode = .back
      }
    }
    if mode == .back && now >= page.end {
      mode = .auto
      start = page.end
      yaw = .still(0)
      pitch = .still(0)
      page = .still(0)
      m = .still(0)
      return stateLocked(now)
    }
    return VisorBootOnboardingState(t: frozenT, page: page.at(now), m: m.at(now), yaw: yaw.at(now), pitch: pitch.at(now),
                                    run: runBase + (now - runFrom))
  }

  // MARK: touch

  /// Any touch hands the film over to the pages, at the page the film was on.
  private func pause(_ now: Double) {
    let s = stateLocked(now)
    if mode == .auto {
      page = .still(M.pageOfFilm(s.t))
      runBase = s.run
      runFrom = now
      m = .timing(from: 0, to: 1, t0: now, dur: M.takeover, curve: M.easeInOutTouch)
      // the film's own time keeps feeding the blend while m rises: freeze it where it was
      frozenT = s.t
    } else {
      page = .still(s.page)
      yaw = .still(s.yaw)
      pitch = .still(s.pitch)
    }
    mode = .manual
    lastActivity = now
  }

  /// The film time the blend fades out of after a touch (React Native's t stops when the film stops).
  private(set) var frozenT = 0.0

  func goPage(_ i: Int, now: Double) {
    locked {
      pause(now)
      let target = Double(max(0, min(M.pages - 1, i)))
      let cur = page.at(now)
      page = reduced ? .timing(from: cur, to: target, t0: now, dur: 0.22, curve: { $0 })
        : Self.pageSpring(from: cur, to: target, v0: 0, now: now)
    }
  }

  func next(now: Double) { goPage(Int(state(at: now).page.rounded()) + 1, now: now) }
  func skip(now: Double) { goPage(M.pages - 1, now: now) }
  func start(now: Double) { locked { pause(now) } }

  // orbit
  static let yawPerPt = 0.36, pitchPerPt = 0.16, pitchMin = -10.0, pitchMax = 24.0
  private var yawFrom = 0.0, pitchFrom = 0.0, pageFrom = 0.0

  static func rubber(_ x: Double, _ lo: Double, _ hi: Double, _ k: Double) -> Double {
    if x < lo { let o = lo - x; return lo - o / (1 + o / k) }
    if x > hi { let o = x - hi; return hi + o / (1 + o / k) }
    return x
  }

  func orbitBegan(_ now: Double) {
    locked {
      pause(now)
      dragging = true
      yawFrom = yaw.at(now)
      pitchFrom = pitch.at(now)
    }
  }

  func orbitChanged(_ now: Double, dx: Double, dy: Double) {
    locked {
      lastActivity = now
      yaw = .still(yawFrom - dx * Self.yawPerPt)
      pitch = .still(Self.rubber(pitchFrom + dy * Self.pitchPerPt, Self.pitchMin, Self.pitchMax, 10))
    }
  }

  func orbitEnded(_ now: Double, vx: Double, vy: Double) {
    locked {
      dragging = false
      lastActivity = now
      let p = pitch.at(now)
      let back = max(Self.pitchMin, min(Self.pitchMax, p))
      pitch = .spring(from: p, to: back, v0: vy * Self.pitchPerPt, t0: now, omega: 2 * Double.pi / 0.4 * 0.95, zeta: 0.8)
      if reduced { return }
      yaw = .decay(x0: yaw.at(now), v0: -vx * Self.yawPerPt, t0: now)
    }
  }

  // pager
  static let pageSpan = 0.8

  func pagerBegan(_ now: Double) {
    locked {
      pause(now)
      dragging = true
      pageFrom = page.at(now)
    }
  }

  func pagerChanged(_ now: Double, dx: Double, width: Double) {
    locked {
      lastActivity = now
      page = .still(Self.rubber(pageFrom - dx / (width * Self.pageSpan), 0, Double(M.pages - 1), 0.35))
    }
  }

  func pagerEnded(_ now: Double, dx: Double, vx: Double, width: Double) {
    locked {
      dragging = false
      lastActivity = now
      let base = pageFrom.rounded()
      var target = base
      if dx < -50 || vx < -500 { target = base + 1 } else if dx > 50 || vx > 500 { target = base - 1 }
      target = max(0, min(Double(M.pages - 1), target))
      let cur = page.at(now)
      let v = -vx / (width * Self.pageSpan)
      page = reduced ? .timing(from: cur, to: target, t0: now, dur: 0.22, curve: { $0 })
        : Self.pageSpring(from: cur, to: target, v0: v, now: now)
    }
  }
}
