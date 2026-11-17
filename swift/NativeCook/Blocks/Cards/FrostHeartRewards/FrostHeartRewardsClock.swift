import CoreMotion
import SwiftUI
import UIKit

/// A Reanimated-style animated number sampled by wall time: a set value, withTiming, withSpring or withDecay.
struct FrostHeartRewardsAnim {
  enum Kind { case value, timing, spring, decay }
  private(set) var kind = Kind.value
  private var from = 0.0, to = 0.0, v0 = 0.0, start = 0.0, duration = 0.0
  private var curve: (Double) -> Double = { $0 }
  private var k = 0.0, c = 0.0, lo = -Double.infinity, hi = Double.infinity, lambda = 0.0
  init(_ value: Double = 0) { from = value; to = value }

  static func inOutQuad(_ x: Double) -> Double { x < 0.5 ? 2 * x * x : 1 - pow(-2 * x + 2, 2) / 2 }

  func value(_ now: Double) -> Double {
    switch kind {
    case .value: return to
    case .timing:
      guard duration > 0 else { return to }
      return from + (to - from) * curve(min(1, max(0, (now - start) / duration)))
    case .spring:
      let t = max(0, now - start)
      let w = sqrt(k), z = c / (2 * w), a = from - to
      if z < 1 {
        let wd = w * sqrt(1 - z * z)
        let b = (v0 + z * w * a) / wd
        return to + exp(-z * w * t) * (a * cos(wd * t) + b * sin(wd * t))
      }
      return to + exp(-w * t) * (a + (v0 + w * a) * t)
    case .decay:
      let t = max(0, now - start) * 1000
      let x = from + v0 / 1000 * (1 - exp(-lambda * t)) / lambda
      return min(hi, max(lo, x))
    }
  }
  func velocity(_ now: Double) -> Double {
    let h = 1.0 / 240
    return (value(now + h) - value(now - h)) / (2 * h)
  }
  func settled(_ now: Double) -> Bool {
    switch kind {
    case .value: return true
    case .timing: return now - start >= duration
    case .spring: return now - start > 0.2 && abs(value(now) - to) < 0.001 && abs(velocity(now)) < 0.01
    case .decay: return (now - start) * 1000 * lambda > 9
    }
  }
  mutating func set(_ v: Double) { kind = .value; from = v; to = v }
  mutating func timing(_ target: Double, _ seconds: Double, _ now: Double, curve: @escaping (Double) -> Double = FrostHeartRewardsAnim.inOutQuad) {
    from = value(now); to = target; start = now; duration = seconds; self.curve = curve; kind = .timing
  }
  mutating func spring(_ target: Double, damping: Double, stiffness: Double, _ now: Double) {
    let v = velocity(now)
    from = value(now); to = target; v0 = kind == .value ? 0 : v; start = now; k = stiffness; c = damping; kind = .spring
  }
  mutating func decay(velocity: Double, deceleration: Double, clamp: (Double, Double), _ now: Double) {
    from = value(now); v0 = velocity; start = now; lambda = (1 - deceleration) * 0.1; lo = clamp.0; hi = clamp.1; kind = .decay
  }
}

/// FrostHeartRewards.tsx's shared values and frame callback: the demo loop, the pinned DEBUG
/// frame, and the touch session (claims, breath, hold-to-polish, drag-to-turn, return to rest).
final class FrostHeartRewardsClock: ObservableObject {
  typealias M = FrostHeartRewardsMotion
  typealias A = FrostHeartRewardsAnim
  static let NEVER = -1000.0

  struct Spoken: Equatable { var balance = 1650; var level = 3; var claimed = [true, true, false, false, false, false, false] }
  @Published private(set) var spoken = Spoken()
  @Published private(set) var touchSession = false

  var time = 0.0
  var local = 0.0
  var interactive = false
  /// 0 demo, 1 manual polish/claim, 2 Atelier sequence, 3 blur return.
  var mode = 0
  var touchedAt = -10000.0
  var claims = [NEVER, NEVER, NEVER, NEVER, NEVER]
  var finger = false
  var moved = false
  var holdWall = 0.0
  var polishX = 0.0
  var breath = A(0)
  var breathX = 133.0
  var breathY = 258.0
  var target = 0
  var charge = A(0)
  var dragX = A(0)
  var dragY = A(0)
  var ring = A(0)
  var jelly = A(0)
  var reset = 0.0
  var lowMotionLevel = A(0)
  private var levelReportAt = -1.0
  private var lastDate: Date?
  private var motion: CMMotionManager?

  struct Sample {
    var scene: M.Scene
    var tiltX = 0.0, tiltY = 0.0, jelly = 0.0, ring = 0.0
    var chipAge: [Double]
    var gem: FrostHeartRewardsGemFrame
  }

  static func wall() -> Double { Date().timeIntervalSince1970 }

