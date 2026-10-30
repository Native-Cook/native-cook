import SwiftUI
import UIKit

// TimelineView samples this non-published state. Only touches notify SwiftUI.
final class ListeningBuddyPaywallClock: ObservableObject {
  typealias M = ListeningBuddyPaywallMotion
  typealias Theme = ListeningBuddyPaywallTheme
  @Published private(set) var revision = 0
  private var driver = M.Driver()
  private var date: Date?
  private var now = 0.0
  private let audio = ListeningBuddyPaywallAudio()
  private var reduced = false

  func resetDate() { date = nil }
  func reset() {
    driver = M.Driver()
    now = 0
    date = nil
    revision += 1
  }
  func stop() {
    date = nil
    audio.stop()
  }
  func sample(_ at: Date, auto: Bool, reduced: Bool, pinned: Double?) -> M.Frame {
    self.reduced = reduced
    let dt = min(max(0, date.map { at.timeIntervalSince($0) } ?? 0), 0.05)
    date = at
    now += dt
    if let pinned {
      driver.mode = 0
      driver.t = pinned
      driver.amb = pinned
    } else {
      M.stepDriver(&driver, dt, now, auto && !reduced)
    }
    var frame = M.frameOf(driver, now)
    if reduced {
      frame.amb = 0
      frame.selAt = 10
      frame.go = -1
      frame.poke = -1
    }
    return frame
  }
  private func pin(_ point: CGPoint, _ width: Double) -> CGPoint {
    let scale = width / Theme.REF_W
    return CGPoint(x: point.x / scale / Theme.S, y: (point.y / scale - Theme.DY) / Theme.S)
  }
  func begin(_ point: CGPoint, width: Double) {
    M.takeOver(&driver, now)
    let p = pin(point, width)
    driver.released = 0
    if p.y > Theme.UI.bar.y {
      driver.pressing = 1
      driver.pressAt = now
    } else if hypot(p.x - Theme.UI.close.x, p.y - Theme.UI.close.y) < Theme.UI.close.r + 14 {
      driver.pressing = 2
      driver.pressAt = now
    }
    revision += 1
  }
  func end(_ point: CGPoint, width: Double) {
    driver.last = now
    let p = pin(point, width)
    let was = driver.pressing
    if was > 0 {
      driver.released = was
      driver.releaseAt = now
      driver.pressing = 0
    }
    defer { revision += 1 }
    if was == 1 {
      if p.y < Theme.UI.bar.y - 20 { return }
      driver.goAt = now
      if !reduced { audio.play("notes") }
      UINotificationFeedbackGenerator().notificationOccurred(.success)
      return
    }
    if was == 2 {
      UIImpactFeedbackGenerator(style: .light).impactOccurred()
      return
    }
    let cards = Theme.UI.cards
    if p.y > cards.y - 14 && p.y < cards.y + cards.h + 14 {
      for index in 0..<3 {
        if p.x > cards.x[index] - 12 && p.x < cards.x[index] + cards.w + 12
          && M.select(&driver, index, now)
        {
          if !reduced { audio.play(index == 0 ? "pick1" : index == 2 ? "pick2" : "pick3") }
          UISelectionFeedbackGenerator().selectionChanged()
        }
      }
      return
    }
    if p.x < 300 && p.y > 380 && p.y < Theme.UI.white {
      driver.pokeAt = now
      if !reduced { audio.play("boop") }
      UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }
  }
  func nextPlan() {
    M.takeOver(&driver, now)
    M.select(&driver, (driver.plan + 1) % 3, now)
    revision += 1
  }
}
