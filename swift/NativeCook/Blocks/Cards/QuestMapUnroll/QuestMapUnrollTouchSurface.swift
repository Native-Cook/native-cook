import UIKit

final class QuestMapUnrollTouchSurface: UIView {
    var pan: (UIGestureRecognizer.State, Double, Double) -> Void = { _,_,_ in }
    var tap: (CGPoint) -> Void = { _ in }
    private var beganAt = 0.0
    private var tapCancelled = false
    override init(frame: CGRect) {
        super.init(frame:frame)
        let recognizer=QuestMapUnrollPanRecognizer(target:self,action:#selector(didPan(_:)))
        addGestureRecognizer(recognizer)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    @objc private func didPan(_ recognizer: UIPanGestureRecognizer) {
        pan(recognizer.state,recognizer.translation(in:window).y,recognizer.velocity(in:window).y)
    }
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let t=touches.first else { return }
        beganAt=t.timestamp;tapCancelled=false
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let t=touches.first else { return }
        if !bounds.contains(t.location(in:self)) { tapCancelled=true }
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let t=touches.first else { return }
        let p=t.location(in:self)
        // RNGH Tap has no distance criterion here, but cancels outside and after 500 ms.
        if !tapCancelled && bounds.contains(p) && t.timestamp-beganAt <= 0.5 { tap(p) }
    }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { tapCancelled=true }
}
