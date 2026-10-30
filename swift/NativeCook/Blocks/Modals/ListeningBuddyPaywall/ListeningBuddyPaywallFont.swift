import CoreText
import Foundation

final class ListeningBuddyPaywallFont {
  let font: CTFont
  private var outlines: [String: CGPath] = [:]
  private var widths: [String: Double] = [:]
  static let registered: Void = {
    for name in [
      "Manrope_400Regular", "Manrope_500Medium", "Manrope_600SemiBold", "Manrope_700Bold",
      "Nunito-ExtraBold",
    ] {
      if let url = Bundle.main.url(
        forResource: "ListeningBuddyPaywall-" + name, withExtension: "ttf")
      {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
      }
    }
  }()
  init(_ file: String, _ size: Double) {
    _ = Self.registered
    // Read the bundled face itself; this avoids guessing a TTF's PostScript name.
    if let url = Bundle.main.url(
      forResource: "ListeningBuddyPaywall-" + file, withExtension: "ttf"),
      let provider = CGDataProvider(url: url as CFURL), let face = CGFont(provider)
    {
      font = CTFontCreateWithGraphicsFont(face, size, nil, nil)
    } else {
      font = CTFontCreateWithName("Helvetica" as CFString, size, nil)
    }
  }
  func getSize() -> Double { CTFontGetSize(font) }
  func measureText(_ text: String) -> (width: Double, height: Double) {
    if let width = widths[text] { return (width, 0) }
    let line = CTLineCreateWithAttributedString(
      NSAttributedString(
        string: text,
        attributes: [NSAttributedString.Key(kCTFontAttributeName as String): font]))
    let width = CTLineGetTypographicBounds(line, nil, nil, nil)
    widths[text] = width
    return (width, 0)
  }
  func outline(_ text: String) -> CGPath {
    if let path = outlines[text] { return path }
    let line = CTLineCreateWithAttributedString(
      NSAttributedString(
        string: text,
        attributes: [NSAttributedString.Key(kCTFontAttributeName as String): font]))
    let path = CGMutablePath()
    for run in CTLineGetGlyphRuns(line) as! [CTRun] {
      let count = CTRunGetGlyphCount(run)
      var glyphs = [CGGlyph](repeating: 0, count: count)
      var positions = [CGPoint](repeating: .zero, count: count)
      CTRunGetGlyphs(run, CFRange(location: 0, length: 0), &glyphs)
      CTRunGetPositions(run, CFRange(location: 0, length: 0), &positions)
      for index in 0..<count {
        if let glyph = CTFontCreatePathForGlyph(font, glyphs[index], nil) {
          path.addPath(
            glyph,
            transform: CGAffineTransform(translationX: positions[index].x, y: positions[index].y))
        }
      }
    }
    outlines[text] = path
    return path
  }
}
