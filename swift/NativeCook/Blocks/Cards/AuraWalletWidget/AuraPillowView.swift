// Labels stay unscaled while the glass and touch frames trade width beneath them.
import SwiftUI
import UIKit

final class AuraPillowView: UIView {
  private let label: UILabel
  private let arrow: AuraIconView
  private let incoming: AuraIconView?
  private let amountContainer = UIView()
  private var number: UIHostingController<AuraNumber>?
  private let index: Int
  private var labelWidth = 0.0
  private var rowWidth = 0.0
  private var scale = 1.0
  private var words: AuraWords

  init(index: Int, words: AuraWords) {
    self.index = index
    self.words = words
    let keys = ["settings", "swap", "receive", "send"]
    label = AuraText.label(index >= 2 ? AuraL10n.t(keys[index]) : "", size: 15, weight: .medium)
    arrow = AuraIconView(keys[index])
    incoming = index == 3 ? AuraIconView("send") : nil
    super.init(frame: .zero)
    isUserInteractionEnabled = false
    accessibilityElementsHidden = true
    addSubview(label)
    addSubview(arrow)
    if let incoming { addSubview(incoming) }
    if index == 3 {
      let controller = UIHostingController(rootView: AuraNumber(words: words, balance: false))
      controller.safeAreaRegions = []
      controller.view.backgroundColor = .clear
      controller.view.isUserInteractionEnabled = false
      // Own visibility outside the hosting view, whose layout SwiftUI manages.
      amountContainer.clipsToBounds = true
      amountContainer.addSubview(controller.view)
      addSubview(amountContainer)
      number = controller
    }
  }

  @available(*, unavailable) required init?(coder: NSCoder) { fatalError("Created in code") }

  func configure(scale u: Double, priced: Bool) {
    self.scale = u
    label.font = .systemFont(ofSize: 15 * u, weight: .medium)
    labelWidth = index >= 2 ? label.intrinsicContentSize.width : 0
    let amountAttributes: [NSAttributedString.Key: Any] = [
      .font: UIFont.systemFont(ofSize: 15 * u, weight: .medium), .kern: -0.25,
    ]
    let amountWidth = priced ? (words.amount as NSString).size(withAttributes: amountAttributes).width : 0
    let space = (" " as NSString).size(withAttributes: amountAttributes).width
    let icon = (index >= 2 ? 14.0 : 18.0) * u
    let gap = index >= 2 ? 5 * u : 0
    rowWidth = labelWidth + gap + icon + (priced ? amountWidth + space : 0)
    bounds = CGRect(x: 0, y: 0, width: rowWidth, height: 20 * u)
    label.frame = CGRect(x: 0, y: 0, width: labelWidth, height: 20 * u)
    amountContainer.isHidden = !priced
    amountContainer.frame = CGRect(x: labelWidth + space, y: 0, width: amountWidth, height: 20 * u)
    number?.view.frame = amountContainer.bounds
    let arrowFrame = CGRect(x: rowWidth - icon, y: (20 * u - icon) / 2, width: icon, height: icon)
    // Never set frame while an arrow has an animation transform: UIKit would
    // bake that displacement into its base position on every content update.
    for iconView in [arrow, incoming].compactMap({ $0 }) {
      iconView.bounds = CGRect(origin: .zero, size: arrowFrame.size)
      iconView.center = CGPoint(x: arrowFrame.midX, y: arrowFrame.midY)
    }
    arrow.stroke = 1.5 * u
    incoming?.stroke = 1.5 * u
  }

  func render(_ drive: AuraDrive, reduced: Bool) {
    let trade = reduced ? (drive.wide < 0.5 ? 0.0 : 1.0) : drive.wide
    let frames = auraActionFrames(trade)
    center = CGPoint(
      x: (frames.xs[index] + frames.widths[index] / 2 + (index >= 2 ? 1 : 0)) * scale,
      y: 324 * scale)
    let compression = reduced ? 1 : 1 - 0.04 * drive.press[index]
    transform = CGAffineTransform(scaleX: compression, y: compression)
    alpha = reduced && index >= 2 ? abs(1 - 2 * auraClamp(drive.wide)) : 1
    if index == 2 {
      label.alpha = max(0, 1 - drive.wide * 2)
      arrow.transform = CGAffineTransform(translationX: -(labelWidth + 5 * scale) / 2 * trade, y: 0)
    } else if index == 3 {
      let sending = drive.phase == .sending
      let outgoing = sending ? min(1, drive.clock / AuraFlow.views.arrow.exitTime) : 0
      let entering =
        sending
        ? auraClamp(
          (drive.clock - AuraFlow.views.arrow.enterDelay) / AuraFlow.views.arrow.enterTime) : 0
      arrow.alpha = 1 - outgoing
      arrow.transform = CGAffineTransform(translationX: 0, y: reduced ? 0 : -12 * scale * outgoing)
      incoming?.alpha = entering
      incoming?.transform = CGAffineTransform(
        translationX: 0, y: reduced ? 0 : 8 * scale * (1 - entering))
    }
  }
}
