import SwiftUI
import UIKit

struct SnowGlobeGiftTouchSurface: UIViewRepresentable {
    var clock: SnowGlobeGiftClock
    var radius: Double
    func makeUIView(context:Context) -> SnowGlobeGiftTouchView { SnowGlobeGiftTouchView() }
    func updateUIView(_ view:SnowGlobeGiftTouchView,context:Context) { view.clock = clock; view.radius = radius }
}
final class SnowGlobeGiftTouchView: UIView {
    weak var clock: SnowGlobeGiftClock?
    var radius = 132.0
    private var start = CGPoint.zero, previous = CGPoint.zero
    private var stamp = 0.0, speed = 0.0, beganAt = 0.0
    private var dragged = false
    override func touchesBegan(_ touches:Set<UITouch>,with event:UIEvent?) {
        guard let t = touches.first else { return }
        start = t.location(in:self); previous = start; stamp = t.timestamp; beganAt = stamp; speed = 0; dragged = false
        clock?.begin()
    }
    override func touchesMoved(_ touches:Set<UITouch>,with event:UIEvent?) {
        guard let t = touches.first else { return }
        let p = t.location(in:self), x = p.x-start.x
        if !dragged && hypot(p.x-start.x,p.y-start.y) >= 2 { dragged = true; clock?.dragStart(x) }
        if dragged { clock?.drag(x,radius) }
        if t.timestamp > stamp { speed = (p.x-previous.x)/(t.timestamp-stamp) }
        previous = p; stamp = t.timestamp
    }
    override func touchesEnded(_ touches:Set<UITouch>,with event:UIEvent?) {
        let endAt = touches.first?.timestamp ?? stamp
        let releasedSpeed = endAt-stamp > 0.1 ? 0 : speed
        clock?.end(releasedSpeed,radius,dragged,!dragged && endAt-beganAt > 0.5)
    }
    override func touchesCancelled(_ touches:Set<UITouch>,with event:UIEvent?) { clock?.end(speed,radius,dragged,true) }
}
