import CoreGraphics

// SkContourMeasure's quadratic subdivision (resScale=1, tolerance=0.5, depth=8).
// Distances and path coordinates use Float, as Skia does; the physics remains Double.
struct RibbonRoadOnboardingContour {
  struct Quad {
    var a, b, c: CGPoint
    var line=false
    func point(_ t: Double) -> CGPoint {
      if line { return Self.mix(a,c,t) }
      return Self.mix(Self.mix(a,b,t),Self.mix(b,c,t),t)
    }
    func tangent(_ t: Double) -> CGPoint {
      let v=line ? CGPoint(x:c.x-a.x,y:c.y-a.y) : CGPoint(x:2*((1-t)*(b.x-a.x)+t*(c.x-b.x)),y:2*((1-t)*(b.y-a.y)+t*(c.y-b.y)))
      let len=max(1e-20,hypot(v.x,v.y))
      return CGPoint(x:v.x/len,y:v.y/len)
    }
    static func mix(_ a: CGPoint,_ b: CGPoint,_ t: Double) -> CGPoint {
      CGPoint(x:a.x+(b.x-a.x)*t,y:a.y+(b.y-a.y)*t)
    }
  }
  struct Entry { var distance: Float; var index: Int; var t: Double }
  var quads: [Quad]=[]
  var entries: [Entry]=[]
  var length: Double { Double(entries.last?.distance ?? 0) }
  init(_ points: [CGPoint],_ field: RibbonRoadOnboardingField,_ offset: Int) {
    func quantize(_ p: CGPoint) -> CGPoint { CGPoint(x:Double(Float(p.x)),y:Double(Float(p.y))) }
    let pts=points.indices.map { i in CGPoint(x:points[i].x+field.ox[offset+i],y:points[i].y+field.oy[offset+i]) }
    var a=quantize(pts[0])
    for i in 1..<169 {
      let b=quantize(pts[i]), c=quantize(Quad.mix(pts[i],pts[i+1],0.5))
      quads.append(Quad(a:a,b:b,c:c)); a=c
    }
    quads.append(Quad(a:a,b:a,c:quantize(pts[169]),line:true))
    for i in quads.indices {
      let q=quads[i]
      append(q.a,q.b,q.c,0,1,i,0,q.line)
    }
  }
  private mutating func append(_ a: CGPoint,_ b: CGPoint,_ c: CGPoint,_ t0: Double,_ t1: Double,_ index: Int,_ depth: Int,_ line: Bool) {
    let dx=abs(Float(b.x)/2-(Float(a.x)+Float(c.x))/4)
    let dy=abs(Float(b.y)/2-(Float(a.y)+Float(c.y))/4)
    if !line && depth<8 && max(dx,dy)>0.5 {
      func half(_ p: CGPoint,_ q: CGPoint) -> CGPoint { CGPoint(x:Double((Float(p.x)+Float(q.x))*0.5),y:Double((Float(p.y)+Float(q.y))*0.5)) }
      let ab=half(a,b), bc=half(b,c), mid=half(ab,bc), tm=(t0+t1)/2
      append(a,ab,mid,t0,tm,index,depth+1,false)
      append(mid,bc,c,tm,t1,index,depth+1,false)
    } else {
      let old=entries.last?.distance ?? 0
      let d=old+hypot(Float(c.x-a.x),Float(c.y-a.y))
      if d>old { entries.append(Entry(distance:d,index:index,t:t1)) }
    }
  }
  func locate(_ distance: Double) -> (Int,Double) {
    let d=Float(min(length,max(0,distance)))
    var lo=0, hi=entries.count-1
    while lo<hi { let mid=(lo+hi)/2; if entries[mid].distance<d { lo=mid+1 } else { hi=mid } }
    let e=entries[lo], prev=lo>0 ? entries[lo-1].distance : 0
    let t0=lo>0 && entries[lo-1].index==e.index ? entries[lo-1].t : 0
    return (e.index,t0+(e.t-t0)*Double((d-prev)/max(1e-20,e.distance-prev)))
  }
  func position(_ distance: Double) -> (CGPoint,CGPoint) {
    let (i,t)=locate(distance); return (quads[i].point(t),quads[i].tangent(t))
  }
  func part(_ a: Double,_ b: Double) -> CGPath {
    let path=CGMutablePath()
    let (first,t0)=locate(a), (last,t1)=locate(b)
    path.move(to:quads[first].point(t0))
    for i in first...last {
      let q=quads[i], lo=i==first ? t0 : 0, hi=i==last ? t1 : 1
      if q.line { path.addLine(to:q.point(hi)) }
      else {
        let start=q.point(lo)
        let derivative=CGPoint(x:2*((1-lo)*(q.b.x-q.a.x)+lo*(q.c.x-q.b.x)),y:2*((1-lo)*(q.b.y-q.a.y)+lo*(q.c.y-q.b.y)))
        let control=CGPoint(x:start.x+derivative.x*(hi-lo)/2,y:start.y+derivative.y*(hi-lo)/2)
        path.addQuadCurve(to:q.point(hi),control:control)
      }
    }
    return path
  }
}
