import UIKit

final class PaddockWidgetBentoTouchView: UIView {
    var onDown: ((Int, CGFloat, TimeInterval) -> Void)?
    var onHold: ((Int, CGFloat) -> Void)?
    private var primaryTouch: UITouch?
    var onMove: ((Int, CGFloat) -> Void)?
    var onUp: ((Int) -> Void)?
    var onCancel: ((Int) -> Void)?

    private weak var coordinatedScrollView: UIScrollView?
    private lazy var holdRecognizer: UILongPressGestureRecognizer = {
        let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(holdRecognized))
        recognizer.minimumPressDuration = PaddockWidgetBentoMotion.longPressDuration
        recognizer.allowableMovement = 10
        recognizer.cancelsTouchesInView = false
        recognizer.delaysTouchesBegan = false
        recognizer.delaysTouchesEnded = false
        return recognizer
    }()

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isOpaque = false
        isAccessibilityElement = false
        addGestureRecognizer(holdRecognizer)
    }

    required init?(coder: NSCoder) { nil }

    override func layoutSubviews() {
        super.layoutSubviews()
        var ancestor = superview
        while let view = ancestor {
            if let scrollView = view as? UIScrollView {
                if coordinatedScrollView !== scrollView {
                    scrollView.panGestureRecognizer.require(toFail: holdRecognizer)
                    coordinatedScrollView = scrollView
                }
                break
            }
            ancestor = view.superview
        }
    }

    // Recognition only arbitrates with scrolling; raw touches keep their stable owners.
    @objc private func holdRecognized(_ recognizer: UILongPressGestureRecognizer) {
        guard let touch = primaryTouch else { return }
        let id = owner(touch)
        let y = recognizer.location(in: self).y
        switch recognizer.state {
        case .began: onHold?(id, y)
        case .changed: onMove?(id, y)
        case .ended: onUp?(id); primaryTouch = nil
        case .cancelled: onCancel?(id); primaryTouch = nil
        default: break
        }
    }


    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        if primaryTouch == nil { primaryTouch = touches.min(by: { $0.timestamp < $1.timestamp }) }
        touches.forEach { onDown?(owner($0), $0.location(in: self).y, $0.timestamp) }
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        touches.forEach { onMove?(owner($0), $0.location(in: self).y) }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        touches.forEach { onUp?(owner($0)) }
        if let primaryTouch, touches.contains(primaryTouch) { self.primaryTouch = nil }
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        // Recognition may cancel the raw delivery stream while retaining the gesture itself.
        // Once the hold wins, its changed/ended/cancelled callbacks own completion.
        if holdRecognizer.state == .began || holdRecognizer.state == .changed { return }
        touches.forEach { onCancel?(owner($0)) }
        if let primaryTouch, touches.contains(primaryTouch) { self.primaryTouch = nil }
    }

    private func owner(_ touch: UITouch) -> Int { ObjectIdentifier(touch).hashValue }
}
