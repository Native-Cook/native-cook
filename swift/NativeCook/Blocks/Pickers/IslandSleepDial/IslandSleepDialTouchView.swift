import SwiftUI
import UIKit

// A two-point activation threshold, including the failed/cancelled finalize callbacks in RNGH.
struct IslandSleepDialTouchView: UIViewRepresentable {
    var top: Double
    var enabled: Bool
    var move: (CGPoint) -> Void
    var finalize: () -> Void
    func makeUIView(context: Context) -> IslandSleepDialTouchSurface { IslandSleepDialTouchSurface() }
    func updateUIView(_ view: IslandSleepDialTouchSurface, context: Context) {
        view.top = top; view.isUserInteractionEnabled = enabled
        view.move = move; view.finalize = finalize
    }
}

final class IslandSleepDialTouchSurface: UIView {
    var top = 0.0
    var move: ((CGPoint) -> Void)?
    var finalize: (() -> Void)?
    private var start = CGPoint.zero
    private var active = false
    override init(frame: CGRect) { super.init(frame: frame); backgroundColor = .clear; isMultipleTouchEnabled = false }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first else { return }
        start = touch.location(in: self); active = false
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first else { return }
        let p = touch.location(in: self)
        if !active && hypot(p.x - start.x, p.y - start.y) >= 2 { active = true }
        if active { move?(CGPoint(x: p.x, y: p.y + top)) }
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) { active = false; finalize?() }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { active = false; finalize?() }
}
