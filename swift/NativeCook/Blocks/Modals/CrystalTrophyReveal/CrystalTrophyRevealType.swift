import CoreText
import UIKit

/// Glyph outlines use the same bundled Barlow faces and unkerned advances as the Skia canvas.
final class CrystalTrophyRevealType: UIView {
  var motion = CrystalTrophyRevealMotion(auto: true)
  var composition = CrystalTrophyRevealLayout(
    size: .init(width: 402, height: 874), insets: .init(top: 62, left: 0, bottom: 34, right: 0))
  private var cache: [String: [(CGPath?, Double)]] = [:]
  private static let registered: Void = {
    for face in ["ExtraBoldItalic", "SemiBoldItalic", "Bold", "Medium"] {
      if let url = Bundle.main.url(forResource: "BarlowCondensed-\(face)", withExtension: "ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
      }
    }
  }()

  override init(frame: CGRect) {
    super.init(frame: frame)
    _ = Self.registered
    backgroundColor = .clear
    isOpaque = false
    isUserInteractionEnabled = false
    accessibilityElementsHidden = true
  }

  required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

  private func glyphs(_ text: String, face: String, size: Double) -> [(CGPath?, Double)] {
    let key = "\(face)-\(size)-\(text)"
    if let saved = cache[key] { return saved }
    let font = CTFontCreateWithName("BarlowCondensed-\(face)" as CFString, size, nil)
    var characters = Array(text.utf16)
    var glyphs = [CGGlyph](repeating: 0, count: characters.count)
    CTFontGetGlyphsForCharacters(font, &characters, &glyphs, characters.count)
    var advances = [CGSize](repeating: .zero, count: glyphs.count)
    CTFontGetAdvancesForGlyphs(font, .horizontal, &glyphs, &advances, glyphs.count)
    let result = glyphs.enumerated().map { i, glyph in
      (CTFontCreatePathForGlyph(font, glyph, nil), Double(advances[i].width))
    }
    cache[key] = result
    return result
  }

  private func baseline(_ size: Double, face: String, center: Double) -> Double {
    let font = CTFontCreateWithName("BarlowCondensed-\(face)" as CFString, size, nil)
    return center + CTFontGetCapHeight(font) / 2
  }

