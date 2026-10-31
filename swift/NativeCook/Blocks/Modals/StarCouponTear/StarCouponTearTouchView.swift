import SwiftUI
import UIKit

struct StarCouponTearTouchView: UIViewRepresentable {
  var onEvent: (UIGestureRecognizer.State, Double, Double) -> Void
  func makeUIView(context: Context) -> StarCouponTearTouchSurface {
    let v = StarCouponTearTouchSurface()
    v.onEvent = onEvent
    return v
  }
  func updateUIView(_ view: StarCouponTearTouchSurface, context: Context) { view.onEvent = onEvent }
}
final class StarCouponTearTouchSurface: UIView, UIGestureRecognizerDelegate {
  var onEvent: ((UIGestureRecognizer.State, Double, Double) -> Void)?
  private var origin = CGPoint.zero, last = CGPoint.zero
  private var lastTime = 0.0, velocity = 0.0
  override init(frame: CGRect) {
    super.init(frame: frame)
    backgroundColor = .clear
    isOpaque = false
    isAccessibilityElement = false
    let g = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
    g.minimumPressDuration = 0
    g.allowableMovement = .greatestFiniteMagnitude
    g.cancelsTouchesInView = false
    g.delegate = self
    addGestureRecognizer(g)
  }
  @available(*, unavailable) required init?(coder: NSCoder) { nil }
  func gestureRecognizer(
    _ gestureRecognizer: UIGestureRecognizer,
    shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
  ) -> Bool { true }
  @objc private func handle(_ g: UILongPressGestureRecognizer) {
    let p = g.location(in: self)
    let t = ProcessInfo.processInfo.systemUptime
    if g.state == .began {
      origin = p
      last = p
      lastTime = t
      velocity = 0
    } else if g.state == .changed {
      velocity = (p.y - last.y) / max(0.001, t - lastTime)
      last = p
      lastTime = t
    }
    onEvent?(g.state, p.y - origin.y, velocity)
  }
}
