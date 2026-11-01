import SwiftUI
import UIKit
import CoreText

enum RamenBowlBuilderTheme {
  static func color(_ hex: Int, _ alpha: Double = 1) -> Color {
    Color(.sRGB, red: Double(hex>>16 & 255)/255, green: Double(hex>>8 & 255)/255,
          blue: Double(hex & 255)/255, opacity: alpha)
  }
  static let register: Void = {
    for name in ["TitanOne-Regular", "Nunito-Black", "Nunito-ExtraBold"] {
      if let url = Bundle.main.url(forResource: "RamenBowlBuilder-\(name)", withExtension: "ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
      }
    }
  }()
  static func font(_ name: String, _ size: Double) -> Font {
    _ = register
    return .custom(name, fixedSize: size)
  }
}

// Static paths are constructed once per geometry, then rasterized into small, predecoded sprites.
final class RamenBowlBuilderArt {
  typealias M = RamenBowlBuilderMotion
  typealias C = RamenBowlBuilderTheme
  struct Mark {
    var path: Path
    var hex: Int
    var alpha = 1.0
    var stroke = 0.0
  }
  struct Sprite {
    var image: Image
    var rect: CGRect
    func draw(_ context: GraphicsContext) { context.draw(image, in: rect) }
  }
  private static var lastSize = CGSize.zero
  private static var lastArt: RamenBowlBuilderArt?
  static func cached(_ size: CGSize) -> RamenBowlBuilderArt {
    if let art = lastArt, lastSize == size { return art }
    let art = RamenBowlBuilderArt(size)
    lastSize = size; lastArt = art
    return art
  }
  let geo: M.Geo
  let toppings: [Sprite]
  let broth: Sprite
  let body: Sprite
  let chopsticks: [Path]
  static func oval(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> Path {
    Path(ellipseIn: CGRect(x: x, y: y, width: w, height: h))
  }
  static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path { oval(x-r,y-r,r*2,r*2) }
  static func line(_ x: Double, _ y: Double, _ xx: Double, _ yy: Double) -> Path {
    Path { $0.move(to: CGPoint(x:x,y:y)); $0.addLine(to: CGPoint(x:xx,y:yy)) }
  }
  static func sprite(_ marks: [Mark], _ rect: CGRect, _ clip: Path? = nil) -> Sprite {
    let format = UIGraphicsImageRendererFormat()
    format.scale = 3; format.preferredRange = .standard
    let image = UIGraphicsImageRenderer(size: rect.size, format: format).image { renderer in
      let c = renderer.cgContext
      c.translateBy(x: -rect.minX, y: -rect.minY)
      if let clip { c.addPath(clip.cgPath); c.clip() }
      c.setLineCap(.round); c.setLineJoin(.miter)
      for m in marks {
        let color = UIColor(red: Double(m.hex>>16 & 255)/255, green: Double(m.hex>>8 & 255)/255,
                            blue: Double(m.hex & 255)/255, alpha: m.alpha).cgColor
        c.addPath(m.path.cgPath)
        if m.stroke > 0 { c.setStrokeColor(color); c.setLineWidth(m.stroke); c.strokePath() }
        else { c.setFillColor(color); c.fillPath() }
      }
    }
    return Sprite(image: Image(uiImage:image), rect:rect)
  }
  init(_ size: CGSize) {
    let g = M.Geo(size); geo = g
    let cx = g.cx, cy = g.cy, s = g.s, u = g.s*0.56
    chopsticks = [Self.line(cx-s*0.1,cy-s*0.7,cx+s*1.1,cy-s*1.15),
      Self.line(cx-s*0.05,cy-s*0.58,cx+s*1.15,cy-s)]
    var top: [Sprite] = []
    for kind in 0..<5 {
      var a: [Mark] = []
      func add(_ p: Path, _ color: Int, _ alpha: Double = 1, _ stroke: Double = 0) {
        a.append(Mark(path:p,hex:color,alpha:alpha,stroke:stroke))
      }
      if kind == 0 {
        add(Self.oval(-u*0.55,-u*0.42,u*1.1,u*0.84),0xffffff)
        add(Self.circle(0,0,u*0.26),0xffaa1f)
        add(Self.circle(-u*0.06,-u*0.06,u*0.08),0xffd66b)
      } else if kind == 1 {
        add(Self.circle(0,0,u*0.45),0xffffff)
        var p = Path()
        for k in 0..<40 {
          let a = Double(k)*0.42, r = u*0.04+Double(k)*u*0.0085
          let point = CGPoint(x:cos(a)*r,y:sin(a)*r)
          if k == 0 { p.move(to:point) } else { p.addLine(to:point) }
        }
        add(p,0xff7a9c,1,u*0.07)
        for k in 0..<12 {
          let a = Double(k)/12*Double.pi*2
          add(Self.circle(cos(a)*u*0.45,sin(a)*u*0.45,u*0.06),0xffffff)
        }
      } else if kind == 2 {
        add(Path(roundedRect:CGRect(x:-u*0.32,y:-u*0.55,width:u*0.64,height:u*1.1),cornerRadius:u*0.05),0x1f3b2e)
        add(Self.line(-u*0.2,-u*0.4,-u*0.2,u*0.4),0x2f5442,1,1.5)
        add(Self.line(u*0.1,-u*0.4,u*0.1,u*0.4),0x2f5442,1,1.5)
      } else if kind == 3 {
        var p = Path()
        p.addArc(center:.zero,radius:u*0.45,startAngle:.degrees(200),endAngle:.degrees(450),clockwise:false)
        add(p,0xff7e4a,1,u*0.3)
        for k in 0..<4 {
          let a = (200+Double(k)*60)*Double.pi/180
          add(Self.line(cos(a)*u*0.3,sin(a)*u*0.3,cos(a)*u*0.6,sin(a)*u*0.6),0xffffff,0.7,2)
        }
      } else {
        add(Self.circle(0,0,u*0.45),0x1f3b2e)
        add(Self.circle(0,0,u*0.36),0xffffff)
        add(Self.circle(0,0,u*0.14),0xff8a4c)
        add(Self.circle(u*0.1,-u*0.1,u*0.07),0x7cc46a)
      }
      top.append(Self.sprite(a,CGRect(x:-u*0.8,y:-u*0.8,width:u*1.6,height:u*1.6)))
    }
    toppings = top
    var b: [Mark] = [Mark(path:Self.oval(cx-s,cy-s*0.3,s*2,s*0.6),hex:0xe8914a),
      Mark(path:Self.oval(cx-s*0.92,cy-s*0.24,s*1.84,s*0.48),hex:0xf6b460)]
    for k in 0..<6 {
      let ny = cy-s*0.12+Double(k%3)*s*0.1
      var p = Path(); p.move(to:CGPoint(x:cx-s*0.7,y:ny))
      for q in 1...8 {
        p.addLine(to:CGPoint(x:cx-s*0.7+Double(q)*s*0.18,y:ny+sin(Double(q)*1.7+Double(k))*5))
      }
      b.append(Mark(path:p,hex:0xffe08a,stroke:4))
    }
    broth = Self.sprite(b,CGRect(x:cx-s*1.02,y:cy-s*0.32,width:s*2.04,height:s*0.64))
    var p = Path(); p.move(to:CGPoint(x:cx-s*1.08,y:cy))
    p.addCurve(to:CGPoint(x:cx+s*1.08,y:cy),control1:CGPoint(x:cx-s,y:cy+s*0.95),control2:CGPoint(x:cx+s,y:cy+s*0.95))
    p.closeSubpath()
    var dots = [Mark(path:p,hex:0xe8443a)]
    for k in 0..<14 {
      let dx = cx-s*0.95+Double(k%7)*s*0.32+(k>6 ? s*0.16:0)
      let dy = cy+s*0.22+(k>6 ? s*0.28:0)
      // Clip each dot once rather than clipping the complete rim and foot.
      let clipped = Self.circle(dx,dy,s*0.07).intersection(p)
      dots.append(Mark(path:clipped,hex:0xffffff))
    }
    dots.append(Mark(path:Self.oval(cx-s*1.1,cy-s*0.08,s*2.2,s*0.16),hex:0xc7342c))
    dots.append(Mark(path:Self.oval(cx-s*0.55,cy+s*0.68,s*1.1,s*0.16),hex:0xb02c25))
    body = Self.sprite(dots,CGRect(x:cx-s*1.12,y:cy-s*0.1,width:s*2.24,height:s*0.96))
  }
  func draw(_ context: GraphicsContext, _ o: M.Frame, _ reduced: Bool) {
    let g = geo, cx = geo.cx, cy = geo.cy, s = geo.s
    let full = o.k.reduce(0,+)/5
    func stroke(_ path: Path, _ color: Int, _ width: Double, _ alpha: Double = 1) {
      context.stroke(path,with:.color(C.color(color,alpha)),style:StrokeStyle(lineWidth:width,lineCap:.round,lineJoin:.miter))
    }
    for k in 0..<3 {
      let ph = o.t*2*Double.pi/M.loop+Double(k)*2.1, x0 = cx+Double(k-1)*s*0.35
      var p = Path(); p.move(to:CGPoint(x:x0,y:cy-s*0.25))
      p.addCurve(to:CGPoint(x:x0+sin(ph+1)*10,y:cy-s*1.05),control1:CGPoint(x:x0+sin(ph)*14,y:cy-s*0.55),control2:CGPoint(x:x0-sin(ph)*14,y:cy-s*0.8))
      stroke(p,0xffffff,7,0.25+0.45*full)
    }
    let lift = reduced ? 0 : sin(o.t*2*Double.pi*2/M.loop)*6
    var sticksContext = context
    sticksContext.translateBy(x:0,y:lift)
    for i in 0..<2 {
      sticksContext.stroke(chopsticks[i],with:.color(C.color(i == 0 ? 0xc2533c:0xd8644a)),
        style:StrokeStyle(lineWidth:7,lineCap:.round,lineJoin:.miter))
    }
    for k in 0..<4 {
      let nx = cx+s*0.05+Double(k)*7
      stroke(Self.line(nx,cy-s*0.62+lift,nx+sin(o.t*8*Double.pi/M.loop+Double(k))*3,cy-s*0.1),0xffd878,5)
    }
    broth.draw(context)
    for i in 0..<5 where o.k[i] > 0.999 && i != o.dragI {
      var c = context; c.translateBy(x:g.slots[i].x,y:g.slots[i].y); c.scaleBy(x:1,y:0.62)
      toppings[i].draw(c)
    }
    body.draw(context)
    for i in 0..<5 {
      let k = o.k[i]
      if k > 0.999 && i != o.dragI { continue }
      let h = g.homes[i]
      let bob = reduced ? 0 : sin(o.t*2*Double.pi*Double(1+i%2)/M.loop+Double(i)*1.4)*8
      var x = h.x, y = h.y+bob
      var rot = reduced ? 0 : sin(o.t*2*Double.pi/M.loop+Double(i))*18
      var sc = 1.0
      if i == o.dragI { x = o.dx; y = o.dy; sc = 1.12 }
      else if o.back[i] > 0 { x = M.mix(o.bx[i],h.x,M.smoother(o.back[i])); y = M.mix(o.by[i],h.y+bob,M.smoother(o.back[i])) }
      if k > 0 && i != o.dragI {
        let sx = o.back[i] < 0 ? o.bx[i]:x, sy = o.back[i] < 0 ? o.by[i]:y
        x = M.mix(sx,g.slots[i].x,k)
        y = M.mix(sy,g.slots[i].y,k)-sin(Double.pi*k)*s*0.6
        rot *= 1-k; sc = M.mix(1,0.9,k)
      }
      var c = context; c.translateBy(x:x,y:y); c.rotate(by:.degrees(rot)); c.scaleBy(x:sc,y:sc)
      toppings[i].draw(c)
      if k > 0.8 && k < 1 {
        let sk = (k-0.8)/0.2
        for d in 0..<6 {
          let a = Double.pi+Double(d)/5*Double.pi
          context.fill(Self.circle(g.slots[i].x+cos(a)*s*0.3*sk,g.slots[i].y+sin(a)*s*0.25*sk,4*(1-sk)),with:.color(C.color(0xf6b460)))
        }
      }
    }
  }
}
