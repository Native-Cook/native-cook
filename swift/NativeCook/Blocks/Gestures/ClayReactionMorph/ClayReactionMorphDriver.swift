import SwiftUI
import UIKit

// An analytical counterpart of Reanimated's duration springs and cubic-bezier timings.
struct ClayReactionMorphTrack {
  var from = 0.0, to = 0.0, start = 0.0, duration = 0.0, damping = 1.0
  var omega = 0.0, velocity = 0.0
  var curve = 0 // 0: cubic out (0.23,1,0.32,1), 1: in/out (0.77,0,0.175,1), 2: spring, 3: Reanimated default in/out quadratic
  func value(_ now: Double) -> Double {
    let t = max(0,now-start)
    if duration <= 0 || (curve != 2 && t >= duration) { return to }
    if curve == 2 {
      let x = from-to
      let displacement: Double, speed: Double
      if damping >= 1 {
        let envelope = exp(-omega*t)
        displacement = envelope*(x+(velocity+omega*x)*t)
        speed = -omega*displacement+envelope*(velocity+omega*x)
      } else {
        let w = omega*sqrt(1-damping*damping), envelope = exp(-damping*omega*t)
        displacement = envelope*(x*cos(w*t)+(velocity+damping*omega*x)/w*sin(w*t))
        speed = -damping*omega*displacement+envelope*((velocity+damping*omega*x)*cos(w*t)-w*x*sin(w*t))
      }
      let initial = omega*omega*x*x+velocity*velocity
      if initial == 0 || (omega*omega*displacement*displacement+speed*speed)/initial <= 6e-9 { return to }
      return to+displacement
    }
    let x = t/duration
    if curve == 3 {
      let y = x < 0.5 ? 2*x*x : 1-pow(-2*x+2,2)/2
      return from+(to-from)*y
    }
    let x1 = curve == 0 ? 0.23 : 0.77, x2 = curve == 0 ? 0.32 : 0.175
    var lo = 0.0, hi = 1.0
    for _ in 0..<30 {
      let u = (lo+hi)/2, v = 1-u
      let bx = 3*v*v*u*x1+3*v*u*u*x2+u*u*u
      if bx < x { lo = u } else { hi = u }
    }
    let u = (lo+hi)/2, v = 1-u
    let y = curve == 0 ? 3*v*v*u+3*v*u*u+u*u*u : 3*v*u*u+u*u*u
    return from+(to-from)*y
  }
  mutating func animate(_ end: Double, _ now: Double, _ span: Double, curve: Int = 0, damping: Double = 1) {
    let x = value(now)
    var v = self.curve == 2 ? (value(now+0.00001)-value(now-0.00001))/0.00002 : 0
    if (end > x && v < 0) || (end < x && v > 0) { v = 0 }
    self = Self(from:x,to:end,start:now,duration:span,damping:damping,velocity:v,curve:curve)
    if curve == 2 {
      // Same stiffness bisection, mass 4, energy threshold 6e-9, perceptual duration ×1.5.
      let d = span*1.5, x0 = x-end
      var lo = Double.ulpOfOne, hi = 8000.0
      for _ in 0..<100 {
        let k = (lo+hi)/2, w = sqrt(k/4)*damping
        let xt = (x0+(v+x0*w)*d)*exp(-w*d)
        let vt = xt*(-w)+(v+x0*w)*exp(-w*d)
        let e0 = 0.5*k*x0*x0+2*v*v
        let fraction = e0 > 0 ? (0.5*k*xt*xt+2*vt*vt)/e0 : 0
        if abs(fraction-6e-9) < 6e-12 { lo = k; hi = k; break }
        if fraction > 6e-9 { lo = k } else { hi = k }
      }
      omega = sqrt((lo+hi)/8)
      duration = d
    }
  }
  mutating func set(_ x: Double) { self = Self(from:x,to:x) }
}

