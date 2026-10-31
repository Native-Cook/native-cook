import SwiftUI
import UIKit

struct IconEraScrubberTouchView: UIViewRepresentable {
    var onEvent: (UIGestureRecognizer.State, CGPoint, CGSize, CGVector) -> Void
    func makeUIView(context: Context) -> IconEraScrubberTouchSurface {
        let view = IconEraScrubberTouchSurface(); view.onEvent = onEvent; return view
    }
    func updateUIView(_ view: IconEraScrubberTouchSurface, context: Context) { view.onEvent = onEvent }
}

// Zero-delay recognizer, simultaneous with the gallery's navigation back swipe.
final class IconEraScrubberTouchSurface: UIView, UIGestureRecognizerDelegate {
    var onEvent: ((UIGestureRecognizer.State, CGPoint, CGSize, CGVector) -> Void)?
    private var start = CGPoint.zero, last = CGPoint.zero
    private var lastTime = 0.0
    private var velocity = CGVector.zero
    override init(frame: CGRect) {
        super.init(frame:frame)
        backgroundColor = .clear; isOpaque = false; isAccessibilityElement = false
        let recognizer = UILongPressGestureRecognizer(target:self,action:#selector(handle(_:)))
        recognizer.minimumPressDuration = 0
        recognizer.allowableMovement = .greatestFiniteMagnitude
        recognizer.cancelsTouchesInView = false
        recognizer.delegate = self
        addGestureRecognizer(recognizer)
    }
    @available(*,unavailable) required init?(coder:NSCoder) { nil }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                           shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }
    @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
        let point = recognizer.location(in:self)
        let time = ProcessInfo.processInfo.systemUptime
        if recognizer.state == .began {
            start = point; last = point; lastTime = time; velocity = .zero
        } else if recognizer.state == .changed {
            let dt = max(0.001,time-lastTime)
            velocity = .init(dx:(point.x-last.x)/dt,dy:(point.y-last.y)/dt)
            last = point; lastTime = time
        } else if time-lastTime > 0.05 {
            // A held ruler releases at rest, rather than reusing the last drag event's speed.
            velocity = .zero
        }
        onEvent?(recognizer.state,point,.init(width:point.x-start.x,height:point.y-start.y),velocity)
    }
}
