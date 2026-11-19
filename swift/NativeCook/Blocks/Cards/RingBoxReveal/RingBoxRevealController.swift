import Foundation
import QuartzCore

/// The block's one source of truth: the film's clock and whatever a finger set. `frame(at:)` is a pure function of
/// the stored fields and a media time (CACurrentMediaTime), read by SceneKit's render loop (the 3D stage) and by
/// SwiftUI's TimelineView (the floor, the glints, the controls), so the two never disagree.
///
/// React Native's modes: the film (auto, played from the buyer's choices); a finger's values (manual: timings and
/// springs started by a touch, a pick or a tap); and 2 s after the last touch the ease back (the camera pulls back,
/// the lid closes, the choices blend home), after which the film restarts.
final class RingBoxRevealController {
  typealias M = RingBoxRevealMotion

  /// One animated value: still, a timing (with a delay and a curve) or a damped spring.
  struct Track {
    enum Kind { case timing((Double) -> Double), spring(w: Double, z: Double) }
    var from: Double, to: Double, t0: Double, dur: Double, v0 = 0.0
    var kind: Kind = .timing({ $0 })

    static func still(_ v: Double) -> Track { Track(from: v, to: v, t0: 0, dur: 0) }
    static func timing(_ from: Double, _ to: Double, _ now: Double, _ dur: Double, delay: Double = 0,
                       _ curve: @escaping (Double) -> Double = { $0 }) -> Track {
      Track(from: from, to: to, t0: now + delay, dur: dur, kind: .timing(curve))
    }
    /// Reanimated's withSpring(damping, stiffness, mass) from the current value and velocity.
    static func spring(_ from: Double, _ to: Double, _ now: Double, damping: Double, stiffness: Double,
                       mass: Double = 1, velocity: Double = 0) -> Track {
      let w = sqrt(stiffness / mass), z = damping / (2 * sqrt(stiffness * mass))
      return Track(from: from, to: to, t0: now, dur: min(4, 6 / (z * w)), v0: velocity, kind: .spring(w: w, z: z))
    }

    var end: Double { t0 + dur }
    func at(_ now: Double) -> Double {
      if now >= t0 + dur { return to }
      if now <= t0 { return from }
      switch kind {
      case .timing(let curve):
        return from + (to - from) * curve(M.clamp01((now - t0) / dur))
      case .spring(let w, let z):
        let t = now - t0, e = from - to
        if z < 1 {
          let wd = w * sqrt(1 - z * z)
          return to + exp(-z * w * t) * (e * cos(wd * t) + (v0 + z * w * e) / wd * sin(wd * t))
        }
        return to + exp(-w * t) * (e + (v0 + w * e) * t)
      }
    }
    func moving(_ now: Double) -> Bool { now < t0 + dur }
  }

  struct Frame {
    var pose: RingBoxRevealPose
    var pickerMetal: Int
    var pickerStone: Int
    var sliderCarat: Double
    var booked: Bool
    var filmPlaying: Bool
  }

  enum Mode { case auto, manual, back }

  static let lidPt = 110.0, yawPerPt = 0.008, yawLimit = 0.7

  private let lock = NSLock()
  let film: Bool
  let reduced: Bool

  private var mode: Mode
  private var start: Double?
  private var heldT = 0.0
  private var metalHome = 0, stoneHome = 0
  private var caratHome = M.caratModel
  private var open: Track, shot: Track, yaw = Track.still(0), burst = Track.still(0), carat = Track.still(M.caratModel)
  private var mFrom = 0, mTo = 0, mProg = Track.still(0)
  private var sFrom = 0, sTo = 0, sProg = Track.still(0)
  private var lastActivity = 0.0
  private var busyUntil = 0.0
  private var bookedUntil = -1.0
  private(set) var dragging = false
  private var dragKind = 0  // 0 none, 1 lid, 2 turn
  private var dragFrom = 0.0, yawFrom = 0.0

  init(autoPlay: Bool, reduced: Bool) {
    self.reduced = reduced
    film = autoPlay && !reduced
    mode = film ? .auto : .manual
    open = .still(reduced ? 1 : 0)
    shot = .still(reduced ? 1 : 0)
  }

  private func locked<T>(_ f: () -> T) -> T { lock.lock(); defer { lock.unlock() }; return f() }

  func frame(at now: Double) -> Frame { locked { frameLocked(now) } }

  /// Starts the film's clock with the first frame the box is on screen.
  func begin(_ now: Double) { locked { if mode == .auto && start == nil { start = now } } }

