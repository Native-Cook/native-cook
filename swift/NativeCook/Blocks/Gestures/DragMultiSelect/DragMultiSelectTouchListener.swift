import UIKit

/// Hands the first finger on the list to the engine and never recognises anything itself, so the
/// list still scrolls and the gallery's edge swipe still works beside it. It is a recogniser rather
/// than the view's own touch methods because it keeps getting the touches when a recogniser further
/// up takes them from the view.
final class DragMultiSelectTouchListener: UIGestureRecognizer {
    weak var engine: DragMultiSelectEngine?
    private var tracked: UITouch?

    override func canBePrevented(by preventingGestureRecognizer: UIGestureRecognizer) -> Bool { false }
    override func canPrevent(_ preventedGestureRecognizer: UIGestureRecognizer) -> Bool { false }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        guard tracked == nil, let touch = touches.first else { return }
        tracked = touch
        let point = touch.location(in: view)
        engine?.touchBegan(x: point.x, y: point.y)
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
        guard let touch = tracked, touches.contains(touch) else { return }
        let point = touch.location(in: view)
        engine?.touchMoved(x: point.x, y: point.y)
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
        lift(touches, cancelled: false)
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
        lift(touches, cancelled: true)
    }

    private func lift(_ touches: Set<UITouch>, cancelled: Bool) {
        guard let touch = tracked, touches.contains(touch) else { return }
        tracked = nil
        let point = touch.location(in: view)
        engine?.touchEnded(x: point.x, y: point.y, cancelled: cancelled)
        state = .failed
    }

    override func reset() {
        super.reset()
        tracked = nil
    }
}
