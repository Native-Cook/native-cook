import CoreImage
import CoreText
import SwiftUI
import UIKit

final class CoinCardOnboardingResources {
  static let shared = CoinCardOnboardingResources()
  let names: [String: String]
  private let fonts: [String: Font]
  let cardBodies: [UIImage]
  let cardDetails: [UIImage]
  let symbols: [UIImage]
  let shadow: UIImage
  private static let paintSpace = CGColorSpace(name: CGColorSpace.displayP3)!
  private var cardFrames: [UIImage?] = [nil, nil]
  private var cardSheens = [Double.nan, Double.nan]
  private var copyFrames: [String: UIImage] = [:]
  private var backdropFrame: (String, UIImage)?
  static let cardPath = Path(roundedRect: CGRect(x: 0, y: 0, width: 252, height: 160), cornerRadius: 18)
  static let unitCircle = Path(ellipseIn: CGRect(x: -1, y: -1, width: 2, height: 2))

  static func color(_ hex: Int, _ a: Double = 1) -> Color {
    Color(.sRGB, red: Double((hex >> 16) & 255) / 255,
          green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: a)
  }
  static func uiColor(_ hex: Int, _ a: Double = 1) -> UIColor {
    UIColor(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
            blue: Double(hex & 255) / 255, alpha: a)
  }
  func font(_ name: String, _ size: Double) -> Font { fonts["\(name)-\(size)"]! }
  private static func bitmap(_ size: CGSize, scale: Double = 3, _ draw: (CGContext) -> Void) -> UIImage {
    // Preserve precision through P3 composition, especially the dark magenta card's green channel.
    let width = Int((size.width * scale).rounded()), height = Int((size.height * scale).rounded())
    let c = CGContext(data: nil, width: width, height: height, bitsPerComponent: 16,
      bytesPerRow: 0, space: paintSpace,
      bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder16Little.rawValue)
      ?? CGContext(data: nil, width: width, height: height, bitsPerComponent: 8,
        bytesPerRow: 0, space: paintSpace, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    c.translateBy(x: 0, y: Double(height))
    c.scaleBy(x: Double(width) / size.width, y: -Double(height) / size.height)
    draw(c)
    return UIImage(cgImage: c.makeImage()!, scale: scale, orientation: .up)
  }
  func backdrop(_ p: Double, _ size: CGSize, _ top: Double) -> UIImage {
    let tops = [[247.0, 211, 234], [207.0, 240, 226], [226.0, 214, 252]]
    let blooms = [[244.0, 170, 214], [140.0, 222, 190], [190.0, 165, 250]]
    let q = min(2, max(0, p)), i = min(1, Int(floor(q)))
    let k = CoinCardOnboardingMotion.smooth(q - Double(i))
    func rgb(_ palette: [[Double]]) -> [Double] {
      (0..<3).map { CoinCardOnboardingMotion.mix(palette[i][$0], palette[i + 1][$0], k).rounded() / 255 }
    }
    let tc = rgb(tops), bc = rgb(blooms)
    let key = "\(size)-\(top)-\(tc)-\(bc)"
    if let (previous, image) = backdropFrame, previous == key { return image }
    func color(_ rgb: [Double], _ alpha: Double) -> CGColor {
      UIColor(red: rgb[0], green: rgb[1], blue: rgb[2], alpha: alpha).cgColor
        .converted(to: Self.paintSpace, intent: .defaultIntent, options: nil)!
    }
    // A smooth wash needs no 3x raster; interpolate this cache at screen resolution.
    let image = Self.bitmap(size, scale: 1) { c in
      let colors = [color(tc, 1), Self.uiColor(0xfbf7fb).cgColor.converted(to: Self.paintSpace, intent: .defaultIntent, options: nil)!, color([1, 1, 1], 1)]
      let linear = CGGradient(colorsSpace: Self.paintSpace, colors: colors as CFArray, locations: [0, 0.62, 1])!
      c.drawLinearGradient(linear, start: .zero, end: CGPoint(x: 0, y: size.height), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
      let radial = CGGradient(colorsSpace: Self.paintSpace, colors: [color(bc, 0.55), color(bc, 0)] as CFArray, locations: [0, 1])!
      let center = CGPoint(x: size.width / 2, y: top + 266)
      c.drawRadialGradient(radial, startCenter: center, startRadius: 0, endCenter: center,
        endRadius: size.width * 0.7, options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
    }
    backdropFrame = (key, image)
    return image
  }
  func card(_ front: Bool, _ sheen: Double) -> UIImage {
    let i = front ? 1 : 0
    if cardSheens[i] == sheen, let frame = cardFrames[i] { return frame }
    let frame = Self.bitmap(CGSize(width: 252, height: 160)) { c in
      func image(_ image: UIImage) {
        c.saveGState()
        c.translateBy(x: 0, y: 160)
        c.scaleBy(x: 1, y: -1)
        c.draw(image.cgImage!, in: CGRect(x: 0, y: 0, width: 252, height: 160))
        c.restoreGState()
      }
      image(cardBodies[i])
      c.saveGState()
      c.addPath(CGPath(roundedRect: CGRect(x: 0, y: 0, width: 252, height: 160), cornerWidth: 18, cornerHeight: 18, transform: nil))
      c.clip()
      let colors = [0.0, 0.32, 0.0].map { Self.uiColor(0xffffff, $0).cgColor.converted(to: Self.paintSpace, intent: .defaultIntent, options: nil)! }
      let gradient = CGGradient(colorsSpace: Self.paintSpace, colors: colors as CFArray, locations: [0, 0.5, 1])!
      c.drawLinearGradient(gradient, start: CGPoint(x: 252 * (sheen - 0.6), y: 0),
        end: CGPoint(x: 252 * (sheen + 0.4), y: 160), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
      c.restoreGState()
      image(cardDetails[i])
    }
    cardSheens[i] = sheen
    cardFrames[i] = frame
    return frame
  }
  func copy(_ text: String, title: Bool, width: Double) -> UIImage {
    let key = "\(width)-\(text)"
    if let image = copyFrames[key] { return image }
    let lineHeight = title ? 37.0 : 21.0
    let height = lineHeight * 2
    let fontName = title ? "Outfit-Medium" : "Inter-Regular"
    let font = UIFont(name: names[fontName] ?? fontName, size: title ? 31 : 15)!
    let paragraph = NSMutableParagraphStyle()
    paragraph.alignment = .center
    paragraph.minimumLineHeight = lineHeight
    paragraph.maximumLineHeight = lineHeight
    var attributes: [NSAttributedString.Key: Any] = [
      .font: font, .foregroundColor: Self.uiColor(title ? 0x141416 : 0x55555c), .paragraphStyle: paragraph
    ]
    if title { attributes[.kern] = -0.4 }
    if lineHeight >= font.lineHeight { attributes[.baselineOffset] = (lineHeight - font.lineHeight) / 2 }
    let storage = NSTextStorage(string: text, attributes: attributes)
    let manager = NSLayoutManager()
    manager.usesFontLeading = false
    let container = NSTextContainer(size: CGSize(width: width, height: height))
    container.lineFragmentPadding = 0
    container.lineBreakMode = .byClipping
    manager.addTextContainer(container)
    storage.addLayoutManager(manager)
    let range = manager.glyphRange(for: container)
    let image = withExtendedLifetime(storage) {
      Self.bitmap(CGSize(width: width, height: height)) { c in
        UIGraphicsPushContext(c)
        manager.drawGlyphs(forGlyphRange: range, at: .zero)
        UIGraphicsPopContext()
      }
    }
    copyFrames[key] = image
    return image
  }
  private static func glyphs(_ text: String, _ font: CTFont) -> ([CGGlyph], [CGPoint], Double) {
    let chars = Array(text.utf16)
    var glyphs = [CGGlyph](repeating: 0, count: chars.count)
    CTFontGetGlyphsForCharacters(font, chars, &glyphs, chars.count)
    var widths = [CGSize](repeating: .zero, count: chars.count)
    CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &widths, glyphs.count)
    var x = 0.0
    let points = widths.map { w -> CGPoint in
      defer { x += w.width }
      return CGPoint(x: x, y: 0)
    }
    return (glyphs, points, x)
  }
  private static func text(_ c: CGContext, _ s: String, _ face: CTFont, _ x: Double, _ y: Double,
                           _ color: Int, _ alpha: Double = 1) -> Double {
    let g = glyphs(s, face)
    c.saveGState()
    c.translateBy(x: x, y: y)
    c.scaleBy(x: 1, y: -1)
    c.setFillColor(uiColor(color, alpha).cgColor)
    CTFontDrawGlyphs(face, g.0, g.1, g.0.count, c)
    c.restoreGState()
    return g.2
  }
  init() {
    var names: [String: String] = [:]
    for file in ["Inter-Regular", "Inter-Medium", "Inter-Bold", "Outfit-Regular", "Outfit-Medium"] {
      if let url = Bundle.main.url(forResource: "CoinCardOnboarding-\(file)", withExtension: "ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        if let descriptors = CTFontManagerCreateFontDescriptorsFromURL(url as CFURL) as? [CTFontDescriptor],
           let d = descriptors.first,
           let name = CTFontDescriptorCopyAttribute(d, kCTFontNameAttribute) as? String { names[file] = name }
      }
    }
    self.names = names
    var fonts: [String: Font] = [:]
    for (name, sizes) in [("Outfit-Medium", [17.0, 31]), ("Inter-Regular", [15.0]), ("Inter-Medium", [14.0, 16])] {
      for size in sizes { fonts["\(name)-\(size)"] = .custom(names[name] ?? name, fixedSize: size) }
    }
    self.fonts = fonts
    func face(_ name: String, _ size: Double) -> CTFont {
      (UIFont(name: names[name] ?? name, size: size) ?? UIFont.systemFont(ofSize: size)) as CTFont
    }
    let small = face("Inter-Medium", 12), big = face("Outfit-Medium", 26), sym = face("Inter-Bold", 34)
    cardBodies = [false, true].map { front in
      Self.bitmap(CGSize(width: 252, height: 160)) { c in
        c.addPath(CGPath(roundedRect: CGRect(x: 0, y: 0, width: 252, height: 160), cornerWidth: 18, cornerHeight: 18, transform: nil))
        c.clip()
        let colors = front ? [0xe0479a, 0x8e1450, 0x5c0a33, 0xc72f7f] : [0xc7a6ff, 0x9b72ee, 0x7c55d6, 0xb896ff]
        let gradient = CGGradient(colorsSpace: Self.paintSpace, colors: colors.map { Self.uiColor($0).cgColor.converted(to: Self.paintSpace, intent: .defaultIntent, options: nil)! } as CFArray, locations: [0, 0.45, 0.72, 1])!
        c.drawLinearGradient(gradient, start: .zero, end: CGPoint(x: 252, y: 160), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
      }
    }
    cardDetails = [false, true].map { front in
      Self.bitmap(CGSize(width: 252, height: 160)) { c in
        c.setStrokeColor(Self.uiColor(0xffffff, 0.35).cgColor)
        c.setLineWidth(1)
        c.addPath(CGPath(roundedRect: CGRect(x: 0.5, y: 0.5, width: 251, height: 159), cornerWidth: 17.5, cornerHeight: 17.5, transform: nil))
        c.strokePath()
        c.setStrokeColor(Self.uiColor(0xffffff).cgColor)
        c.setLineWidth(3)
        c.setLineCap(.round)
        for i in 0..<4 {
          let a = Double(i) * .pi / 4
          c.move(to: CGPoint(x: 26 - cos(a) * 8, y: 26 - sin(a) * 8))
          c.addLine(to: CGPoint(x: 26 + cos(a) * 8, y: 26 + sin(a) * 8))
          c.strokePath()
        }
        _ = Self.text(c, "Balance", small, 18, 108, 0xffffff, 0.75)
        let w = Self.text(c, front ? "$ 24 597" : "$ 2 597", big, 18, 138, 0xffffff)
        _ = Self.text(c, ".36", big, 18 + w, 138, 0xffffff, 0.55)
      }
    }
    symbols = ["$", "€"].map { s in
      Self.bitmap(CGSize(width: 64, height: 64)) { c in
        let x = 32 - Self.glyphs(s, sym).2 / 2
        _ = Self.text(c, s, sym, x + 1, 33.2, 0x7d828d)
        _ = Self.text(c, s, sym, x - 0.8, 31.2, 0xffffff)
        _ = Self.text(c, s, sym, x, 32, 0xc3c7cf)
      }
    }
    let raw = Self.bitmap(CGSize(width: 416, height: 202)) { c in
      c.setFillColor(Self.uiColor(0xb0306f).cgColor)
      c.fillEllipse(in: CGRect(x: 78, y: 78, width: 260, height: 46))
    }
    let input = CIImage(cgImage: raw.cgImage!)
    // Skia MakeBlur uses sigma=26 pt; Core Image's inputRadius is also sigma in pixels.
    let blurred = input.applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: 26 * 3])
    let ci = CIContext(options: [.workingColorSpace: CGColorSpace(name: CGColorSpace.sRGB)!])
    shadow = ci.createCGImage(blurred, from: input.extent).map { UIImage(cgImage: $0, scale: 3, orientation: .up) } ?? raw
  }
}
