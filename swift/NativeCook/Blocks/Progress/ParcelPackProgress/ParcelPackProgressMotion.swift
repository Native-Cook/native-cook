import Foundation
import simd

/// One loop time drives everything: the carton (flaps, tape, label, its slide), the camera and the list. Every value
/// is a pure function of t, so t = loop gives the same frame as t = 0.
///
///  0.00 - 0.80  rest: an open carton, "Packed" in progress
///  0.80 - 2.40  the two short flaps fall shut, then the two long ones; the camera rises and moves in
///  2.75 - 3.95  the tape runs up the left wall, along the seam and down the right wall while a finger turns the view
///  4.60 - 5.35  the label drops, lands at 4.95 and settles with a small squash
///  5.80 - 6.70  the camera eases back
///  6.25 - 7.45  the carton backs up 3 cm, then slides off to the right
///  7.65         out of sight, it becomes a new open carton on the left
///  7.80 - 8.15  the list starts over
///  7.90 - 8.75  the new carton slides in, turning the last few degrees square
///  8.75 - 10.0  rest; last frame = first frame
enum ParcelPackProgressMotion {
  typealias V3 = SIMD3<Double>

  static let loop = 10.0

  // MARK: the scene (glTF space: y up, metres, the floor is y = 0, the viewer at +z)

  static let boxYaw = -30.0

  struct Flap {
    let node: String
    let pivot: V3
    let axis: V3
    /// Its angle when open (deg, signed) and how far its hinge sinks as it closes.
    let open: Double
    let sink: Double
  }

  static let flaps: [Flap] = [
    Flap(node: "FlapLeft", pivot: V3(-0.15, 0.19, 0), axis: V3(0, 0, 1), open: 128, sink: 0.0056),
    Flap(node: "FlapRight", pivot: V3(0.15, 0.19, 0), axis: V3(0, 0, 1), open: -136, sink: 0.0056),
    Flap(node: "FlapFront", pivot: V3(0, 0.19, 0.11), axis: V3(1, 0, 0), open: 146, sink: 0),
    Flap(node: "FlapBack", pivot: V3(0, 0.19, -0.11), axis: V3(1, 0, 0), open: -110, sink: 0),
  ]

  static let tapeA = V3(-0.1503, 0.135, 0)
  static let tapeTop = V3(-0.1509, 0.1906, 0)
  static let tapeB = V3(0.1503, 0.1912, 0)
  static let tapeSide = 0.055
  static let tapeSeam = 0.3

  static let label = V3(0.058, 0.1908, 0.066)
  static let labelYaw = 4.0
  static let tiltAxis = V3(0.94, 0, 0.34)
  /// A piece that is "not there" is this small.
  static let none = 0.0001

  static let target = V3(0, 0.15, 0)
  static let dist = 1.25
  static let elRest = 30.0
  static let frameW = 0.62
  static let frameH = 0.52
  static let near = 0.05
  static let far = 30.0

  static let iblIntensity = 30_000.0
  static let keyDirection = V3(0.4, -0.85, -0.35)
  static let keyLux = 72_000.0
  static let fillDirection = V3(-0.7, -0.3, -0.6)
  static let fillLux = 15_000.0

  // MARK: palette and copy

  static let backdrop: UInt32 = 0xE4DCCE
  static let panel: UInt32 = 0xFBFAF7
  static let ink: UInt32 = 0x1F1D1A
  static let mute: UInt32 = 0x7C766B
  static let green: UInt32 = 0x1F5C4A
  static let greenSoft: UInt32 = 0xE3EEE9
  static let track: UInt32 = 0xDDD7CB

  static let shop = "Carton & Co."
  static let order = "Order 4821"
  static let arrives = "Arrives Thursday by 6 PM"
  static let steps: [(title: String, at: String)] = [
    ("Packed", "9:41 AM"), ("Sealed", "9:44 AM"), ("Labelled", "9:47 AM"), ("On its way", "9:52 AM"),
  ]
  static let headlines = ["Packing your order", "Sealing the box", "Adding the label", "Waiting for the courier", "On its way"]
  static var stepN: Int { steps.count }

