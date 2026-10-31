import Foundation

enum LiquidArtDeckMotion {
  static let loop = 9.0
  static let titles = ["Mercury Bloom", "Quiet Chrome", "Dusk Liquor", "Soft Alloy", "Pearl Static", "Night Syrup"]
  static let authors = ["@ines.form", "@tomas.k", "@mira.studio", "@juno.labs", "@ade.works", "@koa.prints"]
  static let prices = ["$240", "$180", "$320", "$95", "$410", "$150"]
  static let seeds = [0.1, 0.37, 0.62, 0.83, 0.21, 0.5]
  static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
  static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b-a)*k }
  static func smooth(_ k: Double) -> Double { let x = clamp(k); return x*x*(3-2*x) }
  static func ease(_ k: Double) -> Double { let x = clamp(k); return x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2,3)/2 }
  struct Deck {
    var cur = 0, held = 0, fly = -1, kind = 0, back = 0, count = 12
    var dx = 0.0, dy = 0.0, vx = 0.0, vy = 0.0, sx = 0.0, sy = 0.0, svx = 0.0, svy = 0.0
    var start = 0.0, fx = 0.0, fy = 0.0, beat = -10.0
    var ds = (0..<6).map(Double.init), vds = Array(repeating: 0.0, count: 6), hist: [Int] = []
    @discardableResult mutating func swipe(_ kind: Int, _ now: Double) -> Bool {
      if fly >= 0 || cur >= 6 { return false }
      fly = cur; self.kind = kind; back = 0; start = now; fx = dx; fy = dy; hist.append(kind)
      cur += 1; dx = 0; dy = 0; vx = 0; vy = 0; return true
    }
    @discardableResult mutating func undo(_ now: Double) -> Bool {
      if fly >= 0 || hist.isEmpty { return false }
      kind = hist.removeLast(); cur -= 1; fly = cur; back = 1; start = now; fx = 0; fy = 0
      if kind == 1 { count -= 1 }; return true
    }
    mutating func step(_ dt: Double, _ now: Double) {
      let n = max(1, Int(ceil(dt*240))), h = dt/Double(n), px = dx, py = dy
      for _ in 0..<n {
        if held == 0 {
          vx += (-230*dx - 2*sqrt(230)*0.72*vx)*h; vy += (-230*dy - 2*sqrt(230)*0.72*vy)*h
          dx += vx*h; dy += vy*h
        }
        for i in 0..<6 {
          vds[i] += (190*(Double(i-cur)-ds[i]) - 2*sqrt(190)*0.86*vds[i])*h
          ds[i] += vds[i]*h
        }
      }
      let tx = max(-64, min(64, (dt > 0 ? (dx-px)/dt : 0)*0.1))
      let ty = max(-64, min(64, (dt > 0 ? (dy-py)/dt : 0)*0.1))
      for _ in 0..<n {
        svx += (260*(tx-sx)-2*sqrt(260)*0.28*svx)*h; svy += (260*(ty-sy)-2*sqrt(260)*0.28*svy)*h
        sx += svx*h; sy += svy*h
      }
      if fly >= 0 && now-start >= (kind == 1 ? 0.6 : 0.42) {
        if back == 0 && kind == 1 { count += 1; beat = now }
        if back == 1 { ds[fly] = 0 }; fly = -1
      }
    }
    mutating func script(_ t0: Double, _ t: Double) {
      if t >= 1 && t < 1.6 {
        let k = LiquidArtDeckMotion.ease((t-1)/0.6)
        held = 1; dx = 150*k; dy = -30*sin(k * .pi)-10*k
      } else if t >= 3.4 && t < 3.9 {
        let k = LiquidArtDeckMotion.ease((t-3.4)/0.5)
        held = 1; dx = -140*k; dy = 20*sin(k * .pi)
      } else if held == 1 { held = 0; swipe(dx > 0 ? 1 : 2,t) }
      if t0 < 5.6 && t >= 5.6 { undo(t) }
      if t0 < 7.2 && t >= 7.2 { undo(t) }
      step(t-t0,t)
    }
  }
  static func at(_ time: Double) -> Deck {
    var d = Deck(), t = 0.0
    while t < time { let end = min(time,t+1/120); d.script(t,end); t = end }
    return d
  }
  struct Pose { var i: Int; var x = 0.0, y = 0.0, rotation = 0.0, scale = 1.0, alpha = 1.0, dim = 0.0 }
  static func poses(_ d: Deck, _ now: Double, _ w: Double, _ cy: Double, _ tab: Double) -> [Pose] {
    var order: [Int] = []
    for s in stride(from: 3, through: 0, by: -1) {
      for i in 0..<6 where i != d.fly && i >= d.cur && Int(floor(d.ds[i]+0.5)) == s { order.append(i) }
    }
    if d.fly >= 0 { order.append(d.fly) }
    return order.compactMap { i in
      var p = Pose(i:i)
      if i == d.fly {
        var k = clamp((now-d.start)/(d.kind == 1 ? 0.6 : 0.42)); if d.back == 1 { k = 1-k }
        let e = d.back == 1 ? ease(k) : k*k*(3-2*k)
        if d.kind == 1 {
          let tx = w*0.625-w/2, ty = tab-cy, kx = (d.fx+tx)*0.5, ky = min(d.fy,ty)-170, u = 1-e
          p.x = u*u*d.fx+2*u*e*kx+e*e*tx; p.y = u*u*d.fy+2*u*e*ky+e*e*ty
          p.scale = mix(1,0.06,e*e); p.rotation = mix(d.fx*0.06,8,e); p.alpha = 1-smooth((e-0.88)/0.12)
        } else {
          p.x = mix(d.fx,-w*1.25,e*e); p.y = mix(d.fy,d.fy+50,e); p.rotation = mix(d.fx*0.06,-22,e)
        }
      } else {
        let ds = d.ds[i], a = min(abs(ds),1)
        p.x = (ds < 0 ? -1 : ds > 0 ? 1 : 0)*(211.8*a+max(0,abs(ds)-1)*140)
        p.scale = 1-0.2*a; p.dim = 0.55*a; p.alpha = 1-smooth(abs(ds)-1.4)
        if i == d.cur { p.x += d.dx; p.y += d.dy; p.rotation = d.dx*0.06 }
      }
      return p.alpha > 0.01 ? p : nil
    }
  }
}
