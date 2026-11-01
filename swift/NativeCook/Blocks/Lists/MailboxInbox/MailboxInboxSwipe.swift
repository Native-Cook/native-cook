import SwiftUI
import UIKit

// Match RNGH's activeOffsetX([12, 999]), including resetting translation on activation.
final class MailboxInboxPanRecognizer: UIPanGestureRecognizer {
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        minimumNumberOfTouches = 20
        super.touchesBegan(touches, with:event)
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesMoved(touches,with:event)
        let x = translation(in:view?.window).x
        if state == .possible && (x < 12 || x > 999) {
            minimumNumberOfTouches = 1
            if numberOfTouches >= 1 {
                state = .began
                setTranslation(.zero,in:view)
            }
        }
    }
}

struct MailboxInboxSwipe: UIViewRepresentable {
    var onChange: (Double) -> Void
    var onEnd: (Double,Double) -> Void
    func makeCoordinator() -> MailboxInboxSwipeCoordinator { MailboxInboxSwipeCoordinator(self) }
    func makeUIView(context: Context) -> UIView {
        let view=UIView()
        view.backgroundColor = .clear
        view.isAccessibilityElement = false
        let pan=MailboxInboxPanRecognizer(target:context.coordinator,action:#selector(MailboxInboxSwipeCoordinator.handle(_:)))
        view.addGestureRecognizer(pan)
        return view
    }
    func updateUIView(_ view: UIView, context: Context) { context.coordinator.owner=self }
}

final class MailboxInboxSwipeCoordinator: NSObject {
    var owner: MailboxInboxSwipe
    private var active = false
    init(_ owner: MailboxInboxSwipe) { self.owner=owner }
    @objc func handle(_ pan: UIPanGestureRecognizer) {
        let x=pan.translation(in:pan.view?.window).x
        if pan.state == .began { active = true }
        if pan.state == .changed { owner.onChange(max(0,x)) }
        if pan.state == .ended || pan.state == .cancelled || pan.state == .failed {
            if active { owner.onEnd(x,pan.velocity(in:pan.view?.window).x) }
            active = false
        }
    }
}
