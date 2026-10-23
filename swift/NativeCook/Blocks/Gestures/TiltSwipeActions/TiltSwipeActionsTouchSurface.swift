import SwiftUI
import UIKit

/// The whole screen as one touch surface. A zero-delay recogniser sees the first finger beside the
/// gallery's edge-back gesture and never takes the touch from it; the model finds what is under the
/// finger in the flat app's coordinates.
struct TiltSwipeActionsTouchSurface: UIViewRepresentable {
    let model: TiltSwipeActionsModel

    func makeUIView(context: Context) -> Surface { Surface(model: model) }

    func updateUIView(_ view: Surface, context: Context) {}

    final class Surface: UIView, UIGestureRecognizerDelegate {
        private weak var model: TiltSwipeActionsModel?

        init(model: TiltSwipeActionsModel) {
            self.model = model
            super.init(frame: .zero)
            backgroundColor = .clear
            isAccessibilityElement = false
            let press = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
            press.minimumPressDuration = 0
            press.allowableMovement = .greatestFiniteMagnitude
            press.numberOfTouchesRequired = 1
            press.cancelsTouchesInView = false
            press.delegate = self
            addGestureRecognizer(press)
        }

        required init?(coder: NSCoder) { nil }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }

        @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
            guard let model else { return }
            let point = recognizer.location(in: self)
            switch recognizer.state {
            case .began:
                model.touchBegan(at: point)
            case .changed:
                model.touchMoved(to: point)
            case .ended:
                model.touchEnded(at: point, cancelled: false)
            case .cancelled, .failed:
                model.touchEnded(at: point, cancelled: true)
            default:
                break
            }
        }
    }
}
