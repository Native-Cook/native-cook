import Foundation
import QuartzCore

/// The block's one source of truth: the film's clock and whatever a finger set. `frame(at:)` is a pure function of
/// the stored fields and a media time (CACurrentMediaTime), read by SceneKit's render loop (the 3D stage) and by
/// SwiftUI's TimelineView (shadows, bars, times, the native controls), so they never disagree and nothing writes
/// SwiftUI state per frame.
///
/// React Native's modes: the film (auto), the listener's record (manual: the motor turns it at the speed the last
/// play or pause set, the song plays with it), and the return 2 s after the last touch (the record stops and the scene
/// eases back into the film: from the middle to the close in 1.4 s, nearer the sleeve along the film's own slide).
final class BoomboxNowPlayingController {
  typealias M = BoomboxNowPlayingMotion

  /// A value easing from `from` to `to` over `dur` seconds from media time `t0`.
  struct Ease {
    var from: Double, to: Double, t0: Double, dur: Double
    var curve: (Double) -> Double = { $0 }
    func at(_ now: Double) -> Double {
      if dur <= 0 || now >= t0 + dur { return to }
      if now <= t0 { return from }
      return from + (to - from) * curve((now - t0) / dur)
    }
    var end: Double { t0 + dur }
    static func still(_ v: Double) -> Ease { Ease(from: v, to: v, t0: 0, dur: 0) }
    /// The integral of the value over [a, b] (a >= t0 is not required).
    func integral(_ a: Double, _ b: Double) -> Double {
      if b <= a { return 0 }
      var sum = 0.0
      // before the ease: `from`; after it: `to`; inside it: Simpson's rule
      let i0 = max(a, min(b, t0)), i1 = min(b, max(a, end))
      sum += from * max(0, min(b, t0) - a)
      sum += to * max(0, b - max(a, end))
      if i1 > i0 {
        let n = 24
        let h = (i1 - i0) / Double(n)
        var s = at(i0) + at(i1)
        for k in 1..<n { s += at(i0 + Double(k) * h) * (k % 2 == 1 ? 4 : 2) }
        sum += s * h / 3
      }
      return sum
    }
  }

  enum Mode { case auto, manual, back }

  struct Frame {
    var stage: Double
    var ret: Double
    var platter: Double
    var song: Double
    var level: Double
    var volume: Double
    var playing: Bool
    var auto: Bool
  }

  private let lock = NSLock()
  let film: Bool
  let reduced: Bool

  private var mode: Mode
  /// Media time of the film's t = 0 (nil until the stage has drawn the record).
  private var start: Double?
  private var resumeAt = 0.0
  private var platterBase = 0.0
  // the listener's record
  private var stageE: Ease
  private var retE = Ease.still(0)
  private var speedE = Ease.still(0)
  private var volumeE = Ease.still(M.volume0)
  /// The record's angle and the song at media time `anchorT`; the motor adds the integral of the speed since then.
  private var platter0: Double
  private var song0: Double
  private var anchorT = 0.0
  /// An eased song move (seek, the return's run back to 0:00): it wins until it ends, then the motor carries on.
  private var songE: Ease?
  private var playing = false
  private var lastActivity = -1.0
  private var backDone: (Double) -> Void = { _ in }
  private var backEnd = 0.0
  var dragging = false
  var scrubbing = false
  private var scrubEndedAt = -1.0

  init(autoPlay: Bool, reduced: Bool) {
    self.reduced = reduced
    film = autoPlay && !reduced
    mode = film ? .auto : .manual
    let s0 = film ? 0 : M.stillT
    stageE = .still(s0)
    platter0 = M.platterAt(s0)
    song0 = film ? 0 : M.songAt(M.stillT)
  }

  private func locked<T>(_ f: () -> T) -> T { lock.lock(); defer { lock.unlock() }; return f() }

  /// The film may start once the stage has drawn the record (its textures are up), as in React Native.
  func markReady(_ now: Double) { locked { if mode == .auto && start == nil { start = now - resumeAt; resumeAt = 0 } } }

  func frame(at now: Double) -> Frame { locked { frameLocked(now) } }

  // MARK: reading

