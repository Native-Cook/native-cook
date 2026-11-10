import SwiftUI
import UIKit

struct RainWindowForecastTouchView: UIViewRepresentable {
    var bottom: CGFloat
    var touch: (CGPoint, Bool) -> Void
    func makeUIView(context: Context) -> RainWindowForecastTouchSurface {
        let view = RainWindowForecastTouchSurface()
        view.isAccessibilityElement = true
        view.accessibilityLabel = "Rainy window"
        view.accessibilityHint = "Wipe to clear the fog"
        return view
    }
    func updateUIView(_ view: RainWindowForecastTouchSurface, context: Context) {
        view.bottom = bottom
        view.touch = touch
    }
}
