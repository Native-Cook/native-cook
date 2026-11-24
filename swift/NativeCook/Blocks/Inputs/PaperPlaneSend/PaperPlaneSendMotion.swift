import Foundation
import simd

// Paper Plane Send: the scene's fixed numbers and the film, ported from React Native's planeScene.ts and
// planeMotion.ts. One loop time drives everything: the typing, the 2D paper, the fold, the plane's pose on screen
// and the thread. Every value is a pure function of t (and of the screen's geometry), so t = loop gives the same
// frame as t = 0, and a finger only moves the clock (to the send) and sets the throw's size.
//
//  0.0 – 0.6   rest: three bubbles, an empty composer
//  0.6 – 2.3   the message types itself into the field (no keyboard); the send button lights up
//  2.3 – 2.6   a finger arrives on the send button, 2.6 it presses
//  2.7 – 3.1   the message leaves the field as a sheet of paper: it rises and grows to the sheet's size
//  3.06 – 3.16 hand-off: the flat 3D sheet fades in over the 2D paper (same rectangle), the 2D paper fades out
//  3.12 – 4.52 the fold (1.4 s): corners in, edges in, halves up, wings down, while the sheet lies back and its nose
//              swings up to the launch heading; a small dip before the throw
//  4.52 – 5.72 the flight (1.2 s): up, over the thread and down into the new message's place, banking, shrinking
//  5.42 – 5.72 the thread moves up to make room
//  5.68        the sent bubble pops in on a spring as the plane fades into it (haptic); 6.0 "Delivered"
//  7.4 – 8.2   reset: the bubble and its receipt fade, the thread settles back
//  8.2 – 9.0   rest; last frame = first frame
enum PaperPlaneSendMotion {
  typealias V3 = SIMD3<Double>

  static let loop: Double = 9

  // MARK: scene (planeScene.ts)

  /// The sheet (metres): sheetL along the nose axis (+x), sheetW across (z), the top face up (+y).
  static let sheetL = 0.21
  static let sheetW = 0.148

  static let foldS = 1.4
  enum Step {
    case corner, second, half, wing
    /// Seconds within the fold for each step; every hinge turns 0 → its angle with an ease-in-out cubic.
    var span: (Double, Double) {
      switch self {
      case .corner: return (0, 0.42)
      case .second: return (0.32, 0.76)
      case .half: return (0.68, 1.08)
      case .wing: return (0.98, 1.4)
      }
    }
  }

