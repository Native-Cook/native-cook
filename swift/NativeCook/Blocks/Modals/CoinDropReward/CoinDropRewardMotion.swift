import Foundation

enum CoinDropRewardMotion {
  static let loop = 9.0
  static let pullFull = 0.32
  static let cooldown = 4.0
  static let reward = 5
  static let launchTime = 0.95
  static let release = 3.8
  static let absorb = 4.35
  static let launchSeconds = 0.6
  static let flySeconds = absorb - release
  static func clamp01(_ v: Double) -> Double { min(1, max(0, v)) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t-a)/(b-a)) }
  static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
    let u = clamp01((x-a)/(b-a)); return u*u*(3-2*u)
  }
  static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2,3)/2 }
  static func easeOut(_ x: Double) -> Double { 1-pow(1-x,3) }
  static func easeIn(_ x: Double) -> Double { x*x*x }
  static func backOut(_ x: Double) -> Double {
    let u = clamp01(x)-1; return 1+2.5*u*u*u+1.5*u*u
  }
  static func wobbleAt(_ u: Double) -> Double { u <= 0 || u >= 1 ? 0 : exp(-u*5)*sin(u*26) }
  static func meltOf(_ pull: Double) -> Double { 0.8*smooth(0,1,pull/pullFull) }
  static func frameAt(_ time: Double) -> CoinDropRewardFrame {
    let t = time
    let launch = t < launchTime ? 0 : backOut(seg(t,launchTime,launchTime+launchSeconds))
    let flip = easeInOut(seg(t,1.6,2.4))
    let held = pullFull*easeInOut(seg(t,2.7,3.5))
    let fly = easeIn(seg(t,release,absorb))*0.55+0.45*easeInOut(seg(t,release,absorb))
    let after = t-absorb
    let out = t >= launchTime && t < absorb
    func near(_ a: Double, _ b: Double) -> Double {
      easeOut(seg(t,a-0.25,a))*(1-easeOut(seg(t,b,b+0.2)))
    }
    return CoinDropRewardFrame(
      launch: launch, flip: flip, pull: held*(1-fly),
      melt: t < release ? meltOf(held) : 0.8+0.2*easeOut(seg(t,release,release+0.3)),
      fly: fly, trail: smooth(release,absorb-0.1,t),
      toast: smooth(launchTime,launchTime+0.35,t)*(1-smooth(release+0.1,release+0.45,t)),
      text: smooth(launchTime+0.3,launchTime+0.6,t)*(1-smooth(release,release+0.25,t)),
      wobbleU: t < absorb ? 1 : seg(t,absorb,absorb+0.7),
      plus: t < absorb ? 1 : seg(t,absorb,absorb+0.9),
      coinVis: out ? 0 : after > 0 ? 0.35+0.65*smooth(cooldown,cooldown+0.25,after) : 1,
      cooldown: after > 0 ? max(0,cooldown-after) : 0,
      press: easeOut(seg(t,0.72,0.8))*(1-easeOut(seg(t,0.86,1.02))),
      finger: clamp01(near(0.75,0.95)+near(2.55,release)),
      onCoin: t > (0.8+2.55)/2 ? 1 : 0, fingerPull: held, out: out)
  }
  // Invert cubic-bezier x, then evaluate y, as Reanimated's Easing.bezier does.
  static func bezier(_ x: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
    let x = clamp01(x)
    if x == 0 || x == 1 { return x }
    func cubic(_ u: Double, _ a: Double, _ b: Double) -> Double {
      3*(1-u)*(1-u)*u*a+3*(1-u)*u*u*b+u*u*u
    }
    var lo = 0.0, hi = 1.0
    for _ in 0..<28 { let u = (lo+hi)/2; if cubic(u,x1,x2) < x { lo = u } else { hi = u } }
    return cubic((lo+hi)/2,y1,y2)
  }
}

struct CoinDropRewardFrame {
  var launch = 0.0, flip = 0.0, pull = 0.0, melt = 0.0, fly = 0.0, trail = 0.0
  var toast = 0.0, text = 0.0, wobbleU = 1.0, plus = 1.0, coinVis = 1.0, cooldown = 0.0
  var press = 0.0, finger = 0.0, onCoin = 0.0, fingerPull = 0.0
  var out = false
}

// Duration-based Reanimated 4 spring: solve the same energy equation, then evaluate analytically.
struct CoinDropRewardTween {
  enum Curve { case linear, standard, out, inOut, pour, spring(Double) }
  var from: Double, to: Double, start: Double, duration: Double
  var curve: Curve = .linear
  var omega = 0.0
  init(_ from: Double, _ to: Double, _ start: Double, _ duration: Double, _ curve: Curve = .linear) {
    self.from = from; self.to = to; self.start = start; self.duration = duration; self.curve = curve
    if case .spring(let zeta) = curve {
      var lo = Double.ulpOfOne, hi = 8000.0
      let settling = duration*1.5
      for _ in 0..<100 {
        let stiffness = (lo+hi)/2, w = sqrt(stiffness/4)*zeta
        let displacement = (1+w*settling)*exp(-w*settling)
        let velocity = -w*w*settling*exp(-w*settling)
        let fraction = displacement*displacement+4*velocity*velocity/stiffness
        if abs(fraction-6e-9) <= 6e-12 { lo = stiffness; hi = stiffness; break }
        if fraction > 6e-9 { lo = stiffness } else { hi = stiffness }
      }
      omega = sqrt((lo+hi)/8)
    }
  }
  func value(_ now: Double) -> Double {
    let t = max(0,now-start)
    if case .spring(let zeta) = curve {
      let w1 = omega*sqrt(1-zeta*zeta)
      let envelope = exp(-zeta*omega*t)
      let d = envelope*(cos(w1*t)+zeta*omega/w1*sin(w1*t))
      let v = -envelope*omega*omega/w1*sin(w1*t)
      return d*d+v*v/(omega*omega) <= 6e-9 ? to : to+(from-to)*d
    }
    let u = duration <= 0 ? 1 : CoinDropRewardMotion.clamp01(t/duration)
    let eased: Double
    switch curve {
    case .linear: eased = u
    case .standard: eased = u < 0.5 ? 2*u*u : 1-pow(-2*u+2,2)/2
    case .out: eased = CoinDropRewardMotion.bezier(u,0.23,1,0.32,1)
    case .inOut: eased = CoinDropRewardMotion.bezier(u,0.77,0,0.175,1)
    case .pour: eased = CoinDropRewardMotion.bezier(u,0.55,0,0.75,0.2)
    case .spring: eased = u
    }
    return from+(to-from)*eased
  }
}
