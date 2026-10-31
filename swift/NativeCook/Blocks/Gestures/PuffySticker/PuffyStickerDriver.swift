import SwiftUI
import UIKit

final class PuffyStickerDriver: ObservableObject {
  typealias M = PuffyStickerMotion
  typealias A = PuffyStickerAnimation
  @Published var frame = M.Frame()
  var x = 0.0, y = 0.0, axis = 0.0
  var auto = false, reduced = false, manual = true, holding = false, dragging = false,
    resuming = false
  var start = Date.timeIntervalSinceReferenceDate, now = Date.timeIntervalSinceReferenceDate
  var lastTouch = -100.0, holdStart = 0.0, holdFrom = 0.0, quarter = 0
  var animations: [String: A] = [:]
  var shapeLanding = false
  let audio = PuffyStickerAudio()
  var previousLoopTime = 0.0
  var active: Bool { auto || holding || !animations.isEmpty }
  var selected: Int { min(3, max(0, Int(frame.shape.rounded()))) }
  var puffy: Bool { frame.puff > 0.5 }
  var pin: Double? {
    #if DEBUG
      if UserDefaults.standard.object(forKey: "ncPinTime") != nil {
        return UserDefaults.standard.double(forKey: "ncPinTime")
      }
    #endif
    return nil
  }
  func configure(autoPlay: Bool, reduced: Bool) {
    self.reduced = reduced
    auto = autoPlay && !reduced
    manual = !auto
    start = Date.timeIntervalSinceReferenceDate
    animations.removeAll()
    if auto {
      frame = M.Frame()
      x = 0
      y = 0
    }
    if reduced {
      frame.touch = 0
      frame.tapPress = 0
      frame.flatPress = 0
      frame.squish = 0
    }
    axis = 0
    holding = false
    dragging = false
    resuming = false
    objectWillChange.send()
  }
  func value(_ key: String) -> Double {
    switch key {
    case "puff": return frame.puff
    case "shape": return frame.shape
    case "squish": return frame.squish
    case "x": return x
    case "y": return y
    case "touch": return frame.touch
    default: return 0
    }
  }
  func set(_ key: String, _ v: Double) {
    switch key {
    case "puff": frame.puff = v
    case "shape": frame.shape = v
    case "squish": frame.squish = v
    case "x": x = v
    case "y": y = v
    case "touch": frame.touch = v
    default: break
    }
  }
  func animate(_ key: String, _ steps: [A.Step]) {
    if reduced, let first = steps.first, case .spring = first.curve {
      animations.removeValue(forKey: key)
      set(key, steps.last!.end)
      return
    }
    var queued = steps
    if let first = queued.first, case .spring(let duration, let ratio, let velocity) = first.curve,
      let old = animations[key], let oldStep = old.steps.first,
      case .spring(let oldDuration, let oldRatio, _) = oldStep.curve
    {
      // Reanimated continues an already running spring to the same destination.
      if oldStep.end == first.end && oldDuration == duration && oldRatio == ratio { return }
      queued[0] = .spring(first.end, duration, ratio, velocity + old.lastVelocity)
    }
    animations[key] = A(value(key), now, queued)
    objectWillChange.send()
  }
  func cue(_ cue: String) { if !reduced { audio.play(cue) } }
  func buzz(_ kind: String) {
    if kind == "select" {
      UISelectionFeedbackGenerator().selectionChanged()
    } else if kind == "success" {
      UINotificationFeedbackGenerator().notificationOccurred(.success)
    } else {
      UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }
  }
  func tick(_ date: Date) {
    now = date.timeIntervalSinceReferenceDate
    if !manual {
      let t =
        pin
        ?? ((now - start).truncatingRemainder(dividingBy: M.loop) + M.loop).truncatingRemainder(
          dividingBy: M.loop)
      frame = M.frameAt(t)
      if DemoSound.inAutoPlay && !reduced && pin == nil {
        if t < previousLoopTime { previousLoopTime = 0 }
        for (at, c) in [
          (M.hold0, "inflate"), (M.star + 0.1, "morph"), (M.cloud + 0.1, "morph"),
          (M.smiley + 0.1, "morph"), (M.heart + 0.1, "morph"),
        ] where previousLoopTime < at && t >= at { cue(c) }
        if previousLoopTime < M.hold1 && t >= M.hold1 { audio.stop("inflate") }
        for q in 1...3 {
          let p = Double(q) / 4
          if M.frameAt(previousLoopTime).puff < p && frame.puff >= p { cue("tick") }
        }
      }
      previousLoopTime = t
      axis = 0
      return
    }
    for key in Array(animations.keys) {
      guard var a = animations[key] else { continue }
      let (v, done) = a.sample(now)
      set(key, v)
      if done {
        animations.removeValue(forKey: key)
        if key == "shape" && shapeLanding {
          shapeLanding = false
          animate("squish", [.timing(0.05, 0.09), .spring(0, 0.6, 0.3)])
        }
      } else {
        animations[key] = a
      }
    }
    if holding && !dragging {
      let held = now - holdStart - 0.12
      if held > 0 {
        frame.puff = holdFrom + (1 - holdFrom) * M.inflateAfter(held)
        let q = Int(floor(frame.puff * 4 + 0.001))
        if q > quarter {
          quarter = q
          cue("tick")
          buzz("select")
        }
      }
    } else if auto && !resuming && !holding && now - lastTouch > 2 {
      resuming = true
      shapeLanding = false
      animate("puff", [.timing(0, 0.7, A.ease)])
      animate("shape", [.timing(0, 0.7 + 0.26 * frame.shape, A.ease)])
      animate("x", [.spring(0, 0.5, 0.8)])
      animate("y", [.spring(0, 0.5, 0.8)])
    } else if resuming && frame.puff < 0.002 && frame.shape < 0.002 && now - lastTouch > 3.4 {
      resuming = false
      start = now
      manual = false
      animations.removeAll()
      previousLoopTime = 0
    }
    objectWillChange.send()
  }
  func takeOver() {
    now = Date.timeIntervalSinceReferenceDate
    if !manual { tick(Date()) }
    lastTouch = now
    resuming = false
    if !manual {
      manual = true
      animate("touch", [.timing(0, 0.15)])
      frame.tapPress = 0
      frame.flatPress = 0
      animate("squish", [.spring(0, 0.5, 0.35)])
    }
  }
  func goToShape(_ i: Int) {
    takeOver()
    let delta = abs(frame.shape - Double(i))
    guard delta >= 0.01 else { return }
    cue("morph")
    buzz("light")
    shapeLanding = !reduced
    animate(
      "shape",
      [.timing(Double(i), reduced ? 0.18 : M.morph * (0.7 + 0.3 * delta), reduced ? .quad : A.ease)]
    )
  }
  func setPuff(_ on: Bool) {
    takeOver()
    buzz("light")
    if on { cue("inflate") }
    animate(
      "puff", reduced ? [.timing(on ? 1 : 0, 0.2)] : [.spring(on ? 1 : 0, 0.7, on ? 0.55 : 0.8)])
    if !reduced { animate("squish", [.timing(on ? -0.05 : 0.07, 0.14), .spring(0, 0.7, 0.3)]) }
  }
  func done() {
    takeOver()
    buzz("success")
    cue("land")
    guard !reduced else { return }
    axis = 0
    animate(
      "squish",
      [.timing(0.08, 0.11), .timing(-0.1, 0.14), .timing(0.06, 0.16), .spring(0, 0.6, 0.35)])
    animate("y", [.timing(0, 0.11), .timing(-38, 0.22, A.out), .spring(0, 0.52, 0.45)])
  }
  func cancel() {
    takeOver()
    shapeLanding = false
    animate("puff", [.timing(0, reduced ? 0.18 : 0.42, A.ease)])
    animate("shape", [.timing(0, reduced ? 0.18 : 0.42 + 0.2 * frame.shape, A.ease)])
  }
  func stickerEvent(_ state: UIGestureRecognizer.State, _ translation: CGSize, _ velocity: CGVector)
  {
    switch state {
    case .began:
      takeOver()
      holding = true
      dragging = false
      holdStart = now
      holdFrom = frame.puff
      animations.removeValue(forKey: "puff")
      quarter = Int(floor(frame.puff * 4 + 0.001))
      if !reduced { animate("squish", [.timing(-0.03, 0.12)]) }
      if frame.puff < 0.98 { cue("inflate") }
    case .changed:
      now = Date.timeIntervalSinceReferenceDate
      lastTouch = now
      if !dragging && hypot(translation.width, translation.height) > 10 {
        dragging = true
        audio.stop("inflate")
      }
      if dragging {
        animations.removeValue(forKey: "x")
        animations.removeValue(forKey: "y")
        animations.removeValue(forKey: "squish")
        x = translation.width
        y = translation.height
        if !reduced {
          frame.squish = -min(0.14, hypot(velocity.dx, velocity.dy) / 9000)
          axis = atan2(velocity.dy, velocity.dx)
        }
      }
    case .ended, .cancelled, .failed:
      now = Date.timeIntervalSinceReferenceDate
      lastTouch = now
      holding = false
      audio.stop("inflate")
      let speed = hypot(velocity.dx, velocity.dy)
      if dragging {
        animate("x", [.spring(0, 0.56, 0.6, velocity.dx)])
        animate("y", [.spring(0, 0.56, 0.6, velocity.dy)])
        if !reduced {
          animate("squish", [.timing(min(0.1, speed / 12000 + 0.03), 0.12), .spring(0, 0.7, 0.3)])
        }
      } else if !reduced {
        animate(
          "squish", [.timing(now - holdStart < 0.15 ? 0.09 : 0.07, 0.1), .spring(0, 0.8, 0.25)])
      }
      dragging = false
    default: break
    }
    objectWillChange.send()
  }
  func scrub(_ value: Double) {
    takeOver()
    animations.removeValue(forKey: "shape")
    shapeLanding = false
    frame.shape = min(3, max(0, value))
  }
  func endScrub() {
    lastTouch = Date.timeIntervalSinceReferenceDate
    animate("shape", [.spring(Double(selected), 0.42, 0.7)])
  }
}
