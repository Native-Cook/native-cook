import Foundation

enum TrophyFanCaseMotion {
  static let loop = 8.0
  static let count = 4
  static let width = 150.0
  static let height = 228.0
  static let clear = 190.0
  static let pass = 172.0
  static let drop = 70.0

  struct Pose {
    var x = 0.0, y = 0.0, r = 0.0, s = 1.0, f = 0.0
  }
  struct Body {
    var x = 0.0, y = 0.0, r = 0.0, s = 1.0, f = 0.0
    var vx = 0.0, vy = 0.0, vr = 0.0, vs = 0.0, vf = 0.0
    // 0 home; 1 rises behind neighbours; 2 free; 3 returns above; 4 slides behind.
    var ph = 0
  }
  struct Hand {
    var cards = Array(repeating: Body(), count: 4)
    var spread = 0.0, vspread = 0.0
    var focus = -9.0, vfocus = 0.0, focusOn = 0.0, vfocusOn = 0.0
    var sel = -1, held = -1
    var hx = 0.0, hy = 0.0
    var top: Int { cards.indices.last(where: { cards[$0].ph == 2 || cards[$0].ph == 3 }) ?? -1 }
  }
  struct Want {
    var spread = 0.0, focus = -9.0, focusOn = 0.0
    var sel = -1
  }
  struct Layout: Equatable {
    var width: Double
    var top: Double
    var cx: Double { width / 2 }
    var fy: Double { top + 352 }
    var sy: Double { top + 336 }
    var big: Double { min(1.55, (width - 56) / TrophyFanCaseMotion.width) }
  }