  private func filmPose(_ t: Double) -> RingBoxRevealPose {
    var p = RingBoxRevealPose()
    p.t = t
    p.open = M.filmOpen(t)
    p.shot = M.filmShot(t)
    p.yaw = M.filmYaw(t)
    p.burst = M.filmBurst(t)
    let fm = M.filmSteps(t, M.metalSteps)
    p.mFrom = (metalHome + fm.0) % M.metals.count
    p.mTo = (metalHome + fm.1) % M.metals.count
    p.mProg = fm.2
    let fs = M.filmSteps(t, M.stoneSteps)
    p.sFrom = (stoneHome + fs.0) % M.stones.count
    p.sTo = (stoneHome + fs.1) % M.stones.count
    p.sProg = fs.2
    p.carat = M.filmCarat(t, caratHome)
    return p
  }

  private func manualPose(_ now: Double) -> RingBoxRevealPose {
    RingBoxRevealPose(t: heldT, open: open.at(now), shot: shot.at(now), yaw: yaw.at(now), burst: burst.at(now),
                      mFrom: mFrom, mTo: mTo, mProg: mProg.at(now), sFrom: sFrom, sTo: sTo, sProg: sProg.at(now),
                      carat: carat.at(now))
  }

  private func frameLocked(_ now: Double) -> Frame {
    if mode == .auto {
      guard let start else {
        let p = filmPose(0)
        return Frame(pose: p, pickerMetal: metalHome, pickerStone: stoneHome, sliderCarat: caratHome, booked: false, filmPlaying: true)
      }
      let t = max(0, now - start).truncatingRemainder(dividingBy: M.loop)
      let p = filmPose(t)
      return Frame(pose: p, pickerMetal: p.mProg <= 0.001 ? p.mFrom : p.mTo, pickerStone: p.sProg <= 0.001 ? p.sFrom : p.sTo,
                   sliderCarat: M.filmCaratStep(t, caratHome), booked: M.filmBooked(t), filmPlaying: true)
    }
    if mode == .manual && film && !dragging {
      let r0 = max(lastActivity, busyUntil, mProg.end, sProg.end) + M.idle
      if now >= r0 { startReturn(r0) }
    }
    if mode == .back && now >= open.end {
      mode = .auto
      start = open.end
      return frameLocked(now)
    }
    let p = manualPose(now)
    return Frame(pose: p, pickerMetal: mTo, pickerStone: sTo, sliderCarat: caratHome, booked: now < bookedUntil, filmPlaying: false)
  }

  /// 2 s after the last touch: the camera pulls back, the lid closes, the choices blend home; then the film.
  private func startReturn(_ r0: Double) {
    let p = manualPose(r0)
    let d = M.returnDuration
    shot = .timing(p.shot, 0, r0, d * 0.7, M.easeTouch)
    yaw = .timing(p.yaw, 0, r0, d, M.easeTouch)
    burst = .timing(p.burst, 0, r0, 0.3)
    carat = .timing(p.carat, caratHome, r0, 0.4)
    if mTo != metalHome || p.mProg < 1 {
      mFrom = p.mProg >= 0.5 ? mTo : mFrom
      mTo = metalHome
      mProg = .timing(0, 1, r0, M.blendS)
    }
    if sTo != stoneHome || p.sProg < 1 {
      sFrom = p.sProg >= 0.5 ? sTo : sFrom
      sTo = stoneHome
      sProg = .timing(0, 1, r0, M.blendS)
    }
    open = .timing(p.open, 0, r0, d * 0.7, delay: d * 0.3, M.easeTouch)
    mode = .back
  }

  // MARK: touch

  /// Any touch pauses the film (or the ease back) where it is: the current pose becomes the manual values.
  private func pause(_ now: Double) {
    let f = frameLocked(now)
    let p = f.pose
    heldT = p.t
    open = .still(p.open)
    shot = .still(p.shot)
    yaw = .still(p.yaw)
    burst = .still(p.burst)
    carat = .still(p.carat)
    if p.mProg >= 1 || p.mProg <= 0 {
      let m = p.mProg >= 1 ? p.mTo : p.mFrom
      mFrom = m; mTo = m; mProg = .still(1)
    } else {
      mFrom = p.mFrom; mTo = p.mTo; mProg = .timing(p.mProg, 1, now, M.blendS * (1 - p.mProg))
    }
    if p.sProg >= 1 || p.sProg <= 0 {
      let s = p.sProg >= 1 ? p.sTo : p.sFrom
      sFrom = s; sTo = s; sProg = .still(1)
    } else {
      sFrom = p.sFrom; sTo = p.sTo; sProg = .timing(p.sProg, 1, now, M.blendS * (1 - p.sProg))
    }
    if mode == .auto {
      // the film's choices at this moment are what the controls show; its booking ends with it
      bookedUntil = f.booked ? now + 0.5 : -1
    }
    mode = .manual
    lastActivity = now
  }

