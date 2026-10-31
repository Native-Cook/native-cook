import Observation
import SwiftUI
import UIKit

@Observable final class StarCouponTearDriver {
  typealias M = StarCouponTearMotion
  var epoch = Date.timeIntervalSinceReferenceDate
  var manualStart: Double? = nil
  var initial = M.Frame()
  var held = M.Frame()
  var returnDuration = 0.45
  var returnDamping = 0.55
  var reducedIdle = true
  var action = 0  // 0: return to rest, 1: drag, 2: claim, 3: finish pop
  var phaseOrigin = 0.0
  var dragging = false
  var lastTouch = -100.0
  var reduced = false
  var autoPlay = false
  var pausedAt: Double? = nil
  var pauseTotal = 0.0
  private var doneTask: Task<Void, Never>?
  var now: Double { (pausedAt ?? Date.timeIntervalSinceReferenceDate) - epoch - pauseTotal }
  var pin: Double? {
    #if DEBUG
      if UserDefaults.standard.object(forKey: "ncPinTime") != nil {
        return UserDefaults.standard.double(forKey: "ncPinTime")
      }
    #endif
    return nil
  }
  func cancelCompletion() {
    doneTask?.cancel()
    doneTask = nil
  }
  func pause(_ paused: Bool) {
    if paused && pausedAt == nil { pausedAt = Date.timeIntervalSinceReferenceDate }
    if !paused, let p = pausedAt {
      pauseTotal += Date.timeIntervalSinceReferenceDate - p
      pausedAt = nil
    }
  }
  var settleTime: Double {
    guard let start = manualStart else { return 0 }
    return start + (action == 2 ? (reduced ? 1.35 : 2.65) : 0)
  }
  var busyUntil: Double { (manualStart ?? 0) + (reduced ? 1.25 : 2.65) }
  func resumed(_ t: Double) -> Bool {
    autoPlay && !reduced && action != 1 && t >= max(settleTime, lastTouch) + 2
  }
  func frame(_ t: Double) -> M.Frame {
    guard let start = manualStart else {
      return autoPlay && !reduced
        ? M.frameAt(pin ?? t.truncatingRemainder(dividingBy: M.loop)) : M.Frame()
    }
    if resumed(t) {
      return M.frameAt((t - max(settleTime, lastTouch) - 2).truncatingRemainder(dividingBy: M.loop))
    }
    let dt = max(0, t - start)
    if action == 1 { return held }
    if action == 3 {
      var f = initial
      f.pop += (1 - f.pop) * M.defaultEase(M.seg(dt, 0, 0.3))
      f.finger *= 1 - M.defaultEase(M.seg(dt, 0, 0.15))
      return f
    }
    if action == 0 {
      var f = initial
      f.pull *= 1 - (reduced ? 1 : M.spring(dt, returnDuration, returnDamping))
      f.tear *= 1 - (reduced ? 1 : M.spring(dt, returnDuration, 0.8))
      f.finger *= 1 - M.defaultEase(M.seg(dt, 0, 0.15))
      return f
    }
    let morph = reduced ? 0.25 : 0.4
    let wait = reduced ? 0.0 : 0.45
    let flight = reduced ? 0.3 : 0.7
    let done = morph + wait + flight
    if dt < done {
      var f = initial
      f.pull *= 1 - (reduced ? 1 : M.spring(dt, 0.4, 0.6))
      f.tear += (1 - f.tear) * M.bezier(dt / morph, 0.23, 1, 0.32, 1)
      f.fly += (1 - f.fly) * M.bezier((dt - morph - wait) / flight, 0.77, 0, 0.175, 1)
      f.finger *= 1 - M.defaultEase(M.seg(dt, 0, 0.15))
      return f
    }
    return M.Frame(
      pop: M.seg(dt, done + 0.5, done + 0.5 + (reduced ? 0.2 : 0.6)),
      chip: M.seg(dt, done, done + 0.8))
  }
  func phase(_ t: Double) -> Double {
    if reduced { return phaseOrigin }
    if manualStart == nil {
      return 2 * Double.pi * (pin ?? t.truncatingRemainder(dividingBy: M.loop)) / M.loop
    }
    if resumed(t) { return 2 * Double.pi * (t - max(settleTime, lastTouch) - 2) / M.loop }
    return phaseOrigin + 2 * Double.pi * (t - (manualStart ?? t)) / M.loop
  }
  func takeOver() {
    let t = now
    let wasAuto = manualStart == nil || resumed(t)
    let snapshot = frame(t)
    let oldPhase = phase(t)
    lastTouch = t
    if wasAuto {
      let f = snapshot
      manualStart = t
      initial = f
      held = f
      phaseOrigin = oldPhase
      action = 0
      returnDuration = 0.4
      returnDamping = 0.6
      if f.fly > 0.001 || f.tear > 0.95 { claim() } else if f.pop < 1 { action = 3 }
    }
  }
  func claim() {
    let t = now
    if action == 2 && t < busyUntil && frame(t).fly > 0 { return }
    let f = frame(t)
    let p = phase(t)
    manualStart = t
    initial = f
    phaseOrigin = p
    action = 2
    lastTouch = t
    reducedIdle = false
    doneTask?.cancel()
    doneTask = Task { @MainActor [weak self] in
      guard let self else { return }
      let delay = self.reduced ? 0.55 : 1.55
      // Suspension keeps the completion haptic aligned when the app becomes inactive.
      while self.now < t + delay {
        try? await Task.sleep(for: .milliseconds(20))
        if Task.isCancelled { return }
      }
      UINotificationFeedbackGenerator().notificationOccurred(.success)
      UIAccessibility.post(notification: .announcement, argument: "+1")
      while self.now < t + (self.reduced ? 1.35 : 2.65) {
        try? await Task.sleep(for: .milliseconds(20))
        if Task.isCancelled { return }
      }
      self.reducedIdle = true
    }
  }
  func touch(_ state: UIGestureRecognizer.State, _ dy: Double, _ velocity: Double) {
    if state == .began {
      takeOver()
      dragging = false
      return
    }
    if state == .changed {
      if action == 2 && now < busyUntil { return }
      if !dragging {
        guard abs(dy) > 6 else { return }
        dragging = true
        initial = frame(now)
        phaseOrigin = phase(now)
        manualStart = now
        action = 1
        held = initial
        held.finger = 0
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
      }
      lastTouch = now
      var p = max(0, dy / M.pullPT)
      if p > 1 { p = 1 + 0.15 * (1 - exp(-(p - 1) / 0.15)) }
      held.pull = reduced ? 0 : p
      held.tear = M.tearOf(min(1, p))
    } else if state == .ended || state == .cancelled || state == .failed {
      guard dragging else { return }
      dragging = false
      lastTouch = now
      if dy / M.pullPT + velocity / M.pullPT * 0.15 > M.claimAt {
        claim()
      } else {
        initial = held
        phaseOrigin = phase(now)
        manualStart = now
        returnDuration = 0.45
        returnDamping = 0.55
        action = 0
      }
    }
  }
}
