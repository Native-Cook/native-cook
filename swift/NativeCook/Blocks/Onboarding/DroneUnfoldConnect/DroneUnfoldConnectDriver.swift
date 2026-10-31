import SwiftUI
import UIKit

// The scripted loop is pure. Only manual input integrates the reference's mass-following spring.
final class DroneUnfoldConnectDriver: ObservableObject {
  typealias M = DroneUnfoldConnectMotion
  @Published var manual = M.Frame()
  @Published var stage = 0
  @Published var taken = false
  var inactiveAt: Date?
  func activity(_ active: Bool) {
    let now=Date()
    if !active { if inactiveAt == nil { inactiveAt=now }; return }
    if let paused=inactiveAt {
      let gap=now.timeIntervalSince(paused)
      origin=origin.addingTimeInterval(gap); actionStart=actionStart.addingTimeInterval(gap)
      lastTouch=lastTouch.addingTimeInterval(gap); takeoverStart=takeoverStart.addingTimeInterval(gap)
      releaseStart=releaseStart?.addingTimeInterval(gap); lastDate=now; inactiveAt=nil
    }
  }
  var origin = Date()
  var lastDate = Date()
  var lastTouch = Date.distantPast
  var actionStart = Date()
  var action = 0 // 1 connect, 2 land, 3 finish a connection taken over from the loop
  var base = M.Frame()
  var velocities = [0.0,0.0,0.0]
  var releaseStart: Date?
  var takeoverSpring = false
  var releaseX = 0.0, releaseY = 0.0
  var touchZone = 0
  var didLight = false, didDone = false
  var takeoverStart = Date()
  var takeoverFinger = 0.0, takeoverPress = 0.0
  func time(_ now: Date) -> Double {
    #if DEBUG
    if UserDefaults.standard.object(forKey:"ncPinTime") != nil { return UserDefaults.standard.double(forKey:"ncPinTime") }
    #endif
    return max(0,now.timeIntervalSince(origin)).truncatingRemainder(dividingBy:M.loop)
  }
  func frame(_ now: Date, auto: Bool) -> M.Frame { auto && !taken ? M.frameAt(time(now)) : manual }
  func currentStage(_ now: Date, auto: Bool) -> Int { auto && !taken ? M.frameAt(time(now)).stage(at:time(now)) : stage }
  func takeOver(_ now: Date, auto: Bool) {
    lastTouch=now
    if !auto || taken { return }
    let t=time(now)
    manual=M.frameAt(t); stage=manual.stage(at:t); taken=true
    takeoverStart=now; takeoverFinger=manual.finger; takeoverPress=manual.pressConnect
    velocities=[0,0,0]
    release(now,takeover:true)
    if stage == 1 && manual.connected < 0.5 && manual.unfold > 0.001 { begin(3,now) }
    // Complete an interrupted landing too; unlike a detached animation, a sampled loop has no callback.
    if stage == 3 { begin(2,now) }
  }
  func begin(_ kind: Int, _ now: Date) {
    action=kind; actionStart=now; base=manual; didLight=false; didDone=false
    stage=kind == 2 ? 3 : 1
  }
  func connect(_ now: Date, auto: Bool) {
    takeOver(now,auto:auto)
    if stage == 0 { begin(1,now) }
  }
  func land(_ now: Date, auto: Bool) {
    takeOver(now,auto:auto)
    if stage == 2 { begin(2,now); release(now,takeover:true) }
  }
  func release(_ now: Date, takeover: Bool = false) {
    takeoverSpring=takeover
    lastTouch=now; releaseStart=now; releaseX=manual.stickX; releaseY=manual.stickY
  }
  func move(_ translation: CGSize, _ now: Date) {
    guard stage == 2 else { return }
    lastTouch=now; releaseStart=nil
    let x=translation.width/M.stickRadius, y=translation.height/M.stickRadius
    let m=max(1,hypot(x,y)); manual.stickX=x/m; manual.stickY=y/m
  }
  func advance(_ now: Date, auto: Bool, reduced: Bool) {
    let dt=min(0.05,max(0,now.timeIntervalSince(lastDate))); lastDate=now
    guard taken || !auto else { return }
    var f=manual
    let k=reduced ? 0.25 : 1
    func tween(_ start: Double, _ end: Double, _ t: Double, _ duration: Double, _ curve: Int) -> Double {
      let u=M.clamp01(t/duration)
      let v=curve == 0 ? M.bezier(u,0.77,0,0.175,1) : curve == 1 ? M.bezier(u,0.23,1,0.32,1) : (u < 0.5 ? 2*u*u : 1-pow(-2*u+2,2)/2)
      return start+(end-start)*v
    }
    if let start=releaseStart {
      let t=now.timeIntervalSince(start)
      let v=reduced ? 0 : M.stickSpring(t,takeover:takeoverSpring)
      f.stickX=releaseX*v; f.stickY=releaseY*v
      if v == 0 { releaseStart=nil }
    }
    if action != 0 {
      let t=now.timeIntervalSince(actionStart)
      if action == 1 {
        f.unfold=tween(base.unfold,1,t,1.2*k,0)
        f.spin=tween(base.spin,1,t-1.2*k,0.7*k,1)
        f.lift=tween(base.lift,M.lift,t-1.9*k,0.9*k,1)
        f.light=tween(base.light,1,t-1.2*k,0.2,2)
        f.connected=tween(base.connected,1,t-2.8*k,0.3,2)
        if t >= 1.2*k && !didLight { UIImpactFeedbackGenerator(style:.light).impactOccurred(); didLight=true }
        if t >= 2.8*k { stage=2; if !didDone { UINotificationFeedbackGenerator().notificationOccurred(.success); didDone=true } }
        if t >= 2.8*k+0.3 { action=0 }
      } else if action == 3 {
        f.unfold=tween(base.unfold,1,t,0.4,2); f.spin=tween(base.spin,1,t,0.4,2)
        f.light=tween(base.light,1,t,0.2,2); f.lift=tween(base.lift,M.lift,t,0.5,2)
        f.connected=tween(base.connected,1,t-0.5,0.3,2)
        if t >= 0.5 { stage=2 }; if t >= 0.8 { action=0 }
      } else {
        f.connected=tween(base.connected,0,t,0.25,2)
        f.lift=tween(base.lift,0,t,1*k,0)
        f.spin=tween(base.spin,0,t-1*k,0.6*k,1)
        f.unfold=tween(base.unfold,0,t-1.6*k,1*k,0)
        f.light=tween(base.light,0,t-2.6*k,0.2,2)
        if t >= 2.6*k { stage=0 }; if t >= 2.6*k+0.2 { action=0 }
      }
    }
    let targets=[-f.stickY*M.tiltMax*(reduced ? 0.5 : 1),-f.stickX*M.tiltMax*(reduced ? 0.5 : 1),f.stickX*M.drift]
    var values=[f.pitch,f.roll,f.drift]
    for i in 0..<3 { velocities[i] += ((targets[i]-values[i])*60-velocities[i]*11)*dt; values[i] += velocities[i]*dt }
    f.pitch=values[0]; f.roll=values[1]; f.drift=values[2]
    let fade=1-M.clamp01(now.timeIntervalSince(takeoverStart)/0.15)
    f.finger=takeoverFinger*fade; f.pressConnect=takeoverPress*fade
    manual=f
    if stage == 1 || stage == 3 || abs(f.stickX)+abs(f.stickY) > 0.01 { lastTouch=now }
    if auto && now.timeIntervalSince(lastTouch) >= 2 {
      if stage == 2 { begin(2,now); release(now,takeover:true) }
      else if stage == 0 && f.unfold < 0.001 { origin=now; taken=false; manual=M.Frame() }
    }
    // Bob is added at draw time so its height never feeds the next transition's baseline.
  }
}
