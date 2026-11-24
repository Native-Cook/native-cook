import Foundation
import simd

/// One loop time drives the film. The blades' speed is a sum of smooth ramps, and their angle is its exact integral,
/// scaled so the loop holds a whole number of turns: t = loop gives the same frame as t = 0.
///
///  0.00 - 0.80  rest: Off, blades still, head at its rest angle
///  0.80         level 1: spins up over 0.7 s
///  2.60         level 2
///  4.30         level 3: the peak, the camera has moved in a little
///  6.60         back to level 1
///  7.70         Off: the blades coast down for 1.7 s (to 9.4)
///  1.10 - 9.20  the head oscillates, two sweeps, under an envelope that eases in and out
///  9.40 - 10.0  rest; last frame = first frame
enum DeskFanSpeedMotion {
  typealias V3 = SIMD3<Double>

  static let loop = 10.0
  /// Whole turns of the blades in one loop.
  static let turns = 20.0
  /// Blade speed for each level (turns a second, before the small scale that makes `turns` exact).
  static let speeds: [Double] = [0, 1.5, 2.9, 4.4]
  /// The film's level changes: time, new level, ramp length (s).
  static let ramps: [(at: Double, level: Int, len: Double)] = [(0.8, 1, 0.7), (2.6, 2, 0.7), (4.3, 3, 0.7), (6.6, 1, 0.9), (7.7, 0, 1.7)]
  /// The head: rest angle and the film's sweep (deg), its sweeps per loop and envelope.
  static let yawRest = -10.0
  static let oscDeg = 24.0
  static let oscCycles = 2.0
  static let oscIn = (1.1, 2.4)
  static let oscOut = (7.4, 9.2)
  /// A finger's aim: degrees for a drag across the screen, and the most it turns (rubber-banded past it).
  static let aimSpan = 110.0
  static let aimMax = 42.0
  /// A finger's speed changes: time constants (s) of the motor spinning up and coasting down.
  static let spinUpTau = 0.42
  static let spinDownTau = 0.95
  /// Airflow dashes: the pattern's length (pt) and how many patterns pass per blade turn (a half, so `turns` stays whole).
  static let flowPeriod = 72.0
  static let flowPerTurn = 0.5
  /// A pressed key takes this long to go down (s, around its moment).
  static let keyLead = 0.05
  static let keyFall = 0.09

  // MARK: the scene (glTF space: y up, metres, the floor is y = 0, the fan faces +z)

  static let headPivot = V3(0, 0.285, 0)
  static let bladesPivot = V3(0, 0, 0.056)
  static let keyPositions: [V3] = [V3(-0.05175, 0.052, 0.082), V3(-0.01725, 0.052, 0.082), V3(0.01725, 0.052, 0.082), V3(0.05175, 0.052, 0.082)]
  static let keyTravel = 0.0055
  /// The blur disc's colour (linear), its alpha set per frame.
  static let blurRGB = V3(0.69, 0.46, 0.15)

  static let target = V3(0, 0.215, 0)
  static let dist = 1.35
  static let azRest = -16.0
  static let azPeak = -10.0
  static let elRest = 9.0
  static let elPeak = 8.0
  static let zoomPeak = 0.05
  static let frameW = 0.4
  static let frameH = 0.5
  static let near = 0.04
  static let far = 30.0

  static let iblIntensity = 30_000.0
  static let keyDirection = V3(-0.4, -0.8, -0.45)
  static let keyLux = 70_000.0
  static let fillDirection = V3(0.7, -0.3, -0.6)
  static let fillLux = 14_000.0

  // MARK: palette and copy

  static let backdrop: UInt32 = 0xE4D7BA
  static let panel: UInt32 = 0xFCFAF4
  static let card: UInt32 = 0xFFFFFF
  static let ink: UInt32 = 0x1F2A26
  static let mute: UInt32 = 0x6F7670
  static let green: UInt32 = 0x2F6657

  static let app = "Breeze"
  static let room = "Bedroom fan"
  static let levels = ["Off", "1", "2", "3"]
  /// What each level reads as on the panel: motor rpm and watts.
  static let rpm: [Double] = [0, 620, 1040, 1480]
  static let watts: [Double] = [0, 14, 26, 41]

  // MARK: helpers

