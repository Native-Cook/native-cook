import UIKit

final class BlacklightFlightPassportTouchSurface: UIView, UIGestureRecognizerDelegate {
    var onTouch: ((CGPoint, Int, CGSize) -> Void)?
    private var start = CGPoint.zero
    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = false
        backgroundColor = .clear
        isAccessibilityElement = false
        let gesture = UILongPressGestureRecognizer(target: self, action: #selector(touch(_:)))
        gesture.minimumPressDuration = 0
        gesture.allowableMovement = .greatestFiniteMagnitude
        gesture.cancelsTouchesInView = false
        gesture.delegate = self
        addGestureRecognizer(gesture)
    }
    @available(*, unavailable) required init?(coder: NSCoder) { nil }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }
    @objc private func touch(_ gesture: UILongPressGestureRecognizer) {
        let point = gesture.location(in: self)
        switch gesture.state {
        case .began:
            start = point
            onTouch?(point, 0, .zero)
        case .changed:
            onTouch?(point, 1, CGSize(width: point.x - start.x, height: point.y - start.y))
        case .ended, .cancelled, .failed:
            // RN onFinalize also toggles after cancellation if the button has not moved.
            onTouch?(point, 2, CGSize(width: point.x - start.x, height: point.y - start.y))
        default: break
        }
    }
}
