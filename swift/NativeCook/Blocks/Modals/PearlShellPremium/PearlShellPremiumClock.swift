import SwiftUI
import UIKit

struct PearlShellPremiumTween {
  var from: Double, to: Double, at: Double, duration: Double
  // 0: Reanimated default in-out quadratic; 1: ease-out; 2: lift curve.
  var curve = 0
  func value(_ now: Double) -> Double {
    let k=PearlShellPremiumMotion.clamp((now-at)/duration)
    let e: Double
    if curve==1 { e=PearlShellPremiumMotion.bezier(k,0.23,1,0.32,1) }
    else if curve==2 { e=PearlShellPremiumMotion.bezier(k,0.77,0,0.175,1) }
    else { e = k<0.5 ? 2*k*k : 1-pow(-2*k+2,2)/2 }
    return from+(to-from)*e
  }
  func done(_ now: Double) -> Bool { now-at>=duration }
}

final class PearlShellPremiumClock: ObservableObject {
  typealias M = PearlShellPremiumMotion
  @Published var running = false
  private var date: Date?
  private var now=0.0, autoStart=0.0, lastTouch = -100.0
  private var automatic=true, resumeAsked=false, dragging=false
  private var reduced=false, autoPlay=false
  private var frame=M.Frame()
  private var angle: PearlShellPremiumTween?, glow: PearlShellPremiumTween?, lift: PearlShellPremiumTween?
  private var yaw: PearlShellPremiumTween?, finger: PearlShellPremiumTween?, fingerPress: PearlShellPremiumTween?
  private var spring: PearlShellPremiumSpring?
  private var pulseAt: Double?, closingHaptic=false
  private var openFrom=M.closed, panStarted=false
  private let light=UIImpactFeedbackGenerator(style:.light)
  private let success=UINotificationFeedbackGenerator()
  private let select=UISelectionFeedbackGenerator()

