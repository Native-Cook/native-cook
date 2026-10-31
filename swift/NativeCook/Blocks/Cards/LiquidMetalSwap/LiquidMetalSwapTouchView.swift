import SwiftUI
import UIKit

// A zero-delay recognizer shares the gallery's navigation gesture without losing the hold.
struct LiquidMetalSwapTouchView: UIViewRepresentable {
    var onEvent: (UIGestureRecognizer.State, CGSize, CGVector) -> Void
    func makeUIView(context: Context) -> LiquidMetalSwapTouchSurface {
        let view=LiquidMetalSwapTouchSurface(); view.onEvent=onEvent; return view
    }
    func updateUIView(_ view: LiquidMetalSwapTouchSurface, context: Context) { view.onEvent=onEvent }
}
final class LiquidMetalSwapTouchSurface: UIView, UIGestureRecognizerDelegate {
    var onEvent: ((UIGestureRecognizer.State, CGSize, CGVector) -> Void)?
    private var start=CGPoint.zero, last=CGPoint.zero, lastTime=0.0
    private var velocity=CGVector.zero
    override init(frame: CGRect) {
        super.init(frame:frame); backgroundColor = .clear; isOpaque=false; isAccessibilityElement=false
        let recognizer=UILongPressGestureRecognizer(target:self,action:#selector(handle(_:)))
        recognizer.minimumPressDuration=0; recognizer.allowableMovement = .greatestFiniteMagnitude
        recognizer.cancelsTouchesInView=false; recognizer.delegate=self; addGestureRecognizer(recognizer)
    }
    @available(*,unavailable) required init?(coder:NSCoder) { nil }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }
    @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
        let point=recognizer.location(in:self), now=ProcessInfo.processInfo.systemUptime
        if recognizer.state == .began { start=point; last=point; lastTime=now; velocity = .zero }
        else if recognizer.state == .changed {
            let dt=max(0.001,now-lastTime)
            velocity=CGVector(dx:(point.x-last.x)/dt,dy:(point.y-last.y)/dt)
            last=point; lastTime=now
        }
        onEvent?(recognizer.state,CGSize(width:point.x-start.x,height:point.y-start.y),velocity)
    }
}
