import UIKit

/// The source block's native numeric engine, stepped by the shared clock instead
/// of a second display link. Glyph rasters and layer pairs are prepared at layout.
@MainActor final class HorizonNumber: UIView {
  private let engine = HorizonNumericGlyphTransitionEngine()
  private var typesetter = HorizonNumericGlyphTypesetter(font: .systemFont(ofSize: 26), letterSpacing: 0)
  private var spec = HorizonNumericNumericTextSpec(text: "0 %", alignment: .trailing,
    shape: .init(blurIntensity: 1, maxBlurRadius: 4, enterScale: 1, travelRatio: 0.22), duration: 0.22, bounce: 0)
  private var shown = -1, wasReduced = false
  private var images: [String: [HorizonNumericGlyphImage]] = [:]
  private var pairs: [HorizonNumberLayer] = []
  private var configured = false
  override init(frame: CGRect) {
    super.init(frame: frame); isUserInteractionEnabled = false; clipsToBounds = true
    // Three integer slots can have at most ten transient digit identities each,
    // plus the unchanged suffix. Recycle storage rather than allocate during ticks.
    for _ in 0..<32 {
      let pair = HorizonNumberLayer(); pairs.append(pair); layer.addSublayer(pair.window)
    }
  }
  @available(*, unavailable) required init?(coder: NSCoder) { fatalError() }
  func configure(_ l: HorizonLayout, scale: Double) {
    // Match NumericText's system regular face followed by its tabular-digit feature.
    let system = UIFont.systemFont(ofSize: 26 * l.typeScale, weight: .regular)
    let font = UIFont(descriptor: system.fontDescriptor.addingAttributes([
      .featureSettings: [[UIFontDescriptor.FeatureKey.type: kNumberSpacingType,
                          UIFontDescriptor.FeatureKey.selector: kMonospacedNumbersSelector]]
    ]), size: system.pointSize)
    typesetter = HorizonNumericGlyphTypesetter(font: font, letterSpacing: 0)
    images.removeAll(keepingCapacity: true)
    for unit in "0123456789 %" {
      let text = String(unit)
      let line = typesetter.typeset(text, decimalSeparator: ".", leftAnchored: false)
      guard let placement = line.placements.first else { continue }
      let glyph = HorizonNumericGlyphState(id: 0, unit: text, springs: .init(duration: 0.22, bounce: 0))
      glyph.updateMetrics(placement: placement, line: line, shape: spec.shape)
      let canvas = CGSize(width: glyph.advance + 2 * glyph.bleed, height: glyph.lineHeight + 2 * glyph.bleed)
      images[text] = (0...4).compactMap { radius in
        HorizonNumericGlyphImageCache.shared.image(unit: text, attributes: typesetter.attributes,
          color: HorizonResources.ink, size: canvas, blurRadius: radius, deviceScale: scale)
      }
    }
    engine.apply(spec: spec, typesetter: typesetter)
    configured = true
  }
  func update(_ number: Int, ms: Double, reduced: Bool) {
    guard configured else { return }
    if shown != number || wasReduced != reduced {
      shown = number; wasReduced = reduced
      spec.text = "\(number) %"; spec.animationsEnabled = !reduced
      engine.apply(spec: spec, typesetter: typesetter)
    }
    _ = engine.tick(min(ms / 1000, 1 / 15))
    // Retire missing identities before assigning new ones, so interruptions reuse
    // the original engine's presentation position, opacity and blur.
    for pair in pairs where pair.id != nil {
      if !engine.glyphs.contains(where: { $0.id == pair.id }) { pair.id = nil; pair.window.isHidden = true }
    }
    let anchorY = max((36 * typesetter.font.pointSize / 26 - engine.contentSize.height) / 2, 0)
    for glyph in engine.glyphs {
      guard let pair = pairs.first(where: { $0.id == glyph.id }) ?? pairs.first(where: { $0.id == nil }),
        let cached = images[glyph.unit], cached.count == 5 else { continue }
      pair.id = glyph.id; pair.window.isHidden = false
      let windowSize = CGSize(width: glyph.advance + glyph.bleed * 2, height: glyph.lineHeight)
      let canvas = CGSize(width: windowSize.width, height: glyph.lineHeight + glyph.bleed * 2)
      if pair.window.bounds.size != windowSize {
        pair.window.bounds = CGRect(origin: .zero, size: windowSize)
        pair.content.bounds = CGRect(origin: .zero, size: canvas)
        pair.content.position = CGPoint(x: windowSize.width / 2, y: windowSize.height / 2)
      }
      pair.window.position = CGPoint(x: bounds.width + glyph.x.value + glyph.advance / 2, y: anchorY + glyph.lineHeight / 2)
      pair.content.opacity = Float(glyph.opacity.value)
      pair.content.transform = CATransform3DMakeTranslation(0, glyph.offset.value * glyph.travel, 0)
      let radius = min(4, max(0, HorizonNumericGlyphImageCache.quantize(glyph.blur.value)))
      let raster = cached[radius]
      pair.content.contentsScale = raster.scale; pair.content.contents = raster.image
    }
  }
}
