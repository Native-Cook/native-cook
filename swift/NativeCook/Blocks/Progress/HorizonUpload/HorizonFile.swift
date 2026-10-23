import UIKit

/// Three original raster layers, independently relit by Metal; the badge rotates
/// around its 82×52 pt body, not its transparent eight-point padding.
@MainActor final class HorizonFile: UIView {
  let sheet = HorizonSurface(.sheet), front = HorizonSurface(.front), back = HorizonSurface(.back)
  private let floating = UIView(), badge = UIView(), frontFace = UIView(), backFace = UIView()
  private let stamp = UILabel(), engraving = UILabel()
  override init(frame: CGRect) {
    super.init(frame: frame); isUserInteractionEnabled = false
    addSubview(floating); floating.frame = CGRect(x: 0, y: 0, width: 116, height: 112)
    floating.addSubview(sheet); sheet.frame = CGRect(x: 18, y: -8, width: 106, height: 128)
    floating.addSubview(badge); badge.frame = CGRect(x: 0, y: 41.2, width: 82, height: 52)
    for face in [frontFace, backFace] { badge.addSubview(face); face.frame = badge.bounds; face.layer.isDoubleSided = false }
    frontFace.addSubview(front); backFace.addSubview(back)
    front.frame = CGRect(x: -8, y: -8, width: 98, height: 68); back.frame = front.frame
    for label in [engraving, stamp] {
      frontFace.addSubview(label); label.frame = CGRect(x: 9, y: 13, width: 64, height: 26)
      label.font = .systemFont(ofSize: 20, weight: .heavy); label.textAlignment = .center
      label.attributedText = NSAttributedString(string: "TXT", attributes: [.kern: 0.2])
    }
    engraving.textColor = UIColor(red: 206 / 255, green: 224 / 255, blue: 1, alpha: 0.48)
    engraving.transform = CGAffineTransform(translationX: 0, y: 0.5)
    stamp.textColor = UIColor(red: 27 / 255, green: 38 / 255, blue: 71 / 255, alpha: 1)
  }
  @available(*, unavailable) required init?(coder: NSCoder) { fatalError() }
  func update(_ m: HorizonUpload) {
    let c = m.crest.value
    let amplitude = HorizonMath.part(m.power.value, 0.06, 1), angle = m.time / 3200 * 2 * .pi
    let rise = HorizonMath.part(c, 0.4, 0.51), settle = HorizonMath.part(c, 0.62, 1)
    let lift = -4 * sin(rise * .pi / 2) * pow(1 - settle, 2)
    floating.transform = CGAffineTransform(translationX: 0, y: m.reduced ? 0 : 2 * amplitude * sin(angle) + lift)
    badge.transform = CGAffineTransform(translationX: 0, y: m.reduced ? 0 : amplitude * sin(angle - .pi / 2))
    let turn = m.reduced || c <= 0 || c >= 1 ? HorizonMath.clamp(m.done.value) : HorizonMath.smooth(HorizonMath.part(c, 0.4, 0.62))
    frontFace.alpha = m.reduced ? 1 - turn : (turn < 0.5 ? 1 : 0)
    backFace.alpha = m.reduced ? turn : (turn >= 0.5 ? 1 : 0)
    var perspective = CATransform3DIdentity; perspective.m34 = -1 / 420
    frontFace.layer.transform = CATransform3DRotate(perspective, m.reduced ? 0 : turn * .pi, 1, 0, 0)
    backFace.layer.transform = CATransform3DRotate(perspective, m.reduced ? 0 : (turn - 1) * .pi, 1, 0, 0)
    stamp.alpha = 0.72 + 0.28 * amplitude; engraving.alpha = stamp.alpha
  }
  func render(_ m: HorizonUpload, layout: HorizonLayout) {
    HorizonRenderer.shared.draw(sheet, upload: m, layout: layout)
    if frontFace.alpha > 0 { HorizonRenderer.shared.draw(front, upload: m, layout: layout) }
    if backFace.alpha > 0 { HorizonRenderer.shared.draw(back, upload: m, layout: layout) }
  }
}
