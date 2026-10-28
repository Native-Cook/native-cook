import SwiftUI
import UIKit

/// The hero's touch surface. Pan, tap and hold race one another, so the raw touches go to the
/// motion model, which decides (a drag past 3 pt, a tap under 450 ms, a hold at 380 ms) and also
/// feeds the finger to the particle world from the moment it lands. Coordinates are the hero's own.
struct HabitatSpecimenCarouselTouchSurface: UIViewRepresentable {
    let model: HabitatSpecimenCarouselModel
    let label: String
    let hint: String
    let value: String
    let onStep: (Int) -> Void

    func makeUIView(context: Context) -> TouchView {
        let view = TouchView()
        view.backgroundColor = .clear
        view.isMultipleTouchEnabled = false
        return view
    }

    func updateUIView(_ view: TouchView, context: Context) {
        view.model = model
        view.onStep = onStep
        view.isAccessibilityElement = true
        view.accessibilityLabel = label
        view.accessibilityHint = hint
        view.accessibilityValue = value
        view.accessibilityTraits = .adjustable
    }

    final class TouchView: UIView {
        weak var model: HabitatSpecimenCarouselModel?
        var onStep: ((Int) -> Void)?
        private var tracked: UITouch?

        override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
            guard tracked == nil, let touch = touches.first else { return }
            tracked = touch
            let p = touch.location(in: self)
            MainActor.assumeIsolated { model?.touchBegan(x: p.x, y: p.y) }
        }

        override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
            guard let touch = tracked, touches.contains(touch) else { return }
            let p = touch.location(in: self)
            MainActor.assumeIsolated { model?.touchMoved(x: p.x, y: p.y) }
        }

        override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) { finish(touches, cancelled: false) }
        override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { finish(touches, cancelled: true) }

        private func finish(_ touches: Set<UITouch>, cancelled: Bool) {
            guard let touch = tracked, touches.contains(touch) else { return }
            tracked = nil
            let p = touch.location(in: self)
            MainActor.assumeIsolated { model?.touchEnded(x: p.x, y: p.y, cancelled: cancelled) }
        }

        override func accessibilityIncrement() { onStep?(1) }
        override func accessibilityDecrement() { onStep?(-1) }
    }
}
