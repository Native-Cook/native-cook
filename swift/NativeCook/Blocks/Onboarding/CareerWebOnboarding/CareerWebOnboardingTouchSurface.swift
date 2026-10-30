import UIKit

// Zero-delay recognition survives the gallery's interactive back-swipe cancellation.
final class CareerWebOnboardingTouchSurface: UIView, UIGestureRecognizerDelegate {
    var onBegin: ((CGPoint) -> Void)?
    var onChange: ((CGPoint, CGPoint) -> Void)?
    var onEnd: ((CGVector) -> Void)?
    private var start = CGPoint.zero, last = CGPoint.zero
    private var lastTime = 0.0
    private var velocity = CGVector.zero
    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = false; backgroundColor = .clear; isMultipleTouchEnabled = false; isAccessibilityElement = false
        let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
        recognizer.minimumPressDuration = 0; recognizer.allowableMovement = .greatestFiniteMagnitude
        recognizer.cancelsTouchesInView = false; recognizer.delegate = self; addGestureRecognizer(recognizer)
    }
    @available(*, unavailable) required init?(coder: NSCoder) { nil }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }
    @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
        let location = recognizer.location(in: self), now = Date().timeIntervalSinceReferenceDate
        switch recognizer.state {
        case .began:
            start = location; last = location; lastTime = now; velocity = .zero; onBegin?(location)
        case .changed:
            let dt = max(0.001, now - lastTime)
            velocity = CGVector(dx: (location.x - last.x) / dt, dy: (location.y - last.y) / dt)
            last = location; lastTime = now
            onChange?(location, CGPoint(x: location.x - start.x, y: location.y - start.y))
        case .ended, .cancelled, .failed: onEnd?(velocity)
        default: break
        }
    }
}
