import Foundation

/// Measured in the supplied 60fps clip; drawing fractions use the 274px track.
/// This is an explanatory replay, not the latency of a live control.
let glowknobReferenceDuration = 10050.0

enum GlowknobReferenceGeometry {
  static let width = 1000.0
  static let height = 284.0
  enum track { static let x = 80.0, y = 94.0, width = 523.0, height = 104.0 }
  enum dial { static let x = 849.0, y = 146.0, radius = 100.0 }
  enum brightness { static let x = 676.0, y = 88.0, radius = 47.0 }
  enum volume { static let x = 680.0, y = 198.0, radius = 47.0 }
  // Percentage readout box inside the track (right-aligned glyph run).
  enum readout { static let x = 464.0, width = 110.0, y = 121.0, line = 38.0, size = 30.0 }
}

typealias GlowknobPoint2 = (Double, Double)

func glowknobSample(_ points: [GlowknobPoint2], _ time: Double) -> Double {
  if time <= points[0].0 { return points[0].1 }
  for i in 1..<points.count where time <= points[i].0 {
    let (a, x) = points[i - 1], (b, y) = points[i]
    return x + ((y - x) * (time - a)) / (b - a)
  }
  return points[points.count - 1].1
}

private let levelWidths: [GlowknobPoint2] = [
  (0, 0),
  (0.14, 61.0 / 274),
  (0.52, 163.0 / 274),
  (0.53, 166.0 / 274),
  (0.72, 212.0 / 274),
  (1, 1),
]
func glowknobFillForLevel(_ level: Double) -> Double {
  glowknobSample(levelWidths, max(0, min(1, level)))
}
private let positionLevels: [GlowknobPoint2] = levelWidths.map { ($0.1, $0.0) }
func glowknobLevelForFill(_ position: Double) -> Double {
  glowknobSample(positionLevels, max(0, min(1, position)))
}

private let fillPixels: [GlowknobPoint2] = [
  (0, 26), (200, 52), (300, 96), (500, 137), (750, 156), (1000, 163), (1983, 163),
  (2083, 142), (2200, 82), (2350, 48), (2450, 40), (2550, 43), (2650, 63), (2800, 101),
  (3000, 126), (3250, 144), (3500, 153), (4000, 161), (4383, 166), (4667, 191), (5000, 206),
  (5500, 211), (6000, 212), (6583, 211), (6683, 204), (6833, 175), (7000, 120), (7250, 88),
  (7500, 72), (8000, 62), (8500, 61), (10050, 61),
]
private let lightAlphaPoints: [GlowknobPoint2] = [
  (0, 0), (200, 0.35), (600, 0.75), (1100, 1), (2200, 1), (2450, 0.55), (2750, 1),
  (9100, 1), (9400, 0.5), (9700, 0), (10050, 0),
]
private let readoutAlpha: [GlowknobPoint2] = [
  (0, 0), (270, 0), (630, 1), (2000, 1), (2400, 0), (2750, 0), (3150, 1), (4400, 1),
  (4640, 0), (4950, 1), (6600, 1), (7000, 0), (7300, 1), (9100, 1), (9700, 0),
]

// Label luminance sampled from the same source ROI (97th percentile), relative
// to its settled foreground/background. The label fade leads the fill collapse.
private let brightnessLabelAlpha: [GlowknobPoint2] = [
  (0, 0), (100, 0.13), (200, 0.34), (300, 0.56), (500, 0.99), (1900, 0.97), (2000, 0.72),
  (2050, 0.57), (2100, 0.43), (2150, 0.29), (2200, 0.12), (2250, 0.02), (2300, 0),
]
private let volumeLabelAlpha: [GlowknobPoint2] = [
  (2483, 0), (2500, 0.04), (2550, 0.12), (2600, 0.24), (2700, 0.46), (2800, 0.7), (3000, 1),
]

struct GlowknobReferencePose: Equatable {
  var mode: Int
  var level: Double
  var display: Double
  var fill: Double
  var light: Double
  var number: Double
  var brightnessLabel: Double
  var volumeLabel: Double
  var mix: Double
  var blur: Double
  var phase: Int
}

func glowknobReferencePose(_ timeMs: Double, reduced: Bool = false) -> GlowknobReferencePose {
  let time = max(0, min(glowknobReferenceDuration, timeMs))
  let mode = time < 2000 ? 0 : 1
  let level = time < 2000 ? 0.52 : time < 4400 ? 0.53 : time < 6600 ? 0.72 : 0.14
  let display: Double = time < 2500 ? 52 : time < 4640 ? 53 : time < 7000 ? 72 : 14
  let mix = glowknobSample(reduced ? [(2000, 0), (2200, 1)] : [(2433, 0), (2583, 1)], time)
  return GlowknobReferencePose(
    mode: mode,
    level: level,
    display: reduced ? glowknobJsRound(level * 100) : display,
    fill: reduced ? glowknobFillForLevel(level) : glowknobSample(fillPixels, time) / 274,
    light: reduced ? (time < 9700 ? 1 : 0) : glowknobSample(lightAlphaPoints, time),
    number: reduced ? 1 : glowknobSample(readoutAlpha, time),
    brightnessLabel: reduced ? Double(1 - mode) : glowknobSample(brightnessLabelAlpha, time),
    volumeLabel: reduced ? Double(mode) : glowknobSample(volumeLabelAlpha, time),
    mix: mix,
    blur: reduced ? 0 : glowknobSample([(0, 0), (2150, 0), (2400, 14), (2500, 10), (2700, 0)], time),
    phase: time < 2000 ? 0 : time < 4400 ? 1 : time < 6600 ? 2 : time < 9100 ? 3 : 4
  )
}

/// iOS-style resistance past a hard limit: follows the finger at first, then
/// approaches `limit` asymptotically. Sign is preserved; 0 stays 0.
func glowknobRubberBand(_ overflow: Double, _ limit: Double) -> Double {
  let distance = abs(overflow)
  if distance == 0 || limit <= 0 { return 0 }
  let resisted = (1 - 1 / ((distance * 0.55) / limit + 1)) * limit
  return glowknobJsSign(overflow) * resisted
}

/// `Math.sign`.
func glowknobJsSign(_ x: Double) -> Double { x > 0 ? 1 : x < 0 ? -1 : x }

/// `Math.round`: halves round toward +∞.
func glowknobJsRound(_ x: Double) -> Double { (x + 0.5).rounded(.down) }
