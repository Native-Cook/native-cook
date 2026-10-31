import SwiftUI
import UIKit

struct PerfectMonthGemTouch: UIViewRepresentable {
    var onEvent: (UIGestureRecognizer.State, CGSize, CGVector) -> Void
    func makeUIView(context: Context) -> PerfectMonthGemTouchSurface {
        let view = PerfectMonthGemTouchSurface()
        view.onEvent = onEvent
        return view
    }
    func updateUIView(_ view: PerfectMonthGemTouchSurface, context: Context) { view.onEvent = onEvent }
}

final class PerfectMonthGemTouchSurface: UIView, UIGestureRecognizerDelegate {
    var onEvent: ((UIGestureRecognizer.State, CGSize, CGVector) -> Void)?
    private var start = CGPoint.zero
    private var beganAt = 0.0
    private let velocityPan = PerfectMonthGemPanRecognizer(target: nil, action: nil)
    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isAccessibilityElement = false
        velocityPan.delegate = self
        velocityPan.cancelsTouchesInView = false
        addGestureRecognizer(velocityPan)
        let gesture = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
        gesture.minimumPressDuration = 0
        gesture.allowableMovement = .greatestFiniteMagnitude
        gesture.delegate = self
        addGestureRecognizer(gesture)
    }
    @available(*, unavailable) required init?(coder: NSCoder) { nil }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }
    @objc private func handle(_ gesture: UILongPressGestureRecognizer) {
        let point = gesture.location(in: self)
        let now = ProcessInfo.processInfo.systemUptime
        if gesture.state == .began {
            start = point
            beganAt = now
        }
        let velocity = velocityPan.velocity(in: self)
        // RNGH's unconfigured Tap expires after 500 ms; a held pan still finalizes normally.
        let state: UIGestureRecognizer.State = gesture.state == .ended && now - beganAt > 0.5 ? .cancelled : gesture.state
        onEvent?(state, CGSize(width: point.x - start.x, height: point.y - start.y), CGVector(dx: velocity.x, dy: velocity.y))
    }
}

// Keep UIKit's native velocity estimator, with the same six-point activation as RN's pan.
final class PerfectMonthGemPanRecognizer: UIPanGestureRecognizer {
    private var origin = CGPoint.zero
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        if state == .possible, let touch = touches.first { origin = touch.location(in: view) }
        super.touchesBegan(touches, with: event)
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesMoved(touches, with: event)
        if state == .possible, let touch = touches.first {
            let p = touch.location(in: view)
            if hypot(p.x - origin.x, p.y - origin.y) >= 6 { state = .began }
        }
    }
}
