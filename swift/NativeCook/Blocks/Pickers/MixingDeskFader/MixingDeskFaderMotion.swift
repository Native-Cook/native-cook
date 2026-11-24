import Foundation
import simd

/// One loop time drives the film: the faders, knobs and mutes are a pure function of t, and the meters are periodic
/// signals of t, so t = loop gives the same frame as t = 0.
///
///  0.00 - 0.95  rest: the Intro preset (music up), meters moving
///  0.95 - 1.95  a finger rides Music down to 0.40
///  2.35 - 3.25  a finger brings Host up to 0 dB, where it settles exactly
///  3.90         SFX is muted: its button sinks, its light and its meter go out (0.16 s)
///  5.00         the Interview preset is tapped: all four faders and knobs glide there (1.1 s each, 70 ms apart)
///  7.40         Intro is tapped: everything glides back, SFX comes on again
///  8.71 - 10.0  rest; last frame = first frame. The camera leans in a little (0.6 - 2.4) and back (8.0 - 9.8).
///
/// A pose is 12 numbers: faders 0..3 (0..1 of their travel), knobs 4..7 (0..1 of their sweep), mutes 8..11 (0 live,
/// 1 muted). Ported from mixerScene.ts and mixerMotion.ts of the React Native block.
enum MixingDeskFaderMotion {
  typealias V3 = SIMD3<Double>

  static let loop = 10.0
  static let poseN = 12
  static let channels = 4

  // MARK: the scene (glTF space: y up, metres, the floor is y = 0, the desk faces +z)

  /// The Deck node: its origin and its tilt back about x (rad). The moving nodes are its children, posed in its frame.
  static let deckOrigin = V3(0, 0.032, 0.099288)
  static let deckSlope = 0.119429
  static let faderBase: [V3] = [V3(-0.096, 0, -0.034), V3(-0.036, 0, -0.034), V3(0.024, 0, -0.034), V3(0.084, 0, -0.034)]
  static let travel = 0.082
  static let capY = 0.011
  static let mutePos: [V3] = [V3(-0.09, 0, -0.142), V3(-0.03, 0, -0.142), V3(0.03, 0, -0.142), V3(0.09, 0, -0.142)]
  static let muteTravel = 0.0022
  static let knobPos: [V3] = [V3(-0.09, 0, -0.173), V3(-0.03, 0, -0.173), V3(0.03, 0, -0.173), V3(0.09, 0, -0.173)]
  static let knobZeroDeg = 135.0
  static let knobSpanDeg = 270.0
  static let leds = 8
  static let ledGreen = V3(0.1046, 0.8228, 0.2384)
  static let ledAmber = V3(1.0, 0.4564, 0.0452)
  static let ledRed = V3(1.0, 0.0802, 0.0513)
  static let ledOn = 5.0
  static let lampRGB = V3(0.5776, 1.0, 0.3515)
  static let lampOn = 3.2

  /// Camera: it orbits the target at `dist` (az + = to the viewer's right). frameW x frameH metres stay in frame.
  static let target = V3(0, 0.09, 0)
  static let dist = 0.78
  static let azRest = -8.0
  static let elRest = 68.0
  static let azLean = -3.0
  static let elLean = 64.0
  static let zoomLean = 0.02
  static let frameW = 0.35
  static let frameH = 0.2
  static let near = 0.04
  static let far = 30.0

  /// Light (Filament units). The IBL is Poly Haven's studio_small_08 baked with cmgen.
  static let iblIntensity = 24_000.0
  static let keyDirection = V3(-0.35, -0.85, -0.4)
  static let keyLux = 60_000.0
  static let fillDirection = V3(0.7, -0.4, -0.55)
  static let fillLux = 12_000.0

  // MARK: palette and copy (a dark studio)

  static let backdrop: UInt32 = 0x24211E
  static let panel: UInt32 = 0x1B1A19
  static let card: UInt32 = 0x262523
  static let ink: UInt32 = 0xF3F0EA
  static let mute: UInt32 = 0x9D9890
  static let rec: UInt32 = 0xFF5A4A
  static let channelColours: [UInt32] = [0xFF7A59, 0xF5C04A, 0x4FC3B0, 0xA68BFF]

  static let app = "Booth"
  static let studio = "Studio B · Live"
  static let episode = "Episode 42"
  static let names = ["Host", "Guest", "Music", "SFX"]
  static let presetNames = ["Intro", "Interview", "Outro"]

  // MARK: the film

  /// Intro, Interview, Outro: faders, knobs, mutes.
  static let presets: [[Double]] = [
    [0.62, 0.62, 0.82, 0.5, 0.55, 0.5, 0.45, 0.4, 0, 0, 0, 0],
    [0.75, 0.72, 0.24, 0.36, 0.62, 0.6, 0.35, 0.42, 0, 0, 0, 1],
    [0.55, 0.55, 0.88, 0.6, 0.5, 0.5, 0.6, 0.5, 0, 0, 0, 0],
  ]
  /// The film's rides: channel, to, start, end.
  static let rides: [(c: Int, to: Double, a: Double, b: Double)] = [(2, 0.4, 0.95, 1.95), (0, 0.75, 2.35, 3.25)]
  static let muteTap = (c: 3, at: 3.9)
  static let recalls: [(preset: Int, at: Double)] = [(1, 5.0), (0, 7.4)]
  static let glide = 1.1
  static let stagger = 0.07
  static let muteMove = 0.16
  static let lean = (0.6, 2.4, 8.0, 9.8)

