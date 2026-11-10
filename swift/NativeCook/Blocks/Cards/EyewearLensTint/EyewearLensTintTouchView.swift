import SwiftUI
import UIKit

struct EyewearLensTintTouchView: UIViewRepresentable {
  let layout: EyewearLensTintTheme.Layout
  let tint: Int
  let clock: EyewearLensTintClock
  func makeUIView(context: Context) -> EyewearLensTintTouchSurface { EyewearLensTintTouchSurface() }
  func updateUIView(_ view: EyewearLensTintTouchSurface, context: Context) {
    view.accessibilityLabel = "Lenses tinted \(EyewearLensTintTheme.tints[tint].name)"
    view.onAdjust = { clock.adjust($0) }
    view.onEvent = { state, p, dx, velocity in
      let point = CGPoint(x:p.x,y:p.y+layout.touch.minY)
      switch state {
      case .began: clock.begin(point,layout)
      case .changed: clock.move(point,dx)
      case .ended, .cancelled, .failed: clock.end(layout,velocity)
      default: break
      }
    }
  }
}

// Like the accepted GoldTicketReward surface, a zero-delay recognizer handles cancellation too.
final class EyewearLensTintTouchSurface: UIView, UIGestureRecognizerDelegate {
  var onEvent: ((UIGestureRecognizer.State, CGPoint, Double, CGPoint) -> Void)?
  var onAdjust: ((Bool) -> Void)?
  private var start = CGPoint.zero
  // Track UIKit's native pan velocity alongside the zero-delay pickup recognizer.
  private let velocityPan = UIPanGestureRecognizer()
  override init(frame: CGRect) {
    super.init(frame:frame)
    backgroundColor = .clear; isOpaque = false
    isAccessibilityElement = true; accessibilityTraits = .adjustable
    accessibilityHint = "Drag a tint from the right onto a lens"
    let gesture = UILongPressGestureRecognizer(target:self,action:#selector(handle(_:)))
    gesture.minimumPressDuration = 0; gesture.allowableMovement = .greatestFiniteMagnitude
    gesture.cancelsTouchesInView = false; gesture.delegate = self
    addGestureRecognizer(gesture)
    velocityPan.maximumNumberOfTouches = 1; velocityPan.delegate = self
    velocityPan.cancelsTouchesInView = false; addGestureRecognizer(velocityPan)
  }
  @available(*,unavailable) required init?(coder:NSCoder) { nil }
  func gestureRecognizer(_ gestureRecognizer:UIGestureRecognizer,shouldRecognizeSimultaneouslyWith otherGestureRecognizer:UIGestureRecognizer) -> Bool { true }
  @objc private func handle(_ gesture:UILongPressGestureRecognizer) {
    let p = gesture.location(in:self)
    if gesture.state == .began { start = p }
    onEvent?(gesture.state,p,p.x-start.x,velocityPan.velocity(in:self))
  }
  override func accessibilityIncrement() { onAdjust?(true) }
  override func accessibilityDecrement() { onAdjust?(false) }
}
