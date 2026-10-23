import CoreText
import UIKit

/// Bundled Inter outlines, with the reference's baseline metrics and growing digit cells.
final class GlassPeakChartTypeView: UIView {
  var picture = GlassPeakChartFrame()
  private static let regular = font("Regular", size: 12)
  private static let label = font("Regular", size: 13)
  private static let caption = font("Regular", size: 14)
  private static let bold = font("Bold", size: 68)
  private static let digitPaths = Array("0123456789%").map { outline(String($0), font: bold) }
  private static let digitWidths = Array("0123456789%").map { width(String($0), font: bold) }
  private static let cell = digitWidths.prefix(10).max()! - 2
  private var paths: [String: CGPath] = [:]

  override init(frame: CGRect) {
    super.init(frame: frame)
    isOpaque = false
    backgroundColor = .clear
    isAccessibilityElement = false
  }

  required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

  private static func font(_ weight: String, size: CGFloat) -> CTFont {
    guard
      let url = Bundle.main.url(
        forResource: "GlassPeakChart-Inter-\(weight)", withExtension: "ttf"),
      let provider = CGDataProvider(url: url as CFURL), let font = CGFont(provider)
    else {
      preconditionFailure("Missing bundled Glass Peak Chart Inter font")
    }
    return CTFontCreateWithGraphicsFont(font, size, nil, nil)
  }

