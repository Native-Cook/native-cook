import CoreText
import SwiftUI
import UIKit

final class PerfumeInkBloomResources {
  static let shared=PerfumeInkBloomResources()
  private var cachedLayout: PerfumeInkBloomTheme.Layout?
  private var cachedPage=UIImage()
  private var cachedHeart=Path(), cachedBuy=Path()
  var heartOutline: Path { cachedHeart }
  var buyOutline: Path { cachedBuy }
  lazy var buyLabel: UIImage = MainActor.assumeIsolated {
    let renderer=ImageRenderer(content:Text("Buy now").font(.custom("NunitoExtraLight-SemiBold",fixedSize:19)).foregroundColor(.white).fixedSize())
    renderer.scale=3
    return renderer.uiImage!
  }
  static func font(_ weight: String,_ size: Double) -> UIFont {
    _ = shared
    return UIFont(name:"NunitoExtraLight-\(weight)",size:size)!
  }
  init() {
    for weight in ["Regular","SemiBold","Bold"] {
      if let url=Bundle.main.url(forResource:"PerfumeInkBloom-Nunito-\(weight)",withExtension:"ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil)
      }
    }
  }
  // Static art sampled by pib-notes.ts from the bundled Nunito Bold at 402 x 874.
  // Keeping Skia's actual alpha samples avoids amplifying tiny font raster differences into
  // different particle destinations. Rows stay 33 pt apart; glyphs follow the width scale.
  private lazy var referenceNotes: [SIMD2<Float>] = {
    let url=Bundle.main.url(forResource:"PerfumeInkBloom-note-points",withExtension:"bin")!
    let data=try! Data(contentsOf:url)
    precondition(data.count==6144*8)
    return data.withUnsafeBytes { p in
      (0..<6144).map { i in
        SIMD2<Float>(p.loadUnaligned(fromByteOffset:i*8,as:Float.self),p.loadUnaligned(fromByteOffset:i*8+4,as:Float.self))
      }
    }
  }()
  func notes(_ l: PerfumeInkBloomTheme.Layout) -> [SIMD2<Float>] {
    referenceNotes.map { p in
      let y=Double(p.y), row=floor((y-322)/33+0.5)
      return .init(Float(Double(p.x)*l.sx),Float(l.y(322)+row*33+(y-322-row*33)*l.sx))
    }
  }
  func page(_ l: PerfumeInkBloomTheme.Layout) -> UIImage {
    if cachedLayout==l { return cachedPage }
    cachedLayout=l
    cachedHeart=Path(roundedRect:CGRect(x:31*l.sx+0.6,y:l.y(754)+0.6,width:77*l.sx-1.2,height:76*l.k-1.2),cornerRadius:19.4)
    cachedBuy=Path(roundedRect:CGRect(x:135*l.sx,y:l.y(754),width:235*l.sx,height:76*l.k),cornerRadius:22)
    let format=UIGraphicsImageRendererFormat(); format.scale=3; format.opaque=false
    cachedPage=UIGraphicsImageRenderer(size:.init(width:l.w,height:l.h),format:format).image { r in
      func text(_ string: String,_ x: Double,_ y: Double,_ size: Double,_ weight: String,_ color: UInt,_ width: Double?=nil,_ line: Double?=nil) {
        let face=Self.font(weight,size)
        let paragraph=NSMutableParagraphStyle()
        if let line { paragraph.minimumLineHeight=line; paragraph.maximumLineHeight=line }
        var attrs: [NSAttributedString.Key:Any]=[.font:face,.foregroundColor:Self.uiColor(color),.paragraphStyle:paragraph]
        if let line { attrs[.baselineOffset]=(line-face.lineHeight)/2 }
        (string as NSString).draw(in:CGRect(x:x,y:y-(line == nil ? 0 : 1.0/3),width:width ?? l.w-x,height:120),withAttributes:attrs)
      }
      text(PerfumeInkBloomTheme.name,32*l.sx,l.y(476)-21,30,"SemiBold",0x0B0B0F)
      text("Eau de Parfum",32*l.sx,l.y(513)-14,19.5,"Regular",0x0B0B0F)
      text("Reviews",32*l.sx,l.y(568)-12,16,"SemiBold",0x4B4B55)
      text("Price",288*l.sx,l.y(567)-12,16,"SemiBold",0x4B4B55)
      text("$148",288*l.sx,l.y(603)-15,22,"Bold",0x0B0B0F)
      text(PerfumeInkBloomTheme.body,32*l.sx,l.y(657)-12.75,13.2,"Regular",0x8C8C96,312*l.sx,25.5)
      let points: [(Double,Double)]=[(12,1.8),(15.1,8.3),(22.2,9.2),(17,14.1),(18.3,21.2),(12,17.8),(5.7,21.2),(7,14.1),(1.8,9.2),(8.9,8.3)]
      for i in 0..<5 {
        let p=UIBezierPath()
        for (j,v) in points.enumerated() {
          let pt=CGPoint(x:32*l.sx+Double(i)*28+v.0*21/24,y:l.y(603)-10.5+v.1*21/24)
          if j==0 { p.move(to:pt) } else { p.addLine(to:pt) }
        }
        p.close(); Self.uiColor(i<4 ? 0xFF9C1A : 0xE6E6EC).setFill(); p.fill()
      }
    }
    return cachedPage
  }
  static func uiColor(_ hex: UInt) -> UIColor {
    UIColor(red:Double((hex>>16)&255)/255,green:Double((hex>>8)&255)/255,blue:Double(hex&255)/255,alpha:1)
  }
  static let badge=Path(ellipseIn:.init(x:13.4,y:0.9,width:7.2,height:7.2))
  static let back=Path { p in
    p.move(to:.init(x:9,y:2)); p.addLine(to:.init(x:2,y:9)); p.addLine(to:.init(x:9,y:16))
    p.move(to:.init(x:2.6,y:9)); p.addLine(to:.init(x:20.5,y:9))
  }
  static let bag=Path { p in
    p.move(to:.init(x:3.5,y:7.5)); p.addLine(to:.init(x:16.5,y:7.5)); p.addLine(to:.init(x:16.5,y:21)); p.addLine(to:.init(x:3.5,y:21)); p.closeSubpath()
    p.move(to:.init(x:6.8,y:7.5)); p.addLine(to:.init(x:6.8,y:5.6))
    p.addArc(center:.init(x:10,y:5.6),radius:3.2,startAngle:.degrees(180),endAngle:.degrees(360),clockwise:false)
    p.addLine(to:.init(x:13.2,y:7.5))
  }
  static let heart: Path = {
    let p=UIBezierPath(); p.move(to:.init(x:11,y:18.2))
    p.addCurve(to:.init(x:1.6,y:6.6),controlPoint1:.init(x:4,y:13.4),controlPoint2:.init(x:1.6,y:10.2))
    func arc(_ end: CGPoint) {
      let start=p.currentPoint, dx=start.x-end.x, dy=start.y-end.y
      let radius=max(4.8,hypot(dx,dy)/2)
      let fac=sqrt(max(0,(radius*radius-(dx*dx+dy*dy)/4)/((dx*dx+dy*dy)/4)))
      let center=CGPoint(x:(start.x+end.x)/2+fac*dy/2,y:(start.y+end.y)/2-fac*dx/2)
      p.addArc(withCenter:center,radius:radius,startAngle:atan2(start.y-center.y,start.x-center.x),endAngle:atan2(end.y-center.y,end.x-center.x),clockwise:true)
    }
    arc(.init(x:11,y:4.6)); arc(.init(x:20.4,y:6.6))
    p.addCurve(to:.init(x:11,y:18.2),controlPoint1:.init(x:20.4,y:10.2),controlPoint2:.init(x:18,y:13.4)); p.close()
    return Path(p.cgPath)
  }()
}

