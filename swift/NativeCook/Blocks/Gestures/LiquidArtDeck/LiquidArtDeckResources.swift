import SwiftUI
import UIKit
import CoreText

final class LiquidArtDeckResources {
  static let shared = LiquidArtDeckResources()
  let cards: [UIImage]
  static func color(_ hex: Int) -> Color { Color(.sRGB, red: Double((hex>>16)&255)/255, green: Double((hex>>8)&255)/255, blue: Double(hex&255)/255, opacity: 1) }
  static func canvasColor(_ hex: Int) -> Color { Color(ui(hex)) }
  static func ui(_ hex: Int) -> UIColor { UIColor(red: CGFloat((hex>>16)&255)/255, green: CGFloat((hex>>8)&255)/255, blue: CGFloat(hex&255)/255, alpha: 1) }
  init() {
    for name in ["Outfit-Medium", "Inter-Medium", "Inter-SemiBold"] {
      if let url = Bundle.main.url(forResource: "LiquidArtDeck-\(name)", withExtension: "ttf") { CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil) }
    }
    let title = UIFont(name:"Outfit-Medium",size:19) ?? .systemFont(ofSize:19)
    let small = UIFont(name:"Inter-Medium",size:12.5) ?? .systemFont(ofSize:12.5)
    let fmt = UIGraphicsImageRendererFormat(); fmt.scale = 3
    cards = (0..<6).map { i in
      UIGraphicsImageRenderer(size:CGSize(width:382,height:484),format:fmt).image { r in
        let c = r.cgContext
        c.setShadow(offset:CGSize(width:1000,height:0),blur:36,color:UIColor.black.withAlphaComponent(0.5).cgColor)
        Self.ui(0x000000).setFill()
        UIBezierPath(roundedRect:CGRect(x:-940,y:72,width:262,height:374),cornerRadius:26).fill()
        c.setShadow(offset:.zero,blur:0,color:nil)
        Self.ui(0x1c1c22).setFill(); UIBezierPath(roundedRect:CGRect(x:50,y:50,width:282,height:384),cornerRadius:26).fill()
        func text(_ s: String, _ x: Double, _ baseline: Double, _ f: UIFont, _ color: UIColor) {
          let font = f as CTFont, chars = Array(s.utf16)
          var glyphs = Array(repeating:CGGlyph(0),count:chars.count)
          CTFontGetGlyphsForCharacters(font,chars,&glyphs,chars.count)
          var widths = Array(repeating:CGSize.zero,count:chars.count)
          CTFontGetAdvancesForGlyphs(font,.horizontal,glyphs,&widths,chars.count)
          var advance = 0.0
          let points = widths.map { width -> CGPoint in
            let point = CGPoint(x:advance,y:0); advance += width.width; return point
          }
          c.saveGState(); c.translateBy(x:x,y:baseline); c.scaleBy(x:1,y:-1); c.setFillColor(color.cgColor)
          CTFontDrawGlyphs(font,glyphs,points,glyphs.count,c); c.restoreGState()
        }
        text(LiquidArtDeckMotion.titles[i],64,380,title,.white)
        text(LiquidArtDeckMotion.authors[i],64,402,small,Self.ui(0x9a9aa6))
        let price = LiquidArtDeckMotion.prices[i]
        let chars = Array(price.utf16)
        var glyphs = Array(repeating:CGGlyph(0),count:chars.count)
        CTFontGetGlyphsForCharacters(small as CTFont,chars,&glyphs,chars.count)
        var widths = Array(repeating:CGSize.zero,count:chars.count)
        CTFontGetAdvancesForGlyphs(small as CTFont,.horizontal,glyphs,&widths,chars.count)
        let tw = widths.reduce(0.0) { $0+$1.width }
        Self.ui(0x2b2b33).setFill(); UIBezierPath(roundedRect:CGRect(x:322-tw-22,y:378,width:tw+18,height:26),cornerRadius:13).fill()
        text(price,322-tw-13,395,small,Self.ui(0xe8ff8a))
      }
    }
  }
  private static let icons = (0..<4).map { makeIcon($0) }
  static func icon(_ kind: Int) -> Path { icons[kind] }
  private static func makeIcon(_ kind: Int) -> Path {
    var p = Path()
    if kind == 0 {
      p.move(to:CGPoint(x:12,y:20.5)); p.addCurve(to:CGPoint(x:4.5,y:10.4),control1:CGPoint(x:12,y:20.5),control2:CGPoint(x:4.5,y:15.9))
      func arc(_ from: CGPoint, _ to: CGPoint, _ radius: Double) {
        let dx = to.x-from.x, dy = to.y-from.y, dist = hypot(dx,dy)
        let h = sqrt(max(0,radius*radius-dist*dist/4))
        let center = CGPoint(x:(from.x+to.x)/2-dy/dist*h,y:(from.y+to.y)/2+dx/dist*h)
        p.addArc(center:center,radius:radius,startAngle:.radians(atan2(from.y-center.y,from.x-center.x)),endAngle:.radians(atan2(to.y-center.y,to.x-center.x)),clockwise:false)
      }
      arc(CGPoint(x:4.5,y:10.4),CGPoint(x:12,y:7.7),4.3)
      arc(CGPoint(x:12,y:7.7),CGPoint(x:19.5,y:10.4),4.3)
      p.addCurve(to:CGPoint(x:12,y:20.5),control1:CGPoint(x:19.5,y:15.9),control2:CGPoint(x:12,y:20.5)); p.closeSubpath()
    } else if kind == 1 {
      p.move(to:CGPoint(x:4,y:11)); for v in [(12.0,4.0),(20,11),(20,19)] { p.addLine(to:CGPoint(x:v.0,y:v.1)) }
      p.addQuadCurve(to:CGPoint(x:19,y:20),control:CGPoint(x:20,y:20))
      for v in [(15.0,20.0),(15,14),(9,14),(9,20),(5,20)] { p.addLine(to:CGPoint(x:v.0,y:v.1)) }
      p.addQuadCurve(to:CGPoint(x:4,y:19),control:CGPoint(x:4,y:20));p.closeSubpath()
    } else if kind == 2 {
      p.addEllipse(in:CGRect(x:4,y:4,width:14,height:14));p.move(to:CGPoint(x:16,y:16));p.addLine(to:CGPoint(x:20.5,y:20.5))
    } else {
      p.addEllipse(in:CGRect(x:8.2,y:4.7,width:7.6,height:7.6));p.move(to:CGPoint(x:4.8,y:20.5));p.addCurve(to:CGPoint(x:12,y:14.7),control1:CGPoint(x:5.9,y:16.7),control2:CGPoint(x:8.7,y:14.7));p.addCurve(to:CGPoint(x:19.2,y:20.5),control1:CGPoint(x:15.3,y:14.7),control2:CGPoint(x:18.1,y:16.7))
    }
    return p.applying(CGAffineTransform(scaleX:26/24,y:26/24))
  }
}
