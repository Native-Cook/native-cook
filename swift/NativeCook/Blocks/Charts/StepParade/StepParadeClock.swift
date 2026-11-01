import SwiftUI
import UIKit

final class StepParadeClock: ObservableObject {
  @Published var running = false
  @Published var revision = 0
  var h = StepParadeMotion.now
  var velocity = 0.0
  var dragging = false
  var h0 = StepParadeMotion.now
  var lastTouch = -100.0
  var scripted = false
  var loopT = 0.0
  var selected = -1
  var bubbleSelected = -1
  var lastHour = 0
  private var clock = 0.0
  private var date: Date?
  private let haptic = UISelectionFeedbackGenerator()
  func reset(_ auto: Bool) {
    h = auto ? 6 : StepParadeMotion.now
    velocity = 0
    dragging = false
    scripted = false
    loopT = 0
    selected = -1
    bubbleSelected = -1
    lastTouch = -100
    clock = 0
    date = nil
    running = false
  }
  func suspend() { date = nil }
  func sample(_ date: Date, _ auto: Bool, _ reduced: Bool, _ pinned: Double?) -> Double {
    if let pinned { return StepParadeMotion.hour(pinned) }
    let wall = min(0.5, max(0, self.date.map { date.timeIntervalSince($0) } ?? 0.016))
    self.date = date
    let dt = min(wall, 0.05)
    clock += wall
    if auto && !dragging && clock - lastTouch > 3.5
      && (scripted || abs(h - StepParadeMotion.now) < 0.01)
    {
      if !scripted {
        scripted = true
        loopT = 7
        selected = -1
      }
      loopT = (loopT + wall).truncatingRemainder(dividingBy: 9)
      h = StepParadeMotion.hour(loopT)
      velocity = 0
    } else {
      scripted = false
      if !dragging {
        let k = reduced ? 400.0 : 18.0
        velocity += (k * (StepParadeMotion.now - h) - 2 * sqrt(k) * velocity) * dt
        h += velocity * dt
        if !auto && abs(h - StepParadeMotion.now) < 0.00001 && abs(velocity) < 0.00001 && running {
          h = StepParadeMotion.now
          velocity = 0
          DispatchQueue.main.async { [weak self] in self?.running = false }
        }
      }
    }
    return h
  }
  func pan(_ dx: Double, _ width: Double) {
    if !dragging {
      dragging = true
      scripted = false
      h0 = h
      velocity = 0
      lastHour = Int(floor(h))
    }
    var hour = h0 + dx / width * 16
    if hour < 6 { hour = 6 - (1 - exp(-(6 - hour))) * 0.4 }
    if hour > 21.5 { hour = 21.5 + (1 - exp(-(hour - 21.5))) * 0.3 }
    h = hour
    let hr = Int(floor(h))
    if hr != lastHour {
      lastHour = hr
      haptic.selectionChanged()
    }
    lastTouch = clock
    running = true
    revision += 1
  }
  func endPan() {
    dragging = false
    lastTouch = clock
    running = true
    revision += 1
  }
  func tap(_ p: CGPoint, _ g: CGRect) {
    lastTouch = clock
    var best = -1
    var bd = 34.0
    for i in 0..<6 {
      let at = StepParadeMotion.place(g, i, StepParadeMotion.steps(i, h))
      let d = hypot(p.x - at.x, p.y - (at.y - 30 * at.s))
      if d < bd {
        bd = d
        best = i
      }
    }
    selected = best == selected ? -1 : best
    bubbleSelected = selected
    haptic.selectionChanged()
    running = true
    revision += 1
  }
}
