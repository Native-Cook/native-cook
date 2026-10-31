import CoreText
import SwiftUI

enum StarCouponTearTheme {
  static func color(_ hex: UInt32) -> Color {
    Color(
      .sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
      blue: Double(hex & 255) / 255, opacity: 1)
  }
  static let page = color(0x15123A), mute = color(0xA49FD0), pill = color(0x262150)
  static let line = color(0x3A3470), gold = color(0xFFD23F), goldInk = color(0x2B1E00)
  static let registered: Void = {
    for weight in ["Regular", "Medium", "SemiBold"] {
      if let url = Bundle.main.url(
        forResource: "StarCouponTear-Inter-\(weight)", withExtension: "ttf")
      {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
      }
    }
  }()
  static func font(_ size: Double, _ weight: String = "Medium") -> Font {
    _ = registered
    return .custom(weight == "Regular" ? "Inter24pt-Regular" : "Inter-\(weight)", fixedSize: size)
  }
}
