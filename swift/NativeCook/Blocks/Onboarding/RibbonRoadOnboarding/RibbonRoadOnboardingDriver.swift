import Foundation
import CoreGraphics

final class RibbonRoadOnboardingDriver {
  typealias M = RibbonRoadOnboardingMotion
  let lines: [[CGPoint]]
  let field = RibbonRoadOnboardingField()
  var time=0.0, clock=0.0, last = -10.0
  var e=0.0, z = -1.0, phase=0.0, flow=1.0, button=0.0
  private var touching=false
  var cues: [String]=[]
  var on=false, x=0.0, y=0.0, press=0.0, grab = -1, gi=0, gx=0.0, gy=0.0
  init(_ size: CGSize) { lines=[M.centreLine(M.blue,size), M.centreLine(M.white,size)] }
  func advance(_ t: Double,_ dt: Double,_ scripted: Bool,_ reduced: Bool) {
    if scripted {
      e=t; z=t >= 3.8+0.15 ? t-3.8-0.15 : -1
      let k=M.smooth(M.seg(t,1.75,1.75+0.55))
      on=t>=1.75 && t<2.5
      if on {
        if grab != 0 { grab=0; gi=100; gx=lines[0][100].x; gy=lines[0][100].y }
        x=lines[0][100].x+70*k; y=lines[0][100].y+44*k
      }
      button=t>=3.8 && t<3.8+0.18 ? 1 : 0
    } else {
      e += dt
      if z>=0 { z += dt; if z>2.2 { z = -1; e=0 } }
      button *= exp(-dt*14)
    }
    press += ((on ? 1.0 : 0)-press)*(1-exp(-dt*(on ? 18 : 30)))
    let boost=z>=0 && !reduced ? 1+5*M.smooth(M.seg(z,0.1,0.7)) : 1
    let want=(on && grab>=0 ? 0.35 : 1)*boost
    flow += (want-flow)*(1-exp(-dt*4))
    if !reduced { phase += 64*flow*dt }
    field.step(grab,gi,x-gx,y-gy,press,dt)
    if !on && press<0.01 { grab = -1 }
  }
  func step(_ dt: Double,_ auto: Bool,_ reduced: Bool) {
    clock += dt
    cues.removeAll(keepingCapacity:true)
    // The scripted finger must not interrupt its own autoplay clock.
    let touched=touching || clock-last<2
    var remaining=dt
    while remaining>1e-10 {
      let h=min(1.0/60,remaining)
      if auto && !reduced && !touched {
        let previous=time
        time += h
        if time>=M.loop {
          time -= M.loop; phase=0; flow=1; press=0; grab = -1; on=false
          field.reset()
        }
        for (beat,name) in [(1.75,"stretch"),(2.5,"twang"),(3.8,"zip")] {
          if previous<beat && time>=beat { cues.append(name) }
        }
        advance(time,h,true,reduced)
      } else { advance(time,h,false,reduced) }
      remaining -= h
    }
  }
  static func replay(_ t: Double,_ size: CGSize,_ reduced: Bool) -> RibbonRoadOnboardingDriver {
    let d=RibbonRoadOnboardingDriver(size)
    var st=0.0
    while st<t { let dt=min(1.0/60,t-st); st += dt; d.advance(st,dt,true,reduced) }
    d.time=t
    return d
  }
  @discardableResult func begin(_ p: CGPoint) -> Bool {
    last=clock
    var best=44.0, hit = -1, index=0
    for i in 0..<340 {
      let r=i<170 ? 0 : 1, j=i%170
      let pt=lines[r][j]
      let distance=hypot(pt.x+field.ox[i]-p.x,pt.y+field.oy[i]-p.y)-(i>=170 ? 2 : 0)
      if distance<best { best=distance; hit=r; index=j }
    }
    touching=true; on=true; x=p.x; y=p.y; grab=hit
    if hit>=0 { gi=index; gx=p.x; gy=p.y }
    return hit>=0
  }
  func move(_ p: CGPoint) { last=clock; x=p.x; y=p.y }
  @discardableResult func end() -> Bool { last=clock; touching=false; on=false; return grab>=0 }
  @discardableResult func start() -> Bool {
    last=clock
    if z>=0 { return false }
    z=0; button=1
    return true
  }
}
