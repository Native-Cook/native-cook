import Foundation
import simd

/// One loop time drives the film: the dial, the unlock and the camera are pure functions of t, so t = loop gives the
/// same frame as t = 0. The unlock is one timeline `u` (0 = locked, `unlock` = door open): handle, then bolts, then
/// door. The film plays it forward and back; a finger's right code plays the same timeline.
///
///  0.00 - 0.70  rest: locked, dial at 0
///  0.70 - 1.70  the dial turns clockwise to the first number
///  2.05 - 3.05  counter-clockwise to the second
///  3.40 - 4.30  clockwise to the third
///  4.50 - 6.25  u 0 -> 1.75: handle swings (0 - 0.5), bolts draw in (0.22 - 0.78), door opens (0.65 - 1.75)
///  6.25 - 7.30  held open, warm light through the gap
///  7.30 - 8.75  u 1.75 -> 0: door shuts, bolts go out, handle returns
///  8.70 - 9.40  the dial returns to 0, the entries clear
///  9.40 - 10.0  rest; last frame = first frame
enum CombinationSafeDialMotion {
  typealias V3 = SIMD3<Double>

  static let loop = 10.0
  static let dialN = 40
  static let degPerNumber = 360.0 / Double(dialN)

  // MARK: the scene (glTF space: y up, metres, the floor is y = 0, the door faces +z)

  /// The door turns about y through its hinge (opening towards the viewer is a negative angle).
  static let doorHinge = V3(-0.235, 0.333, 0.188)
  /// In the Door node's frame: the door's centre, the dial's and the handle's pivots (both turn about z).
  static let doorCentre = V3(0.235, 0, 0)
  static let dialPivot = V3(0.235, 0.052, 0)
  static let handlePivot = V3(0.235, -0.112, -0.001)
  static let doorRadius = 0.205
  static let boreRadius = 0.213
  static let frontZ = 0.17
  static let dialFaceZ = 0.194
  static let boltCount = 8
  static let boltFirstDeg = 22.5
  static let boltStepDeg = 45.0
  static let boltTravel = 0.03

  static let target = V3(0, 0.325, 0)
  static let dist = 2.1
  static let azRest = 13.0
  static let elRest = 7.0
  static let azOpen = 19.0
  static let elOpen = 6.0
  static let zoomOpen = 0.06
  static let frameW = 0.74
  static let frameH = 0.76
  static let near = 0.05
  static let far = 30.0

  static let iblIntensity = 30_000.0
  static let keyDirection = V3(-0.35, -0.7, -0.62)
  static let keyLux = 76_000.0
  static let fillDirection = V3(0.7, -0.3, -0.6)
  static let fillLux = 14_000.0

  static var dialWorldY: Double { doorHinge.y + dialPivot.y }

  // MARK: palette and copy

  static let backdrop: UInt32 = 0xCFC5B2
  static let panel: UInt32 = 0xFBF8F1
  static let card: UInt32 = 0xFFFFFF
  static let ink: UInt32 = 0x1D211E
  static let mute: UInt32 = 0x747568
  static let brass: UInt32 = 0x9A6F1E
  static let green: UInt32 = 0x1F4238
  static let red: UInt32 = 0xB33A2B
  static let glow: UInt32 = 0xFFB95C

  static let app = "Keep"
  static let vault = "Personal vault"
  static let rows: [(label: String, value: String)] = [("Passwords", "128"), ("Recovery codes", "16"), ("Secure notes", "9")]
  static let demoCode = (24, 8, 31)

  // MARK: timing

  static let phaseA = 0.7, phaseAEnd = 1.7
  static let phaseB = 2.05, phaseBEnd = 3.05
  static let phaseC = 3.4, phaseCEnd = 4.3
  static let open = 4.5
  static let shut = 7.3, shutEnd = 8.75
  static let dialBack = 8.7, dialBackEnd = 9.4

  /// The unlock timeline, in seconds of `u`.
  static let unlock = 1.75
  static let handleU = (0.0, 0.5)
  static let boltU = (0.22, 0.64)
  static let boltStagger = 0.02
  static let doorU = (0.65, 1.75)
  static let handleDeg = 82.0
  static let doorDeg = 36.0
  /// How strongly the dial catches on each number (0 = smooth, 1 = stops at every number).
  static let detent = 0.62

  // MARK: helpers