  static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : x > 1 ? 1 : x }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
  static func lerp(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
  static func smooth(_ x: Double) -> Double { x * x * (3 - 2 * x) }
  /// The integral of smooth(x) from 0 to x.
  static func smoothIntegral(_ x: Double) -> Double { x * x * x - x * x * x * x / 2 }
  /// JavaScript's Math.round: halves go up.
  static func jsRound(_ x: Double) -> Double { floor(x + 0.5) }

  /// The turns of the film's speed profile from 0 to t, before scaling.
  static func rawTurns(_ t: Double) -> Double {
    var total = 0.0, prev = 0.0
    for r in ramps {
      let to = speeds[r.level]
      let dv = to - prev
      prev = to
      if t <= r.at { continue }
      if t >= r.at + r.len { total += dv * (t - r.at - r.len / 2) } else { total += dv * r.len * smoothIntegral((t - r.at) / r.len) }
    }
    return total
  }

  static let turnScale = turns / rawTurns(loop)

  // MARK: the film

  /// The film's blade angle, in turns (0 .. 20): a pure function of the loop time.
  static func filmTurns(_ t: Double) -> Double { rawTurns(t) * turnScale }

  /// The film's blade speed (turns a second).
  static func filmSpeed(_ t: Double) -> Double {
    var v = 0.0, prev = 0.0
    for r in ramps {
      let to = speeds[r.level]
      v += (to - prev) * smooth(seg(t, r.at, r.at + r.len))
      prev = to
    }
    return v * turnScale
  }

  /// The level the film has selected.
  static func filmLevel(_ t: Double) -> Int {
    var level = 0
    for r in ramps where t >= r.at { level = r.level }
    return level
  }

  /// The film's head angle (deg).
  static func filmYaw(_ t: Double) -> Double {
    let env = smooth(seg(t, oscIn.0, oscIn.1)) * (1 - smooth(seg(t, oscOut.0, oscOut.1)))
    return yawRest + oscDeg * env * sin(2 * .pi * oscCycles * t / loop)
  }

  /// How far key i is down in the film, 0..1.
  static func filmKey(_ t: Double, _ i: Int) -> Double {
    var v = i == 0 ? 1.0 : 0
    var prev = 0
    for r in ramps {
      let e = smooth(seg(t, r.at - keyLead, r.at + keyFall))
      if r.level == i { v += e }
      if prev == i { v -= e }
      prev = r.level
    }
    return clamp01(v)
  }

  static func filmKeys(_ t: Double) -> [Double] { (0..<4).map { filmKey(t, $0) } }

  /// The film's finger on the segmented control: opacity, pressed, and the level it is over.
  static func tapGhost(_ t: Double) -> (opacity: Double, pressed: Double, level: Int) {
    var best = 0, dist = 1e9
    for (k, r) in ramps.enumerated() where abs(t - r.at) < dist {
      dist = abs(t - r.at)
      best = k
    }
    let at = ramps[best].at
    return (seg(t, at - 0.3, at - 0.08) * (1 - seg(t, at + 0.1, at + 0.3)),
            seg(t, at - 0.08, at) * (1 - seg(t, at + 0.04, at + 0.16)),
            ramps[best].level)
  }

  // MARK: what the speed reads as

  /// How much the blades are a blur at this speed, 0..1.
  static func blurAt(_ speed: Double) -> Double { smooth(seg(speed, 0.9, 3.6)) * 0.85 }

  /// A reading between the levels' values, from the blade speed.
  static func readAt(_ speed: Double, _ table: [Double]) -> Double {
    for i in 0..<(speeds.count - 1) where speed <= speeds[i + 1] {
      return lerp(table[i], table[i + 1], seg(speed, speeds[i], speeds[i + 1]))
    }
    return table[table.count - 1]
  }

  static func rpmAt(_ speed: Double) -> Int { Int(jsRound(readAt(speed, rpm) / 10) * 10) }
  static func wattsAt(_ speed: Double) -> Int { Int(jsRound(readAt(speed, watts))) }

  // MARK: camera

  /// tan(half the vertical field of view) that keeps frameW x frameH around the target in a stage of this aspect.
  static func tanHalfFovY(_ aspect: Double) -> Double { max(frameH / 2, frameW / 2 / aspect) / dist }

  /// The camera for a blade speed (it leans in as the fan works). It looks at `target`.
  static func eye(speed: Double) -> V3 {
    let k = smooth(clamp01(speed / speeds[3]))
    let az = lerp(azRest, azPeak, k) * .pi / 180
    let el = lerp(elRest, elPeak, k) * .pi / 180
    let d = dist * (1 - zoomPeak * k)
    return V3(target.x + d * cos(el) * sin(az), target.y + d * sin(el), target.z + d * cos(el) * cos(az))
  }

  /// A finger's aim from its drag (pt), rubber-banded past aimMax either side of the centre.
  static func aimFromDrag(_ from: Double, _ dx: Double, _ width: Double) -> Double {
    let a = from + dx / width * aimSpan
    let m = abs(a)
    if m <= aimMax { return a }
    let over = m - aimMax
    return (a < 0 ? -1 : 1) * (aimMax + over * 12 / (over + 12))
  }
}
