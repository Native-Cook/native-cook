import Foundation
import simd

// Ring Box Reveal: the scene's fixed numbers and the film, ported from React Native's ringScene.ts and ringMotion.ts.
// glTF space: y up, metres, the camera in front (+z). One number drives the box, `open` 0 (shut) .. 1 (lid up); the
// camera's `shot` (0 the open box, 1 the ring, 2 the macro on the stone), the metal, the stone, the carat and the
// booking are film curves of t that a finger can take over. Every value is a pure function of t.
//
//  0.00 – 0.70  poster: the shut navy box on the paper, the camera drifting
//  0.70 – 0.88  anticipation: the lid presses down a hair
//  0.88 – 1.12  the hinge snaps to 104 deg (6 % past), a rigid haptic; glints burst at 0.98
//  1.12 – 1.60  it settles on its spring
//  1.15 – 2.35  the camera comes round to the front: the ring shot
//  2.35 / 3.05 / 3.75  the metal picker: yellow gold, rose gold, back to platinum
//  3.95 – 5.05  macro push on the stone (the box turns a little, 4.2 – 8.2)
//  4.75 / 5.45 / 6.15  the stone picker: sapphire, morganite, back to diamond
//  6.70 / 6.95  the carat slider steps to 1.75 and 2.00 ct; 7.75 / 8.00 back to 1.75 and 1.50
//  8.00 – 9.20  the camera pulls back to the open box
//  8.70         "Book a private viewing" is pressed: "Viewing requested" until 10.2
//  9.40 – 10.25 the lid closes; it lands with a tiny rebound (soft haptic)
// 10.47 – 11.5  poster; last frame = first frame
enum RingBoxRevealMotion {
  typealias V3 = SIMD3<Double>
  struct Shot { var eye: V3; var target: V3 }

  static let loop: Double = 11.5

  // MARK: scene (ringScene.ts)

  static let boxW = 0.05, boxD = 0.046, boxRV = 0.015, baseR = 0.0032, baseH = 0.025
  static let lidHinge = V3(0, 0.025, -0.023)
  static let lidOpen = 104 * Double.pi / 180
  static let ringY = 0.0238
  static let stoneC = V3(0, 0.0384, 0)
  static let stoneR = 0.0037
  static let stoneCrown = 0.0012
  static let stoneLocalY = 0.0146

  struct Metal { let name: String; let short: String; let color: UInt32; let rough: Double; let price: Double }
  static let metals: [Metal] = [
    Metal(name: "Platinum", short: "Platinum", color: 0xE5E4E2, rough: 0.08, price: 0),
    Metal(name: "Yellow gold", short: "Yellow", color: 0xF0C77F, rough: 0.1, price: -350),
    Metal(name: "Rose gold", short: "Rose", color: 0xEBB39B, rough: 0.1, price: -350),
  ]
  struct Stone { let name: String; let color: UInt32; let glow: Double; let price: Double }
  static let stones: [Stone] = [
    Stone(name: "Diamond", color: 0xF6F8FF, glow: 1.15, price: 0),
    Stone(name: "Sapphire", color: 0x2F56D8, glow: 1.5, price: -2400),
    Stone(name: "Morganite", color: 0xF3AE9C, glow: 1.2, price: -3900),
  ]
  static let caratMin = 1.0, caratMax = 2.0, caratStep = 0.25, caratModel = 1.5
  static let priceBase = 9_800.0, pricePerCt = 6_200.0

  static let hfovDeg = 30.0
  static let near = 0.004, far = 5.0

  static let poster = Shot(eye: V3(0.1, 0.114, 0.16), target: V3(0, 0.028, 0))
  static let openShot = Shot(eye: V3(0.094, 0.118, 0.158), target: V3(0, 0.034, 0))
  static let ringShot = Shot(eye: V3(0.03, 0.0663, 0.142), target: V3(0, 0.03, 0))
  static let macroShot = Shot(eye: V3(0.015, 0.0487, 0.0461), target: V3(0, 0.0366, 0))

  static let iblIntensity = 36_000.0
  static let keyDirection = V3(0.35, -0.85, -0.4)
  static let keyLux = 80_000.0
  static let fillDirection = V3(-0.7, -0.3, -0.6)
  static let fillLux = 16_000.0
  /// Where the glints look for light: unit vectors towards the light, and a strength.
  static let glintLights: [SIMD4<Double>] = [
    SIMD4(-0.356, 0.865, 0.407, 1.0),
    SIMD4(0.722, 0.31, 0.619, 0.45),
    SIMD4(-0.2, 0.55, 0.81, 0.8),
    SIMD4(0.55, 0.75, -0.37, 0.6),
  ]

  static let bgTop: UInt32 = 0xF3EFE8
  static let bgBottom: UInt32 = 0xE6E0D6
  static let ink: UInt32 = 0x1B1A18
  static let mute: UInt32 = 0x6A645B
  static let navy: UInt32 = 0x18213D

  static let atelier = "Atelier Mira"
  static let ringName = "Eloise Solitaire"

