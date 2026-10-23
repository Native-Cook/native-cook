// Complete native ink is clipped by complementary paths in pane coordinates.
import UIKit

final class AuraReceiptView: UIView {
  private let portrait = UIImageView()
  private let pearl = UIView()
  private let pearlLight = CAGradientLayer()
  private let orbit = AuraIconView(
    "orbit", color: UIColor(red: 138 / 255, green: 79 / 255, blue: 216 / 255, alpha: 1))
  private let title = AuraText.label(size: 14, weight: .semibold)
  private let note = AuraText.label(size: 11, opacity: 0.62)
  private let amount = AuraText.label(size: 16, weight: .semibold)
  private let circleMask = CAShapeLayer()
  private var receipt: AuraReceipt?

  init() {
    super.init(frame: .zero)
    isOpaque = false
    backgroundColor = .clear
    isUserInteractionEnabled = false
    accessibilityElementsHidden = true
    addSubview(pearl)
    pearl.layer.addSublayer(pearlLight)
    pearlLight.colors = [
      UIColor.white.cgColor,
      UIColor(red: 207 / 255, green: 193 / 255, blue: 228 / 255, alpha: 1).cgColor,
    ]
    pearlLight.startPoint = CGPoint(x: 0, y: 0)
    pearlLight.endPoint = CGPoint(x: 0.8, y: 1)
    pearl.addSubview(orbit)
    portrait.contentMode = .scaleToFill
    portrait.clipsToBounds = true
    addSubview(portrait)
    addSubview(title)
    addSubview(note)
    addSubview(amount)
    amount.textAlignment = .right
    circleMask.fillRule = .evenOdd
    circleMask.fillColor = UIColor.white.cgColor
  }

  @available(*, unavailable) required init?(coder: NSCoder) { fatalError("Created in code") }

  func configure(_ receipt: AuraReceipt, scale: Double) {
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    defer { CATransaction.commit() }
    if self.receipt != receipt {
      self.receipt = receipt
      portrait.isHidden = receipt.friend < 0
      pearl.isHidden = receipt.friend >= 0
      portrait.image = receipt.friend >= 0 ? AuraPeople.friends[receipt.friend] : nil
      AuraText.set(title, receipt.friend < 0 ? "Orbit Pro" : AuraPeople.names[receipt.friend])
      AuraText.set(note, receipt.friend < 0 ? "Jan 17 · 20:12" : AuraL10n.t("now"))
      AuraText.set(amount, "\(receipt.friend < 0 ? "" : "−")$\(AuraL10n.cents(receipt.cents))")
    }
    let u = scale
    portrait.frame = CGRect(x: 28 * u, y: 234 * u, width: 36 * u, height: 36 * u)
    // The actual face and its clip are ready before the first reveal frame;
    // there is no placeholder or deferred nested portrait layout in this slot.
    portrait.layer.cornerRadius = 18 * u
    pearl.frame = portrait.frame
    pearl.layer.cornerRadius = 18 * u
    pearl.clipsToBounds = true
    pearlLight.frame = pearl.bounds
    orbit.frame = CGRect(x: 6 * u, y: 6 * u, width: 24 * u, height: 24 * u)
    orbit.stroke = 1.5 * u
    title.font = .systemFont(ofSize: 14 * u, weight: .semibold)
    note.font = .systemFont(ofSize: 11 * u)
    amount.font = .monospacedDigitSystemFont(ofSize: 16 * u, weight: .semibold)
    title.frame = CGRect(x: 72 * u, y: 236 * u, width: 109 * u, height: 18 * u)
    note.frame = CGRect(x: 72 * u, y: 255 * u, width: 109 * u, height: 14 * u)
    amount.frame = CGRect(x: 181 * u, y: 249 * u, width: 153 * u, height: 20 * u)
    // AuraIconView lays out its shape layer separately. Finish that work in
    // this transaction, before either complete row can enter the reveal.
    layoutIfNeeded()
  }

  func reveal(circle: CGPath?, inverse: Bool = false) {
    if let circle {
      // Keep the mask at the row's actual resolution; do not magnify a
      // two-point mask or scale the inverse mask's outer rectangle.
      circleMask.frame = bounds
      circleMask.contentsScale = traitCollection.displayScale
      if inverse {
        let outside = CGMutablePath()
        outside.addRect(bounds)
        outside.addPath(circle)
        circleMask.path = outside
      } else {
        circleMask.path = circle
      }
      if layer.mask == nil { layer.mask = circleMask }
    } else if layer.mask != nil {
      layer.mask = nil
    }
  }
}
