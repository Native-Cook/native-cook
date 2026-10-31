import SwiftUI
import CoreText

enum CoinDropRewardTheme {
  static func color(_ hex: UInt32) -> Color {
    Color(.sRGB, red: Double((hex>>16)&255)/255, green: Double((hex>>8)&255)/255, blue: Double(hex&255)/255, opacity: 1)
  }
  static let page = color(0xFBFAFF), ink = color(0x191A23), mute = color(0x8B8D9A)
  static let line = color(0xEEEDF5), accent = color(0x6A5CFF), blue = color(0x2F6BFF)
  static let blueSoft = color(0xEAF0FF), tile = Color.white, stamp = color(0xF1F0F7)
  static let missions = ["Cedar desk lamp", "Linen throw blanket"]
  static let days = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
  static let stamped = [0,1,2,4]
  static let rowHeight = 72.0, coinDiameter = 44.0, coinSize = 22.0, heroSize = 66.0, tileSize = 236.0
  static let walletWidth = 124.0, walletHeight = 40.0
  static let fonts: [String: String] = {
    var names: [String:String] = [:]
    for weight in ["Regular", "Medium", "SemiBold"] {
      guard let url = Bundle.main.url(forResource: "CoinDropReward-Inter-\(weight)", withExtension: "ttf") else { continue }
      CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil)
      if let descriptors = CTFontManagerCreateFontDescriptorsFromURL(url as CFURL) as? [CTFontDescriptor],
        let first = descriptors.first,
        let name = CTFontDescriptorCopyAttribute(first,kCTFontNameAttribute) as? String { names[weight] = name }
    }
    return names
  }()
  static func font(_ size: Double, _ weight: String = "Regular") -> Font {
    .custom(fonts[weight] ?? (weight == "Regular" ? "Inter24pt-Regular" : "Inter-\(weight)"), fixedSize: size)
  }
}

struct CoinDropRewardLayout {
  var w: Double, h: Double, side = 20.0
  var titleTop: Double, sectionTop: Double, rowsTop: Double, bonusLabelTop: Double, bonusTop: Double
  var tileCx: Double, tileCy: Double, walletLeft: Double, walletTop: Double, daysTop: Double
  var toastCoin: CGPoint { CGPoint(x:tileCx,y:tileCy-24) }
  var walletIcon: CGPoint { CGPoint(x:walletLeft+24,y:walletTop+20) }
  func rowCoin(_ i: Int) -> CGPoint { CGPoint(x:42,y:rowsTop+Double(i)*72+36) }
  var dx: Double { walletIcon.x-toastCoin.x }
  var dy: Double { walletIcon.y-toastCoin.y }
  var span: Double { hypot(dx,dy) }
  init(w: Double,h: Double,top: Double,bottom: Double) {
    self.w = w; self.h = h
    walletTop = top+8; titleTop = walletTop+40+12
    sectionTop = titleTop+30+4+20+28; rowsTop = sectionTop+18+8
    bonusLabelTop = rowsTop+144+20; bonusTop = bonusLabelTop+18+8
    tileCx = w/2; walletLeft = w-20-124
    daysTop = h-max(bottom,16)-76; tileCy = ((bonusTop+72+daysTop)/2).rounded()
  }
}
