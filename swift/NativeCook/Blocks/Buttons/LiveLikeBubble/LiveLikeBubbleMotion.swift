import Foundation

// A direct transcription of llb-motion.ts. All times are seconds and positions are scene units.
enum LiveLikeBubbleMotion {
  static let loop = 9.0
  static let FLIGHT = 0.8, SWALLOW = 0.16, BASE = 11, SLOTS = 6
  static let CENTRE_Y = 1.15
  static let THROWS = [1.0, 1.45, 1.9, 3.0, 3.22, 3.44, 3.66, 3.88, 4.1, 4.32, 4.54, 4.76, 4.98, 5.2]
  static let LANES = [-0.25, 0.1, 0.3, 0.0, -0.15, 0.2, -0.08, 0.14, -0.22, 0.05, 0.25, -0.12, 0.18, -0.03]
  static let DECAY = [6.4, 8.6]
  struct Frame { var count: Int; var swell: Double; var press: Double; var finger: Double; var fingerX = 0.0 }
  static func clamp01(_ v: Double) -> Double { min(1, max(0, v)) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t-a)/(b-a)) }
  static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double { let u = seg(x,a,b); return u*u*(3-2*u) }
  static func easeOut(_ x: Double) -> Double { let u = 1-x; return 1-u*u*u }
  static func targetOf(_ i: Int) -> SIMD3<Double> {
    let a = (Double(i)*2.39996).truncatingRemainder(dividingBy: 2 * .pi)
    return SIMD3(0.15+0.75*cos(a), CENTRE_Y+0.45+0.15*sin(a), 0.1)
  }
  static func heartAt(_ now: Double, _ start: Double, _ lane: Double, _ i: Int) -> SIMD4<Float> {
    let u = (now-start)/FLIGHT
    if u < 0 || u > 1+SWALLOW/FLIGHT { return .zero }
    let tg = targetOf(i), k = clamp01(u)
    let e = k*k*(1.6-0.6*k), v = 1-e
    let sx = lane, sy = -1.25, sz = 2.3
    let cx = (sx+tg.x)/2, cy = max(sy,tg.y)+0.9, cz = 0.8
    let x = v*v*sx+2*v*e*cx+e*e*tg.x
    let y = v*v*sy+2*v*e*cy+e*e*tg.y
    let z = v*v*sz+2*v*e*cz+e*e*tg.z
    let w = clamp01((now-start-FLIGHT)/SWALLOW)
    let size = (0.22+0.04*sin(Double(i)))*smooth(0,0.12,u)*(1-w)
    return SIMD4(Float(x*(1-w*0.5)), Float(y+(CENTRE_Y-y)*w*0.5), Float(z*(1-w)), Float(size))
  }
  static func pulse(_ age: Double) -> Double {
    if age <= 0 || age >= 0.6 { return 0 }
    return exp(-age*9)*sin(age*22+0.3)
  }
  static func countAt(_ t: Double) -> Int {
    let n = THROWS.filter { t >= $0+FLIGHT+SWALLOW }.count
    return BASE+n-Int(floor(Double(THROWS.count)*smooth(DECAY[0],DECAY[1],t)+1e-6))
  }
  static func frameAt(_ t: Double) -> Frame {
    let sw = THROWS.reduce(0.0) { $0+pulse(t-$1-FLIGHT-SWALLOW*0.5) }
    func tap(_ a: Double) -> Double { easeOut(seg(t,a-0.06,a))*(1-easeOut(seg(t,a+0.05,a+0.16))) }
    var press = tap(THROWS[0])+tap(THROWS[1])+tap(THROWS[2])
    let last = THROWS.last!
    press += easeOut(seg(t,THROWS[3]-0.06,THROWS[3]))*(1-easeOut(seg(t,last+0.05,last+0.2)))
    let near = easeOut(seg(t,THROWS[0]-0.3,THROWS[0]-0.05))*(1-easeOut(seg(t,last+0.1,last+0.35)))
    return Frame(count: countAt(t), swell: min(1,max(-1,sw)), press: clamp01(press), finger: near)
  }
  static func heartsAt(_ t: Double) -> [SIMD4<Float>] {
    var hearts = Array(repeating: SIMD4<Float>.zero, count: SLOTS)
    for i in THROWS.indices where t >= THROWS[i] && t <= THROWS[i]+FLIGHT+SWALLOW {
      hearts[i%SLOTS] = heartAt(t,THROWS[i],LANES[i],i)
    }
    return hearts
  }
}
