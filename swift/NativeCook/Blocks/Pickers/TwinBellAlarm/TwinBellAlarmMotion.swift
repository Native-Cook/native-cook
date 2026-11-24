import Foundation
import simd

/// One loop time drives the film: the wheel, the hands, the switch, the ring and the camera are pure functions of t,
/// so t = loop gives the same frame as t = 0.
///
///  0.00 - 0.65  rest: 7:00, alarm off
///  0.90 - 1.80  a finger drags the wheel to 6:30, one row every 0.15 s; the hands sweep back with it, then settle
///  2.80         the finger taps the switch: alarm on
///  2.90 - 4.10  the ring: the hammer chatters, the bells shake, the clock hops and rocks; the camera moves in
///  4.40 - 5.60  the camera eases back
///  6.00         the switch goes off
///  6.50 - 7.40  the wheel goes back to 7:00; the hands follow and settle (to 8.00)
///  8.20 - 10.0  rest; last frame = first frame
enum TwinBellAlarmMotion {
  typealias V3 = SIMD3<Double>

  static let loop = 10.0

  // MARK: the scene (glTF space: y up, metres, the floor is y = 0, the dial faces +z)

  static let hourPivot = V3(0, 0.092, 0.0263)
  static let minutePivot = V3(0, 0.092, 0.0276)
  static let hammerPivot = V3(0, 0.156, 0)
  static let hammerReach = 10.9
  static let bellRPivot = V3(0.03145, 0.15116, 0)
  static let bellLPivot = V3(-0.03145, 0.15116, 0)

  static let target = V3(0, 0.142, 0)
  static let dist = 0.82
  static let azRest = -12.0
  static let elRest = 10.0
  static let frameW = 0.25
  static let frameH = 0.36
  static let near = 0.03
  static let far = 30.0

  static let iblIntensity = 30_000.0
  static let keyDirection = V3(0.4, -0.85, -0.35)
  static let keyLux = 72_000.0
  static let fillDirection = V3(-0.7, -0.3, -0.6)
  static let fillLux = 15_000.0

  // MARK: palette and copy

  static let backdrop: UInt32 = 0xB9C5DA
  static let panel: UInt32 = 0xFBFAF7
  static let ink: UInt32 = 0x1C1D22
  static let mute: UInt32 = 0x767A86
  static let yellow: UInt32 = 0xE9A61C
  static let onText: UInt32 = 0x9A6A05

  static let app = "Earlybird"
  static let repeats = "Weekdays"
  static let restMin = 7 * 60
  static let filmMin = 6 * 60 + 30
  static let nowMin = 22 * 60 + 18
  static let wheelStep = 5

  // MARK: timing

  static let wheelA = 0.9, wheelAEnd = 1.8
  static let on = 2.8, ringAt0 = 2.9
  static let pushIn = 2.8, pushInEnd = 3.5, pushOut = 4.4, pushOutEnd = 5.6
  static let off = 6.0
  static let wheelB = 6.5, wheelBEnd = 7.4

  static let ring = 1.2, ringIn = 0.05, ringOut = 0.22
  static var hammerSwing: Double { hammerReach - 0.6 }
  static let hammerHz = 15.0
  static let bellShake = 2.2, bellHzL = 23.0, bellHzR = 26.0
  static let rock = 1.5, rockHz = 11.0
  static let shuffle = 0.0012, shuffleHz = 17.0
  static let hop = 0.012, hopS = 0.2
  static let chatter = 0.003, chatterHz = 9.0
  static let zoom = 0.14, azPush = 17.0, elPush = -3.0
  static let orbitSpan = 110.0, orbitMax = 40.0
  static let settle = 0.04, settleDecay = 8.0, settleFreq = 16.0, settleS = 0.6
  static let wheelLead = 0.05, wheelBeat = 0.15
  static var wheelRows: Int { (restMin - filmMin) / wheelStep }

  // MARK: helpers

