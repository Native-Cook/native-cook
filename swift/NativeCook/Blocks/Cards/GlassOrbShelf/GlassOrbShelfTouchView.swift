import SwiftUI

struct GlassOrbShelfTouchView: UIViewRepresentable {
    let label: String
    let onAdjust: (Int) -> Void
    let onEvent: (UIGestureRecognizer.State, CGPoint, CGSize, CGVector) -> Void
    func makeUIView(context: Context) -> GlassOrbShelfTouchSurface { GlassOrbShelfTouchSurface() }
    func updateUIView(_ view: GlassOrbShelfTouchSurface, context: Context) {
        view.onEvent=onEvent; view.onAdjust=onAdjust; view.accessibilityLabel=label
    }
}
