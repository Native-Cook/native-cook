import Foundation

enum DroneUnfoldConnectMotion {
  static let loop = 10.0
  static let lift = 0.9, stickRadius = 44.0, tiltMax = 0.35, drift = 0.35, yaw = 0.5
  static func clamp01(_ v: Double) -> Double { min(1, max(0, v)) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t-a)/(b-a)) }
  static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
    let u = seg(x,a,b); return u*u*(3-2*u)
  }
  static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2,3)/2 }
  static func easeOut(_ x: Double) -> Double { 1-pow(1-x,3) }
  static func stickAt(_ t: Double) -> (x: Double, y: Double) {
    (easeInOut(seg(t,4.2,4.9))-easeInOut(seg(t,5.5,6.0)),
     -(easeInOut(seg(t,6.1,6.6))-easeInOut(seg(t,6.9,7.3))))
  }
  struct Frame {
    var unfold = 0.0, spin = 0.0, lift = 0.0, pitch = 0.0, roll = 0.0, drift = 0.0
    var light = 0.0, connected = 0.0, stickX = 0.0, stickY = 0.0
    var pressConnect = 0.0, finger = 0.0, fingerAt = 0.0
    func stage(at t: Double) -> Int { connected > 0.5 ? 2 : t > 7.4 ? 3 : unfold > 0.001 ? 1 : 0 }
  }
  static func frameAt(_ t: Double) -> Frame {
    let u = easeInOut(seg(t,1,2.2))*(1-easeInOut(seg(t,8.6,9.6)))
    let spin = smooth(2.2,2.9,t)*(1-smooth(8.2,8.8,t))
    let fly = easeOut(seg(t,2.9,3.8))*(1-easeInOut(seg(t,7.4,8.4)))
    let bob = 0.05*sin(2*Double.pi*3*t/loop)*fly
    let s = stickAt(t-0.12), st = stickAt(t)
    func tap(_ a: Double) -> Double { easeOut(seg(t,a-0.08,a))*(1-easeOut(seg(t,a+0.06,a+0.2))) }
    func near(_ a: Double, _ b: Double) -> Double { easeOut(seg(t,a-0.25,a))*(1-easeOut(seg(t,b,b+0.2))) }
    let c = near(0.75,0.95), j = near(4.1,7.35)
    return Frame(unfold:u, spin:spin, lift:lift*fly+bob, pitch:-s.y*tiltMax*fly,
                 roll:-s.x*tiltMax*fly, drift:s.x*drift*fly,
                 light:smooth(2.3,2.5,t)*(1-smooth(9.4,9.6,t)),
                 connected:smooth(2.9,3.8,t)*(1-smooth(7.4,7.7,t)), stickX:st.x, stickY:st.y,
                 pressConnect:tap(0.8), finger:clamp01(c+j), fingerAt:c > 0.001 ? 0 : 1)
  }
  // Invert the x coordinate of Reanimated's cubic Bézier before evaluating y.
  static func bezier(_ v: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
    let x = clamp01(v)
    func cubic(_ t: Double, _ a: Double, _ b: Double) -> Double { 3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*b+t*t*t }
    var lo = 0.0, hi = 1.0
    for _ in 0..<24 { let m = (lo+hi)/2; if cubic(m,x1,x2) < x { lo=m } else { hi=m } }
    return cubic((lo+hi)/2,y1,y2)
  }
  // Reanimated 4 duration spring: solve the same normalized energy threshold (mass 4).
  static func springOmega(_ duration: Double, _ ratio: Double) -> Double {
    let settling = duration*1.5, mass=4.0
    func energyDifference(_ stiffness: Double) -> Double {
      let omega=sqrt(stiffness/mass)*ratio
      let e=exp(-omega*settling)
      let x=(1+omega*settling)*e
      let v=(1+omega*settling)*e*(-omega)+omega*e
      return (stiffness*x*x+mass*v*v)/stiffness-6e-9
    }
    var lo=Double.ulpOfOne, hi=8000.0, mid=(Double.ulpOfOne+8000)/2
    for _ in 0..<100 {
      let f=energyDifference(mid)
      if abs(f) <= 6e-12 { break }
      if f > 0 { lo=mid } else { hi=mid }; mid=(lo+hi)/2
    }
    return sqrt(mid/mass)
  }
  static let releaseOmega=springOmega(0.35,0.9)
  static let takeoverOmega=springOmega(0.3,1)
  static func stickSpring(_ t: Double, takeover: Bool) -> Double {
    let z=takeover ? 1.0 : 0.9, omega=takeover ? takeoverOmega : releaseOmega
    let e=exp(-z*omega*t)
    let x: Double, v: Double
    if z < 1 {
      let wd=omega*sqrt(1-z*z), frag=e*(cos(wd*t)+z*omega/wd*sin(wd*t))
      x=frag; v = -z*omega*frag+e*(cos(wd*t)*z*omega-wd*sin(wd*t))
    } else { x=e*(1+omega*t); v = -omega*e*(1+omega*t)+e*omega }
    return x*x+v*v/(omega*omega) <= 6e-9 ? 0 : x
  }

}
