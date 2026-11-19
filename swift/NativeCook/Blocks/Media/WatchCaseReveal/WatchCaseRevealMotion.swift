import Foundation
import simd

// Watch Case Reveal: the scene's fixed numbers and the film, ported from React Native's watchScene.ts and
// watchMotion.ts. glTF space: y up, metres, the camera in front (+z). Every value is a pure function of the loop
// time t (and of `open`), so t = loop gives the same frame as t = 0.
//
//  0.0 –  1.2  poster: the closed walnut case, the camera drifting
//  1.2 –  1.36 the clasp lets go: the lid pops 2 deg (haptic)
//  1.36 – 3.7  the lid swings open on a damped hinge: quick to start, a long slow settle (soft haptic at rest)
//  3.7 –  4.7  the pillow rises 18 mm and tips the dial 15 deg to the lens (haptic at the top)
//  4.7 –  8.6  the watch turns to catch the light (left, right, home); the camera pushes in to the dial (5.4 – 6.8)
//              and pulls back (7.9 – 8.9); the dial picker tries every dial: green 5.5, silver 6.9, blue 8.2
//  8.5         "Reserve in a boutique" is pressed: it reads "Reserved" until 10.3
//  8.6 –  9.4  the pillow sinks back
//  9.4 – 11.3  the lid closes (soft haptic as it lands)
// 11.3 – 12.0  poster; last frame = first frame
enum WatchCaseRevealMotion {
  typealias V3 = SIMD3<Double>
  struct Shot { var eye: V3; var target: V3 }

  static let loop: Double = 12

  // MARK: scene (watchScene.ts)

  static let caseW = 0.124
  static let lidHinge = V3(0, 0.052, -0.056)
  static let lidOpen = 104 * Double.pi / 180
  static let cushionY = 0.031
  static let lift = 0.018
  static let tilt = 15 * Double.pi / 180
  static let watchY = 0.0184
  static let dialC = V3(0, watchY + 0.008, 0)
  static let dialR = 0.0162

  struct Dial { let name: String; let color: UInt32; let metal: Double; let rough: Double }
  static let dials: [Dial] = [
    Dial(name: "Blue", color: 0x173764, metal: 0.55, rough: 0.3),
    Dial(name: "Green", color: 0x1E4A3B, metal: 0.55, rough: 0.3),
    Dial(name: "Silver", color: 0xC3C7CC, metal: 0.65, rough: 0.28),
  ]

  static let hfovDeg = 30.0
  static let near = 0.01
  static let far = 10.0

  static let poster = Shot(eye: V3(0.12, 0.275, 0.325), target: V3(0, 0.028, 0))
  static let openShot = Shot(eye: V3(0.101, 0.323, 0.336), target: V3(0, 0.06, 0))
  static let turnShot = Shot(eye: V3(0.06, 0.202, 0.21), target: V3(0, 0.05, 0))
  static let macroShot = Shot(eye: V3(0.03, 0.153, 0.12), target: V3(0, 0.065, 0.004))

  /// Light (Filament units: lux, and the IBL's intensity). See WatchCaseRevealScene3D for the SceneKit mapping.
  static let iblIntensity = 30_000.0
  static let keyDirection = V3(0.4, -0.85, -0.35)
  static let keyLux = 72_000.0
  static let fillDirection = V3(-0.7, -0.3, -0.6)
  static let fillLux = 15_000.0

  static let bgTop: UInt32 = 0xF1EDE7
  static let bgBottom: UInt32 = 0xE1DAD0
  static let ink: UInt32 = 0x1A1917
  static let mute: UInt32 = 0x68625A

  static let brand = "Halden"
  static let model = "Arden 39"
  static let line = "Automatic, steel on calf leather"
  static let price = "$2,450"
  struct Spec { let value: Int; let unit: String; let label: String }
  static let specs: [Spec] = [
    Spec(value: 39, unit: "mm", label: "Case"),
    Spec(value: 70, unit: "h", label: "Power reserve"),
    Spec(value: 100, unit: "m", label: "Water resistant"),
  ]

  // MARK: phases

  static let latch = 1.2, swing = 1.36, swingEnd = 3.7, liftEnd = 4.7
  static let turnStart = 4.7, turnEnd = 8.6
  static let macroIn = 5.4, macroInEnd = 6.8, macroOut = 7.9, macroOutEnd = 8.9
  static let sinkEnd = 9.4, closeEnd = 11.3
  static let reserve = 8.5, reservedEnd = 10.3
  static let dialSteps: [Double] = [5.5, 6.9, 8.2]
  static let reservedTouch = 1.5
  static let dialBlend = 0.5
  static let idle = 2.0
  static let returnDuration = 2.0

