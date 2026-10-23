import SwiftUI
import UIKit

struct DragCardToSendTouchView: UIViewRepresentable {
    let motion: DragCardToSendMotion

    func makeCoordinator() -> Coordinator { Coordinator(motion: motion) }

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .clear
        let gesture = UILongPressGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handle(_:)))
        gesture.minimumPressDuration = 0
        gesture.allowableMovement = DragCardToSendTokens.gesture.allowableMovement
        gesture.cancelsTouchesInView = false
        gesture.delegate = context.coordinator
        view.addGestureRecognizer(gesture)
        context.coordinator.view = view
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) { context.coordinator.motion = motion }

    @MainActor
    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var motion: DragCardToSendMotion
        weak var view: UIView?
        private var previous = CGPoint.zero
        private var previousTime = 0.0
        private var velocity = CGPoint.zero

        init(motion: DragCardToSendMotion) { self.motion = motion }

        @objc func handle(_ gesture: UILongPressGestureRecognizer) {
            guard let view else { return }
            let point = gesture.location(in: view)
            let now = ProcessInfo.processInfo.systemUptime
            switch gesture.state {
            case .began:
                previous = point
                previousTime = now
                velocity = .zero
                motion.touchBegan(at: point)
            case .changed:
                let delta = max(now - previousTime, DragCardToSendTokens.gesture.minimumVelocityDelta)
                velocity = CGPoint(x: (point.x - previous.x) / delta, y: (point.y - previous.y) / delta)
                previous = point
                previousTime = now
                motion.touchMoved(to: point, velocityX: velocity.x)
            case .ended, .cancelled, .failed:
                motion.touchEnded(velocity: velocity)
            default:
                break
            }
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool { true }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldBeRequiredToFailBy otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool { false }
    }
}
