import SwiftUI
import UIKit

// TimelineView reads this clock without publishing per-frame changes to the whole screen.
final class SavingsFutureSimClock: ObservableObject {
  typealias M = SavingsFutureSimMotion
  @Published var running = false
  var driver = M.Driver()
  let audio = SavingsFutureSimAudio()
  private var date: Date?
  private var now = 0.0
  private var lastStep = 0.0
  func resetDate() { date = nil }
  func reset() {
    driver = M.Driver()
    now = 0
    date = nil
    running = false
  }
  func sample(_ date: Date, auto: Bool, reduced: Bool, pinned: Double?) -> M.Frame {
    let dt = min(max(0, self.date.map { date.timeIntervalSince($0) } ?? 0.016), 0.05)
    self.date = date
    now += dt
    if let pinned {
      driver.mode = 0
      driver.t = pinned
      driver.amb = pinned
    } else {
      driver.step(dt, now, auto)
    }
    var frame = driver.frameOf(now)
    if reduced {
      frame.amb = 0
      frame.morph = frame.open ? 1 : 0
    }
    return frame
  }
  func toggle() {
    driver.takeOver(now)
    driver.setOpen(!driver.open, now)
    running = true
  }
  func touch(_ phase: Int, _ p: CGPoint, reduced: Bool) {
    if phase == 0 {
      driver.takeOver(now)
      driver.pressing = 0
      if p.x > 80 && p.x < 656 && p.y > 1097 && p.y < 1204 {
        driver.pressing = 1
        driver.pressAt = now
      } else if driver.open && p.y > 552 && p.y < 832 {
        driver.pressing = 2
        driver.closeAt = -10
        driver.year = yearAt(p.x)
        lastStep = floor(driver.year)
      } else if !driver.open {
        let angle = Double.pi + Double.pi * SavingsFutureSimArt.rateFrac(driver.rate)
        if hypot(p.x - (368 + 260 * cos(angle)), p.y - (770 + 260 * sin(angle))) < 70 {
          driver.pressing = 3
          lastStep = driver.rate
        }
      }
      running = true
    } else if phase == 1 {
      driver.last = now
      if driver.pressing == 2 {
        driver.year = yearAt(p.x)
        let year = floor(driver.year)
        if year != lastStep {
          lastStep = year
          tick(reduced)
        }
      } else if driver.pressing == 3 {
        var angle = atan2(p.y - 770, p.x - 368)
        if angle > Double.pi / 2 { angle -= 2 * Double.pi }
        let u = M.clamp01((angle + Double.pi) / Double.pi)
        let rate = min(8, max(4, ((4 + (u - 0.1) / 0.125) * 4).rounded() / 4))
        if rate != lastStep {
          lastStep = rate
          driver.rate = rate
          tick(reduced)
        }
      }
    } else {
      driver.last = now
      let was = driver.pressing
      driver.pressing = 0
      if was == 1 {
        driver.releaseAt = now
        if p.x < 60 || p.x > 676 || p.y < 1075 || p.y > 1226 { return }
        driver.setOpen(!driver.open, reduced ? now - 2 : now)
        if driver.open { driver.year = 1 }
        if !reduced { audio.play(driver.open ? "unfurl" : "furl") }
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
      } else if was == 2 {
        driver.closeAt = now + 1.2
      }
    }
  }
  private func yearAt(_ x: Double) -> Double { min(30, max(1, ((x - 92) / 552) * 30)) }
  private func tick(_ reduced: Bool) {
    if !reduced { audio.play("tick") }
    UISelectionFeedbackGenerator().selectionChanged()
  }
}
