import SwiftUI
import UIKit

final class EvolveHoldMorphClock: ObservableObject {
  @Published var awake = false
  private var date: Date?
  private var now = 0.0
  private var stage = 0
  private var c = 0.0, cV = 0.0, sq = 0.0, sqV = 0.0
  private var hop = 0.0, hopV = 0.0
  private var hold = false
  private var lastTouch = -100.0
  private var scripted = false
  private var loopT = 0.0
  private var burstAt = -10.0
  private var tick = 0
  private var blinkWake: DispatchWorkItem?
  private let selection = UISelectionFeedbackGenerator()
  private let light = UIImpactFeedbackGenerator(style: .light)
  private let success = UINotificationFeedbackGenerator()
  func resetDate() { date = nil }
  func preferencesChanged() {
    wake()
    date = nil
  }
  private func sleepUntilBlink() {
    guard blinkWake == nil else { return }
    let wait = 3.6 - now.truncatingRemainder(dividingBy: 3.6)
    let sleepDate = Date()
    let work = DispatchWorkItem { [weak self] in
      guard let self else { return }
      self.blinkWake = nil
      self.now += Date().timeIntervalSince(sleepDate)
      self.date = Date()
      self.awake = true
    }
    blinkWake = work
    DispatchQueue.main.asyncAfter(deadline: .now() + wait, execute: work)
  }
  deinit { blinkWake?.cancel() }
  func sample(_ at: Date, auto: Bool, reduced: Bool, pinned: Double?) -> EvolveHoldMorphFrame {
    if let pinned { return EvolveHoldMorphMotion.at(pinned) }
    let wall = min(0.5, max(0, date.map { at.timeIntervalSince($0) } ?? 0.016))
    date = at
    if reduced && !awake && blinkWake == nil {
      DispatchQueue.main.async { [weak self] in self?.awake = true }
    }
    let dt = min(wall, 0.05)
    now += wall
    let blinkPh = now.truncatingRemainder(dividingBy: 3.6)
    let blink = blinkPh < 0.14 ? sin(blinkPh / 0.14 * .pi) : 0
    var yaw = reduced ? 0 : 0.28 * sin(now * 2 * .pi / 9)
    if auto && !reduced && !hold && now - lastTouch > 3.5
      && (scripted || (c < 0.001 && abs(sq) < 0.004)) {
      if !scripted {
        scripted = true
        loopT = stage == 0 ? 0.9 : stage == 1 ? 3.6 : 6.8
      }
      loopT = (loopT + wall).truncatingRemainder(dividingBy: 9)
      let sc = EvolveHoldMorphMotion.at(loopT)
      stage = sc.stage; c = sc.c; sq = sc.sq; sqV = 0
      burstAt = sc.burst >= 0 ? now - sc.burst : -10
    } else {
      scripted = false
      if hold {
        c += dt / 1.4
        let ticks = Int(floor(c * (4 + c * 8)))
        if ticks != tick { tick = ticks; selection.selectionChanged() }
        if c >= 1 {
          c = 0; cV = 0; stage = (stage + 1) % 3
          burstAt = stage == 0 ? -10 : now
          hold = false; tick = 0
          if !reduced { sqV -= 1.6 }
          success.notificationOccurred(.success)
        }
      } else if c > 0 {
        cV += (-90 * c - 14 * cV) * dt
        c += cV * dt
        if c < 0.001 { c = 0; cV = 0 }
      }
      let target = reduced ? 0 : EvolveHoldMorphMotion.chargeShape(c)
      sqV += (240 * (target - sq) - 8 * sqV) * dt
      sq += sqV * dt
      if hold && !reduced { yaw += sin(now * 46) * 0.05 * c }
    }
    hopV -= 1600 * dt
    hop += hopV * dt
    if hop < 0 { hop = 0; hopV = abs(hopV) > 80 ? -hopV * 0.3 : 0 }
    let m = reduced ? (c > 0.95 ? 1.0 : 0) : EvolveHoldMorphMotion.smooth(0.5, 1, c)
    // Sleep between the 140 ms blinks; the source keeps blinking under reduced motion.
    if reduced && !hold && c == 0 && hop == 0 && abs(sq) < 0.0001 && abs(sqV) < 0.001 && blinkPh >= 0.14 && !(burstAt > 0 && now - burstAt < 0.9) && awake {
      DispatchQueue.main.async { [weak self] in
        self?.awake = false; self?.sleepUntilBlink()
      }
    }
    return EvolveHoldMorphFrame(stage: stage, c: c, m: m, sq: sq, yaw: yaw,
      burst: burstAt > 0 ? now - burstAt : -1, blink: blink, t: now, hop: hop, hold: hold)
  }
  private func wake() {
    let sleeping = blinkWake != nil && !awake
    if sleeping, let date { now += max(0, Date().timeIntervalSince(date)) }
    blinkWake?.cancel(); blinkWake = nil
    if sleeping { date = nil }
    if !awake { awake = true }
  }
  func begin() {
    wake()
    hold = true; scripted = false; tick = 0; lastTouch = now
  }
  func end() { hold = false; lastTouch = now }
  func tap() { wake(); hopV = 380; lastTouch = now; light.impactOccurred() }
}

struct EvolveHoldMorphTouch: UIViewRepresentable {
  var pet: Bool
  var clock: EvolveHoldMorphClock
  func makeUIView(context: Context) -> EvolveHoldMorphTouchSurface {
    let view = EvolveHoldMorphTouchSurface()
    view.backgroundColor = .clear
    view.isMultipleTouchEnabled = false
    view.pet = pet; view.clock = clock
    return view
  }
  func updateUIView(_ uiView: EvolveHoldMorphTouchSurface, context: Context) {
    uiView.pet = pet; uiView.clock = clock
  }
}
final class EvolveHoldMorphTouchSurface: UIView {
  var pet = false
  weak var clock: EvolveHoldMorphClock?
  private var start = CGPoint.zero
  private var startTime = 0.0
  private var cancelled = false
  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard let touch = touches.first else { return }
    start = touch.location(in: self); startTime = touch.timestamp; cancelled = false
    clock?.begin()
  }
  override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard let p = touches.first?.location(in: self) else { return }
    if hypot(p.x - start.x, p.y - start.y) > 80 && !cancelled {
      cancelled = true; clock?.end()
    }
  }
  override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
    clock?.end()
    if pet, !cancelled, let t = touches.first, t.timestamp - startTime <= 0.2 {
      let p = t.location(in: self)
      if hypot(p.x - start.x, p.y - start.y) <= 10 { clock?.tap() }
    }
  }
  override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { clock?.end() }
}
