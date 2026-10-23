import SwiftUI
import UIKit

/// The calendar's pan: a zero-delay long-press recognizer, so the gallery's edge-back gesture and
/// SwiftUI scrolling never cancel it after ~24 pt. It reports the finger in screen points; the
/// model turns them into reference points.
struct StampDiaryCalendarTouch: UIViewRepresentable {
    var onBegan: (CGPoint) -> Void
    var onMoved: (CGSize) -> Void
    /// Velocity in points per second.
    var onEnded: (CGFloat) -> Void

    func makeUIView(context: Context) -> Surface {
        let view = Surface()
        view.owner = self
        return view
    }

    func updateUIView(_ view: Surface, context: Context) { view.owner = self }

    final class Surface: UIView, UIGestureRecognizerDelegate {
        var owner: StampDiaryCalendarTouch?
        private var origin = CGPoint.zero
        private var previous = CGPoint.zero
        private var previousTime = 0.0
        private var velocityY = 0.0

        override init(frame: CGRect) {
            super.init(frame: frame)
            backgroundColor = .clear
            isAccessibilityElement = false
            let press = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
            press.minimumPressDuration = 0
            press.allowableMovement = .greatestFiniteMagnitude
            press.cancelsTouchesInView = false
            press.delegate = self
            addGestureRecognizer(press)
        }

        required init?(coder: NSCoder) { nil }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }

        @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
            let p = recognizer.location(in: self)
            let time = CACurrentMediaTime()
            switch recognizer.state {
            case .began:
                origin = p
                previous = p
                previousTime = time
                velocityY = 0
                owner?.onBegan(p)
            case .changed:
                let dt = max(1.0 / 240, time - previousTime)
                let weight = 1 - exp(-dt / 0.035)
                velocityY += ((p.y - previous.y) / dt - velocityY) * weight
                previous = p
                previousTime = time
                owner?.onMoved(CGSize(width: p.x - origin.x, height: p.y - origin.y))
            case .ended, .cancelled, .failed:
                if time - previousTime > 0.1 { velocityY = 0 }
                owner?.onEnded(velocityY)
            default:
                break
            }
        }
    }
}
