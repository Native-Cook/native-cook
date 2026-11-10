import SwiftUI

enum EyewearLensTintTheme {
  struct Tint {
    let name: String
    let swatch: Int
  }
  static let tints = [Tint(name: "Midnight", swatch: 0x1B2433), Tint(name: "Rose", swatch: 0xD97C8A), Tint(name: "Amber", swatch: 0xD69A3A), Tint(name: "Sea", swatch: 0x3E8F8A)]
  static let body = "Round titanium frames with lenses tinted by hand. Drag a tint onto a lens to try it on."
  static func color(_ hex: Int, _ alpha: Double = 1) -> Color {
    Color(.sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255, blue: Double(hex & 255) / 255, opacity: alpha)
  }
  struct Layout: Equatable {
    let w, h, sx, k, top, bottom, safeBottom: Double
    init(_ size: CGSize, _ safeTop: Double, _ safeBottom: Double) {
      w = size.width; h = size.height; sx = size.width / 402
      top = max(safeTop, 44); bottom = max(safeBottom, 20); self.safeBottom = safeBottom
      k = (size.height - top - bottom) / (874 - 62 - 34)
    }
    func y(_ value: Double) -> Double { value < 62 ? value * top / 62 : top + (value - 62) * k }
    var lensY: Double { y(303) }
    var lensR: Double { 52 * sx }
    func lens(_ i: Int) -> CGPoint { CGPoint(x: (i == 0 ? 130 : 274.3) * sx, y: lensY) }
    var photo: CGRect { CGRect(x: -73.7 * sx, y: lensY - 429 * sx, width: 536 * sx, height: 800 * sx) }
    func drop(_ i: Int) -> CGPoint { CGPoint(x: 370 * sx, y: y(400 + Double(i) * 44)) }
    func pour(_ i: Int) -> CGPoint { CGPoint(x: lens(i).x + (i == 0 ? -0.45 : 0.45) * lensR, y: lensY - 0.55 * lensR) }
    var card: CGRect { CGRect(x: 20 * sx, y: y(600), width: 362 * sx, height: h - y(600) - safeBottom + 6) }
    var touch: CGRect { CGRect(x: 0, y: y(140), width: w, height: y(600) - y(140) - 60) }
  }
}
