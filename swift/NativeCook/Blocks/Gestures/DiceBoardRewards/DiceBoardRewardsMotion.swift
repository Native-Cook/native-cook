import Foundation

enum DiceBoardRewardsMotion {
  static let loop = 9.0
  static let hop = 0.3
  static let path: [(Double, Double)] = [(3,3),(3,2),(3,1),(3,0),(2,0),(1,0),(0,0),(0,1),(0,2),(0,3),(1,3),(2,3)]
  static let colors = ["FFD95A","BFE7FF","FFC7D9","C9F2C7","E3D4FF","BFE7FF","FFE2B8","FFC7D9","C9F2C7","E3D4FF","FFE2B8","BFE7FF"]
  static let kinds = ["start","coin","gift","coin","star","coin","coupon","gift","coin","star","coupon","coin"]
  static let labels = ["START","+20","","+50","","+20","10%","","+80","","5%","+30"]
  static let prizes = ["Lap bonus · +100 coins","+20 coins","Mystery gift","+50 coins","Lucky star · roll again","+20 coins","10% OFF your basket","Mystery gift","+80 coins","Lucky star · roll again","5% OFF your basket","+30 coins"]
  static let rotations: [(Double,Double)] = [(0,0),(0,0),(.pi/2,0),(0,-.pi/2),(0,.pi/2),(-.pi/2,0),(.pi,0)]
  struct Throw {
    var at: Double = -10
    var value = 1
    var steps = 0
    var from = 0
  }
  struct Out {
    var th = Throw()
    var u = -1.0
    var prev = Throw()
    var uPrev = -1.0
  }
  struct Roll {
    var fl, rx, rz, bounce, home, pawn, flip, toast, hopsDone: Double
  }
  static func clamp(_ x: Double) -> Double { min(1,max(0,x)) }
  static func easeOut(_ x: Double) -> Double { 1-pow(1-clamp(x),3) }
  static func smoother(_ x: Double) -> Double {
    let x = clamp(x)
    return x*x*x*(x*(x*6-15)+10)
  }
  static func scriptAt(_ time: Double) -> Out {
    let t = time.truncatingRemainder(dividingBy: loop)
    let first = Throw(at:0.6,value:6,steps:6,from:0)
    if t < first.at { return Out(th:first,u:-1) }
    if t >= 4.6 {
      let second = Throw(at:4.6,value:6,steps:6,from:6)
      return Out(th:second,u:t-second.at,prev:first,uPrev:t-first.at)
    }
    return Out(th:first,u:t-first.at)
  }
  static func toastIndex(_ o: Out) -> Int {
    if rollAt(o.th,o.u).toast > 0.01 { return (o.th.from+o.th.steps)%12 }
    if o.uPrev > 0 && rollAt(o.prev,o.uPrev).toast > 0.01 { return (o.prev.from+o.prev.steps)%12 }
    return -1
  }
  static func rollAt(_ th: Throw, _ u: Double) -> Roll {
    let fl = clamp(u/0.95)
    let end = 1.15+Double(th.steps)*hop
    let home = clamp((u-end-0.5)/0.45)
    let spin = 1-easeOut(fl)
    let rot = rotations[th.value]
    let bounce = u > 0.95 && u < 1.25 ? sin((u-0.95)/0.3 * .pi)*0.18 : 0
    let hopU = (u-1.15)/hop
    let done = th.steps == 0 ? 0 : clamp(hopU/Double(th.steps))*Double(th.steps)
    let pawn: Double
    if hopU <= 0 { pawn = Double(th.from) }
    else if hopU >= Double(th.steps) { pawn = Double(th.from+th.steps) }
    else { pawn = Double(th.from)+floor(hopU)+smoother(hopU-floor(hopU)) }
    let toast = u > end+0.2 ? clamp((u-end-0.2)/0.3)*(1-clamp((u-end-0.9)/0.2)) : 0
    return Roll(fl: fl,rx:rot.0+spin * .pi*6,rz:rot.1+spin * .pi*4,bounce:bounce,home:home,pawn:pawn,flip:clamp((u-end)/0.35)+clamp((u-end-1.1)/0.35),toast:toast,hopsDone:done)
  }
}