  func startMotion() {
    guard motion == nil else { return }
    let m = CMMotionManager()
    guard m.isDeviceMotionAvailable else { return }
    m.deviceMotionUpdateInterval = 0.032
    m.startDeviceMotionUpdates()
    motion = m
  }
  func stopMotion() { motion?.stopDeviceMotionUpdates(); motion = nil }

  // MARK: scene

  func scene(_ now: Double, reduced: Bool) -> M.Scene {
    var s = M.sceneAt(time)
    guard interactive else { return s }
    var balance = 1650.0
    for i in 0..<5 where claims[i] >= 0 {
      let age = local - claims[i] - (reduced ? 0 : 0.65)
      balance += M.VALUES[i] * M.ease(M.seg(age, 0, reduced ? 0.16 : 0.18))
    }
    s.balance = balance
    s.remaining = max(0, 65 - (balance - 1650))
    s.polish = reduced ? 0 : charge.value(now)
    s.polishX = polishX
    s.breath = time < 2.4 ? breath.value(now) : 0
    s.breathX = breathX
    s.breathY = breathY
    if mode == 3 { s.reset = reset }
    if reduced {
      var from = 1650.0, to = 1650.0, blend = 1.0
      for i in 0..<5 where claims[i] >= 0 {
        let age = local - claims[i]
        if age >= 0.16 { from += M.VALUES[i]; to += M.VALUES[i] } else if age >= 0 { to += M.VALUES[i]; blend = min(blend, M.ease(M.clamp(age / 0.16))) }
      }
      s.balanceFrom = from; s.balanceTo = to; s.numberFade = blend; s.balance = to
      let level = lowMotionLevel.value(now)
      s.gem = level; s.hue = level; s.flip = level; s.level = level > 0.5 ? 4 : 3
      s.broken = 0; s.charge = 0; s.core = 0; s.display = 0; s.push = 0; s.spotlight = level; s.cut = level
    }
    return s
  }

  /// One sampled frame. `pinned` is the DEBUG hook (`-frostHeartRewardsTime <t>`).
  func sample(_ date: Date, autoPlay: Bool, reduced: Bool, pinned: Double?) -> Sample {
    let now = Self.wall()
    if let pinned {
      interactive = false
      mode = 0
      let frameTime = (pinned.truncatingRemainder(dividingBy: M.LOOP) + M.LOOP).truncatingRemainder(dividingBy: M.LOOP)
      time = frameTime
      local = frameTime + M.LOOP
      return makeSample(now, reduced: reduced, snapshot: true)
    }
    let dt = lastDate.map { min(0.5, max(0, date.timeIntervalSince($0))) } ?? 0
    lastDate = date
    if !reduced || interactive { step(dt, now, autoPlay: autoPlay, reduced: reduced) }
    return makeSample(now, reduced: reduced, snapshot: false)
  }

  private func makeSample(_ now: Double, reduced: Bool, snapshot: Bool) -> Sample {
    let s = scene(now, reduced: reduced)
    var roll = 0.0, pitch = 0.0
    if !snapshot, let attitude = motion?.deviceMotion?.attitude { roll = attitude.roll; pitch = attitude.pitch }
    let tx = reduced ? 0 : snapshot ? 0 : M.clamp(dragX.value(now) + roll * 5, -8, 8)
    let ty = reduced ? 0 : snapshot ? 0 : M.clamp(dragY.value(now) + pitch * 3, -8, 8)
    let j = snapshot ? 0 : jelly.value(now)
    let r = snapshot ? 0 : ring.value(now)
    var ages = [Double](repeating: 1000, count: 7)
    for i in 2..<7 {
      ages[i] = interactive ? (claims[i - 2] < 0 ? -1000 : local - claims[i - 2]) : s.t - M.CLAIMS[i - 2]
    }
    var gem = FrostHeartRewardsGemFrame()
    gem.scene = s
    gem.time = time
    gem.ring = r
    gem.tiltX = tx
    gem.tiltY = ty
    gem.jelly = j
    gem.interactive = interactive
    gem.touchTime = interactive && !reduced ? local : 0
    gem.claims = claims
    gem.reduced = reduced
    gem.snapshot = snapshot
    return Sample(scene: s, tiltX: tx, tiltY: ty, jelly: j, ring: r, chipAge: ages, gem: gem)
  }

  /// Whether the timeline must keep running.
  func moving(_ now: Double, autoPlay: Bool, reduced: Bool) -> Bool {
    if interactive { return true }
    if !(jelly.settled(now) && ring.settled(now) && dragX.settled(now) && dragY.settled(now) && breath.settled(now) && lowMotionLevel.settled(now)) { return true }
    return autoPlay && !reduced
  }

  // MARK: frame callback