  private static func width(_ text: String, font: CTFont) -> CGFloat {
    let chars = Array(text.utf16)
    var glyphs = [CGGlyph](repeating: 0, count: chars.count)
    CTFontGetGlyphsForCharacters(font, chars, &glyphs, chars.count)
    return CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, nil, glyphs.count)
  }

  private static func outline(_ text: String, font: CTFont) -> CGPath {
    let chars = Array(text.utf16)
    var glyphs = [CGGlyph](repeating: 0, count: chars.count)
    var advances = [CGSize](repeating: .zero, count: chars.count)
    CTFontGetGlyphsForCharacters(font, chars, &glyphs, chars.count)
    CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &advances, glyphs.count)
    let path = CGMutablePath()
    var x: CGFloat = 0
    for i in glyphs.indices {
      if let glyph = CTFontCreatePathForGlyph(font, glyphs[i], nil) {
        path.addPath(glyph, transform: CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: x, ty: 0))
      }
      x += advances[i].width
    }
    return path
  }

  private func text(
    _ text: String, font: CTFont, x: CGFloat? = nil, baseline: CGFloat,
    alpha: Double, context: CGContext, shadow: Bool = false
  ) {
    guard alpha > 0 else { return }
    let key = "\(CTFontGetSize(font))-\(text)"
    let path = paths[key] ?? Self.outline(text, font: font)
    paths[key] = path
    context.saveGState()
    context.translateBy(x: x ?? (201 - Self.width(text, font: font) / 2), y: baseline)
    context.setAlpha(alpha * picture.presented)
    if shadow {
      context.setShadow(
        offset: .zero, blur: 6,
        color: UIColor(red: 0, green: 12 / 255, blue: 3 / 255, alpha: 0.35).cgColor)
      context.setFillColor(UIColor(red: 0, green: 12 / 255, blue: 3 / 255, alpha: 0.35).cgColor)
    } else {
      context.setFillColor(UIColor.white.cgColor)
    }
    context.addPath(path)
    context.fillPath()
    context.restoreGState()
  }

  private func around(_ context: CGContext, x: Double, y: Double, scale: Double, body: () -> Void) {
    context.saveGState()
    context.translateBy(x: x, y: y)
    context.scaleBy(x: scale, y: scale)
    context.translateBy(x: -x, y: -y)
    body()
    context.restoreGState()
  }

  static func soft(_ value: Double, _ low: Double, _ high: Double) -> Double {
    let a = max(0, min(1, (value - low) / (high - low)))
    return a * a * (3 - 2 * a)
  }

  override func draw(_ rect: CGRect) {
    guard let context = UIGraphicsGetCurrentContext() else { return }
    let f = picture
    let periods = GlassPeakChartSimulation.PERIODS
    let previous = periods[Int(f.periodPrev)]
    let current = periods[Int(f.period)]
    let status = ["Data updated just now", "Updating…"]
    around(context, x: 58, y: 232, scale: 1 - 0.03 * f.refreshPress) {
      text(
        status[Int(f.statusPrev)], font: Self.regular, x: 72, baseline: 236 - 6 * f.statusMix,
        alpha: 0.62 * (1 - Self.soft(f.statusMix, 0, 0.6)), context: context)
      text(
        status[Int(f.status)], font: Self.regular, x: 72, baseline: 236 + 6 * (1 - f.statusMix),
        alpha: 0.62 * Self.soft(f.statusMix, 0.4, 1), context: context)
    }
    around(context, x: 296, y: 232, scale: 1 - 0.03 * f.pillPress) {
      context.clip(to: CGRect(x: 250, y: 218, width: 71, height: 27))
      text(
        previous.label, font: Self.label, x: 258, baseline: 237 - 8 * f.periodMix,
        alpha: 0.88 * (1 - Self.soft(f.periodMix, 0, 0.6)), context: context)
      text(
        current.label, font: Self.label, x: 258, baseline: 237 + 8 * (1 - f.periodMix),
        alpha: 0.88 * Self.soft(f.periodMix, 0.4, 1), context: context)
    }
    text("Conversion rate", font: Self.caption, baseline: 512, alpha: 0.78, context: context)
    drawNumber(context)
    let oldAlpha = 1 - Self.soft(f.periodMix, 0, 0.5)
    let newAlpha = Self.soft(f.periodMix, 0.5, 1)
    text(
      f.periodMix < 0.5 ? previous.sub : current.sub, font: Self.regular, baseline: 604,
      alpha: oldAlpha + newAlpha, context: context, shadow: true)
    text(previous.sub, font: Self.regular, baseline: 602, alpha: 0.86 * oldAlpha, context: context)
    text(current.sub, font: Self.regular, baseline: 602, alpha: 0.86 * newAlpha, context: context)
  }

  private func drawNumber(_ context: CGContext) {
    let f = picture
    let value = max(0, min(999, f.value))
    let rounded = Int(value.rounded())
    let tens = Self.soft(value, 8, 12)
    let hundreds = Self.soft(value, 90, 110)
    let total = Self.cell * (1 + tens + hundreds) + Self.digitWidths[10] - 2
    var x = 201 - total / 2
    let cells = [hundreds, tens, 1]
    let digits = [rounded / 100, (rounded / 10) % 10, rounded % 10, 10]
    var slots: [(CGPath, Double)] = []
    for i in 0..<4 {
      let visible = i >= 2 || (i == 0 ? rounded >= 100 : rounded >= 10)
      if visible {
        let position = i == 3 ? x - 2 : x + (Self.cell * cells[i] - Self.digitWidths[digits[i]]) / 2
        var transform = CGAffineTransform(translationX: position, y: 576)
        let path = Self.digitPaths[digits[i]].copy(using: &transform)!
        let alpha = i == 0 ? Self.soft(value, 99.5, 100) : i == 1 ? Self.soft(value, 9.5, 10) : 1
        slots.append((path, alpha))
      }
      if i < 3 { x += Self.cell * cells[i] }
    }
    around(context, x: 201, y: 552, scale: f.numberScale) {
      if f.numberGlow > 0.01 {
        context.saveGState()
        context.setAlpha(f.numberAlpha * f.presented * f.numberGlow * 0.46)
        context.setBlendMode(.plusLighter)
        let green = UIColor(red: 43 / 255, green: 1, blue: 90 / 255, alpha: 1).cgColor
        context.setShadow(offset: .zero, blur: 9, color: green)
        context.setFillColor(green)
        for (path, _) in slots { context.addPath(path) }
        context.fillPath()
        context.restoreGState()
      }
      let colors = [
        UIColor(red: 246 / 255, green: 1, blue: 248 / 255, alpha: 1).cgColor,
        UIColor(red: 207 / 255, green: 1, blue: 217 / 255, alpha: 1).cgColor,
      ]
      let gradient = CGGradient(
        colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors as CFArray, locations: [0, 1])!
      for (path, alpha) in slots {
        context.saveGState()
        context.setAlpha(f.numberAlpha * f.presented * alpha)
        context.addPath(path)
        context.clip()
        context.drawLinearGradient(
          gradient, start: CGPoint(x: 201, y: 521), end: CGPoint(x: 201, y: 576),
          options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
        context.restoreGState()
      }
    }
  }
}
