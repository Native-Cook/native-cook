import SwiftUI
import UIKit

final class PopUpQuestCardClock: ObservableObject {
  typealias M = PopUpQuestCardMotion
  @Published var running = false
  @Published var pressed = -1
  var pose = M.Pose()
  var active = 0
  private var v = 0.0, yawV = 0.0, target = 0.0, now = 0.0, lastTouch = -100.0
  private var loopT = 0.0, scripted = false, drag = false, horizontal = false, o0 = 0.0
  private var previous: Date?
  private var wake: Timer?
  func sample(_ date: Date, _ auto: Bool, _ reduced: Bool, _ pinned: Double?) -> M.Pose {
    if let pinned { return M.at(pinned) }
    let wall = min(0.5, max(0, previous.map { date.timeIntervalSince($0) } ?? 0.016))
    previous = date; now += wall
    let dt = min(wall, 0.05)
    if auto && !reduced && !drag && now - lastTouch > 3.5 && (scripted || (pose.o < 0.001 && target == 0)) {
      if !scripted { scripted = true; loopT = 7 }
      loopT = (loopT + wall).truncatingRemainder(dividingBy: M.loop)
      pose = M.at(loopT)
      if active != 0 && pose.o == 0 { active = 0 }
      v = 0; yawV = 0; target = pose.o > 0.5 ? 1 : 0
    } else {
      scripted = false
      if !drag {
        let k = reduced ? 500.0 : 120.0, c = 2 * 0.86 * sqrt(k)
        v += (k * (target - pose.o) - c * v) * dt; pose.o += v * dt
        if pose.o < 0 { pose.o = 0; v = 0 }
        yawV += (-160 * pose.yaw - 18 * yawV) * dt; pose.yaw += yawV * dt
      }
    }
    return pose
  }
  func settle(_ auto: Bool) {
    if auto && running && scripted && pose.o == 0 {
      let delay = loopT < 1 ? 1 - loopT : M.loop - loopT + 1
      running = false; previous = nil
      wake?.invalidate()
      wake = Timer.scheduledTimer(withTimeInterval: delay, repeats: false) { [weak self] _ in
        guard let self else { return }
        self.loopT = 1; self.now += delay; self.running = true
      }
      return
    }
    guard running && !drag && !scripted && abs(pose.o - target) < 0.00001 && abs(v) < 0.0001 && abs(pose.yaw) < 0.00001 && abs(yawV) < 0.0001 else { return }
    pose.o = target; pose.yaw = 0; v = 0; yawV = 0
    running = false; previous = nil
    if auto && target == 0 {
      wake?.invalidate()
      wake = Timer.scheduledTimer(withTimeInterval: max(0.02, 3.51 - (now - lastTouch)), repeats: false) { [weak self] _ in
        guard let self else { return }; self.now = max(self.now, self.lastTouch + 3.51); self.running = true
      }
    }
  }
  private func touched() { scripted = false; lastTouch = now; wake?.invalidate(); running = true }
  func open(_ i: Int) {
    UIImpactFeedbackGenerator(style: .light).impactOccurred()
    guard pose.o <= 0.02 || active == i else { return }
    active = i; target = 1; touched()
  }
  func close() { UIImpactFeedbackGenerator(style: .light).impactOccurred(); target = 0; touched() }
  func pan(_ state: UIGestureRecognizer.State, _ translation: CGPoint, _ velocity: CGPoint, _ heroH: Double) {
    switch state {
    case .began:
      drag = true; horizontal = abs(translation.x) > abs(translation.y); o0 = pose.o; v = 0; touched()
      if horizontal { pose.yaw = min(0.45, max(-0.45, translation.x * 0.0022)) }
      else { pose.o = M.clamp(o0 - max(0, translation.y) / (heroH * 0.9)) }
    case .changed:
      if horizontal { pose.yaw = min(0.45, max(-0.45, translation.x * 0.0022)) }
      else { pose.o = M.clamp(o0 - max(0, translation.y) / (heroH * 0.9)) }
      lastTouch = now
    case .ended, .cancelled, .failed:
      guard drag else { return }; drag = false
      if horizontal { yawV = velocity.x * 0.0015 }
      else {
        let closing = pose.o < 0.75 || velocity.y > 700
        target = closing ? 0 : 1; v = -velocity.y / (heroH * 0.9)
        if closing { UISelectionFeedbackGenerator().selectionChanged() }
      }
      lastTouch = now
    default: break
    }
  }
  func suspend() { previous = nil; wake?.invalidate() }
  func activate(_ auto: Bool) {
    previous = nil
    if auto || abs(pose.o - target) > 0.00001 || abs(pose.yaw) > 0.00001 { running = true }
  }
}
