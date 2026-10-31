import SwiftUI
import UIKit
import CoreText

enum ChromeStepNumeralsTheme {
  static func color(_ hex: UInt32) -> Color {
    Color(.sRGB, red: Double((hex >> 16)&255)/255, green: Double((hex >> 8)&255)/255, blue: Double(hex&255)/255, opacity: 1)
  }
  static let page=color(0xEEEDEA), ink=color(0x1E1C1A), mute=color(0x8A847C)
  static let accent=color(0xC08A57), dot=color(0xD4CFC8)
  static let titles=["Link your bank", "Pick a goal", "Watch it grow"]
  static let lines=["Connect an account in a minute. We only ever read balances.",
    "A trip, a bike, a rainy-day fund — name it and set a date.",
    "Round-ups and payday transfers fill it without thinking."]
  static let register: Void = {
    for face in ["Regular", "Medium", "SemiBold"] {
      if let url=Bundle.main.url(forResource: "ChromeStepNumerals-Inter-\(face)", withExtension: "ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil)
      }
    }
  }()
  static func font(_ face: String, _ size: CGFloat) -> Font {
    _ = register
    return .custom(face == "Regular" ? "Inter24pt-Regular" : "Inter-\(face)",size:size)
  }
  struct Layout {
    var w: Double, h: Double, top: Double, bottom: Double
    var unit: Double { (w*0.3).rounded() }
    var cy: Double { top+((h-top-bottom)*0.33).rounded() }
    var floorY: Double { cy+unit*1.15 }
    var titleTop: Double { cy+unit*1.45 }
    var buttonTop: Double { h-max(bottom,16)-8-52 }
    var dotsTop: Double { buttonTop-40 }
  }
}
