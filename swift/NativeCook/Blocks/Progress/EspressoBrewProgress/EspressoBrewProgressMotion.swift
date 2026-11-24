import Foundation
import simd

/// One loop time drives the film: every part of the machine, the cup and the panel is a pure function of t, so t = loop
/// gives the same frame as t = 0. A finger's shot plays the same timeline (from `Phase.brew`), so it looks the same.
///
///  0.00 - 0.75  rest: Single selected, the cup empty, the needle at 0, the group light off
///  0.75         Double tapped on the segmented control
///  1.35         Brew tapped: the group light warms up (to 1.70)
///  1.40 - 2.35  pre-infusion: the needle climbs to 3 bar and waits
///  2.35 - 3.10  full pressure: up to 9 bar, a slight tremble while it holds
///  2.45         first drops: two streams fall from the spouts and fill out by 3.10
///  2.60 - 6.95  the cup fills, the crema rises on top (2.80 - 7.50) and turns slowly
///  6.50         the pump stops: the needle falls (to 7.20), the streams thin and their tails fall (6.80 - 7.05)
///  7.10 - 7.70  the light fades; "Shot ready"
///  7.85 - 8.45  the cup is carried out to the right; a fresh one comes in (8.55 - 9.20)
///  8.95         Single tapped again
///  9.20 - 10.0  rest; last frame = first frame. The camera leans in for the shot (1.5 - 3.3) and back (7.4 - 9.1).
///
/// Ported from espressoScene.ts and espressoMotion.ts of the React Native block.
enum EspressoBrewProgressMotion {
  typealias V3 = SIMD3<Double>

  static let loop = 10.0

  // MARK: the scene (glTF space: y up, metres, the floor is y = 0, the machine faces +z)

  /// The gauge's needle turns about z through this point; 0 bar is `needleZeroDeg` counter-clockwise from straight up.
  static let needlePivot = V3(0.058, 0.262, 0.1269)
  static let needleZeroDeg = 135.0
  static let needleSpanDeg = 270.0
  static let gaugeMaxBar = 15.0

  /// The cup's foot on the grate, the coffee's base inside it, and the spout tips the streams hang from.
  static let cupRest = V3(0, 0.036, 0.0625)
  static let coffeeBase = 0.01
  static let spoutTipY = 0.122
  static let streamTips: [V3] = [V3(-0.0095, 0.122, 0.0625), V3(0.0095, 0.122, 0.0625)]
  /// Mesh heights the app scales: the coffee cylinder, the crema layer and a stream.
  static let coffeeRef = 0.01
  static let cremaRef = 0.005
  static let streamRef = 0.01
  /// Liquid height for a single and a double (coffee and crema together), and the crema's share of it.
  static let doseH = (0.0135, 0.027)
  static let cremaH = (0.0028, 0.0046)
  /// Yield in grams for a single and a double, and how long a shot takes on the machine's own clock (s).
  static let doseG = [18.0, 36.0]
  static let shotS = 28.0
  static let cupAway = 0.36
  static let cupLift = 0.006
  static let cremaSwirl = 1.4
  /// Below this a layer or a stream is not drawn (m).
  static let minH = 0.00015

  /// Camera: it orbits its target at `dist` (az + = to the viewer's right). frameW x frameH metres stay in frame.
  static let targetRest = V3(0, 0.18, 0.01)
  static let targetPeak = V3(0, 0.18, 0.03)
  static let dist = 1.3
  static let azRest = 18.0
  static let elRest = 9.0
  static let azPeak = 12.0
  static let elPeak = 6.0
  static let zoomPeak = 0.02
  static let frameW = 0.42
  static let frameH = 0.42
  static let near = 0.04
  static let far = 30.0

  /// Light (Filament units). The IBL is Poly Haven's studio_small_08 baked with cmgen.
  static let iblIntensity = 30_000.0
  static let keyDirection = V3(-0.45, -0.75, -0.5)
  static let keyLux = 68_000.0
  static let fillDirection = V3(0.7, -0.3, -0.6)
  static let fillLux = 14_000.0
  /// The group head's light band (linear colour) and its emissive strength when fully on.
  static let groupLightRGB = V3(1.0, 0.62, 0.3)
  static let groupLightOn = 6.0

  // MARK: palette and copy

  static let backdrop: UInt32 = 0xE8DFD0
  static let panel: UInt32 = 0xFBF8F2
  static let card: UInt32 = 0xFFFFFF
  static let ink: UInt32 = 0x22262A
  static let mute: UInt32 = 0x6E716F
  static let accent: UInt32 = 0xB5652B

