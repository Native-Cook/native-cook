import UIKit

final class WavyFrameAuctionTouchSurface: UIView, UIGestureRecognizerDelegate {
  var onEvent: ((UIGestureRecognizer.State, Double, Double) -> Void)?
  private var start = CGPoint.zero
  private var previous = CGPoint.zero
  private var previousTime = 0.0
  private var velocity = 0.0
  private var activated = false
  override init(frame: CGRect) { super.init(frame: frame); backgroundColor = .clear; isAccessibilityElement = false }
  required init?(coder: NSCoder) { nil }
  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard let touch = touches.first else { return }
    start = touch.location(in: self); previous = start; previousTime = touch.timestamp; velocity = 0; activated = false
    onEvent?(.began, 0, 0)
  }
  override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard let touch = touches.first else { return }
    let p = touch.location(in: self)
    velocity = (p.y - previous.y) / max(0.001, touch.timestamp - previousTime)
    previous = p; previousTime = touch.timestamp
    if hypot(p.x - start.x, p.y - start.y) >= 4 { activated = true }
    if activated { onEvent?(.changed, p.y - start.y, velocity) }
  }
  override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) { onEvent?(.ended, previous.y - start.y, velocity) }
  override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { onEvent?(.cancelled, previous.y - start.y, velocity) }
}
