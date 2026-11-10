import CoreText
import SwiftUI
import UIKit

final class AppIconAtelierResources {
  static let shared=AppIconAtelierResources()
  static func url(_ name: String,_ ext: String) -> URL? {
    Bundle.main.url(forResource:"AppIconAtelier-\(name)",withExtension:ext)
  }
  private let fonts: [String:[Double:Font]] = [
    "Inter-Regular":[15:.custom("Inter24pt-Regular",fixedSize:15),17:.custom("Inter24pt-Regular",fixedSize:17)],
    "Inter-Medium":[11:.custom("Inter-Medium",fixedSize:11),12:.custom("Inter-Medium",fixedSize:12)],
    "Inter-SemiBold":[13:.custom("Inter-SemiBold",fixedSize:13),17:.custom("Inter-SemiBold",fixedSize:17)],
    "InstrumentSerif-Regular":[38:.custom("InstrumentSerif-Regular",fixedSize:38)]
  ]
  func font(_ face: String,_ size: Double) -> Font { fonts[face]![size]! }
  private var cachedLayout: AppIconAtelierTheme.Layout?
  private var cachedArt=UIImage()
  init() {
    for name in ["InstrumentSerif-Regular","Inter-Regular","Inter-Medium","Inter-SemiBold"] {
      if let url=Self.url(name,"ttf") { CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil) }
    }
  }
  func art(_ L: AppIconAtelierTheme.Layout) -> UIImage {
    if cachedLayout==L { return cachedArt }
    let format=UIGraphicsImageRendererFormat(); format.scale=3; format.opaque=false
    cachedArt=UIGraphicsImageRenderer(size:CGSize(width:L.w,height:L.h),format:format).image { r in
      let c=r.cgContext
      func color(_ h: Int,_ a: Double=1) -> CGColor { UIColor(red:Double((h>>16)&255)/255,green:Double((h>>8)&255)/255,blue:Double(h&255)/255,alpha:a).cgColor }
      func circle(_ x: Double,_ y: Double,_ radius: Double) -> CGPath { CGPath(ellipseIn:CGRect(x:x-radius,y:y-radius,width:radius*2,height:radius*2),transform:nil) }
      func fill(_ p: CGPath,_ h: Int,_ a: Double=1) { c.addPath(p); c.setFillColor(color(h,a)); c.fillPath() }
      func stroke(_ p: CGPath,_ h: Int,_ a: Double,_ width: Double) { c.addPath(p); c.setStrokeColor(color(h,a)); c.setLineWidth(width); c.strokePath() }
      func gradient(_ path: CGPath,_ a: Int,_ b: Int,_ start: CGPoint,_ end: CGPoint) {
        c.saveGState(); c.addPath(path); c.clip()
        let g=CGGradient(colorsSpace:CGColorSpace(name:CGColorSpace.sRGB),colors:[color(a),color(b)] as CFArray,locations:[0,1])!
        c.drawLinearGradient(g,start:start,end:end,options:[.drawsBeforeStartLocation,.drawsAfterEndLocation]); c.restoreGState()
      }
      let from=[0xFFE7A3,0xBFE3FF,0,0xCDEFD9], to=[0xF4C04E,0x5E9EF0,0,0x4FB37E]
      for i in [0,1,3] {
        let x=L.appX[i]-30,y=L.appsY-30
        let box=CGPath(roundedRect:CGRect(x:x,y:y,width:60,height:60),cornerWidth:15,cornerHeight:15,transform:nil)
        c.saveGState(); c.setShadow(offset:CGSize(width:0,height:3),blur:10,color:color(0x3C286E,0.18)); fill(box,from[i]); c.restoreGState()
        gradient(box,from[i],to[i],CGPoint(x:x,y:y),CGPoint(x:x,y:y+60))
        stroke(CGPath(roundedRect:CGRect(x:x+0.5,y:y+0.5,width:59,height:59),cornerWidth:14.5,cornerHeight:14.5,transform:nil),0xffffff,0.45,1)
        c.saveGState(); c.translateBy(x:x,y:y); c.setLineCap(.round); c.setLineJoin(.round); c.setLineWidth(4); c.setStrokeColor(color(0xffffff))
        let p=CGMutablePath()
        if i==0 { for (y,end) in [(22.0,42.0),(30.0,42.0),(38.0,34.0)] { p.move(to:CGPoint(x:18,y:y)); p.addLine(to:CGPoint(x:end,y:y)) } }
        if i==1 { for y in [28.0,37.0] { p.move(to:CGPoint(x:14,y:y)); p.addCurve(to:CGPoint(x:30,y:y),control1:CGPoint(x:20,y:y-6),control2:CGPoint(x:24,y:y-6)); p.addCurve(to:CGPoint(x:46,y:y),control1:CGPoint(x:36,y:y+6),control2:CGPoint(x:40,y:y+6)) } }
        if i==3 { for (x,y) in [(20.0,34.0),(30.0,24.0),(40.0,30.0)] { p.move(to:CGPoint(x:x,y:42)); p.addLine(to:CGPoint(x:x,y:y)) } }
        c.addPath(p); c.strokePath(); c.restoreGState()
      }
      for i in 0..<5 {
        let x=L.chip(Double(i)),y=L.chipsTop+26, r=22.0, g=AppIconAtelierTheme.glasses[i]
        let p=circle(x,y,r)
        c.saveGState(); c.setShadow(offset:CGSize(width:0,height:2),blur:8,color:color(0x28185A,0.14)); fill(p,g.light); c.restoreGState()
        c.saveGState(); c.addPath(p); c.clip()
        let gradient=CGGradient(colorsSpace:CGColorSpace(name:CGColorSpace.sRGB),colors:[color(g.light),color(g.deep)] as CFArray,locations:[0,1])!
        c.drawRadialGradient(gradient,startCenter:CGPoint(x:x-r*0.35,y:y-r*0.4),startRadius:0,endCenter:CGPoint(x:x-r*0.35,y:y-r*0.4),endRadius:r*1.5,options:[.drawsAfterEndLocation]); c.restoreGState()
        // Pre-render the two-point Gaussian highlight, once per layout.
        let e=CGRect(x:x-r*0.55,y:y-r*0.78,width:r*0.8,height:r*0.42)
        c.saveGState(); c.setShadow(offset:CGSize(width:0,height:1000),blur:4,color:color(0xffffff,0.7)); fill(CGPath(ellipseIn:e.offsetBy(dx:0,dy:-1000),transform:nil),0xffffff); c.restoreGState()
        stroke(circle(x,y,r-0.5),0xffffff,0.55,1); stroke(circle(x,y,r+0.25),0x1C1630,0.1,0.5)
      }
    }
    cachedLayout=L
    return cachedArt
  }
}
