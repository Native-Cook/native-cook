import SwiftUI
import UIKit

struct ClayReactionMorphTouchView: UIViewRepresentable {
  var onEvent: (UIGestureRecognizer.State, CGPoint, CGSize, CGVector) -> Void
  func makeUIView(context: Context) -> ClayReactionMorphTouchSurface {
    let view = ClayReactionMorphTouchSurface(); view.onEvent = onEvent; return view
  }
  func updateUIView(_ view: ClayReactionMorphTouchSurface, context: Context) { view.onEvent = onEvent }
}
final class ClayReactionMorphTouchSurface: UIView, UIGestureRecognizerDelegate {
  var onEvent: ((UIGestureRecognizer.State, CGPoint, CGSize, CGVector) -> Void)?
  private var origin = CGPoint.zero, previous = CGPoint.zero
  private var timestamp = 0.0
  override init(frame: CGRect) {
    super.init(frame:frame); backgroundColor = .clear; isOpaque = false; isAccessibilityElement = false
    let recognizer = UILongPressGestureRecognizer(target:self,action:#selector(handle(_:)))
    recognizer.minimumPressDuration = 0; recognizer.allowableMovement = .greatestFiniteMagnitude
    recognizer.cancelsTouchesInView = false; recognizer.delegate = self; addGestureRecognizer(recognizer)
  }
  @available(*,unavailable) required init?(coder:NSCoder) { nil }
  func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                         shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }
  @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
    let p = recognizer.location(in:self), time = ProcessInfo.processInfo.systemUptime
    if recognizer.state == .began { origin = p; previous = p; timestamp = time }
    let dt = max(0.001,time-timestamp)
    let velocity = CGVector(dx:(p.x-previous.x)/dt,dy:(p.y-previous.y)/dt)
    onEvent?(recognizer.state,p,CGSize(width:p.x-origin.x,height:p.y-origin.y),velocity)
    previous = p; timestamp = time
  }
}
