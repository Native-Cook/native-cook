import SwiftUI
import UIKit

// Simultaneous zero-delay recognition survives the gallery's navigation back-swipe recognizer.
struct AiFeatureTourTouchView: UIViewRepresentable {
    var scale: Double
    var enabled: Bool
    var onBegin: (CGPoint) -> Void
    var onFinalize: (CGPoint) -> Void

    func makeUIView(context: Context) -> Surface {
        let view = Surface()
        updateUIView(view, context: context)
        return view
    }
    func updateUIView(_ view: Surface, context: Context) {
        view.scale = scale
        view.isUserInteractionEnabled = enabled
        view.onBegin = onBegin
        view.onFinalize = onFinalize
    }
    final class Surface: UIView, UIGestureRecognizerDelegate {
        var scale = 1.0
        var onBegin: ((CGPoint) -> Void)?
        var onFinalize: ((CGPoint) -> Void)?
        private var began = false
        override init(frame: CGRect) {
            super.init(frame: frame)
            backgroundColor = .clear
            isOpaque = false
            isAccessibilityElement = false
            let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
            recognizer.minimumPressDuration = 0
            recognizer.allowableMovement = .greatestFiniteMagnitude
            recognizer.cancelsTouchesInView = false
            recognizer.delegate = self
            addGestureRecognizer(recognizer)
        }
        @available(*, unavailable)
        required init?(coder: NSCoder) { nil }
        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                               shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }
        @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
            let location = recognizer.location(in: self)
            let point = CGPoint(x: location.x / max(scale, 0.001), y: location.y / max(scale, 0.001))
            switch recognizer.state {
            case .began:
                began = true
                onBegin?(point)
            case .ended, .cancelled, .failed:
                guard began else { return }
                began = false
                onFinalize?(point)
            default: break
            }
        }
    }
}
