import Foundation
import simd

// Rocker Light Switch: the scene's fixed numbers and the film, ported from React Native's switchScene.ts and
// switchMotion.ts. glTF space: y up, metres, the wall is the plane z = 0 and faces the camera (+z).
//
// One number drives the switch: `press`, 0 (off: the bottom half in) .. 1 (on: the top half in). The film is
// press = filmPress(t); a finger moves the same number. The light follows the latch (press >= 0.5) the way a
// filament does: a short lag, then an ease-out warm-up. Every value is a pure function of t, so t = loop gives the
// same frame as t = 0.
//
//  0.0 – 0.8   poster: the room at dusk, the switch off, the camera drifting
//  0.8 – 1.22  a finger presses the top half: the rocker follows the press 40 % of the way
//  1.22        it tips past the middle and snaps home on a spring (click); 120 ms later the filament warms up
//  2.0 – 4.4   the camera eases a few degrees round the wall
//  2.5 – 3.2   the brightness slider goes down to 40 %,  3.7 – 4.4 back up to 100 %
//  4.9 – 6.9   the camera eases back
//  5.2 – 5.62  the finger presses the bottom half; at 5.62 it snaps off (click), the light dies in 0.3 s
//  6.9 – 8.0   poster; last frame = first frame
enum RockerLightSwitchMotion {
  typealias V3 = SIMD3<Double>

  static let loop: Double = 8

  // MARK: scene (switchScene.ts)

  /// The rocker's axis (the node "Rocker"): it turns about x through this point.
  static let rockerPivot = V3(0.032, -0.082, 0.004)
  /// The rocker rests at +rock (off: bottom half in) or -rock (on: top half in).
  static let rock = 7 * Double.pi / 180
  static let rockerHalfH = 0.036
  static let rockerFaceZ = 0.0052
  static let plateHalfW = 0.043
  static let plateHalfH = 0.061

  /// The sconce's opal globe: centre and radius. The bulb sits at its centre.
  static let globe = V3(-0.066, 0.062, 0.1)
  static let globeR = 0.043

  static let target = V3(-0.012, 0.023, 0)
  static let eyeRest = V3(0.085, -0.005, 0.66)
  static let eyeOrbit = V3(-0.07, 0.005, 0.662)
  static let frameW = 0.305
  static let frameH = 0.44
  static let near = 0.02
  static let far = 10.0

  /// Light (Filament units).
  static let iblIntensity = 9_000.0
  static let bulbLumens = 42_000.0
  static let bulbFalloff = 2.5
  static let bounceDirection = V3(0.35, -0.3, -0.89)
  static let bounceLux = 16_000.0
  static let globeEmissive = 7.0
  static let shadowRGB = V3(0.0242, 0.0152, 0.0103)
  static let warmLow = V3(1, 0.42, 0.14)
  static let warmFull = V3(1, 0.78, 0.52)

  /// Palette (sRGB).
  static let dusk: UInt32 = 0x16233F
  static let panel: UInt32 = 0xF5F2EC
  static let card: UInt32 = 0xFFFFFF
  static let ink: UInt32 = 0x1D1B18
  static let mute: UInt32 = 0x7A746B
  static let amber: UInt32 = 0xE39B2E

  /// Copy: an invented smart-home app.
  static let app = "Lumen Home"
  static let room = "Living room"
  static let lightName = "Wall sconce"
  static let otherLightsOn = 2

  // MARK: film (switchMotion.ts)

  static let pressOn = 0.8, snapOn = 1.22
  static let orbitOut = 2.0, orbitOutEnd = 4.4
  static let dim = 2.5, dimEnd = 3.2, bright = 3.7, brightEnd = 4.4
  static let orbitBack = 4.9, orbitBackEnd = 6.9
  static let pressOff = 5.2, snapOff = 5.62

  static let pressDepth = 0.4
  static let pressTime = snapOn - pressOn
  /// The snap: x(τ) = 1 - e^(-Zτ) (cos Wτ + Z/W sin Wτ) (mass 1, stiffness 1096, damping 28).
  static let snapZ = 14.0, snapW = 30.0
  static let lag = 0.12, warm = 0.52, lagOff = 0.06, cool = 0.3
  static let dimTo = 0.4
  static let brightMin = 0.1
  static let idle = 2.0
  static let returnTime = 1.1
  static let ghostIn = 0.3, ghostOut = 0.3

