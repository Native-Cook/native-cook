import SwiftUI

// Port of flare-palette.ts: the three notes, the
// page's neutrals and every motion beat, in ms and in pill heights.
//
// The neutrals were sampled from the reference clip (4K frames); they lean
// violet (OKLCH h≈301, C≈0.005), which stops the dark pill reading as plain
// gray next to the magenta. The flames themselves are fitted blob animations
// (`flare-blobs.json`), so they carry the reference's own colors.

/// An sRGB color with straight alpha, as RN parses `#RRGGBB` / `rgba(...)`.
struct FlareRGBA: Equatable, Sendable {
  var r: Double, g: Double, b: Double, a: Double = 1

  /// `#RRGGBB`.
  init(hex: String) {
    let n = UInt32(hex.dropFirst(), radix: 16) ?? 0
    r = Double((n >> 16) & 255) / 255
    g = Double((n >> 8) & 255) / 255
    b = Double(n & 255) / 255
    a = 1
  }

  /// `rgba(r,g,b,a)` with 0...255 channels.
  init(_ r: Double, _ g: Double, _ b: Double, _ a: Double) {
    self.r = r / 255
    self.g = g / 255
    self.b = b / 255
    self.a = a
  }

  var color: Color { Color(.sRGB, red: r, green: g, blue: b, opacity: a) }
  var uiColor: UIColor { UIColor(red: r, green: g, blue: b, alpha: a) }
  /// `hexToRgb`: the body as 0...1 floats for the shader.
  var rgb: SIMD3<Float> { SIMD3(Float(r), Float(g), Float(b)) }
}

enum FlareTone {
  /// The reference's page: a cool gray with the same violet lean.
  static let page = FlareRGBA(hex: "#ECEDF4")
  /// Hint and index text on that page (#737373 only reaches 4.1:1 there).
  static let pageHint = FlareRGBA(hex: "#666569")
  static let dark = FlareRGBA(hex: "#212023")
  static let mist = FlareRGBA(hex: "#F6F5F8")
  static let white = FlareRGBA(hex: "#FFFFFF")
  /// On the dark pill: the reference's #A1A0A3 (6.2:1).
  static let subDark = FlareRGBA(hex: "#A1A0A3")
  /// On the light pills. The reference used #89898B, which only reaches 3.2:1
  /// on #F6F5F8; this keeps the hue and clears 4.5:1.
  static let subLight = FlareRGBA(hex: "#6B6A70")
  static let inkLight = FlareRGBA(hex: "#0C0B0E")
  static let inkDark = FlareRGBA(hex: "#FFFFFF")
  /// `ui.tsx`: the "Deliver again" pill and its ink.
  static let graphite = FlareRGBA(hex: "#2C2C2C")
  static let onGraphite = FlareRGBA(hex: "#EEEEEE")
}

struct Flare: Identifiable, Sendable {
  enum ID: String, Sendable { case kiln, tide, bloom }

  let id: ID
  let title: String
  /// The note's status line, localized (`t(line[0], line[1])`).
  let lineKey: String
  let body: FlareRGBA
  let ink: FlareRGBA
  let sub: FlareRGBA
  /// The cast shadow's color; the reference draws no rim stroke.
  let shadow: FlareRGBA
}

let FLARES: [Flare] = [
  Flare(id: .kiln, title: "KILN.*", lineKey: "line.kiln", body: FlareTone.dark, ink: FlareTone.inkDark,
        sub: FlareTone.subDark, shadow: FlareRGBA(22, 20, 34, 0.30)),
  Flare(id: .tide, title: "TIDE*", lineKey: "line.tide", body: FlareTone.mist, ink: FlareTone.inkLight,
        sub: FlareTone.subLight, shadow: FlareRGBA(22, 20, 34, 0.11)),
  Flare(id: .bloom, title: "BLOOM.*", lineKey: "line.bloom", body: FlareTone.white, ink: FlareTone.inkLight,
        sub: FlareTone.subLight, shadow: FlareRGBA(22, 20, 34, 0.10)),
]

