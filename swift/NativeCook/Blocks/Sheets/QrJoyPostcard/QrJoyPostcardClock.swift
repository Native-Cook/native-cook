import SwiftUI
import UIKit

// Only touch/fade transitions publish; drawing samples the clock without view-state writes.
final class QrJoyPostcardClock: ObservableObject {
  typealias M = QrJoyPostcardMotion
  typealias Theme = QrJoyPostcardTheme
  @Published var running = false
  private var driver = M.makeDriver()
  private var date: Date?
  private var now = 0.0
  private var target = 0
  private var start = CGPoint.zero
  private var dragging = false
  private var fade = 1.0
  private var fadeFrom = 1.0
  private var fadeTo = 1.0
  private var fadeAt: Date?
  private let audio = QrJoyPostcardAudio()

  func resetDate() { date = nil }
  func reset(reduced: Bool) {
    driver = M.makeDriver(reduced ? M.PEAK : 0)
    date = nil
    now = 0
    fade = 1
    fadeAt = nil
    running = false
  }
  func sample(_ at: Date, auto: Bool, reduced: Bool, pinned: Double?) -> (
    frame: M.Frame, live: QrJoyPostcardArt.Live, fade: Double
  ) {
    if let pinned { return (M.frameAt(pinned), .init(), 1) }
    if reduced {
      if let fadeAt {
        let p = M.clamp01(at.timeIntervalSince(fadeAt) / 0.22)
        let eased = p < 0.5 ? 2 * p * p : 1 - pow(-2 * p + 2, 2) / 2
        fade = M.mix(fadeFrom, fadeTo, eased)
        if p >= 1 {
          self.fadeAt = nil
          DispatchQueue.main.async { [weak self] in self?.running = false }
        }
      }
      return (M.frameAt(M.PEAK), .init(), fade)
    }
    // The loop runs on the wall clock; only the springs take the clamped step. On a slow
    // machine (Xcode Cloud's simulators) frames longer than 50 ms would otherwise stretch
    // the 9 s loop, and the recording would not repeat.
    let wall = max(0, date.map { at.timeIntervalSince($0) } ?? 0.016)
    let dt = min(wall, 0.05)
    date = at
    now += wall
    let before = driver.t
    let time = M.stepDriver(&driver, dt, wall, now, auto)
    let bear = M.bearExtra(&driver, now)
    let shake = M.jiggle(&driver, now)
    // The site's recording plays the sounds a touch would make (DemoSound).
    let selfPlaying = DemoSound.inAutoPlay && auto && driver.mode == 0
    if selfPlaying && before < M.T.commit && time >= M.T.commit { audio.play("flip") }
    if (driver.mode == 1 && driver.rate > 0) || selfPlaying {
      if before < M.T.heartAt && time >= M.T.heartAt {
        audio.play("sparkle")
        buzz(2)
      }
      if before < M.T.sticker[1] && time >= M.T.sticker[1] {
        audio.play("sticker")
        buzz(0)
      }
    }
    var live = QrJoyPostcardArt.Live()
    live.amb = driver.amb
    live.tiltX = driver.tiltX
    live.tiltY = driver.tiltY
    live.lean = driver.lean
    live.cta = driver.ctaP
    live.hop = bear.hop
    live.squash = bear.squash
    live.wave = bear.wave
    live.jiggle = shake
    return (M.frameAt(time), live, 1)
  }
  private func buzz(_ kind: Int) {
    if kind == 2 {
      UISelectionFeedbackGenerator().selectionChanged()
    } else {
      UIImpactFeedbackGenerator(style: kind == 1 ? .medium : .light).impactOccurred()
    }
  }
  private func toggleReduced(_ toPostcard: Bool) {
    fadeFrom = fade
    fadeTo = toPostcard ? 1 : 0
    fadeAt = Date()
    running = true
  }
  private func reference(_ point: CGPoint, _ size: CGSize) -> CGPoint {
    let scale = size.width / Theme.REF_W
    let dy = size.height - Theme.REF_H * scale
    return CGPoint(x: point.x / scale, y: (point.y - dy) / scale)
  }
  private func cardPoint(_ point: CGPoint) -> CGPoint {
    let lift = M.frameAt(driver.t).lift
    let angle = M.mix(Theme.CARD.angle, M.TURN_LIFTED, M.clamp01(lift))
    let dx = point.x - Theme.CARD.cx
    let dy = point.y - (Theme.CARD.cy - M.LIFT * lift)
    return CGPoint(
      x: dx * cos(-angle) - dy * sin(-angle) + Theme.CARD.w / 2,
      y: dx * sin(-angle) + dy * cos(-angle) + Theme.CARD.h / 2)
  }
  private func onButton(_ point: CGPoint, _ pad: Double) -> Bool {
    point.x >= Theme.SHEET.cta.x - pad && point.x <= Theme.SHEET.cta.x + Theme.SHEET.cta.w + pad
      && point.y >= Theme.SHEET.cta.y - pad
      && point.y <= Theme.SHEET.cta.y + Theme.SHEET.cta.h + pad
  }
  func touch(_ phase: QrJoyPostcardTouchView.Phase, point: CGPoint, size: CGSize, reduced: Bool) {
    let p = reference(point, size)
    switch phase {
    case .began:
      start = p
      dragging = false
      let showingA = reduced ? fade < 0.5 : M.inRestA(driver.t)
      let showingB =
        reduced
        ? fade >= 0.5 : M.inPostcard(driver.t) || (driver.t >= M.PEAK && driver.t < M.T.back)
      let card = cardPoint(p)
      let onBear =
        card.x > Theme.BEAR.x - 48 && card.x < Theme.BEAR.x + 48
        && card.y > Theme.BEAR.y - Theme.BEAR.h + 8 && card.y < Theme.BEAR.y - 4
      let onCard =
        card.x > -8 && card.x < Theme.CARD.w + 8 && card.y > -8 && p.y < Theme.SHEET.heroBottom
      if hypot(p.x - Theme.SHEET.close.x, p.y - Theme.SHEET.close.y) < 24 && showingB {
        target = 2
      } else if onButton(p, 6) {
        target = showingB ? 4 : showingA ? 3 : 0
      } else if onBear && !reduced {
        target = 5
      } else if onCard {
        target = 1
      } else {
        target = 0
      }
      if reduced { return }
      M.touched(&driver, now)
      if target == 3 {
        M.pressA(&driver)
        driver.ctaAim = 1
      }
      if target == 4 { driver.ctaAim = 1 }
    case .changed:
      if reduced { return }
      driver.last = now
      if target != 1 && target != 5 { return }
      // RN thresholds and tilt denominators use physical view points, before reference scaling.
      let dx = (p.x - start.x) * size.width / Theme.REF_W
      let dy = (p.y - start.y) * size.width / Theme.REF_W
      if !dragging && hypot(dx, dy) < 8 { return }
      dragging = true
      driver.aimX = min(1, max(-1, dx / 110))
      driver.aimY = min(1, max(-1, dy / 140))
    case .ended, .cancelled:
      let tap = hypot(p.x - start.x, p.y - start.y) < 12 && !dragging
      let previous = target
      target = 0
      driver.ctaAim = 0
      driver.aimX = 0
      driver.aimY = 0
      if reduced {
        if tap && (previous == 1 || previous == 3) {
          toggleReduced(true)
        } else if tap && previous == 2 {
          toggleReduced(false)
        }
        return
      }
      if previous == 3 {
        let on = tap && onButton(p, 12)
        M.releaseA(&driver, on)
        if on { audio.play("flip") }
      } else if previous == 1 && tap {
        if M.inRestA(driver.t) {
          M.pressA(&driver)
          driver.t = M.T.commit
          M.releaseA(&driver, true)
          audio.play("flip")
          buzz(0)
        } else if M.inPostcard(driver.t) {
          driver.jiggleAt = now
          buzz(0)
        }
      } else if previous == 2 && tap {
        if M.closeB(&driver) { buzz(0) }
      } else if previous == 4 && tap {
        driver.jiggleAt = now
        audio.play("sparkle")
        buzz(1)
      } else if previous == 5 && tap {
        driver.bearAt = now
        buzz(0)
      }
      driver.last = now
    }
  }
  func accessibilityToggle(reduced: Bool) {
    if reduced {
      toggleReduced(fade < 0.5)
      return
    }
    driver.last = now
    if M.inRestA(driver.t) {
      driver.t = M.T.commit
      M.releaseA(&driver, true)
    } else if !M.closeB(&driver) {
      driver.mode = 1
      driver.rate = 1
      driver.stop = driver.t < M.PEAK ? M.PEAK : M.LOOP
    }
  }
}
