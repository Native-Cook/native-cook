import QuartzCore
import SwiftUI
import UIKit

/// A zero-delay recognizer keeps the card pan simultaneous with the gallery's edge-back gesture.
struct MenuProductCarouselTouchView: UIViewRepresentable {
    let onBegan: () -> Void
    let onChanged: (CGFloat) -> Void
    let onEnded: (CGFloat) -> Void

    func makeUIView(context: Context) -> MenuProductCarouselTouchSurface {
        let view = MenuProductCarouselTouchSurface()
        updateUIView(view, context: context)
        return view
    }

    func updateUIView(_ view: MenuProductCarouselTouchSurface, context: Context) {
        view.onBegan = onBegan
        view.onChanged = onChanged
        view.onEnded = onEnded
    }
}

final class MenuProductCarouselTouchSurface: UIView, UIGestureRecognizerDelegate {
    var onBegan: (() -> Void)?
    var onChanged: ((CGFloat) -> Void)?
    var onEnded: ((CGFloat) -> Void)?

    private var startPoint = CGPoint.zero
    private var previousPoint = CGPoint.zero
    private var previousTime = 0.0
    private var velocityX = CGFloat.zero

    override init(frame: CGRect) {
        super.init(frame: frame)
        let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
        recognizer.minimumPressDuration = 0
        recognizer.allowableMovement = .greatestFiniteMagnitude
        recognizer.numberOfTouchesRequired = 1
        recognizer.cancelsTouchesInView = false
        recognizer.delegate = self
        addGestureRecognizer(recognizer)
        isAccessibilityElement = false
        accessibilityElementsHidden = true
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        true
    }

    @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
        let point = recognizer.location(in: self)
        let now = CACurrentMediaTime()
        switch recognizer.state {
        case .began:
            startPoint = point
            previousPoint = point
            previousTime = now
            velocityX = 0
            onBegan?()
        case .changed:
            let elapsed = max(0.001, now - previousTime)
            velocityX = (point.x - previousPoint.x) / elapsed
            previousPoint = point
            previousTime = now
            onChanged?(point.x - startPoint.x)
        case .ended:
            onEnded?(velocityX)
        case .cancelled, .failed:
            onEnded?(0)
        default:
            break
        }
    }
}