  /// The fader law: travel (0..1) to dB, piecewise linear; 0 dB sits at snapV.
  static let vTab: [Double] = [0, 0.125, 0.25, 0.375, 0.5, 0.625, 0.75, 0.875, 1]
  static let dbTab: [Double] = [-60, -40, -30, -20, -10, -5, 0, 5, 10]
  static let snapV = 0.75
  static let snapZone = 0.03
  static let overpull = 0.035

  static let signalDb: [Double] = [0, -2, -6, -4]
  static let meterFloor = -42.0
  static let meterSpan = 48.0
  static let hits: [Double] = [1.4, 4.6, 6.9, 8.3]
  static let hitDecay = 0.35
  /// Reduced motion's meters: the signal at this moment, and its mix (Interview).
  static let stillT = 2.6
  static let reducedPreset = 1
  /// The REC light breathes this many times a loop.
  static let recBreaths = 3.0

  // MARK: helpers

  static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : x > 1 ? 1 : x }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
  static func lerp(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
  static func smooth(_ x: Double) -> Double { x * x * (3 - 2 * x) }
  static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }

  /// The film's pose at t (12 numbers).
  static func filmPose(_ t: Double) -> [Double] {
    var out = presets[0]
    for r in rides {
      out[r.c] = lerp(out[r.c], r.to, easeInOut(seg(t, r.a, r.b)))
    }
    out[8 + muteTap.c] = lerp(out[8 + muteTap.c], 1, smooth(seg(t, muteTap.at, muteTap.at + muteMove)))
    for r in recalls {
      let p = presets[r.preset]
      for c in 0..<channels {
        let k = easeInOut(seg(t, r.at + Double(c) * stagger, r.at + Double(c) * stagger + glide))
        out[c] = lerp(out[c], p[c], k)
        out[4 + c] = lerp(out[4 + c], p[4 + c], k)
        out[8 + c] = lerp(out[8 + c], p[8 + c], smooth(seg(t, r.at + 0.1, r.at + 0.1 + muteMove)))
      }
    }
    return out
  }

  /// The preset the film's segmented control shows.
  static func filmPreset(_ t: Double) -> Int {
    var p = 0
    for r in recalls where t >= r.at { p = r.preset }
    return p
  }

  /// The film's finger on the desk: opacity, pressed, channel, and whether it taps a mute button.
  static func deskGhost(_ t: Double) -> (opacity: Double, pressed: Double, channel: Int, mute: Bool) {
    for r in rides {
      if t < r.a - 0.35 || t > r.b + 0.35 { continue }
      return (seg(t, r.a - 0.32, r.a - 0.08) * (1 - seg(t, r.b + 0.1, r.b + 0.32)),
              seg(t, r.a - 0.08, r.a) * (1 - seg(t, r.b, r.b + 0.12)), r.c, false)
    }
    let at = muteTap.at
    if t > at - 0.35, t < at + 0.35 {
      return (seg(t, at - 0.32, at - 0.1) * (1 - seg(t, at + 0.1, at + 0.32)),
              seg(t, at - 0.1, at) * (1 - seg(t, at + 0.04, at + 0.18)), muteTap.c, true)
    }
    return (0, 0, 0, false)
  }

  /// The film's finger on the presets: opacity, pressed, preset.
  static func presetGhost(_ t: Double) -> (opacity: Double, pressed: Double, preset: Int) {
    var best = 0
    var dist = 1e9
    for (k, r) in recalls.enumerated() where abs(t - r.at) < dist {
      dist = abs(t - r.at)
      best = k
    }
    let at = recalls[best].at
    return (seg(t, at - 0.32, at - 0.1) * (1 - seg(t, at + 0.1, at + 0.32)),
            seg(t, at - 0.1, at) * (1 - seg(t, at + 0.04, at + 0.18)), recalls[best].preset)
  }

  // MARK: sound

  static func faderDb(_ v: Double) -> Double {
    if v <= 0 { return dbTab[0] }
    for i in 0..<(vTab.count - 1) where v <= vTab[i + 1] {
      return lerp(dbTab[i], dbTab[i + 1], (v - vTab[i]) / (vTab[i + 1] - vTab[i]))
    }
    return dbTab[dbTab.count - 1]
  }

  /// What the panel says for a channel.
  static func dbLabel(_ v: Double, muted: Double) -> String {
    if muted > 0.5 { return "Muted" }
    if v < 0.01 { return "−∞ dB" }
    let r = (faderDb(v) * 10 + 0.5).rounded(.down) / 10
    if r == 0 { return "0.0 dB" }
    return "\(r > 0 ? "+" : "−")\(String(format: "%.1f", abs(r))) dB"
  }

  /// Channel c's signal (0..1) at signal time s.
  static func signalAt(_ c: Int, _ s: Double) -> Double {
    let w = 2 * Double.pi / loop
    let cd = Double(c)
    if c <= 1 {
      let turn = 0.5 + 0.5 * sin(w * 2 * s)
      let mine = c == 0 ? turn : 1 - turn
      let syll = 0.5 + 0.3 * sin(w * (37 + 6 * cd) * s + cd * 1.7) + 0.2 * sin(w * (61 + 4 * cd) * s + cd * 0.6)
      return (0.25 + 0.75 * mine) * syll
    }
    if c == 2 {
      let beat = 0.5 + 0.5 * cos(w * 20 * s)
      return 0.55 + 0.33 * beat * beat * beat * beat + 0.1 * sin(w * 7 * s)
    }
    var e = 0.08
    for h in hits {
      let d = (s - h + loop).truncatingRemainder(dividingBy: loop)
      e = max(e, exp(-d / hitDecay))
    }
    return e
  }

  /// Channel c's meter (0..1 of its LEDs) for its fader, knob and mute.
  static func meterAt(_ c: Int, _ s: Double, fader: Double, knob: Double, mute: Double) -> Double {
    let db = -40 + 40 * signalAt(c, s) + signalDb[c] + faderDb(fader) + (knob - 0.5) * 12
    return clamp01((db - meterFloor) / meterSpan) * (1 - mute)
  }

  static func ledAt(_ level: Double, _ i: Int) -> Double { clamp01(level * Double(leds) - Double(i)) }

  /// A knob's turn (rad, about the deck's up axis) for its value.
  static func knobRad(_ k: Double) -> Double { (knobZeroDeg - k * knobSpanDeg) * Double.pi / 180 }

  // MARK: a finger on a fader

  /// The 0 dB detent: inside snapZone the cap is drawn towards snapV.
  static func magnet(_ v: Double) -> Double {
    let d = v - snapV
    let m = abs(d)
    if m >= snapZone { return v }
    return snapV + d * (m / snapZone) * (m / snapZone)
  }

  /// A dragged fader's value: rubber-banded past either end, then the detent.
  static func faderFromDrag(_ raw: Double) -> Double {
    if raw > 1 {
      let o = raw - 1
      return 1 + o * overpull / (o + overpull)
    }
    if raw < 0 {
      let o = -raw
      return -(o * overpull / (o + overpull))
    }
    return magnet(raw)
  }

  // MARK: camera and projection

  /// How far the camera leans in, 0..1.
  static func leanAt(_ t: Double) -> Double {
    smooth(seg(t, lean.0, lean.1)) * (1 - smooth(seg(t, lean.2, lean.3)))
  }

  /// tan(half the vertical field of view) that keeps frameW x frameH in a stage of this aspect.
  static func tanHalfFovY(_ aspect: Double) -> Double { max(frameH / 2, frameW / 2 / aspect) / dist }

  /// The camera's eye for a lean k. It looks at `target`.
  static func eye(_ k: Double) -> V3 {
    let az = lerp(azRest, azLean, k) * Double.pi / 180
    let el = lerp(elRest, elLean, k) * Double.pi / 180
    let d = dist * (1 - zoomLean * k)
    return target + V3(d * cos(el) * sin(az), d * sin(el), d * cos(el) * cos(az))
  }

  /// A point in the deck's frame to world space.
  static func deckToWorld(_ p: V3) -> V3 {
    let c = cos(deckSlope), s = sin(deckSlope)
    return deckOrigin + V3(p.x, p.y * c - p.z * s, p.y * s + p.z * c)
  }

  /// A world point to stage points (y down) for a lean k.
  static func project(_ p: V3, k: Double, width: Double, height: Double) -> SIMD2<Double> {
    let e = eye(k)
    let f = simd_normalize(target - e)
    let r = simd_normalize(V3(-f.z, 0, f.x))
    let u = V3(-r.z * f.y, r.z * f.x - r.x * f.z, r.x * f.y)
    let d = p - e
    let depth = simd_dot(d, f)
    let aspect = width / height
    let tanY = tanHalfFovY(aspect)
    let ndcX = simd_dot(d, r) / (depth * tanY * aspect)
    let ndcY = simd_dot(d, u) / (depth * tanY)
    return SIMD2((ndcX * 0.5 + 0.5) * width, (0.5 - ndcY * 0.5) * height)
  }

  /// Fader c's grip on screen at travel v.
  static func capOnScreen(_ c: Int, _ v: Double, k: Double, width: Double, height: Double) -> SIMD2<Double> {
    let b = faderBase[c]
    return project(deckToWorld(V3(b.x, capY, b.z - v * travel)), k: k, width: width, height: height)
  }

  /// Mute button c on screen.
  static func muteOnScreen(_ c: Int, k: Double, width: Double, height: Double) -> SIMD2<Double> {
    let m = mutePos[c]
    return project(deckToWorld(V3(m.x, 0.005, m.z)), k: k, width: width, height: height)
  }
}
