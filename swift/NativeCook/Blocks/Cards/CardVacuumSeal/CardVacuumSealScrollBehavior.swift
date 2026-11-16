import SwiftUI
import UIKit

/// RN sets bounces=false even when a small phone needs to scroll the transaction list.
struct CardVacuumSealScrollBehavior: UIViewRepresentable {
    final class Probe: UIView {
        override func didMoveToWindow() { super.didMoveToWindow(); apply() }
        override func didMoveToSuperview() { super.didMoveToSuperview(); apply() }
        func apply() {
            var ancestor = superview
            while let view = ancestor {
                if let scroll = view as? UIScrollView { scroll.bounces = false; return }
                ancestor = view.superview
            }
        }
    }
    func makeUIView(context: Context) -> Probe {
        let view = Probe(); view.isUserInteractionEnabled = false; return view
    }
    func updateUIView(_ uiView: Probe,context: Context) { uiView.apply() }
}
