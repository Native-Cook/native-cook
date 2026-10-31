import SwiftUI
import CoreText

enum ClayRelightSettingsTheme {
  static let sceneH = 232.0, bandH = 76.0, rowH = 60.0, tile = 40.0, iconCol = 64.0
  static let rows = [
    ("Notifications","Allowed","Silenced"), ("Focus","Off","Sleep"),
    ("Display & Brightness","Light","Dark"), ("Battery","86%","Charging"),
    ("Privacy & Security","","")
  ]
  static let palette: [String: [UInt32]] = [
    "page": [0xEEF0F4,0xF4E6DC,0x0B0E19], "card": [0xFFFFFF,0xFFF8F2,0x161B2B],
    "ink": [0x14161C,0x2B1E1A,0xEEF0F7], "mute": [0x646A79,0x7A6058,0x8F95AA],
    "line": [0xE7E9EE,0xF1E2D8,0x232A3E], "trackOff": [0xD9DCE3,0xE9D6CB,0x2B3247],
    "pill": [0xFFFFFF,0xFFF4EB,0x0E1222], "pillInk": [0x1E2230,0x3A2620,0xE6E9F5]
  ]
  // interpolateColor defaults to gamma 2.2 in the RN reference.
  static func tone(_ key: String, _ m: Double, _ theme: Double) -> Color {
    let p = palette[key]!
    let k = min(1,max(0,m*2)), th = min(1,max(0,theme))
    func channel(_ shift: UInt32) -> Double {
      let a = Double((p[0] >> shift)&255)/255
      let b = Double((p[1] >> shift)&255)/255
      let c = Double((p[2] >> shift)&255)/255
      let lit = pow(a,2.2)*(1-k)+pow(b,2.2)*k
      return pow(lit*(1-th)+pow(c,2.2)*th,1/2.2)
    }
    return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0),
      opacity: key == "pill" ? 0.72*(1-th)+0.62*th : 1)
  }
  static let registerFonts: Void = {
    for weight in ["Regular","Medium","SemiBold"] {
      if let url = Bundle.main.url(forResource: "ClayRelightSettings-Inter-"+weight, withExtension: "ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil)
      }
    }
  }()
  static func font(_ size: Double, _ weight: String = "Regular") -> Font {
    _ = registerFonts
    return .custom(weight == "Regular" ? "Inter24pt-Regular" : "Inter-"+weight,size:size)
  }
}
