import SwiftUI
import UIKit

/// A zero-delay UIKit recognizer keeps the rotary drag simultaneous with the
/// gallery's navigation pan instead of being cancelled by it.
struct GumballCrankTouch: UIViewRepresentable {
    let model: GumballCrankModel

    func makeUIView(context: Context) -> Surface {
        Surface(model: model)
    }

    func updateUIView(_ view: Surface, context: Context) {
        view.model = model
    }

    @MainActor
    final class Surface: UIView, UIGestureRecognizerDelegate {
        var model: GumballCrankModel
        private var origin = CGPoint.zero
        private var travelled = CGFloat.zero

        init(model: GumballCrankModel) {
            self.model = model
            super.init(frame: .zero)
            backgroundColor = .clear
            isAccessibilityElement = false
            accessibilityElementsHidden = true

            let gesture = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
            gesture.minimumPressDuration = 0
            gesture.allowableMovement = .greatestFiniteMagnitude
            gesture.cancelsTouchesInView = false
            gesture.delegate = self
            addGestureRecognizer(gesture)
        }

        required init?(coder: NSCoder) { nil }

        override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
            model.containsKnob(point)
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            true
        }

        @objc private func handle(_ gesture: UILongPressGestureRecognizer) {
            let point = gesture.location(in: self)
            switch gesture.state {
            case .began:
                origin = point
                travelled = 0
                model.pointerDown(at: point)
            case .changed:
                travelled = max(travelled, hypot(point.x - origin.x, point.y - origin.y))
                model.pointerMoved(to: point)
            case .ended:
                model.pointerEnded()
                if travelled < CGFloat(GumballCrankTheme.presentation.tapTravelLimit) {
                    model.command(.turn)
                }
            case .cancelled, .failed:
                model.pointerEnded()
            default:
                break
            }
        }
    }
}
