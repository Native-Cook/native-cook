import UIKit

final class AuroraTeammatesOnboardingTouchSurface: UIView {
    var onEvent:((UIGestureRecognizer.State,CGPoint,CGSize,CGVector)->Void)?
    private var start=CGPoint.zero,last=CGPoint.zero,velocity=CGVector.zero,lastTime=0.0
    override init(frame:CGRect) {
        super.init(frame:frame);backgroundColor = .clear;isOpaque=false;isAccessibilityElement=false
        let g=UILongPressGestureRecognizer(target:self,action:#selector(handle(_:)))
        g.minimumPressDuration=0;g.allowableMovement = .greatestFiniteMagnitude;addGestureRecognizer(g)
    }
    @available(*,unavailable) required init?(coder:NSCoder) { nil }
    @objc private func handle(_ g:UILongPressGestureRecognizer) {
        let p=g.location(in:self),t=ProcessInfo.processInfo.systemUptime
        if g.state == .began { start=p;last=p;lastTime=t;velocity = .zero }
        else if g.state == .changed { let dt=max(0.001,t-lastTime);velocity=CGVector(dx:(p.x-last.x)/dt,dy:(p.y-last.y)/dt);last=p;lastTime=t }
        onEvent?(g.state,p,CGSize(width:p.x-start.x,height:p.y-start.y),velocity)
    }
}
