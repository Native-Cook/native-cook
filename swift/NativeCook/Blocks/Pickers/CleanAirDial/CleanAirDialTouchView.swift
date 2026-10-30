import SwiftUI
import UIKit

struct CleanAirDialTouchView: UIViewRepresentable {
  var onTouch: (CGPoint, UIGestureRecognizer.State) -> Void
  func makeUIView(context: Context) -> CleanAirDialTouchSurface {
    let view = CleanAirDialTouchSurface()
    view.onTouch = onTouch
    return view
  }
  func updateUIView(_ view: CleanAirDialTouchSurface, context: Context) { view.onTouch = onTouch }
}

final class CleanAirDialTouchSurface: UIView, UIGestureRecognizerDelegate {
  var onTouch: ((CGPoint, UIGestureRecognizer.State) -> Void)?
  override init(frame: CGRect) {
    super.init(frame: frame)
    backgroundColor = .clear
    isOpaque = false
    isAccessibilityElement = false
    isMultipleTouchEnabled = false
    // A simultaneous zero-delay recognizer survives the gallery's navigation back-swipe.
    let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
    recognizer.minimumPressDuration = 0
    recognizer.allowableMovement = .greatestFiniteMagnitude
    recognizer.cancelsTouchesInView = false
    recognizer.delegate = self
    addGestureRecognizer(recognizer)
  }
  @available(*, unavailable)
  required init?(coder: NSCoder) { nil }
  func gestureRecognizer(
    _ gestureRecognizer: UIGestureRecognizer,
    shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
  ) -> Bool { true }
  @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
    onTouch?(recognizer.location(in: self), recognizer.state)
  }
}
