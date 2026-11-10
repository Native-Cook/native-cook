import SwiftUI
import UIKit

final class SapphireWatchCrownArt {
  static let shared = SapphireWatchCrownArt()
  private var layout: SapphireWatchCrownLayout?
  private var page = UIImage()
  private var controls: [UIImage] = []
  private var fonts: [String: UIFont] = [:]
  let back: UIBezierPath
  let cart: UIBezierPath
  let arrows: Path
  static let metalHex = [0x2E2E31,0xB9977D,0xE8B33D,0xF4F4F2]
  static let metalNames = ["Black","Rose gold","Yellow gold","White gold"]
  static let sizeNames = ["11.5","12.3","13.6","14.79"]
  private init() {
    _ = SapphireWatchCrownResources.shared
    for (weight,size) in [("SemiBold",17.0),("Regular",12),("SemiBold",19),("Regular",11),("Medium",17),("Regular",13),("Regular",15),("SemiBold",16),("Regular",11.5),("SemiBold",13)] {
      fonts["\(weight)-\(size)"] = UIFont(name:"Poppins-\(weight)",size:size) ?? .systemFont(ofSize:size)
    }
    back=UIBezierPath(); back.move(to:.init(x:8.5,y:1.5)); back.addLine(to:.init(x:1.5,y:9)); back.addLine(to:.init(x:8.5,y:16.5))
    cart=UIBezierPath(); cart.move(to:.init(x:1.5,y:3)); cart.addLine(to:.init(x:5,y:3)); cart.addLine(to:.init(x:7.5,y:15)); cart.addLine(to:.init(x:19,y:15)); cart.addLine(to:.init(x:21.5,y:7)); cart.addLine(to:.init(x:6.2,y:7))
    var a=Path()
    let points: [[CGPoint]] = [[.init(x:109,y:10),.init(x:101,y:14),.init(x:109,y:18)], [.init(x:347,y:109),.init(x:351,y:102),.init(x:355,y:109)]]
    for i in 0..<2 {
      let center=CGPoint(x:i == 0 ? 109:351,y:i == 0 ? 10:109)
      let rad=(i == 0 ? -20.0 : -15.0)*Double.pi/180
      var p=Path(); p.addLines(points[i])
      let tr=CGAffineTransform(translationX:center.x,y:center.y).rotated(by:rad).translatedBy(x:-center.x,y:-center.y)
      a.addPath(p,transform:tr)
    }
    arrows=a
  }
  static func color(_ hex: Int,_ alpha: Double = 1) -> UIColor { UIColor(red:Double((hex>>16)&255)/255,green:Double((hex>>8)&255)/255,blue:Double(hex&255)/255,alpha:alpha) }
  private func font(_ weight: String,_ size: Double) -> UIFont { fonts["\(weight)-\(size)"]! }
  private func text(_ s: String,_ x: Double,_ y: Double,_ weight: String,_ size: Double,_ alpha: Double = 1,_ right: Bool = false) {
    let attrs: [NSAttributedString.Key:Any] = [.font:font(weight,size),.foregroundColor:UIColor.white.withAlphaComponent(alpha)]
    let width=(s as NSString).size(withAttributes:attrs).width
    (s as NSString).draw(at:CGPoint(x:right ? x-width:x,y:y),withAttributes:attrs)
  }
  private func centered(_ s: String,_ rect: CGRect,_ weight: String,_ size: Double,_ hex: Int = 0xffffff) {
    let attrs: [NSAttributedString.Key:Any] = [.font:font(weight,size),.foregroundColor:Self.color(hex)]
    let bounds=(s as NSString).size(withAttributes:attrs)
    (s as NSString).draw(at:.init(x:rect.midX-bounds.width/2,y:rect.midY-bounds.height/2),withAttributes:attrs)
  }
  func prepare(_ l: SapphireWatchCrownLayout) {
    if layout == l { return }; layout=l
    let format=UIGraphicsImageRendererFormat(); format.scale=UIScreen.main.scale; format.opaque=false; format.preferredRange = .standard
    page=UIGraphicsImageRenderer(size:CGSize(width:l.w,height:l.h),format:format).image { renderer in
      let c=renderer.cgContext
      Self.color(0x050505).setFill(); c.fill(CGRect(x:0,y:0,width:l.w,height:l.h))
      let center=CGPoint(x:l.cx,y:l.y(355))
      for (radius,hex) in [(175.0,0x1A1A1C),(146.0,0x111113)] {
        c.setStrokeColor(Self.color(hex).cgColor); c.setLineWidth(1)
        c.strokeEllipse(in:CGRect(x:center.x-radius*l.sx,y:center.y-radius*l.sx,width:2*radius*l.sx,height:2*radius*l.sx))
      }
      c.saveGState()
      let r=128*l.sx
      c.addEllipse(in:CGRect(x:center.x-r,y:center.y-r,width:2*r,height:2*r)); c.clip()
      let gradient=CGGradient(colorsSpace:CGColorSpace(name:CGColorSpace.sRGB),colors:[Self.color(0x2A2A2E).cgColor,Self.color(0x141416).cgColor] as CFArray,locations:[0,1])!
      c.drawRadialGradient(gradient,startCenter:CGPoint(x:center.x-30,y:center.y-40),startRadius:0,endCenter:CGPoint(x:center.x-30,y:center.y-40),endRadius:r*1.3,options:[.drawsAfterEndLocation]); c.restoreGState()
      text("Store Online",118*l.sx,l.y(77)-13,"SemiBold",17)
      text("Easy Shopping",118*l.sx,l.y(112)-9,"Regular",12,0.45)
      text("Maison Aurel",26*l.sx,l.y(580)-15,"SemiBold",19)
      let nameWidth=ceil(("Maison Aurel" as NSString).size(withAttributes:[.font:font("SemiBold",19)]).width*UIScreen.main.scale)/UIScreen.main.scale
      let baseline=font("SemiBold",19).ascender-font("Regular",11).ascender
      text("(Swiss Made)",26*l.sx+nameWidth+8,l.y(580)-15+baseline,"Regular",11,0.32)
      text("7.9/10",376*l.sx,l.y(580)-14,"Medium",17,1,true)
      Self.color(0xffffff,0.1).setFill()
      c.fill(CGRect(x:26,y:l.y(612),width:l.w-52,height:1/UIScreen.main.scale))
      c.fill(CGRect(x:213*l.sx,y:l.y(612)+8,width:1/UIScreen.main.scale,height:l.y(720)-l.y(612)-8))
      text("Color",26*l.sx,l.y(640)-10,"Regular",13,0.45)
      text("Sizing",228*l.sx,l.y(640)-10,"Regular",13,0.45)
    }
    controls=(0..<4).map { i in
      let size=CGSize(width:i >= 2 ? (i == 2 ? 350:201)*l.sx:68,height:(i >= 2 ? 72:68)*l.k)
      return UIGraphicsImageRenderer(size:size,format:format).image { renderer in
        let c=renderer.cgContext
        Self.color(i == 3 ? 0xFFC56E:0x1C1C1E).setFill()
        let radius = i >= 2 ? 30.0:16.0
        // RN borderRadius is circular; UIKit's rounded UIBezierPath uses corner smoothing.
        c.addPath(CGPath(roundedRect:CGRect(origin:.zero,size:size),cornerWidth:radius,cornerHeight:radius,transform:nil)); c.fillPath()
        if i == 2 {
          let priceW=149*l.sx
          centered("$ 450.23",CGRect(x:0,y:0,width:priceW,height:size.height),"Regular",15)
        } else if i == 3 {
          centered("Buy Online",CGRect(origin:.zero,size:size),"SemiBold",16,0x111111)
        } else {
          c.translateBy(x:(size.width-(i == 0 ? 10:26))/2,y:(size.height-(i == 0 ? 18:24))/2)
          UIColor.white.setStroke(); let p=i == 0 ? back:cart; p.lineWidth=i == 0 ? 1.8:1.7; p.lineCapStyle = .round; p.lineJoinStyle = .round; p.stroke()
          if i == 1 {
            UIColor.white.setFill()
            c.fillEllipse(in:CGRect(x:7.4,y:17.9,width:3.2,height:3.2)); c.fillEllipse(in:CGRect(x:15.9,y:17.9,width:3.2,height:3.2))
            Self.color(0xE6B76B).setFill(); c.fillEllipse(in:CGRect(x:17.2,y:0.2,width:7.6,height:7.6))
          }
        }
      }
    }
  }
  func background(_ l: SapphireWatchCrownLayout) -> Image { prepare(l); return Image(uiImage:page) }
  func control(_ i: Int,_ l: SapphireWatchCrownLayout) -> Image { prepare(l); return Image(uiImage:controls[i]) }
}