  // MARK: helpers

  static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
  static func lerp(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
  static func easeInOutCubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
  static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
  static func easeInOutSine(_ x: Double) -> Double { -(cos(Double.pi * x) - 1) / 2 }

  /// A damped hinge released from rest (critically damped step response, normalised to land on 1).
  static func hinge(_ u: Double) -> Double {
    let x = clamp01(u) * 6
    let end = 1 - 7 * exp(-6.0)
    return (1 - (1 + x) * exp(-x)) / end
  }

  /// Cubic bezier easing (CSS-style control points), solved for x by Newton steps.
  static func bezier(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double, _ x: Double) -> Double {
    let x = clamp01(x)
    func bx(_ s: Double) -> Double { 3 * (1 - s) * (1 - s) * s * x1 + 3 * (1 - s) * s * s * x2 + s * s * s }
    func by(_ s: Double) -> Double { 3 * (1 - s) * (1 - s) * s * y1 + 3 * (1 - s) * s * s * y2 + s * s * s }
    var s = x
    for _ in 0..<8 {
      let d = (bx(s + 1e-4) - bx(s - 1e-4)) / 2e-4
      if abs(d) < 1e-6 { break }
      s = min(1, max(0, s - (bx(s) - x) / d))
    }
    return by(s)
  }
  static func easeInOutTouch(_ x: Double) -> Double { bezier(0.65, 0, 0.35, 1, x) }
  static func easeHinge(_ x: Double) -> Double { bezier(0.25, 0.8, 0.3, 1, x) }

  // MARK: the film

  static func filmOpen(_ t: Double) -> Double {
    if t < latch { return 0 }
    if t < swing { return 0.022 * easeOutCubic(seg(t, latch, swing)) }
    if t < swingEnd { return 0.022 + 0.978 * hinge(seg(t, swing, swingEnd)) }
    if t < liftEnd { return 1 + easeInOutCubic(seg(t, swingEnd, liftEnd)) }
    if t < turnEnd { return 2 }
    if t < sinkEnd { return 2 - easeInOutCubic(seg(t, turnEnd, sinkEnd)) }
    if t < closeEnd { return 1 - easeInOutCubic(seg(t, sinkEnd, closeEnd)) }
    return 0
  }

  /// The watch's turn (rad): left, right, home, with no speed at either end.
  static func filmYaw(_ t: Double) -> Double {
    let u = seg(t, turnStart, turnEnd)
    let env = (1 - cos(2 * Double.pi * u)) / 2
    let deg = -22 * sin(2 * Double.pi * u) * env * 1.6 + 4 * sin(Double.pi * u) * env
    return deg * Double.pi / 180
  }

  static func filmMacro(_ t: Double) -> Double {
    easeInOutSine(seg(t, macroIn, macroInEnd)) * (1 - easeInOutSine(seg(t, macroOut, macroOutEnd)))
  }

  /// The film's dial state from home: (from offset, to offset, blend 0..1), offsets modulo the three dials.
  static func filmDial(_ t: Double) -> (from: Int, to: Int, k: Double) {
    var k = -1
    for (i, s) in dialSteps.enumerated() where t >= s { k = i }
    if k < 0 { return (0, 0, 0) }
    return (k, k + 1, easeInOutSine(seg(t, dialSteps[k], dialSteps[k] + dialBlend)))
  }

  static func filmReserved(_ t: Double) -> Bool { t >= reserve && t < reservedEnd }

  // MARK: open -> the case's parts

  static func lidOf(_ o: Double) -> Double { clamp01(o) }
  static func liftOf(_ o: Double) -> Double { easeInOutSine(seg(o, 1, 2)) }
  static func tipOf(_ o: Double) -> Double { easeInOutSine(seg(o, 1.3, 2)) }
  static func specsOf(_ o: Double) -> Double { easeOutCubic(seg(o, 0.6, 1.9)) }

  // MARK: camera

  static func blendShot(_ a: Shot, _ b: Shot, _ k: Double) -> Shot {
    let az0 = atan2(a.eye.x, a.eye.z)
    var az1 = atan2(b.eye.x, b.eye.z)
    if az1 - az0 > Double.pi { az1 -= 2 * Double.pi }
    if az0 - az1 > Double.pi { az1 += 2 * Double.pi }
    let r0 = hypot(a.eye.x, a.eye.z), r1 = hypot(b.eye.x, b.eye.z)
    let az = lerp(az0, az1, k), r = lerp(r0, r1, k)
    return Shot(eye: V3(sin(az) * r, lerp(a.eye.y, b.eye.y, k), cos(az) * r),
                target: V3(lerp(a.target.x, b.target.x, k), lerp(a.target.y, b.target.y, k), lerp(a.target.z, b.target.z, k)))
  }

  /// Camera for a case state, the push to the dial (0..1) and the loop time.
  static func cameraAt(_ o: Double, _ macro: Double, _ t: Double) -> Shot {
    var c = blendShot(poster, openShot, easeInOutSine(seg(o, 0.15, 1.3)))
    c = blendShot(c, turnShot, easeInOutSine(seg(o, 1.1, 2)))
    c = blendShot(c, macroShot, clamp01(macro))
    let d = sin(2 * Double.pi * t / loop)
    let az = atan2(c.eye.x - c.target.x, c.eye.z - c.target.z) + 0.05 * d
    let r = hypot(c.eye.x - c.target.x, c.eye.z - c.target.z)
    c.eye.x = c.target.x + sin(az) * r
    c.eye.z = c.target.z + cos(az) * r
    c.eye.y += 0.004 * d
    return c
  }

  // MARK: projection (the stage camera's maths, for the 2D layers)

  struct Basis { var f: V3; var rx: Double; var rz: Double; var u: V3; var focal: Double }

  static func basis(_ c: Shot, width: Double) -> Basis {
    var f = c.target - c.eye
    let fl = simd_length(f); f = fl > 0 ? f / fl : V3(0, 0, -1)
    var rx = -f.z, rz = f.x
    let rl = hypot(rx, rz); if rl > 0 { rx /= rl; rz /= rl }
    let u = V3(-rz * f.y, rz * f.x - rx * f.z, rx * f.y)
    return Basis(f: f, rx: rx, rz: rz, u: u, focal: width / 2 / tan(hfovDeg * Double.pi / 360))
  }

  /// Screen point (pt) and depth of a world point, nil behind the camera.
  static func project(_ c: Shot, _ b: Basis, _ w: Double, _ h: Double, _ p: V3) -> (x: Double, y: Double, z: Double)? {
    let q = p - c.eye
    let z = simd_dot(q, b.f)
    if z < 0.01 { return nil }
    return (w / 2 + (q.x * b.rx + q.z * b.rz) / z * b.focal, h / 2 - simd_dot(q, b.u) / z * b.focal, z)
  }

  /// A point in cushion space to world space: tipped about x, turned about y, lifted.
  static func cushionToWorld(_ q: V3, tilt: Double, yaw: Double, y: Double) -> V3 {
    let ct = cos(tilt), st = sin(tilt)
    let x1 = q.x, y1 = q.y * ct - q.z * st, z1 = q.y * st + q.z * ct
    let cy = cos(yaw), sy = sin(yaw)
    return V3(x1 * cy + z1 * sy, y1 + y, -x1 * sy + z1 * cy)
  }

  /// The second hand's angle (rad) for clock seconds: one tick a second, an 80 ms ease-out cubic.
  static func secondAngle(_ secs: Double) -> Double {
    let whole = floor(secs)
    let frac = clamp01((secs - whole) / 0.08)
    return (whole + (1 - pow(1 - frac, 3))) * Double.pi / 30
  }

  static func linear(_ hex: UInt32) -> SIMD3<Double> {
    func ch(_ v: UInt32) -> Double {
      let c = Double(v & 255) / 255
      return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4)
    }
    return SIMD3(ch(hex >> 16), ch(hex >> 8), ch(hex))
  }
}

/// Everything the 3D stage shows in one frame.
struct WatchCaseRevealPose: Equatable {
  var t: Double = 0
  var open: Double = 0
  var yaw: Double = 0
  var macro: Double = 0
  var dialFrom: Int = 0
  var dialTo: Int = 0
  var dialK: Double = 0
  var secs: Double = 0

  static func film(_ t: Double, home: Int) -> WatchCaseRevealPose {
    typealias M = WatchCaseRevealMotion
    let d = M.filmDial(t)
    let n = M.dials.count
    return WatchCaseRevealPose(t: t, open: M.filmOpen(t), yaw: M.filmYaw(t), macro: M.filmMacro(t),
                               dialFrom: (home + d.from) % n, dialTo: (home + d.to) % n, dialK: d.k, secs: t)
  }
}
