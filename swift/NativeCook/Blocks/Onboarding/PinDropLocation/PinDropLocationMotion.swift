import CoreGraphics

struct PinDropLocationLayout: Equatable {
  var w, h, top, bottom: Double
  var sx: Double { w / 402 }
  var k: Double { (h - top - bottom) / 778 }
  func y(_ y: Double) -> Double { y < 62 ? y * top / 62 : top + (y - 62) * k }
  var cx: Double { 201 * sx }
  var cy: Double { y(333) }
  var radius: Double { 92 * sx }
  var allow: CGRect { CGRect(x: 20*sx, y: y(763), width: 362*sx, height: 59*k) }
  var skip: CGRect { CGRect(x: 312*sx, y: y(73), width: 69, height: 42) }
  var map: CGRect { CGRect(x: cx-radius-20, y: cy-radius-20, width: 2*radius+40, height: 2*radius+40) }
  init(_ w: Double, _ h: Double, _ top: Double, _ bottom: Double) { self.w=w; self.h=h; self.top=max(top,44); self.bottom=max(bottom,20) }
}
struct PinDropLocationFrame: Equatable {
  var bend = 0.0, lon = 0.0, lat = 0.0, hover = 0.0, fall = 1.0, since = -1.0
  var lights = 0.0, zoom = 0.0, places = 0.0, allowed = 0.0
  var finger = 0.0, fingerAt = 0, fingerU = 0.0, press = 0.0
}
enum PinDropLocationMotion {
  static let loop = 10.0
  static func clamp(_ v: Double) -> Double { min(1,max(0,v)) }
  static func seg(_ t: Double,_ a: Double,_ b: Double) -> Double { clamp((t-a)/(b-a)) }
  static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2,3)/2 }
  static func easeOut(_ x: Double) -> Double { 1-pow(1-x,3) }
  static func frameAt(_ t: Double) -> PinDropLocationFrame {
    var f = PinDropLocationFrame()
    f.bend = easeInOut(seg(t,0.5,1.1)) * (1-easeInOut(seg(t,2.6,3.1)))
    let turn = easeInOut(seg(t,1.1,2.6)), home = easeInOut(seg(t,8,9.4))
    f.lon = 1.5*turn*(1-home)
    f.lat = 0.35*sin(.pi*turn)*(1-home)
    f.hover = t < 3.15 ? easeOut(seg(t,0.5,1.1)) : 0
    if t >= 0.5 && t < 3.15 { f.fall = 1-pow(seg(t,0.6,1.3),3) }
    else if t >= 3.15 && t < 3.55 { f.fall = pow(seg(t,3.15,3.55),3) }
    f.since = t >= 3.55 ? t-3.55 : -1
    f.lights = 1-easeInOut(seg(t,8.6,9.4))
    f.zoom = easeInOut(seg(t,5.5,6.3)) * (1-easeInOut(seg(t,8,8.8)))
    f.places = easeOut(seg(t,6,6.8)) * (1-easeInOut(seg(t,8,8.4)))
    f.allowed = t >= 5.4 && t < 8.3 ? 1 : 0
    let drag = easeOut(seg(t,0.32,0.48)) * (1-easeOut(seg(t,2.6,2.76)))
    if drag > f.finger {
      f.finger = drag; f.fingerU = t < 1.1 ? -1+easeInOut(seg(t,0.5,1.1)) : turn; f.press = drag
    }
    let a = easeOut(seg(t,5.22,5.36)) * (1-easeOut(seg(t,5.54,5.7)))
    if a > f.finger {
      f.finger = a; f.fingerAt = 1
      f.press = easeOut(seg(t,5.36,5.45)) * (1-easeOut(seg(t,5.5,5.62)))
    }
    return f
  }
  static func bezier(_ x: Double,_ a: Double,_ b: Double,_ c: Double,_ d: Double) -> Double {
    if x <= 0 { return 0 }; if x >= 1 { return 1 }
    var lo = 0.0, hi = 1.0
    for _ in 0..<28 { let t=(lo+hi)/2; let v=3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*c+t*t*t; if v<x { lo=t } else { hi=t } }
    let t=(lo+hi)/2
    return 3*(1-t)*(1-t)*t*b+3*(1-t)*t*t*d+t*t*t
  }
}
