import SwiftUI
import UIKit
import CoreText

final class RibbonRoadOnboardingResources {
  static let shared=RibbonRoadOnboardingResources()
  let font: CTFont
  let glyphs: [CGGlyph]
  let advances: [Double]
  let patternLength: Double
  init() {
    for name in ["Manrope_400Regular","Manrope_600SemiBold","Archivo-ExtraBold"] {
      if let url=Bundle.main.url(forResource:"RibbonRoadOnboarding-\(name)",withExtension:"ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil)
      }
    }
    font=(UIFont(name:"Archivo-ExtraBold",size:21) ?? UIFont.systemFont(ofSize:21,weight:.heavy)) as CTFont
    let chars=Array("NEW EVENTS FOR YOU  ·  ".utf16)
    var ids=[CGGlyph](repeating:0,count:chars.count)
    CTFontGetGlyphsForCharacters(font,chars,&ids,chars.count)
    glyphs=ids
    var widths=[CGSize](repeating:.zero,count:chars.count)
    CTFontGetAdvancesForGlyphs(font,.horizontal,ids,&widths,ids.count)
    advances=widths.map { $0.width+0.6 }
    patternLength=advances.reduce(0,+)
  }
  static func color(_ hex: UInt32) -> UIColor {
    UIColor(red:Double((hex>>16)&255)/255,green:Double((hex>>8)&255)/255,blue:Double(hex&255)/255,alpha:1)
  }
  static func face(_ size: Double,_ semi: Bool=true) -> UIFont {
    _=shared
    return UIFont(name:semi ? "Manrope-SemiBold" : "Manrope-Regular",size:size) ?? UIFont.systemFont(ofSize:size)
  }
  static let copyLeading = 21-face(15,false).lineHeight
  static let icons: [UIImage] = (0..<3).map { kind in
    let side = kind==0 ? 22.0 : kind==1 ? 18.0 : 24.0
    return UIGraphicsImageRenderer(size:CGSize(width:side,height:side)).image { renderer in
      RibbonRoadOnboardingArt.icon(renderer.cgContext,kind)
    }
  }
}
