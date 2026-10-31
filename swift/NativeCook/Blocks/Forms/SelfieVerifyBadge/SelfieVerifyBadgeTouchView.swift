import SwiftUI
import UIKit

// A zero-delay long press survives the gallery's simultaneous navigation back swipe.
final class SelfieVerifyBadgeTouchSurface: UIView, UIGestureRecognizerDelegate {
  var onEvent: ((UIGestureRecognizer.State, CGPoint, CGSize, CGVector) -> Void)?
  private var start = CGPoint.zero
  private var last = CGPoint.zero
  private var lastTime = 0.0
  private var velocity = CGVector.zero
  var activate: (() -> Void)?
  override func accessibilityActivate() -> Bool { activate?(); return true }
  override init(frame: CGRect) {
    super.init(frame: frame)
    backgroundColor = .clear
    isOpaque = false
    isAccessibilityElement = false
    let gesture = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
    gesture.minimumPressDuration = 0
    gesture.allowableMovement = .greatestFiniteMagnitude
    gesture.cancelsTouchesInView = false
    gesture.delegate = self
    addGestureRecognizer(gesture)
  }
  @available(*, unavailable) required init?(coder: NSCoder) { nil }
  func gestureRecognizer(
    _ gestureRecognizer: UIGestureRecognizer,
    shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
  ) -> Bool { true }
  @objc private func handle(_ gesture: UILongPressGestureRecognizer) {
    let point = gesture.location(in: self)
    let now = ProcessInfo.processInfo.systemUptime
    if gesture.state == .began {
      start = point
      last = point
      lastTime = now
      velocity = .zero
    } else if gesture.state == .changed {
      let dt = max(0.001, now - lastTime)
      velocity = CGVector(dx: (point.x - last.x) / dt, dy: (point.y - last.y) / dt)
      last = point
      lastTime = now
    }
    onEvent?(
      gesture.state, point, CGSize(width: point.x - start.x, height: point.y - start.y), velocity)
  }
}

struct SelfieVerifyBadgeTouchView: UIViewRepresentable {
    var done: Bool
    var onEvent: (UIGestureRecognizer.State, CGSize, CGVector) -> Void
    var onTap: () -> Void
    func makeUIView(context: Context) -> SelfieVerifyBadgeTouchSurface { SelfieVerifyBadgeTouchSurface() }
    func updateUIView(_ view: SelfieVerifyBadgeTouchSurface, context: Context) {
        view.onEvent = { state, _, delta, velocity in onEvent(state,delta,velocity) }
        view.isAccessibilityElement=true
        view.accessibilityLabel=done ? "Verified badge" : "Camera preview"
        view.accessibilityHint=done ? "Tap to spin it." : "Drag to turn the head."
        view.accessibilityTraits=done ? .button : .allowsDirectInteraction
        view.activate=onTap
    }
}
