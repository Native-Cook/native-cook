// Fixed system type inside the drawing; the hint alone follows Dynamic Type.
import UIKit

enum AuraText {
  static func label(
    _ text: String = "", size: Double, weight: UIFont.Weight = .regular,
    tracking: Double = -0.25, opacity: Double = 1
  ) -> UILabel {
    let label = UILabel()
    label.font = .systemFont(ofSize: size, weight: weight)
    label.textColor = .white.withAlphaComponent(opacity)
    label.numberOfLines = 1
    label.isUserInteractionEnabled = false
    set(label, text, tracking: tracking)
    return label
  }

  static func set(
    _ label: UILabel, _ text: String, tracking: Double = -0.25, underline: Bool = false
  ) {
    var attributes: [NSAttributedString.Key: Any] = [.kern: tracking]
    if underline { attributes[.underlineStyle] = NSUnderlineStyle.single.rawValue }
    label.attributedText = NSAttributedString(string: text, attributes: attributes)
  }
}
