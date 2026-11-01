import UIKit

final class PopUpQuestCardPanGesture: UIPanGestureRecognizer {
  private var origin: CGPoint?
  private weak var tracked: UITouch?
  private var raw = CGPoint.zero
  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
    if origin == nil, let touch = touches.first { tracked = touch; origin = touch.location(in: view) }
    super.touchesBegan(touches, with: event)
  }
  override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
    if let origin, let point = tracked?.location(in: view) { raw = CGPoint(x: point.x - origin.x, y: point.y - origin.y) }
    super.touchesMoved(touches, with: event)
    if state == .possible && hypot(raw.x, raw.y) >= 8 { state = .began }
  }
  override func translation(in target: UIView?) -> CGPoint {
    guard let view, let target, target !== view else { return raw }
    let a = view.convert(CGPoint.zero, to: target), b = view.convert(raw, to: target)
    return CGPoint(x: b.x - a.x, y: b.y - a.y)
  }
  override func reset() {
    super.reset(); origin = nil; tracked = nil; raw = .zero
  }
}
