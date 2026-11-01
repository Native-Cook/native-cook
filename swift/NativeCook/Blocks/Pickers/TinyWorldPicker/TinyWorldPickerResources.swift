import SwiftUI
import CoreText
import UIKit

final class TinyWorldPickerResources {
  struct Label {
    let image: UIImage
    let width, height: Double
  }
  static let shared = TinyWorldPickerResources()
  var labels: [String: Label] = [:]
  private var paths: [String: Path] = [:]
  private var staticLayers: [String: UIImage] = [:]
  init() {
    for name in ["TitanOne-Regular", "Nunito-Black", "Nunito-ExtraBold", "Ionicons", "MaterialCommunityIcons"] {
      if let url = Bundle.main.url(forResource: "TinyWorldPicker-\(name)", withExtension: "ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
      }
    }
  }
  func rect(_ rect: CGRect) -> Path {
    let key = "r|\(rect)"
    if let path = paths[key] { return path }
    let path = Path(rect); paths[key] = path; return path
  }
  func ellipse(_ rect: CGRect) -> Path {
    let key = "e|\(rect)"
    if let path = paths[key] { return path }
    let path = Path(ellipseIn: rect); paths[key] = path; return path
  }
  func rounded(_ rect: CGRect, _ radius: Double) -> Path {
    let key = "c|\(rect)|\(radius)"
    if let path = paths[key] { return path }
    let path = Path(roundedRect: rect, cornerRadius: radius); paths[key] = path; return path
  }
  func excluding(_ crop: CGRect, _ size: CGSize) -> Path {
    let key = "mask|\(crop)|\(size)"
    if let path = paths[key] { return path }
    var path = Path(CGRect(origin: .zero, size: size)); path.addRect(crop)
    paths[key] = path; return path
  }
  // Explicit quarter-circle Beziers keep UIKit's rounded-path implementation from
  // resolving square control backgrounds as capsules on newer simulator runtimes.
  static func bezier(_ rect: CGRect, _ radius: Double) -> UIBezierPath {
    let r = min(radius, min(rect.width, rect.height) / 2), k = 0.5522847498307936 * r
    let l = rect.minX, t = rect.minY, b = rect.maxY, right = rect.maxX
    let p = CGMutablePath()
    p.move(to: CGPoint(x: l + r, y: t)); p.addLine(to: CGPoint(x: right - r, y: t))
    p.addCurve(to: CGPoint(x: right, y: t + r), control1: CGPoint(x: right - r + k, y: t), control2: CGPoint(x: right, y: t + r - k))
    p.addLine(to: CGPoint(x: right, y: b - r))
    p.addCurve(to: CGPoint(x: right - r, y: b), control1: CGPoint(x: right, y: b - r + k), control2: CGPoint(x: right - r + k, y: b))
    p.addLine(to: CGPoint(x: l + r, y: b))
    p.addCurve(to: CGPoint(x: l, y: b - r), control1: CGPoint(x: l + r - k, y: b), control2: CGPoint(x: l, y: b - r + k))
    p.addLine(to: CGPoint(x: l, y: t + r))
    p.addCurve(to: CGPoint(x: l + r, y: t), control1: CGPoint(x: l, y: t + r - k), control2: CGPoint(x: l + r - k, y: t))
    p.closeSubpath()
    return UIBezierPath(cgPath: p)
  }
  static func color(_ rgb: UInt32, _ alpha: Double = 1) -> UIColor {
    UIColor(red: Double((rgb >> 16) & 255) / 255, green: Double((rgb >> 8) & 255) / 255,
            blue: Double(rgb & 255) / 255, alpha: alpha)
  }
  func label(_ string: String, _ family: String, _ size: Double, _ rgb: UInt32 = 0xFFFFFF,
             alpha: Double = 1, spacing: Double = 0, lineHeight: Double? = nil) -> Label {
    let key = "\(string)|\(family)|\(size)|\(rgb)|\(alpha)|\(spacing)|\(lineHeight ?? 0)"
    if let cached = labels[key] { return cached }
    let base = UIFont(name: family, size: size) ?? UIFont.systemFont(ofSize: size)
    let tabular = (family == "Nunito-Black" || family == "Nunito-ExtraBold") && (size == 13 || size == 14)
    let font = tabular ? UIFont(descriptor: base.fontDescriptor.addingAttributes([
      .featureSettings: [[UIFontDescriptor.FeatureKey.type: kNumberSpacingType,
                          UIFontDescriptor.FeatureKey.selector: kMonospacedNumbersSelector]]
    ]), size: size) : base
    var attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: Self.color(rgb, alpha)]
    if spacing != 0 { attributes[.kern] = spacing }
    if let lineHeight {
      let paragraph = NSMutableParagraphStyle()
      paragraph.minimumLineHeight = lineHeight; paragraph.maximumLineHeight = lineHeight
      attributes[.paragraphStyle] = paragraph
      if lineHeight >= font.lineHeight { attributes[.baselineOffset] = (lineHeight - font.lineHeight) / 2 }
    }
    let storage = NSTextStorage(string: string, attributes: attributes)
    let layout = NSLayoutManager(); layout.usesFontLeading = false
    let container = NSTextContainer(size: CGSize(width: 10000, height: 1000)); container.lineFragmentPadding = 0
    layout.addTextContainer(container); storage.addLayoutManager(layout)
    layout.ensureLayout(for: container)
    let used = layout.usedRect(for: container)
    let width = ceil(used.width * 3) / 3, height = ceil(used.height * 3) / 3
    let format = UIGraphicsImageRendererFormat(); format.scale = 3; format.opaque = false
    let image = UIGraphicsImageRenderer(size: CGSize(width: max(1, width), height: height), format: format).image { _ in
      layout.drawGlyphs(forGlyphRange: layout.glyphRange(for: container), at: .zero)
    }
    let label = Label(image: image, width: width, height: height)
    labels[key] = label
    return label
  }
  func icon(_ code: Int, _ size: Double, _ rgb: UInt32 = 0xCFEFF4, _ alpha: Double = 1, material: Bool = false) -> Label {
    label(String(UnicodeScalar(code)!), material ? "MaterialDesignIcons" : "Ionicons", size, rgb, alpha: alpha, lineHeight: size)
  }
  func staticLayer(_ size: CGSize, _ top: Double, _ bottom: Double, _ scale: Double) -> UIImage {
    let key = "\(size)|\(top)|\(bottom)|\(scale)"
    if let image = staticLayers[key] { return image }
    let format = UIGraphicsImageRendererFormat(); format.scale = scale; format.opaque = false
    let image = UIGraphicsImageRenderer(size: size, format: format).image { renderer in
      let c = renderer.cgContext
      let w = size.width, h = size.height, cy = (h * 0.47).rounded()
      let border = 2 / scale
      func text(_ label: Label, _ x: Double, _ y: Double) {
        label.image.draw(in: CGRect(x: x, y: y, width: label.width, height: label.height))
      }
      func center(_ label: Label, _ x: Double, _ y: Double) {
        text(label, x - label.width / 2, y - label.height / 2)
      }
      func glass(_ rect: CGRect, _ radius: Double) {
        let path = Self.bezier(rect, radius)
        Self.color(0x071A21, 0.62).setFill(); path.fill()
        Self.color(0xA0E6F0, 0.16).setStroke()
        let inset = Self.bezier(rect.insetBy(dx: border / 2, dy: border / 2), radius - border / 2)
        inset.lineWidth = border; inset.stroke()
      }
      let hudY = top + 6
      let ring = UIBezierPath(ovalIn: CGRect(x: 16, y: hudY, width: 42, height: 42))
      Self.color(0x0B2129).setFill(); ring.fill()
      Self.color(0xFFD84A).setStroke()
      let ringStroke = UIBezierPath(ovalIn: CGRect(x: 17.5, y: hudY + 1.5, width: 39, height: 39))
      ringStroke.lineWidth = 3; ringStroke.stroke()
      center(label("20", "TitanOne", 16), 37, hudY + 21.5)
      let energy = label("17,963", "Nunito-Black", 14)
      let energyW = border * 2 + 20 + 14 + 64 + energy.width + 12
      glass(CGRect(x: 66, y: hudY + 5, width: energyW, height: 32), 16)
      let energyX = 66 + border + 10
      center(icon(62230, 14, 0xFFD84A), energyX + 7, hudY + 21)
      let track = CGRect(x: energyX + 20, y: hudY + 17, width: 64, height: 8)
      UIColor.white.withAlphaComponent(0.12).setFill(); Self.bezier(track, 4).fill()
      Self.color(0xFFD84A).setFill(); Self.bezier(CGRect(x: track.minX, y: track.minY, width: 64 * 0.72, height: 8), 4).fill()
      text(energy, track.maxX + 6, hudY + 21 - energy.height / 2)
      let coins = label("13,401,935", "Nunito-Black", 14)
      let coinW = border * 2 + 24 + 16 + 6 + coins.width
      glass(CGRect(x: w - 16 - coinW, y: hudY, width: coinW, height: 32), 16)
      let coinX = w - 16 - coinW + border + 12
      Self.color(0xFFC531).setFill(); c.fillEllipse(in: CGRect(x: coinX, y: hudY + 8, width: 16, height: 16))
      Self.color(0xE39A12).setStroke(); c.setLineWidth(1.5)
      c.strokeEllipse(in: CGRect(x: coinX + 4.75, y: hudY + 12.75, width: 6.5, height: 6.5))
      text(coins, coinX + 22, hudY + 16 - coins.height / 2)
      let stars = label("18,313", "Nunito-Black", 14)
      let maxStars = label(" / 20,000", "Nunito-ExtraBold", 14, 0xCFEFF4, alpha: 0.55)
      let starsW = border * 2 + 24 + 13 + 6 + stars.width + maxStars.width
      glass(CGRect(x: w - 16 - starsW, y: hudY + 40, width: starsW, height: 32), 16)
      let starsX = w - 16 - starsW + border + 12
      center(icon(62869, 13, 0xFFB84A), starsX + 6.5, hudY + 56)
      text(stars, starsX + 19, hudY + 56 - stars.height / 2)
      text(maxStars, starsX + 19 + stars.width, hudY + 56 - maxStars.height / 2)
      for (index, code) in [61916, 62264, 62531].enumerated() {
        let y = cy - 176 + Double(index) * 56
        glass(CGRect(x: w - 60, y: y, width: 44, height: 44), 15)
        center(icon(code, 21), w - 38, y + 22)
      }
      let by = h - bottom - 12 - 56
      glass(CGRect(x: 16, y: by + 2, width: 52, height: 52), 18)
      glass(CGRect(x: w - 68, y: by + 2, width: 52, height: 52), 18)
      center(icon(62361, 24), 42, by + 28)
      center(icon(62294, 24), w - 42, by + 28)
      let cta = CGRect(x: 80, y: by, width: w - 160, height: 56)
      Self.color(0xFFD84A).setFill(); Self.bezier(cta, 28).fill()
      c.saveGState(); Self.bezier(cta, 28).addClip()
      Self.color(0xD9A21E).setFill(); c.fill(CGRect(x: 80, y: by + 52, width: w - 160, height: 4)); c.restoreGState()
      let badgeLabel = label("2", "Nunito-Black", 11)
      let badgeWidth = max(20, badgeLabel.width + 14)
      // Yoga positions an absolute child from the parent's inside border edge.
      let badge = CGRect(x: w - 11 - border - badgeWidth, y: by - 3 + border, width: badgeWidth, height: 20)
      Self.color(0xFF5A4E).setFill(); Self.bezier(badge, 10).fill()
      Self.color(0x0B2129).setStroke(); let bp = Self.bezier(badge.insetBy(dx: 1, dy: 1), 9); bp.lineWidth = 2; bp.stroke()
      center(badgeLabel, badge.midX, badge.midY)
    }
    staticLayers[key] = image
    return image
  }
}
