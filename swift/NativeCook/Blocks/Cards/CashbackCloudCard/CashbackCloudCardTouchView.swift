import SwiftUI
import UIKit

struct CashbackCloudCardTouchView: UIViewRepresentable {
    var down: (Bool) -> Void
    func makeUIView(context: Context) -> CashbackCloudCardTouchSurface {
        let view = CashbackCloudCardTouchSurface()
        view.backgroundColor = .clear
        view.isMultipleTouchEnabled = true
        view.isAccessibilityElement = true
        view.accessibilityTraits = .button
        view.accessibilityLabel = "Cashback cloud. Press and hold to collect your cashback"
        view.down = down
        return view
    }
    func updateUIView(_ view: CashbackCloudCardTouchSurface, context: Context) {
        view.down = down
    }
}

final class CashbackCloudCardTouchSurface: UIView {
    var down: ((Bool) -> Void)?
    private var finger: UITouch?
    private var start = CGPoint.zero
    private var collecting = false
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard finger == nil, touches.count == 1, let touch = touches.first else { finish(); return }
        finger = touch; start = touch.location(in: self); collecting = true
        down?(true)
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard collecting, let finger else { return }
        let p = finger.location(in: self)
        if hypot(p.x - start.x, p.y - start.y) > 80 || !bounds.contains(p) { finish() }
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) { finish() }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { finish() }
    private func finish() {
        if collecting { down?(false) }
        collecting = false; finger = nil
    }
}