  private func step(_ dt: Double, _ now: Double, autoPlay: Bool, reduced: Bool) {
    local += dt
    let before = time
    if !interactive {
      if autoPlay && !reduced { time = (before + dt).truncatingRemainder(dividingBy: M.LOOP) } else { time = 0 }
      // Untouched autoplay stays silent (React Native); the site's recording voices the cut too.
      if !reduced && DemoSound.inAutoPlay && before < 3.8 && time >= 3.8 { cue("cutComplete") }
      if autoPlay && !reduced && DemoSound.inAutoPlay { autoCues(before, time) }
      return
    }
    if finger && !moved && target == 1 && !reduced {
      let age = Self.wall() - holdWall - 0.18
      if age > 0 { charge.set(M.ease(M.clamp(age / 0.8))) }
    }
    if mode == 1 && time == 0 && scene(now, reduced: reduced).remaining <= 0 && !finger {
      if reduced {
        time = 8.9
        lowMotionLevel.timing(1, 0.22, now)
        levelReportAt = now + 0.22
        cue("level")
      } else {
        time = 1.7; mode = 2; cue("frost")
      }
    }
    if levelReportAt >= 0 && now >= levelReportAt { levelReportAt = -1; report(now, reduced: reduced) }
    if mode == 2 {
      let playingBefore = time
      if !finger { time = min(8.9, playingBefore + dt) }
      let after = time
      if !reduced && playingBefore < 3.8 && after >= 3.8 { cue("cutComplete") }
      if playingBefore < 2.4 && after >= 2.4 { cue("frost") }
      for k in 0..<12 {
        let at = 5.8 + Double(k) * 0.04
        if k % 3 == 0 && playingBefore < at && after >= at { cue("snap") }
      }
      if playingBefore < 6.95 && after >= 6.95 {
        cue("level")
        var mask = 0, value = 1650.0
        for i in 0..<5 where claims[i] >= 0 { mask |= 1 << i; value += M.VALUES[i] }
        announce(value, 4, mask)
      }
      if after >= 8.9 { mode = 1 }
    }
    for i in 0..<5 where claims[i] >= 0 {
      let age = local - claims[i]
      if age >= 0.65 && age - dt < 0.65 && !reduced { cue("tinkle") }
      let settled = reduced ? 0.16 : 0.83
      if age >= settled && age - dt < settled {
        var mask = 0, value = 1650.0
        for j in 0..<5 where claims[j] >= 0 { mask |= 1 << j; value += M.VALUES[j] }
        announce(value, scene(now, reduced: reduced).level, mask)
      }
    }
    if !finger && mode != 3 && local - touchedAt > 2 { mode = 3; reset = 0 }
    if mode == 3 {
      reset = min(1, reset + dt / (reduced ? 0.25 : 1.1))
      if reset >= 1 {
        interactive = false
        mode = 0
        time = 0
        charge.set(0)
        lowMotionLevel.set(0)
        claims = [Self.NEVER, Self.NEVER, Self.NEVER, Self.NEVER, Self.NEVER]
        ring.set(0)
        announce(1650, 3, 0)
        DispatchQueue.main.async { self.touchSession = false }
      }
    }
  }

  /// The demo's other beats, voiced only while the site records the demo (DemoSound.inAutoPlay).
  private func autoCues(_ a: Double, _ b: Double) {
    func crossed(_ t: Double) -> Bool { a < t && b >= t }
    if crossed(0.6) || crossed(0.95) { cue("claim3") }
    if crossed(1.25) || crossed(1.6) { cue("tinkle") }
    if crossed(1.7) || crossed(2.4) { cue("frost") }
    for k in stride(from: 0, to: 12, by: 3) where crossed(5.8 + Double(k) * 0.04) { cue("snap") }
    if crossed(6.95) { cue("level") }
  }

  // MARK: cues

  func cue(_ name: String) {
    let res = FrostHeartRewardsResources.shared
    switch name {
    case "cutComplete":
      res.play("cut-ting")
      UIImpactFeedbackGenerator(style: .rigid).impactOccurred()
    case "tick", "snap":
      UIImpactFeedbackGenerator(style: .rigid).impactOccurred()
    case "impact":
      UIImpactFeedbackGenerator(style: .soft).impactOccurred()
    default:
      res.play(name)
      if name.hasPrefix("claim") { UIImpactFeedbackGenerator(style: .soft).impactOccurred() }
      if name == "level" { UIImpactFeedbackGenerator(style: .rigid).impactOccurred() }
    }
  }

  private func announce(_ balance: Double, _ level: Int, _ mask: Int) {
    let value = Spoken(balance: Int(balance.rounded()), level: level, claimed: (0..<7).map { $0 < 2 || (mask & (1 << ($0 - 2))) != 0 })
    DispatchQueue.main.async { if self.spoken != value { self.spoken = value } }
  }
  func report(_ now: Double, reduced: Bool) {
    let s = scene(now, reduced: reduced)
    var mask = 0
    for i in 0..<5 where claims[i] >= 0 { mask |= 1 << i }
    announce(s.balance, s.level, mask)
  }