  static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : x > 1 ? 1 : x }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
  static func lerp(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
  static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
  static func easeInQuad(_ x: Double) -> Double { x * x }
  static func easeInOutSine(_ x: Double) -> Double { -(cos(Double.pi * x) - 1) / 2 }

  static func snapStep(_ tau: Double) -> Double {
    if tau <= 0 { return 0 }
    return 1 - exp(-snapZ * tau) * (cos(snapW * tau) + snapZ / snapW * sin(snapW * tau))
  }

  /// One flip, τ seconds after the finger starts pressing: follows the press to pressDepth, then snaps to 1.
  static func flipCurve(_ tau: Double) -> Double {
    if tau <= 0 { return 0 }
    if tau < pressTime { return pressDepth * easeInQuad(tau / pressTime) }
    return pressDepth + (1 - pressDepth) * snapStep(tau - pressTime)
  }

  /// A finger dragging the rocker: it resists near either rest and tips quickly through the middle.
  static func detent(_ x: Double) -> Double { x - 0.1 * sin(2 * Double.pi * x) }

  static func detentInverse(_ p: Double) -> Double {
    var lo = 0.0, hi = 1.0
    for _ in 0..<14 {
      let mid = (lo + hi) / 2
      if detent(mid) < p { lo = mid } else { hi = mid }
    }
    return (lo + hi) / 2
  }

  static func filmPress(_ t: Double) -> Double {
    t < pressOff ? flipCurve(t - pressOn) : 1 - flipCurve(t - pressOff)
  }

  static func filmLevel(_ t: Double) -> Double {
    let up = easeOutCubic(seg(t, snapOn + lag, snapOn + lag + warm))
    let down = easeOutCubic(seg(t, snapOff + lagOff, snapOff + lagOff + cool))
    return up * (1 - down)
  }

  static func filmBright(_ t: Double) -> Double {
    let k = easeInOutSine(seg(t, dim, dimEnd)) - easeInOutSine(seg(t, bright, brightEnd))
    return 1 - (1 - dimTo) * k
  }

  static func filmOrbit(_ t: Double) -> Double {
    easeInOutSine(seg(t, orbitOut, orbitOutEnd)) - easeInOutSine(seg(t, orbitBack, orbitBackEnd))
  }

  /// The touch dot: opacity 0..1, half (+1 the top half, -1 the bottom), pressed 0..1.
  static func filmGhost(_ t: Double) -> (opacity: Double, half: Double, pressed: Double) {
    let second = t > (snapOn + pressOff) / 2
    let a = second ? pressOff : pressOn
    let b = second ? snapOff : snapOn
    return (seg(t, a - ghostIn, a) * (1 - seg(t, b + 0.05, b + 0.05 + ghostOut)), second ? -1 : 1,
            seg(t, a, b) * (1 - seg(t, b, b + 0.12)))
  }

  /// What the scene shows: the filament times the brightness.
  static func lightOut(_ level: Double, _ bright: Double) -> Double { clamp01(level) * clamp01(bright) }

  /// The light's colour (linear RGB) at an output k: orange while it barely glows, warm white at full.
  static func warmColor(_ k: Double) -> V3 {
    let q = sqrt(clamp01(k))
    return warmLow + (warmFull - warmLow) * q
  }

  // MARK: camera

  struct Shot { var eye: V3; var target: V3 }

  static let dist = simd_length(eyeRest - target)

  /// tan(half the vertical field of view) that keeps frameW x frameH of wall in a stage of this aspect (w / h).
  static func tanHalfFovY(_ aspect: Double) -> Double { max(frameH / 2, frameW / 2 / aspect) / dist }

  /// Camera at orbit k and film time t. A slow drift (period loop) keeps it alive.
  static func cameraAt(_ k: Double, _ t: Double) -> Shot {
    let w = 2 * Double.pi * t / loop
    return Shot(eye: V3(lerp(eyeRest.x, eyeOrbit.x, k) + 0.006 * sin(w),
                        lerp(eyeRest.y, eyeOrbit.y, k) + 0.004 * sin(2 * w),
                        lerp(eyeRest.z, eyeOrbit.z, k)), target: target)
  }

  /// A world point on the stage (w x h pt): x, y and the points per metre there.
  static func project(_ c: Shot, _ w: Double, _ h: Double, _ p: V3) -> (x: Double, y: Double, ppm: Double) {
    let f = simd_normalize(c.target - c.eye)
    let r = simd_normalize(V3(-f.z, 0, f.x))
    let u = V3(-r.z * f.y, r.z * f.x - r.x * f.z, r.x * f.y)
    let d = p - c.eye
    let depth = simd_dot(d, f)
    let ppm = h / 2 / (tanHalfFovY(w / h) * depth)
    return (w / 2 + simd_dot(d, r) * ppm, h / 2 - simd_dot(d, u) * ppm, ppm)
  }
}
