import UIKit

// Begin at zero distance, with UIKit's pan translation and velocity (as RNGH does on iOS).
final class SeasonTierMedalsTouchSurface: UIView, UIGestureRecognizerDelegate {
    var onEvent: ((UIGestureRecognizer.State, CGPoint, CGSize, CGVector) -> Void)?
    private final class SeasonTierMedalsPanRecognizer: UIPanGestureRecognizer {
        override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
            super.touchesBegan(touches, with: event)
            if state == .possible { state = .began }
        }
    }
    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isOpaque = false
        isAccessibilityElement = false
        let gesture = SeasonTierMedalsPanRecognizer(target: self, action: #selector(handle(_:)))
        gesture.cancelsTouchesInView = false
        gesture.delegate = self
        addGestureRecognizer(gesture)
    }
    @available(*, unavailable) required init?(coder: NSCoder) { nil }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                           shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }
    @objc private func handle(_ gesture: UIPanGestureRecognizer) {
        let point = gesture.location(in: self)
        let translation = gesture.translation(in: self)
        let velocity = gesture.velocity(in: self)
        onEvent?(gesture.state, point, CGSize(width: translation.x, height: translation.y), CGVector(dx: velocity.x, dy: velocity.y))
    }
}
