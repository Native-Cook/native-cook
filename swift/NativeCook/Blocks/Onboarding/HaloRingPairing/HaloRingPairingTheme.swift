import SwiftUI
import CoreText

enum HaloRingPairingTheme {
  static func color(_ hex: UInt32) -> Color {
    Color(.sRGB, red: Double((hex>>16)&255)/255, green: Double((hex>>8)&255)/255, blue: Double(hex&255)/255, opacity: 1)
  }
  static let page = color(0x06080A), ink = color(0xF2F4F3), mute = color(0x8A918E)
  static let card = color(0x121618), line = color(0x20262A), green = color(0x3BE38A), greenInk = color(0x04140B)
  static let ringSize = 148.0, chipSize = 13.0
  static let stats = [("Readiness","86",""),("Sleep","7h 42m",""),("Resting heart","58","bpm"),("Steps","4,210","")]
  static let fontNames: [String: String] = {
    var names = [String: String]()
    for weight in ["Regular","Medium","SemiBold"] {
      guard let url = Bundle.main.url(forResource: "HaloRingPairing-Inter-"+weight, withExtension: "ttf") else { continue }
      CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
      if let descriptors = CTFontManagerCreateFontDescriptorsFromURL(url as CFURL) as? [CTFontDescriptor], let descriptor = descriptors.first {
        names[weight] = CTFontDescriptorCopyAttribute(descriptor,kCTFontNameAttribute) as? String
      }
    }
    return names
  }()
  static func font(_ size: Double,_ weight: String = "Regular") -> Font {
    .custom(fontNames[weight] ?? (weight == "Regular" ? "Inter24pt-Regular" : "Inter-"+weight), fixedSize: size)
  }
}

struct HaloRingPairingLayout {
  let w: Double, h: Double, top: Double, bottom: Double
  var stageY: Double { top+((h-top-bottom)*0.33).rounded() }
  var titleTop: Double { stageY+168 }
  var sizesTop: Double { titleTop+84 }
  var buttonTop: Double { h-max(bottom,16)-8-56 }
  var chipX: Double { 42 }
  var chipY: Double { top+32 }
}
