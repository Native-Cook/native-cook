import SwiftUI
import UIKit

struct MessageBottleShareTouch: UIViewRepresentable {
    let action: (UIGestureRecognizer.State, CGSize, CGPoint) -> Void
    // A zero-delay recognizer also survives the gallery's simultaneous back gesture.
    final class MessageBottleShareTouchView: UIView, UIGestureRecognizerDelegate {
        var action: ((UIGestureRecognizer.State, CGSize, CGPoint) -> Void)?
        private var origin = CGPoint.zero
        private var previous = CGPoint.zero
        private var previousTime = 0.0
        private var velocity = CGPoint.zero
        override init(frame: CGRect) {
            super.init(frame: frame)
            backgroundColor = .clear
            isOpaque = false
            let gesture = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
            gesture.minimumPressDuration = 0
            gesture.allowableMovement = .greatestFiniteMagnitude
            gesture.cancelsTouchesInView = false
            gesture.delegate = self
            addGestureRecognizer(gesture)
        }
        required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                               shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }
        @objc private func handle(_ gesture: UILongPressGestureRecognizer) {
            let p = gesture.location(in: self), time = ProcessInfo.processInfo.systemUptime
            if gesture.state == .began {
                origin = p; previous = p; previousTime = time; velocity = .zero
            } else if gesture.state == .changed {
                let dt = max(0.001, time-previousTime)
                velocity = CGPoint(x: (p.x-previous.x)/dt, y: (p.y-previous.y)/dt)
                previous = p; previousTime = time
            }
            action?(gesture.state, CGSize(width: p.x-origin.x, height: p.y-origin.y), velocity)
        }
    }
    func makeUIView(context: Context) -> MessageBottleShareTouchView { MessageBottleShareTouchView() }
    func updateUIView(_ view: MessageBottleShareTouchView, context: Context) { view.action = action }
}
