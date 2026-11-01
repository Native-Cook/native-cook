import SwiftUI
import UIKit

final class LanternWishSendClock: ObservableObject {
  @Published var running = false
  private var date: Date?
  private var now = 0.0
  private var at = -100.0, flick = 0.0, lastTouch = -100.0, loopT = 0.0
  private var scripted = false, counted = false
  private let impact = UIImpactFeedbackGenerator(style: .light)
  private let success = UINotificationFeedbackGenerator()
  func resume() { date = nil }
  func sample(_ date: Date, auto: Bool, pinned: Double?) -> LanternWishSendMotion.Frame {
    if let pinned { return LanternWishSendMotion.at(pinned) }
    let wall = min(0.5, max(0, self.date.map { date.timeIntervalSince($0) } ?? 0.016))
    self.date = date
    now += wall
    flick *= exp(-3 * min(wall, 0.05))
    var f: LanternWishSendMotion.Frame
    if auto && now - at >= 6.2 && now - lastTouch > 3.5 {
      if !scripted { scripted = true; loopT = 0 }
      loopT = (loopT + wall).truncatingRemainder(dividingBy: LanternWishSendMotion.loop)
      f = LanternWishSendMotion.at(loopT)
      f.t = now
    } else {
      scripted = false
      let u = now - at
      f = LanternWishSendMotion.sendAt(u * (u > 1.6 ? 1 + flick * 0.6 : 1))
      f.sending = u >= 0 && u < 6
      f.t = now
      f.flick = flick
      f.back = f.sending ? 0 : LanternWishSendMotion.smoother((u - 6) / 1)
    }
    let reached = f.count == 1249
    if reached && !counted { success.notificationOccurred(.success) }
    counted = reached
    if running && now - at >= 7 && now - lastTouch > 3.5 {
      DispatchQueue.main.async { [weak self] in self?.running = false }
    }
    return f
  }
  func send() {
    impact.impactOccurred()
    guard now - at >= 6.2 else { return }
    at = now
    scripted = false
    lastTouch = now
    date = nil
    running = true
  }
  func flickUp(_ velocity: Double) {
    if velocity < -400 {
      flick = 1
      lastTouch = now
      date = nil
      running = true
    }
  }
}
