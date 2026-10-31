import SwiftUI
import UIKit

final class CoinDropRewardDriver: ObservableObject {
  typealias M = CoinDropRewardMotion
  enum Channel: CaseIterable { case launch,flip,pull,melt,fly,trail,toast,text,wobble,plus,finger,press }
  let epoch = Date()
  var pausedAt: Date?, pausedSeconds = 0.0
  var ghostOnCoin = 0.0, ghostPull = 0.0
  var autoPlay = false, reduced = false, manual = true, holding = false
  var autoStart = 0.0, lastTouch = -100.0
  var stage = 0, selected = 0
  var end = [0.0,0.0], waitStart = 0.0, popStart = 0.0, pourEnd = 0.0
  var out = false
  var channels: [Channel: CoinDropRewardTween] = [:]
  var dragStart = 0.0, pressedRow: Int?, hitCoin = false, dragged = false
  private var wasTicking = false
  var pin: Double? {
    #if DEBUG
    if UserDefaults.standard.object(forKey:"ncPinTime") != nil { return UserDefaults.standard.double(forKey:"ncPinTime") }
    #endif
    return nil
  }
  func now(_ date: Date = Date()) -> Double { (pausedAt ?? date).timeIntervalSince(epoch)-pausedSeconds }
  func active(_ active: Bool) {
    if active, let paused = pausedAt { pausedSeconds += Date().timeIntervalSince(paused); pausedAt = nil }
    else if !active && pausedAt == nil { pausedAt = Date() }
  }
  var ticking: Bool { (!manual && autoPlay && !reduced) || stage != 0 || end.contains(where:{$0 > now()}) || (autoPlay && !reduced && manual) || holding || channels.values.contains { now() < $0.start+$0.duration*2 } }
  func configure(autoPlay: Bool,reduced: Bool) {
    if self.autoPlay == autoPlay && self.reduced == reduced { return }
    objectWillChange.send()
    self.autoPlay = autoPlay; self.reduced = reduced; manual = !autoPlay || reduced; autoStart = now()
  }
  func v(_ c: Channel,_ t: Double) -> Double {
    channels[c]?.value(t) ?? ((c == .wobble || c == .plus) ? 1 : 0)
  }
  func set(_ c: Channel,_ value: Double,_ t: Double) { channels[c] = CoinDropRewardTween(value,value,t,0) }
  func animate(_ c: Channel,_ value: Double,_ t: Double,_ seconds: Double,_ curve: CoinDropRewardTween.Curve = .standard,_ delay: Double = 0) {
    channels[c] = CoinDropRewardTween(v(c,t),value,t+delay,seconds,curve)
  }
  func loopTime(_ t: Double) -> Double { pin ?? ((t-autoStart).truncatingRemainder(dividingBy:M.loop)+M.loop).truncatingRemainder(dividingBy:M.loop) }
  func frame(_ t: Double) -> CoinDropRewardFrame {
    if !manual { return M.frameAt(loopTime(t)) }
    return CoinDropRewardFrame(launch:v(.launch,t),flip:v(.flip,t),pull:v(.pull,t),melt:v(.melt,t),fly:v(.fly,t),trail:v(.trail,t),toast:v(.toast,t),text:v(.text,t),wobbleU:v(.wobble,t),plus:v(.plus,t),cooldown:max(0,end[0]-t),press:v(.press,t),finger:v(.finger,t),onCoin:ghostOnCoin,fingerPull:ghostPull,out:out)
  }
  func left(_ i: Int,_ t: Double) -> Double { !manual ? (i == 0 ? M.frameAt(loopTime(t)).cooldown : 0) : max(0,end[i]-t) }
  func tick(_ t: Double) {
    guard manual else { return }
    if stage == 1 && t-popStart >= 1.45 { objectWillChange.send(); stage = 2; waitStart = t }
    if stage == 2 && t-waitStart > 3 { collect(t) }
    if stage == 4 && t >= pourEnd { absorb(t) }
    let busy = stage != 0 || end.contains(where:{$0 > t}) || v(.wobble,t) < 1 || v(.plus,t) < 1
    if busy { lastTouch = t }
    if autoPlay && !reduced && !holding && t-lastTouch > 2 {
      objectWillChange.send(); autoStart = t; manual = false; selected = 0
    }
    let nextTicking = ticking
    if nextTicking != wasTicking { wasTicking = nextTicking; objectWillChange.send() }
  }
  func takeOver(_ t: Double) {
    lastTouch = t
    guard !manual else { return }
    objectWillChange.send()
    let s = frame(t), lt = loopTime(t)
    manual = true; ghostOnCoin = s.onCoin; ghostPull = s.fingerPull
    let pairs: [(Channel,Double)] = [(.launch,s.launch),(.flip,s.flip),(.pull,s.pull),(.melt,s.melt),(.fly,s.fly),(.trail,s.trail),(.toast,s.toast),(.text,s.text),(.wobble,s.wobbleU),(.plus,s.plus),(.press,s.press),(.finger,s.finger)]
    for (c,value) in pairs { set(c,value,t) }
    animate(.finger,0,t,0.15); animate(.press,0,t,0.15)
    if s.cooldown > 0 { end[0] = t+s.cooldown }
    if lt >= M.launchTime && lt < M.absorb {
      selected = 0; out = true; animate(.launch,1,t,0.2); animate(.flip,1,t,0.25)
      if lt >= M.release { stage = 3; collect(t) } else { settle(t) }
    } else { out = false; stage = 0 }
  }
  func open(_ i: Int,_ t: Double) {
    takeOver(t)
    guard stage == 0 && left(i,t) <= 0 else { return }
    objectWillChange.send(); selected = i; stage = reduced ? 2 : 1; out = true; popStart = t
    for c in [Channel.pull,.melt,.fly,.trail,.flip,.launch] { set(c,0,t) }
    UIImpactFeedbackGenerator(style:.light).impactOccurred()
    if reduced {
      set(.launch,1,t); set(.flip,1,t); animate(.toast,1,t,0.2); animate(.text,1,t,0.2); waitStart = t
    } else {
      animate(.launch,1,t,0.6,.spring(0.62))
      animate(.toast,1,t,0.35,.out); animate(.text,1,t,0.3,.out,0.3)
      animate(.flip,1,t,0.8,.inOut,0.65)
    }
  }
  func collect(_ t: Double) {
    guard stage != 4 else { return }
    objectWillChange.send(); stage = 4
    let rest = max(0.25,1-v(.fly,t))
    animate(.melt,1,t,0.3,.out); animate(.trail,1,t,M.flySeconds*0.9*rest,.out)
    animate(.toast,0,t,0.35,.out); animate(.text,0,t,0.25,.out)
    let seconds = (reduced ? 0.3 : M.flySeconds)*rest
    animate(.fly,1,t,seconds,.pour); pourEnd = t+seconds
  }
  func absorb(_ t: Double) {
    objectWillChange.send(); stage = 0; out = false; end[selected] = t+M.cooldown
    set(.wobble,reduced ? 1 : 0,t); animate(.wobble,1,t,reduced ? 0 : 0.7,.linear)
    set(.plus,0,t); animate(.plus,1,t,0.9,.linear)
    for c in [Channel.launch,.flip,.pull,.melt,.fly,.trail] { set(c,0,t) }
    UINotificationFeedbackGenerator().notificationOccurred(.success)
  }
  func settle(_ t: Double) {
    stage = 2; waitStart = t
    animate(.pull,0,t,0.5,.spring(0.6)); animate(.melt,0,t,0.5,.spring(0.8))
  }
  func accessibilityCollect() {
    let t = now(); takeOver(t)
    if stage == 1 || stage == 2 { collect(t) }
  }
  func touch(_ state: UIGestureRecognizer.State,_ point: CGPoint,_ translation: CGSize,_ velocity: CGVector,_ layout: CoinDropRewardLayout) {
    let t = now()
    switch state {
    case .began:
      takeOver(t); objectWillChange.send(); holding = true; dragged = false
      hitCoin = out && CGRect(x:layout.tileCx-118,y:layout.tileCy-118,width:236,height:236).contains(point)
      pressedRow = nil
      for i in 0..<2 where CGRect(x:20,y:layout.rowsTop+Double(i)*72,width:layout.w-40,height:72).contains(point) { pressedRow = i }
    case .changed:
      lastTouch = t
      if hitCoin && !dragged && hypot(translation.width,translation.height) >= 4 && (stage == 1 || stage == 2) {
        objectWillChange.send(); dragged = true; stage = 3; animate(.flip,1,t,0.2); dragStart = v(.pull,t); set(.pull,dragStart,t)
      }
      if dragged && stage == 3 {
        objectWillChange.send()
        var value = dragStart+(translation.width*layout.dx+translation.height*layout.dy)/(layout.span*layout.span)
        if value < 0 { value = -0.04*(1-exp(value/0.04)) }
        if value > 0.45 { value = 0.45+0.1*(1-exp(-(value-0.45)/0.1)) }
        set(.pull,value,t); set(.melt,M.meltOf(value),t)
      }
      if hypot(translation.width,translation.height) > 10 && pressedRow != nil { objectWillChange.send(); pressedRow = nil }
    case .ended,.cancelled,.failed:
      objectWillChange.send(); holding = false; lastTouch = t
      if dragged && stage == 3 {
        let speed = (velocity.dx*layout.dx+velocity.dy*layout.dy)/(layout.span*layout.span)
        if v(.pull,t) > 0.16 || speed > 0.9 { collect(t) } else { settle(t) }
      } else if state == .ended {
        if hitCoin && (stage == 1 || stage == 2) { collect(t) }
        else if let row = pressedRow { open(row,t) }
      }
      pressedRow = nil
    default: break
    }
  }
}
