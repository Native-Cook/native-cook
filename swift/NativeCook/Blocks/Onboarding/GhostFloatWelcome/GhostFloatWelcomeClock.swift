import AVFoundation
import CoreMotion
import SwiftUI
import UIKit

/// Main-thread owner of the touch driver, audio, and motion sensor. Sampling does not publish frames.
final class GhostFloatWelcomeClock: ObservableObject {
  typealias M = GhostFloatWelcomeMotion
  typealias Theme = GhostFloatWelcomeTheme
  @Published var fading = false
  @Published var revision = 0
  var driver = GhostFloatWelcomeDriver()
  private var live = GhostFloatWelcomeLive()
  private var date: Date?
  private var now = 0.0
  private var target = 0
  private var start = CGPoint.zero
  private var moved = false
  private var swished = false
  private var loopT = 0.0
  private var fade = 0.0
  private var fadeFrom = 0.0
  private var fadeAim = 0.0
  private var fadeAt = Date.distantPast
  private let motion = CMMotionManager()
  private var gyroInitialized = false
  private var basePitch = 0.0
  private var baseRoll = 0.0
  private var tiltX = 0.0
  private var tiltY = 0.0
  private var players: [String: AVAudioPlayer] = [:]
  private let light = UIImpactFeedbackGenerator(style: .light)
  private let medium = UIImpactFeedbackGenerator(style: .medium)
  private let selection = UISelectionFeedbackGenerator()
  private let notification = UINotificationFeedbackGenerator()

