import SwiftUI
import Combine
import UIKit

final class ClayCategoryTabsDriver: ObservableObject {
  let objectWillChange = ObservableObjectPublisher()
  typealias M = ClayCategoryTabsMotion
  struct Tween {
    var start: Double, from: Double, to: Double, duration: Double
    var out = false
    var spring: ClayCategoryTabsSpring? = nil
    func sample(_ clock: Double) -> (Double, Bool) {
      if let spring { return spring.sample(clock-start) }
      let u = M.seg(clock,start,start+duration)
      return (from+(to-from)*(out ? M.touchOut(u) : M.touchEase(u)), u >= 1)
    }
  }
  var frame = M.Frame()
  var time = 0.0
  var selected = 0 { didSet { if selected != oldValue { invalidate() } } }
  private(set) var moving = false { didSet { if moving != oldValue { invalidate() } } }
  private var clock = 0.0, autoStart = 0.0, lastTouch = -100.0
  private var lastDate = Date(), manual = false, resumeAsked = false, dragging = false
  private var animation: Tween?, landedAt: Double?, bellAt: Double?
  private var squashSpring: ClayCategoryTabsSpring?, wobbleSpring: ClayCategoryTabsSpring?
  private var squashFrom = 0.0, knobFrom = 0.0, wobbleFrom = 0.0
  private var fadeFrame = M.Frame(), takeoverAt = -100.0
  private var startPoint = CGPoint.zero, dragStart = 0.0, failed = false
  // The timeline samples mutable driver data without publishing every animation frame.
  // Only gesture/semantic transitions invalidate the enclosing view.
  private func invalidate() { DispatchQueue.main.async { self.objectWillChange.send() } }
  func sample(_ date: Date, auto: Bool, reduced: Bool, pin: Double?) -> (frame: M.Frame, time: Double) {
    if let pin { return (M.frameAt(pin), pin) }
    advance(date, auto:auto, reduced:reduced)
    return (frame,time)
  }
  func reset(_ auto: Bool, _ reduced: Bool) {
    invalidate()
    clock = 0; autoStart = 0; lastDate = Date(); manual = !auto || reduced
    frame = .init(); time = 0; selected = 0; animation = nil; landedAt = nil; bellAt = nil
    moving = false; dragging = false; resumeAsked = false; lastTouch = -100
  }
  func reactivate() { lastDate = Date() }
  func advance(_ date: Date, auto: Bool, reduced: Bool) {
    let dt = min(0.05,max(0,date.timeIntervalSince(lastDate))); lastDate = date; clock += dt
    if !manual && auto && !reduced {
      let t = (clock-autoStart).truncatingRemainder(dividingBy:M.loop)
      time = t; frame = M.frameAt(t); return
    }
    var f = frame
    if !reduced { time += dt }
    if let animation {
      let (v,done) = animation.sample(clock); f.r = v
      if done { self.animation = nil; moving = false; land(Int(animation.to), reduced:reduced, current:f) }
    }
    if let landedAt, !reduced {
      let u = clock-landedAt
      if u < 0.09 { f.squash = squashFrom+(0.55-squashFrom)*M.touchOut(u/0.09) }
      else { f.squash = squashSpring?.sample(u-0.09).value ?? 0 }
    }
    if let bellAt, !reduced {
        let u = clock-bellAt
        f.knob = u < 0.12 ? knobFrom+(1-knobFrom)*M.touchOut(u/0.12) : 1-M.touchOut(M.clamp01((u-0.12)/0.18))
        // withTiming's default curve is inOut(quad), used by the first 70 ms of wobble.
        let q = M.clamp01(u/0.07), ease = q < 0.5 ? 2*q*q : 1-pow(-2*q+2,2)/2
        f.wobble = u < 0.07 ? wobbleFrom+(0.14-wobbleFrom)*ease : wobbleSpring?.sample(u-0.07).value ?? 0
    }
    if landedAt == nil {
      let u = clock-takeoverAt
      f.squash = fadeFrame.squash*(1-M.defaultEase(M.clamp01(u/0.2)))
      f.knob = fadeFrame.knob*(1-M.defaultEase(M.clamp01(u/0.15)))
      f.wobble = fadeFrame.wobble*(1-M.defaultEase(M.clamp01(u/0.2)))
    }
    let fade = 1-M.defaultEase(M.clamp01((clock-takeoverAt)/0.15))
    f.finger = fadeFrame.finger*fade; f.press = fadeFrame.press*fade
    frame = f
    if moving || dragging { lastTouch = clock }
    guard auto && !reduced && !dragging && clock-lastTouch >= 2 else { return }
    if !resumeAsked {
      resumeAsked = true
      if f.r > 0.001 { go(0,reduced:false) }
    } else if !moving && f.r < 0.001 && abs(f.squash) < 0.002 && abs(f.wobble) < 0.002 {
      resumeAsked = false; manual = false; autoStart = clock; time = 0; frame = .init(); landedAt = nil; bellAt = nil
    }
  }
  private func land(_ k: Int, reduced: Bool, current: M.Frame) {
    selected = k
    UIImpactFeedbackGenerator(style:.light).impactOccurred()
    guard !reduced else { return }
    landedAt = clock
    squashFrom = current.squash
    if k == 2 { bellAt = clock; knobFrom = current.knob; wobbleFrom = current.wobble
      wobbleSpring = .init(from:0.14,to:0,duration:1,damping:0.2)
    }
    squashSpring = .init(from:0.55,to:0,duration:0.65,damping:0.35)
  }
  func takeOver() {
    lastTouch = clock; resumeAsked = false
    if manual { return }
    manual = true; fadeFrame = frame; takeoverAt = clock; landedAt = nil; bellAt = nil
  }
  func tap(_ k: Int, reduced: Bool) {
    takeOver()
    if abs(frame.r-Double(k)) < 0.001 && !moving { return }
    go(k,reduced:reduced)
  }
  private func go(_ k: Int, reduced: Bool) {
    moving = true
    animation = Tween(start:clock,from:frame.r,to:Double(k),duration:reduced ? 0.26 : 0.7+0.4*abs(Double(k)-frame.r))
  }
  func touch(_ state: UIGestureRecognizer.State, _ point: CGPoint, _ delta: CGSize, _ velocity: CGVector,
             layout: ClayCategoryTabsTheme.Layout, reduced: Bool) {
    invalidate()
    switch state {
    case .began:
      takeOver(); startPoint = point; failed = false; dragging = false
    case .changed:
      lastTouch = clock
      guard startPoint.y >= layout.pagerTop, !failed else { return }
      if !dragging {
        if abs(delta.height) > 12 { failed = true; return }
        if abs(delta.width) <= 10 { return }
        dragging = true; moving = false; animation = nil; dragStart = frame.r
      }
      var v = dragStart-Double(delta.width)/layout.w
      if v < 0 { v = -0.12*(1-exp(v/0.12)) }
      if v > 2 { v = 2+0.12*(1-exp(-(v-2)/0.12)) }
      let before = Int(min(2,max(0,frame.r)).rounded())
      frame.r = v
      if Int(min(2,max(0,v)).rounded()) != before { UISelectionFeedbackGenerator().selectionChanged() }
    case .ended, .cancelled, .failed:
      lastTouch = clock
      if dragging {
        dragging = false; moving = true
        let v = -Double(velocity.dx)/layout.w
        let k = min(2,max(0,(frame.r+0.22*v).rounded()))
        animation = Tween(start:clock,from:frame.r,to:k,duration:0.26,out:true,
          spring: reduced ? nil : ClayCategoryTabsSpring(from:frame.r,to:k,velocity:v,duration:0.65,damping:0.9))
      } else if state == .ended && !failed && abs(delta.width) <= 10 && abs(delta.height) <= 12 && startPoint.y >= layout.stageTop && startPoint.y < layout.pagerTop {
        let k = layout.xs.enumerated().min(by:{ abs($0.element-point.x) < abs($1.element-point.x) })?.offset ?? 0
        if abs(point.x-layout.xs[k]) <= layout.slot/2 { tap(k,reduced:reduced) }
      }
    default: break
    }
  }
}