  private func filmFrame(_ t: Double) -> Frame {
    Frame(stage: t, ret: 0, platter: platterBase + M.platterAt(t), song: M.songAt(t), level: M.speedAt(t),
          volume: M.volumeAt(t), playing: M.playingAt(t), auto: true)
  }

  private func platterAt(_ now: Double) -> Double {
    dragging ? platter0 : platter0 + M.omega * speedE.integral(anchorT, now)
  }

  private func songAt(_ now: Double) -> Double {
    if let e = songE {
      if now < e.end { return e.at(now) }
      if dragging || scrubbing { return e.to }
      return min(M.trackSeconds, e.to + speedE.integral(max(anchorT, e.end), now))
    }
    if dragging || scrubbing { return song0 }
    return min(M.trackSeconds, song0 + speedE.integral(anchorT, now))
  }

  private func manualFrame(_ now: Double) -> Frame {
    Frame(stage: stageE.at(now), ret: retE.at(now), platter: platterAt(now), song: songAt(now),
          level: speedE.at(now), volume: volumeE.at(now), playing: playing, auto: false)
  }

  private func frameLocked(_ now: Double) -> Frame {
    switch mode {
    case .auto:
      guard let start else { return filmFrame(resumeAt) }
      var t = (now - start).truncatingRemainder(dividingBy: M.loop)
      if t < 0 { t += M.loop }
      return filmFrame(t)
    case .back:
      if now >= backEnd {
        backDone(backEnd)
        return frameLocked(now)
      }
      return manualFrame(now)
    case .manual:
      let busy = dragging || scrubbing || (songE.map { now < $0.end } ?? false)
      if film && !busy && lastActivity >= 0 {
        let r0 = max(lastActivity, songE?.end ?? 0) + M.idle
        if now >= r0 {
          beginReturn(r0)
          return frameLocked(now)
        }
      }
      return manualFrame(now)
    }
  }

  /// Re-bases the record's angle and the song at `now` (before the speed or the song changes course).
  private func reanchor(_ now: Double) {
    platter0 = platterAt(now)
    song0 = songAt(now)
    songE = nil
    anchorT = now
  }

  // MARK: the return

  private func beginReturn(_ r0: Double) {
    reanchor(r0)
    mode = .back
    playing = false
    speedE = Ease(from: speedE.at(r0), to: 0, t0: r0, dur: 1.2, curve: M.outQuad)
    songE = Ease(from: song0, to: 0, t0: r0, dur: 1.4, curve: M.ease)
    volumeE = Ease(from: volumeE.at(r0), to: M.volume0, t0: r0, dur: 1.4, curve: M.inOutQuad)
    let s0 = stageE.at(r0)
    if s0 >= M.centredFrom && s0 <= M.centredTo {
      retE = Ease(from: retE.at(r0), to: 1, t0: r0, dur: 1.4, curve: M.ease)
      backEnd = r0 + 1.4
      backDone = { [unowned self] t in
        let p = platterAt(t)
        platterBase = p - M.platterAt(M.closeT)
        enterFilm(at: t, filmT: M.closeT)
      }
    } else {
      let target = s0 > M.centredTo ? M.loop : 0
      let dur = max(1.2, abs(target - s0) * 0.8)
      stageE = Ease(from: s0, to: target, t0: r0, dur: dur, curve: M.ease)
      backEnd = r0 + dur
      backDone = { [unowned self] t in
        platterBase = platterAt(t)
        enterFilm(at: t, filmT: 0)
      }
    }
  }

  private func enterFilm(at t: Double, filmT: Double) {
    mode = .auto
    start = t - filmT
    retE = .still(0)
    songE = nil
  }

  // MARK: touch

  /// Any touch takes the record over where the film (or the return) is; the film's state carries on as the
  /// listener's own: playing stays playing (up to full speed), a run-down finishes running down.
  private func takeOver(_ now: Double) {
    let f = frameLocked(now)
    let wasAuto = mode == .auto
    platter0 = f.platter
    song0 = f.song
    songE = nil
    anchorT = now
    stageE = .still(f.stage)
    retE = .still(f.ret)
    volumeE = .still(f.volume)
    playing = f.playing
    if wasAuto {
      if f.playing {
        speedE = Ease(from: f.level, to: 1, t0: now, dur: 1 - f.level + 0.001, curve: M.ease)
      } else if f.level > 0 {
        speedE = Ease(from: f.level, to: 0, t0: now, dur: 1.2, curve: M.outQuad)
      } else {
        speedE = .still(0)
      }
    } else if mode == .back {
      speedE = .still(f.level)
    }
    mode = .manual
    lastActivity = now
  }