  private func linePath(
    _ text: String, face: String, size: Double, tracking: Double, x: Double, y: Double
  ) -> CGPath {
    let letters = glyphs(text, face: face, size: size)
    let width = letters.reduce(0) { $0 + $1.1 } + Double(max(0, letters.count - 1)) * tracking
    var cursor = x - width / 2
    let path = CGMutablePath()
    for (glyph, advance) in letters {
      if let glyph {
        var transform = CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: cursor, ty: y)
        if let transformed = glyph.copy(using: &transform) { path.addPath(transformed) }
      }
      cursor += advance + tracking
    }
    return path
  }

  private func drawPath(_ path: CGPath, context: CGContext, alpha: Double, color: UIColor) {
    guard alpha > 0 else { return }
    context.saveGState()
    context.setAlpha(alpha)
    context.setFillColor(color.cgColor)
    context.addPath(path)
    context.fillPath()
    context.restoreGState()
  }

  override func draw(_ rect: CGRect) {
    guard let context = UIGraphicsGetCurrentContext() else { return }
    let f = composition
    let s = motion
    context.saveGState()
    context.translateBy(x: f.originX, y: f.titleY)
    context.rotate(by: f.titleTilt)
    context.translateBy(x: -f.originX, y: -f.titleY)
    var letterIndex = 0
    let allTitle = CGMutablePath()
    for (lineIndex, text) in ["PERFECT", "MONTH"].enumerated() {
      let letters = glyphs(text, face: "ExtraBoldItalic", size: f.titleSize)
      let width = letters.reduce(0) { $0 + $1.1 } + Double(letters.count - 1) * 0.5
      var cursor = f.originX - width / 2
      let y = f.titleY - 7 * f.k + Double(lineIndex) * f.titleLine
      for (glyph, advance) in letters {
        let age = (s.titleTime - 2.66 - Double(letterIndex) * 0.038) / 0.42
        let p = CrystalTrophyRevealMotion.enter(age)
        let alpha = s.reduced ? s.ui : CrystalTrophyRevealMotion.enter(age / 0.45) * s.ui
        let dy = s.reduced ? 0 : -26 * f.k * (1 - p) + 8 * (1 - s.ui)
        if let glyph {
          var transform = CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: cursor, ty: y + dy)
          if let path = glyph.copy(using: &transform) {
            allTitle.addPath(path)
            context.saveGState()
            context.setAlpha(alpha)
            context.setShadow(
              offset: CGSize(width: 0, height: 6), blur: 12,
              color: UIColor(red: 0, green: 5 / 255, blue: 35 / 255, alpha: 0.45).cgColor)
            context.addPath(path)
            context.setFillColor(UIColor.white.cgColor)
            context.fillPath()
            context.setShadow(offset: .zero, blur: 0, color: nil)
            context.addPath(path)
            context.clip()
            let colors =
              [
                UIColor.white.cgColor,
                UIColor(red: 220 / 255, green: 230 / 255, blue: 1, alpha: 1).cgColor,
              ] as CFArray
            let gradient = CGGradient(
              colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0, 1])!
            context.drawLinearGradient(
              gradient, start: CGPoint(x: 0, y: y - f.titleSize * 0.72), end: CGPoint(x: 0, y: y),
              options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
            context.restoreGState()
          }
        }
        cursor += advance + 0.5
        letterIndex += 1
      }
    }
    let landed = 2.66 + 11 * 0.038 + 0.42
    if !s.reduced && s.titleTime >= landed {
      let initial = s.titleTime < landed + 0.52
      let alpha = initial ? 0.65 * s.ui : s.flashAge < 0.52 ? 0.34 * s.ui : 0
      if alpha > 0 {
        let age = initial ? s.titleTime - landed : s.flashAge
        let x = f.originX - f.titleSize * 2.8 + age / 0.52 * f.titleSize * 5.6
        context.saveGState()
        context.setAlpha(alpha)
        context.addPath(allTitle)
        context.clip()
        let colors =
          [
            UIColor.white.withAlphaComponent(0).cgColor,
            UIColor(red: 188 / 255, green: 220 / 255, blue: 1, alpha: 0.4).cgColor,
            UIColor.white.cgColor,
            UIColor(red: 188 / 255, green: 220 / 255, blue: 1, alpha: 0.4).cgColor,
            UIColor.white.withAlphaComponent(0).cgColor,
          ] as CFArray
        let gradient = CGGradient(
          colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors,
          locations: [0, 0.3, 0.5, 0.7, 1])!
        context.drawLinearGradient(
          gradient, start: CGPoint(x: x, y: f.titleY - f.titleSize),
          end: CGPoint(x: x + 45 * f.k, y: f.titleY + f.titleSize), options: [])
        context.restoreGState()
      }
    }
    let dateAlpha = s.uiProgress(3.12)
    let dateY =
      baseline(f.dateSize, face: "SemiBoldItalic", center: f.dateY)
      + (s.reduced ? 0 : 8 * (1 - dateAlpha))
    drawPath(
      linePath(
        "Won on Oct 13, 2026", face: "SemiBoldItalic", size: f.dateSize, tracking: 0, x: f.originX,
        y: dateY), context: context, alpha: dateAlpha,
      color: UIColor(red: 229 / 255, green: 235 / 255, blue: 1, alpha: 1))
    context.restoreGState()

    let bodyAlpha = s.uiProgress(3.26)
    // Match React Native’s centered 22-point line box with NSString’s baseline placement.
    let bodyY = f.bodyTop - 1 + (s.reduced ? 0 : 8 * (1 - bodyAlpha))
    let paragraph = NSMutableParagraphStyle()
    paragraph.alignment = .center
    paragraph.minimumLineHeight = 22
    paragraph.maximumLineHeight = 22
    let attributes: [NSAttributedString.Key: Any] = [
      .font: UIFont.systemFont(ofSize: 16),
      .foregroundColor: UIColor(red: 222 / 255, green: 229 / 255, blue: 1, alpha: 0.78 * bodyAlpha),
      .paragraphStyle: paragraph,
    ]
    ("Thirty days, thirty sessions.\nNot a single one missed." as NSString).draw(
      in: CGRect(x: 24, y: bodyY, width: f.width - 48, height: 48), withAttributes: attributes)

    let controlAlpha = s.uiProgress(3.4, 0.42)
    context.saveGState()
    context.translateBy(
      x: f.share.midX, y: f.share.midY + (s.reduced ? 0 : 16 * (1 - controlAlpha)))
    context.scaleBy(x: 1 - 0.03 * s.sharePress, y: 1 - 0.03 * s.sharePress)
    context.translateBy(x: -f.share.midX, y: -f.share.midY)
    let labelY = baseline(22, face: "Bold", center: f.share.midY)
    let normalY = labelY + (s.reduced ? 0 : -10 * s.copied)
    let copiedY = labelY + (s.reduced ? 0 : 10 * (1 - s.copied))
    let labelColor = UIColor(red: 245 / 255, green: 248 / 255, blue: 1, alpha: 1)
    drawPath(
      linePath("SHARE", face: "Bold", size: 22, tracking: 3.5, x: f.originX, y: normalY),
      context: context, alpha: controlAlpha * (1 - s.copied), color: labelColor)
    drawPath(
      linePath("LINK COPIED", face: "Bold", size: 22, tracking: 3.5, x: f.originX + 10, y: copiedY),
      context: context, alpha: controlAlpha * s.copied, color: labelColor)
    if s.copied > 0 {
      let copiedWidth =
        glyphs("LINK COPIED", face: "Bold", size: 22).reduce(0) { $0 + $1.1 } + 10 * 3.5
      let x = f.originX - copiedWidth / 2 - 13
      let y = f.share.midY + (s.reduced ? 0 : 10 * (1 - s.copied))
      context.setStrokeColor(labelColor.withAlphaComponent(controlAlpha * s.copied).cgColor)
      context.setLineWidth(1.7)
      context.setLineCap(.round)
      context.setLineJoin(.round)
      context.move(to: CGPoint(x: x, y: y))
      let progress = s.check
      let firstLength = sqrt(32.0)
      let secondLength = sqrt(145.0)
      let distance = (firstLength + secondLength) * progress
      if distance <= firstLength {
        context.addLine(
          to: CGPoint(x: x + 4 * distance / firstLength, y: y + 4 * distance / firstLength))
      } else {
        context.addLine(to: CGPoint(x: x + 4, y: y + 4))
        let part = (distance - firstLength) / secondLength
        context.addLine(to: CGPoint(x: x + 4 + 8 * part, y: y + 4 - 9 * part))
      }
      context.strokePath()
    }
    context.restoreGState()
    if s.phase == .closed {
      drawPath(
        linePath(
          "TAP TO REVEAL", face: "Medium", size: 13, tracking: 3, x: f.originX, y: f.share.midY),
        context: context, alpha: s.hint,
        color: UIColor(red: 222 / 255, green: 229 / 255, blue: 1, alpha: 0.56))
    }
    if s.veil > 0 {
      context.setFillColor(UIColor.black.withAlphaComponent(s.veil).cgColor)
      context.fill(bounds)
    }
  }
}
