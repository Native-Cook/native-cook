import Foundation

struct HaloRingPairingFrame {
  var condense = 0.0, search = 1.0, yaw = 0.3, scale = 1.0
  var size = 9
  var fly = 0.0, paired = 0.0, found = 0.0
  var sizeBump = 0.0, pressPair = 0.0, finger = 0.0
  var fingerAt = 0, fingerDx = 0.0
  // Rendering uses bump(progress * 0.7), rather than frameAt's unused sizeBump.
  var bumpProgress = 1.0
  var phase = 0.0
  var step: Int { fly > 0.5 ? 2 : condense > 0.5 ? 1 : 0 }
}

enum HaloRingPairingMotion {
  static let loop = 11.0
  static let sizes = Array(7...12)
  static let startSize = 9
  static let searchSeconds = 1.8
  static let yaw0 = 0.3
  static func clamp01(_ v: Double) -> Double { min(1, max(0, v)) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t-a)/(b-a)) }
  static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
    let u = seg(x,a,b); return u*u*(3-2*u)
  }
  static func easeInOut(_ x: Double) -> Double {
    x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2,3)/2
  }
  static func easeOut(_ x: Double) -> Double { 1-pow(1-x,3) }
  static func glide(_ x: Double) -> Double { (1-exp(-4.5*clamp01(x)))/(1-exp(-4.5)) }
  static func bump(_ u: Double) -> Double {
    u <= 0 || u >= 0.7 ? 0 : exp(-u*7)*sin(u*20)
  }
  static func scaleOf(_ size: Int) -> Double { 1+Double(size-startSize)*0.025 }
  static func frameAt(_ t: Double) -> HaloRingPairingFrame {
    var f = HaloRingPairingFrame()
    f.condense = easeInOut(seg(t,1.8,3))*(1-easeInOut(seg(t,9.9,10.7)))
    f.search = clamp01(1-smooth(1.8,2.4,t)+smooth(10.2,loop,t))
    let dragU = seg(t,3.6,4)
    let yaw = yaw0+2*Double.pi*(0.2*dragU+0.8*glide(seg(t,4,5.2)))
    f.yaw = t < 3.6 ? yaw0 : t > 9.2 ? yaw0+2*Double.pi : yaw
    f.size = t >= 5.4 && t < 9.2 ? 10 : startSize
    f.scale = scaleOf(f.size)
    f.fly = easeInOut(seg(t,6.5,7.3))*(1-easeInOut(seg(t,9.2,9.9)))
    f.paired = smooth(6.8,7.5,t)*(1-smooth(9.1,9.5,t))
    f.found = smooth(2.5,3.2,t)*(1-smooth(6.4,6.65,t))
    func tap(_ a: Double) -> Double { easeOut(seg(t,a-0.08,a))*(1-easeOut(seg(t,a+0.06,a+0.2))) }
    func near(_ a: Double,_ b: Double) -> Double { easeOut(seg(t,a-0.25,a))*(1-easeOut(seg(t,b,b+0.2))) }
    let onRing = near(3.6,4.05), onSize = near(5.35,5.55), onPair = near(6.25,6.45)
    f.sizeBump = bump(t-5.4)
    f.bumpProgress = t > 5.4 ? min(1,(t-5.4)/0.7) : 1
    f.pressPair = tap(6.3)
    f.finger = clamp01(onRing+onSize+onPair)
    f.fingerAt = onRing > 0.001 ? 0 : onSize > 0.001 ? 1 : 2
    f.fingerDx = 160*easeInOut(dragU)
    f.phase = 2*Double.pi*t/loop
    return f
  }

  static func defaultEase(_ u: Double) -> Double {
    let x = clamp01(u); return x < 0.5 ? 2*x*x : 1-pow(-2*x+2,2)/2
  }
  // Reanimated 4's duration spring: mass 4, energy threshold 6e-9, 1.5× perceptual duration.
  static func springOmega(_ x0: Double,_ v0: Double,_ duration: Double) -> Double {
    if abs(x0) < 1e-12 && abs(v0) < 1e-12 { return 30.069499574943045 }
    var lo = Double.ulpOfOne, hi = 8000.0
    var stiffness = (lo+hi)/2
    let settling = duration*1.5
    for _ in 0..<100 {
      stiffness = (lo+hi)/2
      let omega = sqrt(stiffness/4)*0.6
      let envelope = exp(-omega*settling)
      let xt = (x0+(v0+x0*omega)*settling)*envelope
      let vt = xt * -omega+(v0+x0*omega)*envelope
      let fraction = (stiffness*xt*xt+4*vt*vt)/(stiffness*x0*x0+4*v0*v0)
      let diff = fraction-6e-9
      if abs(diff) <= 6e-12 { break }
      if diff > 0 { lo = stiffness } else { hi = stiffness }
    }
    return sqrt(stiffness/4)
  }

  // Reanimated's explicit touch timing curves (the auto loop above uses cubic polynomials).
  static func bezier(_ u: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
    let x = clamp01(u)
    func coordinate(_ t: Double,_ a: Double,_ b: Double) -> Double {
      3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*b+t*t*t
    }
    var lo = 0.0, hi = 1.0
    for _ in 0..<24 {
      let mid = (lo+hi)/2
      if coordinate(mid,x1,x2) < x { lo = mid } else { hi = mid }
    }
    return coordinate((lo+hi)/2,y1,y2)
  }
}
