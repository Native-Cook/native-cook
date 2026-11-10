import UIKit

// Current RN uses system fonts and no sound or bundled artwork. TextKit bitmaps are cached.
final class PinDropLocationResources {
  static let shared = PinDropLocationResources()
  static let body = "Please enable location access so\nwe could provide you accurate results of\nnearest restaurants and bars"
  static let names = ["Corner Coffee","Juniper Books","Night Market"]
  static let positions = [CGPoint(x: -0.58,y: 0.3),CGPoint(x: 0.6,y: -0.25),CGPoint(x: 0.35,y: 0.72)]
  static let sides = [-1.0,1.0,1.0]
  private var cache: [String: UIImage] = [:]
  private var fonts: [String: UIFont] = [:]
  static func color(_ hex: Int,_ alpha: Double = 1) -> UIColor {
    UIColor(red: Double((hex>>16)&255)/255,green: Double((hex>>8)&255)/255,blue: Double(hex&255)/255,alpha: alpha)
  }
  func font(_ size: Double,_ weight: UIFont.Weight) -> UIFont {
    let key = "\(size)/\(weight.rawValue)"
    if let f = fonts[key] { return f }
    let f = UIFont.systemFont(ofSize: size,weight: weight); fonts[key] = f; return f
  }
  func text(_ text: String,_ size: Double,_ weight: UIFont.Weight,_ hex: Int,_ width: Double,_ height: Double,_ scale: Double,_ line: Double? = nil,_ tracking: Double = 0) -> UIImage {
    let key = "\(text)/\(width)/\(height)/\(scale)"
    if let image = cache[key] { return image }
    let face = font(size,weight)
    let p = NSMutableParagraphStyle(); p.alignment = line == nil ? .left : .center
    var attrs: [NSAttributedString.Key:Any] = [.font:face,.foregroundColor:Self.color(hex),.paragraphStyle:p]
    if tracking != 0 { attrs[.kern] = tracking }
    if let line {
      p.minimumLineHeight = line; p.maximumLineHeight = line
      if line >= face.lineHeight { attrs[.baselineOffset] = (line-face.lineHeight)/2 }
    }
    let storage = NSTextStorage(string:text,attributes:attrs), manager = NSLayoutManager()
    let container = NSTextContainer(size:CGSize(width:width,height:height)); container.lineFragmentPadding = 0
    manager.addTextContainer(container); storage.addLayoutManager(manager); manager.ensureLayout(for:container)
    let used = manager.usedRect(for:container)
    var origin = CGPoint.zero
    if line == nil {
      let measured = CGSize(width:ceil(used.width*scale)/scale,height:ceil(used.height*scale)/scale)
      container.size = measured; manager.ensureLayout(for:container)
      origin = CGPoint(x:floor((width-measured.width)/2*scale)/scale,y:floor((height-measured.height)/2*scale)/scale)
    }
    let format = UIGraphicsImageRendererFormat(); format.scale = scale; format.preferredRange = .standard
    let image = UIGraphicsImageRenderer(size:CGSize(width:width,height:height),format:format).image { _ in
      let range=manager.glyphRange(for:container)
      manager.drawBackground(forGlyphRange:range,at:origin); manager.drawGlyphs(forGlyphRange:range,at:origin)
    }
    cache[key]=image; return image
  }
  func background(_ l: PinDropLocationLayout,_ scale: Double) -> UIImage {
    let key = "bg/\(l)/\(scale)"; if let image = cache[key] { return image }
    let format=UIGraphicsImageRendererFormat(); format.scale=scale; format.opaque=true; format.preferredRange = .standard
    let image=UIGraphicsImageRenderer(size:CGSize(width:l.w,height:l.h),format:format).image { r in
      let c=r.cgContext
      Self.color(0x0A071A).setFill(); c.fill(CGRect(x:0,y:0,width:l.w,height:l.h))
      let space=CGColorSpace(name:CGColorSpace.sRGB)!
      let colors=[Self.color(0x17112F).cgColor,Self.color(0x0A071A).cgColor] as CFArray
      let gradient=CGGradient(colorsSpace:space,colors:colors,locations:[0,1])!
      let centre=CGPoint(x:l.cx,y:l.cy+40)
      c.drawRadialGradient(gradient,startCenter:centre,startRadius:0,endCenter:centre,endRadius:l.w*0.9,options:[])
    }
    cache[key]=image; return image
  }
  func pill(_ i: Int,_ scale: Double) -> UIImage {
    let key="pill\(i)/\(scale)"; if let image=cache[key] { return image }
    let face=font(12,.semibold)
    let w=ceil((Self.names[i] as NSString).size(withAttributes:[.font:face]).width*scale)/scale+20+2/scale
    let format=UIGraphicsImageRendererFormat(); format.scale=scale; format.preferredRange = .standard
    let image=UIGraphicsImageRenderer(size:CGSize(width:w,height:26),format:format).image { r in
      let c=r.cgContext, rect=CGRect(x:0,y:0,width:w,height:26)
      c.setFillColor(Self.color(0x16132A,0.88).cgColor); c.addPath(CGPath(roundedRect:rect,cornerWidth:13,cornerHeight:13,transform:nil)); c.fillPath()
      let border=1/scale
      c.setStrokeColor(Self.color(0xC9C3EA,0.35).cgColor); c.setLineWidth(border)
      c.addPath(CGPath(roundedRect:rect.insetBy(dx:border/2,dy:border/2),cornerWidth:13-border/2,cornerHeight:13-border/2,transform:nil)); c.strokePath()
      text(Self.names[i],12,.semibold,0xECE9F7,w,26,scale).draw(at:.zero)
    }
    cache[key]=image; return image
  }
}
