import SwiftUI
import UIKit

// As in RN, the timeline owns the clock and integrates the two springs with dt capped at 50 ms.
// Only touches notify SwiftUI; sampling never publishes per-frame state.
final class SunUvBuddyClock: ObservableObject {
  typealias M = SunUvBuddyMotion
  typealias UI = SunUvBuddyTheme.UI
  var driver = M.Driver()
  private var date: Date?
  private var now = 0.0
  private var chipAt = -10.0
  private var shadesOn = false
  private var zone = 0
  private var startX = 0.0
  private var lastHour = 3
  private var lastT = 0.0
  private let audio = SunUvBuddyAudio()
  private let selection = UISelectionFeedbackGenerator()
  private let light = UIImpactFeedbackGenerator(style: .light)
  private let medium = UIImpactFeedbackGenerator(style: .medium)

  func resetDate() { date = nil }
  func reset() {
    driver = M.Driver()
    date = nil
    now = 0
    lastHour = 3
  }
  func sample(_ at: Date, auto: Bool, reduced: Bool, pinned: Double?) -> M.Live {
    let dt = min(max(0, date.map { at.timeIntervalSince($0) } ?? 0.016), 0.05)
    date = at
    now += dt
    let base: Double
    if let pinned {
      driver.mode = 0
      driver.t = pinned
      driver.amb = pinned
      base = M.stepDriver(&driver, 0, now, false)
    } else {
      base = M.stepDriver(&driver, dt, now, auto)
    }
    let tau = base - driver.at
    if pinned != nil || reduced {
      driver.size = M.sizeFor(M.uvAt(driver.hour, driver.from, driver.to, reduced ? 10 : tau))
      driver.sizeV = 0
    }
    let strong = M.uvAt(driver.hour, driver.from, driver.to, tau) > 4.2
    if strong != shadesOn {
      shadesOn = strong
      // The site's recording plays the sounds a touch would make (DemoSound).
      if strong && (driver.mode == 2 || (DemoSound.inAutoPlay && auto)) && !reduced {
        audio.play("shades")
      }
    }
    if DemoSound.inAutoPlay && auto && !reduced && driver.mode == 0 {
      let step = Int(driver.hour.rounded())
      if step != lastHour {
        lastHour = step
        audio.play("tick")
      }
    }
    return M.Live(
      amb: reduced ? 0 : driver.amb, hour: driver.hour, hourV: reduced ? 0 : driver.hourV,
      from: driver.from, to: driver.to, tau: reduced ? 10 : tau, size: driver.size,
      squish: driver.squishAt < 0 || reduced ? -1 : now - driver.squishAt,
      dayPress: max(0, 1 - (now - chipAt) / 0.25))
  }
  private func hourAt(_ x: Double) -> Double {
    max(0, min(10, (x - UI.bars.x0 - UI.bars.w / 2) / (UI.bars.w + UI.bars.gap)))
  }
  func touch(_ point: CGPoint, phase: Int, reduced: Bool) {
    let x = Double(point.x)
    let y = Double(point.y)
    switch phase {
    case 0:
      M.takeOver(&driver, now)
      startX = x
      lastT = now
      if y > UI.sheet.bottom - 8 && y < UI.bars.labelY + 20 {
        zone = 1
        driver.dragging = 1
        driver.hourV = 0
        driver.hour = hourAt(x)
        lastHour = Int(driver.hour.rounded())
        if !reduced { audio.play("tick") }
        selection.selectionChanged()
      } else if abs(y - UI.chips.y) < 24 {
        zone = 2
      } else if hypot(x - UI.sun.x, y - UI.sun.y) < UI.sun.r * 1.15 {
        zone = 3
      } else {
        zone = 0
      }
    case 1:
      guard zone == 1 else { return }
      driver.last = now
      let dt = max(1 / 240.0, now - lastT)
      let next = hourAt(x)
      driver.hourV = driver.hourV * 0.6 + ((next - driver.hour) / dt) * 0.4
      driver.hour = next
      lastT = now
      let step = Int(next.rounded())
      if step != lastHour {
        lastHour = step
        if !reduced { audio.play("tick") }
        selection.selectionChanged()
      }
    default:
      driver.last = now
      if zone == 1 {
        driver.dragging = 0
        driver.hourAim = max(0, min(10, (driver.hour + driver.hourV * 0.08).rounded()))
        if reduced {
          driver.hour = driver.hourAim
          driver.hourV = 0
        }
        return
      }
      guard abs(x - startX) < 10 else { return }
      if zone == 2 {
        for i in 0..<5 {
          if x > UI.chips.x[i] - 4 && x < UI.chips.x[i] + UI.chips.w[i] + 4 {
            if M.pickDay(&driver, i, now) {
              chipAt = now
              light.impactOccurred()
            }
            break
          }
        }
      } else if zone == 3 && !reduced {
        driver.squishAt = now
        driver.sizeV -= 1.6
        audio.play("boing")
        medium.impactOccurred()
      }
    }
  }
  func nextDay() {
    M.takeOver(&driver, now)
    M.pickDay(&driver, (driver.to + 1) % SunUvBuddyTheme.forecast.count, now)
  }
}
