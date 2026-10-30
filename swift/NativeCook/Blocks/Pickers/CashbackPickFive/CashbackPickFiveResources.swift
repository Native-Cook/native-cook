import CoreText
import SwiftUI
import UIKit

// Cache glyph runs once: the animation only transforms already rasterized Inter text.
final class CashbackPickFiveResources {
  static let shared = CashbackPickFiveResources()
  struct Label {
    var image: Image
    var width: Double
    var size: CGSize
    var ascender: Double
  }
  private var fonts: [String: UIFont] = [:]
  private var labels: [String: Label] = [:]
  init() {
    for weight in ["Regular", "Medium", "SemiBold", "Bold", "ExtraBold"] {
      if let url = Bundle.main.url(
        forResource: "CashbackPickFive-Inter-\(weight)", withExtension: "ttf")
      {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        if let descriptors = CTFontManagerCreateFontDescriptorsFromURL(url as CFURL)
          as? [CTFontDescriptor],
          let descriptor = descriptors.first
        {
          let font = CTFontCreateWithFontDescriptor(descriptor, 100, nil)
          fonts[weight] = UIFont(name: CTFontCopyPostScriptName(font) as String, size: 100)
        }
      }
    }
  }
  func font(_ size: Double, _ weight: String) -> UIFont {
    (fonts[weight] ?? UIFont.systemFont(ofSize: 100)).withSize(size)
  }
  func label(_ string: String, _ size: Double, _ weight: String) -> Label {
    let key = "\(weight)|\(size)|\(string)"
    if let label = labels[key] { return label }
    let font = font(size, weight)
    let attributed = NSAttributedString(
      string: string, attributes: [.font: font, .foregroundColor: UIColor.white])
    let line = CTLineCreateWithAttributedString(attributed)
    // Skia measureText reports ink width, while the run's advances remain untouched.
    let width = CTLineGetBoundsWithOptions(line, .useGlyphPathBounds).width
    let bounds = CGSize(
      width: max(width, attributed.size().width) + 24, height: font.lineHeight + 24)
    let format = UIGraphicsImageRendererFormat()
    format.scale = 2
    let uiImage = UIGraphicsImageRenderer(size: bounds, format: format).image { _ in
      attributed.draw(at: CGPoint(x: 12, y: 12))
    }
    let label = Label(
      image: Image(uiImage: uiImage).renderingMode(.template), width: width, size: bounds,
      ascender: font.ascender)
    labels[key] = label
    return label
  }
}