  /// Play or pause: play brings the record out of its sleeve first and spins it up in 1 s; pause runs it down in 1.2 s.
  func toggle(_ now: Double) {
    locked {
      takeOver(now)
      reanchor(now)
      let v = speedE.at(now)
      if playing {
        playing = false
        speedE = Ease(from: v, to: 0, t0: now, dur: 1.2, curve: M.outQuad)
      } else {
        playing = true
        let s0 = stageE.at(now)
        if s0 < M.centredFrom {
          stageE = Ease(from: s0, to: M.stillT, t0: now, dur: max(0.5, (M.stillT - s0) * 0.7), curve: M.ease)
        } else if s0 > M.centredTo {
          stageE = Ease(from: s0, to: M.centredTo - 0.1, t0: now, dur: max(0.5, (s0 - M.centredTo) * 0.7), curve: M.ease)
        }
        speedE = Ease(from: v, to: 1, t0: now, dur: 1, curve: M.inOutQuad)
      }
    }
  }

  func seek(_ now: Double, forward: Bool) {
    locked {
      takeOver(now)
      reanchor(now)
      let to = forward ? min(M.trackSeconds - 1, song0 + M.skipS) : 0
      songE = Ease(from: song0, to: to, t0: now, dur: 0.5, curve: M.ease)
      lastActivity = now
    }
  }

  func scrub(_ now: Double, fraction: Double) {
    locked {
      if mode != .manual { takeOver(now) }
      reanchor(now)
      song0 = fraction * M.trackSeconds
      lastActivity = now
    }
  }

  func setScrubbing(_ active: Bool, now: Double) {
    locked {
      // The song slider can report the end of an edit and a new start in the same instant once its thumb has
      // glided to rest (seen when play or pause was tapped while it glided). No finger is on it then, and a
      // start that is never ended kept the film from ever coming back, so that echo is ignored.
      if active && now - scrubEndedAt < 0.1 { return }
      if mode != .manual { takeOver(now) }
      reanchor(now)
      scrubbing = active
      if !active { scrubEndedAt = now }
      lastActivity = now
    }
  }

  func setVolume(_ v: Double, now: Double) {
    locked {
      if mode != .manual { takeOver(now) }
      volumeE = .still(v)
      lastActivity = now
    }
  }

  // MARK: the rotary scrub

  private var lastAngle = 0.0
  private var centre = (x: 0.0, y: 0.0, reach: 0.0)

  /// A finger on the record turns it about its centre (where the stage's camera puts it), and the song with it.
  func dragBegan(_ now: Double, x: Double, y: Double, width: Double, height: Double) {
    locked {
      takeOver(now)
      reanchor(now)
      dragging = true
      let f = manualFrame(now)
      let sc = M.sceneAt(f.stage, f.ret)
      let b = M.basis(sc.shot)
      if let q = M.project(sc.shot, b, width, height, sc.pose.record) {
        centre = (q.x, q.y, M.rVinyl * q.z * 1.25)
      }
      lastAngle = atan2(y - centre.y, x - centre.x)
    }
  }

  /// Returns the song position after the move (for the detent haptics).
  @discardableResult
  func dragChanged(_ now: Double, x: Double, y: Double) -> Double {
    locked {
      lastActivity = now
      let dx = x - centre.x, dy = y - centre.y
      let a = atan2(dy, dx)
      let d = M.angleDelta(a, lastAngle)
      lastAngle = a
      let r2 = dx * dx + dy * dy
      if r2 < 14 * 14 || r2 > centre.reach * centre.reach { return song0 }
      // screen y points down, so a growing angle is clockwise, the way the record plays
      platter0 += d
      song0 = max(0, min(M.trackSeconds, song0 + d * M.seekPerRad))
      return song0
    }
  }

  func dragEnded(_ now: Double) {
    locked {
      dragging = false
      anchorT = now
      lastActivity = now
    }
  }
}
