import CoreText
import SwiftUI
import UIKit

final class DiceBoardRewardsResources {
  struct Label {
    let image: Image
    let width, height, ascent: Double
  }
  static let shared = DiceBoardRewardsResources()
  private var cache: [String: Label] = [:]
  init() {
    for name in ["TitanOne-Regular","Nunito-Black","Nunito-ExtraBold","Ionicons"] {
      if let url = Bundle.main.url(forResource:"DiceBoardRewards-\(name)",withExtension:"ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil)
      }
    }
    _ = label("Lucky Lap",26,"2E2A3D",title:true)
    for (text,size,color) in [("2,480",14.0,"2E2A3D"),("Land on a coupon to win it for your basket",14,"8A8098"),("Flick the die up to roll",13,"A196B0"),("Roll",18,"FFFFFF")] {
      _ = label(text,size,color)
    }
    for text in DiceBoardRewardsMotion.labels { _ = label(text,11,"3B3350",skia:true); _ = label(text,11,"FFFFFF",skia:true) }
    for text in DiceBoardRewardsMotion.prizes { _ = label(text,15,"2E2A3D") }
    for i in 0...3 { _ = label("\(i) rolls left today",15,"5B5270") }
    _ = label(String(UnicodeScalar(62026)!),22,"2E2A3D",icon:true)
    _ = label(String(UnicodeScalar(62116)!),20,"FFFFFF",icon:true)
    _ = label(String(UnicodeScalar(62698)!),18,"FF6B5A",icon:true)
  }
  static func color(_ hex: String,_ alpha: Double = 1,_ shade: Double = 1) -> Color {
    let v = UInt32(hex,radix:16) ?? 0
    func c(_ shift: UInt32) -> Double { min(255,(Double((v >> shift)&255)*shade).rounded())/255 }
    return Color(.sRGB,red:c(16),green:c(8),blue:c(0),opacity:alpha)
  }
  func label(_ text: String,_ size: Double,_ hex: String,title: Bool = false,icon: Bool = false,skia: Bool = false) -> Label {
    let key = "\(text)/\(size)/\(hex)/\(title)/\(icon)/\(skia)"
    if let found = cache[key] { return found }
    let font = UIFont(name:icon ? "Ionicons" : title ? "TitanOne" : "Nunito-Black",size:size) ?? .systemFont(ofSize:size)
    let ctFont = font as CTFont
    let characters = Array(text.utf16)
    var glyphs = [CGGlyph](repeating:0,count:characters.count)
    CTFontGetGlyphsForCharacters(ctFont,characters,&glyphs,characters.count)
    var advances = [CGSize](repeating:.zero,count:glyphs.count)
    var bounds = [CGRect](repeating:.zero,count:glyphs.count)
    CTFontGetAdvancesForGlyphs(ctFont,.horizontal,glyphs,&advances,glyphs.count)
    CTFontGetBoundingRectsForGlyphs(ctFont,.horizontal,glyphs,&bounds,glyphs.count)
    var advance = 0.0
    var union = CGRect.null
    let positions = glyphs.indices.map { i -> CGPoint in
      let point = CGPoint(x:advance,y:0)
      union = union.union(bounds[i].offsetBy(dx:advance,dy:0))
      advance += advances[i].width
      return point
    }
    // SkFont.measureText returns the glyph bounds, including its one-point
    // antialias outset, rather than the advance width of the string.
    let width = skia && !glyphs.isEmpty ? ceil(union.maxX)-floor(union.minX)+2 : ceil((text as NSString).size(withAttributes:[.font:font]).width*3)/3
    let height = ceil(font.lineHeight*3)/3
    let format = UIGraphicsImageRendererFormat()
    format.scale = 3
    let bitmap = UIGraphicsImageRenderer(size:CGSize(width:max(1,ceil(max(width,advance))+4),height:height+4),format:format).image { renderer in
      let cg = renderer.cgContext
      cg.setFillColor(UIColor(DiceBoardRewardsResources.color(hex)).cgColor)
      cg.translateBy(x:2,y:2+font.ascender)
      cg.scaleBy(x:1,y:-1)
      if skia {
        // drawSimpleText in Skia does not shape or kern the tile labels.
        CTFontDrawGlyphs(ctFont,glyphs,positions,glyphs.count,cg)
      } else {
        let line = CTLineCreateWithAttributedString(NSAttributedString(string:text,attributes:[.font:font,.foregroundColor:UIColor(DiceBoardRewardsResources.color(hex))]))
        cg.textPosition = .zero
        CTLineDraw(line,cg)
      }
    }
    let result = Label(image:Image(uiImage:bitmap),width:width,height:height,ascent:font.ascender)
    cache[key] = result
    return result
  }
}