struct PerfumeInkBloomArt {
  typealias H=PerfumeInkBloomTheme
  func draw(_ c: GraphicsContext,_ l: H.Layout,_ s: PerfumeInkBloomMotion.Frame,_ clock: PerfumeInkBloomClock) {
    c.draw(Image(uiImage:PerfumeInkBloomResources.shared.page(l)),in:CGRect(x:0,y:0,width:l.w,height:l.h))
    func control(_ id: Int,_ box: CGRect,_ draw: (GraphicsContext)->Void) {
      var d=c
      let p=clock.controlPress(id)
      if clock.reduced { d.opacity=1-0.28*p }
      else {
        let scale=1-(id==3 ? 0.03 : 0.04)*p
        d.translateBy(x:box.midX,y:box.midY); d.scaleBy(x:scale,y:scale); d.translateBy(x:-box.midX,y:-box.midY)
      }
      draw(d)
    }
    control(0,.init(x:39*l.sx-22,y:l.y(83)-22,width:44,height:44)) { d in
      var d=d; d.translateBy(x:39*l.sx-11,y:l.y(83)-9)
      d.stroke(PerfumeInkBloomResources.back,with:.color(H.color(0x0B0B0F)),style:.init(lineWidth:1.9,lineCap:.round,lineJoin:.round))
    }
    control(1,.init(x:358*l.sx-22,y:l.y(85)-22,width:44,height:44)) { d in
      var d=d; d.translateBy(x:358*l.sx-11,y:l.y(85)-12)
      d.stroke(PerfumeInkBloomResources.bag,with:.color(H.color(0x0B0B0F)),style:.init(lineWidth:1.7,lineJoin:.round))
      d.fill(PerfumeInkBloomResources.badge,with:.color(H.color(0x0A5CFF)))
    }
    control(2,.init(x:31*l.sx,y:l.y(754),width:77*l.sx,height:76*l.k)) { d in
      let box=CGRect(x:31*l.sx,y:l.y(754),width:77*l.sx,height:76*l.k)
      d.stroke(PerfumeInkBloomResources.shared.heartOutline,with:.color(H.color(0x8F96BC)),lineWidth:1.2)
      var d=d; d.translateBy(x:box.midX-11,y:box.midY-10)
      d.stroke(PerfumeInkBloomResources.heart,with:.color(H.color(0x8F96BC)),style:.init(lineWidth:1.6,lineJoin:.round))
    }
    control(3,.init(x:135*l.sx,y:l.y(754),width:235*l.sx,height:76*l.k)) { d in
      let box=CGRect(x:135*l.sx,y:l.y(754),width:235*l.sx,height:76*l.k)
      d.fill(PerfumeInkBloomResources.shared.buyOutline,with:.color(H.color(0x0A5CFF)))
      let label=PerfumeInkBloomResources.shared.buyLabel
      d.draw(Image(uiImage:label),in:CGRect(x:box.midX-label.size.width/2,y:box.midY-label.size.height/2,width:label.size.width,height:label.size.height))
    }
    if s.finger>0 {
      let x=s.fingerAt==0 ? l.capX : l.cx+90*s.fingerU*l.sx
      let y=s.fingerAt==0 ? l.capY+6*(clock.autoPlaying ? s.press : clock.frozenFingerPress) : l.cy+20*l.sx
      let r=22*(1-0.12*s.press2), p=Path(ellipseIn:.init(x:x-r,y:y-r,width:r*2,height:r*2))
      c.fill(p,with:.color(H.color(0x141E3C,0.08*s.finger)))
      c.stroke(p,with:.color(H.color(0x141E3C,0.3*s.finger)),lineWidth:1.5)
    }
  }
}
