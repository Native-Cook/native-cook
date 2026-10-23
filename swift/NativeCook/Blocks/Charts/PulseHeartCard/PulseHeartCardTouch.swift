import SwiftUI
import UIKit

/// The chart's scrub: a zero-delay press that follows the finger, beside the gallery's back swipe and
/// the page's scroll (`Gesture.Pan().minDistance(0)` in the React Native version).
struct PulseHeartCardTouch: UIViewRepresentable {
    /// A touch began or moved: the x of the finger inside the chart, in points.
    let onTouch: (Double) -> Void
    /// The finger lifted or the touch was cancelled.
    let onEnd: () -> Void

    func makeUIView(context: Context) -> PulseHeartCardTouchView {
        let view = PulseHeartCardTouchView()
        view.isAccessibilityElement = false
        view.accessibilityElementsHidden = true
        return view
    }

    func updateUIView(_ view: PulseHeartCardTouchView, context: Context) {
        view.onTouch = onTouch
        view.onEnd = onEnd
    }
}

final class PulseHeartCardTouchView: UIView, UIGestureRecognizerDelegate {
    var onTouch: ((Double) -> Void)?
    var onEnd: (() -> Void)?

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
        recognizer.minimumPressDuration = 0
        recognizer.allowableMovement = .greatestFiniteMagnitude
        recognizer.cancelsTouchesInView = false
        recognizer.delegate = self
        addGestureRecognizer(recognizer)
    }

    required init?(coder: NSCoder) { nil }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                           shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }

    @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
        switch recognizer.state {
        case .began, .changed:
            onTouch?(Double(recognizer.location(in: self).x))
        case .ended, .cancelled, .failed:
            onEnd?()
        default:
            break
        }
    }
}
