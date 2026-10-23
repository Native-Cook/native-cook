import SwiftUI
import UIKit

/// Zero-delay scrub surface that keeps receiving alongside the gallery's back-swipe recognizer.
struct PitStopReviewRatingTouch: UIViewRepresentable {
    let onChanged: (CGFloat) -> Void

    func makeUIView(context: Context) -> PitStopReviewRatingTouchView {
        let view = PitStopReviewRatingTouchView()
        view.onChanged = onChanged
        return view
    }

    func updateUIView(_ view: PitStopReviewRatingTouchView, context: Context) {
        view.onChanged = onChanged
    }
}

final class PitStopReviewRatingTouchView: UIView, UIGestureRecognizerDelegate {
    var onChanged: (CGFloat) -> Void = { _ in }

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isOpaque = false
        isAccessibilityElement = false

        let scrub = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
        scrub.minimumPressDuration = 0
        scrub.allowableMovement = .greatestFiniteMagnitude
        scrub.cancelsTouchesInView = false
        scrub.delaysTouchesBegan = false
        scrub.delaysTouchesEnded = false
        scrub.delegate = self
        addGestureRecognizer(scrub)
    }

    required init?(coder: NSCoder) { nil }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        true
    }

    @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
        guard recognizer.state == .began || recognizer.state == .changed else { return }
        onChanged(min(bounds.width, max(0, recognizer.location(in: self).x)))
    }
}
