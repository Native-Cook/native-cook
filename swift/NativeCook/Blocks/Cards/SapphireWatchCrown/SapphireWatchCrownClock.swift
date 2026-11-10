import SwiftUI
import UIKit

final class SapphireWatchCrownClock: ObservableObject {
  typealias M = SapphireWatchCrownMotion
  struct Sample { var frame: M.Frame; var time: Double; var clock: Double }
  struct Tween {
    var a, b, start, duration: Double
    var custom = true
    func value(_ now: Double) -> Double {
      let p = M.clamp((now-start)/max(duration,0.000001))
      let k = custom ? M.bezier(p) : (p < 0.5 ? 2*p*p : 1-pow(-2*p+2,2)/2)
      return a+(b-a)*k
    }
    func done(_ now: Double) -> Bool { now >= start+duration }
  }
  @Published var running = false
  private var date: Date?
  private var now = 0.0, autoStart = 0.0, lastTouch = -100.0
  private var mode = 0, lastDetent = 0
  private var resumeAsked = false, turning = false
  private var hours0 = M.restHours
  private var state = M.Frame()
  private var flipTween: Tween?, metalTween: Tween?, hoursTween: Tween?, crownTween: Tween?, fingerTween: Tween?
  private let light = UIImpactFeedbackGenerator(style: .light)
  private let medium = UIImpactFeedbackGenerator(style: .medium)
  private let selection = UISelectionFeedbackGenerator()
  func reset(_ auto: Bool) {
    date=nil; now=0; autoStart=0; lastTouch = -100; mode = auto ? 0 : 1; state=M.Frame()
    flipTween=nil; metalTween=nil; hoursTween=nil; crownTween=nil; fingerTween=nil
    resumeAsked=false; turning=false; running=false
  }
  func active(_ active: Bool) {
    if !running && mode == 1, let date { now += max(0,Date().timeIntervalSince(date)) }
    date = active ? Date():nil
  }
  private func tween(_ a: Double,_ b: Double,_ d: Double,_ custom: Bool = true) -> Tween { Tween(a:a,b:b,start:now,duration:d,custom:custom) }
  private func wake() {
    if !running {
      // The front can sleep without losing the movement's wall-clock phase on the next touch.
      if mode == 1, let date { now += max(0,Date().timeIntervalSince(date)) }
      date=nil; running=true
    }
  }
  func takeOver() {
    wake(); lastTouch=now; resumeAsked=false
    if mode != 0 { return }
    mode=1
    fingerTween=tween(state.finger,0,0.15,false); state.press=0
    if state.flip > 0.001 && state.flip < 0.999 { flipTween=tween(state.flip,state.flip > 0.5 ? 1 : 0,0.4,false) }
    if state.metalK > 0 { recast(state.selectedMetal,false) }
    state.hours=M.modulo(state.hours,12)
  }
  func turnOver(_ to: Double,_ reduced: Bool) {
    wake(); flipTween=tween(state.flip,to,reduced ? 0.25 : 0.85)
    medium.impactOccurred()
    if to > 0.5 { SapphireWatchCrownResources.shared.play("tick") }
  }
  func flip(_ reduced: Bool) { takeOver(); turnOver(state.flip > 0.5 ? 0 : 1,reduced) }
  private func recast(_ to: Int,_ reduced: Bool) {
    let current = state.selectedMetal
    if current == to && state.metalK == 0 { return }
    state.metalFrom=current; state.metalTo=to; state.metalK=0
    metalTween=tween(0,1,reduced ? 0.2 : 0.6)
  }
  func pick(_ to: Int,_ reduced: Bool) { light.impactOccurred(); takeOver(); recast(to,reduced) }
  func windStart() {
    turning=true; hours0=state.hours; hoursTween=nil
    lastDetent=Int(floor(state.hours*30+0.5))
  }
  func wind(_ dx: Double,_ dy: Double) {
    lastTouch=now
    let along=(dx-dy)*0.7
    state.hours=hours0+along*0.06; state.crown=along*0.12; crownTween=nil
    let det=Int(floor(state.hours*30+0.5))
    if det != lastDetent { lastDetent=det; selection.selectionChanged(); SapphireWatchCrownResources.shared.play("detent") }
  }
  func end() { lastTouch=now; turning=false }
  func adjust(_ increment: Bool) { takeOver(); hoursTween=tween(state.hours,state.hours+(increment ? 0.25 : -0.25),0.3,false) }
  func sample(_ at: Date,_ auto: Bool,_ reduced: Bool,_ pin: Double?) -> Sample {
    if let pin { return Sample(frame:M.frameAt(pin),time:pin,clock:pin) }
    // Wall-clock advance is deliberately independent of a physics/render budget.
    let dt=min(0.5,max(0,date.map { at.timeIntervalSince($0) } ?? 0))
    date=at; now += dt
    let lt=M.modulo(now-autoStart,M.loop)
    if mode == 0 {
      let before=state
      state=M.frameAt(lt)
      if DemoSound.inAutoPlay {
        if lt >= 0.7 && lt <= 2.4 && Int(floor(before.hours*30+0.5)) != Int(floor(state.hours*30+0.5)) { SapphireWatchCrownResources.shared.play("detent") }
        let previous=M.modulo(now-dt-autoStart,M.loop)
        if previous < 3.1 && lt >= 3.1 { SapphireWatchCrownResources.shared.play("tick") }
      }
    } else {
      if let v=flipTween { state.flip=v.value(now); if v.done(now) { state.flip=v.b; flipTween=nil } }
      if let v=metalTween { state.metalK=v.value(now); if v.done(now) { state.metalFrom=state.metalTo; state.metalK=0; metalTween=nil } }
      if let v=hoursTween { state.hours=v.value(now); if v.done(now) { state.hours=v.b; hoursTween=nil } }
      if let v=crownTween { state.crown=v.value(now); if v.done(now) { state.crown=v.b; crownTween=nil } }
      if let v=fingerTween { state.finger=v.value(now); if v.done(now) { state.finger=0; fingerTween=nil } }
      if auto && !turning && now-lastTouch >= 2 {
        if !resumeAsked {
          resumeAsked=true
          if state.flip > 0.001 { turnOver(0,reduced) }
          if state.selectedMetal != 1 { recast(1,reduced) }
          var d=M.restHours-M.modulo(state.hours,12)
          if d > 6 { d-=12 }; if d < -6 { d+=12 }
          hoursTween=tween(state.hours,state.hours+d,0.9)
          crownTween=tween(state.crown,state.crown+d*Double.pi,0.9)
        } else if state.flip < 0.001 && state.metalK == 0 && state.metalFrom == 1 && abs(M.modulo(state.hours,12)-M.restHours) < 0.002 && now-lastTouch > 3.2 {
          resumeAsked=false; autoStart=now; state.crown=0; mode=0
        }
      }
      if !auto && running && !turning && state.flip < 0.001 && flipTween == nil && metalTween == nil && hoursTween == nil && fingerTween == nil && crownTween == nil {
        // Publish once when the last tween settles, never per animation frame.
        DispatchQueue.main.async { [weak self] in self?.running=false }
      }
    }
    return Sample(frame:state,time:mode == 0 ? lt : M.modulo(now,M.loop),clock:M.modulo(now,600))
  }
}
