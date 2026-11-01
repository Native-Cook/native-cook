import SwiftUI
import UIKit

struct QuestMapUnrollTouchView: UIViewRepresentable {
    var pan: (UIGestureRecognizer.State, Double, Double) -> Void
    var tap: (CGPoint) -> Void
    func makeUIView(context: Context) -> QuestMapUnrollTouchSurface {
        let v = QuestMapUnrollTouchSurface(); v.backgroundColor = .clear
        v.pan = pan; v.tap = tap
        v.isAccessibilityElement = true
        v.accessibilityTraits = .adjustable
        v.accessibilityLabel = "Level map. Drag down to unroll, tap a level to travel there"
        return v
    }
    func updateUIView(_ view: QuestMapUnrollTouchSurface, context: Context) { view.pan = pan; view.tap = tap }
}
