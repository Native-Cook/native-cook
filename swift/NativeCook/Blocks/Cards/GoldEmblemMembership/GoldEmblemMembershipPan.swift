import UIKit

// RNPanHandler's custom activation path: prevent UIKit activation with 20 required
// touches, then activate beyond ±8 pt (failing beyond ±14 pt vertically) and zero
// the translation. UIKit supplies the same native velocity estimator as RN.
final class GoldEmblemMembershipPan: UIPanGestureRecognizer {
    var onBegin: (() -> Void)?
    var onInactiveEnd: (() -> Void)?
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        minimumNumberOfTouches = 20
        super.touchesBegan(touches, with: event)
        onBegin?()
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesMoved(touches, with: event)
        guard state == .possible else { return }
        let p = translation(in: view?.window)
        if p.y < -14 || p.y > 14 {
            state = .failed; onInactiveEnd?()
        } else if p.x < -8 || p.x > 8 {
            minimumNumberOfTouches = 1
            if numberOfTouches >= 1 { state = .began; setTranslation(.zero, in: view) }
        }
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
        let inactive = state == .possible
        super.touchesEnded(touches, with: event)
        if inactive { onInactiveEnd?() }
    }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
        let inactive = state == .possible
        super.touchesCancelled(touches, with: event)
        if inactive { onInactiveEnd?() }
    }
}
