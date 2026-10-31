import SwiftUI
import CoreText
import UIKit

enum JellyScaleExchangeTheme {
  static func color(_ hex: UInt32) -> Color {
    Color(.sRGB, red: Double((hex>>16)&255)/255, green: Double((hex>>8)&255)/255, blue: Double(hex&255)/255, opacity: 1)
  }
  static let page = color(0x14122B), ink = color(0xF3F1FF), mute = color(0x9894B8)
  static let card = color(0x211E3D), line = color(0x34305A), accent = color(0xFFC53D)
  static let accentInk = color(0x2A1D00), lcd = color(0x9CF2C5)
  static let fonts: [String: String] = {
    var names: [String: String] = [:]
    for weight in ["Regular", "Medium", "SemiBold"] {
      if let url = Bundle.main.url(forResource: "JellyScaleExchange-Inter-\(weight)", withExtension: "ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        if let descriptors = CTFontManagerCreateFontDescriptorsFromURL(url as CFURL) as? [CTFontDescriptor],
           let descriptor = descriptors.first,
           let name = CTFontDescriptorCopyAttribute(descriptor, kCTFontNameAttribute) as? String { names[weight] = name }
      }
    }
    return names
  }()
  static func font(_ size: Double, _ weight: String = "SemiBold") -> Font {
    .custom(fonts[weight] ?? (weight == "Regular" ? "Inter24pt-Regular" : "Inter-\(weight)"), size: size)
  }
  struct Layout {
    let w: Double, h: Double, top: Double, bottom: Double
    var ppu: Double { (w*0.4).rounded() }
    var stageY: Double { top+96+((h-top-bottom-96-120)*0.5).rounded() }
    var buttonsTop: Double { h-max(bottom,16)-8-56 }
    var lcdY: Double { stageY+(0.6*cos(0.55)+0.78*sin(0.55))*ppu }
    var lcdW: Double { ppu*0.92 }; var lcdH: Double { ppu*0.25 }
    var pillX: Double { w-80 }; var pillY: Double { top+30 }
  }
}
