import SwiftUI
import UIKit

struct GoldEmblemMembershipTouch: UIViewRepresentable {
    enum Event { case begin, start, update, end, finalize }
    var onEvent: (Event, Double, Double, Double) -> Void
    func makeUIView(context: Context) -> GoldEmblemMembershipTouchSurface {
        let view = GoldEmblemMembershipTouchSurface(); view.onEvent = onEvent; return view
    }
    func updateUIView(_ view: GoldEmblemMembershipTouchSurface, context: Context) { view.onEvent = onEvent }
}