  // MARK: touch

  func takeOver(_ now: Double) {
    if !interactive {
      var existing = [Self.NEVER, Self.NEVER, Self.NEVER, Self.NEVER, Self.NEVER]
      for i in 0..<5 where time < 9.9 && time >= M.CLAIMS[i] { existing[i] = local - (time - M.CLAIMS[i]) }
      claims = existing
      mode = time >= 1.7 && time < 8.9 ? 2 : 1
      if mode == 1 { time = 0 }
      DispatchQueue.main.async { self.touchSession = true }
    }
    if mode == 3 {
      mode = 1
      time = 0
      charge.set(0)
    }
    interactive = true
    touchedAt = local
    reset = 0
  }
  func claimChip(_ i: Int, _ now: Double) {
    takeOver(now)
    if claims[i] >= 0 { return }
    claims[i] = local
    cue("claim\(min(i, 3))")
  }
  func bounceBear(_ now: Double) {
    takeOver(now)
    jelly.set(1)
    jelly.spring(0, damping: 20, stiffness: 230, now)
    cue("impact")
  }
  func revealLevel(_ now: Double, reduced: Bool) {
    takeOver(now)
    if reduced {
      lowMotionLevel.timing(1, 0.22, now)
      levelReportAt = now + 0.22
    } else {
      time = 1.7
      mode = 2
    }
    cue("frost")
  }

  /// Pan with minDistance 0 over the hero: begin, update, end/finalize. Points are design points.
  func panBegan(_ p: CGPoint, _ now: Double, reduced: Bool) {
    takeOver(now)
    finger = true
    moved = false
    holdWall = Self.wall()
    let x = Double(p.x), y = Double(p.y)
    polishX = M.clamp((x - 175) / 105, -1.2, 1.2)
    ring.set(ring.value(now))
    target = x > 75 && x < 283 && y > 225 && y < 367 ? 1 : 2
    if target == 1 && time < 2.4 {
      breathX = x; breathY = y
      breath.timing(1, reduced ? 0.22 : 0.3, now, curve: M.cubicOut)
    }
  }
  func panChanged(_ p: CGPoint, _ translation: CGSize, _ now: Double, reduced: Bool) {
    touchedAt = local
    let x = Double(p.x), y = Double(p.y)
    if target == 1 && time < 2.4 { breathX = x; breathY = y }
    polishX = M.clamp((x - 175) / 105, -1.2, 1.2)
    let tx = Double(translation.width), ty = Double(translation.height)
    if hypot(tx, ty) > 8 { moved = true }
    if !moved || reduced { return }
    dragX.set(8 * tanh(tx / 95))
    dragY.set(8 * tanh(ty / 95))
    ring.set(0.61 * tanh(tx / 105))
    if mode == 1 { charge.timing(0, 0.4, now) }
  }
  func panEnded(_ velocityX: Double, _ now: Double, reduced: Bool) {
    if moved && !reduced { ring.decay(velocity: velocityX * 0.003, deceleration: 0.985, clamp: (-0.61, 0.61), now) }
    if !moved && Self.wall() - holdWall < 0.25 {
      if target == 2 {
        jelly.set(1)
        jelly.spring(0, damping: 20, stiffness: 230, now)
        cue("impact")
      } else if reduced {
        lowMotionLevel.timing(lowMotionLevel.value(now) > 0.5 ? 0 : 1, 0.22, now)
        levelReportAt = now + 0.22
        cue("level")
      }
    }
    report(now, reduced: reduced)
    finger = false
    breath.timing(0, 1.2, now, curve: M.cubicOut)
    touchedAt = local
    dragX.spring(0, damping: 16, stiffness: 130, now)
    dragY.spring(0, damping: 16, stiffness: 130, now)
    charge.timing(0, reduced ? 0.16 : 0.24, now)
  }

  func a11yClaim(_ i: Int, _ now: Double) {
    claimChip(i, now)
    var s = spoken
    if !s.claimed[i + 2] {
      s.claimed[i + 2] = true
      s.balance += Int(M.VALUES[i])
      spoken = s
    }
  }

  func resetAll() {
    interactive = false
    mode = 0
    time = 0
    local = 0
    claims = [Self.NEVER, Self.NEVER, Self.NEVER, Self.NEVER, Self.NEVER]
    charge.set(0); ring.set(0); jelly.set(0); dragX.set(0); dragY.set(0); breath.set(0); lowMotionLevel.set(0)
    reset = 0
    lastDate = nil
  }
  func pause() { lastDate = nil; FrostHeartRewardsResources.shared.stopSounds() }
}
