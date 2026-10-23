import SwiftUI
import UIKit

/// A zero-delay recognizer keeps the hero drag alive beside the gallery's back swipe.
struct VitalsChipMorphTouch: UIViewRepresentable {
    let playback: VitalsChipMorphPlayback
    let k: Double
    let label: String
    let value: String

    func makeUIView(context: Context) -> Surface { Surface(playback: playback) }
    func updateUIView(_ view: Surface, context: Context) {
        view.k = k
        view.accessibilityLabel = label
        view.accessibilityValue = value
    }

    final class Surface: UIView, UIGestureRecognizerDelegate {
        private let playback: VitalsChipMorphPlayback
        var k = 1.0
        private var start = CGPoint.zero
        private var last = CGPoint.zero
        private var lastTime = 0.0
        private var velocity = 0.0
        private var activated = false
        private var failed = false

        init(playback: VitalsChipMorphPlayback) {
            self.playback = playback
            super.init(frame: .zero)
            backgroundColor = .clear
            isAccessibilityElement = true
            accessibilityTraits = .adjustable
            accessibilityHint = "Swipe up or down to switch metric"
            let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
            recognizer.minimumPressDuration = 0
            recognizer.allowableMovement = .greatestFiniteMagnitude
            recognizer.cancelsTouchesInView = false
            recognizer.delegate = self
            addGestureRecognizer(recognizer)
        }
        required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
        override func accessibilityIncrement() { playback.adjust(.increment) }
        override func accessibilityDecrement() { playback.adjust(.decrement) }
        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                               shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }

        @objc private func handle(_ gesture: UILongPressGestureRecognizer) {
            let point = gesture.location(in: self)
            let now = ProcessInfo.processInfo.systemUptime
            switch gesture.state {
            case .began:
                start = point; last = point; lastTime = now; velocity = 0
                activated = false; failed = false
                playback.beginDrag()
            case .changed:
                guard !failed else { return }
                let dx = point.x - start.x
                if !activated {
                    if abs(point.y - start.y) > 14 { failed = true; playback.endDrag(velocity: 0, k: k); return }
                    activated = abs(dx) > 10
                }
                if now > lastTime { velocity = (point.x - last.x) / (now - lastTime) }
                last = point; lastTime = now
                if activated { playback.updateDrag(translation: dx, velocity: velocity, k: k) }
            case .ended, .cancelled, .failed:
                guard !failed else { return }
                playback.endDrag(velocity: now - lastTime > 0.1 ? 0 : velocity, k: k)
            default: break
            }
        }
    }
}
