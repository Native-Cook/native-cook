import SwiftUI
import UIKit

struct GlassApertureCameraTouchSurface: UIViewRepresentable {
    var layout: GlassApertureCameraTheme.Layout
    var clock: GlassApertureCameraClock
    var reduced: Bool
    func makeUIView(context: Context) -> GlassApertureCameraTouchView { GlassApertureCameraTouchView() }
    func updateUIView(_ view: GlassApertureCameraTouchView, context: Context) { view.layout = layout; view.clock = clock; view.reduced = reduced }
}
final class GlassApertureCameraTouchView: UIView, UIGestureRecognizerDelegate {
    var layout = GlassApertureCameraTheme.Layout(CGSize(width:402,height:874),62,34)
    weak var clock: GlassApertureCameraClock?
    var reduced = false
    private var start = CGPoint.zero
    private var beganAt = 0.0
    private var target = 0
    private var tapMoved = false
    override init(frame: CGRect) {
        super.init(frame:frame); isMultipleTouchEnabled = true
        let pinch = UIPinchGestureRecognizer(target:self,action:#selector(pinched(_:)))
        pinch.delegate = self; addGestureRecognizer(pinch)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    private func control(_ p: CGPoint, _ retention: Double = 0) -> Int {
        for (id,x,r) in [(1,82*layout.sx,20.0),(2,layout.shutterX,26.0),(3,316*layout.sx,20.0)] {
            if abs(p.x-x) <= r+retention && abs(p.y-layout.shutterY) <= r+retention { return id }
        }; return 0
    }
    private func tool(_ point: CGPoint) -> Bool {
        guard abs(point.y-layout.Y(607)) <= 22 else { return false }
        for (x,width) in [(48.0,44.0),(148.0,44.0),(250.0,56.0),(349.0,44.0)] {
            if point.x >= x*layout.sx-22 && point.x <= x*layout.sx-22+width { return true }
        }
        return false
    }
    override func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        let point = gestureRecognizer.location(in:self)
        return target == 0 && point.y < layout.viewH && control(point) == 0 && !tool(point)
    }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        let point = touch.location(in:self)
        return point.y < layout.viewH && control(point) == 0 && !tool(point)
    }
    @objc private func pinched(_ g: UIPinchGestureRecognizer) {
        switch g.state {
        case .began:
            if target > 0 { clock?.press(target,false) }
            target = 0; tapMoved = true; clock?.pinchBegin(reduced)
        case .changed: clock?.pinchMove(g.scale)
        case .ended, .cancelled, .failed: clock?.pinchEnd(reduced)
        default: break
        }
    }
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first else { return }
        if target > 0 { clock?.press(target,false) }
        beganAt = touch.timestamp
        start = touch.location(in:self); tapMoved = (event?.allTouches?.count ?? 1) > 1
        target = control(start)
        if target > 0 { clock?.press(target,true) }
        else if tool(start) {
            tapMoved = true
        }
    }
    override func touchesMoved(_ touches:Set<UITouch>,with event:UIEvent?) {
        guard let touch = touches.first else { return }; let p = touch.location(in:self)
        if hypot(p.x-start.x,p.y-start.y) > 10 { tapMoved = true }
        if target > 0 { clock?.press(target,control(p,16) == target) }
    }
    override func touchesEnded(_ touches:Set<UITouch>,with event:UIEvent?) {
        guard let touch = touches.first else { return }; let p = touch.location(in:self)
        if target > 0 {
            clock?.press(target,false)
            if target == 2 && control(p,16) == target { clock?.shoot(reduced) }
        } else if !tapMoved && touch.timestamp-beganAt <= 0.5 && p.y < layout.viewH { clock?.focus(p,layout,reduced) }
        target = 0
    }
    override func touchesCancelled(_ touches:Set<UITouch>,with event:UIEvent?) {
        if target > 0 { clock?.press(target,false) }; target = 0; tapMoved = true
    }
}
