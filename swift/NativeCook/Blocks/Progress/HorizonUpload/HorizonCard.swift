import UIKit
import CoreImage

/// Persistent native content over the scene's pane. Layout happens only on size or
/// font changes; the animation tick updates cached rasters and layer transforms.
@MainActor final class HorizonCard: UIView {
  let file = HorizonFile(), bar = HorizonSurface(.bar), number = HorizonNumber()
  let nameLabel = UILabel(), sizeLabel = UILabel()
  private let shadow = UIImageView()
  private static let phases = HorizonPhase.allCases
  private let coins = HorizonPhase.allCases.map { _ in UIImageView() }
  private let labels = HorizonPhase.allCases.map { _ in UIImageView() }
  private var labelRasters: [[UIImage]] = []
  private let cross = UIImageView(), arrow = UIImageView(), close = UIView()
  private var layout = HorizonLayout()
  override init(frame: CGRect) {
    super.init(frame: frame); isUserInteractionEnabled = false
    addSubview(shadow); addSubview(file); addSubview(nameLabel); addSubview(sizeLabel)
    addSubview(bar)
    for (i, phase) in Self.phases.enumerated() {
      coins[i].image = HorizonResources.image(phase.image)
      addSubview(coins[i]); addSubview(labels[i])
    }
    addSubview(number); addSubview(close)
    cross.image = HorizonResources.image("glyph-close"); arrow.image = HorizonResources.image("glyph-retry")
    close.addSubview(cross); close.addSubview(arrow)
    cross.frame = CGRect(x: 0, y: 0, width: 44, height: 44); arrow.frame = cross.frame
    nameLabel.text = "Project Brief.txt"; nameLabel.textColor = HorizonResources.ink; nameLabel.lineBreakMode = .byTruncatingMiddle
    sizeLabel.text = "97.23 KB"; sizeLabel.textColor = UIColor(red: 142 / 255, green: 151 / 255, blue: 174 / 255, alpha: 1)
  }
  @available(*, unavailable) required init?(coder: NSCoder) { fatalError() }
  func configure(_ l: HorizonLayout, scale: Double) {
    layout = l; bounds = CGRect(x: 0, y: 0, width: l.width, height: 240)
    nameLabel.font = .systemFont(ofSize: 18 * min(l.fontScale, 1.3), weight: .regular)
    nameLabel.attributedText = NSAttributedString(string: "Project Brief.txt", attributes: [.font: nameLabel.font!, .kern: -0.4])
    nameLabel.frame = CGRect(x: l.copyLeft, y: 68, width: l.copyWidth, height: 24 * min(l.fontScale, 1.3))
    sizeLabel.font = .monospacedDigitSystemFont(ofSize: 12 * min(l.fontScale, 1.3), weight: .regular)
    sizeLabel.frame = CGRect(x: l.copyLeft, y: 68 + 24 * min(l.fontScale, 1.3) + 2, width: l.copyWidth, height: 16 * min(l.fontScale, 1.3))
    file.bounds = CGRect(x: 0, y: 0, width: 116, height: 112)
    file.center = CGPoint(x: 24 + 58 * l.fileScale, y: 80)
    file.transform = CGAffineTransform(scaleX: l.fileScale, y: l.fileScale)
    close.bounds = CGRect(x: 0, y: 0, width: 44, height: 44); close.center = CGPoint(x: l.width - 26, y: 26)
    bar.frame = CGRect(x: 18, y: 182, width: l.barWidth + 36, height: 40)
    let font = HorizonResources.labelFont(15 * l.typeScale)
    labelRasters = HorizonPhase.allCases.map { HorizonTextRaster.images(HorizonL10n.t($0.rawValue), font: font,
      width: l.labelWidth, line: 20 * l.typeScale, scale: scale) }
    for i in 0..<5 {
      labels[i].frame = CGRect(x: 24 + 40 - 8, y: 158 + l.labelTop - 8, width: l.labelWidth + 16, height: 20 * l.typeScale + 16)
      coins[i].bounds = CGRect(x: 0, y: 0, width: 28.8, height: 28.8)
      coins[i].center = CGPoint(x: 24 + 12 + 9, y: 158 + l.glyphTop + 9)
    }
    number.frame = CGRect(x: l.width - 24 - 12 - 78 * l.typeScale, y: 158 + l.figureTop,
      width: 78 * l.typeScale, height: l.figureClipHeight)
    number.configure(l, scale: scale)
    makeShadow(l, scale: scale)
  }
  private func makeShadow(_ l: HorizonLayout, scale: Double) {
    let width = 84 * l.fileScale, height = 78 * l.fileScale
    let format = UIGraphicsImageRendererFormat(); format.scale = scale
    let image = UIGraphicsImageRenderer(size: CGSize(width: width + 36, height: height + 36), format: format).image { _ in
      UIColor.black.withAlphaComponent(0.18).setFill()
      UIBezierPath(roundedRect: CGRect(x: 18, y: 18, width: width, height: height), cornerRadius: 12).fill()
    }
    if let ci = CIImage(image: image), let cg = CIContext().createCGImage(ci.applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: 6 * scale]), from: ci.extent) {
      shadow.image = UIImage(cgImage: cg, scale: scale, orientation: .up)
    }
    shadow.frame = CGRect(x: 24 + 26 * l.fileScale - 18,
      y: 24 + 112 * (1 - l.fileScale) / 2 + 6 + 34 * l.fileScale - 18,
      width: width + 36, height: height + 36)
  }
  func update(_ m: HorizonUpload, ms: Double) {
    let l = layout, a = m.appearance
    center = CGPoint(x: l.x + l.width / 2 + m.shiftX.value, y: l.y + 120 + m.shiftY.value - 46 * m.lift)
    transform = CGAffineTransform(scaleX: m.cardScale, y: m.cardScale)
    file.center = CGPoint(x: 24 + 58 * l.fileScale + m.shiftX.value * 0.07, y: 80 + m.shiftY.value * 0.07)
    shadow.transform = CGAffineTransform(translationX: -m.shiftX.value * 0.07, y: -m.shiftY.value * 0.07)
    file.update(m)
    let light = 0.7 + 0.3 * HorizonMath.part(m.power.value, 0.1, 1)
    for (i, phase) in Self.phases.enumerated() {
      let show = HorizonMath.clamp(a.labels[i].value)
      labels[i].image = labelRasters[i][m.reduced ? 0 : Int(((1 - show) * 8).rounded())]
      labels[i].alpha = show * (phase == .done ? 1 : 0.82)
      labels[i].transform = CGAffineTransform(translationX: 0, y: m.reduced ? 0 : (m.label == phase ? 1 : -1) * (1 - show) * 4)
      coins[i].alpha = a.shows[i].value * light
      let s = m.reduced ? 1 : a.scales[i].value
      coins[i].transform = CGAffineTransform(rotationAngle: phase == .uploading && !m.reduced ? a.rotation * .pi / 180 : 0).scaledBy(x: s, y: s)
    }
    number.alpha = a.figure.value; number.update(m.percent, ms: ms, reduced: m.reduced)
    let out = HorizonMath.clamp(a.swap.value * 2), into = HorizonMath.clamp(a.swap.value * 2 - 1)
    cross.alpha = 1 - out; arrow.alpha = into
    cross.transform = CGAffineTransform(scaleX: m.reduced ? 1 : 1 - 0.2 * out, y: m.reduced ? 1 : 1 - 0.2 * out)
    arrow.transform = CGAffineTransform(scaleX: m.reduced ? 1 : 0.8 + 0.2 * into, y: m.reduced ? 1 : 0.8 + 0.2 * into)
    let pressure = 1 - 0.08 * HorizonMath.clamp(m.closePress.value)
    close.transform = CGAffineTransform(scaleX: pressure, y: pressure)
  }
}