  static let app = "Crema"
  static let machine = "Kitchen · Duo"
  static let recipe = "Morning shot"
  static let doses = ["Single", "Double"]
  static let status = ["Ready", "Pre-infusion", "Extracting", "Shot ready", "Fresh cup"]

  // MARK: the film

  enum Phase {
    static let dose = 0.75
    static let brew = 1.35
    static let lightIn = 1.7
    static let pre = 1.4
    static let preEnd = 2.0
    static let ramp = 2.35
    static let rampEnd = 3.1
    static let stream = 2.45
    static let streamHit = 2.72
    static let streamFull = 3.1
    static let fill = 2.6
    static let fillEnd = 6.95
    static let crema = 2.8
    static let cremaEnd = 7.5
    static let stop = 6.5
    static let dropEnd = 7.2
    static let thinEnd = 6.8
    static let tailEnd = 7.05
    static let lightOut = 7.1
    static let lightOutEnd = 7.7
    /// A finger's shot stops here, with the cup full.
    static let full = 7.75
    static let cupOut = 7.85
    static let cupOutEnd = 8.45
    /// The cup is out of frame: from here it is the fresh, empty one.
    static let swap = 8.5
    static let cupIn = 8.55
    static let cupInEnd = 9.2
    static let single = 8.95
    static let camIn = 1.5
    static let camInEnd = 3.3
    static let camOut = 7.4
    static let camOutEnd = 9.1
  }

  /// The film's taps: time and what (0 = Single, 1 = Double, 2 = the Brew button).
  static let taps: [(Double, Int)] = [(Phase.dose, 1), (Phase.brew, 2), (Phase.single, 0)]

  static let preBar = 3.0
  static let fullBar = 9.0
  static let trembleBar = 0.14
  static let trembleCycles = 9.0
  static let streamLag = 0.06
  static let streamThin = 0.45
  static let streamEnd = 0.5

  // MARK: helpers