  func reset(_ auto: Bool, _ reduced: Bool) {
    self.autoPlay=auto; self.reduced=reduced
    automatic=auto && !reduced
    date=nil;now=0;autoStart=0;lastTouch = -100
    frame=M.Frame();spring=nil;angle=nil;glow=nil;lift=nil;yaw=nil;finger=nil;pulseAt=nil
    resumeAsked=false;dragging=false;fingerPress=nil;running=true
  }
  func configure(_ auto: Bool, _ reduced: Bool) {
    self.autoPlay=auto;self.reduced=reduced;autoStart=now;date=nil
    if auto && !reduced { automatic=true;running=true;return }
    automatic=false;spring=nil;closingHaptic=false;pulseAt=nil
    let on=frame.open>0.55
    angle = .init(from:frame.open,to:on ? M.open : M.closed,at:now,duration:0.2)
    glow = .init(from:frame.glow,to:on ? 1 : 0,at:now,duration:0.2)
    lift = .init(from:frame.lift,to:on ? M.lift : 0,at:now,duration:0.2)
    yaw = .init(from:frame.yaw,to:0,at:now,duration:0.2)
    finger = .init(from:frame.finger,to:0,at:now,duration:0.2)
    fingerPress = .init(from:frame.fingerPress,to:0,at:now,duration:0.2)
    running=true
  }
  func activeChanged() { date=nil }
  func sample(_ date: Date, _ pin: Double?) -> M.Frame {
    if let pin { return M.frameAt(pin) }
    // Wall-clock time for recording on slow simulators; only physics has a 64 ms cap.
    let dt=min(0.5,max(0,self.date.map { date.timeIntervalSince($0) } ?? 0.016))
    self.date=date; now+=dt
    if automatic {
      spring=nil;angle=nil;glow=nil;lift=nil;yaw=nil;finger=nil;fingerPress=nil;pulseAt=nil;closingHaptic=false
      frame=M.frameAt((now-autoStart).truncatingRemainder(dividingBy:M.loop))
      return frame
    }
    if var s=spring {
      s.step(dt);frame.open=s.value
      spring=s.finished ? nil : s
      if s.finished && closingHaptic { closingHaptic=false;light.impactOccurred() }
    }
    if let v=angle { frame.open=v.value(now);if v.done(now) { angle=nil } }
    if let v=glow { frame.glow=v.value(now);if v.done(now) { glow=nil } }
    if let v=lift { frame.lift=v.value(now);if v.done(now) { lift=nil } }
    if let v=yaw { frame.yaw=v.value(now);if v.done(now) { yaw=nil } }
    if let v=finger { frame.finger=v.value(now);if v.done(now) { finger=nil } }
    if let v=fingerPress { frame.fingerPress=v.value(now);if v.done(now) { fingerPress=nil } }
    if let p=pulseAt {
      if now-p>=0.12 {
        glow = .init(from:1.4,to:1,at:p+0.12,duration:0.6,curve:1)
        frame.glow=glow!.value(now);pulseAt=nil
      }
    }
    // The RN ticker keeps the sheen and caustics running in manual/reduced mode;
    // only the scripted opening and breathing stop under Reduce Motion.
    frame.spin=(frame.spin+M.tau/M.loop*dt).truncatingRemainder(dividingBy:M.tau)
    frame.phase=(frame.phase+M.tau*2/M.loop*dt).truncatingRemainder(dividingBy:M.tau)
    if autoPlay && !reduced && !dragging && now-lastTouch>=2 {
      if !resumeAsked {
        resumeAsked=true
        if frame.open>M.closed+0.01 || frame.glow>0.001 { setOpen(false,0) }
        yaw = .init(from:frame.yaw,to:0,at:now,duration:0.6)
      } else if abs(frame.open-M.closed)<0.004 && frame.glow<0.002 && frame.lift<0.002 && abs(frame.yaw)<0.002 {
        frame.spin=0;frame.phase=0;autoStart=now;automatic=true;resumeAsked=false
      }
    }
    return frame
  }
  private func takeOver() {
    lastTouch=now;resumeAsked=false
    if automatic {
      automatic=false
      finger = .init(from:frame.finger,to:0,at:now,duration:0.15)
      frame.fingerPress=0
    }
    if !running { date=nil;running=true }
  }
  func setOpen(_ on: Bool, _ v: Double) {
    pulseAt=nil
    if reduced {
      spring=nil;closingHaptic=false
      angle = .init(from:frame.open,to:on ? M.open : M.closed,at:now,duration:0.25)
      glow = .init(from:frame.glow,to:on ? 1 : 0,at:now,duration:0.25)
      lift = .init(from:frame.lift,to:on ? M.lift : 0,at:now,duration:0.25)
      return
    }
    let previousVelocity=spring?.velocity ?? 0
    angle=nil
    spring = .init(frame.open,on ? M.open : M.closed,on ? 0.7 : 0.65,on ? 0.55 : 0.62,previousVelocity+v,v)
    closingHaptic = !on
    glow = .init(from:frame.glow,to:on ? 1 : 0,at:now,duration:on ? 0.8 : 0.5,curve:1)
    lift = .init(from:frame.lift,to:on ? M.lift : 0,at:now,duration:on ? 1 : 0.45,curve:on ? 2 : 1)
    if on { success.notificationOccurred(.success) }
  }
  func touch(_ state: Int, _ translation: CGPoint, _ velocity: Double, _ scale: Double) {
    switch state {
    case 0:
      takeOver();panStarted=false
    case 1:
      lastTouch=now
      if !panStarted && hypot(translation.x,translation.y)>=2 {
        panStarted=true;dragging=true;openFrom=frame.open;spring=nil;angle=nil;closingHaptic=false
      }
      guard panStarted else { return }
      let raw=openFrom-translation.y/scale*0.95, lo=M.closed, hi=M.open+0.1
      frame.open = raw<lo ? lo-(lo-raw)/(1+(lo-raw)/0.08) : raw>hi ? hi+(raw-hi)/(1+(raw-hi)/0.15) : raw
      if !reduced { glow=nil;pulseAt=nil;frame.glow=M.clamp((frame.open-0.4)/0.6)*0.6 }
    case 2:
      if panStarted {
        let v = -velocity/scale*0.95
        setOpen(v>1.5 || (v > -1.5 && frame.open>0.55),v)
      } else {
        if frame.open>0.8 && !reduced {
          glow = .init(from:frame.glow,to:1.4,at:now,duration:0.12,curve:1)
          pulseAt=now;light.impactOccurred()
        } else { setOpen(true,0) }
      }
      dragging=false;lastTouch=now
    default: dragging=false;lastTouch=now
    }
    objectWillChange.send()
  }
  func accessibilityToggle() { takeOver();setOpen(frame.open<0.55,0);objectWillChange.send() }
  func selectPlan() { select.selectionChanged() }
}
