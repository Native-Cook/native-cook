import CoreText
import SwiftUI
import UIKit

/// Predecoded avatars and unkerned glyph outlines match Skia's manual glyph advances.
enum GhostFloatWelcomeResources {
  struct Label {
    let path: Path
    let width: Double
    let size: Double
  }
  static let registered: Void = {
    for name in [
      "Manrope_400Regular", "Manrope_500Medium", "Manrope_600SemiBold", "Manrope_700Bold",
    ] {
      if let url = Bundle.main.url(forResource: "GhostFloatWelcome-" + name, withExtension: "ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
      }
    }
  }()
  static func label(_ text: String, _ size: Double, _ weight: String) -> Label {
    _ = registered
    let font = UIFont(name: "Manrope-" + weight, size: size) ?? UIFont.systemFont(ofSize: size)
    let ct = font as CTFont
    let chars = Array(text.utf16)
    var glyphs = [CGGlyph](repeating: 0, count: chars.count)
    CTFontGetGlyphsForCharacters(ct, chars, &glyphs, chars.count)
    var widths = [CGSize](repeating: .zero, count: chars.count)
    CTFontGetAdvancesForGlyphs(ct, .horizontal, glyphs, &widths, glyphs.count)
    let outline = CGMutablePath()
    var x = 0.0
    for i in glyphs.indices {
      if let glyph = CTFontCreatePathForGlyph(ct, glyphs[i], nil) {
        outline.addPath(glyph, transform: CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: x, ty: 0))
      }
      x += widths[i].width
    }
    return Label(path: Path(outline), width: x, size: size)
  }
  static let title = label("Welcome to Wisp", 29, "Bold")
  static let sub1 = label("To get started, create a new wallet or", 17.6, "Regular")
  static let sub2 = label("import an existing one.", 17.6, "Regular")
  static let agree = label("I agree to the ", 17.4, "Regular")
  static let terms = label("Terms of Service", 17.4, "SemiBold")
  static let create = label("Create a new wallet", 17.4, "Bold")
  static let have = label("I already have a wallet", 17.2, "SemiBold")
  static let help = label("?", 12.5, "SemiBold")
  static func image(_ name: String) -> Image {
    guard
      let url = Bundle.main.url(
        forResource: "GhostFloatWelcome-avatar-" + name, withExtension: "webp"),
      let image = UIImage(contentsOfFile: url.path)
    else { return Image(uiImage: UIImage()) }
    return Image(uiImage: image.preparingForDisplay() ?? image)
  }
  static let dino = image("dino"), penguin = image("penguin")
}
