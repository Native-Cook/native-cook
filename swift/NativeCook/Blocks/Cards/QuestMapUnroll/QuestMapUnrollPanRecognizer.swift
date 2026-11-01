import UIKit

// Match RNGH's iOS activeOffsetY path, including resetting translation at activation.
final class QuestMapUnrollPanRecognizer: UIPanGestureRecognizer {
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        minimumNumberOfTouches = 20
        super.touchesBegan(touches,with:event)
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesMoved(touches,with:event)
        if state == .possible && abs(translation(in:view?.window).y) > 10 {
            minimumNumberOfTouches = 1
            if numberOfTouches >= 1 {
                state = .began
                setTranslation(.zero,in:view)
            }
        }
    }
}
