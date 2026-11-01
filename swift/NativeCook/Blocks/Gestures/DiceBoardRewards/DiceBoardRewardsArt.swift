import SwiftUI

struct DiceBoardRewardsArt {
  typealias M = DiceBoardRewardsMotion
  typealias R = DiceBoardRewardsResources
  let g: DiceBoardRewardsGeometry
  let resources = R.shared
  static let unitOval = Path(ellipseIn:CGRect(x:0,y:0,width:1,height:1))
  static let pawnBody = Path { p in
    p.move(to:CGPoint(x:-14,y:0)); p.addQuadCurve(to:CGPoint(x:-6,y:-30),control:CGPoint(x:-12,y:-22))
    p.addLine(to:CGPoint(x:6,y:-30)); p.addQuadCurve(to:CGPoint(x:14,y:0),control:CGPoint(x:12,y:-22)); p.closeSubpath()
  }
  static let faces: [[Double]] = [
    [0,1,0,1,1,0,0,0,0,1],[0,-1,0,6,1,0,0,0,0,-1],
    [0,0,1,2,1,0,0,0,1,0],[0,0,-1,5,-1,0,0,0,1,0],
    [1,0,0,3,0,0,-1,0,1,0],[-1,0,0,4,0,0,1,0,1,0]
  ]
  static let pips: [[(Double,Double)]] = [[],[(0,0)],[(-0.5,-0.5),(0.5,0.5)],[(-0.5,-0.5),(0,0),(0.5,0.5)],[(-0.5,-0.5),(0.5,-0.5),(-0.5,0.5),(0.5,0.5)],[(-0.5,-0.5),(0.5,-0.5),(0,0),(-0.5,0.5),(0.5,0.5)],[(-0.5,-0.55),(0.5,-0.55),(-0.5,0),(0.5,0),(-0.5,0.55),(0.5,0.55)]]
  func polygon(_ points: [CGPoint]) -> Path {
    Path { p in
      guard let a = points.first else { return }
      p.move(to:a)
      for q in points.dropFirst() { p.addLine(to:q) }
      p.closeSubpath()
    }
  }
  func ellipse(_ c: GraphicsContext,_ x: Double,_ y: Double,_ w: Double,_ h: Double,_ hex: String,_ alpha: Double = 1) {
    var cv = c
    cv.translateBy(x:x,y:y); cv.scaleBy(x:w,y:h)
    cv.fill(Self.unitOval,with:.color(R.color(hex,alpha)))
  }
  func rect(_ c: GraphicsContext,_ x: Double,_ y: Double,_ w: Double,_ h: Double,_ hex: String,_ radius: Double = 0) {
    c.fill(Path(roundedRect:CGRect(x:x,y:y,width:w,height:h),cornerRadius:radius),with:.color(R.color(hex)))
  }
  func text(_ c: GraphicsContext,_ text: String,_ size: Double,_ color: String,_ x: Double,_ y: Double,center: Bool = false,baseline: Bool = false,title: Bool = false,icon: Bool = false) {
    let l = resources.label(text,size,color,title:title,icon:icon,skia:baseline)
    c.draw(l.image,at:CGPoint(x:x-(center ? l.width/2 : 0)-2,y:y-(baseline ? l.ascent : 0)-2),anchor:.topLeading)
  }
  func tile(_ c: GraphicsContext,_ i: Int,_ flip: Double) {
    let a = M.path[i], p = g.iso(a.0,a.1)
    let hw = g.tw*0.46, hh = g.th*0.46, cy = p.y+g.th*0.5
    let color = M.colors[i]
    c.fill(polygon([CGPoint(x:p.x-hw,y:cy),CGPoint(x:p.x,y:cy+hh),CGPoint(x:p.x+hw,y:cy),CGPoint(x:p.x+hw,y:cy+12),CGPoint(x:p.x,y:cy+hh+12),CGPoint(x:p.x-hw,y:cy+12)]),with:.color(R.color(color,1,0.78)))
    let sx = flip > 0 ? abs(cos(flip * .pi)) : 1
    let revealed = flip > 0.5 && flip < 1.5
    c.fill(polygon([CGPoint(x:p.x-hw*sx,y:cy),CGPoint(x:p.x,y:cy-hh),CGPoint(x:p.x+hw*sx,y:cy),CGPoint(x:p.x,y:cy+hh)]),with:.color(R.color(revealed ? "FFFFFF" : color)))
    if sx < 0.2 { return }
    var cv = c
    cv.translateBy(x:p.x,y:cy)
    cv.scaleBy(x:sx,y:0.55)
    if revealed {
      ellipse(cv,-15,-15,30,30,"FF6B5A"); ellipse(cv,-7,-7,14,14,"FFFFFF")
    } else {
      switch M.kinds[i] {
      case "coin": ellipse(cv,-14,-16,28,28,"F5B731"); ellipse(cv,-9,-11,18,18,"FFD766")
      case "gift": rect(cv,-13,-12,26,22,"FF6B8B"); rect(cv,-3,-12,6,22,"FFE066"); rect(cv,-15,-16,30,7,"FF8FA6")
      case "star":
        cv.fill(polygon((0..<10).map { k in
          let a = -.pi/2+Double(k) * .pi/5, r = k%2 == 1 ? 7.0 : 16.0
          return CGPoint(x:cos(a)*r,y:sin(a)*r)
        }),with:.color(R.color("9B6BFF")))
      case "coupon": rect(cv,-18,-11,36,22,"FF8A3D",5)
      default: rect(cv,-22,-10,44,20,"FF8A3D",10)
      }
    }
    if !revealed && !M.labels[i].isEmpty {
      var labelContext = c
      labelContext.opacity = M.kinds[i] == "start" ? 1 : 0.8
      text(labelContext,M.labels[i],11,M.kinds[i] == "start" ? "FFFFFF" : "3B3350",p.x,cy+(M.kinds[i] == "start" ? 4 : hh*0.75),center:true,baseline:true)
    }
  }
  func pawn(_ c: GraphicsContext,_ x: Double,_ y: Double,_ hop: Double) {
    let h = sin(.pi*hop)*26
    let sq = 1+0.12*sin(.pi*hop)-0.14*max(0,1-hop*6)*(hop > 0 ? 1 : 0)
    ellipse(c,x-13+h*0.1,y-4,26-h*0.2,9,"3B3350",0.18-h*0.003)
    var cv = c
    cv.translateBy(x:x,y:y-h); cv.scaleBy(x:1,y:sq)
    cv.fill(Self.pawnBody,with:.color(R.color("FF5C5C")))
    ellipse(cv,-15,-5,30,9,"E84545"); ellipse(cv,-11,-49,22,22,"FF6E6E"); ellipse(cv,-7.5,-45.5,7,7,"FFFFFF",0.7)
  }
  func proj(_ x: Double,_ y: Double,_ size: Double,_ rx: Double,_ rz: Double,_ vx: Double,_ vy: Double,_ vz: Double) -> (CGPoint,Double) {
    let b = vy*cos(rx)-vz*sin(rx), c = vy*sin(rx)+vz*cos(rx)
    let a2 = vx*cos(rz)-b*sin(rz), b2 = vx*sin(rz)+b*cos(rz)
    let a3 = a2*cos(.pi/4)+c*sin(.pi/4), c3 = -a2*sin(.pi/4)+c*cos(.pi/4)
    let b4 = b2*cos(0.62)-c3*sin(0.62), c4 = b2*sin(0.62)+c3*cos(0.62)
    return (CGPoint(x:x+a3*size,y:y-b4*size),c4)
  }
  func die(_ c: GraphicsContext,_ x: Double,_ y: Double,_ size: Double,_ rx: Double,_ rz: Double) {
    let depth = Self.faces.map { proj(x,y,size,rx,rz,$0[0],$0[1],$0[2]).1 }
    // Preserve RN's exact selection-sort order, including ties.
    var order = Array(0..<6)
    for i in 0..<6 { for j in (i+1)..<6 { if depth[order[j]] < depth[order[i]] { order.swapAt(i,j) } } }
    for f in order where depth[f] > 0.001 {
      let a = Self.faces[f]
      let points = [(-0.92,-0.92),(0.92,-0.92),(0.92,0.92),(-0.92,0.92)].map { u,v in
        proj(x,y,size,rx,rz,a[0]+a[4]*u+a[7]*v,a[1]+a[5]*u+a[8]*v,a[2]+a[6]*u+a[9]*v).0
      }
      c.fill(polygon(points),with:.color(R.color("FFFFFF",1,0.72+0.28*depth[f])))
      let val = Int(a[3]), radius = size*0.15*(0.6+0.4*depth[f])
      for (u,v) in Self.pips[val] {
        let q = proj(x,y,size,rx,rz,a[0]*1.001+a[4]*u+a[7]*v,a[1]*1.001+a[5]*u+a[8]*v,a[2]*1.001+a[6]*u+a[9]*v).0
        ellipse(c,q.x-radius,q.y-radius,radius*2,radius*2,val == 1 ? "FF4D5E" : "2E2A3D")
      }
    }
  }
  func board(_ c: GraphicsContext,_ o: M.Out,_ reduced: Bool,_ tiles: [DiceBoardRewardsStaticArt.Tile]) {
    let r = M.rollAt(o.th,o.u), landed = (o.th.from+o.th.steps)%12
    let landedPrev = (o.prev.from+o.prev.steps)%12
    let flipPrev = o.uPrev > 0 ? M.rollAt(o.prev,o.uPrev).flip : 0
    let c0 = g.iso(0,0), c3 = g.iso(4,4)
    ellipse(c,g.cx-g.tw*2.4,(c0.y+c3.y)/2-g.th*1.7+10,g.tw*4.8,g.th*4.4,"2D2550",0.07)
    for i in 0..<12 {
      let flip = i == landed && o.u > 0 ? r.flip : (i == landedPrev && flipPrev > 0 && flipPrev < 2 ? flipPrev : 0)
      if flip > 0 { tile(c,i,flip) }
      else if tiles.count == 12 { c.draw(tiles[i].image,at:tiles[i].origin,anchor:.topLeading) }
      else { tile(c,i,0) }
    }
    let midC = g.iso(1.5,1.5), mid = CGPoint(x:midC.x,y:midC.y+g.th*0.5)
    ellipse(c,mid.x-g.tw*0.8,mid.y-g.th*0.55,g.tw*1.6,g.th*1.1,"FFFFFF",0.8)
    let pp = g.tileAt(o.u < 0 ? Double(o.th.from) : r.pawn)
    let hop = o.u > 1.15 ? r.pawn-floor(r.pawn) : 0
    let size = g.tw*0.3
    var dx = g.cx, dy = g.trayY, rx = M.rotations[o.th.value].0, rz = M.rotations[o.th.value].1
    if o.u >= 0 && r.home < 1 {
      let f = r.fl, ex = mid.x, ey = mid.y-6
      dx = g.cx+(ex-g.cx)*M.easeOut(f)
      dy = g.trayY+(ey-g.trayY)*f-sin(.pi*f)*170-r.bounce*60
      if r.home > 0 {
        let h = M.smoother(r.home)
        dx = ex+(g.cx-ex)*h; dy = ey+(g.trayY-ey)*h-sin(.pi*h)*80
      }
      if !reduced { rx = r.rx; rz = r.rz }
    }
    ellipse(c,dx-size*1.3,(o.u >= 0 && r.home < 1 ? mid.y : g.trayY)+size*0.9,size*2.6,size*0.7,"2D2550",0.16)
    if dy+size < pp.y { die(c,dx,dy,size,rx,rz); pawn(c,pp.x,pp.y-4,hop) }
    else { pawn(c,pp.x,pp.y-4,hop); die(c,dx,dy,size,rx,rz) }
  }
}
