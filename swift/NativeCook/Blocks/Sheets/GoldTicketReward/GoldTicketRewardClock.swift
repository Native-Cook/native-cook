import AVFoundation
import CoreMotion
import SwiftUI
import UIKit

final class GoldTicketRewardClock: ObservableObject {
  typealias M = GoldTicketRewardMotion
  typealias D = GoldTicketRewardDriver
  typealias H = GoldTicketRewardTheme
  struct Sample {
    var frame: M.Frame
    var live: GoldTicketRewardArt.Live
    var fade: Double
  }
  @Published var running = false
  @Published var fading = false
  private var driver = D()
  private var date: Date?
  private var now = 0.0
  private var target = 0
  private var start = CGPoint.zero
  private var scrubbed = 0.0
  private var fade = 1.0
  private var fadeStart = 1.0
  private var fadeTarget = 1.0
  private var fadeAt = 0.0
  private let motion = CMMotionManager()
  private var gyroInitialized = false
  private var pitch = 0.0
  private var roll = 0.0
  private var gyroX = 0.0
  private var gyroY = 0.0
  private var players: [String: AVAudioPlayer] = [:]
  private let light = UIImpactFeedbackGenerator(style: .light)
  private let medium = UIImpactFeedbackGenerator(style: .medium)
  private let selection = UISelectionFeedbackGenerator()