  static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : x > 1 ? 1 : x }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
  static func lerp(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
  static func easeInOutSine(_ x: Double) -> Double { -(cos(.pi * x) - 1) / 2 }
  static func smooth(_ x: Double) -> Double { x * x * (3 - 2 * x) }

  /// The shortest way round from one angle to another (deg, -180..180), with JavaScript's `%`.
  static func shortest(_ d: Double) -> Double {
    (d.truncatingRemainder(dividingBy: 360) + 540).truncatingRemainder(dividingBy: 360) - 180
  }

  static func hourDeg(_ min: Int) -> Double { Double(min % 720) / 2 }
  static func minuteDeg(_ min: Int) -> Double { Double(min % 60) * 6 }

  static func settleWave(_ tau: Double) -> Double {
    if tau <= 0 || tau >= settleS { return 0 }
    return settle * exp(-settleDecay * tau) * sin(settleFreq * tau) * (1 - seg(tau, settleS - 0.15, settleS))
  }

  // MARK: the film

  static func filmSweep(_ t: Double) -> Double {
    let a = easeInOutSine(seg(t, wheelA, wheelAEnd)) + settleWave(t - wheelAEnd)
    let b = easeInOutSine(seg(t, wheelB, wheelBEnd)) + settleWave(t - wheelBEnd)
    return a - b
  }

  static func filmHour(_ t: Double) -> Double { lerp(hourDeg(restMin), hourDeg(filmMin), filmSweep(t)) }
  static func filmMinute(_ t: Double) -> Double { lerp(0, -Double(restMin - filmMin) * 6, filmSweep(t)) }

  static func filmWheel(_ t: Double) -> Int {
    let down = min(wheelRows, max(0, Int(floor((t - wheelA - wheelLead) / wheelBeat)) + 1))
    let up = min(wheelRows, max(0, Int(floor((t - wheelB - wheelLead) / wheelBeat)) + 1))
    return restMin - wheelStep * (down - up)
  }

  static func filmArmed(_ t: Double) -> Bool { t >= on && t < off }

  static func filmRing(_ t: Double) -> Double {
    let tau = t - ringAt0
    return tau >= 0 && tau <= ring ? tau : -1
  }

  static func filmPush(_ t: Double) -> Double {
    easeInOutSine(seg(t, pushIn, pushInEnd)) - easeInOutSine(seg(t, pushOut, pushOutEnd))
  }

  static func wheelGhost(_ t: Double) -> (opacity: Double, pressed: Double, along: Double) {
    let second = t > (wheelAEnd + wheelB) / 2
    let a = second ? wheelB : wheelA
    let b = second ? wheelBEnd : wheelAEnd
    let k = easeInOutSine(seg(t, a, b))
    return (seg(t, a - 0.25, a - 0.05) * (1 - seg(t, b + 0.1, b + 0.3)),
            seg(t, a - 0.05, a + 0.05) * (1 - seg(t, b, b + 0.12)),
            second ? 0.5 - k : k - 0.5)
  }

  static func toggleGhost(_ t: Double) -> (opacity: Double, pressed: Double) {
    let at = t > (on + off) / 2 ? off : on
    return (seg(t, at - 0.3, at - 0.08) * (1 - seg(t, at + 0.1, at + 0.3)),
            seg(t, at - 0.08, at) * (1 - seg(t, at + 0.04, at + 0.16)))
  }

  // MARK: the ring

  static func ringEnvelope(_ tau: Double) -> Double {
    if tau < 0 || tau > ring { return 0 }
    return smooth(seg(tau, 0, ringIn)) * (1 - smooth(seg(tau, ring - ringOut, ring)))
  }

  struct Ring {
    var hammer = 0.0, bellL = 0.0, bellR = 0.0, rock = 0.0, shuffle = 0.0, lift = 0.0
  }

  static func ringPose(_ tau: Double) -> Ring {
    let e = ringEnvelope(tau)
    if e <= 0 { return Ring() }
    let w = 2 * Double.pi * tau
    let rad = Double.pi / 180
    let u = tau / hopS
    let jump = u < 1 ? hop * 4 * u * (1 - u) : 0
    return Ring(hammer: e * hammerSwing * rad * sin(w * hammerHz),
                bellL: e * bellShake * rad * sin(w * bellHzL + 0.6),
                bellR: e * bellShake * rad * sin(w * bellHzR + 2.1),
                rock: e * rock * rad * sin(w * rockHz),
                shuffle: e * shuffle * sin(w * shuffleHz + 1),
                lift: jump + e * chatter * abs(sin(Double.pi * chatterHz * tau)) * seg(tau, hopS * 0.8, hopS * 1.4))
  }

  // MARK: camera

  static func tanHalfFovY(_ aspect: Double) -> Double { max(frameH / 2, frameW / 2 / aspect) / dist }

  static func eye(push: Double, orbit: Double) -> V3 {
    let az = (azRest + azPush * push + orbit) * .pi / 180
    let el = (elRest + elPush * push) * .pi / 180
    let d = dist * (1 - zoom * push)
    return V3(target.x + d * cos(el) * sin(az), target.y + d * sin(el), target.z + d * cos(el) * cos(az))
  }

  static func orbitFromDrag(_ dx: Double, width: Double) -> Double {
    let a = (-dx / width) * orbitSpan
    let m = abs(a)
    if m <= orbitMax { return a }
    let over = m - orbitMax
    return (a < 0 ? -1 : 1) * (orbitMax + (over * 14) / (over + 14))
  }

  // MARK: copy

  static func clockText(_ minutes: Int) -> String {
    let h = (minutes / 60) % 24
    return "\((h + 11) % 12 + 1):\(String(format: "%02d", minutes % 60))"
  }

  static func ringsIn(_ minutes: Int) -> String {
    let d = (minutes - nowMin + 1440) % 1440
    return "Rings in \(d / 60) h \(d % 60) min"
  }
}
