import SwiftUI
import CoreText

enum DroneUnfoldConnectTheme {
  static func color(_ hex: UInt32) -> Color {
    Color(.sRGB, red:Double((hex>>16)&255)/255, green:Double((hex>>8)&255)/255, blue:Double(hex&255)/255, opacity:1)
  }
  // Reanimated interpolateColor uses gamma 2.2 for the RGB interpolation.
  static func statusColor(_ t: Double) -> Color {
    func channel(_ a: Double, _ b: Double) -> Double { pow(pow(a/255,2.2)*(1-t)+pow(b/255,2.2)*t,1/2.2) }
    return Color(.sRGB,red:channel(255,61),green:channel(91,220),blue:channel(79,132),opacity:1)
  }
  static let page=color(0x0D1014), ink=color(0xF1F3F5), mute=color(0x8A9099)
  static let card=color(0x171B21), line=color(0x262C34), accent=color(0xE9FF70), accentInk=color(0x1A1F00)
  static let fonts: [String:String] = {
    var names: [String:String] = [:]
    for weight in ["Regular","Medium","SemiBold"] {
      guard let url=Bundle.main.url(forResource:"DroneUnfoldConnect-Inter-\(weight)",withExtension:"ttf") else { continue }
      CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil)
      if let ds=CTFontManagerCreateFontDescriptorsFromURL(url as CFURL) as? [CTFontDescriptor], let d=ds.first {
        names[weight]=CTFontDescriptorCopyAttribute(d,kCTFontNameAttribute) as? String
      }
    }
    return names
  }()
  static func font(_ size: Double, _ weight: String = "SemiBold") -> Font {
    if let name=fonts[weight] { return .custom(name, fixedSize:size) }
    return .system(size:size,weight:weight == "Regular" ? .regular : weight == "Medium" ? .medium : .semibold)
  }
}
