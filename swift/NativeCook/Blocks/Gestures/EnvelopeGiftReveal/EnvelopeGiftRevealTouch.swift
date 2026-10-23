import SwiftUI
import UIKit
import QuartzCore

/// The finger over the whole screen: touch down, drag, lift. A zero-delay press recogniser that lets every other
/// recogniser run beside it (the gallery's back swipe among them) and never cancels the touches the buttons need.
struct EnvelopeGiftRevealTouchSurface: UIViewRepresentable {
    let engine: EnvelopeGiftRevealEngine

    func makeUIView(context: Context) -> EnvelopeGiftRevealTouchView {
        let view = EnvelopeGiftRevealTouchView()
        view.engine = engine
        return view
    }

    func updateUIView(_ view: EnvelopeGiftRevealTouchView, context: Context) {
        view.engine = engine
    }
}

final class EnvelopeGiftRevealTouchView: UIView, UIGestureRecognizerDelegate {
    weak var engine: EnvelopeGiftRevealEngine?
    private var previous = CGPoint.zero
    private var previousTime = 0.0
    private var velocityY = 0.0

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        let press = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
        press.minimumPressDuration = 0
        press.allowableMovement = .greatestFiniteMagnitude
        press.cancelsTouchesInView = false
        press.numberOfTouchesRequired = 1
        press.delegate = self
        addGestureRecognizer(press)
    }

    required init?(coder: NSCoder) { nil }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }

    @objc private func handle(_ gesture: UILongPressGestureRecognizer) {
        // Window coordinates: the engine only compares points with the point the touch began at.
        let point = gesture.location(in: nil)
        let time = CACurrentMediaTime()
        switch gesture.state {
        case .began:
            previous = point
            previousTime = time
            velocityY = 0
            engine?.touchBegan(x: point.x, y: point.y)
        case .changed:
            let dt = max(EnvelopeGiftRevealTokens.Interaction.velocityFloorSeconds, time - previousTime)
            let weight = 1 - exp(-dt / EnvelopeGiftRevealTokens.Interaction.velocitySmoothingSeconds)
            velocityY += ((point.y - previous.y) / dt - velocityY) * weight
            previous = point
            previousTime = time
            engine?.touchMoved(x: point.x, y: point.y)
        case .ended, .cancelled, .failed:
            // A finger that rested before it lifted is not flicking.
            if time - previousTime > EnvelopeGiftRevealTokens.Interaction.staleVelocitySeconds { velocityY = 0 }
            engine?.touchEnded(velocityY: velocityY)
        default:
            break
        }
    }
}
