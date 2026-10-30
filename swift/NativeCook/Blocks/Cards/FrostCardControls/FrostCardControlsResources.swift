import CoreImage
import CoreText
import SwiftUI
import UIKit

// The sharp card, seven-point blurred card, page and glyph runs are baked once at 3×.
final class FrostCardControlsResources {
  static let shared = FrostCardControlsResources()
  typealias T = FrostCardControlsTheme
  typealias Copy = T.Copy
  let card: UIImage
  let frosted: UIImage
  let page: UIImage
  private var labels: [String: UIImage] = [:]

  init() {
    for name in [
      "Inter-Regular", "Inter-Medium", "Inter-SemiBold", "Inter-Bold", "Poppins-SemiBold",
    ] {
      if let url = Bundle.main.url(forResource: "FrostCardControls-" + name, withExtension: "ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
      }
    }
    card = Self.bake(T.UI.card.size) { Self.drawCard($0) }
    let input = CIImage(cgImage: card.cgImage!)
    let blurred = input.clampedToExtent().applyingFilter(
      "CIGaussianBlur", parameters: [kCIInputRadiusKey: 21])
    let image = CIContext(options: [.workingColorSpace: CGColorSpace(name: CGColorSpace.sRGB)!])
      .createCGImage(blurred, from: input.extent)!
    frosted = UIImage(cgImage: image, scale: 3, orientation: .up)
    page = Self.bake(CGSize(width: T.REF_W, height: 1100)) { Self.drawPage($0) }
    for (copy, size) in [
      (Copy.tiles[0], 15.0), (Copy.tiles[1], 15), (Copy.tiles[2], 15),
      (Copy.unfreeze, 15), (Copy.frozen, 12),
    ] {
      let font = Self.font("Inter-SemiBold", size)
      let width = Self.width(copy, font)
      labels[copy] = Self.bake(CGSize(width: ceil(width) + 2, height: ceil(font.lineHeight) + 2)) {
        Self.text($0, copy, 0, font.ascender, font, 0xFFFFFF)
      }
    }
  }
  static func font(_ name: String, _ size: Double) -> UIFont {
    UIFont(name: name, size: size) ?? .systemFont(ofSize: size)
  }
  static func width(_ text: String, _ font: UIFont) -> Double {
    let line = CTLineCreateWithAttributedString(
      NSAttributedString(string: text, attributes: [.font: font, .kern: 0]))
    // SkFont.measureText returns the ink rectangle, rather than the text advance.
    return CTLineGetImageBounds(line, nil).width
  }
  static func uiColor(_ hex: Int, _ alpha: Double = 1) -> UIColor {
    UIColor(
      red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255,
      blue: Double(hex & 255) / 255, alpha: alpha)
  }
  static func bake(_ size: CGSize, _ draw: (CGContext) -> Void) -> UIImage {
    let format = UIGraphicsImageRendererFormat()
    format.scale = 3
    format.opaque = false
    format.preferredRange = .standard
    return UIGraphicsImageRenderer(size: size, format: format).image { draw($0.cgContext) }
  }
  static func text(
    _ c: CGContext, _ copy: String, _ x: Double, _ baseline: Double,
    _ font: UIFont, _ color: Int
  ) {
    let line = CTLineCreateWithAttributedString(
      NSAttributedString(
        string: copy,
        attributes: [
          .font: font, .kern: 0, .foregroundColor: uiColor(color),
        ]))
    c.saveGState()
    c.translateBy(x: x, y: baseline)
    c.scaleBy(x: 1, y: -1)
    c.textMatrix = .identity
    c.textPosition = .zero
    CTLineDraw(line, c)
    c.restoreGState()
  }
  static func round(_ c: CGContext, _ rect: CGRect, _ radius: Double, _ color: Int) {
    c.setFillColor(uiColor(color).cgColor)
    c.addPath(CGPath(roundedRect: rect, cornerWidth: radius, cornerHeight: radius, transform: nil))
    c.fillPath()
  }
  static func gradient(
    _ c: CGContext, _ colors: [Int], _ stops: [CGFloat], _ start: CGPoint, _ end: CGPoint
  ) {
    let g = CGGradient(
      colorsSpace: CGColorSpace(name: CGColorSpace.sRGB),
      colors: colors.map { uiColor($0).cgColor } as CFArray, locations: stops)!
    c.drawLinearGradient(
      g, start: start, end: end, options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
  }
  static func drawCard(_ c: CGContext) {
    let w = T.UI.card.width
    let h = T.UI.card.height
    let bounds = CGRect(x: 0, y: 0, width: w, height: h)
    let edge = CGPath(roundedRect: bounds, cornerWidth: 14, cornerHeight: 14, transform: nil)
    c.saveGState()
    c.addPath(edge)
    c.clip()
    gradient(
      c, [0xF4F4F6, 0xD9DADF, 0xEDEEF1, 0xC9CBD1], [0, 0.4, 0.7, 1], .zero, CGPoint(x: w, y: h))
    c.setLineWidth(0.5)
    for y in stride(from: 0.0, to: h, by: 1.6) {
      let alpha = max(0, 0.03 + 0.03 * sin(y * 3.7) * sin(y * 0.41))
      c.setStrokeColor(UIColor.white.withAlphaComponent((alpha * 1000).rounded() / 1000).cgColor)
      c.move(to: CGPoint(x: 0, y: y))
      c.addLine(to: CGPoint(x: w, y: y))
      c.strokePath()
    }
    let soft = CGGradient(
      colorsSpace: CGColorSpace(name: CGColorSpace.sRGB),
      colors: [
        UIColor.white.withAlphaComponent(0.55).cgColor, UIColor.white.withAlphaComponent(0).cgColor,
      ] as CFArray,
      locations: [0, 1])!
    let center = CGPoint(x: w * 0.42, y: h * 0.55)
    c.drawRadialGradient(
      soft, startCenter: center, startRadius: 0, endCenter: center, endRadius: w * 0.6,
      options: [.drawsAfterEndLocation])
    let size = 100 * (w + 6) / width(Copy.brand, font("Poppins-SemiBold", 100))
    c.beginTransparencyLayer(auxiliaryInfo: nil)
    gradient(c, [T.C.brandA, T.C.brandB, T.C.brandA], [0, 0.55, 1], .zero, CGPoint(x: w, y: 60))
    c.setBlendMode(.destinationIn)
    c.beginTransparencyLayer(auxiliaryInfo: nil)
    text(c, Copy.brand, -3, size * 0.6, font("Poppins-SemiBold", size), 0xFFFFFF)
    c.endTransparencyLayer()
    c.endTransparencyLayer()
    c.setBlendMode(.normal)
    text(
      c, Copy.physical, T.Card.physical.x, T.Card.physical.y + 5.4, font("Inter-Regular", 15),
      0x8F9097)
    text(c, Copy.last4, T.Card.last4.x, T.Card.last4.y + 6.1, font("Inter-Medium", 17), 0x26272B)
    let mark = T.Card.mark
    c.saveGState()
    c.addEllipse(in: CGRect(x: mark.x + 1, y: mark.y - 13, width: 26, height: 26))
    c.setLineWidth(6)
    c.replacePathWithStrokedPath()
    c.clip()
    gradient(
      c, [0xFFB020, 0xFF6A1A], [0, 1], CGPoint(x: mark.x, y: mark.y - 16),
      CGPoint(x: mark.x + 30, y: mark.y + 16))
    c.restoreGState()
    c.setFillColor(uiColor(0xE5262B).cgColor)
    c.fillEllipse(in: CGRect(x: mark.x - 13, y: mark.y - 11, width: 22, height: 22))
    c.restoreGState()
    c.addPath(edge)
    c.setLineWidth(0.8)
    c.setStrokeColor(UIColor.black.withAlphaComponent(0.08).cgColor)
    c.strokePath()
  }
  static func drawPage(_ c: CGContext) {
    text(c, Copy.balance, 41.5, 100.5, font("Inter-Bold", 25), T.C.ink)
    text(c, Copy.noLimit, 41.5, 127.8, font("Inter-Regular", 16), T.C.ink)
    let sf = font("Inter-SemiBold", 16)
    text(c, Copy.setLimit, 362.7 - width(Copy.setLimit, sf), 127.8, sf, T.C.blue)
    let hf = font("Inter-Medium", 15.5)
    let hx = 202 - width(Copy.hint, hf) / 2 + 12
    text(c, Copy.hint, hx, 432.6, hf, 0x7E7F86)
    c.saveGState()
    c.translateBy(x: hx - 18, y: 420)
    let hand = CGMutablePath()
    hand.move(to: CGPoint(x: 2, y: 9))
    hand.addLine(to: CGPoint(x: 2, y: 1.5))
    hand.addCurve(
      to: CGPoint(x: 3.6, y: 1.5), control1: CGPoint(x: 2, y: 0.4),
      control2: CGPoint(x: 3.6, y: 0.4))
    hand.addLine(to: CGPoint(x: 3.6, y: 6))
    hand.addLine(to: CGPoint(x: 3.6, y: 4.6))
    hand.addCurve(
      to: CGPoint(x: 5.2, y: 4.6), control1: CGPoint(x: 3.6, y: 3.5),
      control2: CGPoint(x: 5.2, y: 3.5))
    hand.addLine(to: CGPoint(x: 5.2, y: 6.2))
    hand.addLine(to: CGPoint(x: 5.2, y: 5.2))
    hand.addCurve(
      to: CGPoint(x: 6.8, y: 5.2), control1: CGPoint(x: 5.2, y: 4.1),
      control2: CGPoint(x: 6.8, y: 4.1))
    hand.addLine(to: CGPoint(x: 6.8, y: 6.6))
    hand.addLine(to: CGPoint(x: 6.8, y: 6))
    hand.addCurve(
      to: CGPoint(x: 8.4, y: 6), control1: CGPoint(x: 6.8, y: 4.9),
      control2: CGPoint(x: 8.4, y: 4.9))
    hand.addLine(to: CGPoint(x: 8.4, y: 10))
    hand.addCurve(
      to: CGPoint(x: 4.6, y: 14), control1: CGPoint(x: 8.4, y: 12.6),
      control2: CGPoint(x: 6.6, y: 14))
    hand.addLine(to: CGPoint(x: 4, y: 14))
    hand.addCurve(
      to: CGPoint(x: 1, y: 12), control1: CGPoint(x: 2.6, y: 14), control2: CGPoint(x: 1.8, y: 13.2)
    )
    hand.addLine(to: CGPoint(x: -0.6, y: 9.2))
    hand.addCurve(
      to: CGPoint(x: 1, y: 8.2), control1: CGPoint(x: -1.2, y: 8.2),
      control2: CGPoint(x: 0.2, y: 7.2))
    hand.closeSubpath()
    c.addPath(hand)
    c.setLineWidth(1.2)
    c.setLineJoin(.round)
    c.setStrokeColor(uiColor(0x7E7F86).cgColor)
    c.strokePath()
    c.restoreGState()
    let wb = T.UI.wallet
    round(c, wb, 16, T.C.button)
    let wf = font("Inter-SemiBold", 20)
    let tx = 201 - width(Copy.wallet, wf) / 2 + 18
    let cy = wb.midY
    text(c, Copy.wallet, tx, cy + 7, wf, 0xFFFFFF)
    let ix = tx - 44
    round(c, CGRect(x: ix, y: cy - 11, width: 30, height: 22), 4, 0xE9E2D2)
    for (i, color) in [0xF04E3E, 0xF9B233, 0x3DB36B, 0x3B82F6].enumerated() {
      c.setFillColor(uiColor(color).cgColor)
      c.fill(CGRect(x: ix + 2, y: cy - 9 + Double(i) * 3.2, width: 26, height: 3.2))
    }
    round(c, CGRect(x: ix, y: cy - 1, width: 30, height: 12), 3, 0xE9E2D2)
    round(c, T.UI.tx, 20, 0xFFFFFF)
    text(c, Copy.transactions, 41.5, 719, font("Inter-Bold", 22), T.C.ink)
    text(c, Copy.nothing, 41.5, 742.8, font("Inter-Regular", 16), T.C.ink)
    c.setFillColor(uiColor(0xEEF0F5).cgColor)
    c.fillEllipse(in: CGRect(x: 313, y: 697, width: 54, height: 54))
  }
  func label(
    _ copy: String, _ context: GraphicsContext, _ x: Double, _ baseline: Double,
    _ color: Int, _ alpha: Double = 1
  ) {
    guard let image = labels[copy] else { return }
    let font = Self.font("Inter-SemiBold", copy == T.Copy.frozen ? 12 : 15)
    var c = context
    c.opacity *= alpha
    let resolved = c.resolve(Image(uiImage: image))
    c.drawLayer { layer in
      layer.draw(
        resolved,
        in: CGRect(
          x: x, y: baseline - font.ascender, width: image.size.width, height: image.size.height))
      layer.blendMode = .sourceIn
      layer.fill(
        Path(
          CGRect(
            x: x, y: baseline - font.ascender, width: image.size.width, height: image.size.height)),
        with: .color(T.color(color)))
    }
  }
  func labelWidth(_ copy: String) -> Double {
    Self.width(copy, Self.font("Inter-SemiBold", copy == T.Copy.frozen ? 12 : 15))
  }
}
