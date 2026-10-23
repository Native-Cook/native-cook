import SwiftUI
import UIKit

/// A zero-delay touch surface for the hypnogram: begin on touch down, follow the finger, end or cancel.
/// (A raw SwiftUI DragGesture is cancelled by the gallery's back-swipe after a few points.)
struct NightSleepScoreChartTouch: UIViewRepresentable {
    var onBegan: (CGFloat) -> Void
    var onMoved: (CGFloat) -> Void
    var onEnded: () -> Void
    var onCancelled: () -> Void

    func makeUIView(context: Context) -> NightSleepScoreChartTouchView {
        let view = NightSleepScoreChartTouchView()
        view.isAccessibilityElement = false
        return view
    }

    func updateUIView(_ view: NightSleepScoreChartTouchView, context: Context) {
        view.onBegan = onBegan
        view.onMoved = onMoved
        view.onEnded = onEnded
        view.onCancelled = onCancelled
    }
}

final class NightSleepScoreChartTouchView: UIView, UIGestureRecognizerDelegate {
    var onBegan: ((CGFloat) -> Void)?
    var onMoved: ((CGFloat) -> Void)?
    var onEnded: (() -> Void)?
    var onCancelled: (() -> Void)?

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
        let x = recognizer.location(in: self).x
        switch recognizer.state {
        case .began: onBegan?(x)
        case .changed: onMoved?(x)
        case .ended: onEnded?()
        case .cancelled, .failed: onCancelled?()
        default: break
        }
    }
}
