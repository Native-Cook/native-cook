import SwiftUI
import UIKit

struct WorkoutStackListTouchView: UIViewRepresentable {
    let onBegan: (CGPoint) -> Void
    let onChanged: (_ translationY: CGFloat, _ point: CGPoint) -> Void
    let onEnded: (_ translationY: CGFloat, _ velocityY: CGFloat, _ point: CGPoint, _ tapped: Bool) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .clear
        view.isOpaque = false
        view.isAccessibilityElement = false

        let recognizer = UILongPressGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handle(_:)))
        recognizer.minimumPressDuration = 0
        recognizer.allowableMovement = .greatestFiniteMagnitude
        recognizer.numberOfTouchesRequired = 1
        recognizer.cancelsTouchesInView = false
        recognizer.delegate = context.coordinator
        view.addGestureRecognizer(recognizer)
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.parent = self
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var parent: WorkoutStackListTouchView
        private var start = CGPoint.zero
        private var previous = CGPoint.zero
        private var previousTime = 0.0
        private var velocityY: CGFloat = 0
        private var maximumTravel: CGFloat = 0

        init(parent: WorkoutStackListTouchView) {
            self.parent = parent
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            true
        }

        @objc func handle(_ recognizer: UILongPressGestureRecognizer) {
            let point = recognizer.location(in: recognizer.view)
            let time = ProcessInfo.processInfo.systemUptime
            switch recognizer.state {
            case .began:
                start = point
                previous = point
                previousTime = time
                velocityY = 0
                maximumTravel = 0
                parent.onBegan(point)
            case .changed:
                maximumTravel = max(maximumTravel, hypot(point.x - start.x, point.y - start.y))
                let elapsed = max(time - previousTime, 0.001)
                velocityY = (point.y - previous.y) / elapsed
                previous = point
                previousTime = time
                parent.onChanged(point.y - start.y, point)
            case .ended:
                maximumTravel = max(maximumTravel, hypot(point.x - start.x, point.y - start.y))
                parent.onEnded(point.y - start.y, velocityY, point, maximumTravel < 10)
            case .cancelled, .failed:
                parent.onEnded(point.y - start.y, velocityY, point, false)
            default:
                break
            }
        }
    }
}
