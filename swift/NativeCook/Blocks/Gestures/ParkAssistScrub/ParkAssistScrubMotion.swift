import Foundation

struct ParkAssistScrubPose { var x: Double; var y: Double; var heading: Double }
struct ParkAssistScrubFrame {
  var s = 0.0, assist = 0.0, brake = 0.0, hazard = 0.0, parked = 0.0
  var holding = 0.0, out = 0.0, finger = 0.0
}

enum ParkAssistScrubMotion {
  static let loop = 10.0
  static let wallY = -3.3, halfLength = 2.2, speed = 0.26
  static let hold1 = 0.8, go1 = 0.95, release1 = 3.5, hold2 = 4.3, go2 = 4.4
  static let parked = 6.6, release2 = 6.7, flash = 6.9, outStart = 8.0, outEnd = 9.8
  static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t-a)/(b-a)) }
  static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
    let u = seg(x,a,b); return u*u*(3-2*u)
  }
  static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2,3)/2 }
  static func easeOut(_ x: Double) -> Double { 1-pow(1-x,3) }
  static func poseAt(_ s: Double) -> ParkAssistScrubPose {
    let t = clamp(s), u = 1-t
    let x = u*u*u*2.7 + 3*u*u*t*1.3
    let y = u*u*u*5.1 + 3*u*u*t*4.7 + 3*u*t*t*2.6 - t*t*t*0.8
    let dx = 3*u*u*(1.3-2.7) + 6*u*t*(-1.3)
    let dy = 3*u*u*(4.7-5.1) + 6*u*t*(2.6-4.7) + 3*t*t*(-0.8-2.6)
    return .init(x:x,y:y,heading:atan2(dx,-dy))
  }
  static func steerAt(_ s: Double) -> Double {
    max(-0.5,min(0.5,(poseAt(s+0.01).heading-poseAt(s-0.01).heading)/0.02*0.35))
  }
  static func gapAt(_ s: Double) -> Double {
    let p = poseAt(s)
    return max(0,p.y-cos(p.heading)*halfLength-abs(sin(p.heading))*0.9-wallY)
  }
  static func sAt(_ t: Double) -> Double {
    if t < go1 { return 0 }
    if t < release1+0.25 {
      let u = (t-go1)/(release1+0.25-go1), a = 0.15, b = 0.1
      let d: Double
      if u < a { d = u*u/(2*a) }
      else if u < 1-b { d = a/2+(u-a) }
      else { d = a/2+(1-a-b)+(b-(1-u)*(1-u)/b)/2 }
      return 0.6*d/(1-a/2-b/2)
    }
    if t < go2 { return 0.6 }
    if t < outStart { return 0.6+0.4*easeOut(seg(t,go2,parked)) }
    return 1-easeInOut(seg(t,outStart,outEnd))
  }
  static func frameAt(_ t: Double) -> ParkAssistScrubFrame {
    let assist = smooth(go1-0.1,go1+0.2,t)*(1-smooth(outStart,outStart+0.3,t))
    let brake = smooth(release1+0.15,release1+0.3,t)*(1-smooth(hold2,hold2+0.1,t)) + smooth(parked,parked+0.15,t)*(1-smooth(outStart-0.2,outStart,t))
    func blink(_ a: Double) -> Double { sin(.pi*seg(t,a,a+0.28)) }
    func press(_ a: Double,_ b: Double) -> Double { easeOut(seg(t,a,a+0.1))*(1-easeOut(seg(t,b,b+0.15))) }
    func near(_ a: Double,_ b: Double) -> Double { easeOut(seg(t,a-0.25,a))*(1-easeOut(seg(t,b,b+0.2))) }
    return .init(s:sAt(t),assist:assist,brake:clamp(brake),hazard:blink(flash)+blink(flash+0.35),
      parked:smooth(parked,parked+0.3,t)*(1-smooth(outStart,outStart+0.3,t)),
      holding:press(hold1,release1)+press(hold2,release2),
      out:smooth(outStart,outStart+0.2,t)*(1-smooth(outEnd-0.2,outEnd,t)),
      finger:clamp(near(hold1,release1)+near(hold2,release2)))
  }
  // Reanimated Easing.bezier(0.77, 0, 0.175, 1), used only for manual Drive out.
  static func driveEase(_ x: Double) -> Double {
    var a = 0.0, b = 1.0
    for _ in 0..<30 {
      let u = (a+b)/2, v = 1-u
      if 3*v*v*u*0.77+3*v*u*u*0.175+u*u*u < x { a = u } else { b = u }
    }
    let u = (a+b)/2; return 3*(1-u)*u*u+u*u*u
  }
  // Reanimated's default withTiming easing, Easing.inOut(Easing.quad).
  static func timing(_ x: Double) -> Double { x < 0.5 ? 2*x*x : 1-pow(-2*x+2,2)/2 }
}