  static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : x > 1 ? 1 : x }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
  static func lerp(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
  static func smooth(_ x: Double) -> Double { x * x * (3 - 2 * x) }
  static func easeInOutCubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }

  /// A number on the dial, 0..39, for an unwrapped dial position.
  static func dialNumber(_ n: Double) -> Int {
    let r = Int(jsRound(n)) % dialN
    return r < 0 ? r + dialN : r
  }

  /// The dial catches on each number: between two numbers it hurries, at a number it lingers.
  static func detented(_ v: Double) -> Double {
    let f = floor(v)
    let x = v - f
    return f + lerp(x, smooth(smooth(x)), detent)
  }

  // MARK: the film

  /// The film's dial (unwrapped numbers: + is counter-clockwise), for the combination c0, c1, c2.
  static func filmDial(_ t: Double, _ c: (Int, Int, Int)) -> Double {
    let s1 = Double(c.0 == 0 ? 0 : c.0 - dialN)
    let s2 = Double(c.1)
    let s3 = Double(c.2 == 0 ? 0 : c.2 - dialN)
    let v = s1 * easeInOutCubic(seg(t, phaseA, phaseAEnd))
      + (s2 - s1) * easeInOutCubic(seg(t, phaseB, phaseBEnd))
      + (s3 - s2) * easeInOutCubic(seg(t, phaseC, phaseCEnd))
      - s3 * easeInOutCubic(seg(t, dialBack, dialBackEnd))
    return detented(v)
  }

  /// How many numbers the film has entered (0..3).
  static func filmEntered(_ t: Double) -> Int {
    if t >= dialBack + 0.05 { return 0 }
    if t >= phaseCEnd { return 3 }
    if t >= phaseBEnd { return 2 }
    if t >= phaseAEnd { return 1 }
    return 0
  }

  /// The film's place on the unlock timeline.
  static func filmUnlock(_ t: Double) -> Double {
    clamp01((t - open) / unlock) * unlock - seg(t, shut, shutEnd) * unlock
  }

  /// The film's finger on the dial: opacity 0..1 and pressed 0..1.
  static func dialGhost(_ t: Double) -> (opacity: Double, pressed: Double) {
    let a = t < 1.875 ? phaseA : t < 3.225 ? phaseB : phaseC
    let b = t < 1.875 ? phaseAEnd : t < 3.225 ? phaseBEnd : phaseCEnd
    return (seg(t, a - 0.22, a - 0.04) * (1 - seg(t, b + 0.02, b + 0.16)),
            seg(t, a - 0.06, a + 0.04) * (1 - seg(t, b - 0.04, b + 0.06)))
  }

  // MARK: the unlock, from u

  static func handleAt(_ u: Double) -> Double { easeInOutCubic(seg(u, handleU.0, handleU.1)) }

  static func boltAt(_ u: Double, _ i: Int) -> Double {
    let from = boltU.0 + Double(i) * boltStagger
    return easeInOutCubic(seg(u, from, from + (boltU.1 - boltU.0)))
  }

  static func doorAt(_ u: Double) -> Double { easeInOutCubic(seg(u, doorU.0, doorU.1)) }

  /// Bolt i's direction, clockwise from straight up.
  static func boltDir(_ i: Int) -> (x: Double, y: Double) {
    let a = (boltFirstDeg + boltStepDeg * Double(i)) * .pi / 180
    return (sin(a), cos(a))
  }

  // MARK: camera and projection

  /// tan(half the vertical field of view) that keeps frameW x frameH around the target in a stage of this aspect.
  static func tanHalfFovY(_ aspect: Double) -> Double { max(frameH / 2, frameW / 2 / aspect) / dist }

  /// The camera for the door's opening (0..1). It looks at `target`.
  static func eye(open: Double) -> V3 {
    let k = smooth(open)
    let az = lerp(azRest, azOpen, k) * .pi / 180
    let el = lerp(elRest, elOpen, k) * .pi / 180
    let d = dist * (1 - zoomOpen * k)
    return V3(target.x + d * cos(el) * sin(az), target.y + d * sin(el), target.z + d * cos(el) * cos(az))
  }

  /// Projects a world point to the stage (pt) with the stage's own camera.
  static func project(_ p: V3, open: Double, width: Double, height: Double) -> CGPoint {
    let e = eye(open: open)
    let f = simd_normalize(target - e)
    var r = V3(-f.z, 0, f.x)
    r = r / (r.x * r.x + r.z * r.z).squareRoot()
    let u = V3(-r.z * f.y, r.z * f.x - r.x * f.z, r.x * f.y)
    let q = p - e
    let depth = simd_dot(q, f)
    let aspect = width / height
    let tanY = tanHalfFovY(aspect)
    let ndcX = (q.x * r.x + q.z * r.z) / (depth * tanY * aspect)
    let ndcY = simd_dot(q, u) / (depth * tanY)
    return CGPoint(x: (ndcX * 0.5 + 0.5) * width, y: (0.5 - ndcY * 0.5) * height)
  }

  /// The change of a finger's angle round a centre (radians, -pi..pi).
  static func angleDelta(_ a: Double, _ b: Double) -> Double {
    var d = b - a
    while d > .pi { d -= 2 * .pi }
    while d < -.pi { d += 2 * .pi }
    return d
  }

  /// JavaScript's Math.round: halves go up.
  static func jsRound(_ x: Double) -> Double { floor(x + 0.5) }

  static func twoDigits(_ v: Int) -> String { v < 10 ? "0\(v)" : "\(v)" }
}
