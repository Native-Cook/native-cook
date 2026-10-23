// The lip portraits zoom beyond the clipped disc, with the source's signed faceLift.
import UIKit

final class AuraPortraitView: UIView {
  private let portrait = UIImageView()
  var zoom = 1.0
  var lift = 0.0

  init(image: UIImage, zoom: Double = 1, lift: Double = 0) {
    self.zoom = zoom
    self.lift = lift
    super.init(frame: .zero)
    isUserInteractionEnabled = false
    clipsToBounds = true
    portrait.image = image
    portrait.contentMode = .scaleToFill
    addSubview(portrait)
    accessibilityElementsHidden = true
  }

  @available(*, unavailable) required init?(coder: NSCoder) { fatalError("Created in code") }

  func setImage(_ image: UIImage) { portrait.image = image }

  override func layoutSubviews() {
    super.layoutSubviews()
    layer.cornerRadius = bounds.width / 2
    let size = bounds.width * zoom
    portrait.frame = CGRect(
      x: (bounds.width - size) / 2,
      y: (bounds.height - size) / 2 + size * lift, width: size, height: size)
  }
}
