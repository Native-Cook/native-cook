import SwiftUI
import UIKit

struct JellyScaleExchangeTouchView: UIViewRepresentable {
  var onEvent: (UIGestureRecognizer.State, CGPoint) -> Void
  func makeUIView(context: Context) -> JellyScaleExchangeTouchSurface {
    let view = JellyScaleExchangeTouchSurface(); view.onEvent = onEvent; return view
  }
  func updateUIView(_ view: JellyScaleExchangeTouchSurface, context: Context) { view.onEvent = onEvent }
}
final class JellyScaleExchangeTouchSurface: UIView, UIGestureRecognizerDelegate {
  var onEvent: ((UIGestureRecognizer.State, CGPoint) -> Void)?
  override init(frame: CGRect) {
    super.init(frame:frame); backgroundColor = .clear; isOpaque = false
    let recognizer = UILongPressGestureRecognizer(target:self,action:#selector(handle(_:)))
    recognizer.minimumPressDuration = 0; recognizer.allowableMovement = 400
    recognizer.cancelsTouchesInView = false; recognizer.delegate = self; addGestureRecognizer(recognizer)
  }
  required init?(coder: NSCoder) { nil }
  func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }
  @objc private func handle(_ recognizer: UILongPressGestureRecognizer) { onEvent?(recognizer.state,recognizer.location(in:self)) }
}
