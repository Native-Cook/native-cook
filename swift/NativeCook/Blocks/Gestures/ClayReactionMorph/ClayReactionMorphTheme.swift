import SwiftUI
import UIKit
import CoreText

enum ClayReactionMorphTheme {
  static let reactions = ["Like", "Love", "Haha", "Wow"]
  static let tint: [UInt32] = [0x3880FF, 0xFF2B4D, 0xFFCC33, 0xFF9E24]
  static let messages = [
    "Morning! Still on for the coast?", "Obviously. Bringing the camera",
    "Ferry or the early train on Saturday?", "Ferry. Sunrise over the bay",
    "Then I booked us two window seats for 6:40", "Best news all week"
  ]
  static let page = color(0x0B0B10), head = color(0xF2F2F7), mute = color(0x8D8D99)
  static let incoming = color(0x26262E), outgoing = color(0x5B5BD6)
  static let field = color(0x18181F), fieldLine = color(0x2A2A33)
  static func color(_ hex: UInt32) -> Color {
    Color(.sRGB, red: Double((hex >> 16)&255)/255, green: Double((hex >> 8)&255)/255,
          blue: Double(hex&255)/255, opacity: 1)
  }
  static let register: Void = {
    for weight in ["Regular", "Medium", "SemiBold"] {
      if let url = Bundle.main.url(forResource: "ClayReactionMorph-Inter-\(weight)", withExtension: "ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
      }
    }
  }()
  static func font(_ size: Double, _ weight: String = "Regular") -> Font {
    _ = register
    return .custom(weight == "Regular" ? "Inter24pt-Regular" : "Inter-\(weight)", fixedSize: size)
  }
  static func labelColor(_ near: Double) -> Color {
    let f = min(1,max(0,near))
    func c(_ x: Double) -> Double { pow(pow(x/255,2.2)*(1-f)+f,1/2.2) }
    return Color(.sRGB,red:c(141),green:c(141),blue:c(153),opacity:1)
  }
  static func blendedTint(_ value: Double) -> Color {
    let k = min(3,max(0,value)), a = Int(floor(k)), b = min(3,a+1), f = k-Double(a)
    // Reanimated interpolateColor uses gamma 2.2 for RGB interpolation.
    func channel(_ shift: UInt32) -> Double {
      let x = Double((tint[a] >> shift)&255)/255, y = Double((tint[b] >> shift)&255)/255
      return pow(pow(x,2.2)*(1-f)+pow(y,2.2)*f,1/2.2)
    }
    return Color(.sRGB,red: channel(16),green: channel(8),blue: channel(0),opacity: 1)
  }
}
