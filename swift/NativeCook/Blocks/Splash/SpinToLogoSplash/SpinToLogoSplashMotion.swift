import Foundation
import simd

// Spin to Logo Splash: the scene's fixed numbers and the film, ported from React Native's spinScene.ts and
// spinMotion.ts. glTF space: y up, the outer arch's radius is 1, the arches stand on their feet at y = 0 round the
// vertical axis through the origin. Every film value is a pure function of the loop time t, so t = loop gives the
// same frame as t = 0; the turn of the sculpture (yaw, degrees) is a separate value a finger can own.
//
//  0.0 –  1.25 poster: the sculpture still, the floor track empty, "Give it a full turn." above
//  1.25 – 3.45 a ghost finger drags left to right (accelerating), lets go; the turn decelerates: 0 -> 181 deg
//  3.3  – 5.0  a second flick takes it to exactly 360 deg; the track closes and pops
//  5.1  – 6.5  the fold: the camera sinks to the front; the middle and inner arches swing round with an overshoot;
//              the pearl drops onto the baseline and bounces once
//  6.6  – 7.15 hand-off: the flat 2D mark fades in over the 3D one, the 3D fades out; a gleam crosses it
//  7.45 – 9.1  the mark rises and shrinks to the top, "arca" sets letter by letter, the welcome screen fades up
//  9.1  – 11.4 hold
// 11.4  – 14.0 everything in reverse back to the poster. Last frame = first frame.
enum SpinToLogoSplashMotion {
  typealias V3 = SIMD3<Double>
  struct Shot { var eye: V3; var target: V3 }

  static let loop: Double = 14

  // MARK: scene (spinScene.ts)

  struct Arch { let name: String; let r: Double; let a: Double; let b: Double }
  static let arches: [Arch] = [
    Arch(name: "ArchOuter", r: 1.0, a: 0.092, b: 0.115),
    Arch(name: "ArchMiddle", r: 0.74, a: 0.086, b: 0.108),
    Arch(name: "ArchInner", r: 0.49, a: 0.08, b: 0.1),
  ]
  static let restYaw: [Double] = [0, 60, -60]
  static let pearlR = 0.17
  static let pearlFloat = 0.62
  static let trackR = 1.16

  static let hfovDeg = 26.0
  static let near = 0.1
  static let far = 40.0

  static let poster = Shot(eye: V3(0, 1.655, 5.246), target: V3(0, 0, 0))
  static let front = Shot(eye: V3(0, 0.42, 5.9), target: V3(0, 0.3, 0))

  static let iblIntensity = 32_000.0
  static let keyDirection = V3(-0.45, -0.8, -0.4)
  static let keyLux = 70_000.0
  static let fillDirection = V3(0.6, -0.25, -0.75)
  static let fillLux = 14_000.0
  /// Exposure stops of Filament's colour grading (a 2^EXPOSURE gain before the tone curve).
  static let exposureStops = 0.15

  static let paperTop: UInt32 = 0xF4F1EC
  static let paperMid: UInt32 = 0xEFEBE4
  static let paperBottom: UInt32 = 0xE6E1D9
  static let ink: UInt32 = 0x1D1C1A
  static let mute: UInt32 = 0x6E6961
  static let markColors: [UInt32] = [0x1D1C1A, 0xC2A27B, 0x2B2926, 0xCF4A2A]

  // MARK: phases

  static let down1 = 1.25, drag1 = 1.4, rel1 = 2.25, stop1 = 3.45
  static let down2 = 3.3, drag2 = 3.45, rel2 = 4.2
  static let turnEnd = 5.0
  static let foldStart = 5.15, foldEnd = 6.5
  static let handoff = 6.6
  static let riseStart = 7.45, riseEnd = 8.35
  static let welcomeIn = 8.3
  static let holdEnd = 11.4
  static let unfoldStart = 12.7, unfoldEnd = 13.75

  static let yawPerPt = 0.45
  private static let d1 = 75.0
  private static let td1 = stop1 - rel1
  private static let t1 = rel1 - drag1
  private static let v1 = 2 * d1 / t1
  static let a1 = d1 + v1 * td1 / 2
  private static let t2 = rel2 - drag2
  private static let td2 = turnEnd - rel2
  static let d2 = (360 - a1) / (1 + td2 / t2)
  private static let v2 = 2 * d2 / t2

