import SwiftUI
import UIKit

/// A touch on a picker orb or on the newest entry becomes this block's drag; a touch anywhere else
/// is left alone (the surface does not claim it). A zero-delay UIKit recognizer survives the
/// gallery's parent pan recognizer, which would cancel a SwiftUI `DragGesture` after 24 pt.
struct MoodOrbJarTouchView: UIViewRepresentable {
    let controller: MoodOrbJarController

    func makeUIView(context: Context) -> Surface {
        Surface(controller: controller)
    }

    func updateUIView(_ view: Surface, context: Context) {
        view.controller = controller
    }

    @MainActor
    final class Surface: UIView, UIGestureRecognizerDelegate {
        weak var controller: MoodOrbJarController?

        /// Recent points, to read the finger's speed at release (the React Native pan reports it).
        private var samples: [(time: CFTimeInterval, point: CGPoint)] = []
        private var cancelledForExtraTouch = false

        init(controller: MoodOrbJarController) {
            self.controller = controller
            super.init(frame: .zero)

            backgroundColor = .clear
            isOpaque = false
            isAccessibilityElement = false

            let touch = UILongPressGestureRecognizer(target: self, action: #selector(handleTouch(_:)))
            touch.minimumPressDuration = 0
            touch.allowableMovement = .greatestFiniteMagnitude
            touch.numberOfTouchesRequired = 1
            touch.cancelsTouchesInView = false
            touch.delaysTouchesBegan = false
            touch.delegate = self
            addGestureRecognizer(touch)
        }

        @available(*, unavailable)
        required init?(coder: NSCoder) {
            fatalError("init(coder:) is unavailable")
        }

        /// Only a picker orb or the newest entry takes the touch.
        override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
            controller?.hits(at: point) ?? false
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            true
        }

        @objc private func handleTouch(_ recognizer: UILongPressGestureRecognizer) {
            let finished = recognizer.state == .ended
                || recognizer.state == .cancelled
                || recognizer.state == .failed
            if recognizer.numberOfTouches > 1 {
                if !cancelledForExtraTouch {
                    cancelledForExtraTouch = true
                    controller?.touchEnded(velocity: .zero)
                }
                return
            }
            if cancelledForExtraTouch {
                if finished {
                    cancelledForExtraTouch = false
                }
                return
            }

            let point = recognizer.location(in: self)
            let now = CACurrentMediaTime()
            switch recognizer.state {
            case .began:
                samples = [(now, point)]
                controller?.touchBegan(at: point)

            case .changed:
                record(point, at: now)
                controller?.touchMoved(to: point)

            case .ended:
                controller?.touchEnded(velocity: velocity(at: now))

            case .cancelled, .failed:
                controller?.touchEnded(velocity: .zero)

            default:
                break
            }
        }

        private func record(_ point: CGPoint, at time: CFTimeInterval) {
            samples.append((time, point))
            samples.removeAll { time - $0.time > 0.2 }
        }

        /// Points per second over the last 80 ms; zero when the finger rested before it lifted.
        private func velocity(at time: CFTimeInterval) -> CGVector {
            let recent = samples.filter { time - $0.time <= 0.08 }
            guard let first = recent.first, let last = recent.last, recent.count > 1 else { return .zero }
            let elapsed = max(last.time - first.time, 1.0 / 240.0)
            return CGVector(
                dx: (last.point.x - first.point.x) / elapsed,
                dy: (last.point.y - first.point.y) / elapsed
            )
        }
    }
}