  /// Every hinged panel: its node, where its hinge sits in its parent (the node's translation, metres), the hinge's
  /// axis in the parent's frame, and its angle when folded (deg). R / L are the two halves; K / W the keel's and the
  /// wing's side of the wing crease. Parents: Keel > Wing, FlapBK > FlapC1K > FlapC2K; Wing > FlapBW > FlapC1W > FlapC2W.
  struct Hinge { let node: String; let pivot: V3; let axis: V3; let deg: Double; let step: Step }
  static let hinges: [Hinge] = [
    Hinge(node: "KeelR", pivot: V3(0, -0.0005, 0), axis: V3(1, 0, 0), deg: -86, step: .half),
    Hinge(node: "WingR", pivot: V3(0.105, 0.00025, 0), axis: V3(-0.992049, 0, 0.125849), deg: -77, step: .wing),
    Hinge(node: "FlapBKR", pivot: V3(0.105, 0.001, 0), axis: V3(-0.92388, 0, 0.382683), deg: 179, step: .second),
    Hinge(node: "FlapBWR", pivot: V3(0, 0.00075, 0), axis: V3(-0.92388, 0, 0.382683), deg: 179, step: .second),
    Hinge(node: "FlapC1KR", pivot: V3(0, -0.0005, 0), axis: V3(-0.707107, 0, 0.707107), deg: 179, step: .corner),
    Hinge(node: "FlapC1WR", pivot: V3(0, -0.0005, 0), axis: V3(-0.707107, 0, 0.707107), deg: 179, step: .corner),
    Hinge(node: "FlapC2KR", pivot: V3(0, -0.0005, 0), axis: V3(-0.382683, 0, 0.92388), deg: -179, step: .second),
    Hinge(node: "FlapC2WR", pivot: V3(0, -0.0005, 0), axis: V3(-0.382683, 0, 0.92388), deg: -179, step: .second),
    Hinge(node: "KeelL", pivot: V3(0, -0.0005, 0), axis: V3(1, 0, 0), deg: 86, step: .half),
    Hinge(node: "WingL", pivot: V3(0.105, 0.00025, 0), axis: V3(-0.992049, 0, -0.125849), deg: 77, step: .wing),
    Hinge(node: "FlapBKL", pivot: V3(0.105, 0.001, 0), axis: V3(-0.92388, 0, -0.382683), deg: -179, step: .second),
    Hinge(node: "FlapBWL", pivot: V3(0, 0.00075, 0), axis: V3(-0.92388, 0, -0.382683), deg: -179, step: .second),
    Hinge(node: "FlapC1KL", pivot: V3(0, -0.0005, 0), axis: V3(-0.707107, 0, -0.707107), deg: -179, step: .corner),
    Hinge(node: "FlapC1WL", pivot: V3(0, -0.0005, 0), axis: V3(-0.707107, 0, -0.707107), deg: -179, step: .corner),
    Hinge(node: "FlapC2KL", pivot: V3(0, -0.0005, 0), axis: V3(-0.382683, 0, -0.92388), deg: 179, step: .second),
    Hinge(node: "FlapC2WL", pivot: V3(0, -0.0005, 0), axis: V3(-0.382683, 0, -0.92388), deg: 179, step: .second),
  ]

  /// Camera: straight at the plane z = 0, where one metre is the same number of points everywhere on screen.
  static let vfovDeg = 24.0
  static let near = 0.05
  static let far = 20.0

  /// Light (Filament units).
  static let iblIntensity = 15_000.0
  static let keyDirection = V3(0.42, -0.5, -0.76)
  static let keyLux = 70_000.0
  static let fillDirection = V3(-0.6, 0.35, -0.72)
  static let fillLux = 9_000.0

  /// Palette (sRGB).
  static let bg: UInt32 = 0x191C22
  static let bgTop: UInt32 = 0x20242C
  static let field: UInt32 = 0x272B34
  static let bubbleIn: UInt32 = 0x2D313B
  static let bubbleOut: UInt32 = 0x2E6BE6
  static let ink: UInt32 = 0xF2F3F5
  static let mute: UInt32 = 0xAEB3BE
  static let paper: UInt32 = 0xEFECE3
  static let paperInk: UInt32 = 0x24262B

  /// Copy: an invented chat app and a short thread.
  static let app = "Pidgin"
  static let contact = "Ines Varga"
  static let contactInitials = "IV"
  static let thread: [(out: Bool, text: String)] = [
    (false, "Are you boarding yet?"),
    (true, "Just sat down. Window seat"),
    (false, "Nice. What time do you land?"),
  ]
  static let message = "Landing at 6:40, save me a seat"
  static let delivered = "Delivered"

  // MARK: film (planeMotion.ts)

  static let type = 0.6, typeEnd = 2.3, tap = 2.6, lift = 2.7, liftEnd = 3.1
  static let fold = 3.12, foldEnd = 3.12 + foldS
  static let flight = 3.12 + foldS, flightEnd = 3.12 + foldS + 1.2
  static let pop = 3.12 + foldS + 1.16
  static let deliveredAtT = 6.0, reset = 7.4, resetEnd = 8.2

  static let reducedT = 6.8
  static let handoff = 0.06
  static let tiltFold = 38.0, rollFold = -16.0, rise = 26.0, dip = 9.0
  static let tiltFly = 20.0, bank = 34.0, scaleEnd = 0.2
  static let popZ = 13.0, popW = 24.0, popFrom = 0.55
  static let throwMax = 1.35

