// Original line marks on the same 24-unit grid, with constant point-sized strokes.
import UIKit

final class AuraIconView: UIView {
  private let shape = CAShapeLayer()
  var stroke = 1.5

  init(_ name: String, color: UIColor = .white) {
    super.init(frame: .zero)
    isUserInteractionEnabled = false
    shape.path = AuraIcons.path(name)
    shape.fillColor = nil
    shape.strokeColor = color.cgColor
    shape.lineCap = .round
    shape.lineJoin = .round
    layer.addSublayer(shape)
    accessibilityElementsHidden = true
  }

  @available(*, unavailable) required init?(coder: NSCoder) { fatalError("Created in code") }

  override func layoutSubviews() {
    super.layoutSubviews()
    let scale = bounds.width / 24
    shape.setAffineTransform(CGAffineTransform(scaleX: scale, y: scale))
    shape.lineWidth = stroke / max(scale, 0.001)
  }
}
