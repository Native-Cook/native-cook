import SwiftUI
import UIKit

struct SwipeToOrderOnboardingBackdrop: UIViewRepresentable {
  var top: Double
  final class Surface: UIView {
    let ring = UIView()
    override init(frame: CGRect) {
      super.init(frame: frame)
      backgroundColor = SwipeToOrderOnboardingResources.ui(0x6a37ef)
      ring.layer.cornerRadius = 190
      ring.layer.borderWidth = 92
      ring.layer.borderColor = SwipeToOrderOnboardingResources.ui(0x5b2be0).cgColor
      addSubview(ring)
      isUserInteractionEnabled = false
      isAccessibilityElement = false
    }
    @available(*, unavailable) required init?(coder: NSCoder) { nil }
  }
  func makeUIView(context: Context) -> Surface { Surface() }
  func updateUIView(_ view: Surface, context: Context) {
    view.ring.frame = CGRect(x: -150, y: top+40, width: 380, height: 380)
  }
}
