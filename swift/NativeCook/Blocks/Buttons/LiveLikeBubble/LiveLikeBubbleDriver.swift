import SwiftUI
import UIKit

final class LiveLikeBubbleDriver: ObservableObject {
  typealias M = LiveLikeBubbleMotion
  @Published var manual = false
  @Published var likes = M.BASE
  @Published var holding = false
  @Published var idle = true
  var autoStart = Date()
  let epoch = Date()
  var starts = Array(repeating: -10.0, count: M.SLOTS)
  var lanes = Array(repeating: 0.0, count: M.SLOTS)
  var next = 0
  var holdX = 0.0
  var lastTouch = -100.0, lastLand = -10.0, lastThrow = -10.0
  @Published private var manualClock = 0.0
  var previous = 0.0
  var pressFrom = 0.0, pressTo = 0.0, pressAt = -10.0, pressDuration = 0.15
  var fingerFrom = 0.0, fingerAt = -10.0
  var allowAuto = false
  func time(_ date: Date) -> Double { manual ? manualClock : date.timeIntervalSince(epoch) }
  func loopTime(_ date: Date) -> Double {
    #if DEBUG
    if UserDefaults.standard.object(forKey: "ncPinTime") != nil {
      return UserDefaults.standard.double(forKey: "ncPinTime")
    }
    #endif
    return max(0,date.timeIntervalSince(autoStart)).truncatingRemainder(dividingBy: M.loop)
  }
  var pinned: Bool {
    #if DEBUG
    return UserDefaults.standard.object(forKey: "ncPinTime") != nil
    #else
    return false
    #endif
  }
  func press(_ t: Double) -> Double {
    let u = M.seg(t,pressAt,pressAt+pressDuration)
    // Reanimated withTiming's default is inOut(quad); pressing uses out(quad).
    let e = pressTo == 1 ? 1-(1-u)*(1-u) : (u < 0.5 ? 2*u*u : 1-pow(-2*u+2,2)/2)
    return pressFrom+(pressTo-pressFrom)*e
  }
  func frame(_ date: Date, reduced: Bool) -> M.Frame {
    if !manual && allowAuto { return M.frameAt(loopTime(date)) }
    let t = time(date)
    let sw = reduced ? 0 : starts.reduce(0.0) { $0+M.pulse(t-$1-M.FLIGHT-M.SWALLOW*0.5) }
    let u = M.seg(t,fingerAt,fingerAt+0.15)
    let ease = u < 0.5 ? 2*u*u : 1-pow(-2*u+2,2)/2
    return M.Frame(count: likes, swell: min(1,max(-1,sw)), press: press(t), finger: fingerFrom*(1-ease))
  }
  func hearts(_ date: Date) -> [SIMD4<Float>] {
    if !manual && allowAuto { return M.heartsAt(loopTime(date)) }
    let t = time(date)
    return (0..<M.SLOTS).map { M.heartAt(t,starts[$0],lanes[$0],$0) }
  }
  func takeOver(_ date: Date) {
    let t = time(date)
    lastTouch = t
    idle = false
    if !manual && allowAuto {
      let f = M.frameAt(loopTime(date))
      likes = f.count
      fingerFrom = f.finger; fingerAt = t
      pressFrom = 0; pressTo = 0; pressAt = t
      starts = Array(repeating: -10, count: M.SLOTS)
      lastLand = t
    }
    manualClock = t
    manual = true
    previous = date.timeIntervalSince(epoch)
  }
  func throwHeart(_ lane: Double, _ t: Double) {
    let i = next%M.SLOTS
    next += 1; starts[i] = t; lanes[i] = lane; lastThrow = t
  }
  func begin(_ lane: Double, _ date: Date) {
    takeOver(date)
    let t = time(date)
    holdX = min(0.9,max(-0.9,lane))
    pressFrom = press(t); pressTo = 1; pressAt = t; pressDuration = 0.09
    throwHeart(holdX,t); holding = true
  }
  func end(_ date: Date) {
    let t = time(date)
    holding = false; lastTouch = t
    pressFrom = press(t); pressTo = 0; pressAt = t; pressDuration = 0.15
  }
  func tick(_ date: Date) {
    guard manual else { return }
    let wall = date.timeIntervalSince(epoch)
    let dt = min(0.05,max(0,wall-previous))
    previous = wall
    manualClock += dt
    let t = manualClock
    if holding && t-lastThrow > 0.15 { throwHeart(holdX+sin(t*7)*0.25,t) }
    for start in starts {
      let landed = start+M.FLIGHT+M.SWALLOW
      if landed <= t && landed > t-dt {
        likes += 1; lastLand = t
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
      }
    }
    if t-lastLand > 1.5 && likes > M.BASE && t-lastTouch > 0.25 {
      likes -= 1; lastTouch = t-1.75+0.25
    }
    if holding || t-lastThrow < M.FLIGHT+M.SWALLOW+0.1 { lastTouch = t }
    if !holding && likes == M.BASE && t-lastTouch >= 2 {
      if allowAuto { autoStart = date; manual = false }
      idle = true
    }
  }
}

// Zero-delay simultaneous recognition keeps the gallery's back-swipe from stealing a hold.
struct LiveLikeBubbleTouchView: UIViewRepresentable {
  var button: CGRect
  var onEvent: (UIGestureRecognizer.State, CGPoint, Bool) -> Void
  func makeUIView(context: Context) -> LiveLikeBubbleTouchSurface { LiveLikeBubbleTouchSurface() }
  func updateUIView(_ view: LiveLikeBubbleTouchSurface, context: Context) {
    view.button = button; view.onEvent = onEvent
  }
}
final class LiveLikeBubbleTouchSurface: UIView, UIGestureRecognizerDelegate {
  var button = CGRect.zero
  var onEvent: ((UIGestureRecognizer.State, CGPoint, Bool) -> Void)?
  private var beganOnButton = false
  private var start = CGPoint.zero
  override init(frame: CGRect) {
    super.init(frame: frame)
    backgroundColor = .clear; isOpaque = false; isAccessibilityElement = false
    let g = UILongPressGestureRecognizer(target:self,action:#selector(handle(_:)))
    g.minimumPressDuration = 0; g.allowableMovement = .greatestFiniteMagnitude
    g.cancelsTouchesInView = false; g.delegate = self
    addGestureRecognizer(g)
  }
  @available(*,unavailable) required init?(coder: NSCoder) { nil }
  func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }
  @objc private func handle(_ gesture: UILongPressGestureRecognizer) {
    let point = gesture.location(in:self)
    if gesture.state == .began { beganOnButton = button.contains(point); start = point }
    if gesture.state == .changed && hypot(point.x-start.x,point.y-start.y) > 600 && beganOnButton {
      onEvent?(.cancelled,point,true); beganOnButton = false
    }
    onEvent?(gesture.state,point,beganOnButton)
  }
}
