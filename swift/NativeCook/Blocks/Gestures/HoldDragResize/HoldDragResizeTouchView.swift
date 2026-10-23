import SwiftUI
import UIKit

struct HoldDragResizeTouchView: UIViewRepresentable {
    let motion: HoldDragResizeMotion

    func makeCoordinator() -> Coordinator { Coordinator(motion: motion) }

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .clear
        let gesture = UILongPressGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handle(_:)))
        gesture.minimumPressDuration = 0
        gesture.allowableMovement = 10_000
        gesture.cancelsTouchesInView = false
        gesture.delegate = context.coordinator
        view.addGestureRecognizer(gesture)
        context.coordinator.view = view
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.motion = motion
    }

    @MainActor
    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var motion: HoldDragResizeMotion
        weak var view: UIView?
        private var origin = CGPoint.zero
        private var previous = CGPoint.zero
        private var previousTime = 0.0
        private var velocity = 0.0
        private var held = false
        private var generation = 0

        init(motion: HoldDragResizeMotion) { self.motion = motion }

        @objc func handle(_ gesture: UILongPressGestureRecognizer) {
            guard let view else { return }
            let point = gesture.location(in: view)
            let now = ProcessInfo.processInfo.systemUptime
            switch gesture.state {
            case .began:
                generation += 1
                let current = generation
                origin = point
                previous = point
                previousTime = now
                velocity = 0
                held = false
                motion.touchBegan()
                Task { @MainActor in
                    try? await Task.sleep(for: .milliseconds(HoldDragResizeTokens.resize.holdMilliseconds))
                    guard current == self.generation else { return }
                    self.held = true
                    self.motion.holdBegan()
                }
            case .changed:
                let dt = max(now - previousTime, 1.0 / 240)
                velocity = (point.y - previous.y) / dt
                previous = point
                previousTime = now
                if held { motion.holdMoved(translation: point.y - origin.y) }
            case .ended, .cancelled, .failed:
                generation += 1
                motion.touchEnded(velocity: velocity, wasHeld: held)
                held = false
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
