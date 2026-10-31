import SwiftUI
import UIKit
import CoreText

final class FriendRadarMapResources {
  static let shared = FriendRadarMapResources()
  struct Glyph { let path: Path; let width: Double }
  struct Label { let glyphs: [Glyph]; let width, ascent, height: Double }
  var fonts: [String: CTFont] = [:]
  private var labels: [String: Label] = [:]
  private var glyphPaths: [String: Path] = [:]
  private var viewport = CGSize.zero
  private var fadePath = Path()
  var map = UIImage()
  var faces: [UIImage] = []
  let trapezoids: [Path]
  static func color(_ hex: Int, _ a: Double = 1) -> Color {
    Color(.sRGB, red: Double(hex>>16 & 255)/255, green: Double(hex>>8 & 255)/255, blue: Double(hex & 255)/255, opacity: a)
  }
  static func ui(_ hex: Int) -> UIColor {
    UIColor(red: CGFloat(hex>>16 & 255)/255, green: CGFloat(hex>>8 & 255)/255, blue: CGFloat(hex & 255)/255, alpha: 1)
  }
  init() {
    trapezoids = FriendRadarMapMotion.friends.map { f in
      var p = Path()
      p.move(to: CGPoint(x: -75-150*f.skew*0.2, y: -52))
      p.addLine(to: CGPoint(x: 75-150*f.skew*0.2, y: -52-104*f.skew*0.25))
      p.addLine(to: CGPoint(x: 150*0.38+150*f.skew*0.15, y: 52))
      p.addLine(to: CGPoint(x: -150*0.38+150*f.skew*0.15, y: 52+104*f.skew*0.2))
      p.closeSubpath(); return p
    }
    for (key,file,size) in [("name","PixelifySans-700",64.0),("map","JetBrainsMono-Medium",11.0),("mono","JetBrainsMono-Medium",11.0),("street","JetBrainsMono-Bold",14.0),("order","JetBrainsMono-Bold",13.0),("knob","JetBrainsMono-Bold",28.0)] {
      if let url = Bundle.main.url(forResource: "FriendRadarMap-"+file, withExtension: "ttf"), let provider = CGDataProvider(url: url as CFURL), let font = CGFont(provider) {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        fonts[key] = CTFontCreateWithGraphicsFont(font,size,nil,nil)
      }
    }
    map = makeMap()
    faces = (0..<3).map { makeFace($0) }
    for f in FriendRadarMapMotion.friends {
      _ = label(f.street,"street",0.6); _ = label(f.sooner,"mono",0.6)
    }
    for ch in "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#%&*+=? " { _ = label(String(ch),"name") }
  }
  func label(_ text: String, _ key: String, _ tracking: Double = 0) -> Label {
    let cache = key+"|"+String(tracking)+"|"+text
    if let found = labels[cache] { return found }
    let f = fonts[key] ?? (UIFont.systemFont(ofSize: 14) as CTFont)
    let chars = Array(text.utf16)
    var glyphs = [CGGlyph](repeating: 0,count: chars.count)
    CTFontGetGlyphsForCharacters(f,chars,&glyphs,chars.count)
    var advances = [CGSize](repeating: .zero,count: chars.count)
    CTFontGetAdvancesForGlyphs(f,.horizontal,glyphs,&advances,chars.count)
    let parts = glyphs.enumerated().map { i,g -> Glyph in
      let glyphKey = key+"|"+String(g)
      let path: Path
      if let cached = glyphPaths[glyphKey] { path = cached }
      else {
        path = Path(CTFontCreatePathForGlyph(f,g,nil) ?? CGMutablePath()).applying(CGAffineTransform(scaleX:1,y:-1))
        glyphPaths[glyphKey] = path
      }
      return Glyph(path:path,width:advances[i].width+tracking)
    }
    let result = Label(glyphs: parts,width: parts.reduce(0){$0+$1.width},ascent: CTFontGetAscent(f),height: ceil(CTFontGetAscent(f)+CTFontGetDescent(f)+CTFontGetLeading(f)))
    labels[cache] = result; return result
  }
  func fadeRect(_ size:CGSize)->Path {
    if viewport != size {
      viewport = size; fadePath = Path(CGRect(x:0,y:size.height-330,width:size.width,height:330))
    }
    return fadePath
  }
  private func bitmap(_ size: CGSize, scale: Double = 3, _ draw: (CGContext)->Void) -> UIImage {
    let format = UIGraphicsImageRendererFormat(); format.scale = scale; format.preferredRange = .standard
    return UIGraphicsImageRenderer(size: size,format: format).image { draw($0.cgContext) }
  }
  private func makeMap() -> UIImage {
    bitmap(CGSize(width: 1100,height: 2000)) { c in
      c.setFillColor(Self.ui(0x101012).cgColor); c.fill(CGRect(x:0,y:0,width:1100,height:1500))
      c.setLineCap(.round); c.setLineJoin(.round)
      func stroke(_ p: CGPath,_ width: Double,_ color: Int) { c.setStrokeColor(Self.ui(color).cgColor); c.setLineWidth(width); c.addPath(p); c.strokePath() }
      c.saveGState(); c.translateBy(x:550,y:750); c.rotate(by:18 * .pi/180)
      for i in -9...9 {
        let d = Double(i), x = d*92+(FriendRadarMapMotion.random(d)-0.5)*30
        let p = CGMutablePath(); p.move(to:CGPoint(x:x,y:-1100)); p.addCurve(to:CGPoint(x:x+20,y:1100),control1:CGPoint(x:x+40*(FriendRadarMapMotion.random(d+3)-0.5),y:-300),control2:CGPoint(x:x-40*(FriendRadarMapMotion.random(d+7)-0.5),y:300))
        stroke(p,i%4 == 0 ? 8:3,i%4 == 0 ? 0x38383f:0x26262b)
      }
      for j in -12...12 {
        let d = Double(j), y = d*86+(FriendRadarMapMotion.random(d+40)-0.5)*26
        let p = CGMutablePath(); p.move(to:CGPoint(x:-1000,y:y)); p.addCurve(to:CGPoint(x:1000,y:y+10),control1:CGPoint(x:-300,y:y+30*(FriendRadarMapMotion.random(d+50)-0.5)),control2:CGPoint(x:300,y:y-30*(FriendRadarMapMotion.random(d+60)-0.5)))
        stroke(p,j%5 == 0 ? 8:3,j%5 == 0 ? 0x38383f:0x26262b)
      }
      c.restoreGState()
      let a = CGMutablePath(); a.move(to:CGPoint(x:-50,y:300)); a.addCurve(to:CGPoint(x:1150,y:1300),control1:CGPoint(x:400,y:520),control2:CGPoint(x:700,y:760)); stroke(a,10,0x3d3d45)
      let b = CGMutablePath(); b.move(to:CGPoint(x:1150,y:200)); b.addCurve(to:CGPoint(x:-50,y:1250),control1:CGPoint(x:800,y:600),control2:CGPoint(x:500,y:900)); stroke(b,10,0x3d3d45)
      stroke(CGPath(ellipseIn:CGRect(x:200,y:420,width:720,height:720),transform:nil),6,0x2f2f36)
      let names: [(String,Double,Double,Double)] = [("Berson St",380,610,18),("Kiln Ave",700,820,-42),("Pier Rd",250,1120,18),("Mill Ln",820,420,18),("Ring Rd",220,700,-70),("Dock St",600,1240,18)]
      for (n,x,y,angle) in names {
        c.saveGState(); c.translateBy(x:x,y:y); c.rotate(by:angle * .pi/180); c.setFillColor(Self.ui(0x5a5a62).cgColor)
        for g in label(n,"map").glyphs { c.addPath(g.path.cgPath); c.fillPath(); c.translateBy(x:g.width,y:0) }
        c.restoreGState()
      }
    }
  }
  private func makeFace(_ index: Int) -> UIImage {
    bitmap(CGSize(width:138,height:120)) { c in
      let bayer = [0,8,2,10,12,4,14,6,3,11,1,9,15,7,13,5]
      func sm(_ a: Double,_ b: Double,_ x: Double) -> Double { let k = FriendRadarMapMotion.clamp((x-a)/(b-a)); return k*k*(3-2*k) }
      func ell(_ u:Double,_ v:Double,_ cx:Double,_ cy:Double,_ rx:Double,_ ry:Double)->Double { pow((u-cx)/rx,2)+pow((v-cy)/ry,2) }
      c.setShouldAntialias(false)
      for y in 0..<40 { for x in 0..<46 {
        let u = (Double(x)+0.5)/46-0.5, v = (Double(y)+0.5)/40
        var l = 0.12+0.22*(1-v)
        if ell(u,v,0,1.08,0.47,0.34)<1 { l = 0.26+0.28*sm(0.3,-0.3,u) }
        if abs(u)<0.075 && v>0.6 && v<0.84 { l = 0.5-0.25*sm(-0.02,0.07,u) }
        let face = ell(u,v,0,0.42,0.19,0.25)
        if face<1 {
          l = 0.92-0.55*sm(-0.12,0.2,u)-0.12*sm(0.45,0.65,v)
          if ell(u,v,0.025,0.5,0.025,0.04)<1 { l -= 0.25 }
          if abs(v-0.575)<0.012 && abs(u)<0.055 { l = 0.22 }
          if index == 0 {
            if ell(u,v,-0.075,0.41,0.07,0.04)<1 || ell(u,v,0.075,0.41,0.07,0.04)<1 || (abs(v-0.4)<0.01 && abs(u)<0.03) { l = 0.03 }
            if ell(u,v,-0.095,0.4,0.015,0.012)<1 { l = 0.95 }
          } else {
            if ell(u,v,-0.072,0.41,0.026,0.018)<1 || ell(u,v,0.072,0.41,0.026,0.018)<1 { l = 0.06 }
            if abs(v-0.36)<0.01 && abs(abs(u)-0.075)<0.04 { l = 0.15 }
          }
        }
        let crown = ell(u,v,0,0.36,0.22,0.3)<1 && v<0.3-0.06*cos(u*9)
        if index == 0 && (crown || (abs(u)>0.15 && abs(u)<0.25 && v>0.2 && v<0.6 && face>0.75)) { l = 0.07+0.25*sm(0.18,0.12,abs(u+0.06))*(v<0.3 ? 1:0) }
        if index == 1 && ell(u,v,0,0.36,0.21,0.28)<1 && v<0.24 { l = 0.1 }
        if index == 2 && (ell(u,v,0,0.1,0.085,0.07)<1 || (ell(u,v,0,0.36,0.21,0.28)<1 && v<0.26)) { l = 0.09+0.2*sm(0,-0.1,u) }
        let q = l*2+Double(bayer[(y%4)*4+x%4])/16-0.5
        c.setFillColor(Self.ui(q<0.5 ? 0x0a1c08:q<1.5 ? 0x4f9a2e:0xd9ff9e).cgColor)
        c.fill(CGRect(x:Double(x)*3,y:Double(y)*3,width:3.35,height:3.35))
      }}
    }
  }
}
