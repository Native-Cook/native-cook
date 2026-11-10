import UIKit

struct PinDropLocationTween {
  var from, to, start, duration: Double
  // 0 Reanimated default (in/out quad), 1 cubic-bezier(.77,0,.175,1),
  // 2 out cubic, 3 in cubic, 4 PressScale's cubic-bezier(.23,1,.32,1).
  var curve = 0
  var completion = 0
  func value(_ now: Double) -> Double {
    let p = PinDropLocationMotion.seg(now,start,start+duration)
    let e: Double
    switch curve {
    case 1: e = PinDropLocationMotion.bezier(p,0.77,0,0.175,1)
    case 2: e = PinDropLocationMotion.easeOut(p)
    case 3: e = p*p*p
    case 4: e = PinDropLocationMotion.bezier(p,0.23,1,0.32,1)
    default: e = p < 0.5 ? 2*p*p : 1-pow(-2*p+2,2)/2
    }
    return from+(to-from)*e
  }
}

final class PinDropLocationDriver {
  typealias M = PinDropLocationMotion
  var state = PinDropLocationFrame()
  var now = 0.0, autoStart = 0.0, lastTouch = -100.0, landedAt = -1.0
  var auto = false, reduced = false, manual = true, resuming = false, dragging = false
  var initialized = false, allowed = false
  var animations: [String: PinDropLocationTween] = [:]
  var fingerX = 0.0, fingerY = 0.0
  private var buttons = [0.0,0.0]
  private var pressed = -1, mapTouch = false
  private var start = CGPoint.zero, lastPoint = CGPoint.zero
  private var lastPointAt = 0.0, velocityX = 0.0, lon0 = 0.0, lat0 = 0.0
  private var decayVelocity = 0.0, decayAt = 0.0
  private let light = UIImpactFeedbackGenerator(style: .light)
  private let selection = UISelectionFeedbackGenerator()
  private let success = UINotificationFeedbackGenerator()
  func configure(_ autoPlay: Bool,_ reduce: Bool) {
    let nextAuto = autoPlay && !reduce
    guard !initialized || auto != nextAuto || reduced != reduce else { return }
    auto = nextAuto; reduced = reduce; manual = !auto; initialized = true
    autoStart = now; state = .init(); animations.removeAll()
    // RN resets animated channels when Reduce Motion changes, retaining the React copy state.
    landedAt = -1; decayVelocity = 0; dragging = false; resuming = false
  }
  func get(_ key: String) -> Double {
    switch key {
    case "bend": return state.bend
    case "lon": return state.lon
    case "lat": return state.lat
    case "fall": return state.fall
    case "lights": return state.lights
    case "zoom": return state.zoom
    case "places": return state.places
    case "finger": return state.finger
    case "b0": return buttons[0]
    default: return buttons[1]
    }
  }
  func set(_ key: String,_ value: Double) {
    switch key {
    case "bend": state.bend = value
    case "lon": state.lon = value
    case "lat": state.lat = value
    case "fall": state.fall = value
    case "lights": state.lights = value
    case "zoom": state.zoom = value
    case "places": state.places = value
    case "finger": state.finger = value
    case "b0": buttons[0] = value
    default: buttons[1] = value
    }
  }
  func animate(_ key: String,_ value: Double,_ duration: Double,_ curve: Int = 0,_ delay: Double = 0,_ completion: Int = 0) {
    if key == "lon" { decayVelocity = 0 }
    animations[key] = .init(from: get(key),to: value,start: now+delay,duration: duration,curve: curve,completion: completion)
  }
  func land() { landedAt = now; state.lights = 1; light.impactOccurred() }
  func dropIn(_ delay: Double) {
    if reduced { state.fall = 1; land(); return }
    animate("fall",1,0.42,3,delay,1)
  }
  func hop() {
    if reduced { return }
    animate("fall",0.82,0.22,2,0,2)
    selection.selectionChanged()
  }
  func setAllow(_ open: Bool) {
    animate("zoom",open ? 1 : 0,reduced ? 0.2 : 0.9,reduced ? 0 : 1)
    animate("places",open ? 1 : 0,reduced ? 0.2 : (open ? 0.9 : 0.3),reduced ? 0 : 2)
    allowed = open
  }
  func takeOver() {
    lastTouch = now; resuming = false
    if manual { return }
    manual = true
    animate("finger",0,0.15); state.press = 0
    landedAt = state.since >= 0 ? now-state.since : -1
    if state.bend > 0.001 && !dragging { animate("bend",0,0.4) }
    if state.fall < 0.999 && !dragging { dropIn(0.2) }
    if state.zoom > 0.001 && state.zoom < 0.999 { setAllow(state.zoom > 0.5) }
  }
  func step(_ dt: Double,_ l: PinDropLocationLayout,_ pinned: Double?) {
    if let t = pinned {
      state = M.frameAt(t); allowed = t >= 5.4 && t < 8
      placeFinger(l); return
    }
    // Only the physical decay step is capped at 64 ms. The script uses wall time.
    now += min(0.5,max(0,dt))
    for (key,tween) in animations {
      if now < tween.start { continue }
      set(key,tween.value(now))
      if now >= tween.start+tween.duration {
        animations.removeValue(forKey: key)
        if tween.completion == 1 { land() }
        if tween.completion == 2 { animate("fall",1,0.26,3,0,1) }
      }
    }
    if !manual && auto {
      let t = (now-autoStart).truncatingRemainder(dividingBy: M.loop)
      state = M.frameAt(t); allowed = t >= 5.4 && t < 8
      placeFinger(l)
    } else {
      if dt > 0 && abs(decayVelocity) > 0 {
        // Reanimated rigidDecay: velocity *= exp(-(1-.994)*elapsedMs*.1).
        decayVelocity *= exp(-0.6*(now-decayAt))
        state.lon += decayVelocity*min(0.064,max(0,dt))
        if abs(decayVelocity) < 1 { decayVelocity = 0 }
      }
      state.since = landedAt >= 0 ? now-landedAt : -1
      if auto && !dragging && now-lastTouch >= 2 {
        if !resuming {
          resuming = true
          if state.zoom > 0.001 { setAllow(false) }
          for key in ["lon","lat","lights"] { animate(key,0,1.2,1) }
          animate("bend",0,0.3)
        } else if state.zoom < 0.001 && state.bend < 0.001 && abs(state.lon) < 0.001 && abs(state.lat) < 0.001 && state.fall > 0.999 && state.lights < 0.001 && now-lastTouch > 3.4 {
          resuming = false; autoStart = now; manual = false
        }
      }
    }
  }
  private func placeFinger(_ l: PinDropLocationLayout) {
    if state.fingerAt == 1 { fingerX = l.w/2; fingerY = l.allow.midY }
    else if state.fingerU < 0 { fingerX = l.cx+60; fingerY = l.cy-40+60*(state.fingerU+1) }
    else { fingerX = l.cx+60-120*state.fingerU; fingerY = l.cy+20+22*sin(.pi*state.fingerU) }
  }
  var moving: Bool {
    (!manual && auto) || !animations.isEmpty || decayVelocity != 0 || (auto && manual)
      || (!reduced && landedAt >= 0) // city lights twinkle after landing
  }
  func begin(_ p: CGPoint,_ l: PinDropLocationLayout) {
    pressed = -1; mapTouch = false; start = p; lastPoint = p; lastPointAt = now; velocityX = 0
    if l.skip.contains(p) { pressed = 0 }
    else if l.allow.contains(p) { pressed = 1 }
    if pressed >= 0 { animate("b\(pressed)",1,0.15,4); return }
    mapTouch = l.map.contains(p)
    if mapTouch { takeOver() }
  }
  func move(_ p: CGPoint,_ l: PinDropLocationLayout) {
    guard mapTouch else { return }
    let dx=p.x-start.x, dy=p.y-start.y
    lastTouch = now
    let elapsed = now-lastPointAt
    if elapsed > 0 { velocityX = (p.x-lastPoint.x)/elapsed }
    lastPoint = p; lastPointAt = now
    if !dragging && hypot(dx,dy) >= 6 {
      if state.zoom > 0.01 { setAllow(false) }
      dragging = true; lon0 = state.lon; lat0 = state.lat
      if !reduced {
        animate("bend",1,0.38,2); animate("fall",0,0.34,3); selection.selectionChanged()
      }
    }
    if dragging {
      animations.removeValue(forKey: "lon"); animations.removeValue(forKey: "lat"); decayVelocity = 0
      state.lon = lon0-dx/l.radius; state.lat = max(-0.9,min(0.9,lat0+dy/l.radius))
    }
  }
  func end(_ p: CGPoint,_ l: PinDropLocationLayout,_ ok: Bool,_ nativeVelocityX: Double? = nil) {
    if pressed >= 0 {
      let index = pressed; animate("b\(index)",0,0.15,4); pressed = -1
      let box = index == 0 ? l.skip : l.allow
      if index == 1 && ok && box.insetBy(dx: -16,dy: -16).contains(p) && !allowed { allow() }
      return
    }
    guard mapTouch else { return }; mapTouch = false; lastTouch = now
    if dragging {
      if ok {
        if !reduced {
          let releaseVelocity=nativeVelocityX ?? (now-lastPointAt > 0.1 ? 0 : velocityX)
          decayVelocity = -releaseVelocity/l.radius; decayAt = now
        }
        animate("bend",0,reduced ? 0.001 : 0.52,1,reduced ? 0 : 0.25)
        dropIn(reduced ? 0 : 0.52)
      }
      // RNGH's onFinalize runs after onEnd too; preserve its cancellation guard.
      if state.fall < 0.999 && state.bend > 0.5 { animate("bend",0,0.4); dropIn(0.3) }
      dragging = false
    } else if ok && hypot(p.x-start.x,p.y-start.y) <= 8 {
      takeOver()
      if state.fall > 0.999 && state.bend < 0.01 { hop() }
    }
  }
  func allow() { takeOver(); setAllow(true); success.notificationOccurred(.success) }
  func accessibility(_ direction: Int) {
    takeOver()
    if direction == 0 { hop() } else { animate("lon",state.lon+Double(direction)*0.6,0.4) }
  }
  func buttonAmount(_ i: Int) -> Double { buttons[i] }
}
