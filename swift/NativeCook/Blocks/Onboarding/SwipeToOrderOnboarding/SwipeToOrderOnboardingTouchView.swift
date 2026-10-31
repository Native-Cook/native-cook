import SwiftUI
import UIKit

struct SwipeToOrderOnboardingTouchView: UIViewRepresentable {
  var done: Bool
  var onEvent: (UIGestureRecognizer.State, Double, Double) -> Void
  var onActivate: () -> Void
  final class Surface: UIView, UIGestureRecognizerDelegate {
    var onEvent: ((UIGestureRecognizer.State, Double, Double) -> Void)?
    var onActivate: (() -> Void)?
    var start = CGPoint.zero
    var previous = CGPoint.zero
    var previousTime = 0.0
    var velocity = 0.0
    override init(frame: CGRect) {
      super.init(frame: frame)
      backgroundColor = .clear
      isAccessibilityElement = true
      accessibilityTraits = .button
      accessibilityHint = "Swipe right, or double-tap, to get started"
      let pan = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
      pan.minimumPressDuration = 0; pan.allowableMovement = .greatestFiniteMagnitude
      pan.delegate = self
      addGestureRecognizer(pan)
    }
    @available(*, unavailable) required init?(coder: NSCoder) { nil }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }
    override func accessibilityActivate() -> Bool { onActivate?(); return true }
    @objc func handle(_ gesture: UILongPressGestureRecognizer) {
      let p = gesture.location(in: self), t = ProcessInfo.processInfo.systemUptime
      if gesture.state == .began { start = p; previous = p; previousTime = t; velocity = 0 }
      else if gesture.state == .changed {
        velocity = (p.x-previous.x)/max(0.001, t-previousTime)
        previous = p; previousTime = t
      }
      onEvent?(gesture.state, p.x-start.x, velocity)
    }
  }
  func makeUIView(context: Context) -> Surface { Surface() }
  func updateUIView(_ view: Surface, context: Context) {
    view.onEvent = onEvent; view.onActivate = onActivate
    view.accessibilityLabel = done ? "You're all set" : "Get started"
  }
}