  static let idle = 2.0
  static let returnDuration = 1.2
  /// Where the story clock starts after a finger completes the turn (just before the track's pop).
  static let storyT0 = turnEnd - 0.05
  /// Reduced motion: the welcome screen, still.
  static let reducedT = 10.0

  // MARK: helpers

  static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
  static func lerp(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
  static func easeInOutCubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
  static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
  static func easeInCubic(_ x: Double) -> Double { x * x * x }
  /// Ease out with a small overshoot (about 4 %).
  static func easeOutBack(_ x: Double) -> Double {
    let c1 = 1.05, c3 = c1 + 1
    return 1 + c3 * pow(x - 1, 3) + c1 * pow(x - 1, 2)
  }
  static func bump(_ t: Double, _ a: Double, _ b: Double) -> Double {
    let k = sin(Double.pi * seg(t, a, b))
    return k * k
  }
  static func bezier(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double, _ x: Double) -> Double {
    if x <= 0 { return 0 }
    if x >= 1 { return 1 }
    func bx(_ s: Double) -> Double { 3 * (1 - s) * (1 - s) * s * x1 + 3 * (1 - s) * s * s * x2 + s * s * s }
    func by(_ s: Double) -> Double { 3 * (1 - s) * (1 - s) * s * y1 + 3 * (1 - s) * s * s * y2 + s * s * s }
    var lo = 0.0, hi = 1.0
    for _ in 0..<30 {
      let m = (lo + hi) / 2
      if bx(m) < x { lo = m } else { hi = m }
    }
    return by((lo + hi) / 2)
  }
  static func easeInOutTouch(_ x: Double) -> Double { bezier(0.65, 0, 0.35, 1, x) }

  // MARK: the turn

  private static func flick(_ t: Double, _ a: Double, _ r: Double, _ d: Double, _ v: Double, _ td: Double) -> Double {
    if t <= a { return 0 }
    if t < r {
      let x = (t - a) / (r - a)
      return d * x * x
    }
    let u = min((t - r) / td, 1)
    return d + v * td / 2 * (1 - (1 - u) * (1 - u))
  }

  /// Turn of the sculpture in the film (degrees). 360 from turnEnd on: the same pose as 0.
  static func filmYaw(_ t: Double) -> Double {
    if t >= turnEnd { return 360 }
    return flick(t, drag1, rel1, d1, v1, td1) + flick(t, drag2, rel2, d2, v2, td2)
  }

  /// The ghost finger: x offset in degrees of drag, visibility, press.
  static func filmFinger(_ t: Double) -> (x: Double, vis: Double, press: Double) {
    if t >= down1 - 0.15 && t < rel1 + 0.3 {
      let k = clamp01((t - drag1) / t1)
      return (-d1 / 2 + d1 * k * k,
              easeOutCubic(seg(t, down1 - 0.15, drag1)) * (1 - easeOutCubic(seg(t, rel1, rel1 + 0.25))),
              seg(t, down1, drag1) * (1 - seg(t, rel1, rel1 + 0.12)))
    }
    if t >= down2 - 0.15 && t < turnEnd {
      let k = clamp01((t - drag2) / t2)
      return (-d2 / 2 + d2 * k * k,
              easeOutCubic(seg(t, down2 - 0.15, drag2)) * (1 - easeOutCubic(seg(t, rel2, rel2 + 0.25))),
              seg(t, down2, drag2) * (1 - seg(t, rel2, rel2 + 0.12)))
    }
    return (0, 0, 0)
  }

  static func trackFill(_ t: Double, _ yaw: Double) -> Double { t >= 9 ? 0 : clamp01(abs(yaw) / 360) }
  static func trackPop(_ t: Double) -> Double { bump(t, turnEnd - 0.05, turnEnd + 0.4) }

  // MARK: the fold

  static func archYaw(_ t: Double, _ i: Int) -> Double {
    let rest = restYaw[i]
    if rest == 0 { return 0 }
    let lag = i == 1 ? 0 : 0.15
    let fold = easeOutBack(seg(t, foldStart + 0.1 + lag, foldStart + 1.1 + lag))
    let unfold = easeInOutCubic(seg(t, unfoldStart + lag * 0.6, unfoldEnd - 0.15 + lag * 0.6))
    return rest * (1 - fold + unfold)
  }

  static func pearlY(_ t: Double) -> Double {
    let dropA = foldStart + 0.3
    let land = dropA + 0.45
    if t < dropA { return pearlFloat }
    if t < land {
      let x = seg(t, dropA, land)
      return lerp(pearlFloat, pearlR, x * x)
    }
    if t < unfoldStart {
      let u = seg(t, land, land + 0.36)
      return pearlR + 0.075 * sin(Double.pi * u) * (1 - u * 0.3)
    }
    return lerp(pearlR, pearlFloat, easeInOutCubic(seg(t, unfoldStart + 0.1, unfoldEnd)))
  }

  static func camFront(_ t: Double) -> Double {
    easeInOutCubic(seg(t, turnEnd, foldEnd)) - easeInOutCubic(seg(t, 12.35, 13.95))
  }

  static func cameraAt(_ t: Double) -> Shot {
    let k = camFront(t)
    return Shot(eye: poster.eye + (front.eye - poster.eye) * k, target: poster.target + (front.target - poster.target) * k)
  }

  static var handoffCamera: Shot { cameraAt(handoff) }

  // MARK: layers

  static func stageVisible(_ t: Double) -> Double { 1 - seg(t, handoff + 0.25, handoff + 0.55) + seg(t, 12.4, 12.7) }
  static func markVisible(_ t: Double) -> Double { easeOutCubic(seg(t, handoff, handoff + 0.35)) * (1 - seg(t, 12.6, 12.85)) }
  static func markGleam(_ t: Double) -> (x: Double, k: Double) {
    let u = seg(t, handoff + 0.35, handoff + 0.95)
    return (lerp(-0.25, 1.25, easeInOutCubic(u)), bump(t, handoff + 0.35, handoff + 0.95))
  }
  static func markRise(_ t: Double) -> Double { easeInOutCubic(seg(t, riseStart, riseEnd)) - easeInOutCubic(seg(t, 11.7, 12.5)) }
  static func posterUi(_ t: Double) -> Double {
    1 - easeOutCubic(seg(t, turnEnd + 0.1, turnEnd + 0.5)) + easeInOutCubic(seg(t, 13.35, 13.95))
  }
  static func wordLetter(_ t: Double, _ i: Int) -> Double {
    let a = 7.9 + Double(i) * 0.05
    return easeOutCubic(seg(t, a, a + 0.5)) * (1 - easeInCubic(seg(t, 11.5, 11.8)))
  }
  static func welcomeItem(_ t: Double, _ j: Int) -> Double {
    let a = welcomeIn + Double(j) * 0.08
    let b = holdEnd + Double(3 - j) * 0.04
    return easeOutCubic(seg(t, a, a + 0.55)) * (1 - easeInCubic(seg(t, b, b + 0.3)))
  }

  // MARK: the stage camera, for drawing 2D over 3D

  struct Basis { var f: V3; var rx: Double; var rz: Double; var u: V3; var focal: Double }

  static func basis(_ c: Shot, width: Double) -> Basis {
    var f = c.target - c.eye
    let fl = simd_length(f); f = fl > 0 ? f / fl : V3(0, 0, -1)
    var rx = -f.z, rz = f.x
    let rl = hypot(rx, rz); if rl > 0 { rx /= rl; rz /= rl }
    let u = V3(-rz * f.y, rz * f.x - rx * f.z, rx * f.y)
    return Basis(f: f, rx: rx, rz: rz, u: u, focal: width / 2 / tan(hfovDeg * Double.pi / 360))
  }

  /// Screen point (pt) and depth of a world point (as React Native's project()).
  static func project(_ c: Shot, _ b: Basis, _ w: Double, _ h: Double, _ p: V3) -> (x: Double, y: Double, z: Double) {
    let q = p - c.eye
    let z = simd_dot(q, b.f)
    return (w / 2 + (q.x * b.rx + q.z * b.rz) / z * b.focal, h / 2 - simd_dot(q, b.u) / z * b.focal, z)
  }
}