  static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : x > 1 ? 1 : x }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
  static func lerp(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
  static func smooth(_ x: Double) -> Double { x * x * (3 - 2 * x) }
  static func easeOut(_ x: Double) -> Double {
    let k = 1 - x
    return 1 - k * k * k
  }

  /// The selected dose in the film: 0 = Single, 1 = Double.
  static func filmDose(_ t: Double) -> Int { t >= Phase.dose && t < Phase.single ? 1 : 0 }

  /// The pump's pressure (bar).
  static func barAt(_ t: Double) -> Double {
    let pre = preBar * easeOut(seg(t, Phase.pre, Phase.preEnd))
    let full = (fullBar - preBar) * smooth(seg(t, Phase.ramp, Phase.rampEnd))
    let hold = smooth(seg(t, Phase.rampEnd, Phase.rampEnd + 0.4)) * (1 - smooth(seg(t, Phase.stop - 0.3, Phase.stop)))
    let tremble = trembleBar * hold * sin(2 * Double.pi * trembleCycles * t / loop)
    return (pre + full + tremble) * (1 - smooth(seg(t, Phase.stop, Phase.dropEnd)))
  }

  /// The needle's angle (deg, counter-clockwise from straight up) for a pressure.
  static func needleDeg(_ bar: Double) -> Double { needleZeroDeg - bar / gaugeMaxBar * needleSpanDeg }

  /// The group head's light, 0..1.
  static func lightAt(_ t: Double) -> Double {
    smooth(seg(t, Phase.brew, Phase.lightIn)) * (1 - smooth(seg(t, Phase.lightOut, Phase.lightOutEnd)))
  }

  /// How full the cup is, 0..1 of the dose (the fresh cup after the swap is empty).
  static func fillAt(_ t: Double) -> Double { t >= Phase.swap ? 0 : smooth(seg(t, Phase.fill, Phase.fillEnd)) }

  /// How much of its crema the shot has, 0..1.
  static func cremaAt(_ t: Double) -> Double { t >= Phase.swap ? 0 : smooth(seg(t, Phase.crema, Phase.cremaEnd)) }

  /// The crema's turn (rad).
  static func swirlAt(_ t: Double) -> Double { cremaSwirl * smooth(seg(t, Phase.fill, Phase.cremaEnd + 0.1)) }

  /// Stream i: how far down the gap from spout to surface its head and its tail are (0..1), and its width as a share
  /// of a full stream.
  static func streamAt(_ t: Double, _ i: Int) -> (head: Double, tail: Double, width: Double) {
    let lag = Double(i) * streamLag
    let h = seg(t, Phase.stream + lag, Phase.streamHit + lag)
    let tl = seg(t, Phase.thinEnd + lag, Phase.tailEnd + lag)
    let grow = lerp(streamThin, 1, smooth(seg(t, Phase.stream + lag, Phase.streamFull)))
    let w = t < Phase.stream + lag ? 0 : grow * (1 - (1 - streamEnd) * smooth(seg(t, Phase.stop, Phase.thinEnd)))
    return (h * h, tl * tl, w)
  }

  /// The cup's slide for the change: x (m) and lift (m).
  static func cupAt(_ t: Double) -> (x: Double, lift: Double) {
    if t < Phase.cupIn {
      let k = seg(t, Phase.cupOut, Phase.cupOutEnd)
      return (cupAway * smooth(k), cupLift * sin(Double.pi * k))
    }
    let k = seg(t, Phase.cupIn, Phase.cupInEnd)
    return (cupAway * (1 - smooth(k)), cupLift * sin(Double.pi * k))
  }

  /// The machine's shot timer (its own seconds), reset with the fresh cup.
  static func shotSecondsAt(_ t: Double) -> Double { t >= Phase.swap ? 0 : shotS * seg(t, Phase.brew, Phase.stop) }

  /// The status line's index into `status`.
  static func statusAt(_ t: Double) -> Int {
    if t < Phase.brew { return 0 }
    if t < Phase.ramp { return 1 }
    if t < Phase.stop { return 2 }
    if t < Phase.cupOut { return 3 }
    if t < Phase.cupInEnd { return 4 }
    return 0
  }

  /// The film's finger: opacity, pressed, target (0 Single, 1 Double, 2 Brew).
  static func tapGhost(_ t: Double) -> (opacity: Double, pressed: Double, target: Int) {
    var best = 0
    var dist = 1e9
    for (k, tap) in taps.enumerated() where abs(t - tap.0) < dist {
      dist = abs(t - tap.0)
      best = k
    }
    let at = taps[best].0
    let o = seg(t, at - 0.32, at - 0.1) * (1 - seg(t, at + 0.1, at + 0.32))
    let p = seg(t, at - 0.1, at) * (1 - seg(t, at + 0.04, at + 0.18))
    return (o, p, taps[best].1)
  }

  /// The liquid in the cup for a time and a dose (0 single .. 1 double): coffee body and crema heights (m).
  static func liquid(_ t: Double, dose: Double) -> (body: Double, crema: Double, total: Double) {
    let total = lerp(doseH.0, doseH.1, dose) * fillAt(t)
    let crema = min(total, lerp(cremaH.0, cremaH.1, dose) * cremaAt(t))
    return (total - crema, crema, total)
  }

  // MARK: camera

  /// How far the camera has leaned in towards the cup, 0..1.
  static func camAt(_ t: Double) -> Double {
    smooth(seg(t, Phase.camIn, Phase.camInEnd)) * (1 - smooth(seg(t, Phase.camOut, Phase.camOutEnd)))
  }

  /// tan(half the vertical field of view) that keeps frameW x frameH in a stage of this aspect.
  static func tanHalfFovY(_ aspect: Double) -> Double { max(frameH / 2, frameW / 2 / aspect) / dist }

  /// The camera for a lean k and a finger's orbit (deg).
  static func cameraAt(_ k: Double, orbit: Double) -> (eye: V3, target: V3) {
    let az = (lerp(azRest, azPeak, k) + orbit) * Double.pi / 180
    let el = lerp(elRest, elPeak, k) * Double.pi / 180
    let d = dist * (1 - zoomPeak * k)
    let tg = V3(lerp(targetRest.x, targetPeak.x, k), lerp(targetRest.y, targetPeak.y, k), lerp(targetRest.z, targetPeak.z, k))
    return (tg + V3(d * cos(el) * sin(az), d * sin(el), d * cos(el) * cos(az)), tg)
  }

  /// A finger's orbit (deg) from its drag (pt), rubber-banded past orbitMax.
  static let orbitSpan = 70.0
  static let orbitMax = 22.0
  static func orbitFromDrag(_ from: Double, dx: Double, width: Double) -> Double {
    let a = from + dx / width * orbitSpan
    let m = abs(a)
    if m <= orbitMax { return a }
    let over = m - orbitMax
    return (a < 0 ? -1 : 1) * (orbitMax + over * 8 / (over + 8))
  }
}
