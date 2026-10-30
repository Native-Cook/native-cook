import CoreText
import SwiftUI
import UIKit

// Fonts are bundled and registered once. Cache glyph masks so the moving paper scene never
// rasterizes the same title, number or status label again on each animation frame.
final class GreatWaveMorningResources {
  static let shared = GreatWaveMorningResources()
  struct Face {
    let name: String
    let size: Double
    func getSize() -> Double { size }
    func measureText(_ text: String) -> (width: Double, height: Double) {
      let label = GreatWaveMorningResources.shared.label(text, self)
      return (label.width, label.height)
    }
  }
  struct Fonts {
    let hello = Face(name: "Manrope-SemiBold", size: 40)
    let avatar = Face(name: "Manrope-SemiBold", size: 30)
    let pill = Face(name: "Manrope-Medium", size: 21)
    let cardLabel = Face(name: "Manrope-Medium", size: 23)
    let big = Face(name: "Manrope-SemiBold", size: 70)
    let cardSub = Face(name: "Manrope-Medium", size: 20)
    let focus = Face(name: "Manrope-SemiBold", size: 27)
    let row = Face(name: "Manrope-Medium", size: 20.5)
    let time = Face(name: "Manrope-Medium", size: 22)
    let status = Face(name: "Manrope-Medium", size: 19)
    let tab = Face(name: "Manrope-Medium", size: 19)
  }
  struct Label {
    let image: Image
    let width: Double
    let height: Double
    let baseline: Double
    let imageSize: CGSize
  }
  let f = Fonts()
  private var labels: [String: Label] = [:]
  private init() {
    for file in [
      "Manrope_400Regular", "Manrope_500Medium", "Manrope_600SemiBold", "Manrope_700Bold",
    ] {
      if let url = Bundle.main.url(forResource: "GreatWaveMorning-" + file, withExtension: "ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
      }
    }
  }
  func label(_ string: String, _ face: Face) -> Label {
    let key = "\(face.name):\(face.size):\(string)"
    if let label = labels[key] { return label }
    let font = UIFont(name: face.name, size: face.size) ?? UIFont.systemFont(ofSize: face.size)
    let line = CTLineCreateWithAttributedString(
      NSAttributedString(
        string: string,
        attributes: [
          .font: font, .foregroundColor: UIColor.white,
        ]))
    var ascent: CGFloat = 0
    var descent: CGFloat = 0
    let width = CTLineGetTypographicBounds(line, &ascent, &descent, nil)
    let size = CGSize(width: ceil(width) + 4, height: ceil(ascent + descent) + 4)
    let baseline = Double(ascent) + 2
    let format = UIGraphicsImageRendererFormat()
    format.scale = 2
    format.opaque = false
    let image = UIGraphicsImageRenderer(size: size, format: format).image { renderer in
      let context = renderer.cgContext
      context.translateBy(x: 0, y: size.height)
      context.scaleBy(x: 1, y: -1)
      context.textPosition = CGPoint(x: 2, y: size.height - baseline)
      CTLineDraw(line, context)
    }
    let result = Label(
      image: Image(uiImage: image).renderingMode(.template), width: width,
      height: Double(ascent + descent), baseline: baseline, imageSize: size)
    labels[key] = result
    return result
  }
}
