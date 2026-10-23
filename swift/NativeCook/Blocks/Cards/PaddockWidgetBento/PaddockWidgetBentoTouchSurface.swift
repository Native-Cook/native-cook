import SwiftUI
import UIKit

struct PaddockWidgetBentoTouchSurface: UIViewRepresentable {
    var onDown: (Int, CGFloat, TimeInterval) -> Void
    var onHold: (Int, CGFloat) -> Void
    var onMove: (Int, CGFloat) -> Void
    var onUp: (Int) -> Void
    var onCancel: (Int) -> Void

    func makeUIView(context: Context) -> PaddockWidgetBentoTouchView {
        let view = PaddockWidgetBentoTouchView()
        view.isMultipleTouchEnabled = true
        update(view)
        return view
    }

    func updateUIView(_ view: PaddockWidgetBentoTouchView, context: Context) { update(view) }

    private func update(_ view: PaddockWidgetBentoTouchView) {
        view.onDown = onDown
        view.onHold = onHold
        view.onMove = onMove
        view.onUp = onUp
        view.onCancel = onCancel
    }
}
