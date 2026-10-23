import SwiftUI
import UIKit

struct CardBalanceWalletTouch: UIViewRepresentable {
    let onBegan: (CGPoint) -> Void
    let onMoved: (CGPoint) -> Void
    let onEnded: (CGPoint, CGVector) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .clear
        view.isAccessibilityElement = false
        let recognizer = Recognizer(target: context.coordinator, action: #selector(Coordinator.changed(_:)))
        recognizer.cancelsTouchesInView = false
        recognizer.delegate = context.coordinator
        view.addGestureRecognizer(recognizer)
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.parent = self
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var parent: CardBalanceWalletTouch

        init(parent: CardBalanceWalletTouch) {
            self.parent = parent
        }

        @objc func changed(_ recognizer: Recognizer) {
            switch recognizer.state {
            case .began:
                parent.onBegan(recognizer.location)
            case .changed:
                parent.onMoved(recognizer.location)
            case .ended, .cancelled:
                parent.onEnded(recognizer.location, recognizer.velocity)
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

    final class Recognizer: UIGestureRecognizer {
        private(set) var location = CGPoint.zero
        private(set) var velocity = CGVector.zero
        private var previous = CGPoint.zero
        private var previousTime = 0.0

        override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
            guard numberOfTouches <= 1, let touch = touches.first, let view else {
                state = .failed
                return
            }
            location = touch.location(in: view)
            previous = location
            previousTime = touch.timestamp
            velocity = .zero
            state = .began
        }

        override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
            guard let touch = touches.first, let view else { return }
            let next = touch.location(in: view)
            let elapsed = touch.timestamp - previousTime
            if elapsed > 0 {
                velocity = CGVector(
                    dx: (next.x - previous.x) / elapsed,
                    dy: (next.y - previous.y) / elapsed
                )
            }
            location = next
            previous = next
            previousTime = touch.timestamp
            state = .changed
        }

        override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
            if let touch = touches.first, let view {
                location = touch.location(in: view)
            }
            state = .ended
        }

        override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
            state = .cancelled
        }

        override func reset() {
            location = .zero
            velocity = .zero
            previous = .zero
            previousTime = 0
        }
    }
}
