import SwiftUI
import UIKit

struct CareerWebOnboardingTouchView: UIViewRepresentable {
    var onBegin: (CGPoint) -> Void
    var onChange: (CGPoint, CGPoint) -> Void
    var onEnd: (CGVector) -> Void
    func makeUIView(context: Context) -> CareerWebOnboardingTouchSurface {
        let view = CareerWebOnboardingTouchSurface(); updateUIView(view, context: context); return view
    }
    func updateUIView(_ view: CareerWebOnboardingTouchSurface, context: Context) {
        view.onBegin = onBegin; view.onChange = onChange; view.onEnd = onEnd
    }
}
