import SwiftUI
import UIKit

@MainActor final class ParkAssistScrubDriver: NSObject, ObservableObject {
  @Published var manual = false
  @Published var frame = ParkAssistScrubFrame()
  var autoPlay = false, reduced = false
  private var epoch = Date.timeIntervalSinceReferenceDate
  private var link: CADisplayLink?
  private var previous = 0.0, velocity = 0.0, lastTouch = -100.0, lastBeep = 0.0
  private var resumeAsked = false, parkedAt: Double?, exitAt: Double?, pausedAt: Double?
  private var hazardAt: Double?
  private var exitS = 0.0, exitParked = 0.0, exitAssist = 0.0
  private var fingerAt: Double?, fingerFrom = 0.0
  private var buttonDown = false, outDown = false, tapValid = true
  private var pressAt = 0.0, pressPoint = CGPoint.zero
  private let selection = UISelectionFeedbackGenerator()
  private let impact = UIImpactFeedbackGenerator(style:.light)
  private let notification = UINotificationFeedbackGenerator()
  private typealias M = ParkAssistScrubMotion

  func configure(autoPlay: Bool, reduced: Bool) {
    self.autoPlay = autoPlay; self.reduced = reduced
    manual = !autoPlay || reduced; frame = .init(); epoch = Date.timeIntervalSinceReferenceDate
    velocity = 0; exitAt = nil; parkedAt = nil; hazardAt = nil; fingerAt = nil; resumeAsked = false
    if manual { wake() } else { stop() }
  }
  func updateReduced(_ value: Bool) {
    guard value != reduced else { return }
    let now = Date.timeIntervalSinceReferenceDate
    if value && !manual { takeOver(now) }
    reduced = value
    if autoPlay && !value { epoch = now; manual = false; stop() }
    else { manual = true; wake() }
  }
  func displayed(_ date: Date) -> ParkAssistScrubFrame {
    if manual { return frame }
    var t = max(0,date.timeIntervalSinceReferenceDate-epoch).truncatingRemainder(dividingBy:M.loop)
    #if DEBUG
    if UserDefaults.standard.object(forKey:"ncPinTime") != nil {
      t = UserDefaults.standard.double(forKey:"ncPinTime")
    }
    #endif
    return M.frameAt(t)
  }
  func setActive(_ active: Bool) {
    let now = Date.timeIntervalSinceReferenceDate
    if !active { if pausedAt == nil { pausedAt = now }; stop() }
    else if let paused = pausedAt {
      let shift = now-paused
      epoch += shift; lastTouch += shift; lastBeep += shift
      if let value = parkedAt { parkedAt = value+shift }
      if let value = exitAt { exitAt = value+shift }
      if let value = fingerAt { fingerAt = value+shift }
      if let value = hazardAt { hazardAt = value+shift }
      pausedAt = nil
      if manual { wake() }
    }
  }
  func stop() { link?.invalidate(); link = nil }
  private func wake() {
    guard link == nil else { return }
    previous = Date.timeIntervalSinceReferenceDate
    let display = CADisplayLink(target:self,selector:#selector(tick))
    display.add(to:.main,forMode:.common); link = display
  }
  private func takeOver(_ now: Double) {
    lastTouch = now; resumeAsked = false
    if !manual {
      frame = displayed(Date(timeIntervalSinceReferenceDate:now)); manual = true
      fingerFrom = frame.finger; fingerAt = now
      frame.holding = 0; velocity = 0; frame.hazard = 0; parkedAt = nil; hazardAt = nil
      if frame.out > 0.5 { driveOut(now) }
      else { frame.out = 0; frame.parked = frame.s > 0.999 ? 1 : 0 }
    }
    wake()
  }
  func begin(inButton: Bool, point: CGPoint = .zero) {
    let now = Date.timeIntervalSinceReferenceDate
    pressAt = now; pressPoint = point; tapValid = true
    takeOver(now); buttonDown = inButton; outDown = inButton && frame.parked > 0.5
    if inButton && frame.parked <= 0.5 && frame.out != 1 {
      frame.holding = 1; impact.impactOccurred()
    }
  }
  func move(_ point: CGPoint) {
    if hypot(point.x-pressPoint.x,point.y-pressPoint.y) > 10 { tapValid = false }
  }
  func end(cancelled: Bool = false) {
    let now = Date.timeIntervalSinceReferenceDate
    lastTouch = now; frame.holding = 0
    if buttonDown && outDown && !cancelled && tapValid && now-pressAt <= 0.5 && frame.parked > 0.5 { driveOut(now) }
    buttonDown = false; outDown = false; wake()
  }
  // VoiceOver has no sustained touch; repeat the same hold/release control through named actions.
  func accessibilityHold() { if frame.holding == 1 { end() } else { begin(inButton:true) } }
  func accessibilityOut() { let now = Date.timeIntervalSinceReferenceDate; takeOver(now); driveOut(now) }
  private func driveOut(_ now: Double) {
    exitAt = now; exitS = frame.s; exitParked = frame.parked; exitAssist = frame.assist
    frame.out = 1; parkedAt = nil
  }
  @objc private func tick() {
    let now = Date.timeIntervalSinceReferenceDate, dt = min(0.05,max(0,now-previous)); previous = now
    var f = frame
    if let exit = exitAt {
      let u = M.seg(now,exit,exit+(reduced ? 0.6 : 1.8))
      f.s = exitS*(1-M.driveEase(u))
      let fade = 1-M.timing(M.seg(now,exit,exit+0.3))
      f.parked = exitParked*fade; f.assist = exitAssist*fade
      if u >= 1 { exitAt = nil; f.out = 0; f.s = 0 }
    } else if let park = parkedAt {
      f.parked = M.timing(M.seg(now,park,park+0.3))
    } else if f.parked < 0.5 {
      let want = f.holding == 1 ? M.speed : 0
      velocity += (want-velocity)*min(1,dt*(want > 0 ? 5 : 9))
      let value = f.s+velocity*dt*(1-0.65*M.smooth(0.8,1,f.s))
      if value >= 0.999 {
        f.s = 1; velocity = 0; parkedAt = now; hazardAt = now; notification.notificationOccurred(.success)
      } else { f.s = max(0,value) }
      if f.holding == 1 { f.assist = min(1,f.assist+dt*4) }
      let gap = M.gapAt(f.s)
      if f.holding == 1 && velocity > 0.02 && gap < 1.4 && now-lastBeep > 0.1+gap*0.45 {
        lastBeep = now; selection.selectionChanged()
      }
    }
    if let start = hazardAt {
      let u = M.seg(now,start,start+0.7)
      f.hazard = u >= 1 ? 0 : reduced ? 1 : max(0,sin(.pi*min(1,u*2)))+max(0,sin(.pi*max(0,u*2-1)))
    }
    if let start = fingerAt { f.finger = fingerFrom*(1-M.timing(M.seg(now,start,start+0.15))) }
    let moving = abs(velocity) > 0.01 || f.out == 1
    let wantBrake = !moving && f.s > 0.02 ? 1.0 : 0.0
    f.brake += (wantBrake-f.brake)*min(1,dt*12)
    if f.holding == 1 || moving { lastTouch = now }
    frame = f
    if autoPlay && !reduced && now-lastTouch >= 2 {
      if !resumeAsked { resumeAsked = true; if f.s > 0.001 { driveOut(now) } }
      else if f.out == 0 && f.s < 0.001 {
        resumeAsked = false; velocity = 0; epoch = now; manual = false; stop()
      }
    } else if !autoPlay || reduced {
      if !moving && f.holding == 0 && f.finger < 0.001 && f.hazard == 0 && abs(f.brake-wantBrake) < 0.001 && (parkedAt == nil || f.parked >= 1) {
        stop()
      }
    }
  }
}
