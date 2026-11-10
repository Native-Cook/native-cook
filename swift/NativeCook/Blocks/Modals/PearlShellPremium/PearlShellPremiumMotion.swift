import Foundation

enum PearlShellPremiumMotion {
  static let loop = 10.0
  static let closed = 0.05
  static let open = 1.12
  static let lift = 0.22
  static let tau = Double.pi * 2
  struct Frame {
    var open = closed, glow = 0.0, lift = 0.0, spin = 0.0
    var phase = 0.0, yaw = 0.0
    var finger = 0.0, fingerY = 0.0, fingerPress = 0.0
  }
  static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t-a)/(b-a)) }
  static func easeInOut(_ x: Double) -> Double {
    x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2,3)/2
  }
  static func easeOut(_ x: Double) -> Double { 1-pow(1-x,3) }
  static func frameAt(_ t: Double) -> Frame {
    let breathe = 0.018*sin(tau*t/loop)
    var angle = closed+breathe
    if t >= 0.9 && t < 8.4 {
      if t < 2.1 { angle = closed+breathe+(0.9-closed)*easeInOut(seg(t,0.9,2.1)) }
      else {
        let dt = t-2.1
        angle = open+(0.9+breathe-open)*exp(-5*dt)*cos(7*dt)
      }
      if t >= 7.3 {
        let k = seg(t,7.3,8.4)
        let shut = k < 0.75 ? easeInOut(k/0.75) : 1-0.06*sin(.pi*(k-0.75)/0.25)
        let from = open+(0.9-open)*exp(-5*(7.3-2.1))*cos(7*(7.3-2.1))
        angle = from+(closed+breathe-from)*shut
      }
    }
    let glow = easeOut(seg(t,2.2,3))*(1-easeInOut(seg(t,7.3,8)))
    let rise = lift*easeInOut(seg(t,2.3,3.3))*(1-easeInOut(seg(t,7.3,7.9)))
    let finger = easeOut(seg(t,0.75,0.9))*(1-easeOut(seg(t,2.1,2.28)))
    return Frame(open:angle,glow:glow,lift:rise,spin:tau*t/loop,
      phase:(tau*2*t/loop).truncatingRemainder(dividingBy:tau),yaw:0.12*sin(tau*t/loop),
      finger:finger,fingerY:easeInOut(seg(t,0.9,2.1)),fingerPress:finger)
  }
  // Invert x, then evaluate y, just as Reanimated's cubic-bezier easing does.
  static func bezier(_ x: Double, _ a: Double, _ b: Double, _ c: Double, _ d: Double) -> Double {
    let x = clamp(x)
    func value(_ t: Double, _ p: Double, _ q: Double) -> Double {
      3*(1-t)*(1-t)*t*p+3*(1-t)*t*t*q+t*t*t
    }
    var lo = 0.0, hi = 1.0
    for _ in 0..<32 {
      let mid = (lo+hi)/2
      if value(mid,a,c)<x { lo=mid } else { hi=mid }
    }
    return value((lo+hi)/2,b,d)
  }
}

// Reanimated 4's duration spring (mass 4, relative energy threshold 6e-9).
// Stiffness is solved using the same bisection, not a SwiftUI spring approximation.
struct PearlShellPremiumSpring {
  var value: Double, velocity: Double, target: Double
  let ratio: Double, stiffness: Double, initialEnergy: Double
  var finished = false
  init(_ from: Double, _ target: Double, _ duration: Double, _ ratio: Double, _ velocity: Double, _ energyVelocity: Double? = nil) {
    self.value=from; self.target=target; self.ratio=ratio
    let v = (target>from && velocity<0) || (target<from && velocity>0) ? 0 : velocity
    self.velocity=v
    let x=from-target, duration=duration*1.5
    func fraction(_ k: Double) -> Double {
      let w=sqrt(k/4)*ratio, e=exp(-w*duration)
      let xt=(x+(v+x*w)*duration)*e
      let vt=xt * -w+(v+x*w)*e
      return (0.5*k*xt*xt+2*vt*vt)/(0.5*k*x*x+2*v*v)-6e-9
    }
    var lo=Double.ulpOfOne, hi=8000.0, mid=4000.0
    if x == 0 && v == 0 { mid=4000 }
    else {
      for _ in 0..<100 {
        mid=(lo+hi)/2
        let f=fraction(mid)
        if abs(f)<=6e-12 { break }
        if f>0 { lo=mid } else { hi=mid }
      }
    }
    stiffness=mid
    let initialV=energyVelocity ?? velocity
    initialEnergy=0.5*mid*x*x+2*initialV*initialV
  }
  mutating func step(_ dt: Double) {
    guard !finished else { return }
    let dt=min(max(dt,0),0.064), x=value-target
    let w=sqrt(stiffness/4), wd=w*sqrt(1-ratio*ratio)
    let envelope=exp(-ratio*w*dt), s=sin(wd*dt), c=cos(wd*dt)
    let part=envelope*(s*((velocity+ratio*w*x)/wd)+x*c)
    velocity = -ratio*w*part+envelope*(c*(velocity+ratio*w*x)-wd*x*s)
    value=target+part
    let energy=0.5*stiffness*part*part+2*velocity*velocity
    if initialEnergy==0 || energy/initialEnergy<=6e-9 { value=target;velocity=0;finished=true }
  }
}
