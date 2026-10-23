import SwiftUI
import UIKit

struct BelieveCardCarouselTouchSurface: UIViewRepresentable {
    let onTouchDown: () -> Void
    let onTouchUp: () -> Void
    let onPanBegan: () -> Void
    let onPanChanged: (_ translation: Double) -> Void
    let onPanEnded: (_ velocity: Double) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .clear
        view.isAccessibilityElement = false

        let contact = UILongPressGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.contactChanged(_:)))
        contact.minimumPressDuration = 0
        contact.allowableMovement = .greatestFiniteMagnitude
        contact.cancelsTouchesInView = false
        contact.delegate = context.coordinator
        view.addGestureRecognizer(contact)

        let pan = BelieveCardCarouselPanRecognizer()
        pan.cancelsTouchesInView = false
        pan.delegate = context.coordinator
        pan.onBegan = { [weak coordinator = context.coordinator] in
            coordinator?.parent.onPanBegan()
        }
        pan.onChanged = { [weak coordinator = context.coordinator] translation in
            coordinator?.parent.onPanChanged(translation)
        }
        pan.onEnded = { [weak coordinator = context.coordinator] velocity in
            coordinator?.parent.onPanEnded(velocity)
        }
        view.addGestureRecognizer(pan)
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.parent = self
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var parent: BelieveCardCarouselTouchSurface

        init(parent: BelieveCardCarouselTouchSurface) {
            self.parent = parent
        }

        @objc func contactChanged(_ recognizer: UILongPressGestureRecognizer) {
            switch recognizer.state {
            case .began:
                parent.onTouchDown()
            case .ended, .cancelled, .failed:
                parent.onTouchUp()
            default:
                break
            }
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            true
        }
    }
}

private final class BelieveCardCarouselPanRecognizer: UIGestureRecognizer {
    var onBegan: (() -> Void)?
    var onChanged: ((Double) -> Void)?
    var onEnded: ((Double) -> Void)?

    private var start = CGPoint.zero
    private var previous = CGPoint.zero
    private var previousTime = 0.0
    private var velocityX = 0.0

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        guard numberOfTouches <= 1, touches.count == 1, let touch = touches.first, let view else {
            state = .failed
            return
        }
        start = touch.location(in: view)
        previous = start
        previousTime = touch.timestamp
        velocityX = 0
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
        guard let touch = touches.first, let view else { return }
        let location = touch.location(in: view)
        let translationX = location.x - start.x
        let translationY = location.y - start.y
        let elapsed = touch.timestamp - previousTime
        if elapsed > 0 {
            velocityX = (location.x - previous.x) / elapsed
        }
        previous = location
        previousTime = touch.timestamp

        if state == .possible {
            if abs(translationY) > 16, abs(translationX) < 8 {
                state = .failed
                return
            }
            guard abs(translationX) > 8 else { return }
            state = .began
            onBegan?()
        } else if state == .began || state == .changed {
            state = .changed
        }
        if state == .began || state == .changed {
            onChanged?(Double(translationX))
        }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
        if state == .began || state == .changed {
            onEnded?(velocityX)
            state = .ended
        } else {
            state = .failed
        }
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
        if state == .began || state == .changed {
            onEnded?(0)
            state = .cancelled
        } else {
            state = .failed
        }
    }

    override func reset() {
        start = .zero
        previous = .zero
        previousTime = 0
        velocityX = 0
    }
}
