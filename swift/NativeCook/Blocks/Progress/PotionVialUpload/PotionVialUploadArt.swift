import CoreGraphics
import CoreText
import UIKit
import SwiftUI

enum PotionVialUploadArt {
  typealias M = PotionVialUploadMotion
  typealias C = PotionVialUploadTheme.C
  typealias V = PotionVialUploadTheme.V
  typealias F = PotionVialUploadFrame
  typealias G = PotionVialUploadGeo
  static func color(_ hex: String, _ alpha: Double = 1) -> CGColor {
    let v = Int(hex.dropFirst(), radix:16) ?? 0
    return CGColor(colorSpace: CGColorSpace(name:CGColorSpace.sRGB)!,components:[Double((v>>16)&255)/255,Double((v>>8)&255)/255,Double(v&255)/255,alpha])!
  }
  // Quartz's non-antialiased rasterizer lights every touched cell. Skia tests the cell centre.
  // Rasterize these small hard-edged paths explicitly; retain masks only for static geometry.
  private struct MaskKey: Hashable {
    let path: ObjectIdentifier
    let width: Double
    let transform: [Double]
    let w, h: Int
  }
  private struct Mask { let owner: CGPath; let pixels: [Int] }
  private static var masks: [MaskKey: Mask] = [:]
  private static var pixelClip: CGPath?
  private static var colors: [String: [Double]] = [:]
  private static func raster(_ c: CGContext, _ path: CGPath, _ col: String, _ alpha: Double, _ width: Double = 0, _ cached: Bool = false) {
    guard alpha > 0, let data = c.data else { return }
    let t = c.ctm, w = c.width, h = c.height
    let coverage = width > 0 ? min(1,width * sqrt(abs(t.a*t.d-t.b*t.c))) : 1
    let key = MaskKey(path:ObjectIdentifier(path),width:width,transform:[t.a,t.b,t.c,t.d,t.tx,t.ty],w:w,h:h)
    let keep = pixelClip == nil && (cached || path === outer || path === inner || stopperPaths.contains { $0 === path })
    let pixels: [Int]
    if keep, let mask = masks[key] { pixels = mask.pixels }
    else {
      var transform = t
      let shape: CGPath
      if coverage < 1 {
        // Skia draws non-antialiased subpixel strokes as device-space hairlines.
        // A centre test against the narrower geometric stroke can miss an entire edge.
        guard let centre = path.copy(using:&transform) else { return }
        let hairline = centre.copy(strokingWithWidth:1,lineCap:.round,lineJoin:.round,miterLimit:10)
        var tie = CGAffineTransform(translationX:-0.000001,y:0.000001)
        guard let adjusted = hairline.copy(using:&tie) else { return }
        shape = adjusted
      } else {
        let source = width > 0 ? path.copy(strokingWithWidth:width,lineCap:.round,lineJoin:.round,miterLimit:10) : path
        guard let transformed = source.copy(using:&transform) else { return }
        shape = transformed
      }
      let bounds = shape.boundingBoxOfPath
      guard !bounds.isNull, bounds.minX.isFinite, bounds.minY.isFinite, bounds.maxX.isFinite, bounds.maxY.isFinite else { return }
      let x0 = max(0,Int(floor(bounds.minX))), x1 = min(w,Int(ceil(bounds.maxX)))
      let y0 = max(0,h-Int(ceil(bounds.maxY))), y1 = min(h,h-Int(floor(bounds.minY)))
      var indices: [Int] = []
      if x1 > x0 && y1 > y0 {
        for y in y0..<y1 { for x in x0..<x1 {
          let point = CGPoint(x:Double(x)+0.5,y:Double(h-y)-0.5)
          if shape.contains(point,using:.winding) && (pixelClip?.contains(point,using:.winding) ?? true) {
            indices.append(y*c.bytesPerRow+x*4)
          }
        } }
      }
      pixels = indices
      if keep {
        if masks.count >= 256 { masks.removeAll(keepingCapacity:true) }
        masks[key] = Mask(owner:path,pixels:indices)
      }
    }
    let rgb: [Double]
    if let saved = colors[col] { rgb = saved }
    else {
      let v = Int(col.dropFirst(),radix:16) ?? 0
      rgb = [Double((v>>16)&255),Double((v>>8)&255),Double(v&255)]
      colors[col] = rgb
    }
    // Skia modulates a hairline's byte alpha by its original device-space width.
    let a = coverage < 1 ? Double((Int((min(1,alpha)*255).rounded()) * Int(coverage*256)) >> 8)/255 : min(1,alpha)
    let inverse = 1-a, bytes = data.assumingMemoryBound(to:UInt8.self)
    if a == 1 {
      let value = UInt32(littleEndian:UInt32(rgb[0]) | UInt32(rgb[1])<<8 | UInt32(rgb[2])<<16 | 0xff000000)
      let words = data.assumingMemoryBound(to:UInt32.self)
      for offset in pixels { words[offset/4] = value }
      return
    }
    for offset in pixels {
      for channel in 0..<3 { bytes[offset+channel] = UInt8(min(255,(rgb[channel]*a+Double(bytes[offset+channel])*inverse).rounded())) }
      bytes[offset+3] = UInt8(min(255,(255*a+Double(bytes[offset+3])*inverse).rounded()))
    }
  }
  static func rect(_ c: CGContext, _ x: Double, _ y: Double, _ w: Double, _ h: Double, _ col: String, _ a: Double = 1) {
    raster(c,CGPath(rect:CGRect(x:x,y:y,width:w,height:h),transform:nil),col,a)
  }
  static func oval(_ c: CGContext, _ x: Double, _ y: Double, _ w: Double, _ h: Double, _ col: String, _ a: Double = 1, _ stroke: Double = 0) {
    raster(c,CGPath(ellipseIn:CGRect(x:x,y:y,width:w,height:h),transform:nil),col,a,stroke)
  }
  static func circle(_ c: CGContext, _ x: Double, _ y: Double, _ r: Double, _ col: String, _ a: Double = 1, _ stroke: Double = 0) { oval(c,x-r,y-r,r*2,r*2,col,a,stroke) }
  static func path(_ pts: [(Double,Double)], _ closed: Bool = false) -> CGPath {
    let p = CGMutablePath(); if let f = pts.first { p.move(to: .init(x:f.0,y:f.1)) }
    for q in pts.dropFirst() { p.addLine(to:.init(x:q.0,y:q.1)) }; if closed { p.closeSubpath() }; return p
  }
  static func fill(_ c: CGContext, _ p: CGPath, _ col: String, _ a: Double = 1, _ cached: Bool = false) { raster(c,p,col,a,0,cached) }
  static func stroke(_ c: CGContext, _ p: CGPath, _ col: String, _ width: Double, _ a: Double = 1, _ cached: Bool = false) {
    raster(c,p,col,a,width,cached)
  }
  static func line(_ c: CGContext, _ x: Double, _ y: Double, _ x1: Double, _ y1: Double, _ col: String, _ width: Double, _ a: Double = 1) { stroke(c,path([(x,y),(x1,y1)]),col,width,a) }
  static func outline(_ inset: Double) -> CGPath {
    let half = 23-inset, nh = 11-inset, x0 = 22+inset, p = CGMutablePath()
    p.move(to:.init(x:282,y:-half)); p.addLine(to:.init(x:x0+half,y:-half))
    p.addArc(center:.init(x:x0+half,y:0),radius:half,startAngle:-.pi/2,endAngle:-.pi*1.5,clockwise:true)
    p.addLine(to:.init(x:282,y:half))
    p.addCurve(to:.init(x:302,y:nh),control1:.init(x:295,y:half),control2:.init(x:293,y:nh))
    p.addLine(to:.init(x:311+(inset>0 ? 2:0),y:nh)); p.addLine(to:.init(x:311+(inset>0 ? 2:0),y:-nh)); p.addLine(to:.init(x:302,y:-nh))
    p.addCurve(to:.init(x:282,y:-half),control1:.init(x:293,y:-nh),control2:.init(x:295,y:-half)); p.closeSubpath(); return p
  }
  static let outer = outline(0), inner = outline(2.6)
  static let back = path([(26,17),(20,23),(26,29)])
  static let hourglass = path([(-14.5,-3),(-6.5,-3),(-10.5,0),(-6.5,3),(-14.5,3),(-10.5,0)],true)
  static let stopperPaths: [CGPath] = [(V.band1,2.2),(V.disc,3.4),(V.band2,2.2)].map { b,radius in
    CGPath(roundedRect:CGRect(x:b[0],y:-b[2],width:b[1]-b[0],height:b[2]*2),cornerWidth:radius,cornerHeight:radius,transform:nil)
  }
  static let dashes = [(48.0,120.0),(134,196),(210,228),(242,252),(264,270)]
  static let cubeFaces: [[Double]] = [
    [0,0,1,-1,-1,1,1,-1,1,1,1,1,-1,1,1], [0,0,-1,1,-1,-1,-1,-1,-1,-1,1,-1,1,1,-1],
    [1,0,0,1,-1,1,1,-1,-1,1,1,-1,1,1,1], [-1,0,0,-1,-1,-1,-1,-1,1,-1,1,1,-1,1,-1],
    [0,-1,0,-1,-1,-1,1,-1,-1,1,-1,1,-1,-1,1], [0,1,0,-1,1,1,1,1,1,1,1,-1,-1,1,-1]]
  static func local(_ c: CGContext, _ g: G) { c.translateBy(x:g.ox,y:g.cy); c.scaleBy(x:g.s,y:g.s) }
  static func stars(_ c: CGContext, _ g: G, _ f: F) {
    for j in 0..<46 {
      let i = Double(j), x = M.hash(i*3.1)*g.w, y = g.top+M.hash(i*7.7+1)*(g.h-g.top-g.bottom)
      let r = M.hash(i*1.3)>0.8 ? 1.1:0.7; rect(c,x,y,r,r,C.star,0.22)
    }
    for j in 0..<14 {
      let i = Double(j), a = M.hash(i*5.3+2)*Double.pi*2, rr = 120+M.hash(i*2.9)*130
      let x = g.ox+(190+cos(a)*rr*1.15)*g.s, y = g.cy+sin(a)*rr*0.62*g.s
      let tw = pow(max(0,sin(2 * .pi*3*f.t/12+M.hash(i)*6.28)),6), al = tw*(0.25+0.75*f.glow)
      if al < 0.03 { continue }; let arm = M.hash(i*9.1)>0.55 ? 4.5:2.6, col = M.hash(i*4.4)>0.45 ? C.star:C.starGreen
      rect(c,x-arm,y-0.7,arm*2,1.4,col,al); rect(c,x-0.7,y-arm,1.4,arm*2,col,al)
    }
  }
  // Gaussian mask sigma is preserved (Skia's 9, 24 and 11 are standard deviations).
  static func glow(_ c: CGContext, _ p: CGPath, _ sigma: Double, _ alpha: Double) {
    // Core Image applies a Gaussian to an alpha-only path; cached by the resource owner.
    if let image = PotionVialUploadResources.shared.glows[sigma] {
      c.saveGState(); c.setAlpha(alpha); c.draw(image,in:CGRect(x:-100,y:-120,width:540,height:240)); c.restoreGState()
    }
  }
  static func softFront(_ c: CGContext, _ g: G, _ f: F) {
    c.saveGState(); local(c,g)
    if f.glow >= 0.01 { glow(c,outer,9,0.95*f.glow); glow(c,outer,24,0.32*f.glow) }
    fill(c,inner,"#c4bed2",0.05)
    if let image = PotionVialUploadResources.shared.halo {
      c.setAlpha(0.3+0.25*f.glow); c.draw(image,in:CGRect(x:f.gemX-62.4,y:f.gemY-62.4,width:124.8,height:124.8))
    }
    c.restoreGState()
  }
  static func liquidDetails(_ c: CGContext, _ f: F) {
    var transform = c.ctm
    let previousClip = pixelClip
    pixelClip = inner.copy(using:&transform)
    defer { pixelClip = previousClip }
    c.saveGState(); c.addPath(inner); c.clip()
    if f.cloud >= 0 && f.cloudA > 0.01 {
      let k = 1-pow(1-f.cloud,2), hx = M.mix(305,30.6,k), len = M.mix(24,230,min(1,f.cloud*1.4))
      for j in 0..<40 {
        let i = Double(j), u = i/39, x = hx+u*len; if x > 313 { continue }
        let wob = sin(u*9+f.t*14+i)*3.5*u, r = M.mix(8,5,u)+sin(i*1.7)*1.4
        let al = (u<0.15 ? 0.95:0.75*(1-u*0.75))*f.cloudA
        let col = j<2 ? "#f0ffe0":j<7 ? "#a8f870":u<0.5 ? C.liquidDeep:"#3f8f2c"
        circle(c,x,wob+(M.hash(i)-0.5)*5,r,col,al)
      }
    }
    if f.liquidOn > 0 && f.gone < 1 {
      for j in 0..<9 {
        let i = Double(j), raw = f.t*(0.35+M.hash(i)*0.3)+M.hash(i*2.2), life = raw-floor(raw)
        let x = 34.6+M.hash(i*4.7)*(f.front-42.6)+life*10, y = 15.4-life*30.8*0.85
        if x>f.front-6 { continue }; circle(c,x,y,1.3+M.hash(i*8.8)*1.2,"#eaffd0",0.6*(1-f.gone),1.1)
      }
    }
    c.restoreGState()
  }
  static func glass(_ c: CGContext, _ f: F) {
    stroke(c,outer,C.glass,2.2); stroke(c,inner,C.glassDark,1.3,0.9)
    for d in dashes { let a = max(d.0,f.liquidOn>0 ? f.front+8:0); if d.1>a { line(c,a,-17.4,d.1,-17.4,C.glare,1.4,0.5) } }
    line(c,284,-16.5,292,-14,C.glare,1.3,0.4)
  }
  static func chain(_ c: CGContext, _ f: F) {
    let links = M.chainLinks(315.5,14,f.gemX+sin(f.gemRot)*16,f.gemY-cos(f.gemRot)*16,64)
    for (i,l) in links.enumerated() {
      c.saveGState(); c.translateBy(x:l.x,y:l.y); c.rotate(by:l.a)
      if i%2==0 { oval(c,-3,-2.1,6,4.2,"#5a2c0e",1,2.6); oval(c,-3,-2.1,6,4.2,C.brass,1,1.5); rect(c,-1.2,-2.6,1.6,1,C.brassHi,0.9) }
      else { line(c,-2.6,0,2.6,0,"#5a2c0e",2.8); line(c,-2.4,0,2.4,0,C.brass,1.5) }; c.restoreGState()
    }
  }
  static func stopper(_ c: CGContext) {
    for (index,b) in [V.band1,V.disc,V.band2].enumerated() {
      let p = stopperPaths[index], steel = index == 1
      // A path clip discards Quartz's edge coverage. Mask the gradient with an
      // antialiased fill instead, matching Skia's antialiased rounded rectangles.
      c.saveGState(); c.setShouldAntialias(true); c.clip(to:p.boundingBoxOfPath.insetBy(dx:-2,dy:-2))
      c.beginTransparencyLayer(auxiliaryInfo:nil)
      c.setFillColor(CGColor(gray:1,alpha:1)); c.addPath(p); c.fillPath()
      c.setBlendMode(.sourceIn)
      c.drawLinearGradient(PotionVialUploadResources.shared.bands[steel ? 1:0],start:.init(x:0,y:-b[2]),end:.init(x:0,y:b[2]),options:[])
      c.endTransparencyLayer()
      c.restoreGState(); stroke(c,p,"#1a1014",1.1,0.55)
    }
  }
  static func gem(_ c: CGContext, _ f: F) {
    let cy = cos(0.68), sy = sin(0.68), cp = cos(-0.52), sp = sin(-0.52), cr = cos(f.gemVis), sr = sin(f.gemVis)
    func proj(_ px: Double,_ py: Double,_ pz: Double) -> (x:Double,y:Double,z:Double) {
      let x1 = px*cy+pz*sy, z1 = -px*sy+pz*cy, y2 = py*cp-z1*sp, z2 = py*sp+z1*cp
      return (x1*cr-y2*sr,x1*sr+y2*cr,z2)
    }
    c.saveGState(); c.translateBy(x:f.gemX,y:f.gemY); var right = (x:-999.0,y:0.0)
    for face in cubeFaces {
      let n = proj(face[0],face[1],face[2]); if n.z<=0.02 { continue }
      let pts = (0..<4).map { k in proj(face[3+k*3]*16.5,face[4+k*3]*16.5,face[5+k*3]*16.5) }
      for q in pts where q.x>right.x { right = (q.x,q.y) }
      let lit = M.clamp01(0.35+0.75*(n.x * -0.45+n.y * -0.75+n.z*0.48)), p = path(pts.map{($0.x,$0.y)},true)
      fill(c,p,lit>0.78 ? C.gemLight:lit>0.5 ? C.gem:C.gemDark)
      let mx = pts.reduce(0){$0+$1.x}/4, my = pts.reduce(0){$0+$1.y}/4
      fill(c,path(pts.map{(M.mix(mx,$0.x,0.52)+1.2,M.mix(my,$0.y,0.52)+1.4)},true),lit>0.78 ? C.gem:C.gemDeep,0.75)
      if lit>0.7 { line(c,pts[3].x,pts[3].y,pts[2].x,pts[2].y,C.gemHi,1.4,0.9) }; stroke(c,p,"#0b3a1a",1,0.55)
    }
    let tw = 0.55+0.45*sin(2 * .pi*4*f.t/12), arm = 3+2.5*tw
    rect(c,right.x-arm,right.y-0.7,arm*2,1.4,"#ffffff",0.95); rect(c,right.x-0.7,right.y-arm,1.4,arm*2,"#ffffff",0.95); c.restoreGState()
  }
  static func spider(_ c: CGContext, _ f: F) {
    c.saveGState(); c.translateBy(x:f.spX,y:f.spY-f.spHop*5); c.scaleBy(x:1.42+f.spHop*0.12,y:1.42+f.spHop*0.12); c.rotate(by:f.spHead)
    let rest = [(13.0,15.0),(5,19),(-5,19),(-14,15)]
    for side in [-1.0,1] { for j in 0..<4 {
      let grp = (j+(side>0 ? 1:0))%2, ph = f.spPhase * .pi*2+Double(grp) * .pi
      let sw = sin(ph)*5.5*f.spWalk, idle = sin(2 * .pi*4*f.spIdle/12+Double(j)*1.7+side)*0.9*(1-f.spWalk)
      let hx = 3-Double(j)*2.2, hy = side*3.5, fx = rest[j].0+sw+idle, fy = side*(rest[j].1+cos(ph)*1.2*f.spWalk)
      let kx = (hx+fx)/2+(j<2 ? 3.0:-3), ky = (hy+fy)/2+side*6.5, p = path([(hx,hy),(kx,ky),(fx,fy)])
      stroke(c,p,"#2a2832",2.6); stroke(c,p,C.spiderLeg,1.8)
    } }
    oval(c,-19,-8.5,17,17,C.spider); oval(c,-16,-6.5,8,5,C.spiderHi,0.7); fill(c,hourglass,C.hourglass)
    circle(c,1.5,0,6.2,C.spider); circle(c,0.5,-2,2,C.spiderHi,0.6); rect(c,5,-3,2,2,C.spiderEye); rect(c,5,1,2,2,C.spiderEye); c.restoreGState()
  }
  static func burst(_ c: CGContext, _ f: F) {
    if f.burst<0 { return }
    for j in 0..<90 {
      let i = Double(j), a = f.burst-M.hash(i*2.9)*0.45; if a<=0 { continue }
      let life = 0.6+M.hash(i*5.5)*0.7; if a>life { continue }
      let ang = -Double.pi*0.36+(M.hash(i*1.7)-0.5)*1.5, v = 90+M.hash(i*3.9)*170, drag = (1-exp(-a*3.2))/3.2
      let x = 343+cos(ang)*v*drag+sin(a*6+i)*3, y = -4+sin(ang)*v*drag-14*a
      let al = 1-M.smooth(M.seg(a,life*0.55,life)), col = M.hash(i*2.2)>0.82 ? "#ffffff":M.hash(i)>0.45 ? C.mist:C.starGreen, sz = M.hash(i*7.1)>0.7 ? 2.6:1.5
      rect(c,x,y,sz,sz,col,al)
    }
  }
  static func card(_ c: CGContext, _ g: G, _ f: F) {
    let p = PotionVialUploadResources.shared.cardPath(g.cardY)
    fill(c,p,C.card,1,true); stroke(c,p,f.cardDoneA>0 ? C.cardLineHot:C.cardLine,1.3,1,true)
    let icon = g.icon, sx = icon.x-14, sy = icon.y-17
    if f.slotEmpty>0.01 {
      for i in 0..<4 { let a = Double(i)*8; line(c,sx+a,sy,sx+a+4,sy,C.faint,1.5,f.slotEmpty); line(c,sx+a,sy+34,sx+a+4,sy+34,C.faint,1.5,f.slotEmpty) }
      for i in 0..<5 { let a = Double(i)*8; line(c,sx,sy+a,sx,sy+a+4,C.faint,1.5,f.slotEmpty); line(c,sx+28,sy+a,sx+28,sy+a+4,C.faint,1.5,f.slotEmpty) }
    }
    if f.cardDoneA>0.01 {
      let bx = icon.x+11, by = icon.y+12
      circle(c,bx,by,6.5*M.easeOutBack(f.cardDoneA),C.zip,f.cardDoneA)
      stroke(c,path([(bx-3,by),(bx-0.8,by+2.3),(bx+3.2,by-2.2)]),"#0d2a10",1.6,f.cardDoneA)
    }
  }
  static func solid(_ c: CGContext, _ g: G, _ f: F) {
    c.saveGState(); local(c,g); c.saveGState(); c.translateBy(x:f.shake,y:-abs(f.shake)*0.4)
    liquidDetails(c,f); glass(c,f); chain(c,f); stopper(c); c.restoreGState()
    gem(c,f); spider(c,f); burst(c,f); card(c,g,f); c.restoreGState()
  }
}
