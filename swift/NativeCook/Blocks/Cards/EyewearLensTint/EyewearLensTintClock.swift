import SwiftUI
import UIKit

final class EyewearLensTintClock: ObservableObject {
  typealias M = EyewearLensTintMotion
  struct Tween {
    var from: Double, to: Double, at: Double, duration: Double
    var curve = 0
    func value(_ time: Double) -> Double {
      let p = M.seg(time, at, at + duration)
      let k: Double
      if curve == 1 { k = M.bezier(p, 0.77, 0, 0.175, 1) }
      else if curve == 2 { k = p }
      else { k = p < 0.5 ? 2 * p * p : 1 - pow(-2 * p + 2, 2) / 2 }
      return M.mix(from, to, k)
    }
    func finished(_ t: Double) -> Bool { t >= at + duration }
  }
  @Published var running = false
  private var previous: Date?
  private var clock = 0.0, autoStart = 0.0, lastTouch = -100.0
  private var manual = true, auto = false, reduced = false, resumeAsked = false
  private var grab = -1
  private var f = M.Frame()
  private var pourTween: Tween?, glareTween: Tween?, fingerTween: Tween?, carryTween: Tween?
  private var xSpring: M.Spring?, ySpring: M.Spring?, glareSpring: M.Spring?
  private var prevX = -100.0, prevY = -100.0, stretch = 0.0, heading = 0.0
  private let sound = EyewearLensTintSound()
  private let pick = UISelectionFeedbackGenerator()
  private let impact = UIImpactFeedbackGenerator(style: .soft)
  func reset(_ autoPlay: Bool, _ reduced: Bool) {
    self.reduced = reduced; auto = autoPlay && !reduced; manual = !auto
    previous = nil; clock = 0; autoStart = 0; lastTouch = -100; f = .init(); grab = -1
    resumeAsked = false; pourTween = nil; glareTween = nil; fingerTween = nil
    carryTween = nil; xSpring = nil; ySpring = nil; glareSpring = nil; running = false
    prevX = -100; prevY = -100; stretch = 0; heading = 0
  }
  func suspend() { previous = nil }
  func sample(_ date: Date, _ pinned: Double?, _ l: EyewearLensTintTheme.Layout) -> M.Frame {
    // Pin the shader clock too. RN currently pins only the script, leaving its pour wobble live.
    if let pinned { return M.replay(pinned, l) }
    // The recording clock follows wall time, including slow software-rendered frames.
    let dt = min(0.5, max(0, previous.map { date.timeIntervalSince($0) } ?? 0.016))
    previous = date
    let before = clock - autoStart
    clock += dt
    if !manual {
      if DemoSound.inAutoPlay {
        for d in M.drops {
          for (beat, pouring) in [(d.at - 0.15, false), (d.at + 0.8, true)] {
            if floor((before - beat) / M.loop) < floor((clock - autoStart - beat) / M.loop) { sound.play(pouring) }
          }
        }
      }
      f = M.position(M.at((clock - autoStart).truncatingRemainder(dividingBy: M.loop)), l)
      if f.carry < 0 { f.carryX = prevX; f.carryY = prevY }
      f.time = clock.truncatingRemainder(dividingBy: 600)
      updateStretch(dt)
      return f
    }
    if let tween = pourTween {
      f.pour = tween.value(clock)
      if tween.finished(clock) { f.from = f.to; f.pour = 0; pourTween = nil }
    }
    if let tween = glareTween { f.glare = tween.value(clock); if tween.finished(clock) { glareTween = nil } }
    if let tween = fingerTween { f.finger = tween.value(clock); if tween.finished(clock) { fingerTween = nil } }
    if let tween = carryTween { f.carryOn = tween.value(clock); if tween.finished(clock) { carryTween = nil } }
    if var spring = xSpring {
      let done = spring.step(dt); f.carryX = spring.value; xSpring = done ? nil : spring
    }
    if var spring = ySpring {
      let done = spring.step(dt); f.carryY = spring.value; ySpring = done ? nil : spring
      if done { carryTween = Tween(from: f.carryOn, to: 0, at: clock, duration: 0.14) }
    }
    if var spring = glareSpring {
      let done = spring.step(dt); f.glare = spring.value; glareSpring = done ? nil : spring
    }
    updateStretch(dt)
    f.time = clock.truncatingRemainder(dividingBy: 600)
    if auto && grab == -1 && f.pour == 0 && clock - lastTouch >= 2 {
      if !resumeAsked {
        resumeAsked = true; glareSpring = nil
        glareTween = Tween(from: f.glare, to: 0, at: clock, duration: 0.4)
        if f.to != 0 { pourInto(0, 1) }
      } else if f.from == 0 && abs(f.glare) < 0.001 && clock - lastTouch > 3.6 {
        resumeAsked = false; autoStart = clock; manual = false
      }
    }
    if !auto && grab == -1 && pourTween == nil && glareTween == nil && fingerTween == nil && carryTween == nil && xSpring == nil && ySpring == nil && glareSpring == nil && running {
      DispatchQueue.main.async { [weak self] in self?.running = false }
    }
    return f
  }
  private func updateStretch(_ dt: Double) {
    if dt > 0 && !reduced {
      let vx = (f.carryX - prevX) / dt, vy = (f.carryY - prevY) / dt
      let speed = hypot(vx, vy)
      let want = f.carryOn > 0.5 && speed < 6000 ? min(0.4, speed / 2400) : 0
      stretch += (want - stretch) * min(1, dt * 16)
      if speed > 60 { heading = atan2(vy, vx) }
    }
    prevX = f.carryX; prevY = f.carryY
    f.stretch = reduced ? 0 : stretch; f.heading = heading
  }
  private func takeOver() {
    lastTouch = clock; resumeAsked = false
    if !manual {
      manual = true; f.carry = -1
      // RN immediately hides the tutorial finger when mode leaves autoplay.
      f.finger = 0; fingerTween = nil
      carryTween = Tween(from: f.carryOn, to: 0, at: clock, duration: 0.15)
      glareTween = Tween(from: f.glare, to: 0, at: clock, duration: 0.3)
      if f.pour > 0 { pourTween = Tween(from: f.pour, to: 1, at: clock, duration: (1 - f.pour) * M.pour, curve: 2) }
    }
    previous = nil; running = true
  }
  private func pourInto(_ tint: Int, _ which: Int) {
    let now = f.pour > 0.5 ? f.to : f.from
    f.from = f.pour > 0 ? now : f.to; f.to = tint; f.lens = which; f.pour = 0
    pourTween = Tween(from: 0, to: 1, at: clock, duration: reduced ? 0.25 : M.pour, curve: 2)
    impact.impactOccurred()
    sound.play(true)
  }
  func begin(_ p: CGPoint, _ l: EyewearLensTintTheme.Layout) {
    takeOver(); grab = -2
    for i in 0..<4 where hypot(p.x - l.drop(i).x, p.y - l.drop(i).y) < 26 { grab = i }
    if grab >= 0 {
      f.carryTint = grab; f.carryX = p.x; f.carryY = p.y
      xSpring = nil; ySpring = nil
      carryTween = Tween(from: f.carryOn, to: 1, at: clock, duration: 0.12)
      pick.selectionChanged()
      sound.play(false)
    }
  }
  func move(_ p: CGPoint, _ dx: Double) {
    lastTouch = clock
    if grab >= 0 { f.carryX = p.x; f.carryY = p.y - 28 }
    else { glareTween = nil; glareSpring = nil; f.glare = max(-1, min(1, dx / 140)) }
  }
  func end(_ l: EyewearLensTintTheme.Layout, _ velocity: CGPoint) {
    lastTouch = clock; let g = grab; grab = -1
    if g == -2 {
      glareTween = nil
      if reduced { f.glare = 0; glareSpring = nil }
      else { glareSpring = M.Spring(from: f.glare, to: 0, velocity: abs(f.glare) < 1 ? velocity.x / 140 : 0, duration: 0.9, damping: 0.6) }
      return
    }
    if g < 0 { return }
    var target = -1
    for i in 0..<2 where hypot(f.carryX - l.lens(i).x, f.carryY - l.lens(i).y) < l.lensR * 1.25 { target = i }
    if target >= 0 {
      carryTween = Tween(from: f.carryOn, to: 0, at: clock, duration: 0.16)
      pourInto(g, target)
    } else {
      if reduced {
        f.carryX = l.drop(g).x; f.carryY = l.drop(g).y; f.carryOn = 0; carryTween = nil
      } else {
        xSpring = M.Spring(from: f.carryX, to: l.drop(g).x, velocity: velocity.x, duration: 0.52, damping: 0.62)
        ySpring = M.Spring(from: f.carryY, to: l.drop(g).y, velocity: velocity.y, duration: 0.52, damping: 0.62)
      }
    }
  }
  func adjust(_ increment: Bool) {
    takeOver(); pourInto((f.shownTint + (increment ? 1 : 3)) % 4, 1)
  }
}
