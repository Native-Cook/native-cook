import SwiftUI

struct FriendRadarMapArt {
  typealias M = FriendRadarMapMotion
  typealias R = FriendRadarMapResources
  let size: CGSize
  let top, bottom: Double
  let r = R.shared
  func rect(_ x:Double,_ y:Double,_ w:Double,_ h:Double,_ radius:Double = 0)->Path { Path(roundedRect:CGRect(x:x,y:y,width:w,height:h),cornerRadius:radius) }
  func text(_ context:GraphicsContext,_ text:String,_ key:String,_ x:Double,_ baseline:Double,_ color:Color = .white,_ track:Double = 0,_ anchor:Double = 0.5) {
    let label = r.label(text,key,track)
    var c = context; c.translateBy(x:x-label.width*anchor,y:baseline)
    for g in label.glyphs { c.fill(g.path,with:.color(color)); c.translateBy(x:g.width,y:0) }
  }
  func draw(_ context:GraphicsContext,_ f:M.Frame,_ reduced:Bool) {
    let w = size.width, h = size.height, fx = w/2, fy = top+h*0.3
    let a = M.friends[f.prev], b = M.friends[f.cur]
    let k = reduced ? 1:M.ease(f.since/0.9)
    let camX = M.mix(a.x,b.x,k), camY = M.mix(a.y,b.y,k)
    let zoom = reduced ? 1:1-0.22*sin(.pi*M.clamp(f.since/0.9))
    var map = context
    map.translateBy(x:fx,y:fy); map.scaleBy(x:zoom,y:zoom); map.translateBy(x:-camX,y:-camY)
    map.draw(Image(uiImage:r.map),in:CGRect(x:0,y:0,width:1100,height:2000))
    func point(_ i:Int)->CGPoint { let fr = M.friends[i]; return CGPoint(x:fx+(fr.x-camX)*zoom,y:fy+(fr.y-camY)*zoom) }
    for i in 0..<3 {
      if i == f.cur && f.since>0.35 { continue }
      if i == f.prev && f.prev != f.cur && f.since<0.3 && !reduced { continue }
      let p = point(i)
      context.fill(Path(ellipseIn:CGRect(x:p.x-4,y:p.y-4,width:8,height:8)),with:.color(.white))
      var line = Path(); line.move(to:p); line.addLine(to:CGPoint(x:p.x+10,y:p.y-22)); context.stroke(line,with:.color(.white),lineWidth:1.5)
      context.fill(rect(p.x+0.5,p.y-47.5,31,27),with:.color(.white))
      context.draw(Image(uiImage:r.faces[i]),in:CGRect(x:p.x+2,y:p.y-46,width:28,height:24))
    }
    let gOut = reduced || f.prev == f.cur ? 0:1-pow(M.clamp(f.since/0.3),2)
    let gIn = reduced ? 1:M.back((f.since-0.35)/0.55)
    for pass in 0..<2 {
      let who = pass == 0 ? f.prev:f.cur, g = pass == 0 ? gOut:gIn
      if g<=0.01 || (pass == 0 && f.prev == f.cur) { continue }
      let p = point(who), sc = M.mix(0.2,1,g), path = r.trapezoids[who]
      var c = context; c.translateBy(x:p.x,y:p.y-40*g); c.scaleBy(x:sc,y:sc)
      var glow = c; glow.addFilter(.blur(radius:28))
      glow.fill(path,with:.color(R.color(0x8dff5a,0.55*(0.75+0.25*sin(2 * .pi*3*f.time/M.loop)))))
      var face = c; face.clip(to:path)
      face.draw(Image(uiImage:r.faces[who]),in:CGRect(x:-85,y:-66,width:170,height:132))
      c.stroke(path,with:.color(R.color(0x0a0a0a)),style:StrokeStyle(lineWidth:3,lineJoin:.round))
    }
    let (name,decoded) = f.decoded(reduced)
    let width = r.label(name,"name").width, fit = min(1,(w-40)/width)
    var nc = context; nc.translateBy(x:w/2-width*fit/2,y:h-bottom-228); nc.scaleBy(x:fit,y:fit)
    let glyphs = r.label(decoded,"name").glyphs
    var halo = nc; halo.addFilter(.blur(radius:14))
    halo.drawLayer { layer in
      var p = layer
      for g in glyphs { p.fill(g.path,with:.color(.white.opacity(0.55))); p.translateBy(x:g.width,y:0) }
    }
    var outline = nc
    for g in glyphs { outline.stroke(g.path,with:.color(R.color(0x050505)),style:StrokeStyle(lineWidth:8,lineJoin:.miter)); outline.translateBy(x:g.width,y:0) }
    for g in glyphs { nc.fill(g.path,with:.color(.white)); nc.translateBy(x:g.width,y:0) }
    if !reduced && f.fill>=1 && f.burst>=0 && f.burst<1.4 {
      let since = f.burst, ox = 52.0, oy = h-bottom-54
      for i in 0..<40 {
        let d = Double(i), angle = -.pi/2+(M.hash(d*3.1)-0.3)*1.9, v = 260+M.hash(d*7.7)*420
        let x = ox+cos(angle)*v*since, y = oy+sin(angle)*v*since+700*since*since
        let sz = Double(4+i%3*2)
        context.fill(rect((x/3).rounded()*3,(y/3).rounded()*3,sz,sz),with:.color(R.color(i%3 == 0 ? 0x8dff5a:i%3 == 1 ? 0xd63ce0:0xffffff,1-M.clamp((since-0.8)/0.6))))
      }
    }
    // This gradient overlays the entire Skia layer, including name and particles.
    context.fill(r.fadeRect(size),with:.linearGradient(Gradient(stops:[.init(color:R.color(0x0b0b0d,0),location:0),.init(color:R.color(0x0b0b0d,0.85),location:0.45),.init(color:R.color(0x0b0b0d),location:1)]),startPoint:CGPoint(x:0,y:h-330),endPoint:CGPoint(x:0,y:h)))
  }
}
