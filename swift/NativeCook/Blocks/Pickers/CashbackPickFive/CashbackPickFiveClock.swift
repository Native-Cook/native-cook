import SwiftUI

// TimelineView reads this non-published clock; only user actions invalidate the view.
final class CashbackPickFiveClock: ObservableObject {
  typealias M = CashbackPickFiveMotion
  typealias T = CashbackPickFiveTheme
  @Published private var revision = 0
  private var driver = M.Driver()
  private var date: Date?
  private var now = 0.0
  private var startScroll = 0.0
  private let audio = CashbackPickFiveAudio()

  func resetDate() { date = nil }
  func reset() {
    driver = M.Driver()
    now = 0
    date = nil
    revision += 1
  }
  func sample(_ at: Date, auto: Bool, pinned: Double?) -> M.Frame {
    if let pinned { return M.frameAt(pinned) }
    let dt = min(max(0, date.map { at.timeIntervalSince($0) } ?? 0), 0.05)
    date = at
    now += dt
    let before = driver.t
    let wasAuto = driver.mode == 0
    M.stepDriver(&driver, dt, now, auto)
    // The site's recording plays the sounds a touch would make (DemoSound).
    if DemoSound.inAutoPlay && auto && wasAuto && driver.mode == 0 { playLoopCues(before, driver.t) }
    return M.frameOf(&driver, now)
  }
  // Each cue fires once per loop when the timeline crosses the moment a touch would have made it.
  private func playLoopCues(_ before: Double, _ after: Double) {
    func crossed(_ at: Double) -> Bool {
      after >= before ? (before < at && at <= after) : (at > before || at <= after)
    }
    var picks = 0
    for event in M.loopEvents {
      if event.k == 0 {
        if picks >= T.MAX_PICKS {
          if crossed(event.t) { audio.play("nope", enabled: true) }
        } else {
          picks += 1
          if crossed(event.t) { audio.play("pop", enabled: true) }
          if crossed(event.t + M.FLY) { audio.play("drop", enabled: true) }
        }
      } else if event.k == 1 {
        picks -= 1
        if crossed(event.t) { audio.play("back", enabled: true) }
      }
    }
    if crossed(M.SPARKLE_AT) { audio.play("sparkle", enabled: true) }
  }
  func begin() {
    M.takeOver(&driver, now)
    driver.v = 0
    if !driver.scroll.isFinite { driver.scroll = 0 }
    startScroll = driver.scroll
    revision += 1
  }
  func change(_ translationY: Double, scale: Double) {
    driver.last = now
    let dy = translationY / scale
    if driver.dragging == 0 && abs(dy) > 10 { driver.dragging = 1 }
    let next = startScroll - dy
    if driver.dragging == 1 && next.isFinite { driver.scroll = max(0, min(M.MAX_SCROLL, next)) }
  }
  // Finalization (also cancellation) follows the RN pan handler's row/banner hit regions.
  func end(_ point: CGPoint, velocityY: Double, scale: Double, reduced: Bool) {
    driver.last = now
    if driver.dragging == 1 {
      driver.dragging = 0
      let velocity = -velocityY / scale
      driver.v = velocity.isFinite ? velocity : 0
      return
    }
    let x = point.x / scale
    let y = point.y / scale
    let frame = M.frameOf(&driver, now)
    let floor = CashbackPickFiveArt.panelK(frame) > 0.5 ? T.UI.panelY : T.UI.disabled.y - 20
    if y >= floor || y < 222 { return }
    let py = y + driver.scroll
    let banner = T.UI.banner
    if py > banner.y && py < banner.y + banner.h && x > banner.x && x < banner.x + banner.w {
      driver.sparkleAt = now
      audio.play("sparkle", enabled: !reduced)
      audio.buzz(2)
      revision += 1
      return
    }
    let list = T.UI.list
    for index in T.cats.indices {
      let cy = list.top + Double(index) * list.pitch
      if abs(py - cy) < list.pitch / 2 && x > list.x && x < list.x + list.w {
        let state = M.replay(driver.events, now)
        let picked = state.slotOf[index] >= 0
        let at = reduced ? now - M.FLY - 0.4 : now
        M.push(&driver, .init(t: at, k: picked ? 1 : 0, row: index))
        if picked {
          audio.play("back", enabled: !reduced)
          audio.buzz(2)
        } else if state.picks.count >= T.MAX_PICKS {
          audio.play("nope", enabled: !reduced)
          audio.buzz(3)
        } else {
          audio.pick(enabled: !reduced)
        }
        revision += 1
        return
      }
    }
  }
  func pickNext() {
    M.takeOver(&driver, now)
    let state = M.replay(driver.events, now)
    if let row = T.cats.indices.first(where: { state.slotOf[$0] < 0 }) {
      M.push(&driver, .init(t: now, k: 0, row: row))
    }
    revision += 1
  }
}