  // MARK: timing

  /// Each flap's fall, [start, end], in the order of `flaps`.
  static let folds: [(Double, Double)] = [(0.8, 1.45), (0.98, 1.63), (1.5, 2.2), (1.72, 2.4)]
  static let tapeStart = 2.75, tapeEnd = 3.95
  static let labelDrop = 4.6, labelLand = 4.95, labelSettle = 5.4
  static let backUpAt = 6.25, go = 6.55, gone = 7.45, swap = 7.65
  static let reset = 7.8, resetEnd = 8.15
  static let arrive = 7.9, arriveEnd = 8.75, rest = 8.75

  /// When each step is finished.
  static let done = [2.4, 4.05, 5.4, 7.3]
  /// Where a tap on a step takes the clock.
  static let jump = [2.7, 4.4, 5.75, 7.6]
  static let reducedT = 5.75

  static let elLift = 7.0
  static let zoomIn = 0.08
  static let camIn = (0.8, 2.4)
  static let camOut = (5.8, 6.7)
  static let ghost = [2.55, 2.75, 3.75, 3.9, 4.6]
  static let orbitFilm = 18.0
  static let orbitSpan = 70.0
  static let orbitMax = 25.0

  static let slide = 1.15
  static let backUp = 0.03
  static let arriveBack = 0.6
  static let arriveYaw = 7.0
  static let labelHeight = 0.2
  static let labelTilt = 14.0
  static let labelSquash = 0.1
  static let boxSquashBy = 0.012
  static let fall = 0.62
  static let bounce1 = 0.06
  static let bounce2 = 0.018

  // MARK: helpers

