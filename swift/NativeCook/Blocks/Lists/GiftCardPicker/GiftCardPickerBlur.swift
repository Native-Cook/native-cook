import SwiftUI
import UIKit

// Same UIBlurEffect(.light) and partial linear animator used by expo-blur on iOS.
struct GiftCardPickerBlur: UIViewRepresentable {
    let intensity: Double
    func makeUIView(context: Context) -> BlurView { BlurView(effect: nil) }
    func updateUIView(_ view: BlurView, context: Context) { view.setIntensity(intensity / 100) }

    final class BlurView: UIVisualEffectView {
        private var animator: UIViewPropertyAnimator?
        private var intensity = -1.0
        func setIntensity(_ value: Double) {
            guard intensity != value else { return }
            intensity = value
            animator?.stopAnimation(true)
            effect = nil
            let animator = UIViewPropertyAnimator(duration: 1, curve: .linear) { [weak self] in
                self?.effect = UIBlurEffect(style: .light)
            }
            animator.pausesOnCompletion = true
            animator.fractionComplete = CGFloat(value)
            self.animator = animator
        }
        deinit { animator?.stopAnimation(true) }
    }
}
