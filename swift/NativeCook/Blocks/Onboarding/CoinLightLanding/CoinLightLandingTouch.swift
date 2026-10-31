import SwiftUI
import UIKit

struct CoinLightLandingTouch: UIViewRepresentable {
    let action: (UIGestureRecognizer.State,CGPoint,CGPoint)->Void
    func makeUIView(context: Context) -> CoinLightLandingTouchSurface {
        let v=CoinLightLandingTouchSurface(); v.action=action; return v
    }
    func updateUIView(_ view: CoinLightLandingTouchSurface,context:Context) { view.action=action }
}
final class CoinLightLandingTouchSurface: UIView {
    var action: ((UIGestureRecognizer.State,CGPoint,CGPoint)->Void)?
    private var previous=CGPoint.zero
    private var stamp=0.0
    private var velocity=CGPoint.zero
    override init(frame:CGRect) { super.init(frame:frame); backgroundColor = .clear; isMultipleTouchEnabled=false }
    required init?(coder:NSCoder) { fatalError("init(coder:) has not been implemented") }
    private func send(_ touches:Set<UITouch>,_ state:UIGestureRecognizer.State) {
        guard let touch=touches.first else { return }
        // Gesture.Pan e.x/e.y are local to the fixed 180-point coinHit view in the reference.
        let p=touch.location(in:self)
        if state == .began { velocity = .zero }
        else if state == .changed { let dt=touch.timestamp-stamp; if dt>0 { velocity=CGPoint(x:(p.x-previous.x)/dt,y:(p.y-previous.y)/dt) } }
        previous=p; stamp=touch.timestamp; action?(state,p,velocity)
    }
    override func touchesBegan(_ touches:Set<UITouch>,with event:UIEvent?) { send(touches,.began) }
    override func touchesMoved(_ touches:Set<UITouch>,with event:UIEvent?) { send(touches,.changed) }
    override func touchesEnded(_ touches:Set<UITouch>,with event:UIEvent?) { send(touches,.ended) }
    override func touchesCancelled(_ touches:Set<UITouch>,with event:UIEvent?) { send(touches,.cancelled) }
}

struct CoinLightLandingOverviewStyle: ButtonStyle {
    let clock: CoinLightLandingClock
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.onChange(of:configuration.isPressed) { _,pressed in clock.overviewPressed=pressed }
    }
}
