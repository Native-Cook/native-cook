import SwiftUI
import UIKit

/// TimelineView samples this clock without publishing per-frame state back into the view tree.
final class SetupChecklistAllSetClock: ObservableObject {
  typealias M = SetupChecklistAllSetMotion
  private var driver = M.Driver()
  private var now = 0.0
  private var previous: Date?
  private var start = CGPoint.zero
  private var cues: [DispatchWorkItem] = []
  private var audio: SetupChecklistAllSetAudio?
  @Published private var revision = 0

  func resetDate() { previous = nil }
  func reset() {
    driver = M.Driver()
    now = 0
    previous = nil
    cues.forEach { $0.cancel() }
    cues.removeAll()
    revision += 1
  }
  func stop() {
    previous = nil
    cues.forEach { $0.cancel() }
    cues.removeAll()
    audio?.stop()
  }
  func sample(_ date: Date, auto: Bool, reduced: Bool, pinned: Double?) -> M.Live {
    let dt = min(max(0, date.timeIntervalSince(previous ?? date.addingTimeInterval(-0.016))), 0.05)
    previous = date
    now += dt
    if let pinned {
      driver.mode = 0
      driver.t = pinned
      driver.amb = pinned
    } else {
      let before = driver.t
      driver.step(dt, now, auto)
      if DemoSound.inAutoPlay && auto && driver.mode == 0 { loopCues(before, driver.t) }
    }
    return driver.mode == 0
      ? M.Live(now: driver.t, amb: driver.amb, s: M.loopState(driver.t))
      : M.Live(now: reduced ? now + 10 : now, amb: driver.amb, s: driver.s)
  }
  // The site's recording plays the sounds a touch would make (DemoSound): a check per row
  // as it is ticked, then the "all set" chime and pop after the last tick, as `after` does.
  private func loopCues(_ before: Double, _ t: Double) {
    if audio == nil { audio = SetupChecklistAllSetAudio() }
    func crossed(_ at: Double) -> Bool { before < at && t >= at }
    for at in [0.7, 1.45, 2.15, 2.85, 3.55] where crossed(at) { audio?.play("check") }
    if crossed(3.55 + 0.4 + M.D.morph) { audio?.play("complete") }
    if crossed(3.55 + 0.4 + M.D.morph + 0.2) { audio?.play("pop") }
  }
  func begin(_ point: CGPoint) {
    driver.takeOver(now)
    start = point
    revision += 1
  }
  func end(_ point: CGPoint, reduced: Bool) {
    driver.last = now
    guard hypot(point.x - start.x, point.y - start.y) <= 14 else { return }
    if !reduced && audio == nil { audio = SetupChecklistAllSetAudio() }
    let (kind, row) = driver.hit(point.x, point.y, now)
    switch kind {
    case 1, 3:
      if kind == 3 { driver.s.pressRow = row }
      after(driver.toggle(row, now), reduced: reduced)
    case 2:
      driver.toggleRow(row, now)
      buzz(2)
    case 4:
      driver.skip(row, now)
      buzz(2)
    case 5: if driver.dismiss(now, false) { buzz(0) }
    case 6:
      driver.s.ctaAt = now
      if !reduced { audio?.play("pop") }
      buzz(3)
    case 7:
      driver.s.laterAt = now
      if driver.dismiss(now + 0.15, true) { buzz(0) }
    default: break
    }
    revision += 1
  }
  func tickNext() {
    driver.takeOver(now)
    if let next = driver.s.done.firstIndex(of: false) { _ = driver.toggle(next, now) }
    revision += 1
  }
  private func buzz(_ kind: Int) {
    if kind == 2 {
      UISelectionFeedbackGenerator().selectionChanged()
    } else if kind == 3 {
      UINotificationFeedbackGenerator().notificationOccurred(.success)
    } else {
      UIImpactFeedbackGenerator(style: kind == 1 ? .medium : .light).impactOccurred()
    }
  }
  private func schedule(_ delay: Double, _ action: @escaping () -> Void) {
    // These are wall-clock audio cues, matching RN's setTimeout rather than frame time.
    let item = DispatchWorkItem(block: action)
    cues.append(item)
    DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: item)
  }
  private func after(_ outcome: String, reduced: Bool) {
    if outcome == "uncheck" {
      if !reduced { audio?.play("uncheck") }
      buzz(2)
    } else {
      if !reduced { audio?.play("check") }
      buzz(0)
      if outcome == "complete" {
        schedule(0.4 + M.D.morph) { [weak self] in if !reduced { self?.audio?.play("complete") } }
        schedule(0.4 + M.D.morph + 0.2) { [weak self] in
          if !reduced { self?.audio?.play("pop") }
          self?.buzz(3)
        }
      }
    }
  }
}
