import SwiftUI

final class ClayRelightSettingsState: ObservableObject {
  typealias Motion = ClayRelightSettingsMotion
  struct Tween {
    var from: Double, to: Double, start: Double, duration: Double
    var curve = 0, velocity = 0.0, omega = 0.0
    func value(_ now: Double) -> Double {
      let x = Motion.seg(now,start,start+max(0.0001,duration))
      if x <= 0 { return from }; if x >= 1 { return to }
      let k: Double
      switch curve {
      case 1: k = Motion.bezier(x,0.23,1,0.32,1)
      case 2: k = Motion.bezier(x,0.77,0,0.175,1)
      case 3: k = Motion.bezier(x,0.45,0,0.55,1)
      case 5: k = x < 0.5 ? 2*x*x : 1-pow(-2*x+2,2)/2
      case 4:
        let t = now-start
        let v = to+(from-to+(velocity+omega*(from-to))*t)*exp(-omega*t)
        return min(max(v,min(from,to)),max(from,to))
      default: k = x
      }
      return from+(to-from)*k
    }
    // Reanimated 4 duration spring: mass 4, critical damping, energy threshold 6e-9,
    // perceptual duration * 1.5, and its stiffness bisection. Solve once at release.
    mutating func configureSpring() {
      let x0 = from-to, requestedVelocity = velocity
      if (to > from && velocity < 0) || (to < from && velocity > 0) { velocity = 0 }
      let settling = duration*1.5
      func fraction(_ stiffness: Double, _ t: Double) -> Double {
        let w = sqrt(stiffness/4), a = velocity+x0*w, decay = exp(-w*t)
        let x = (x0+a*t)*decay, v = decay*(a-w*(x0+a*t))
        let initial = 0.5*stiffness*x0*x0+2*velocity*velocity
        return initial == 0 ? 0 : (0.5*stiffness*x*x+2*v*v)/initial
      }
      var lo = Double.ulpOfOne, hi = 8000.0, stiffness = 4000.0
      for _ in 0..<100 {
        let diff = fraction(stiffness,settling)-6e-9
        if abs(diff) <= 6e-12 { break }
        if diff > 0 { lo = stiffness } else { hi = stiffness }
        stiffness = (lo+hi)/2
      }
      omega = sqrt(stiffness/4)
      let initialEnergy = 0.5*stiffness*x0*x0+2*requestedVelocity*requestedVelocity
      func finished(_ t: Double) -> Bool {
        let a = velocity+omega*x0, decay = exp(-omega*t)
        let x = (x0+a*t)*decay, v = decay*(a-omega*(x0+a*t))
        let position = to+x
        return position < min(from,to) || position > max(from,to) || initialEnergy == 0 ||
          (0.5*stiffness*x*x+2*v*v)/initialEnergy <= 6e-9
      }
      lo = 0; hi = settling
      for _ in 0..<40 {
        let mid = (lo+hi)/2
        if finished(mid) { hi = mid } else { lo = mid }
      }
      duration = hi
    }
    var activityEnd: Double {
      if abs(from-to) <= 0.002 { return start }
      var lo = start, hi = end
      for _ in 0..<30 {
        let mid = (lo+hi)/2
        if abs(value(mid)-to) <= 0.002 { hi = mid } else { lo = mid }
      }
      return hi
    }
    var end: Double { start+duration }
    static func still(_ v: Double) -> Self { Self(from:v,to:v,start:0,duration:0) }
  }
  @Published var reducedRest = true
  @Published var revision = 0 { didSet { reducedRest = false } }
  private var pausedAt: Double?
  private var pausedDuration = 0.0
  let epoch = Date.timeIntervalSinceReferenceDate
  let autoPlay: Bool
  var manual = false, dragging = false, themeOn = false
  var lastTouch = -100.0, dragStart = 0.0, on = 0.0, fingerAt = 0.0, fingerX = 0.0
  var light = Tween.still(0), chrome = Tween.still(0), knob = Tween.still(0)
  var press = Tween.still(0), grab = Tween.still(0), finger = Tween.still(0)
  init(autoPlay: Bool) { self.autoPlay = autoPlay }
  func time(_ date: Date = Date()) -> Double {
    (pausedAt ?? date.timeIntervalSinceReferenceDate)-epoch-pausedDuration
  }
  func setActive(_ active: Bool) {
    let now = Date.timeIntervalSinceReferenceDate
    if !active && pausedAt == nil { pausedAt = now }
    else if active, let began = pausedAt { pausedDuration += now-began; pausedAt = nil }
  }
  var reducedAnimationEnd: Double { [light.end,chrome.end,knob.end,press.end,grab.end,finger.end].max() ?? 0 }
  var pin: Double? {
    #if DEBUG
    if UserDefaults.standard.object(forKey:"ncPinTime") != nil {
      return UserDefaults.standard.double(forKey:"ncPinTime")
    }
    #endif
    return nil
  }
  func isUserDriven(_ now: Double, reduced: Bool) -> Bool {
    guard manual else { return false }
    let resume = max(lastTouch,light.activityEnd)+2
    let duration = on == 1 || light.to > 0.002 ? 0.5+2.5*light.to : 0
    return !(autoPlay && !reduced && !dragging && now >= resume+duration)
  }
  func snapshot(_ now: Double, reduced: Bool) -> Motion.Frame {
    if !manual {
      return autoPlay && !reduced ? Motion.frameAt(pin ?? now.truncatingRemainder(dividingBy:Motion.loop)) : Motion.Frame()
    }
    var f = Motion.Frame(m:light.value(now),theme:chrome.value(now),on:on,knob:knob.value(now),
      press:press.value(now),finger:finger.value(now),fingerAt:fingerAt,fingerX:fingerX,grab:grab.value(now))
    // The idle countdown starts after the evening rests, then brings the day back before replay.
    let resume = max(lastTouch,light.activityEnd)+2
    if autoPlay && !reduced && !dragging && now > resume {
      let duration = on == 1 || light.to > 0.002 ? 0.5+2.5*light.to : 0
      if now >= resume+duration {
        return Motion.frameAt((now-resume-duration).truncatingRemainder(dividingBy:Motion.loop))
      }
      let back = Tween(from:light.to,to:0,start:resume,duration:duration,curve:3)
      f.m = back.value(now); f.on = 0
      f.knob = Tween(from:knob.to,to:0,start:resume,duration:0.28,curve:1).value(now)
      f.theme = themeDuring(back, initial:chrome.to, initialOn:themeOn, at:now)
    }
    return f
  }
  func themeDuring(_ tween: Tween, initial: Double, initialOn: Bool, at now: Double) -> Double {
    let threshold = initialOn ? Motion.themeDown : Motion.themeUp
    for i in 0...300 {
      let t = tween.start+tween.duration*Double(i)/300
      let m = tween.value(t)
      if initialOn ? m < threshold : m > threshold {
        return Tween(from:initial,to:initialOn ? 0 : 1,start:t,duration:0.3,curve:2).value(now)
      }
    }
    return initial
  }
  func takeOver(_ now: Double, reduced: Bool) {
    let f = snapshot(now,reduced:reduced)
    if !manual || (autoPlay && now > max(lastTouch,light.activityEnd)+2 && !dragging) {
      light = .still(f.m); chrome = .still(f.theme); knob = .still(f.knob)
      on = f.on; themeOn = f.theme > 0.5; fingerAt = f.fingerAt; fingerX = f.fingerX
      finger = Tween(from:f.finger,to:0,start:now,duration:0.15)
      press = .still(0); grab = .still(0)
    }
    manual = true; lastTouch = now
  }
  func setTarget(_ dest: Double, velocity: Double? = nil, reduced: Bool) {
    let now = time()
    takeOver(now,reduced:reduced)
    let startM = light.value(now)
    on = dest
    knob = Tween(from:knob.value(now),to:dest,start:now,duration:0.26,curve:1)
    light = Tween(from:startM,to:dest,start:now,
      duration:reduced ? 0.4 : velocity == nil ? 0.5+2.5*abs(dest-startM) : 1.8,
      curve:reduced ? 5 : velocity == nil ? 3 : 4, velocity:velocity ?? 0)
    if light.curve == 4 { light.configureSpring() }
    let initial = chrome.value(now), state = themeOn
    for i in 0...300 {
      let t = now+light.duration*Double(i)/300, m = light.value(t)
      if state ? m < Motion.themeDown : m > Motion.themeUp {
        chrome = Tween(from:initial,to:state ? 0 : 1,start:t,duration:0.3,curve:2)
        themeOn = !state; break
      }
    }
    revision += 1
  }
  func switchPress(_ down: Bool, reduced: Bool) {
    let now = time(); takeOver(now,reduced:reduced)
    press = Tween(from:press.value(now),to:down ? 1 : 0,start:now,duration:down ? 0.1 : 0.15,curve:1)
    revision += 1
  }
  func beginDrag(reduced: Bool) {
    let now = time(); takeOver(now,reduced:reduced); dragging = true
    dragStart = light.value(now); light = .still(dragStart)
    grab = Tween(from:grab.value(now),to:1,start:now,duration:0.15)
    revision += 1
  }
  func drag(_ dx: Double, span: Double) {
    let now = time()
    var v = dragStart+dx/span
    if v < 0 { v = -0.06*(1-exp(v/0.06)) }
    if v > 1 { v = 1+0.06*(1-exp(-(v-1)/0.06)) }
    light = .still(v); lastTouch = now
    if (!themeOn && v > Motion.themeUp) || (themeOn && v < Motion.themeDown) {
      themeOn.toggle()
      chrome = Tween(from:chrome.value(now),to:themeOn ? 1 : 0,start:now,duration:0.3,curve:2)
    }
    revision += 1
  }
  func endDrag(_ velocity: Double, span: Double, reduced: Bool) {
    let now = time(), v = velocity/span
    let dest = light.value(now)+0.25*v > 0.5 ? 1.0 : 0.0
    // Keep dragging true through setTarget so it cannot be mistaken for an idle resume.
    setTarget(dest,velocity:v,reduced:reduced)
    dragging = false; lastTouch = now
    grab = Tween(from:grab.value(now),to:0,start:now,duration:0.25)
    revision += 1
  }
}