  static func clamp01(_ x: Double) -> Double { max(0, min(1, x)) }
  static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
  static func smooth(_ k: Double) -> Double {
    let x = clamp01(k)
    return x * x * (3 - 2 * x)
  }
  static func script(_ t: Double) -> Want {
    let k = clamp01((t - 1.5) / (2.6 - 1.5))
    let scrubbing = t >= 1.5 - 0.1 && t < 2.6 + 0.25
    return Want(
      spread: t >= 0.45 && t < 6.45 ? 1 : 0,
      focus: scrubbing ? mix(-0.4, 3.4, smooth(k)) : -9,
      focusOn: scrubbing ? 1 : 0,
      sel: t >= 2.85 && t < 5.25 ? 1 : -1)
  }
  static func home(_ i: Int, _ spread: Double, _ focus: Double, _ focusOn: Double, _ l: Layout) -> Pose {
    let c = Double(i) - 1.5
    let a = c * 11 * spread
    let rad = a * .pi / 180
    let lift = focusOn * max(0, 1 - abs(focus - Double(i)) * 1.5)
    return Pose(
      x: l.cx + mix(c * 5, 520 * sin(rad), spread),
      y: l.fy + mix(abs(c) * 3, 520 * (1 - cos(rad)), spread) - 24 * lift,
      r: mix(c * 1.6, a, spread),
      s: mix(0.97, 1, spread) * (1 + 0.04 * lift), f: 0)
  }
  static func spring(_ p: Double, _ v: Double, _ target: Double, _ k: Double, _ z: Double, _ dt: Double) -> (Double, Double) {
    let c = 2 * sqrt(k) * z
    let n = max(1, Int(ceil(dt / (1.0 / 240))))
    let h = dt / Double(n)
    var x = p, vv = v
    for _ in 0..<n {
      vv += (k * (target - x) - c * vv) * h
      x += vv * h
    }
    return (x, vv)
  }
  static func settled(_ l: Layout, _ reduced: Bool = false) -> Hand {
    var hd = Hand()
    hd.spread = reduced ? 1 : 0
    for i in 0..<count {
      let p = home(i, hd.spread, -9, 0, l)
      hd.cards[i] = Body(x: p.x, y: p.y, r: p.r, s: p.s, f: 0)
    }
    return hd
  }
  static func step(_ hd: inout Hand, _ dt: Double, _ want: Want, _ l: Layout) {
    (hd.spread, hd.vspread) = spring(hd.spread, hd.vspread, want.spread, 90, 0.85, dt)
    if want.focusOn > 0 && hd.focusOn < 0.02 {
      hd.focus = want.focus
      hd.vfocus = 0
    }
    if want.focusOn > 0 {
      (hd.focus, hd.vfocus) = spring(hd.focus, hd.vfocus, want.focus, 260, 1, dt)
    }
    (hd.focusOn, hd.vfocusOn) = spring(hd.focusOn, hd.vfocusOn, want.focusOn, 220, 0.9, dt)
    hd.sel = want.sel
    let away = hd.sel >= 0 || hd.held >= 0 || hd.cards.contains { $0.ph == 1 || $0.ph == 2 || $0.ph == 3 }
    for i in 0..<count {
      var b = hd.cards[i]
      let p = home(i, hd.spread, hd.focus, hd.focusOn, l)
      if hd.held == i {
        if b.ph != 2 { b.ph = b.y < p.y - pass ? 2 : 1 }
        b.vx = dt > 0 ? b.vx * 0.5 + ((hd.hx - b.x) / dt) * 0.5 : b.vx
        b.vy = dt > 0 ? b.vy * 0.5 + ((hd.hy - b.y) / dt) * 0.5 : b.vy
        b.x = hd.hx
        b.y = hd.hy
        (b.r, b.vr) = spring(b.r, b.vr, max(-14, min(14, b.vx * 0.012)), 300, 0.8, dt)
        (b.s, b.vs) = spring(b.s, b.vs, 1.08, 300, 0.8, dt)
        hd.cards[i] = b
        continue
      }
      if hd.sel == i {
        if b.ph == 0 || b.ph == 4 { b.ph = 1 }
        else if b.ph == 3 { b.ph = 2 }
      } else if b.ph == 2 { b.ph = 3 }
      else if b.ph == 1 { b.ph = 0 }
      if b.ph == 1 && b.y < p.y - pass { b.ph = 2 }
      if b.ph == 3 && abs(b.x - p.x) < 24 && abs(b.f) < 25 && b.y < p.y - pass { b.ph = 4 }
      if b.ph == 4 && hypot(b.x - p.x, b.y - p.y) < 3 { b.ph = 0 }

      var tx = p.x, ty = p.y, tr = p.r, ts = p.s, tf = 0.0, k = 150.0
      if b.ph == 1 || b.ph == 3 {
        ty = p.y - clear
        tr = p.r * (b.ph == 1 ? 0.4 : 1)
        ts = 1.04
        k = 170
      } else if b.ph == 2 {
        tx = l.cx; ty = l.sy; tr = 0; ts = l.big; k = 120
        tf = hypot(b.x - l.cx, b.y - l.sy) < 70 ? 180 : 0
      } else if away {
        ty += drop
        ts *= 0.92
      }
      (b.x, b.vx) = spring(b.x, b.vx, tx, k, 0.82, dt)
      (b.y, b.vy) = spring(b.y, b.vy, ty, k, 0.82, dt)
      (b.r, b.vr) = spring(b.r, b.vr, tr, k, 0.82, dt)
      (b.s, b.vs) = spring(b.s, b.vs, ts, k, 0.82, dt)
      (b.f, b.vf) = spring(b.f, b.vf, tf, 110, 0.86, dt)
      hd.cards[i] = b
    }
  }
  // Identical to __trophyFan.at(t): no modulo, reset, or analytic spring approximation.
  static func at(_ t: Double, _ l: Layout, _ reduced: Bool = false) -> Hand {
    var hd = settled(l, reduced)
    var st = 0.0
    while st < t {
      let d = min(1.0 / 120, t - st)
      step(&hd, d, script(st), l)
      st += d
    }
    return hd
  }
  static func cardAt(_ hd: Hand, _ x: Double, _ y: Double) -> Int {
    let order = (hd.top >= 0 ? [hd.top] : []) + [3, 2, 1, 0]
    for i in order {
      let b = hd.cards[i]
      let a = -b.r * .pi / 180
      let dx = x - b.x, dy = y - b.y
      let lx = dx * cos(a) - dy * sin(a), ly = dx * sin(a) + dy * cos(a)
      if abs(lx) < width / 2 * b.s && abs(ly) < height / 2 * b.s { return i }
    }
    return -1
  }
}
