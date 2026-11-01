import CoreText
import ImageIO
import SwiftUI
import UIKit

// The same bundled font files and decoded badge pixels as RN. Static card faces are cached once.
@MainActor final class TrophyFanCaseResources {
  static let shared = TrophyFanCaseResources()
  let fronts: [Image]
  let backs: [Image]
  init() {
    for weight in ["Regular", "Medium", "SemiBold"] {
      if let url = Self.url("Archivo-\(weight)", "ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
      }
    }
    func loadBadge(_ name: String) -> UIImage {
      guard let url = Self.url("badge-\(name)", "png"),
            let src = CGImageSourceCreateWithURL(url as CFURL, nil),
            let cg = CGImageSourceCreateImageAtIndex(src, 0, [kCGImageSourceShouldCacheImmediately: true] as CFDictionary)
      else { return UIImage() }
      return UIImage(cgImage: cg)
    }
    var front: [Image] = [], back: [Image] = []
    for data in TrophyFanCaseTheme.trophies {
      let badge = loadBadge(data.badge)
      for isBack in [false, true] {
        let image = Image(uiImage: Self.face(data, badge, isBack))
        if isBack { back.append(image) } else { front.append(image) }
      }
    }
    fronts = front
    backs = back
  }
  // Use the same TextKit layout as RCTTextLayoutManager, once per cached face.
  private static func text(_ value: String, _ x: Double, _ y: Double, _ width: Double,
                           _ size: Double, _ lineHeight: Double, _ weight: String = "Regular",
                           _ color: UIColor = .white, _ tracking: Double = 0,
                           _ tabular: Bool = false, lines: Int = 1, draw: Bool = true) -> CGSize {
    var font = UIFont(name: "Archivo-\(weight)", size: size) ?? .systemFont(ofSize: size)
    if tabular {
      font = UIFont(descriptor: font.fontDescriptor.addingAttributes([
        .featureSettings: [[UIFontDescriptor.FeatureKey.type: kNumberSpacingType,
                            UIFontDescriptor.FeatureKey.selector: kMonospacedNumbersSelector]]
      ]), size: size)
    }
    let paragraph = NSMutableParagraphStyle()
    paragraph.minimumLineHeight = lineHeight
    paragraph.maximumLineHeight = lineHeight
    var attributes: [NSAttributedString.Key: Any] = [
      .font: font, .foregroundColor: color, .paragraphStyle: paragraph,
      .baselineOffset: max(0, (lineHeight - font.lineHeight) / 2)
    ]
    // An explicit zero kern disables the font's own pair kerning, unlike RN's unset tracking.
    if tracking != 0 { attributes[.kern] = tracking }
    let storage = NSTextStorage(string: value, attributes: attributes)
    let container = NSTextContainer(size: CGSize(width: width, height: 500))
    container.lineFragmentPadding = 0
    container.maximumNumberOfLines = lines
    container.lineBreakMode = lines > 0 ? .byTruncatingTail : .byClipping
    let manager = NSLayoutManager()
    manager.usesFontLeading = false
    manager.addTextContainer(container)
    storage.addLayoutManager(manager)
    let range = manager.glyphRange(for: container)
    let used = manager.usedRect(for: container).size
    if draw { manager.drawGlyphs(forGlyphRange: range, at: CGPoint(x: x, y: y)) }
    return CGSize(width: ceil(used.width * 3) / 3, height: ceil(used.height * 3) / 3)
  }
  private static func face(_ data: TrophyFanCaseTheme.Trophy, _ badge: UIImage, _ back: Bool) -> UIImage {
    let format = UIGraphicsImageRendererFormat()
    format.scale = 3
    format.opaque = false
    format.preferredRange = .standard
    func color(_ hex: Int, _ alpha: Double = 1) -> UIColor {
      UIColor(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
              blue: Double(hex & 255) / 255, alpha: alpha)
    }
    return UIGraphicsImageRenderer(size: CGSize(width: 150, height: 228), format: format).image { _ in
      let shape = UIBezierPath(roundedRect: CGRect(x: 0, y: 0, width: 150, height: 228), cornerRadius: 20)
      shape.addClip()
      color(back ? 0x1a1310 : 0x15161a).setFill()
      shape.fill()
      if back {
        _ = text(data.title, 15, 15, 120, 13, 17, "SemiBold")
        _ = text(data.when, 15, 34, 120, 10, 14, "Regular", .white.withAlphaComponent(0.55), lines: 0)
        let pace = text(data.pace, 15, 62, 120, 30, 34, "Medium", .white, 0, true)
        _ = text("/km", 18 + pace.width, 76, 40, 12, 20, "Regular", .white.withAlphaComponent(0.6))
        let top = data.splits.max() ?? 1
        for (i, split) in data.splits.enumerated() {
          let height = 6 + 28 * split / top
          (split == top ? color(0xff6a1f) : UIColor.white.withAlphaComponent(0.22)).setFill()
          UIBezierPath(roundedRect: CGRect(x: 15 + Double(i) * 25, y: 146 - height,
                                          width: 20, height: height), cornerRadius: 3).fill()
        }
        _ = text(data.best, 15, 160, 120, 10, 14, "Regular", .white.withAlphaComponent(0.6), lines: 0)
        _ = text(data.delta, 15, 176, 120, 16, 20, "SemiBold", color(0xff7a33))
      } else {
        badge.draw(in: CGRect(x: 9, y: 13, width: 88, height: 88))
        let title = text(data.title, 0, 0, 122, 14, 18, "SemiBold", lines: 2, draw: false)
        _ = text(data.title, 15, 171 - title.height, 122, 14, 18, "SemiBold", lines: 2)
        _ = text(data.time, 15, 175, 122, 28, 34, "Medium", .white, -0.5, true)
      }
      (back ? color(0xff7832, 0.35) : UIColor.white.withAlphaComponent(0.1)).setStroke()
      let border = UIBezierPath(roundedRect: CGRect(x: 0.5, y: 0.5, width: 149, height: 227), cornerRadius: 19.5)
      border.lineWidth = 1
      border.stroke()
    }
  }
  nonisolated static func url(_ name: String, _ ext: String) -> URL? {
    Bundle.main.url(forResource: "TrophyFanCase-\(name)", withExtension: ext)
      ?? Bundle.main.url(forResource: "TrophyFanCase-\(name)", withExtension: ext, subdirectory: "Assets")
  }
}

struct TrophyFanCaseLabel: View {
  var text: String
  var weight = "Regular"
  var size: Double
  var height: Double? = nil
  var color: Color = .white
  var tracking = 0.0
  var lines = 1
  var tabular = false
  var body: some View {
    let fontHeight = (UIFont(name: "Archivo-\(weight)", size: size) ?? .systemFont(ofSize: size)).lineHeight
    let leading = height.map { max(0, $0 - fontHeight) } ?? 0
    Group {
      if tabular {
        Text(text).monospacedDigit()
      } else {
        Text(text)
      }
    }
    .font(.custom("Archivo-\(weight)", fixedSize: size))
    .foregroundStyle(color)
    .kerning(tracking)
    .lineSpacing(leading)
    .lineLimit(lines)
    .padding(.vertical, leading / 2)
    .fixedSize(horizontal: false, vertical: true)
  }
}
