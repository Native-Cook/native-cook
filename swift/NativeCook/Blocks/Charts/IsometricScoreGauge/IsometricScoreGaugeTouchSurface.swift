import UIKit

final class IsometricScoreGaugeTouchSurface: UIView, UIGestureRecognizerDelegate {
    var scale = 1.0, dy = 0.0
    var began: ((CGPoint) -> Void)?
    var changed: ((Double, Double) -> Void)?
    var ended: ((Double) -> Void)?
    private var start = CGPoint.zero, last = CGPoint.zero
    private var lastTime = 0.0, velocity = 0.0
    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        let gesture = UILongPressGestureRecognizer(target: self, action: #selector(touch(_:)))
        gesture.minimumPressDuration = 0
        gesture.allowableMovement = .greatestFiniteMagnitude
        gesture.cancelsTouchesInView = false
        gesture.delegate = self
        addGestureRecognizer(gesture)
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { nil }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                           shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }
    @objc private func touch(_ recognizer: UILongPressGestureRecognizer) {
        let point = recognizer.location(in: self)
        let now = CACurrentMediaTime()
        switch recognizer.state {
        case .began:
            start = point; last = point; lastTime = now; velocity = 0
            began?(CGPoint(x: point.x / scale, y: (point.y - dy) / scale))
        case .changed:
            velocity = (point.x - last.x) / max(0.001, now - lastTime)
            last = point; lastTime = now
            changed?((point.x - start.x) / scale, point.x - start.x)
        case .ended, .cancelled, .failed:
            ended?(velocity / scale)
        default: break
        }
    }
}
