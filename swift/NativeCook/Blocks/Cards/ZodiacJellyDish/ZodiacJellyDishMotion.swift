import Foundation

// Double arithmetic and semi-implicit Euler match the JS worklet, including 240 Hz substeps.
enum ZodiacJellyDishMotion {
  static let loop = 8.0
  static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t-a)/(b-a)) }
  static func smooth(_ k: Double) -> Double { let x = clamp(k); return x*x*(3-2*x) }
  static func ease(_ k: Double) -> Double {
    let x = clamp(k)
    return x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2, 3)/2
  }
  struct Jelly {
    var px = 0.0, py = 0.0, vx = 0.0, vy = 0.0, sq = 0.0, sv = 0.0
    var gx = 92.0, gy = 150.0, hold = false, tx = 0.0, ty = 0.0
    mutating func step(_ dt: Double) {
      let n = max(1, Int(ceil(dt/(1.0/240))))
      let h = dt/Double(n)
      for _ in 0..<n {
        let k = hold ? 420.0 : 210.0, c = hold ? 40.0 : 4.2
        vx += (((hold ? tx : 0)-px)*k-vx*c)*h
        vy += (((hold ? ty : 0)-py)*k-vy*c)*h
        px += vx*h; py += vy*h
        sv += (-sq*260-sv*6)*h
        sq += sv*h
      }
    }
    mutating func script(_ before: Double, _ t: Double) {
      if t >= 1.1 && t < 1.75 {
        let k = ZodiacJellyDishMotion.ease(ZodiacJellyDishMotion.seg(t, 1.1, 1.75))
        hold = true; gx = 92; gy = 150; tx = 26*k; ty = 34*k
      } else if before < 1.75 && t >= 1.75 { hold = false }
      if before < 4.95 && t >= 4.95 { sv += 1.6 }
    }
    static func replay(_ time: Double) -> Self {
      var j = Self(), t = 0.0
      let end = max(0, time.truncatingRemainder(dividingBy: loop))
      while t < end {
        let dt = min(1.0/60, end-t), next = t+min(1.0/60, end-t)
        j.script(t, next); j.step(dt); t = next
      }
      return j
    }
  }
  struct Frame {
    var t: Double = 0
    var jelly = Jelly()
    var clock = 0.0, addedAt = -10.0
    var buyPress = 0.0, savePress = 0.0
    var swirl: Double { ease(seg(t, 2.9, 4.5)) }
    var lx: Double { -0.55+sin(swirl * .pi)*1.3+jelly.px/70 }
    var ly: Double { -0.8+jelly.py/90 }
    var pulse: Double {
      let a = t >= 4.95 ? exp(-(t-4.95)*6)*smooth(seg(t, 4.95, 5)) : 0
      let dt = clock-addedAt
      return max(a, dt >= 0 && dt < 1 ? exp(-dt*6) : 0)
    }
    var added: Double {
      let a = smooth(seg(t, 5, 5.25))*(1-smooth(seg(t, 6.45, 6.7)) )
      let dt = clock-addedAt
      return max(a, dt >= 0 && dt < 1.8 ? smooth(dt/0.25)*(1-smooth((dt-1.55)/0.25)) : 0)
    }
    var buyScale: Double {
      let a = t >= 4.75 && t < 5.15 ? sin(seg(t, 4.75, 5.15) * .pi) : 0
      return 1-max(buyPress, a)*0.04
    }
  }
  // Reanimated duration spring: mass 4, energy threshold 6e-9, stiffness bisection
  // on [Number.EPSILON, 8000], and a 1.5 perceptual-duration coefficient.
  struct Press {
    var from = 0.0, velocity = 0.0, aim = 0.0, at = 0.0
    var omega = 1.0, zeta = 1.0, initialEnergy = 0.0
    func sample(_ now: Double) -> (Double, Double) {
      let t = max(0, now-at), x = from-aim
      let envelope = exp(-zeta*omega*t)
      if zeta >= 1 {
        let b = velocity+omega*x
        let p = (x+b*t)*envelope, v = (velocity-omega*b*t)*envelope
        return terminates(p,v) ? (aim,0) : (aim+p,v)
      }
      let wd = omega*sqrt(1-zeta*zeta), b = (velocity+zeta*omega*x)/wd
      let displacement = x*cos(wd*t)+b*sin(wd*t)
      let p = envelope*displacement
      let v = envelope*((-x*wd*sin(wd*t)+b*wd*cos(wd*t))-zeta*omega*displacement)
      return terminates(p,v) ? (aim,0) : (aim+p,v)
    }
    private func terminates(_ x: Double, _ v: Double) -> Bool {
      initialEnergy == 0 || (0.5*4*omega*omega*x*x+0.5*4*v*v)/initialEnergy <= 6e-9
    }
    mutating func set(_ target: Double, _ now: Double, _ reduced: Bool = false) {
      let current = sample(now)
      from = current.0; velocity = current.1; aim = target; at = now
      if reduced { from = target; velocity = 0; initialEnergy = 0; return }
      zeta = target == 1 ? 1 : 0.7
      let duration = (target == 1 ? 0.150 : 0.320)*1.5
      if (target > from && velocity < 0) || (target < from && velocity > 0) { velocity = 0 }
      let x = from-target
      if x == 0 && velocity == 0 { omega = 1; initialEnergy = 0; return }
      func difference(_ stiffness: Double) -> Double {
        let w = sqrt(stiffness/4)*zeta
        let e = exp(-w*duration)
        let xt = (x+(velocity+x*w)*duration)*e
        let vt = -w*xt+(velocity+x*w)*e
        return (0.5*stiffness*xt*xt+2*vt*vt)/(0.5*stiffness*x*x+2*velocity*velocity)-6e-9
      }
      var low = Double.ulpOfOne, high = 8000.0
      let direction = difference(high) >= difference(low) ? 1.0 : -1.0
      var stiffness = (low+high)/2
      for _ in 0..<100 {
        if abs(difference(stiffness)) <= 6e-12 { break }
        if difference(stiffness)*direction < 0 { low = stiffness } else { high = stiffness }
        stiffness = (low+high)/2
      }
      omega = sqrt(stiffness/4)
      initialEnergy = 0.5*stiffness*x*x

    }
  }
}
