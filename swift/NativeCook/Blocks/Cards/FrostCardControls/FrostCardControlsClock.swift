import AVFoundation
import SwiftUI
import UIKit

final class FrostCardControlsClock: ObservableObject {
  typealias M = FrostCardControlsMotion
  typealias T = FrostCardControlsTheme
  struct Sample {
    var frame: M.Frame
    var driver: M.Driver
    var iced: Double
  }
  @Published var fading = false
  var driver = M.Driver()
  private var date: Date?
  private var now = 0.0
  private var start = CGPoint.zero
  private var target = 0
  private var moved = false
  private var iced = 0.0
  private var iceFrom = 0.0
  private var iceAim = 0.0
  private var fadeStart = Date.distantPast
  private var fadeCompletion: DispatchWorkItem?
  private var players: [String: AVAudioPlayer] = [:]

  func prepareAudio(_ enabled: Bool) {
    players.removeAll()
    guard enabled else { return }
    try? AVAudioSession.sharedInstance().setCategory(
      .ambient, mode: .default, options: [.mixWithOthers])
    for cue in ["freeze", "melt", "shred", "reissue"] {
      if let url = Bundle.main.url(
        forResource: "FrostCardControls-fcc-" + cue, withExtension: "wav"),
        let player = try? AVAudioPlayer(contentsOf: url)
      {
        player.volume = cue == "reissue" ? 0.7 : 0.85
        player.prepareToPlay()
        players[cue] = player
      }
    }
  }
  func resetDate() { date = nil }
  func reset() {
    driver = M.Driver()
    now = 0
    date = nil
  }
  func sample(_ at: Date, auto: Bool, reduced: Bool, pinned: Double?) -> Sample {
    if let pinned {
      var d = M.Driver()
      d.t = pinned
      d.amb = pinned.truncatingRemainder(dividingBy: M.LOOP)
      return Sample(frame: M.frameAt(pinned), driver: d, iced: 0)
    }
    if reduced {
      let elapsed = at.timeIntervalSince(fadeStart)
      let p = M.clamp01(elapsed / 0.22)
      // withTiming's default Easing.inOut(Easing.quad), without system motion scaling.
      let eased = p < 0.5 ? 2 * p * p : 1 - pow(-2 * p + 2, 2) / 2
      iced = M.mix(iceFrom, iceAim, eased)
      return Sample(frame: M.frameAt(0), driver: M.Driver(), iced: iced)
    }
    let dt = min(max(0, date.map { at.timeIntervalSince($0) } ?? 0.016), 0.05)
    date = at
    now += dt
    M.stepDriver(&driver, dt, now, auto)
    return Sample(frame: M.liveFrame(driver, now), driver: driver, iced: 0)
  }
  func begin(_ point: CGPoint, scale: Double, reduced: Bool) {
    start = CGPoint(x: point.x / scale, y: point.y / scale)
    moved = false
    target = 0
    let cd = T.UI.card
    if start.x > cd.minX && start.x < cd.maxX && start.y > cd.minY && start.y < cd.maxY {
      target = 1
    } else if start.y > T.UI.tileY && start.y < T.UI.tileY + T.UI.tileH {
      for i in 0..<3 where start.x > T.UI.tileXs[i] && start.x < T.UI.tileXs[i] + T.UI.tileW {
        target = i + 2
      }
    }
    if reduced { return }
    M.takeOver(&driver, now)
    if target >= 2 { driver.pressAim[target - 2] = 1 }
  }
  func change(_ translation: CGSize, reduced: Bool) {
    if reduced { return }
    driver.last = now
    if hypot(translation.width, translation.height) > 8 { moved = true }
    if target == 1 && moved {
      driver.aimX = min(1, max(-1, translation.width / 120))
      driver.aimY = min(1, max(-1, translation.height / 120))
    }
  }
  func end(reduced: Bool) {
    let tg = target
    let tap = !moved
    target = 0
    driver.pressAim = [0, 0, 0]
    driver.aimX = 0
    driver.aimY = 0
    driver.last = now
    if reduced {
      if tap && (tg == 1 || tg == 2) { crossfade() }
      return
    }
    guard tap, now >= driver.busy else { return }
    if tg == 1 || tg == 2 {
      if driver.frozen {
        M.meltNow(&driver, now)
        cue("melt", 0)
      } else {
        let cx = tg == 1 ? start.x - T.UI.card.minX : 46
        let cy = tg == 1 ? start.y - T.UI.card.minY : 186
        M.freezeAt(&driver, now, cx, cy)
        cue("freeze", 1)
      }
    } else if tg == 3 {
      M.reissueNow(&driver, now)
      cue("reissue", 0)
    } else if tg == 4 {
      M.shredNow(&driver, now)
      cue("shred", 3)
    }
  }
  func crossfade() {
    _ = sample(Date(), auto: false, reduced: true, pinned: nil)
    iceFrom = iced
    iceAim = iced > 0.5 ? 0 : 1
    fadeStart = Date()
    fading = true
    fadeCompletion?.cancel()
    let completion = DispatchWorkItem { [weak self] in
      guard let self else { return }
      self.iced = self.iceAim
      self.iceFrom = self.iceAim
      self.fadeStart = .distantPast
      self.fading = false
    }
    fadeCompletion = completion
    // This is the known end of the animation, not a readiness retry.
    DispatchQueue.main.asyncAfter(deadline: .now() + 0.22, execute: completion)
  }
  func accessibilityFreeze(reduced: Bool) {
    if reduced {
      crossfade()
      return
    }
    M.takeOver(&driver, now)
    if driver.frozen { M.meltNow(&driver, now) } else { M.freezeAt(&driver, now, 46, 186) }
  }
  private func cue(_ name: String, _ kind: Int) {
    players[name]?.currentTime = 0
    players[name]?.play()
    if kind == 3 {
      UINotificationFeedbackGenerator().notificationOccurred(.warning)
    } else {
      UIImpactFeedbackGenerator(style: kind == 1 ? .medium : .light).impactOccurred()
    }
  }
}