  func reset() {
    driver = GhostFloatWelcomeDriver()
    live = GhostFloatWelcomeLive()
    now = 0
    date = nil
    loopT = 0
    fade = 0
    fading = false
    revision += 1
  }
  func lifecycle(active: Bool, reduced: Bool) {
    date = nil
    if active && !reduced {
      if motion.isDeviceMotionAvailable && !motion.isDeviceMotionActive {
        motion.deviceMotionUpdateInterval = 1.0 / 60
        motion.startDeviceMotionUpdates()
      }
      if players.isEmpty {
        try? AVAudioSession.sharedInstance().setCategory(
          .ambient, mode: .default, options: .mixWithOthers)
        for cue in ["boo", "tick", "swish"] {
          if let url = Bundle.main.url(
            forResource: "GhostFloatWelcome-gfw-" + cue, withExtension: "wav"),
            let player = try? AVAudioPlayer(contentsOf: url)
          {
            player.volume = cue == "swish" ? 0.55 : 0.85
            player.prepareToPlay()
            players[cue] = player
          }
        }
      }
    } else {
      motion.stopDeviceMotionUpdates()
      for player in players.values { player.stop() }
      players.removeAll()
    }
  }
  deinit { motion.stopDeviceMotionUpdates() }
  private func cue(_ name: String) {
    players[name]?.currentTime = 0
    players[name]?.play()
  }
  func sample(_ at: Date, auto: Bool, reduced: Bool, pinned: Double?) -> (
    M.Frame, GhostFloatWelcomeLive
  ) {
    if let pinned, !reduced {
      // RN debug skips the driver and leaves the eyes/physics at their initial zero values.
      var still = GhostFloatWelcomeLive()
      still.amb = pinned
      return (M.frameAt(pinned), still)
    }
    if reduced {
      var still = GhostFloatWelcomeLive()
      if fading {
        let p = min(1, max(0, at.timeIntervalSince(fadeAt) / 0.2))
        // Reanimated withTiming's default ease is quadratic-in-out.
        let easing = p < 0.5 ? 2 * p * p : 1 - pow(-2 * p + 2, 2) / 2
        fade = M.mix(fadeFrom, fadeAim, easing)
        if p >= 1 {
          DispatchQueue.main.async { [weak self] in self?.fading = false }
        }
      }
      still.agree = fade
      return (M.frameAt(0), still)
    }
    let dt = min(max(0, date.map { at.timeIntervalSince($0) } ?? 0.016), 0.05)
    date = at
    now += dt
    driver.step(dt, now, auto)
    // The site's recording plays the sounds a touch would make (DemoSound).
    if DemoSound.inAutoPlay && auto && driver.mode == 0 {
      let t = driver.t
      func crossed(_ at: Double) -> Bool {
        t >= loopT ? (loopT < at && t >= at) : (at > loopT || at <= t)
      }
      if crossed(M.T.puff) { cue("boo") }
      if crossed(M.T.tick[0]) || crossed(M.T.untick[0]) { cue("tick") }
      loopT = t
    }
    live = driver.extra(now)
    let pitch = motion.deviceMotion?.attitude.pitch ?? 0
    let roll = motion.deviceMotion?.attitude.roll ?? 0
    if !gyroInitialized && (pitch != 0 || roll != 0) {
      gyroInitialized = true
      basePitch = pitch
      baseRoll = roll
    }
    let baseGain = min(1, dt * 0.6)
    basePitch += (pitch - basePitch) * baseGain
    baseRoll += (roll - baseRoll) * baseGain
    let smoothing = 1 - exp(-dt * 8)
    tiltX += (max(-1, min(1, (roll - baseRoll) / 0.35)) - tiltX) * smoothing
    tiltY += (max(-1, min(1, (pitch - basePitch) / 0.35)) - tiltY) * smoothing
    live.tiltX = tiltX
    live.tiltY = tiltY
    return (M.frameAt(driver.t), live)
  }
  private func inButton(_ point: CGPoint, _ pad: Double) -> Bool {
    let b = Theme.button
    return point.x >= b.x - pad && point.x <= b.x + b.w + pad
      && point.y >= b.y - pad && point.y <= b.y + b.h + pad
  }
  private func onCheck(_ point: CGPoint) -> Bool {
    abs(point.y - Theme.check.y) < 24 && point.x > Theme.check.x - 26
      && point.x < Theme.check.x + 250
  }
  func begin(_ point: CGPoint, reduced: Bool) {
    start = point
    moved = false
    swished = false
    target = 0
    if onCheck(point) {
      target = 3
    } else if inButton(point, 6) {
      target = 4
    } else if point.y < Theme.fieldBottom {
      target = 1
      var best = -1
      var bestDistance = 1e9
      for j in 0..<7 {
        let home = M.liveHome(j, driver.t, driver.amb)
        let distance = hypot(point.x - home.x - driver.ox[j], point.y - home.y - driver.oy[j])
        if distance < home.r + 8 && distance < bestDistance {
          best = j
          bestDistance = distance
        }
      }
      if best >= 0 {
        target = 2
        let home = M.liveHome(best, driver.t, driver.amb)
        driver.grab = best
        driver.gdx = point.x - home.x - driver.ox[best]
        driver.gdy = point.y - home.y - driver.oy[best]
      }
    }
    if reduced { return }
    driver.touched(now)
    driver.down = target == 1 || target == 2 ? 1 : 0
    driver.fx = point.x
    driver.fy = point.y
    driver.fvx = 0
    driver.fvy = 0
    if target == 4 { driver.btnAim = 1 }
  }
  func change(_ point: CGPoint, velocity: CGVector, reduced: Bool) {
    if reduced { return }
    driver.last = now
    driver.fx = point.x
    driver.fy = point.y
    driver.fvx = velocity.dx
    driver.fvy = velocity.dy
    if hypot(point.x - start.x, point.y - start.y) > 10 { moved = true }
    if target == 1 && !swished && hypot(velocity.dx, velocity.dy) > 700 {
      swished = true
      cue("swish")
    }
  }
  func end(_ point: CGPoint, velocity: CGVector, reduced: Bool) {
    let tg = target
    let tap = !moved
    target = 0
    driver.down = 0
    driver.btnAim = 0
    driver.last = now
    if reduced {
      if tap && tg == 3 { toggleFade() }
      driver.grab = -1
      return
    }
    if tg == 2 {
      let j = driver.grab
      driver.grab = -1
      if tap && j == 0 {
        driver.boo(now)
        cue("boo")
        medium.impactOccurred()
      } else if j >= 0 {
        driver.vx[j] = velocity.dx
        driver.vy[j] = velocity.dy
        if !tap { light.impactOccurred() }
      }
    } else if tg == 3 && tap {
      toggleAgree()
      cue("tick")
      selection.selectionChanged()
    } else if tg == 4 && tap && inButton(point, 12) {
      let agreed = (driver.agree >= 0 ? driver.agree : M.frameAt(driver.t).tick) > 0.5
      if agreed {
        driver.rollAt = now
        medium.impactOccurred()
      } else {
        driver.noAt = now
        notification.notificationOccurred(.warning)
      }
    }
  }
  private func toggleAgree() {
    let current = driver.agree >= 0 ? driver.agree : M.frameAt(driver.t).tick
    driver.agree = current
    driver.agreeAim = current > 0.5 ? 0 : 1
  }
  private func toggleFade() {
    fadeFrom = fade
    fadeAim = fade > 0.5 ? 0 : 1
    fadeAt = Date()
    fading = true
    revision += 1
  }
  func accessibilityToggle(reduced: Bool) {
    if reduced {
      toggleFade()
    } else {
      driver.touched(now)
      toggleAgree()
      revision += 1
    }
  }
}
