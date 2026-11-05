import SwiftUI
import UIKit

struct AuroraTeammatesOnboardingTouch: UIViewRepresentable {
    var onEvent:(UIGestureRecognizer.State,CGPoint,CGSize,CGVector)->Void
    func makeUIView(context:Context)->AuroraTeammatesOnboardingTouchSurface {
        let v=AuroraTeammatesOnboardingTouchSurface();v.onEvent=onEvent;return v
    }
    func updateUIView(_ v:AuroraTeammatesOnboardingTouchSurface,context:Context) { v.onEvent=onEvent }
}
