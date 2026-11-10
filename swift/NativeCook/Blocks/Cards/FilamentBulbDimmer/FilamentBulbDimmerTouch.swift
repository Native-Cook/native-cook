import SwiftUI
import UIKit

struct FilamentBulbDimmerTouch: UIViewRepresentable {
    let onEvent: (UIGestureRecognizer.State,CGPoint,CGSize) -> Void
    func makeUIView(context: Context) -> FilamentBulbDimmerTouchSurface {
        let v=FilamentBulbDimmerTouchSurface(); v.onEvent=onEvent; return v
    }
    func updateUIView(_ view: FilamentBulbDimmerTouchSurface,context: Context) { view.onEvent=onEvent }
}
final class FilamentBulbDimmerTouchSurface: UIView,UIGestureRecognizerDelegate {
    var onEvent: ((UIGestureRecognizer.State,CGPoint,CGSize)->Void)?
    private var start=CGPoint.zero,last=CGPoint.zero,velocity=CGVector.zero
    private var lastTime=0.0
    override init(frame: CGRect) {
        super.init(frame:frame); backgroundColor = .clear; isOpaque=false; isAccessibilityElement=false
        let gesture=UILongPressGestureRecognizer(target:self,action:#selector(handle(_:)))
        gesture.minimumPressDuration=0; gesture.allowableMovement = .greatestFiniteMagnitude
        gesture.cancelsTouchesInView=false; gesture.delegate=self; addGestureRecognizer(gesture)
    }
    @available(*,unavailable) required init?(coder:NSCoder) { nil }
    func gestureRecognizer(_ gestureRecognizer:UIGestureRecognizer,shouldRecognizeSimultaneouslyWith otherGestureRecognizer:UIGestureRecognizer)->Bool { true }
    @objc private func handle(_ gesture:UILongPressGestureRecognizer) {
        let p=gesture.location(in:self), now=ProcessInfo.processInfo.systemUptime
        if gesture.state == .began { start=p; last=p; lastTime=now; velocity = .zero }
        else if gesture.state == .changed {
            let dt=max(0.001,now-lastTime)
            velocity=CGVector(dx:(p.x-last.x)/dt,dy:(p.y-last.y)/dt)
            last=p; lastTime=now
        }
        onEvent?(gesture.state,gesture.location(in: self),CGSize(width:p.x-start.x,height:p.y-start.y))
    }
}

struct FilamentBulbDimmerBackStyle: ButtonStyle {
    var reduced: Bool
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed && !reduced ? 0.96:1)
            .opacity(configuration.isPressed && reduced ? 0.72:1)
            .animation(.timingCurve(0.23,1,0.32,1,duration:0.15),value:configuration.isPressed)
    }
}
