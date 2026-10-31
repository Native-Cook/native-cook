import SwiftUI
import UIKit

final class SwipeToOrderOnboardingClock: ObservableObject {
  typealias M = SwipeToOrderOnboardingMotion
  @Published var awake = false
  @Published var done = false
  private var frame = M.rest()
  private var date: Date?
  private var now = 0.0
  private var last = -10.0
  private var doneAt = -10.0
  private var velocity = 0.0
  private var held = false
  private var start = 0.0
  private var placed = 0
  private var cachedPin: (Double, Bool, M.Frame)?
  // Touch cues at knob thresholds: rising 0.34, 0.68, 0.985; falling 0.6.
  private static let recordingBeats: [(time: Double, cue: String)] = [
    (1.960176421734, "snap"),
    (3.385701896841, "snap"),
    (3.650964036914, "done"),
    (6.178495330017, "back"),
  ]
  func reset() { frame = M.rest(); date = nil; now = 0; last = -10; doneAt = -10; velocity = 0; held = false; placed = 0; done = false; awake = false }
  func active(_ active: Bool) {
    date = nil
    if !active { for p in SwipeToOrderOnboardingResources.shared.players.values { p.stop() } }
  }
  private func settle(_ sound: Bool, _ reduced: Bool) {
    if !frame.done && frame.s >= 0.985 {
      frame.done = true
      if sound {
        doneAt = now
        SwipeToOrderOnboardingResources.shared.play("done", reduced)
        UINotificationFeedbackGenerator().notificationOccurred(.success)
      }
    } else if frame.done && frame.s < 0.6 {
      frame.done = false
      if sound { SwipeToOrderOnboardingResources.shared.play("back", reduced) }
    }
  }
  func sample(_ at: Date, _ auto: Bool, _ reduced: Bool, _ pin: Double?) -> M.Frame {
    if let pin {
      if let cachedPin, cachedPin.0 == pin && cachedPin.1 == reduced { return cachedPin.2 }
      let f = M.at(pin, reduced)
      cachedPin = (pin, reduced, f)
      return f
    }
    // Keep the recording on wall-clock time while springs retain their bounded step.
    let raw = max(0, date.map { at.timeIntervalSince($0) } ?? 0.016)
    let dt = min(raw, 0.05)
    date = at; now += dt
    let touched = held || now-last < 2
    if auto && !touched {
      let before = frame.t
      frame.t += min(raw, 0.5)
      if frame.t >= M.loop { frame.t -= M.loop }
      frame.s = M.knob(frame.t)
      settle(false, reduced)
      if DemoSound.inAutoPlay && !reduced {
        for beat in Self.recordingBeats {
          let crossed = before <= frame.t
            ? before < beat.time && frame.t >= beat.time
            : before < beat.time || frame.t >= beat.time
          if crossed { SwipeToOrderOnboardingResources.shared.play(beat.cue, reduced) }
        }
      }
    } else if !held {
      let target = frame.done ? 1.0 : 0.0
      velocity += (260*(target-frame.s)-2*sqrt(260)*0.95*velocity)*dt
      frame.s = M.clamp(frame.s+velocity*dt)
      settle(true, reduced)
    } else { settle(true, reduced) }
    M.step(&frame.cards, frame.s, dt, reduced)
    if !reduced { frame.spin = auto && !touched ? 4*Double.pi*frame.t/M.loop : frame.spin+dt*4*Double.pi/M.loop }
    let next = Int(floor(M.clamp(frame.s)/0.34))
    if next > placed && held {
      SwipeToOrderOnboardingResources.shared.play("snap", reduced)
      UISelectionFeedbackGenerator().selectionChanged()
    }
    placed = next
    frame.since = auto && !held && now-last >= 2 ? (frame.t >= 3.8 && frame.t < 5.9 ? frame.t-3.8 : -1) : now-doneAt
    if done != frame.done {
      let value = frame.done
      DispatchQueue.main.async { [weak self] in self?.done = value }
    }
    if reduced && !held && abs(velocity) < 0.00001 && frame.cards.allSatisfy({ b in b.v.allSatisfy { abs($0) < 0.00001 } }) && awake {
      DispatchQueue.main.async { [weak self] in self?.awake = false }
    }
    return frame
  }
  func touch(_ state: UIGestureRecognizer.State, _ dx: Double, _ vx: Double, _ range: Double, _ reduced: Bool) {
    switch state {
    case .began:
      last = now; held = true; start = frame.s; velocity = 0; awake = true
      date = nil
      UIImpactFeedbackGenerator(style: .light).impactOccurred()
    case .changed:
      last = now; frame.s = M.clamp(start+dx/range)
    case .ended, .cancelled, .failed:
      last = now; held = false; velocity = vx/range
      if frame.s > 0.85 || (frame.s > 0.5 && vx > 900) { frame.s = max(frame.s, 0.986) }
      settle(true, reduced)
      if !frame.done && frame.s > 0.05 { SwipeToOrderOnboardingResources.shared.play("back", reduced) }
    default: break
    }
  }
  func accessibilityTap() {
    frame.s = frame.done ? 0 : 0.99
    last = now; awake = true; date = nil
  }
}