  static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : x > 1 ? 1 : x }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
  static func lerp(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
  static func easeInOutSine(_ x: Double) -> Double { -(cos(.pi * x) - 1) / 2 }
  static func easeInOutCubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
  static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
  static func easeInCubic(_ x: Double) -> Double { x * x * x }
  static func easeOutBack(_ x: Double, _ c: Double) -> Double {
    let u = x - 1
    return 1 + (c + 1) * u * u * u + c * u * u
  }

  /// A flap's way down over its window k: 1 (open) falls to 0 at `fall`, then two small rebounds.
  static func flapFall(_ k: Double) -> Double {
    if k <= 0 { return 1 }
    if k >= 1 { return 0 }
    if k < fall {
      let u = k / fall
      return 1 - u * u
    }
    let mid = fall + (1 - fall) * 0.63
    if k < mid {
      let u = (k - fall) / (mid - fall)
      return bounce1 * 4 * u * (1 - u)
    }
    let u = (k - mid) / (1 - mid)
    return bounce2 * 4 * u * (1 - u)
  }

  // MARK: the carton

  static func flapOpen(_ i: Int, _ t: Double) -> Double {
    if t >= swap { return 1 }
    return flapFall(seg(t, folds[i].0, folds[i].1))
  }

  /// The tape's three pieces, each 0..1 of its length.
  static func tapeAt(_ t: Double) -> (Double, Double, Double) {
    if t >= swap { return (0, 0, 0) }
    let p = easeInOutSine(seg(t, tapeStart, tapeEnd)) * (tapeSide * 2 + tapeSeam)
    return (clamp01(p / tapeSide), clamp01((p - tapeSide) / tapeSeam), clamp01((p - tapeSide - tapeSeam) / tapeSide))
  }

  static func landRing(_ t: Double, _ decay: Double, _ freq: Double) -> Double {
    let tau = t - labelLand
    if tau <= 0 || t >= labelSettle { return 0 }
    return exp(-decay * tau) * sin(freq * tau) * (1 - seg(t, labelSettle - 0.12, labelSettle))
  }

  static func labelAt(_ t: Double) -> (height: Double, size: Double, tilt: Double, squash: Double) {
    if t < labelDrop || t >= swap { return (labelHeight, 0, labelTilt, 0) }
    let k = seg(t, labelDrop, labelLand)
    return (labelHeight * (1 - k * k), easeOutCubic(seg(k, 0, 0.4)), labelTilt * (1 - easeInOutSine(k)), labelSquash * landRing(t, 9, 24))
  }

  static func boxSquash(_ t: Double) -> Double { 1 - boxSquashBy * landRing(t, 12, 26) }

  static func slideX(_ t: Double) -> Double {
    if t < backUpAt { return 0 }
    if t < go { return -backUp * easeOutCubic(seg(t, backUpAt, go)) }
    if t < swap { return lerp(-backUp, slide, easeInCubic(seg(t, go, gone))) }
    return lerp(-slide, 0, easeOutBack(seg(t, arrive, arriveEnd), arriveBack))
  }

  static func yawAt(_ t: Double) -> Double {
    if t < swap { return boxYaw }
    return boxYaw + arriveYaw * (1 - easeOutCubic(seg(t, arrive, arriveEnd)))
  }

  // MARK: camera

  static func camK(_ t: Double) -> Double {
    easeInOutSine(seg(t, camIn.0, camIn.1)) - easeInOutSine(seg(t, camOut.0, camOut.1))
  }

  static func filmOrbit(_ t: Double) -> Double {
    orbitFilm * (easeInOutSine(seg(t, ghost[1], ghost[2])) - easeOutCubic(seg(t, ghost[3], ghost[4])))
  }

  static func ghostAt(_ t: Double) -> (opacity: Double, pressed: Double, along: Double) {
    (seg(t, ghost[0] - 0.2, ghost[0]) * (1 - seg(t, ghost[3], ghost[3] + 0.2)),
     seg(t, ghost[0], ghost[0] + 0.1) * (1 - seg(t, ghost[3] - 0.05, ghost[3] + 0.08)),
     easeInOutSine(seg(t, ghost[1], ghost[2])))
  }

  static func tanHalfFovY(_ aspect: Double) -> Double { max(frameH / 2, frameW / 2 / aspect) / dist }

  static func eye(_ t: Double, orbit: Double) -> V3 {
    let k = camK(t)
    let az = (filmOrbit(t) + orbit) * .pi / 180
    let el = (elRest + elLift * k) * .pi / 180
    let d = dist * (1 - zoomIn * k)
    return V3(target.x + d * cos(el) * sin(az), target.y + d * sin(el), target.z + d * cos(el) * cos(az))
  }

  static func orbitFromDrag(_ dx: Double, width: Double) -> Double {
    let a = (-dx / width) * orbitSpan
    let m = abs(a)
    if m <= orbitMax { return a }
    let over = m - orbitMax
    return (a < 0 ? -1 : 1) * (orbitMax + (over * 12) / (over + 12))
  }

  // MARK: the list

  static func resetAt(_ t: Double) -> Double { seg(t, reset, resetEnd) }
  static func doneAt(_ i: Int, _ t: Double) -> Double { seg(t, done[i], done[i] + 0.25) * (1 - resetAt(t)) }
  static func checkScale(_ i: Int, _ t: Double) -> Double {
    0.5 + 0.5 * easeOutBack(seg(t, done[i], done[i] + 0.32), 2.2)
  }

  static func activeRow(_ t: Double) -> Double {
    if t >= reset { return 0 }
    var a = 0.0
    for i in 0..<(stepN - 1) { a += easeInOutCubic(seg(t, done[i], done[i] + 0.4)) }
    return a
  }

  static func activeAlpha(_ t: Double) -> Double {
    1 - seg(t, done[stepN - 1], done[stepN - 1] + 0.25) + seg(t, reset + 0.1, resetEnd)
  }

  static func headlineAlpha(_ j: Int, _ t: Double) -> Double {
    let before = j == 0 ? 1 : doneAt(j - 1, t)
    let after = j == stepN ? 0 : doneAt(j, t)
    return clamp01(before - after)
  }

  static func pulseAt(_ t: Double) -> Double { 0.5 - 0.5 * cos(2 * .pi * t) }
}
