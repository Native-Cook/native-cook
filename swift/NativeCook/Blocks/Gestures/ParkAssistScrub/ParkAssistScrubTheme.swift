import SwiftUI
import CoreText
import UIKit

enum ParkAssistScrubTheme {
  static let page = color(0x0E0F12), ink = color(0xF2F3F5), mute = color(0x8B8E98)
  static let card = color(0x1A1C21), line = color(0x2A2D33), accent = color(0xFF8A3D)
  static let green = color(0x4BDC86), amber = color(0xFFC23D), red = color(0xFF4D45)
  static func color(_ rgb: Int) -> Color {
    Color(.sRGB,red:Double((rgb>>16)&255)/255,green:Double((rgb>>8)&255)/255,blue:Double(rgb&255)/255,opacity:1)
  }
  static let fonts: [String:String] = {
    var result: [String:String] = [:]
    for style in ["Regular","Medium","SemiBold"] {
      if let url = Bundle.main.url(forResource:"ParkAssistScrub-Inter-\(style)",withExtension:"ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil)
        if let descriptors = CTFontManagerCreateFontDescriptorsFromURL(url as CFURL) as? [CTFontDescriptor],
           let descriptor = descriptors.first,
           let name = CTFontDescriptorCopyAttribute(descriptor,kCTFontNameAttribute) as? String { result[style] = name }
      }
    }
    return result
  }()
  static func font(_ size: CGFloat,_ style: String = "SemiBold") -> Font {
    .custom(fonts[style] ?? (style == "Regular" ? "Inter24pt-Regular" : "Inter-\(style)"),fixedSize:size)
  }
  static func gapColor(_ gap: Double) -> Color {
    let warn = ParkAssistScrubMotion.clamp(1-gap/1.2)
    let stops: [(Double, [Double])] = [(0,[242,243,245]),(0.35,[75,220,134]),(0.7,[255,194,61]),(1,[255,77,69])]
    for i in 1..<stops.count where warn <= stops[i].0 {
      let a = stops[i-1], b = stops[i], t = (warn-a.0)/(b.0-a.0)
      // Reanimated interpolateColor defaults to gamma-corrected RGB (gamma 2.2).
      let c = zip(a.1,b.1).map { pow(pow($0/255,2.2)*(1-t)+pow($1/255,2.2)*t,1/2.2) }
      return Color(.sRGB,red:c[0],green:c[1],blue:c[2],opacity:1)
    }
    return red
  }
}
