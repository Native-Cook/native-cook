import Foundation
import UIKit

// Only gesture events publish a change. Timeline sampling mutates this local driver without
// publishing frame-by-frame SwiftUI state, just like the guarded RN UI-thread callback.
final class GreatWaveMorningClock: ObservableObject {
  typealias M = GreatWaveMorningMotion
  typealias UI = GreatWaveMorningTheme.UI
  private var driver = M.Driver()
  private var date: Date?
  private var now = 0.0
  private var startRaw = 0.0
  private var surged = false
  private let audio = GreatWaveMorningAudio()
  private let light = UIImpactFeedbackGenerator(style: .light)
  private let medium = UIImpactFeedbackGenerator(style: .medium)
  private let selection = UISelectionFeedbackGenerator()
  private let tilt = GreatWaveMorningTilt()

  func prepareAudio(reduced: Bool) { audio.enabled = !reduced }
  func stopAudio() { audio.stop() }
  func prepareTilt(reduced: Bool) {
    if reduced { tilt.stop() } else { tilt.start() }
  }
  func stopTilt() { tilt.stop() }
  func resetDate() { date = nil }
  func reset() {
    driver = M.Driver()
    now = 0
    date = nil
    startRaw = 0
    surged = false
  }
  func sample(_ at: Date, auto: Bool, reduced: Bool, pinned: Double?) -> M.Frame {
    if let pinned {
      var frame = M.frameAt(pinned)
      if reduced {
        frame.amb = 0
        frame.foam = -1
      }
      return frame
    }
    let dt = min(max(0, date.map { at.timeIntervalSince($0) } ?? 0.016), 0.05)
    date = at
    now += dt
    let before = driver.t
    M.stepDriver(&driver, dt, now, auto && !reduced)
    // The site's recording plays the sounds a touch would make (DemoSound).
    if DemoSound.inAutoPlay && auto && driver.mode == 0 && driver.t > before {
      let from = M.loopScroll(before), to = M.loopScroll(driver.t)
      if driver.t < M.T.release && from >= -60 && to < -60 { audio.play("surge", reduced: reduced) }
      if before < M.T.release && driver.t >= M.T.release {
        audio.play("splash", reduced: reduced)
      }
    }
    var frame = M.frameOf(driver, now)
    if reduced {
      frame.amb = 0
      frame.foam = -1
    } else {
      let tilt = tilt.sample(dt: dt)
      frame.tiltX = tilt.x
      frame.tiltY = tilt.y
    }
    return frame
  }
  private func toPin(_ point: CGPoint, _ scale: Double) -> CGPoint {
    CGPoint(
      x: point.x / scale / GreatWaveMorningTheme.S,
      y: (point.y / scale - GreatWaveMorningTheme.DY) / GreatWaveMorningTheme.S)
  }
  func begin(_ point: CGPoint, scale: Double) {
    M.takeOver(&driver, now)
    driver.v = 0
    driver.raw = driver.s
    startRaw = driver.s
    surged = false
    let p = toPin(point, scale)
    let rows = UI.rows
    let contentY = p.y + driver.s
    driver.pressRow = -1
    if p.y < UI.tab.y && p.x > rows.x && p.x < rows.x + rows.w {
      for i in 0..<5 {
        let y = rows.y0 + Double(i) * rows.pitch
        if contentY > y && contentY < y + rows.h && p.y > (driver.s > 0 ? 170 : 520) {
          driver.pressRow = i
          driver.pressLast = i
          driver.pressAt = now
        }
      }
    }
    objectWillChange.send()
  }
  func update(_ translation: CGSize, scale: Double, reduced: Bool) {
    driver.last = now
    let dy = translation.height / scale / GreatWaveMorningTheme.S
    if driver.dragging == 0 && abs(dy) > 8 {
      driver.dragging = 1
      if driver.pressRow >= 0 {
        driver.pressRow = -1
        driver.releaseRowAt = now
      }
    }
    if driver.dragging == 0 { return }
    let raw = startRaw - dy
    driver.raw = raw
    let maxScroll = GreatWaveMorningTheme.MAX_SCROLL
    driver.s =
      raw < 0 ? -M.rubber(-raw) : raw > maxScroll ? maxScroll + M.rubber(raw - maxScroll) : raw
    if driver.s < -60 && !surged {
      surged = true
      audio.play("surge", reduced: reduced)
      light.impactOccurred()
    }
  }
  func end(_ point: CGPoint, velocity: CGSize, scale: Double, reduced: Bool) {
    driver.last = now
    if driver.dragging == 1 {
      driver.dragging = 0
      driver.v = max(-4000, min(4000, -velocity.height / scale / GreatWaveMorningTheme.S))
      if driver.s < -60 && !reduced {
        driver.releaseAt = now
        driver.foamPow = min(1.2, -driver.s / M.PULL)
        audio.play("splash", reduced: reduced)
        medium.impactOccurred()
      }
      return
    }
    let p = toPin(point, scale)
    if driver.pressRow >= 0 {
      let i = driver.pressRow
      driver.pressRow = -1
      driver.releaseRowAt = now
      driver.done[i].toggle()
      driver.doneAt[i] = reduced ? now - 1 : now
      audio.play("tap", reduced: reduced)
      if driver.done[i] { light.impactOccurred() } else { selection.selectionChanged() }
      return
    }
    if p.y > UI.tab.y {
      for i in 0..<5 where abs(p.x - UI.tab.x[i]) < 70 && driver.tab != i {
        driver.tab = i
        if reduced { driver.tabPos = Double(i) }
        audio.play("tap", reduced: reduced)
        selection.selectionChanged()
      }
    }
  }
  func markNext() {
    M.takeOver(&driver, now)
    for i in 0..<5 where !driver.done[i] {
      driver.done[i] = true
      driver.doneAt[i] = now
      objectWillChange.send()
      return
    }
  }
}