  private func snapOpen(_ now: Double) {
    let cur = open.at(now)
    if reduced {
      open = .timing(cur, 1, now, 0.2)
      shot = .timing(shot.at(now), 1, now, 0.2)
      busyUntil = now + 0.2
      return
    }
    open = .spring(cur, 1, now, damping: 12, stiffness: 340, mass: 0.6)
    busyUntil = open.end
    burst = .timing(1, 0, now, 1.3, delay: 0.15, M.easeOutQuad)
    shot = .timing(shot.at(now), 1, now, 1.2, delay: 0.35, M.easeTouch)
  }

  private func closeBox(_ now: Double) {
    shot = .timing(shot.at(now), 0, now, 0.7, M.easeTouch)
    yaw = .timing(yaw.at(now), 0, now, 0.6, M.easeTouch)
    open = .timing(open.at(now), 0, now, reduced ? 0.2 : 0.65, delay: 0.25, M.easeInCubic)
    busyUntil = open.end
  }

  func tap(_ now: Double) {
    locked {
      pause(now)
      if open.at(now) < 0.5 { snapOpen(now); return }
      let s = shot.at(now)
      shot = .timing(s, s < 1.5 ? 2 : 1, now, reduced ? 0.2 : 1.0, M.easeTouch)
      lastActivity = max(lastActivity, shot.end)
    }
  }

  func dragBegan(_ now: Double) {
    locked {
      pause(now)
      dragging = true
      dragKind = 0
      dragFrom = open.at(now)
      yawFrom = yaw.at(now)
    }
  }

  func dragChanged(_ now: Double, dx: Double, dy: Double) {
    locked {
      lastActivity = now
      if dragKind == 0 {
        if abs(dx) < 8 && abs(dy) < 8 { return }
        dragKind = abs(dx) > abs(dy) && dragFrom > 0.6 ? 2 : 1
        if dragKind == 1 { busyUntil = 0 }
      }
      if dragKind == 1 {
        open = .still(min(1.08, max(0, dragFrom - dy / Self.lidPt)))
      } else {
        var v = yawFrom + dx * Self.yawPerPt
        let over = abs(v) - Self.yawLimit
        if over > 0 { v = (v < 0 ? -1 : 1) * (Self.yawLimit + over / (1 + over * 3)) }
        yaw = .still(v)
      }
    }
  }

  func dragEnded(_ now: Double, vx: Double, vy: Double) {
    locked {
      dragging = false
      lastActivity = now
      if dragKind == 1 {
        let projected = open.to - vy / Self.lidPt * 0.2
        if projected > 0.45 { snapOpen(now) } else { closeBox(now) }
      } else if dragKind == 2 {
        yaw = .spring(yaw.to, 0, now, damping: 10, stiffness: 40, velocity: vx * Self.yawPerPt)
        lastActivity = max(lastActivity, yaw.end - 1.5)
      }
      dragKind = 0
    }
  }

  func pickMetal(_ i: Int, now: Double) {
    locked {
      pause(now)
      metalHome = i
      let current = mProg.at(now) >= 0.5 ? mTo : mFrom
      if current == i && !mProg.moving(now) { return }
      mFrom = current
      mTo = i
      mProg = .timing(0, 1, now, reduced ? 0.2 : M.blendS)
    }
  }

  func pickStone(_ i: Int, now: Double) {
    locked {
      pause(now)
      stoneHome = i
      let current = sProg.at(now) >= 0.5 ? sTo : sFrom
      if current == i && !sProg.moving(now) { return }
      sFrom = current
      sTo = i
      sProg = .timing(0, 1, now, reduced ? 0.2 : M.blendS)
    }
  }

  func pickCarat(_ ct: Double, now: Double) {
    locked {
      let v = (ct * 4).rounded() / 4
      pause(now)
      caratHome = v
      carat = reduced ? .still(v) : .spring(carat.at(now), v, now, damping: 14, stiffness: 180)
    }
  }

  func book(_ now: Double) {
    locked {
      pause(now)
      bookedUntil = now + M.bookedTouch
    }
  }
}
