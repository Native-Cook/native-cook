import UIKit

// Zero-delay long press preserves drags alongside the gallery's navigation recognizer.
final class MorphObjectOnboardingTouchSurface: UIView, UIGestureRecognizerDelegate {
    var scale = 1.0
    var dy = 0.0
    var onBegan: ((CGPoint) -> Void)?
    var onChanged: ((Double, Double) -> Void)?
    var onEnded: ((CGPoint, Double, Double) -> Void)?
    private var start = CGPoint.zero
    private var last = CGPoint.zero
    private var lastTime = 0.0
    private var velocity = 0.0
    private var active = false

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isOpaque = false
        isMultipleTouchEnabled = false
        isAccessibilityElement = false
        let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
        recognizer.minimumPressDuration = 0
        recognizer.allowableMovement = .greatestFiniteMagnitude
        recognizer.cancelsTouchesInView = false
        recognizer.delegate = self
        addGestureRecognizer(recognizer)
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { nil }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                           shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }
    private func reference(_ point: CGPoint) -> CGPoint {
        CGPoint(x: point.x / scale, y: (point.y - dy) / scale)
    }
    @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
        let point = recognizer.location(in: self)
        let now = ProcessInfo.processInfo.systemUptime
        switch recognizer.state {
        case .began:
            active = true
            start = point
            last = point
            lastTime = now
            velocity = 0
            onBegan?(reference(point))
        case .changed:
            guard active else { return }
            velocity = (point.x - last.x) / max(0.001, now - lastTime)
            last = point
            lastTime = now
            onChanged?((point.x - start.x) / scale, point.x - start.x)
        case .ended, .cancelled, .failed:
            guard active else { return }
            active = false
            onEnded?(reference(point), (point.x - start.x) / scale, velocity / scale)
        default: break
        }
    }
}
