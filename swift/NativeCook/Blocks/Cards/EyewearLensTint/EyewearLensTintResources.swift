import CoreText
import UIKit
import SwiftUI

// All fonts, the decoded portrait and the static artwork are prepared outside the frame loop.
final class EyewearLensTintResources {
  typealias H = EyewearLensTintTheme
  static let shared = EyewearLensTintResources()
  let portrait: UIImage
  private var key: H.Layout?
  private var scale = 0.0
  private var page = UIImage(), droplets = UIImage()
  private var controls: [Int: UIImage] = [:]
  private(set) var rings: [Path] = []
  private init() {
    for name in ["PTSans-Regular", "PTSans-Bold"] {
      if let url = Bundle.main.url(forResource: "EyewearLensTint-\(name)", withExtension: "ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
      }
    }
    if let url = Bundle.main.url(forResource: "EyewearLensTint-elt-portrait", withExtension: "jpg"), let image = UIImage(contentsOfFile: url.path) {
      portrait = image.preparingForDisplay() ?? image
    } else { portrait = UIImage(); assertionFailure("EyewearLensTint portrait missing") }
  }
  static func ui(_ hex: Int, _ alpha: Double = 1) -> UIColor {
    UIColor(red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255, blue: Double(hex & 255) / 255, alpha: alpha)
  }
  private func font(_ size: Double, _ bold: Bool) -> UIFont {
    UIFont(name: bold ? "PTSans-Bold" : "PTSans-Regular", size: size) ?? .systemFont(ofSize: size)
  }
  private func text(_ value: String, _ p: CGPoint, _ size: Double, _ color: Int, bold: Bool = false, track: Double = 0, right: Bool = false, width: Double? = nil, line: Double? = nil) {
    let face = font(size, bold)
    var attrs: [NSAttributedString.Key: Any] = [.font: face, .foregroundColor: Self.ui(color)]
    if track != 0 { attrs[.kern] = track }
    if let line {
      let paragraph = NSMutableParagraphStyle(); paragraph.minimumLineHeight = line; paragraph.maximumLineHeight = line
      attrs[.paragraphStyle] = paragraph
      attrs[.baselineOffset] = max(0, (line - face.lineHeight) / 2)
    }
    let label = NSAttributedString(string: value, attributes: attrs)
    if let width, let line {
      let typesetter = CTTypesetterCreateWithAttributedString(label)
      var start = 0, row = 0
      while start < label.length {
        let count = CTTypesetterSuggestLineBreak(typesetter, start, width)
        guard count > 0 else { break }
        let part = label.attributedSubstring(from: NSRange(location: start, length: count))
        part.draw(with: CGRect(x:p.x,y:p.y+Double(row)*line,width:width,height:line+20), options:[.usesLineFragmentOrigin,.usesFontLeading],context:nil)
        start += count; row += 1
      }
    }
    else {
      let x = ((right ? p.x-label.size().width : p.x)*scale).rounded()/scale
      label.draw(at: CGPoint(x:x,y:p.y))
    }
  }
  private func image(_ size: CGSize, _ scale: Double, _ draw: (CGContext) -> Void) -> UIImage {
    let format = UIGraphicsImageRendererFormat(); format.scale = scale; format.opaque = false
    format.preferredRange = .standard
    return UIGraphicsImageRenderer(size: size, format: format).image { draw($0.cgContext) }
  }
  func prepare(_ l: H.Layout, _ scale: Double) {
    if key == l && self.scale == scale { return }
    key = l; self.scale = scale
    rings = (0..<4).map { i in
      let d = l.drop(i), radius = 13*l.sx+3
      return Path(ellipseIn: CGRect(x:d.x-radius,y:d.y-radius,width:radius*2,height:radius*2))
    }
    droplets = image(CGSize(width: l.w, height: l.h), scale) { c in
      for i in 0..<4 {
        let d = l.drop(i), r = 13 * l.sx
        c.saveGState(); c.addEllipse(in: CGRect(x: d.x-r, y: d.y-r, width: 2*r, height: 2*r)); c.clip()
        let tint = Self.ui(H.tints[i].swatch).cgColor
        if let gradient = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: [UIColor.white.cgColor, tint, tint] as CFArray, locations: [0, 0.35, 1]) {
          c.drawRadialGradient(gradient, startCenter: CGPoint(x: d.x-4, y: d.y-4), startRadius: 0, endCenter: CGPoint(x: d.x-4, y: d.y-4), endRadius: r*1.4, options: [.drawsAfterEndLocation])
        }
        c.restoreGState()
      }
    }
    page = image(CGSize(width: l.w, height: l.h), scale) { c in
      let card = l.card
      if let gradient = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: [Self.ui(0xFFFFFF,0).cgColor, Self.ui(0xFFFFFF,0.75).cgColor] as CFArray, locations: [0,1]) {
        c.saveGState(); c.clip(to: CGRect(x: 0, y: card.minY-90, width: l.w, height: 90))
        c.drawLinearGradient(gradient, start: CGPoint(x: 0, y: card.minY-90), end: CGPoint(x: 0, y: card.minY), options: [])
        c.restoreGState()
      }
      // CALayer shadowRadius 12 corresponds to CGContext blur 24.
      c.saveGState(); c.setShadow(offset: CGSize(width: 0, height: 4), blur: 24, color: Self.ui(0,0.08).cgColor)
      c.setFillColor(UIColor.white.cgColor); c.fill(card); c.restoreGState()
      text("SUNGLASSES", CGPoint(x: 56*l.sx, y: l.y(622)-16), 25, 0x1C1C1E, bold: true, track: 0.5)
      text("$220", CGPoint(x: 345*l.sx, y: l.y(622)-14), 22, 0x1C1C1E, right: true)
      let star = Self.star
      for i in 0..<5 {
        c.saveGState(); c.translateBy(x: 56*l.sx + Double(i)*16, y: l.y(649)-8); c.scaleBy(x: 15/24, y: 15/24)
        c.setFillColor(Self.ui(i < 4 ? 0x1C1C1E : 0xD6D6DB).cgColor); c.addPath(star); c.fillPath()
        if i == 4 { c.clip(to: CGRect(x: 0,y: 0,width: 12,height: 24)); c.setFillColor(Self.ui(0x1C1C1E).cgColor); c.addPath(star); c.fillPath() }
        c.restoreGState()
      }
      text("  4.5   |   18 REVIEWS", CGPoint(x: 56*l.sx+80, y: l.y(649)-8+(15-font(10,false).lineHeight)/2), 10, 0xA2A2A8, track: 0.4)
      text(H.body, CGPoint(x: 56*l.sx, y: l.y(681)-10), 12.5, 0x5E5E64, width: 290*l.sx, line: 21)
      let wish = NSAttributedString(string: "ADD TO WISHLIST", attributes: [.font: font(10,false), .kern: 0.4]).size().width
      let x = ((345*l.sx-wish-20)*scale).rounded()/scale, y = l.y(768)-8
      c.saveGState(); c.translateBy(x: x,y: y); c.scaleBy(x: 14/22,y: 13/20)
      c.setFillColor(Self.ui(0xC9C9CE).cgColor); c.addPath(Self.heart); c.fillPath(); c.restoreGState()
      text("ADD TO WISHLIST", CGPoint(x: x+20,y: y+(13-font(10,false).lineHeight)/2), 10, 0xA2A2A8, track: 0.4)
    }
    controls[0] = image(CGSize(width:44,height:44), scale) { c in
      c.translateBy(x:11,y:14); c.setStrokeColor(Self.ui(0x1C1C1E).cgColor); c.setLineWidth(1.8); c.setLineCap(.round)
      for (y,x) in [(1.5,21.0),(8.0,14.0),(14.5,21.0)] { c.move(to: CGPoint(x:1,y:y)); c.addLine(to: CGPoint(x:x,y:y)) }; c.strokePath()
    }
    controls[1] = image(CGSize(width:44,height:44), scale) { c in
      c.translateBy(x:12,y:12); c.setStrokeColor(Self.ui(0x1C1C1E).cgColor); c.setLineWidth(1.8); c.setLineCap(.round)
      c.strokeEllipse(in: CGRect(x:2,y:2,width:13,height:13)); c.move(to:CGPoint(x:13.5,y:13.5)); c.addLine(to:CGPoint(x:18.5,y:18.5)); c.strokePath()
    }
    controls[2] = image(CGSize(width:48,height:48), scale) { c in
      c.setFillColor(Self.ui(0x1E1E20).cgColor); c.fillEllipse(in:CGRect(x:0,y:0,width:48,height:48))
      c.translateBy(x:17,y:19.5); c.setStrokeColor(UIColor.white.cgColor); c.setLineWidth(1.8); c.setLineCap(.round); c.setLineJoin(.round)
      c.move(to:CGPoint(x:1.5,y:1.5)); c.addLine(to:CGPoint(x:7,y:7)); c.addLine(to:CGPoint(x:12.5,y:1.5)); c.strokePath()
    }
    controls[3] = image(CGSize(width:122*l.sx,height:37), scale) { c in
      c.setFillColor(Self.ui(0x1E1E20).cgColor); c.fill(CGRect(x:0,y:0,width:122*l.sx,height:37))
      let attrs: [NSAttributedString.Key: Any] = [.font: font(15,true), .kern:0.6, .foregroundColor:UIColor.white]
      let text = NSAttributedString(string:"ADD TO BAG",attributes:attrs); let s=text.size()
      text.draw(at:CGPoint(x:((122*l.sx-s.width)/2*scale).rounded()/scale,y:((37-s.height)/2*scale).rounded()/scale))
    }
  }
  func overlay(_ l: H.Layout, _ scale: Double) -> UIImage { prepare(l,scale); return page }
  func drops(_ l: H.Layout, _ scale: Double) -> UIImage { prepare(l,scale); return droplets }
  func control(_ i: Int, _ l: H.Layout, _ scale: Double) -> UIImage { prepare(l,scale); return controls[i] ?? UIImage() }
  static let star: CGPath = {
    let p=CGMutablePath(); let pts:[CGPoint]=[.init(x:12,y:1.8),.init(x:15.1,y:8.3),.init(x:22.2,y:9.2),.init(x:17,y:14.1),.init(x:18.3,y:21.2),.init(x:12,y:17.8),.init(x:5.7,y:21.2),.init(x:7,y:14.1),.init(x:1.8,y:9.2),.init(x:8.9,y:8.3)]
    p.addLines(between:pts); p.closeSubpath(); return p
  }()
  static let heart: CGPath = {
    let p=CGMutablePath(); p.move(to:.init(x:11,y:18.2)); p.addCurve(to:.init(x:1.6,y:6.6),control1:.init(x:4,y:13.4),control2:.init(x:1.6,y:10.2))
    let radius = hypot(4.7,1.0)
    let a = atan2(1.0,-4.7)
    p.addArc(center:.init(x:6.3,y:5.6),radius:radius,startAngle:a,endAngle:a + .pi,clockwise:false)
    let b = atan2(-1.0,-4.7)
    p.addArc(center:.init(x:15.7,y:5.6),radius:radius,startAngle:b,endAngle:b + .pi,clockwise:false)
    p.addCurve(to:.init(x:11,y:18.2),control1:.init(x:20.4,y:10.2),control2:.init(x:18,y:13.4)); p.closeSubpath(); return p
  }()
}
