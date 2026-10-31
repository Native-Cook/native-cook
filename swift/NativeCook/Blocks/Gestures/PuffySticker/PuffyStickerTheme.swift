import CoreText
import SwiftUI

enum PuffyStickerTheme {
  static func color(_ hex: UInt32) -> Color {
    Color(
      .sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
      blue: Double(hex & 255) / 255, opacity: 1)
  }
  static let page = color(0x0E0E12), surface = color(0x1B1B21), surfaceOn = color(0x2A2A33)
  static let ink = color(0xF4F4F6), mute = color(0x8C8C96), accent = color(0xFFD43B),
    accentInk = color(0x1A1600)
  static let labels = ["Heart", "Star", "Cloud", "Smiley"]
  static let fontNames: [String: String] = {
    var names: [String: String] = [:]
    for weight in ["Regular", "Medium", "SemiBold"] {
      guard
        let url = Bundle.main.url(forResource: "PuffySticker-Inter-\(weight)", withExtension: "ttf")
      else { continue }
      CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
      if let descriptors = CTFontManagerCreateFontDescriptorsFromURL(url as CFURL)
        as? [CTFontDescriptor], let d = descriptors.first,
        let name = CTFontDescriptorCopyAttribute(d, kCTFontNameAttribute) as? String
      {
        names[weight] = name
      }
    }
    return names
  }()
  static func font(_ size: Double, _ weight: String = "Medium") -> Font {
    .custom(
      fontNames[weight] ?? (weight == "Regular" ? "Inter24pt-Regular" : "Inter-\(weight)"),
      fixedSize: size)
  }
  struct Layout {
    let w, h, barTop, stageX, stageY, sticker, hintTop, switchTop, trayTop: Double
    let thumb = 64.0
    var trayLeft: Double { w / 2 - 152 }
    init(w: Double, h: Double, top: Double, bottom: Double) {
      self.w = w
      self.h = h
      sticker = min(w * 0.36, 150)
      trayTop = h - bottom - 24 - 64
      switchTop = trayTop - 24 - 40
      barTop = top + 8
      stageX = w / 2
      stageY = (barTop + 44 + switchTop) / 2 - 18
      hintTop = stageY + sticker * 1.12 + 20
    }
  }
}
