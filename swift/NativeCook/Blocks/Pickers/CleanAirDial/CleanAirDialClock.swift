import AVFoundation
import SwiftUI
import UIKit

// TimelineView samples ordinary state; gestures never force a whole-screen publication each frame.
final class CleanAirDialClock: ObservableObject {
  typealias M = CleanAirDialMotion
  typealias T = CleanAirDialTheme
  private var driver = M.Driver()
  private var date: Date?
  private var now = 0.0
  private var quality = 1
  private var zone = 0
  private var start = CGPoint.zero
  private var lastTick = 0.0
  private var players: [String: AVAudioPlayer] = [:]
  private let selection = UISelectionFeedbackGenerator()
  private let light = UIImpactFeedbackGenerator(style: .light)
  private let medium = UIImpactFeedbackGenerator(style: .medium)
  private let success = UINotificationFeedbackGenerator()
  func resetDate() { date = nil }
  func reset() {
    driver = M.Driver()
    date = nil
    now = 0
    quality = 1
    zone = 0
  }
  func stopAudio() {
    players.values.forEach { $0.stop() }
    players.removeAll()
  }
  func prepareAudio(_ enabled: Bool) {
    guard enabled else {
      stopAudio()
      return
    }
    try? AVAudioSession.sharedInstance().setCategory(.ambient, options: .mixWithOthers)
    for name in ["tick", "whoosh", "chime", "tap"] where players[name] == nil {
      guard
        let url = Bundle.main.url(forResource: "CleanAirDial-cad-\(name)", withExtension: "wav"),
        let player = try? AVAudioPlayer(contentsOf: url)
      else { continue }
      player.volume = name == "tick" ? 0.5 : 0.85
      player.prepareToPlay()
      players[name] = player
    }
  }
  private func cue(_ name: String, _ reduced: Bool) {
    guard !reduced else { return }
    if players[name] == nil { prepareAudio(true) }
    players[name]?.currentTime = 0
    players[name]?.play()
  }
  private func buzz(_ kind: Int) {
    if kind == 2 {
      selection.selectionChanged()
    } else if kind == 3 {
      success.notificationOccurred(.success)
    } else if kind == 1 {
      medium.impactOccurred()
    } else {
      light.impactOccurred()
    }
  }
  func sample(_ at: Date, auto: Bool, reduced: Bool, pinned: Double?) -> M.Frame {
    let dt = min(max(0, date.map { at.timeIntervalSince($0) } ?? 0.016), 0.05)
    date = at
    now += dt
    if let pinned {
      driver.mode = 0
      driver.t = pinned
      driver.amb = pinned
    } else {
      M.stepDriver(&driver, dt, now, auto && !reduced)
      if reduced && driver.mode == 2 && driver.dragging == 0 {
        driver.g = driver.power ? T.LEVELS[driver.level].ticks : 0
        driver.gv = 0
        driver.f = driver.power ? T.LEVELS[driver.level].target : -0.5
      }
    }
    var frame = M.frameOf(driver, now)
    if reduced {
      frame.amb = 0
      frame.phi = 0
    }
    let pm = M.pmOf(frame.f)
    let nextQuality = pm < 6.5 ? 0 : pm < 15.5 ? 1 : 2
    if nextQuality != quality {
      if nextQuality == 0 && driver.mode == 2 {
        cue("chime", reduced)
        buzz(3)
      }
      quality = nextQuality
    }
    return frame
  }
  private func toPin(_ point: CGPoint, _ width: Double) -> CGPoint {
    let scale = width / T.REF_W
    return CGPoint(x: point.x / scale / T.S + T.X0, y: (point.y / scale - T.Y0) / T.S)
  }
  private func ringTick(_ point: CGPoint) -> Double {
    var angle = atan2(point.x - T.UI.dial.x, -(point.y - T.UI.dial.y)) * 180 / .pi
    if angle < 0 { angle += 360 }
    return max(1, min(36, angle / 6 + 0.5))
  }
  private func buttonAt(_ point: CGPoint) -> Int {
    let chips = T.UI.chips
    for i in 0..<3 {
      if abs(point.x - chips.x[i]) < chips.w / 2 + 6 && abs(point.y - chips.y) < chips.h / 2 + 6 {
        return i
      }
      if abs(point.x - T.UI.bottom.x[i]) < chips.w / 2 + 6
        && abs(point.y - T.UI.bottom.y) < chips.h / 2 + 6
      {
        return 3 + i
      }
    }
    return -1
  }
  func stepSpeed() {
    M.takeOver(&driver, now)
    driver.level = (driver.level + 1) % T.LEVELS.count
  }
  func touch(_ location: CGPoint, phase: UIGestureRecognizer.State, width: Double, reduced: Bool) {
    let point = toPin(location, width)
    switch phase {
    case .began:
      M.takeOver(&driver, now)
      start = point
      let dial = T.UI.dial
      let distance = hypot(point.x - dial.x, point.y - dial.y)
      if distance > dial.disk - 30 && distance < dial.r1 + 60 && driver.power {
        zone = 1
        driver.dragging = 1
        driver.dragG = ringTick(point)
        lastTick = driver.dragG.rounded()
        buzz(2)
        cue("tick", reduced)
      } else {
        zone = 0
        let button = buttonAt(point)
        if button >= 0 {
          driver.press = button
          driver.pressLast = button
          driver.pressAt = now
        }
      }
    case .changed:
      guard zone == 1 else { return }
      driver.last = now
      driver.dragG = ringTick(point)
      let tick = driver.dragG.rounded()
      if tick != lastTick {
        lastTick = tick
        buzz(2)
        cue("tick", reduced)
      }
    case .ended, .cancelled, .failed:
      driver.last = now
      if zone == 1 {
        let before = driver.level
        driver.level = M.levelAt(driver.dragG)
        driver.g = driver.dragG
        driver.dragging = 0
        driver.auto = false
        if driver.level > before { cue("whoosh", reduced) }
        buzz(0)
        zone = 0
        return
      }
      let button = driver.press
      if button >= 0 {
        driver.press = -1
        driver.releaseAt = now
      }
      guard button >= 0, hypot(point.x - start.x, point.y - start.y) <= 24,
        buttonAt(point) == button
      else { return }
      cue("tap", reduced)
      switch button {
      case 0:
        guard driver.power else { return }
        let arrows = point.x > T.UI.chips.x[0] + 14
        let up = !arrows || point.y < T.UI.chips.y
        let next =
          up
          ? (arrows
            ? min(T.LEVELS.count - 1, driver.level + 1) : (driver.level + 1) % T.LEVELS.count)
          : max(0, driver.level - 1)
        if next > driver.level { cue("whoosh", reduced) }
        driver.level = next
        driver.auto = false
        driver.autoAt = now - 1
        buzz(2)
      case 1:
        driver.timer.toggle()
        driver.timerAt = now
        buzz(0)
      case 2:
        driver.scenes.toggle()
        driver.scenesAt = now
        buzz(0)
      case 3:
        driver.auto.toggle()
        driver.autoAt = now
        buzz(0)
      case 4:
        driver.power.toggle()
        driver.powerAt = now
        buzz(1)
        if driver.power { cue("whoosh", reduced) }
      default: buzz(0)
      }
    default: break
    }
  }
}
