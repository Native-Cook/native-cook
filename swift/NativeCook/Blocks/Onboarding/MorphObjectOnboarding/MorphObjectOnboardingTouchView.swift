import SwiftUI

struct MorphObjectOnboardingTouchView: UIViewRepresentable {
    var scale: Double
    var dy: Double
    var enabled: Bool
    var onBegan: (CGPoint) -> Void
    var onChanged: (Double, Double) -> Void
    var onEnded: (CGPoint, Double, Double) -> Void

    func makeUIView(context: Context) -> MorphObjectOnboardingTouchSurface {
        let view = MorphObjectOnboardingTouchSurface()
        updateUIView(view, context: context)
        return view
    }
    func updateUIView(_ view: MorphObjectOnboardingTouchSurface, context: Context) {
        view.scale = scale
        view.dy = dy
        view.isUserInteractionEnabled = enabled
        view.onBegan = onBegan
        view.onChanged = onChanged
        view.onEnded = onEnded
    }
}
