import SwiftUI

enum PerfumeInkBloomTheme {
  static let name = "Azure Tide Bloom"
  static let body = "Bottled by a small house on the coast: bright citrus over a soft vanilla heart, worn close and lasting into the evening."
  static let notes = ["bergamot", "sea salt", "vanilla"]
  static func color(_ hex: UInt, _ alpha: Double = 1) -> Color {
    Color(.sRGB, red: Double((hex >> 16) & 255)/255, green: Double((hex >> 8) & 255)/255, blue: Double(hex & 255)/255, opacity: alpha)
  }
  struct Layout: Equatable {
    var w, h, top, bottom: Double
    var sx: Double { w/402 }
    var k: Double { (h-top-bottom)/778 }
    var cx: Double { 163*sx }
    var cy: Double { y(300) }
    var capX: Double { cx+sin(0.375)*138*sx }
    var capY: Double { cy-cos(0.375)*138*sx }
    var touchBox: CGRect { .init(x:cx-100*sx-10,y:capY-70*sx,width:320*sx,height:cy+100*sx-capY+80*sx) }
    init(_ size: CGSize, _ top: Double, _ bottom: Double) {
      w=size.width; h=size.height; self.top=max(top,44); self.bottom=max(bottom,20)
    }
    func y(_ v: Double) -> Double { v<62 ? v*top/62 : top+(v-62)*k }
  }
}

enum PerfumeInkBloomMotion {
  static let LOOP=10.0, RATE=240.0, SLOSH_W=9.0, SLOSH_Z=0.16
  static func clamp(_ v: Double) -> Double { min(1,max(0,v)) }
  static func seg(_ t: Double,_ a: Double,_ b: Double) -> Double { clamp((t-a)/(b-a)) }
  static func ease(_ x: Double) -> Double { x<0.5 ? 4*x*x*x : 1-pow(-2*x+2,3)/2 }
  static func out(_ x: Double) -> Double { let u=1-x; return 1-u*u*u }
  static func leanAt(_ t: Double) -> Double {
    if t<7.1 { return 0 }
    if t<7.7 { return -0.22*ease(seg(t,7.1,7.7)) }
    if t<8.4 { return -0.22+0.4*ease(seg(t,7.7,8.4)) }
    let u=t-8.4
    return 0.18*exp(-3.2*u)*cos(7*u)
  }
  // Identical two-pass 240 Hz semi-implicit Euler table, including the periodic carry.
  static let slosh: [Double] = {
    let n=Int(LOOP*RATE), dt=1/RATE
    var x=0.0, v=0.0, result=[Double]()
    for pass in 0..<2 {
      for i in 0..<n {
        let t=Double(i)*dt
        let a=(leanAt(t+dt)-2*leanAt(t)+leanAt(max(0,t-dt)))/(dt*dt)
        v += (-SLOSH_W*SLOSH_W*x-2*SLOSH_Z*SLOSH_W*v-a*0.06)*dt
        x += v*dt
        if pass==1 { result.append(x) }
      }
    }
    return result
  }()
  struct Frame {
    var press=0.0, age = -1.0, condense=0.0, evaporate=0.0, tilt=0.0, slosh=0.0
    var finger=0.0, fingerAt=0, fingerU=0.0, press2=0.0
  }
  static func frameAt(_ t: Double) -> Frame {
    var s=Frame()
    s.press=out(seg(t,0.75,0.95))*(1-out(seg(t,0.95,1.2)))
    s.age=t>=0.75 && t<7.2 ? t-0.75 : -1
    s.condense=ease(seg(t,1.9,3.2)); s.evaporate=seg(t,5.6,6.8)
    s.tilt=leanAt(t)
    s.slosh=slosh[min(slosh.count-1,max(0,Int(floor(t*RATE+0.5))))]
    let f0=out(seg(t,0.55,0.71))*(1-out(seg(t,1.2,1.36)))
    if f0>s.finger { s.finger=f0; s.press2=s.press }
    let f1=out(seg(t,6.88,7.06))*(1-out(seg(t,8.4,8.56)))
    if f1>s.finger { s.finger=f1; s.fingerAt=1; s.fingerU=s.tilt/0.22; s.press2=f1 }
    return s
  }
}