  func reset(_ reduced: Bool) {
    driver = D()
    driver.t = reduced ? M.PEAK : 0
    date = nil
    now = 0
    fade = 1
    fadeTarget = 1
    fading = false
    running = false
    gyroInitialized = false
    gyroX = 0
    gyroY = 0
  }
  func active(_ active: Bool, _ reduced: Bool, _ pinned: Bool) {
    date = nil
    if active && !reduced && !pinned {
      if motion.isDeviceMotionAvailable && !motion.isDeviceMotionActive {
        motion.deviceMotionUpdateInterval = 1.0 / 60
        motion.startDeviceMotionUpdates()
      }
      if players.isEmpty {
        try? AVAudioSession.sharedInstance().setCategory(
          .ambient, mode: .default, options: .mixWithOthers)
        for name in ["reveal", "coin", "sparkle"] {
          if let url = Bundle.main.url(
            forResource: "GoldTicketReward-gtr-\(name)", withExtension: "wav"),
            let player = try? AVAudioPlayer(contentsOf: url)
          {
            player.volume = name == "sparkle" ? 0.7 : 0.9
            player.prepareToPlay()
            players[name] = player
          }
        }
      }
    } else {
      motion.stopDeviceMotionUpdates()
      for player in players.values { player.stop() }
    }
  }
  deinit { motion.stopDeviceMotionUpdates() }
  private func cue(_ name: String) {
    if let player = players[name] {
      player.currentTime = 0
      player.play()
    }
  }
  private func buzz(_ kind: Int) {
    if kind == 2 {
      selection.selectionChanged()
    } else if kind == 1 {
      medium.impactOccurred()
    } else {
      light.impactOccurred()
    }
  }
  func sample(_ at: Date, _ auto: Bool, _ reduced: Bool, _ pinned: Double?) -> Sample {
    if let pinned { return Sample(frame: M.frameAt(pinned), live: .init(), fade: 1) }
    let dt = min(max(0, date.map { at.timeIntervalSince($0) } ?? 0.016), 0.05)
    date = at
    now += dt
    if reduced {
      if fading {
        let p = min(1, (now - fadeAt) / 0.22)
        fade = M.mix(fadeStart, fadeTarget, p)
        if p >= 1 { DispatchQueue.main.async { [weak self] in self?.fading = false } }
      }
      return Sample(frame: M.frameAt(M.PEAK), live: .init(), fade: fade)
    }
    let before = driver.t
    let t = driver.step(dt, now, auto)
    let extra = driver.coinExtra(now)
    let twinkle = driver.ctaTwinkle(now)
    let rotation = motion.deviceMotion?.attitude
    let currentPitch = rotation?.pitch ?? 0
    let currentRoll = rotation?.roll ?? 0
    if !gyroInitialized && (currentPitch != 0 || currentRoll != 0) {
      gyroInitialized = true
      pitch = currentPitch
      roll = currentRoll
    }
    let baseline = min(1, dt * 0.6)
    pitch += (currentPitch - pitch) * baseline
    roll += (currentRoll - roll) * baseline
    let smoothing = 1 - exp(-dt * 10)
    gyroX += (max(-1, min(1, (currentRoll - roll) / 0.35)) - gyroX) * smoothing
    gyroY += (max(-1, min(1, (currentPitch - pitch) / 0.35)) - gyroY) * smoothing
    let amount = D.bAmountAt(t) * 0.6
    if driver.mode == 1 && before < M.T.land && t >= M.T.land && t < M.T.hold[0] {
      cue("coin")
      buzz(1)
    }
    return Sample(
      frame: M.frameAt(t, extra.spin, extra.hop, twinkle),
      live: .init(
        tiltX: driver.tiltX + gyroX * amount, tiltY: driver.tiltY + gyroY * amount, cta: driver.ctaP
      ), fade: 1)
  }
  private func inBox(_ p: CGPoint, _ box: H.Box, _ pad: Double) -> Bool {
    p.x >= box.x - pad && p.x <= box.x + box.w + pad && p.y >= box.y - pad
      && p.y <= box.y + box.h + pad
  }
  private func near(_ p: CGPoint, _ x: Double, _ y: Double, _ r: Double) -> Bool {
    (p.x - x) * (p.x - x) + (p.y - y) * (p.y - y) <= r * r
  }
  private func toggleReduced(_ toB: Bool) {
    fadeStart = fade
    fadeTarget = toB ? 1 : 0
    fadeAt = now
    date = nil
    fading = true
  }
  func touch(
    _ state: UIGestureRecognizer.State, _ point: CGPoint, _ translation: CGSize,
    _ velocity: CGVector, _ size: CGSize, _ reduced: Bool
  ) {
    let scale = size.width / 402
    let dy = size.height - 874 * scale
    let p = CGPoint(x: point.x / scale, y: (point.y - dy) / scale)
    switch state {
    case .began:
      start = p
      scrubbed = 0
      target = 0
      let a = reduced ? fade < 0.5 : D.inRestA(driver.t)
      let b = reduced ? fade >= 0.5 : D.inRewardB(driver.t)
      if a {
        if inBox(p, H.A.button, 6) {
          target = 6
        } else if inBox(p, H.A.ticket, 10) || near(p, H.A.coin.x, H.A.coin.y, H.A.halo + 6) {
          target = 1
        }
      } else if b {
        if near(p, H.B.close.x, H.B.close.y, 24) {
          target = 2
        } else if near(p, H.B.coin.x, H.B.coin.y, H.B.coin.r + 10) {
          target = 3
        } else if inBox(p, H.B.button, 6) {
          target = 4
        } else if inBox(p, H.B.ticket, 12) {
          target = 5
        }
      }
      if reduced { return }
      driver.touched(now)
      if target == 1 || target == 6 { driver.pressA() }
      if target == 4 || target == 6 { driver.ctaAim = 1 }
      running = true
    case .changed:
      if reduced { return }
      driver.last = now
      // RN translates the gesture in screen points, while hit targets use reference space.
      if target == 1 && (translation.height < -6 || scrubbed > 0) {
        let before = scrubbed
        let p = driver.scrubTo(translation.height)
        scrubbed = max(p, 0.0001)
        if (before < D.COMMIT) != (p < D.COMMIT) && before > 0 { buzz(2) }
      } else if target == 5 {
        let box = H.B.ticket
        driver.aimX = max(-1, min(1, (p.x - (box.x + box.w / 2)) / (box.w / 2)))
        driver.aimY = max(-1, min(1, (p.y - (box.y + box.h / 2)) / (box.h * 1.2)))
      }
    case .ended, .cancelled, .failed:
      let tap = hypot(p.x - start.x, p.y - start.y) < 12
      let hit = target
      target = 0
      driver.ctaAim = 0
      if reduced {
        if tap && (hit == 1 || hit == 6) {
          toggleReduced(true)
        } else if tap && hit == 2 {
          toggleReduced(false)
        }
        return
      }
      if hit == 1 && scrubbed > 0 {
        if driver.scrubEnd(translation.height, velocity.dy) { cue("reveal") }
      } else if hit == 1 || hit == 6 {
        let onTarget =
          tap
          && (inBox(p, H.A.ticket, 16) || near(p, H.A.coin.x, H.A.coin.y, H.A.halo + 10)
            || inBox(p, H.A.button, 10))
        driver.releaseA(onTarget)
        if onTarget { cue("reveal") }
      } else if hit == 2 && tap {
        if driver.closeB() { buzz(0) }
      } else if hit == 3 {
        driver.flickCoin(tap ? 0 : velocity.dx, now)
        cue("coin")
        buzz(0)
      } else if hit == 4 && tap {
        driver.ctaAt = now
        cue("sparkle")
        buzz(0)
      }
      driver.aimX = 0
      driver.aimY = 0
      driver.last = now
    default: break
    }
  }
  func accessibilityToggle(_ reduced: Bool) {
    if reduced {
      toggleReduced(fade < 0.5)
      return
    }
    driver.last = now
    if D.inRestA(driver.t) {
      driver.t = M.T.commit
      driver.mode = 1
      driver.rate = 1
      driver.stop = M.PEAK
    } else if !driver.closeB() {
      driver.mode = 1
      driver.rate = 1
      driver.stop = driver.t < M.PEAK ? M.PEAK : M.LOOP
    }
    running = true
  }
}