  // MARK: phases

  static let press = 0.7, snap = 0.88, snapEnd = 1.12, settleEnd = 1.6, burst = 0.98
  static let toRing = 1.15, toRingEnd = 2.35, toMacro = 3.95, toMacroEnd = 5.05
  static let turn = 4.2, turnEnd = 8.2, back = 8.0, backEnd = 9.2, book = 8.7, booked = 10.2
  static let close = 9.4, closeEnd = 10.25, landEnd = 10.47

  static let metalSteps = [2.35, 3.05, 3.75]
  static let stoneSteps = [4.75, 5.45, 6.15]
  static let caratSteps = [6.7, 6.95, 7.75, 8.0]
  static let caratAt = [1.0, 2.0, 1.0, 0.0]
  static let overshoot = 0.06
  static let blendS = 0.45
  static let caratS = 0.28
  static let idle = 2.0
  static let returnDuration = 2.0
  static let bookedTouch = 1.5

  // MARK: helpers

  static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : x > 1 ? 1 : x }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
  static func lerp(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
  static func easeInOutCubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
  static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
  static func easeOutQuart(_ x: Double) -> Double { 1 - pow(1 - x, 4) }
  static func easeInOutSine(_ x: Double) -> Double { -(cos(Double.pi * x) - 1) / 2 }
  static func easeInCubic(_ x: Double) -> Double { x * x * x }
  static func easeOutQuad(_ x: Double) -> Double { 1 - (1 - x) * (1 - x) }
  static func hash(_ i: Double) -> Double {
    let s = sin(i * 12.9898 + 78.233) * 43758.5453
    return s - floor(s)
  }
  /// Easing.bezier(0.65, 0, 0.35, 1).
  static func easeTouch(_ x: Double) -> Double {
    if x <= 0 { return 0 }
    if x >= 1 { return 1 }
    func b(_ s: Double, _ p1: Double, _ p2: Double) -> Double { 3 * (1 - s) * (1 - s) * s * p1 + 3 * (1 - s) * s * s * p2 + s * s * s }
    var lo = 0.0, hi = 1.0, s = x
    for _ in 0..<40 {
      s = (lo + hi) / 2
      if b(s, 0.65, 0.35) < x { lo = s } else { hi = s }
    }
    return b(s, 0, 1)
  }

  static func linear(_ hex: UInt32) -> V3 {
    func ch(_ s: UInt32) -> Double {
      let c = Double(hex >> s & 255) / 255
      return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4)
    }
    return V3(ch(16), ch(8), ch(0))
  }

  // MARK: the film

  static func filmOpen(_ t: Double) -> Double {
    if t < press { return 0 }
    if t < snap { return -0.012 * easeOutCubic(seg(t, press, snap)) }
    if t < snapEnd { return lerp(-0.012, 1 + overshoot, easeOutQuart(seg(t, snap, snapEnd))) }
    if t < settleEnd {
      let u = seg(t, snapEnd, settleEnd)
      return 1 + overshoot * exp(-6 * u) * cos(3 * Double.pi * u)
    }
    if t < close { return 1 }
    if t < closeEnd { return 1 - easeInOutCubic(seg(t, close, closeEnd)) }
    if t < landEnd { return 0.016 * sin(Double.pi * seg(t, closeEnd, landEnd)) }
    return 0
  }

  static func filmShot(_ t: Double) -> Double {
    if t < toMacro { return easeInOutSine(seg(t, toRing, toRingEnd)) }
    if t < back { return 1 + easeInOutSine(seg(t, toMacro, toMacroEnd)) }
    return 2 * (1 - easeInOutCubic(seg(t, back, backEnd)))
  }

  static func filmYaw(_ t: Double) -> Double {
    let u = seg(t, turn, turnEnd)
    let env = (1 - cos(2 * Double.pi * u)) / 2
    return 0.26 * sin(2 * Double.pi * u) * env
  }

  static func filmBurst(_ t: Double) -> Double {
    easeOutCubic(seg(t, burst, burst + 0.2)) * pow(1 - seg(t, burst + 0.2, burst + 1.5), 1.5)
  }

  /// A picker's film state from its home: (from offset, to offset, blend 0..1).
  static func filmSteps(_ t: Double, _ steps: [Double]) -> (Int, Int, Double) {
    var k = -1
    for (i, s) in steps.enumerated() where t >= s { k = i }
    if k < 0 { return (0, 0, 0) }
    return (k, k + 1, easeInOutSine(seg(t, steps[k], steps[k] + blendS)))
  }

  static func caratDir(_ home: Double) -> Double { home <= 1.5 ? 1 : -1 }

  static func filmCaratStep(_ t: Double, _ home: Double) -> Double {
    var at = 0.0
    for (i, s) in caratSteps.enumerated() where t >= s { at = caratAt[i] }
    return home + 0.25 * at * caratDir(home)
  }

  static func filmCarat(_ t: Double, _ home: Double) -> Double {
    var prev = 0.0, at = 0.0, k = -1
    for (i, s) in caratSteps.enumerated() where t >= s {
      prev = at
      at = caratAt[i]
      k = i
    }
    if k < 0 { return home }
    let e = easeOutCubic(seg(t, caratSteps[k], caratSteps[k] + caratS))
    return home + 0.25 * caratDir(home) * lerp(prev, at, e)
  }

  static func filmBooked(_ t: Double) -> Bool { t >= book && t < booked }

  static func sparkleOf(_ o: Double) -> Double { easeInOutSine(seg(o, 0.35, 0.85)) }

  // MARK: camera

  static func blendShot(_ a: Shot, _ b: Shot, _ k: Double) -> Shot {
    let az0 = atan2(a.eye.x, a.eye.z)
    var az1 = atan2(b.eye.x, b.eye.z)
    if az1 - az0 > Double.pi { az1 -= 2 * Double.pi }
    if az0 - az1 > Double.pi { az1 += 2 * Double.pi }
    let r0 = hypot(a.eye.x, a.eye.z), r1 = hypot(b.eye.x, b.eye.z)
    let az = lerp(az0, az1, k), r = lerp(r0, r1, k)
    return Shot(eye: V3(sin(az) * r, lerp(a.eye.y, b.eye.y, k), cos(az) * r),
                target: a.target + (b.target - a.target) * k)
  }

  static func cameraAt(_ o: Double, _ shot: Double, _ t: Double) -> Shot {
    var s = blendShot(poster, openShot, easeInOutSine(seg(o, 0, 1)))
    s = blendShot(s, ringShot, clamp01(shot))
    s = blendShot(s, macroShot, clamp01(shot - 1))
    let d = sin(2 * Double.pi * t / loop)
    let az = atan2(s.eye.x - s.target.x, s.eye.z - s.target.z) + 0.06 * d
    let r = hypot(s.eye.x - s.target.x, s.eye.z - s.target.z)
    s.eye.x = s.target.x + sin(az) * r
    s.eye.z = s.target.z + cos(az) * r
    s.eye.y += 0.002 * d
    return s
  }

  struct Basis { var f: V3, rx: Double, rz: Double, u: V3, focal: Double }

  static func basis(_ c: Shot, width: Double) -> Basis {
    var f = c.target - c.eye
    let fl = simd_length(f) == 0 ? 1 : simd_length(f)
    f /= fl
    var rx = -f.z, rz = f.x
    let rl = hypot(rx, rz) == 0 ? 1 : hypot(rx, rz)
    rx /= rl; rz /= rl
    return Basis(f: f, rx: rx, rz: rz, u: V3(-rz * f.y, rz * f.x - rx * f.z, rx * f.y),
                 focal: width / 2 / tan(hfovDeg * Double.pi / 360))
  }

  /// Screen px (x, y) and depth; nil when behind the lens.
  static func project(_ c: Shot, _ b: Basis, _ w: Double, _ h: Double, _ p: V3) -> V3? {
    let d = p - c.eye
    let z = simd_dot(d, b.f)
    if z < 0.005 { return nil }
    return V3(w / 2 + (d.x * b.rx + d.z * b.rz) / z * b.focal, h / 2 - simd_dot(d, b.u) / z * b.focal, z)
  }

  /// Facet i of the stone's crown: normal and position in stone space (every fifth facet is part of the table).
  static func facet(_ i: Int, _ radius: Double, _ crown: Double) -> (n: V3, p: V3) {
    let fi = Double(i)
    let table = i % 5 == 0
    let th = table ? 0.04 * hash(fi + 3) : 0.38 + 0.32 * hash(fi)
    let ph = 2 * Double.pi * fi / 40 + 0.35 * hash(fi + 7)
    let st = sin(th)
    let rr = table ? radius * 0.45 * hash(fi + 11) : radius * (0.62 + 0.3 * hash(fi + 13))
    return (V3(st * cos(ph), cos(th), st * sin(ph)),
            V3(cos(ph) * rr, table ? crown : crown * (1 - (rr / radius - 0.55) * 1.2), sin(ph) * rr))
  }

  /// A facet's glint for a view direction v (towards the eye) and normal n: the reflected ray against the lights.
  static func glint(_ n: V3, _ v: V3) -> Double {
    let d = simd_dot(n, v)
    if d <= 0 { return 0 }
    let r = 2 * d * n - v
    var s = 0.0
    for l in glintLights {
      let c = r.x * l.x + r.y * l.y + r.z * l.z
      if c > 0.9 { s += l.w * pow(c, 90) }
    }
    return s
  }

  static func priceOf(_ metal: Int, _ stone: Int, _ carat: Double) -> String {
    let ctFactor = stone == 0 ? 1 : 0.35
    let v = priceBase + metals[metal].price + stones[stone].price + (carat - caratModel) * pricePerCt * ctFactor
    let r = Int((v / 50).rounded()) * 50
    let f = NumberFormatter()
    f.numberStyle = .decimal
    f.groupingSeparator = ","
    return "$" + (f.string(from: NSNumber(value: r)) ?? "\(r)")
  }
}
