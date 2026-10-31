import SwiftUI
import UIKit

struct ClayCategoryTabsArtwork {
  typealias T = ClayCategoryTabsTheme
  typealias M = ClayCategoryTabsMotion
  let layout: T.Layout
  let height: Double, bottom: Double, scale: Double
  func text(_ ctx: inout GraphicsContext, _ value: String, _ x: Double, _ y: Double, _ size: Double,
            _ line: Double, _ color: Color, _ weight: Int = 0, width: Double = .greatestFiniteMagnitude,
            align: TextAlignment = .leading) {
    let font = UIFont(name:T.fontNames[weight],size:size) ?? UIFont.systemFont(ofSize:size)
    var value = value
    // Runtime metrics, not hard-coded glyph positions. The RN card title is one line with ellipsis.
    if width.isFinite && width < 10000 {
      let attrs: [NSAttributedString.Key:Any] = [.font:font]
      if (value as NSString).size(withAttributes:attrs).width > width {
        while !value.isEmpty && ((value+"…") as NSString).size(withAttributes:attrs).width > width { value.removeLast() }
        value += "…"
      }
    }
    let measured = (value as NSString).size(withAttributes:[.font:font]).width
    let tx = align == .center ? x-measured/2 : align == .trailing ? x-measured : x
    ctx.draw(Text(value).font(.custom(T.fontNames[weight],fixedSize:size)).foregroundColor(color),
             at:CGPoint(x:tx,y:y+(line-font.lineHeight)/2),anchor:.topLeading)
  }
  func round(_ ctx: inout GraphicsContext, _ rect: CGRect, _ radius: Double, _ color: Color, stroke: Double = 0, continuous: Bool = false) {
    let p = Path(roundedRect:rect,cornerRadius:radius,style:continuous ? .continuous : .circular)
    if stroke > 0 { ctx.stroke(p,with:.color(color),lineWidth:stroke) } else { ctx.fill(p,with:.color(color)) }
  }
  func svg(_ ctx: inout GraphicsContext, _ kind: String, x: Double, y: Double, size: Double, color: Color) {
    var c = ctx; c.translateBy(x:x,y:y)
    let base = kind == "star" ? 12.0 : kind == "map" ? 16.0 : 18.0
    c.scaleBy(x:size/base,y:size/base)
    var p = Path()
    func line(_ x: Double,_ y: Double,_ a: Double,_ b: Double) { p.move(to:.init(x:x,y:y)); p.addLine(to:.init(x:a,y:b)) }
    if kind == "search" {
      p.addEllipse(in:CGRect(x:2,y:2,width:11,height:11)); line(11.6,11.6,16,16)
    } else if kind == "filter" {
      line(2,5,11,5); line(15,5,16,5); line(2,13,3,13); line(7,13,16,13)
      p.addEllipse(in:CGRect(x:11,y:3,width:4,height:4)); p.addEllipse(in:CGRect(x:3,y:11,width:4,height:4))
    } else if kind == "map" {
      p.move(to:.init(x:1.5,y:3.5))
      for point in [CGPoint(x:5.5,y:2),CGPoint(x:10.5,y:4),CGPoint(x:14.5,y:2.5),CGPoint(x:14.5,y:12.5),CGPoint(x:10.5,y:14),CGPoint(x:5.5,y:12),CGPoint(x:1.5,y:13.5)] { p.addLine(to:point) }
      p.closeSubpath(); line(5.5,2,5.5,12); line(10.5,4,10.5,14)
    } else {
      p.move(to:.init(x:6,y:0.8))
      for point in [CGPoint(x:7.6,y:4.1),CGPoint(x:11.2,y:4.6),CGPoint(x:8.6,y:7.1),CGPoint(x:9.2,y:10.7),CGPoint(x:6,y:9),CGPoint(x:2.8,y:10.7),CGPoint(x:3.4,y:7.1),CGPoint(x:0.8,y:4.6),CGPoint(x:4.4,y:4.1)] { p.addLine(to:point) }
      p.closeSubpath(); c.fill(p,with:.color(color)); return
    }
    c.stroke(p,with:.color(color),style:StrokeStyle(lineWidth:kind == "map" ? 1.5 : kind == "filter" ? 1.8 : 2,lineCap:.round,lineJoin:.round))
  }
  func draw(_ ctx: inout GraphicsContext, frame f: M.Frame, time: Double, reduced: Bool) {
    let l = layout, w = l.w, xs = l.xs, k = f.r, kc = min(2,max(0,k)), frac = kc-floor(kc)
    ctx.fill(Path(CGRect(x:0,y:0,width:w,height:height)),with:.color(T.page))
    let pill = CGRect(x:16,y:l.searchTop,width:w-88,height:56)
    round(&ctx,pill,28,T.card); round(&ctx,pill.insetBy(dx:0.5,dy:0.5),27.5,T.line,stroke:1)
    svg(&ctx,"search",x:36,y:l.searchTop+19,size:18,color:T.ink)
    text(&ctx,"Where to?",68,l.searchTop+10,15,20,T.ink,2)
    text(&ctx,"Anywhere · Any week · Add guests",68,l.searchTop+30,12,16,T.mute)
    round(&ctx,CGRect(x:w-60+0.5,y:l.searchTop+6.5,width:43,height:43),21.5,T.line,stroke:1)
    svg(&ctx,"filter",x:w-47,y:l.searchTop+19,size:18,color:T.ink)
    var stage = ctx; stage.translateBy(x:0,y:l.stageTop)
    let hop = reduced ? 0 : T.hop*sin(.pi*frac)
    let shader = Shader(function:ShaderFunction(library:.default,name:"ClayCategoryTabsMain"),arguments:[
      .float4(Float(w/2-l.slot+k*l.slot),Float(T.iconY-hop),Float(T.iconS*(1-0.08*sin(.pi*frac))),Float(f.squash)),
      .float4(Float(k),Float(0.35+(reduced ? 0 : 0.5*sin(.pi*frac))),Float(f.wobble),Float(f.knob)),
      .float4(Float(xs[0]),Float(xs[1]),Float(xs[2]),Float(T.iconY)),
      .float4(Float(M.smooth(0.15,0.85,abs(k))),Float(M.smooth(0.15,0.85,abs(k-1))),Float(M.smooth(0.15,0.85,abs(k-2))),Float(T.ghostS)),
      .float4(Float(time),Float(scale),0,0)])
    stage.fill(Path(CGRect(x:0,y:0,width:w,height:T.stageH)),with:.shader(shader))
    for i in 0..<3 {
      let near = max(0,1-abs(k-Double(i)))
      // Reanimated interpolateColor's default gamma is 2.2.
      let mute = [142.0,140,153], ink = [244.0,243,247]
      let rgb = zip(mute,ink).map { (pow(pow($0/255,2.2)*(1-near)+pow($1/255,2.2)*near,1/2.2)*255).rounded()/255 }
      let color = Color(.sRGB,red:rgb[0],green:rgb[1],blue:rgb[2],opacity:1)
      text(&stage,T.categories[i].name,xs[i],202,14,20,color,2,align:.center)
    }
    round(&stage,CGRect(x:0,y:244-1/scale,width:w,height:1/scale),0,T.line)
    let stretch = 1+0.9*sin(.pi*M.clamp01(k-floor(k))), bx = w/2-l.slot+k*l.slot
    round(&stage,CGRect(x:bx-20*stretch,y:241,width:40*stretch,height:3),1.5,T.ink)
    var pages = ctx
    pages.clip(to:Path(CGRect(x:0,y:l.pagerTop,width:w,height:max(0,height-l.pagerTop))))
    for (i,c) in T.categories.enumerated() {
      let px = Double(i)*w-k*w
      if px < -w || px > w { continue }
      text(&pages,c.heading,px+16,l.pagerTop+20,20,26,T.ink,2)
      for (j,item) in c.listings.enumerated() {
        let x = px+16, y = l.pagerTop+58+Double(j)*106
        round(&pages,CGRect(x:x,y:y,width:w-32,height:94),20,T.card,continuous:true)
        let priceFont = UIFont(name:T.fontNames[2],size:18) ?? .systemFont(ofSize:18)
        let unitFont = UIFont(name:T.fontNames[0],size:12) ?? .systemFont(ofSize:12)
        let priceW = max((item.price as NSString).size(withAttributes:[.font:priceFont]).width,(item.unit as NSString).size(withAttributes:[.font:unitFont]).width)
        let mainW = w-64-12-priceW
        text(&pages,item.title,x+16,y+14,16,22,T.ink,2,width:mainW)
        text(&pages,item.meta,x+16,y+38,13,18,T.mute,width:mainW)
        text(&pages,item.when,x+16,y+62,13,18,T.ink,1)
        let font = UIFont(name:T.fontNames[1],size:13) ?? .systemFont(ofSize:13)
        let whenW = (item.when as NSString).size(withAttributes:[.font:font]).width
        let dotFont = UIFont(name:T.fontNames[0],size:13) ?? .systemFont(ofSize:13)
        let dotW = ("·" as NSString).size(withAttributes:[.font:dotFont]).width
        text(&pages,"·",x+22+whenW,y+62,13,18,T.mute)
        let sx = x+28+whenW+dotW
        svg(&pages,"star",x:sx,y:y+65,size:12,color:T.star)
        text(&pages,item.rating,sx+18,y+62,13,18,T.ink,1)
        text(&pages,item.price,x+w-48,y+27,18,24,T.ink,2,align:.trailing)
        text(&pages,item.unit,x+w-48,y+51,12,16,T.mute,align:.trailing)
      }
    }
    let mapFont = UIFont(name:T.fontNames[2],size:15) ?? .systemFont(ofSize:15)
    let mapTextW = ("Map" as NSString).size(withAttributes:[.font:mapFont]).width
    let mw = mapTextW+64, my = height-max(bottom,16)-56
    var shadow = ctx; shadow.addFilter(.shadow(color:.black.opacity(0.45),radius:20,x:0,y:6))
    round(&shadow,CGRect(x:(w-mw)/2,y:my,width:mw,height:48),24,T.ink)
    text(&ctx,"Map",(w-mw)/2+20,my+14,15,20,T.page,2)
    svg(&ctx,"map",x:(w-mw)/2+28+mapTextW,y:my+16,size:16,color:T.page)
    if f.finger > 0 {
      let x = f.onPage == 1 ? w*(0.78-0.42*f.dragX) : xs[f.tab]
      let y = f.onPage == 1 ? l.pagerTop+150 : l.stageTop+T.labelY+8
      var finger = ctx; finger.opacity = 0.85*f.finger
      finger.translateBy(x:x,y:y); finger.scaleBy(x:1-0.12*f.press,y:1-0.12*f.press)
      var fingerShadow = finger; fingerShadow.addFilter(.shadow(color:.black.opacity(0.2),radius:6,x:0,y:1))
      round(&fingerShadow,CGRect(x:-22,y:-22,width:44,height:44),22,.white.opacity(0.4))
      round(&finger,CGRect(x:-21.25,y:-21.25,width:42.5,height:42.5),21.25,.white.opacity(0.8),stroke:1.5)
    }
  }
}
