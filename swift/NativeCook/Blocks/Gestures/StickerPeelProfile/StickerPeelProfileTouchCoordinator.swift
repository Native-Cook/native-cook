import UIKit

final class StickerPeelProfileTouchCoordinator: NSObject,UIGestureRecognizerDelegate {
    let clock: StickerPeelProfileClock
    var reduced: Bool
    init(_ clock: StickerPeelProfileClock,_ reduced: Bool) { self.clock = clock; self.reduced = reduced }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }
    @objc func pan(_ g: UILongPressGestureRecognizer) { clock.pan(g.state,g.location(in:g.view),reduced) }
    @objc func pinch(_ g: UIPinchGestureRecognizer) { clock.transform(g.state,g.location(in:g.view),Double(g.scale),nil) }
    @objc func rotate(_ g: UIRotationGestureRecognizer) { clock.transform(g.state,g.location(in:g.view),nil,Double(g.rotation)) }
}
