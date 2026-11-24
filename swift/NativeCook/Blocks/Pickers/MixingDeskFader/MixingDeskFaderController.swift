import Foundation
import QuartzCore
import simd

/// The block's one source of truth: the film's clock and what a finger set. `frame(at:)` is read by SceneKit's render
/// loop (the desk) and by SwiftUI's TimelineView (the sheet and the film's finger), so the two never disagree and
/// nothing writes SwiftUI state per frame.
///
/// React Native's modes: the film (auto), a finger's mix (manual), and the way home 2 s after the last touch (return:
/// Intro is recalled, the camera's lean eases to 0, and 1.5 s later the film restarts at t = 0).
final class MixingDeskFaderController {
  typealias M = MixingDeskFaderMotion

  struct Frame {
    var pose: MixingDeskFaderPose
    /// The film's (or the sheet's own) time, for the REC light and the film's fingers.
    var t: Double
    var preset: Int
    var filming: Bool
  }

  /// One value's motion: held, a timed ease (with a delay), or a spring with a starting velocity.
  private enum Track {
    case held(Double)
    case ease(from: Double, to: Double, t0: Double, dur: Double, bezier: Bool)
    case spring(to: Double, x0: Double, v0: Double, t0: Double)

    static let z = 13.0 // stiffness 240, damping 26, mass 1
    static let w = (240.0 - 13.0 * 13.0).squareRoot()

    func at(_ now: Double) -> Double {
      switch self {
      case .held(let v): return v
      case let .ease(from, to, t0, dur, bezier):
        if now <= t0 { return from }
        if now >= t0 + dur { return to }
        let x = (now - t0) / dur
        let k = bezier ? MixingDeskFaderController.easeInOut(x) : (x < 0.5 ? 2 * x * x : 1 - pow(-2 * x + 2, 2) / 2)
        return from + (to - from) * k
      case let .spring(to, x0, v0, t0):
        let s = max(0, now - t0)
        if s > 3 { return to }
        let z = Track.z, w = Track.w
        return to + exp(-z * s) * (x0 * cos(w * s) + (v0 + z * x0) / w * sin(w * s))
      }
    }
  }

