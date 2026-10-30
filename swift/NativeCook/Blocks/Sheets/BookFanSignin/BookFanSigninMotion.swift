import Foundation

// Analytic timeline and touch driver, ported from bfs-motion.ts without native spring substitution.
enum BookFanSigninMotion {
  static let LOOP = 9.0, RESUME = 2.0, PEAK = 3.4
  enum T {
    static let press = [1.3, 1.45], open = 1.45, blur = [1.45, 2.05]
    static let sheet = 1.55, flight = 1.5, icon = 1.95
    static let logo = [2.05, 2.55], tag = [2.1, 2.45], title = [2.15, 2.55]
    static let title2 = [2.25, 2.65], sub = [2.35, 2.75], cta = [2.4, 2.85]
    static let shimmer = [3.0, 3.7], close = 6.6, closeEnd = 7.5
  }
  static let FLIGHTS = 6, FLIGHT_STAGGER = 0.05, FLIGHT = 0.68
  static let BACK_STAGGER = 0.04, BACK = 0.55, SHEET_DROP = 0.45
  static let angle = -27.0, px = 69.0, py = 96.0
  static let rad = angle * .pi / 180
  static let U = (x: cos(rad) * px, y: sin(rad) * px)
  static let V = (x: -sin(rad) * py, y: cos(rad) * py)
  static let FLYING: [(i: Int, j: Int)] = [(1, 3), (2, 2), (3, 3), (4, 2), (5, 3), (6, 2)]
  static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
  static func mix(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }
  static func easeOut(_ x: Double) -> Double {
    let u = 1 - clamp01(x)
    return 1 - u * u * u * u * u
  }
  static func easeIn(_ x: Double) -> Double { pow(clamp01(x), 3) }
  static func easeInOutCubic(_ x: Double) -> Double {
    let p = clamp01(x)
    return p < 0.5 ? 4 * p * p * p : 1 - pow(-2 * p + 2, 3) / 2
  }
  static func smooth(_ x: Double) -> Double {
    let p = clamp01(x)
    return p * p * (3 - 2 * p)
  }
  static func spring(_ tau: Double, _ response: Double, _ damping: Double) -> Double {
    if tau <= 0 { return 0 }
    let w = 2 * Double.pi / response
    let zw = damping * w
    let wd = w * sqrt(1 - damping * damping)
    return 1 - exp(-zw * tau) * (cos(wd * tau) + (zw / wd) * sin(wd * tau))
  }
  static func wallShift(_ amb: Double, _ extra: Double) -> Double {
    let s = -(amb / LOOP) * py + extra
    return ((s.truncatingRemainder(dividingBy: py) + py).truncatingRemainder(dividingBy: py)) - py
  }
  static func cellAt(_ i: Int, _ j: Int, _ shift: Double) -> (x: Double, y: Double) {
    let ci = Double(i) - 3
    let cj = Double(j) - 2.5
    let k = shift / py
    return (214 + ci * U.x + (cj + k) * V.x, 176 + ci * U.y + (cj + k) * V.y)
  }
  static func coverOf(_ i: Int, _ j: Int) -> Int { (i * 3 + j * 7) % 10 }
  static func slot(_ k: Int) -> (x: Double, y: Double, angle: Double) {
    (52 + Double(k) * 76, 476 - Double(k) * 5, -17)
  }
  static func flightAt(_ k: Int, _ t: Double) -> Double {
    if t < T.close {
      let s = T.flight + Double(k) * FLIGHT_STAGGER
      return easeInOutCubic(seg(t, s, s + FLIGHT))
    }
    let s = T.close + Double(FLIGHTS - 1 - k) * BACK_STAGGER
    return 1 - easeInOutCubic(seg(t, s, s + BACK))
  }
  struct Frame {
    var t, press, blur, dim, sheet: Double
    var flights: [Double]
    var icon, logo, tag, title, title2, sub, cta, shimmer: Double
  }
  static func frameAt(_ t: Double) -> Frame {
    let fwd = t < T.close
    let back = seg(t, T.close, T.closeEnd)
    func out(_ x: Double) -> Double { fwd ? x : x * (1 - easeOut(back * 2)) }
    func entrance(_ range: [Double]) -> Double { out(easeOut(seg(t, range[0], range[1]))) }
    let press =
      t >= T.press[0] && t < T.press[1] + 0.14
      ? easeOut(seg(t, T.press[0], T.press[0] + 0.1))
        * (1 - easeOut(seg(t, T.press[1], T.press[1] + 0.14))) : 0
    let sh = seg(t, T.shimmer[0], T.shimmer[1])
    return Frame(
      t: t, press: press,
      blur: fwd
        ? smooth(seg(t, T.blur[0], T.blur[1])) : 1 - smooth(seg(t, T.close + 0.15, T.closeEnd)),
      dim: fwd
        ? smooth(seg(t, T.blur[0], T.blur[1]))
        : 1 - smooth(seg(t, T.close + 0.1, T.closeEnd - 0.1)),
      sheet: fwd
        ? spring(t - T.sheet, 0.55, 0.82) : 1 - easeIn(seg(t, T.close, T.close + SHEET_DROP)),
      flights: (0..<FLIGHTS).map { flightAt($0, t) }, icon: out(spring(t - T.icon, 0.45, 0.6)),
      logo: out(easeInOutCubic(seg(t, T.logo[0], T.logo[1]))), tag: entrance(T.tag),
      title: entrance(T.title),
      title2: entrance(T.title2), sub: entrance(T.sub), cta: entrance(T.cta),
      shimmer: sh > 0 && sh < 1 ? sh : -1)
  }
  static func inHome(_ t: Double) -> Bool { t < T.press[0] || t >= T.closeEnd }
  static func inSheet(_ t: Double) -> Bool { t >= PEAK - 0.6 && t < T.close }
  struct Driver {
    var t = 0.0, mode = 0, stop = 0.0, rate = 1.0, last = -10.0, amb = 0.0
    var scroll = 0.0, scrollV = 0.0, drag = 0.0, dragV = 0.0, dragging = 0
    var ctaAim = 0.0, ctaP = 0.0, pressAim = 0.0, pressP = 0.0
    mutating func step(_ dt: Double, _ now: Double, _ auto: Bool) {
      if mode == 1 {
        t += dt * rate
        if t >= stop {
          t = stop >= LOOP ? 0 : stop
          mode = 2
          rate = 1
          last = now
        }
      } else if mode == 2 {
        if auto && dragging == 0 && now - last >= RESUME { mode = 0 }
      } else if auto {
        t = (t + dt).truncatingRemainder(dividingBy: LOOP)
      }
      amb = (amb + dt).truncatingRemainder(dividingBy: LOOP)
      if dragging == 0 {
        scroll += scrollV * dt
        scrollV *= exp(-dt * 2.4)
      }
      if dragging == 0 && drag != 0 {
        let k = 260.0
        let c = 2 * 0.9 * sqrt(k)
        dragV += (-k * drag - c * dragV) * dt
        drag += dragV * dt
        if abs(drag) < 0.05 && abs(dragV) < 1 {
          drag = 0
          dragV = 0
        }
      }
      ctaP += (ctaAim - ctaP) * (1 - exp(-dt * (ctaAim > 0 ? 40 : 16)))
      pressP += (pressAim - pressP) * (1 - exp(-dt * (pressAim > 0 ? 40 : 16)))
    }
    mutating func touched(_ now: Double) {
      last = now
      if mode != 0 { return }
      rate = 1
      if t >= T.press[0] && t < PEAK {
        mode = 1
        stop = PEAK
      } else if t >= T.close && t < T.closeEnd {
        mode = 1
        stop = LOOP
      } else {
        mode = 2
      }
    }
    mutating func openSheet() {
      t = T.open
      mode = 1
      rate = 1
      stop = PEAK
    }
    @discardableResult mutating func closeSheet() -> Bool {
      guard inSheet(t) || (t >= PEAK && t < T.close) else { return false }
      t = T.close
      mode = 1
      rate = 1.4
      stop = LOOP
      return true
    }
  }
}
