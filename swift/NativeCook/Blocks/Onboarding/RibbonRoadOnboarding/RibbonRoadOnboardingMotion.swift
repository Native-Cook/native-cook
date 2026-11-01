import Foundation
import CoreGraphics

enum RibbonRoadOnboardingMotion {
  static let loop = 6.4
  static let n = 170
  static let blue: [(Double, Double)] = [(-0.2,0.5),(0.04,0.49),(0.17,0.44),(0.25,0.33),(0.37,0.29),(0.47,0.38),(0.5,0.54),(0.6,0.66),(0.74,0.62),(0.8,0.44),(0.86,0.26),(0.95,0.14),(1.15,0.1)]
  static let white: [(Double, Double)] = [(-0.2,0.62),(0.06,0.61),(0.15,0.55),(0.2,0.42),(0.31,0.4),(0.41,0.5),(0.45,0.64),(0.57,0.73),(0.72,0.71),(0.83,0.57),(0.9,0.4),(0.98,0.27),(1.15,0.22)]
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { min(1,max(0,(t-a)/(b-a))) }
  static func smooth(_ x: Double) -> Double { let k = min(1,max(0,x)); return k*k*(3-2*k) }
  static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2,3)/2 }
  static func centreLine(_ points: [(Double,Double)], _ size: CGSize) -> [CGPoint] {
    let p = points.map { CGPoint(x: $0.0*size.width, y: $0.1*size.height) }
    var dense: [CGPoint] = []
    for i in 0..<p.count-1 {
      let a=p[max(0,i-1)], b=p[i], c=p[i+1], d=p[min(p.count-1,i+2)]
      for k in 0..<24 {
        let t=Double(k)/24, t2=t*t, t3=t2*t
        func f(_ a: Double,_ b: Double,_ c: Double,_ d: Double) -> Double {
          0.5*(2*b+(-a+c)*t+(2*a-5*b+4*c-d)*t2+(-a+3*b-3*c+d)*t3)
        }
        dense.append(CGPoint(x:f(a.x,b.x,c.x,d.x),y:f(a.y,b.y,c.y,d.y)))
      }
    }
    dense.append(p.last!)
    var acc=[0.0]
    for i in 1..<dense.count { acc.append(acc.last! + hypot(dense[i].x-dense[i-1].x,dense[i].y-dense[i-1].y)) }
    var j=0
    return (0..<n).map { i in
      let s=acc.last!*Double(i)/Double(n-1)
      while j<acc.count-2 && acc[j+1]<s { j += 1 }
      let k=(s-acc[j])/max(1e-6,acc[j+1]-acc[j])
      return CGPoint(x:dense[j].x+(dense[j+1].x-dense[j].x)*k,y:dense[j].y+(dense[j+1].y-dense[j].y)*k)
    }
  }
}

// Mutable buffers stay in the driver, never in per-frame SwiftUI State.
final class RibbonRoadOnboardingField {
  var revision=0
  var ox = [Double](repeating:0,count:340), oy = [Double](repeating:0,count:340)
  var vx = [Double](repeating:0,count:340), vy = [Double](repeating:0,count:340)
  func reset() {
    ox=Array(repeating:0,count:340); oy=ox; vx=ox; vy=ox
    revision += 1
  }
  func step(_ grab: Int,_ gi: Int,_ dx: Double,_ dy: Double,_ hold: Double,_ dt: Double) {
    let len=hypot(dx,dy), sc=len>0.001 ? 110*tanh(len/110)/len : 0
    let px=dx*sc*hold, py=dy*sc*hold
    let count=max(1,Int(ceil(dt/(1.0/120)))), h=dt/Double(count)
    var changed=false
    for _ in 0..<count {
      for i in 0..<340 {
        let r=i<170 ? 0 : 1
        var tx=0.0, ty=0.0, k=260.0, z=0.28
        if r==grab && hold>0.001 {
          let u=Double(i-r*170-gi)/16, g=exp(-u*u)
          tx=px*g; ty=py*g; k=900; z=0.9
        }
        let c=2*sqrt(k)*z
        vx[i] += (k*(tx-ox[i])-c*vx[i])*h
        vy[i] += (k*(ty-oy[i])-c*vy[i])*h
        let oldX=ox[i], oldY=oy[i]
        ox[i] += vx[i]*h; oy[i] += vy[i]*h
        changed = changed || oldX != ox[i] || oldY != oy[i]
      }
    }
    if changed { revision += 1 }
  }
}