  /// cubic-bezier(0.65, 0, 0.35, 1).
  static func easeInOut(_ x: Double) -> Double {
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

  private enum Mode { case auto, manual, home }

  private static let idle = 2.0
  private static let returnTime = 1.5
  private static let stall = 0.1
  private static let frameTime = 1.0 / 60
  /// A finger finds a fader within this distance (pt) of its track, and a mute button within muteReach.
  static let faderReach = 34.0
  static let muteReach = 30.0
  private static let flick = 0.08
  private static let snapCatch = M.snapZone * 1.3

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

  private var tracks: [Track]
  private var lean: Track = .held(0)
  private var preset: Int
  /// The sheet's own time once a finger has the desk (the meters and the REC light keep going).
  private var sigBase = 0.0, sigT0 = 0.0

  /// The fader a finger holds, its value at the touch, its track on screen and whether it sits in the 0 dB detent.
  private var grab = -1
  private var grabFrom = 0.0
  private var axis = SIMD2<Double>(0, 1)
  private var inDetent = false

  init(autoPlay: Bool, reduced: Bool) {
    self.reduced = reduced
    film = autoPlay && !reduced
    mode = film ? .auto : .manual
    let p = M.presets[reduced ? M.reducedPreset : 0]
    tracks = p.map { .held($0) }
    preset = reduced ? M.reducedPreset : 0
  }

  private func locked<T>(_ f: () -> T) -> T { lock.lock(); defer { lock.unlock() }; return f() }

  /// The model is drawn and its shaders are compiled: the film may start.
  func markReady() { locked { ready = true } }

  func frame(at now: Double) -> Frame { locked { frameLocked(now) } }

  private func sheetTime(_ now: Double) -> Double {
    if reduced { return 0 }
    let s = (sigBase + (now - sigT0)).truncatingRemainder(dividingBy: M.loop)
    return s < 0 ? s + M.loop : s
  }

  private func frameLocked(_ now: Double) -> Frame {
    // a frame that arrives late is a stall: the film picks up where it was instead of skipping what it covered
    if let last = lastNow, now - last > Self.stall, mode == .auto, start != nil { start! += now - last - Self.frameTime }
    lastNow = max(lastNow ?? now, now)

    if mode == .auto {
      let el: Double
      if !ready {
        el = 0
      } else {
        if start == nil { start = now }
        var e = now - start!
        if e >= M.loop {
          let n = floor(e / M.loop)
          start! += n * M.loop
          e -= n * M.loop
        }
        el = max(0, e)
      }
      let pose = MixingDeskFaderPose(p: M.filmPose(el), lean: M.leanAt(el), signal: el)
      return Frame(pose: pose, t: el, preset: M.filmPreset(el), filming: true)
    }
    if mode == .home, now - returnAt >= Self.returnTime {
      // Intro is back and the camera home: the film starts over from t = 0
      mode = .auto
      start = now
      return frameLocked(now)
    }
    if mode == .manual, film, !dragging, now - lastTouch >= Self.idle {
      // 2 s after the last touch: Intro is recalled and the camera's lean eases home
      mode = .home
      returnAt = now
      preset = 0
      recall(0, now: now)
      lean = .ease(from: lean.at(now), to: 0, t0: now, dur: Self.returnTime - 0.1, bezier: true)
    }
    let s = sheetTime(now)
    let pose = MixingDeskFaderPose(p: tracks.map { $0.at(now) }, lean: lean.at(now), signal: reduced ? M.stillT : s)
    return Frame(pose: pose, t: s, preset: preset, filming: false)
  }

  /// Glide every fader, knob and mute to preset p, as a recall does.
  private func recall(_ p: Int, now: Double) {
    let target = M.presets[p]
    for c in 0..<M.channels {
      for i in [c, 4 + c, 8 + c] {
        let cur = tracks[i].at(now)
        if reduced {
          tracks[i] = .held(target[i])
        } else if i >= 8 {
          tracks[i] = .ease(from: cur, to: target[i], t0: now + 0.1, dur: M.muteMove, bezier: false)
        } else {
          tracks[i] = .ease(from: cur, to: target[i], t0: now + Double(c) * M.stagger, dur: M.glide, bezier: true)
        }
      }
    }
  }

  // MARK: touch

  /// A touch takes over from wherever the film (or the return) is: the desk keeps the pose it had.
  private func pause(_ now: Double) {
    if mode == .auto {
      let f = frameLocked(now)
      for i in 0..<M.poseN {
        tracks[i] = .held(i >= 8 ? f.pose.p[i].rounded() : f.pose.p[i])
      }
      lean = .held(f.pose.lean)
      preset = f.preset
      sigBase = f.t
      sigT0 = now
      start = nil
    } else if mode == .home {
      lean = .held(lean.at(now))
    }
    mode = .manual
    lastTouch = now
  }

  func choose(preset p: Int, now: Double) {
    locked {
      pause(now)
      preset = p
      recall(p, now: now)
    }
  }

  /// A finger came down on the stage: the fader whose track is nearest, within reach (its index, or -1).
  func pick(at pt: SIMD2<Double>, width: Double, height: Double, now: Double) -> Int {
    locked {
      let f = frameLocked(now)
      let k = f.pose.lean
      var best = -1
      var bestD = Self.faderReach
      for c in 0..<M.channels {
        let a = M.capOnScreen(c, 0, k: k, width: width, height: height)
        let b = M.capOnScreen(c, 1, k: k, width: width, height: height)
        let d = b - a
        let l2 = simd_dot(d, d)
        let u = max(-0.1, min(1.1, simd_dot(pt - a, d) / l2))
        let dist = simd_length(a + d * u - pt)
        if dist < bestD {
          bestD = dist
          best = c
          axis = d
        }
      }
      grab = best
      return best
    }
  }

  /// The drag became a fader's: it takes over at the value the cap shows. Returns whether a fader is held.
  func dragBegan(now: Double) -> Bool {
    locked {
      guard grab >= 0 else { return false }
      pause(now)
      dragging = true
      let v = tracks[grab].at(now)
      tracks[grab] = .held(v)
      grabFrom = v
      inDetent = abs(v - M.snapV) < M.snapZone * 0.5
      return true
    }
  }

  /// The finger moved (pt from where it came down): returns true when the cap has just caught the 0 dB detent.
  func dragMoved(translation tr: SIMD2<Double>, now: Double) -> Bool {
    locked {
      guard grab >= 0, dragging else { return false }
      lastTouch = now
      let raw = grabFrom + simd_dot(tr, axis) / simd_dot(axis, axis)
      let v = M.faderFromDrag(raw)
      tracks[grab] = .held(v)
      let caught = abs(v - M.snapV) < M.snapZone * 0.5
      defer { inDetent = caught }
      return caught && !inDetent
    }
  }

  /// The finger let go with this velocity (pt/s): the cap is carried a little and lands on a spring. Returns the
  /// channel and where it lands.
  func dragEnded(velocity vel: SIMD2<Double>, now: Double) -> (Int, Double)? {
    locked {
      defer {
        dragging = false
        grab = -1
        lastTouch = now
      }
      guard grab >= 0, dragging else { return nil }
      let v = simd_dot(vel, axis) / simd_dot(axis, axis)
      let cur = tracks[grab].at(now)
      var to = max(0, min(1, cur + v * Self.flick))
      if abs(to - M.snapV) < Self.snapCatch { to = M.snapV }
      tracks[grab] = reduced ? .held(to) : .spring(to: to, x0: cur - to, v0: v, t0: now)
      return (grab, to)
    }
  }

  func dragCancelled(now: Double) {
    locked {
      dragging = false
      grab = -1
      lastTouch = now
    }
  }

  /// A tap on the stage: the mute button within reach toggles (its channel and new state), or nothing.
  func tap(at pt: SIMD2<Double>, width: Double, height: Double, now: Double) -> (Int, Bool)? {
    locked {
      let k = frameLocked(now).pose.lean
      var best = -1
      var bestD = Self.muteReach
      for c in 0..<M.channels {
        let d = simd_length(M.muteOnScreen(c, k: k, width: width, height: height) - pt)
        if d < bestD {
          bestD = d
          best = c
        }
      }
      guard best >= 0 else { return nil }
      pause(now)
      let cur = tracks[8 + best].at(now)
      let to: Double = cur > 0.5 ? 0 : 1
      tracks[8 + best] = reduced ? .held(to) : .ease(from: cur, to: to, t0: now, dur: M.muteMove, bezier: false)
      return (best, to == 1)
    }
  }
}
