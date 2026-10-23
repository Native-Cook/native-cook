import SwiftUI
import UIKit

struct GiftCardPickerPressAnimation: CustomAnimation {
    let duration: Double
    func animate<V: VectorArithmetic>(value: V, time: TimeInterval, context: inout AnimationContext<V>) -> V? {
        guard time < duration else { return nil }
        let p = max(0, time / duration)
        let eased = p < 0.5 ? 2 * p * p : 1 - pow(-2 * p + 2, 2) / 2
        return value.scaled(by: eased)
    }
}

struct GiftCardPickerPressStyle: PrimitiveButtonStyle {
    var onChanged: (Bool) -> Void = { _ in }
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.overlay {
            GiftCardPickerPressSurface(onChanged: onChanged, action: configuration.trigger)
                .accessibilityHidden(true)
        }
        .accessibilityAction { configuration.trigger() }
    }
}

struct GiftCardPickerPressSurface: UIViewRepresentable {
    let onChanged: (Bool) -> Void
    let action: () -> Void
    func makeUIView(context: Context) -> PressView { PressView() }
    func updateUIView(_ view: PressView, context: Context) {
        view.onChanged = onChanged
        view.action = action
    }

    final class PressView: UIView, UIGestureRecognizerDelegate {
        var onChanged: ((Bool) -> Void)?
        var action: (() -> Void)?
        private var pressed = false
        override init(frame: CGRect) {
            super.init(frame: frame)
            backgroundColor = .clear
            isAccessibilityElement = false
            let press = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
            press.minimumPressDuration = 0
            press.numberOfTouchesRequired = 1
            press.allowableMovement = .greatestFiniteMagnitude
            press.cancelsTouchesInView = false
            press.delegate = self
            addGestureRecognizer(press)
        }
        required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }
        private func setPressed(_ next: Bool) {
            guard next != pressed else { return }
            pressed = next
            onChanged?(next)
        }
        @objc private func handle(_ gesture: UILongPressGestureRecognizer) {
            let inside = bounds.insetBy(dx: -16, dy: -16).contains(gesture.location(in: self))
            switch gesture.state {
            case .began, .changed: setPressed(inside)
            case .ended:
                let shouldFire = pressed && inside
                setPressed(false)
                if shouldFire { action?() }
            case .cancelled, .failed: setPressed(false)
            default: break
            }
        }
    }
}
