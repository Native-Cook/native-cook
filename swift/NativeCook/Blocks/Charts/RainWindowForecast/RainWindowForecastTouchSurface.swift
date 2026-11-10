import UIKit

/// Raw touches preserve Pan.minDistance(0), stroke breaks, and cancellation/finalization.
final class RainWindowForecastTouchSurface: UIView {
    var bottom: CGFloat = 0
    var touch: ((CGPoint, Bool) -> Void)?
    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        let card = CGRect(x: 24, y: bounds.height - max(bottom, 16) - 212, width: bounds.width - 48, height: 168)
        return bounds.contains(point) && !card.contains(point)
    }
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        if let t = touches.first { touch?(t.location(in: self), false) }
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        if let t = touches.first { touch?(t.location(in: self), false) }
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        if let t = touches.first { touch?(t.location(in: self), true) }
    }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        if let t = touches.first { touch?(t.location(in: self), true) }
    }
}
