import SwiftUI
import UIKit

struct TapEarnCoinTouchView: UIViewRepresentable {
    var passThrough = false
    var onEvent: (UIGestureRecognizer.State, CGPoint, CGSize, CGVector) -> Void
    func makeUIView(context: Context) -> TapEarnCoinTouchSurface {
        let view = TapEarnCoinTouchSurface(passThrough:passThrough); view.onEvent = onEvent; return view
    }
    func updateUIView(_ view: TapEarnCoinTouchSurface, context: Context) { view.onEvent = onEvent }
}
final class TapEarnCoinTouchSurface: UIView, UIGestureRecognizerDelegate {
    var onEvent: ((UIGestureRecognizer.State, CGPoint, CGSize, CGVector) -> Void)?
    private var start = CGPoint.zero, last = CGPoint.zero
    private var lastTime = 0.0, velocity = CGVector.zero
    private let passThrough: Bool
    private var pressGesture: UILongPressGestureRecognizer!
    private weak var gestureHost: UIView?
    init(passThrough: Bool) {
        self.passThrough = passThrough
        super.init(frame:.zero); backgroundColor = .clear; isOpaque = false; isAccessibilityElement = false
        let gesture = UILongPressGestureRecognizer(target:self,action:#selector(handle(_:)))
        gesture.minimumPressDuration = 0; gesture.allowableMovement = .greatestFiniteMagnitude
        gesture.cancelsTouchesInView = false; gesture.delegate = self; pressGesture = gesture
        if !passThrough { addGestureRecognizer(gesture) }
    }
    @available(*,unavailable) required init?(coder:NSCoder) { nil }
    override func didMoveToWindow() {
        super.didMoveToWindow()
        guard passThrough else { return }
        gestureHost?.removeGestureRecognizer(pressGesture)
        gestureHost = window
        window?.addGestureRecognizer(pressGesture)
    }
    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        passThrough ? false : super.point(inside:point,with:event)
    }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        !passThrough || bounds.contains(touch.location(in:self))
    }
    deinit { gestureHost?.removeGestureRecognizer(pressGesture) }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }
    @objc private func handle(_ gesture: UILongPressGestureRecognizer) {
        let p = gesture.location(in:self), n = ProcessInfo.processInfo.systemUptime
        if gesture.state == .began { start = p; last = p; lastTime = n; velocity = .zero }
        else if gesture.state == .changed {
            let dt = max(0.001,n-lastTime); velocity = CGVector(dx:(p.x-last.x)/dt,dy:(p.y-last.y)/dt)
            last = p; lastTime = n
        }
        onEvent?(gesture.state,p,CGSize(width:p.x-start.x,height:p.y-start.y),velocity)
    }
}
