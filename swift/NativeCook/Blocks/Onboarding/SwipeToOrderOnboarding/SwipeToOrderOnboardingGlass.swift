import SwiftUI
import UIKit

struct SwipeToOrderOnboardingGlass: UIViewRepresentable {
  // Match expo-blur's draw-time animator. The effect must be installed after layout so
  // UIKit creates its backdrop filter before the fractional intensity is applied.
  final class Blur: UIVisualEffectView {
    private var animator: UIViewPropertyAnimator?
    init() { super.init(effect: nil) }
    @available(*, unavailable) required init?(coder: NSCoder) { nil }
    override func draw(_ rect: CGRect) {
      super.draw(rect)
      effect = nil
      animator?.stopAnimation(true)
      animator = UIViewPropertyAnimator(duration: 1, curve: .linear) { [unowned self] in
        effect = UIBlurEffect(style: .light)
      }
      animator?.fractionComplete = 0.28
    }
    deinit { animator?.stopAnimation(true) }
  }
  final class Surface: UIView {
    let blur = Blur()
    let tint = UIView()
    override init(frame: CGRect) {
      super.init(frame: frame)
      clipsToBounds = true
      layer.cornerRadius = 18
      layer.borderWidth = 1
      layer.borderColor = UIColor.white.withAlphaComponent(SwipeToOrderOnboardingResources.alpha(0.28)).cgColor
      addSubview(blur)
      tint.backgroundColor = UIColor.white.withAlphaComponent(SwipeToOrderOnboardingResources.alpha(0.14))
      addSubview(tint)
      let content = UIImageView(image: SwipeToOrderOnboardingResources.shared.cards[0])
      content.frame = CGRect(x: 0, y: 0, width: 300, height: 86)
      addSubview(content)
      isAccessibilityElement = true
      accessibilityLabel = "Client Call ABC Corp., 12 Hours Needed"
    }
    @available(*, unavailable) required init?(coder: NSCoder) { nil }
    override func layoutSubviews() {
      super.layoutSubviews()
      blur.frame = bounds.insetBy(dx: 1, dy: 1)
      tint.frame = blur.frame
    }
  }
  func makeUIView(context: Context) -> Surface { Surface() }
  func updateUIView(_ view: Surface, context: Context) {}
}