/// `flareMotion`: the motion beats, in ms and in pill heights, shared by the
/// views and the tests so the arrival choreography can't drift from what's
/// documented.
enum FlareMotion {
  // Measured on the 4K frames: the pill is 3.66:1, pills sit 0.39 heights
  // apart, the copy starts 0.35 heights in and the title's cap height is
  // 0.2 heights.
  static let height = 84.0
  static let aspect = 3.66
  static let gap = 32.0
  static let inset = 29.0
  static let stagger = 70.0
  static let enter = 320.0
  static let igniteDelay = 60.0
  static let flash = 160.0
  static let settle = 700.0
  static let holdAfter = 260.0
  static let stoke = 1300.0
  static let cool = 650.0
  static let relight = 380.0
  /// Read state: the flame is cut back to an ember reaching this far in from
  /// the cap, in pill heights (the lit flame reaches ~2.1).
  static let ember = 0.75
  /// The status line crossfades when a note is read or relit.
  static let swap = 240.0
  /// "Deliver again": the old notes leave before the new ones arrive.
  static let leave = 180.0
  // The cast shadow, fitted to the reference's falloff (pill heights 84):
  // it hangs 12 below, 6 in from each side, sigma 10, nothing above.
  static let shadowDrop = 12.0
  static let shadowInset = 6.0
  static let shadowBlur = 10.0
}

/// The motion values `flare-notes.tsx` derives from `flareMotion`.
enum FlareBeats {
  /// `Easing.bezier(0.23, 1, 0.32, 1)`.
  static let easeOut = FlareEasing.bezier(0.23, 1, 0.32, 1)
  /// `Easing.bezier(0.77, 0, 0.175, 1)`.
  static let easeInOut = FlareEasing.bezier(0.77, 0, 0.175, 1)
  /// Room around each pill's canvas for the cast shadow (drop + 3 sigma).
  static let pad = FlareMotion.shadowDrop + FlareMotion.shadowBlur * 3 + 4
  /// When the last pill has finished arriving.
  static let arrival = Double(FLARES.count - 1) * FlareMotion.stagger + FlareMotion.enter
  /// The controls fade in after the last note lands.
  static let controlsDelay = arrival + 120
  static let controlsFade = 320.0
  /// The reach spring of the ignition: `withSpring(1, { duration: 900, dampingRatio: 0.55 })`.
  static let reachSpring = FlareSpringConfig.duration(900, dampingRatio: 0.55)
  /// Release: `press` and `stoke` settle back with these springs.
  static let pressRelease = FlareSpringConfig.duration(300, dampingRatio: 0.8)
  static let stokeRelease = FlareSpringConfig.duration(600, dampingRatio: 0.7)
  /// The ignition flash peak.
  static let flashPeak = 1.7
  /// Relight flash: 1.7 in 140 ms, back to 1 in 600 ms.
  static let relightFlash = 140.0
  static let relightSettle = 600.0
  /// Press-in.
  static let pressIn = 120.0
  /// A release that has moved this far (pt) is not a tap.
  static let tapSlop = 24.0
  /// The arrival waits for this many consecutive frames under `smoothFrame` ms.
  static let liveFrames = 6
  static let smoothFrame = 34.0
  /// Flame time: a frame never advances it by more than this (ms).
  static let maxFrameStep = 48.0
  /// Reduced motion: shorter fades (Reanimated still skips them, and so does this port).
  static let reducedEnter = 200.0
  static let reducedSwap = 120.0
  static let reducedCool = 200.0
  static let reducedLeave = 120.0
}

/// The page geometry of `FlareNotes` for a safe-area content box.
struct FlareLayout: Equatable {
  var width: Double
  var x: Double
  var top: Double
  var stack: Double

  init(boxWidth: Double, boxHeight: Double) {
    let count = Double(FLARES.count)
    width = min(boxWidth - 48, flareJsRound(FlareMotion.height * FlareMotion.aspect))
    stack = count * FlareMotion.height + (count - 1) * FlareMotion.gap
    top = flareJsRound(boxHeight * 0.36 - stack / 2)
    x = (boxWidth - width) / 2
  }

  /// The y of pill `i` inside the box.
  func pillTop(_ i: Int) -> Double { top + Double(i) * (FlareMotion.height + FlareMotion.gap) }

  /// The controls (hint, then "Deliver again" 34 below it) sit 34 under the stack.
  var controlsTop: Double { top + stack + 34 }
}

/// JavaScript's `Math.round`: halves round towards +∞.
func flareJsRound(_ x: Double) -> Double { (x + 0.5).rounded(.down) }

/// Yoga's `roundValueToPixelGrid` for a position: to the nearest device
/// pixel, halves away from zero (RN lays every view out on the pixel grid).
func flarePixelRound(_ value: Double, scale: Double) -> Double {
  guard scale > 0 else { return value }
  let scaled = value * scale
  let floorValue = scaled.rounded(.down)
  let fraction = scaled - floorValue
  return (floorValue + (fraction >= 0.5 - 1e-4 ? 1 : 0)) / scale
}

/// RN measures text and rounds its size up to the pixel grid.
func flarePixelCeil(_ value: Double, scale: Double) -> Double {
  guard scale > 0 else { return value }
  return (value * scale - 1e-4).rounded(.up) / scale
}