final class ClayReactionMorphDriver: ObservableObject {
  typealias M = ClayReactionMorphMotion
  @Published var revision = 0
  @Published var badge = 1
  var epoch = Date.timeIntervalSinceReferenceDate
  var auto = false, reduced = false, looping = false, dragging = false, touching = false
  var moving = false, flying = false
  var autoStart = 0.0, touchStart = 0.0, resumeToken = 0, gestureToken = 0, flightToken = 0
  var startX = 0.0, reactionStart = 1.0, lastTick = 1
  var lastPoint = CGPoint.zero
  var tracks = Array(repeating: ClayReactionMorphTrack(), count: 8)
  init() { tracks[1].set(1) }
  var now: Double { Date.timeIntervalSinceReferenceDate-epoch }
  func configure(auto: Bool, reduced: Bool) {
    self.auto = auto && !reduced; self.reduced = reduced; looping = self.auto
    autoStart = now; moving = looping; revision += 1
  }
  func frame(_ time: Double) -> ClayReactionMorphFrame {
    if looping {
      var t = (time-autoStart).truncatingRemainder(dividingBy:M.loop)
      #if DEBUG
      if UserDefaults.standard.object(forKey:"ncPinTime") != nil { t = UserDefaults.standard.double(forKey:"ncPinTime") }
      #endif
      return M.frameAt(t)
    }
    let v = tracks.map { $0.value(time) }
    return ClayReactionMorphFrame(lift:v[0],r:v[1],hold:v[2],press:v[3],squash:v[4],lean:v[5],finger:v[6],fingerDx:v[7])
  }
  func animate(_ i: Int, _ value: Double, _ span: Double, curve: Int = 0, damping: Double = 1) {
    tracks[i].animate(value,now,span,curve:curve,damping:damping)
  }
  func takeOver() {
    resumeToken += 1
    guard looping else { return }
    let f = frame(now)
    let values = [f.lift,f.r,f.hold,f.press,f.squash,f.lean,f.finger,f.fingerDx]
    for i in values.indices { tracks[i].set(values[i]) }
    looping = false; animate(6,0,0.15,curve:3); animate(3,0,0.15,curve:3); animate(4,0,0.2,curve:3)
    if f.lift > 0.001 { sendHome() }
    else { animate(2,0,0.2,curve:3); animate(5,0,0.2,curve:3); animate(1,f.r.rounded(),0.2,curve:3) }
    moving = true; revision += 1
  }
  func later(_ seconds: Double, _ action: @escaping () -> Void) {
    DispatchQueue.main.asyncAfter(deadline:.now()+seconds,execute:action)
  }
  func begin(_ point: CGPoint, bubble: CGRect) {
    takeOver(); touching = true; gestureToken += 1
    lastPoint = point; touchStart = now
    guard bubble.contains(point) else { return }
    animate(3,1,0.12); moving = true; revision += 1
    let token = gestureToken
    later(0.28) { [weak self] in
      guard let self, self.touching, self.gestureToken == token else { return }
      self.flightToken += 1; self.flying = false; self.dragging = true
      let r = self.tracks[1].value(self.now)
      self.tracks[1].set(r); self.reactionStart = r; self.lastTick = Int(min(3,max(0,r)).rounded())
      self.startX = self.lastPoint.x
      self.animate(0,1,self.reduced ? 0.22 : 0.55,curve:self.reduced ? 0 : 2,damping:0.62)
      self.animate(2,1,0.2); self.animate(3,0,0.35,curve:2,damping:0.7)
      if !self.reduced {
        self.animate(4,-0.3,0.12)
        self.later(0.12) { [weak self] in
          guard let self, self.dragging, self.gestureToken == token else { return }
          self.animate(4,0,0.5,curve:2,damping:0.5)
        }
      }
      UIImpactFeedbackGenerator(style:.medium).impactOccurred(); self.revision += 1
    }
  }
  func change(_ point: CGPoint, translation: CGSize, velocity: CGVector) {
    lastPoint = point
    if !dragging {
      if hypot(translation.width,translation.height) > 10 { gestureToken += 1; animate(3,0,0.15) }
      return
    }
    var r = reactionStart+(point.x-startX)/72
    if r < 0 { r = -0.25*(1-exp(r/0.25)) }
    if r > 3 { r = 3+0.25*(1-exp(-(r-3)/0.25)) }
    tracks[1].set(r)
    let tick = Int(min(3,max(0,r)).rounded())
    if tick != lastTick { UISelectionFeedbackGenerator().selectionChanged(); lastTick = tick }
    if !reduced { animate(5,max(-1,min(1,velocity.dx/1400)),0.3,curve:2,damping:0.8) }
    revision += 1
  }
  func end() {
    touching = false; gestureToken += 1; animate(3,0,0.15)
    if dragging { dragging = false; sendHome() } else if !flying { scheduleResume(after:0,settle:0.2) }
    revision += 1
  }
  func sendHome() {
    flying = true; flightToken += 1; let token = flightToken
    let r = min(3,max(0,tracks[1].value(now))).rounded()
    animate(1,r,0.45,curve:2); animate(2,0,0.3); animate(5,0,0.4,curve:2)
    let span = (reduced ? 0.24 : 0.6)*max(0.3,min(1,tracks[0].value(now)))
    animate(0,0,span,curve:1)
    later(span) { [weak self] in
      guard let self, self.flightToken == token else { return }
      self.flying = false; self.badge = Int(r)
      if !self.reduced {
        self.animate(4,0.7,0.09)
        self.later(0.09) { [weak self] in
          guard let self, self.flightToken == token else { return }
          self.animate(4,0,0.65,curve:2,damping:0.35)
        }
      }
      UIImpactFeedbackGenerator(style:.light).impactOccurred()
      self.scheduleResume(after:0,settle:self.reduced ? 0 : 1.065)
    }
  }
  func scheduleResume(after delay: Double, settle: Double = 0) {
    resumeToken += 1; let token = resumeToken
    later(delay) { [weak self] in
      guard let self, self.resumeToken == token, !self.touching, !self.dragging else { return }
      self.later(settle) { [weak self] in
        guard let self, self.resumeToken == token, !self.touching, !self.flying else { return }
        self.moving = false; self.revision += 1
      }
      guard self.auto else { return }
      self.later(2) { [weak self] in
        guard let self, self.resumeToken == token, !self.touching else { return }
        let melt = abs(self.tracks[1].value(self.now)-1) > 0.001 ? 0.6 : 0
        self.animate(1,1,melt,curve:1); self.moving = true; self.revision += 1
        self.later(melt) { [weak self] in
          guard let self, self.resumeToken == token, !self.touching else { return }
          self.autoStart = self.now-M.restTime; self.badge = 1; self.looping = true; self.revision += 1
        }
      }
    }
  }
  func choose(_ delta: Int) {
    takeOver(); let r = Double(min(3,max(0,badge+delta))); badge = Int(r)
    animate(1,r,0.35,curve:1); moving = true; revision += 1
    scheduleResume(after:0,settle:0.35)
  }
}
