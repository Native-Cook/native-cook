import SwiftUI
import UIKit

/// The partial light blur used by Expo BlurView at intensity 14.
struct TasteProfileBlur: UIViewRepresentable {
    let intensity: Double

    func makeUIView(context: Context) -> TasteProfileBlurView {
        TasteProfileBlurView(effect: nil)
    }

    func updateUIView(_ view: TasteProfileBlurView, context: Context) {
        view.setIntensity(intensity / 100)
    }

    final class TasteProfileBlurView: UIVisualEffectView {
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
            animator.fractionComplete = value
            self.animator = animator
        }

        deinit {
            animator?.stopAnimation(true)
        }
    }
}
