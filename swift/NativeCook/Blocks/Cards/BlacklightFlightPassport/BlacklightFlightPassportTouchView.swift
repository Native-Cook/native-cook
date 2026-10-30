import SwiftUI
import UIKit

struct BlacklightFlightPassportTouchView: UIViewRepresentable {
    var onTouch: (CGPoint, Int, CGSize) -> Void
    func makeUIView(context: Context) -> BlacklightFlightPassportTouchSurface {
        let view = BlacklightFlightPassportTouchSurface()
        view.onTouch = onTouch
        return view
    }
    func updateUIView(_ view: BlacklightFlightPassportTouchSurface, context: Context) { view.onTouch = onTouch }
}
