import AVFoundation
import SwiftUI
import UIKit

// Single main-thread owner. TimelineView samples the driver without publishing per-frame state.
final class BookFanSigninClock: ObservableObject {
  typealias M = BookFanSigninMotion
  @Published var fading = false
  var driver = M.Driver()
  private var date: Date?
  private var now = 0.0
  private var target = 0
  private var moved = false
  private var lastTranslation = CGPoint.zero
  private var fade = 0.0, fadeFrom = 0.0, fadeTo = 0.0
  private var fadeDate = Date.distantPast
  private var players: [String: AVAudioPlayer] = [:]
  private let light = UIImpactFeedbackGenerator(style: .light)
  private let medium = UIImpactFeedbackGenerator(style: .medium)
  struct Sample {
    var driver: M.Driver
    var fade: Double
  }
  init() {
    for cue in ["flight", "settle", "tap"] {
      if let url = Bundle.main.url(forResource: "BookFanSignin-bfs-\(cue)", withExtension: "wav"),
        let player = try? AVAudioPlayer(contentsOf: url)
      {
        player.volume = cue == "tap" ? 0.6 : 0.85
        player.prepareToPlay()
        players[cue] = player
      }
    }
  }
  private func cue(_ name: String) {
    try? AVAudioSession.sharedInstance().setCategory(
      .ambient, mode: .default, options: .mixWithOthers)
    players[name]?.currentTime = 0
    players[name]?.play()
  }
  func resetDate() { date = nil }
  func reset() {
    driver = M.Driver()
    date = nil
    now = 0
    fade = 0
    fadeTo = 0
    fading = false
  }
  func sample(_ at: Date, auto: Bool, reduced: Bool, pinned: Double?) -> Sample {
    if let pinned {
      var d = M.Driver()
      d.t = pinned
      d.amb = pinned
      return Sample(driver: d, fade: fade)
    }
    if reduced {
      if fading {
        let p = M.clamp01(at.timeIntervalSince(fadeDate) / 0.22)
        // Reanimated withTiming's default Easing.inOut(Easing.quad).
        let eased = p < 0.5 ? 2 * p * p : 1 - pow(-2 * p + 2, 2) / 2
        fade = M.mix(fadeFrom, fadeTo, eased)
        if p >= 1 { DispatchQueue.main.async { [weak self] in self?.fading = false } }
      }
      return Sample(driver: driver, fade: fade)
    }
    // The loop runs on the wall clock; only the springs take the clamped step. On a slow
    // machine (Xcode Cloud's simulators) frames longer than 50 ms would otherwise stretch
    // the 9 s loop, and the recording would not repeat.
    let wall = max(0, date.map { at.timeIntervalSince($0) } ?? 0.016)
    let dt = min(wall, 0.05)
    date = at
    now += wall
    let before = driver.t
    driver.step(dt, wall, now, auto)
    if driver.mode == 1 && driver.rate > 0 && before < M.T.sheet + 0.5
      && driver.t >= M.T.sheet + 0.5 && driver.t < M.T.close
    {
      light.impactOccurred()
    }
    return Sample(driver: driver, fade: fade)
  }
  private func toggleReduced(_ toSheet: Bool) {
    fadeFrom = fade
    fadeTo = toSheet ? 1 : 0
    fadeDate = Date()
    fading = true
  }
  func touch(
    _ phase: Int, _ point: CGPoint, _ translation: CGPoint, _ velocity: CGPoint, size: CGSize,
    reduced: Bool
  ) {
    let scale = size.width / 402
    let dy = size.height - 874 * scale
    let x = point.x / scale
    let y = point.y / scale
    let ys = (point.y - dy) / scale
    if phase == 0 {
      moved = false
      lastTranslation = .zero
      let home = reduced ? fade < 0.5 : M.inHome(driver.t)
      let sheet =
        reduced ? fade >= 0.5 : M.inSheet(driver.t) || (driver.t >= M.PEAK && driver.t < M.T.close)
      target = 0
      if home {
        if x > 282 && x < 385 && y > 62 && y < 118 { target = 2 } else if y < 300 { target = 1 }
      } else if sheet {
        if x > 36 && x < 366 && ys > 755 && ys < 812 {
          target = 4
        } else if ys > 418 {
          target = 3
        } else {
          target = 5
        }
      }
      if reduced { return }
      driver.touched(now)
      if target == 2 { driver.pressAim = 1 }
      if target == 4 { driver.ctaAim = 1 }
      if target == 1 || target == 3 { driver.dragging = 1 }
    } else if phase == 1 {
      if reduced { return }
      driver.last = now
      if hypot(translation.x, translation.y) > 8 { moved = true }
      if target == 1 {
        let along =
          (-sin(M.rad) * (translation.x - lastTranslation.x) + cos(M.rad)
            * (translation.y - lastTranslation.y)) / scale
        lastTranslation = translation
        driver.scroll += along
      } else if target == 3 {
        driver.drag = translation.y / scale
      }
    } else {
      let tg = target
      let tap = !moved
      target = 0
      driver.pressAim = 0
      driver.ctaAim = 0
      driver.dragging = 0
      driver.last = now
      if reduced {
        if tap && tg == 2 {
          toggleReduced(true)
        } else if tap && (tg == 5 || tg == 3) {
          toggleReduced(false)
        }
        return
      }
      if tg == 1 && !tap {
        driver.scrollV = (-sin(M.rad) * velocity.x + cos(M.rad) * velocity.y) / scale
      } else if tg == 2 && tap {
        driver.openSheet()
        cue("flight")
        light.impactOccurred()
      } else if tg == 3 {
        let v = velocity.y / scale
        if driver.drag > 110 || v > 900 {
          if driver.closeSheet() {
            cue("flight")
            light.impactOccurred()
          }
        }
        driver.dragV = v
      } else if tg == 5 && tap {
        if driver.closeSheet() { cue("flight") }
      } else if tg == 4 && tap {
        cue("tap")
        medium.impactOccurred()
      }
    }
  }
  func accessibilityToggle(reduced: Bool) {
    if reduced {
      toggleReduced(fade < 0.5)
      return
    }
    driver.touched(now)
    if M.inHome(driver.t) {
      driver.openSheet()
    } else if !driver.closeSheet() {
      driver.mode = 1
      driver.rate = 1
      driver.stop = driver.t < M.PEAK ? M.PEAK : M.LOOP
    }
  }
}