  /// The screen's geometry the motion needs (pt): the screen, the sheet's centre and width, the centre of the new
  /// message's place in the thread, and the throw's size (1 .. throwMax).
  struct Geo: Equatable {
    var w = 393.0, h = 852.0
    var sheetX = 196.5, sheetY = 655.0
    var slotX = 250.0, slotY = 713.0
    var sheetPt = 196.5
    var amp = 1.0
  }

  static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : x > 1 ? 1 : x }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
  static func lerp(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
  static func easeInOutCubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
  static func easeInOutSine(_ x: Double) -> Double { -(cos(Double.pi * x) - 1) / 2 }
  static func smooth(_ x: Double) -> Double { x * x * (3 - 2 * x) }

  static func popStep(_ tau: Double) -> Double {
    if tau <= 0 { return 0 }
    return 1 - exp(-popZ * tau) * (cos(popW * tau) + popZ / popW * sin(popW * tau))
  }

  /// One Catmull-Rom coordinate between p1 and p2 (u 0..1), and its derivative.
  static func catmull(_ p0: Double, _ p1: Double, _ p2: Double, _ p3: Double, _ u: Double) -> (v: Double, d: Double) {
    let a = -p0 + 3 * p1 - 3 * p2 + p3
    let b = 2 * p0 - 5 * p1 + 4 * p2 - p3
    let c = p2 - p0
    return (0.5 * (a * u * u * u + b * u * u + c * u + 2 * p1), 0.5 * (3 * a * u * u + 2 * b * u + c))
  }

  // the composer

  /// How many characters of the message the field shows.
  static func typedCount(_ t: Double) -> Int {
    if t >= lift { return 0 }
    return Int(floor(seg(t, type, typeEnd) * Double(message.count) + 1e-6))
  }

  /// The film's finger on the send button: opacity, pressed 0..1.
  static func ghostAt(_ t: Double) -> (opacity: Double, pressed: Double) {
    (seg(t, tap - 0.3, tap - 0.06) * (1 - seg(t, tap + 0.12, tap + 0.36)),
     seg(t, tap - 0.06, tap + 0.02) * (1 - seg(t, tap + 0.06, tap + 0.18)))
  }

  // the 2D paper and the hand-off

  static func liftAt(_ t: Double) -> Double { easeInOutCubic(seg(t, lift, liftEnd)) }
  static func paperAlpha(_ t: Double) -> Double { seg(t, lift, lift + 0.06) * (1 - seg(t, liftEnd + 0.02, liftEnd + 0.02 + handoff)) }
  static func wordsAlpha(_ t: Double) -> Double { 1 - seg(t, lift + 0.1, liftEnd - 0.08) }
  /// The 3D layer's opacity: in over the flat 2D paper, out into the bubble's pop.
  static func stageAlpha(_ t: Double) -> Double { seg(t, liftEnd - 0.04, liftEnd - 0.04 + handoff) * (1 - seg(t, pop - 0.02, pop + 0.06)) }
  /// Whether the 3D part of the film is on (outside it the plane is parked).
  static func stageOn(_ t: Double) -> Bool { t >= liftEnd - 0.1 && t <= flightEnd + 0.1 }
  static func foldTime(_ t: Double) -> Double { min(foldS, max(0, t - fold)) }

  // the flight

  /// The flight path at s (0..1), screen points, and its tangent. A Catmull-Rom curve from the sheet up, over the
  /// thread and down into the slot; a throw makes the arc bigger.
  static func pathAt(_ s: Double, _ g: Geo) -> (x: Double, y: Double, dx: Double, dy: Double) {
    let a = g.amp
    let x0 = g.sheetX, y0 = g.sheetY - rise
    let xs = [x0, x0 - 0.16 * g.w * a, x0 + 0.02 * g.w, x0 + 0.3 * g.w, g.slotX]
    let ys = [y0, y0 - 0.22 * g.h * a, y0 - 0.4 * g.h * a, y0 - 0.27 * g.h * a, g.slotY]
    let f = clamp01(s) * 4
    let i = min(3, Int(floor(f)))
    let u = f - Double(i)
    let xa = i == 0 ? 2 * xs[0] - xs[1] : xs[i - 1]
    let ya = i == 0 ? 2 * ys[0] - ys[1] : ys[i - 1]
    let xd = i == 3 ? 2 * xs[4] - xs[3] : xs[i + 2]
    let yd = i == 3 ? 2 * ys[4] - ys[3] : ys[i + 2]
    let px = catmull(xa, xs[i], xs[i + 1], xd, u)
    let py = catmull(ya, ys[i], ys[i + 1], yd, u)
    return (px.v, py.v, px.d, py.d)
  }

  static func flightAt(_ t: Double) -> Double { smooth(seg(t, flight, flightEnd)) }

  /// The plane on screen: x, y (pt), heading (deg, 0 = nose right, 90 = up), tilt (deg, the top edge away), roll
  /// (deg, + = right wing down), scale. The flat sheet is (sheet centre, 0, 0, 0, 1).
  struct Pose { var x = 0.0, y = 0.0, heading = 0.0, tilt = 0.0, roll = 0.0, scale = 1.0 }

  static func poseAt(_ t: Double, _ g: Geo) -> Pose {
    if t < flight {
      let p = pathAt(0, g)
      let launch = atan2(-p.dy, p.dx) * 180 / Double.pi
      let k = easeInOutCubic(seg(t, fold, foldEnd))
      return Pose(x: g.sheetX, y: g.sheetY - rise * k + dip * sin(Double.pi * seg(t, foldEnd - 0.24, foldEnd)),
                  heading: launch * k, tilt: tiltFold * easeInOutSine(seg(t, fold, fold + 0.9)), roll: rollFold * k, scale: 1)
    }
    let s = flightAt(t)
    let p = pathAt(s, g)
    return Pose(x: p.x, y: p.y, heading: atan2(-p.dy, p.dx) * 180 / Double.pi, tilt: lerp(tiltFold, tiltFly, s),
                roll: rollFold * (1 - clamp01(s * 3)) + bank * sin(Double.pi * s), scale: 1 - (1 - scaleEnd) * pow(s, 2.2))
  }

  /// The plane's soft shadow on the chat: centre, radii (pt), opacity. It slides away and thins as the plane climbs.
  static func shadowAt(_ t: Double, _ g: Geo) -> (x: Double, y: Double, rx: Double, ry: Double, alpha: Double) {
    let p = poseAt(t, g)
    let s = t < flight ? 0 : flightAt(t)
    let f = seg(t, fold, foldEnd)
    let up = f * 0.35 + s * 0.65
    let size = g.sheetPt * p.scale
    // flat against the screen the sheet throws none: it grows out from under the paper as it lifts and lies back
    let r = easeInOutSine(seg(t, fold + 0.1, fold + 0.7))
    return (p.x + 28 * up * p.scale, p.y + (22 * r + 40 * up) * p.scale,
            size * lerp(0.3, 0.42, r) * lerp(1, 0.8, f), size * lerp(0.2, 0.3, r) * lerp(1, 0.7, f),
            stageAlpha(t) * r * lerp(0.7, 0.3, up))
  }

  // the thread

  static func shiftAt(_ t: Double) -> Double { easeInOutCubic(seg(t, flightEnd - 0.3, flightEnd)) - easeInOutCubic(seg(t, reset, resetEnd)) }
  /// The sent bubble: opacity, scale.
  static func bubbleAt(_ t: Double) -> (opacity: Double, scale: Double) {
    (seg(t, pop, pop + 0.08) * (1 - seg(t, reset, reset + 0.4)), popFrom + (1 - popFrom) * popStep(t - pop))
  }
  static func deliveredAt(_ t: Double) -> Double { seg(t, deliveredAtT, deliveredAtT + 0.25) * (1 - seg(t, reset, reset + 0.3)) }
  /// The send button: lit while there is something to send.
  static func sendLitAt(_ t: Double) -> Double { seg(t, type, type + 0.18) * (1 - seg(t, lift, lift + 0.18)) }
}
