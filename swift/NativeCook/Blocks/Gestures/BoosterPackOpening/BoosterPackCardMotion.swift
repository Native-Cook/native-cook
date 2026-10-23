import CoreGraphics
import Foundation

/// How rare a card is. It picks the arrival sound, and an epic card gets the full turn.
enum BoosterPackCardRarity: String {
  case common, rare, epic
}

/// The first card's path out of the pack.
enum BoosterPackCardMotion {
  private typealias M = BoosterPackModel

  static let CARD_CENTERING_DURATION_MS = 650.0
  private static let TIMES = M.CARD_KEYFRAMES.map(\.ms)
  private static let TOPS = M.CARD_KEYFRAMES.map(\.top)
  private static let CENTERED_TOP = (1 - M.REFERENCE_LAYOUT.card.height) / 2
  private static let LAST = M.CARD_KEYFRAMES[M.CARD_KEYFRAMES.count - 1]
  private static let PREVIOUS = M.CARD_KEYFRAMES[M.CARD_KEYFRAMES.count - 2]
  private static let REVEAL_VELOCITY = (LAST.top - PREVIOUS.top) / (LAST.ms - PREVIOUS.ms)

  /// Normalized top; preserve the measured reveal, then ease into the scene center.
  static func cardTopAtTime(_ ms: Double, _ reducedMotion: Bool = false) -> Double {
    // A fade at the destination replaces the upward travel for reduced motion.
    if reducedMotion { return CENTERED_TOP }
    if ms <= M.POST_CUT_DURATION_MS {
      return M.interpolateKeyframes(ms, TIMES, TOPS)
    }

    let t = M.clamp((ms - M.POST_CUT_DURATION_MS) / CARD_CENTERING_DURATION_MS, 0, 1)
    // Hermite interpolation carries the reveal's final velocity into this move,
    // then settles with zero velocity, avoiding a stop at the old video endpoint.
    let t2 = t * t
    let t3 = t2 * t
    return (2 * t3 - 3 * t2 + 1) * LAST.top
      + (t3 - 2 * t2 + t) * REVEAL_VELOCITY * CARD_CENTERING_DURATION_MS
      + (-2 * t3 + 3 * t2) * CENTERED_TOP
  }
}

/// Where a card sits and how it is turned, before projection.
struct BoosterPackCardPose {
  var x: Double
  var y: Double
  var yaw: Double
  var pitch: Double
  var roll: Double
  var scale: Double
}

/// The five-card stack: poses, the epic turn and the arrival of a plain card.
enum BoosterPackCardRevealMotion {
  private typealias M = BoosterPackModel

  static let CARD_COUNT = 5
  // At about 30° of yaw the reference exposes about 2.5% of the projected card width
  // between successive edges (about 19 px on a 755 px face).
  static let CARD_STACK_DEPTH = 0.048
  static let CARD_EXIT_MS = 180.0
  static let EPIC_REVEAL_MS = 2600.0
  static let EPIC_INTERACTION_MS = 1650.0
  /// How many marks appear under each card once it has settled.
  static let CARD_RARITIES = [1, 1, 3, 2, 2]
  static let CARD_HOLO: [Double] = [0, 0, 0.3, 1, 1]
  static let PACK_CARD_RARITIES: [BoosterPackCardRarity] = [.common, .common, .rare, .epic, .epic]

  /// The cards that turn through their back: the ones with full foil.
  static func isEpicCard(_ index: Int) -> Bool {
    CARD_HOLO[index] == 1
  }

  /// Slow movement inspects the stack. Distance OR an intentional flick advances.
  static func shouldAdvanceCard(_ dx: Double, _ dy: Double, _ vx: Double, _ cardWidth: Double) -> Bool {
    if abs(dx) < abs(dy) * 1.25 { return false }
    let flick = abs(dx) > cardWidth * 0.065 && abs(vx) > 650 && dx * vx > 0
    return abs(dx) >= cardWidth * 0.38 || flick
  }

  static func dragCardTilt(_ dx: Double, _ dy: Double, _ cardWidth: Double) -> (yaw: Double, pitch: Double) {
    // The reference exposes about 30° of yaw and much less pitch. Soft resistance
    // lets the finger continue moving without flattening the card against an edge.
    (0.56 * tanh(dx / (cardWidth * 0.24)), -0.16 * tanh(dy / (cardWidth * 0.38)))
  }

  // Measured at 30 fps in the reference video. This is the automatic turn
  // AFTER the previous card slides away, not a drag.
  private static let TURN_TIMES: [Double] = [0, 500, 566, 633, 733, 833, 933, 1033, 1200, 1450, 1650]
  private static let TURN_ANGLES: [Double] = [0, 0, 0.06, 0.52, 2.2, 3.75, 5.05, 5.72, 6.0, 6.2, Double.pi * 2]

  /// Monotone Hermite interpolation keeps angular velocity continuous at samples.
  private static func turnAtTime(_ ms: Double) -> Double {
    if ms <= TURN_TIMES[0] { return 0 }
    if ms >= EPIC_INTERACTION_MS { return Double.pi * 2 }
    func slope(_ i: Int) -> Double {
      if i == 0 || i == TURN_TIMES.count - 1 { return 0 }
      let a = (TURN_ANGLES[i] - TURN_ANGLES[i - 1]) / (TURN_TIMES[i] - TURN_TIMES[i - 1])
      let b = (TURN_ANGLES[i + 1] - TURN_ANGLES[i]) / (TURN_TIMES[i + 1] - TURN_TIMES[i])
      return a * b <= 0 ? 0 : 2 * a * b / (a + b)
    }
    for i in 1..<TURN_TIMES.count {
      if ms > TURN_TIMES[i] { continue }
      let duration = TURN_TIMES[i] - TURN_TIMES[i - 1]
      let t = (ms - TURN_TIMES[i - 1]) / duration
      let t2 = t * t
      let t3 = t2 * t
      return (2 * t3 - 3 * t2 + 1) * TURN_ANGLES[i - 1]
        + (t3 - 2 * t2 + t) * duration * slope(i - 1)
        + (-2 * t3 + 3 * t2) * TURN_ANGLES[i]
        + (t3 - t2) * duration * slope(i)
    }
    return Double.pi * 2
  }

  struct EpicPose {
    var yaw: Double, pitch: Double, roll: Double, scale: Double
    var light: Double, flash: Double, sweep: Double
  }

  static func epicCardPose(_ ms: Double, _ reducedMotion: Bool = false) -> EpicPose {
    let yaw = reducedMotion ? 0 : turnAtTime(ms)
    let light = reducedMotion ? 0 : M.interpolateKeyframes(ms,
      [0, 250, 600, 1800, 2200, 2600], [0, 0, 1, 1, 0.65, 0])
    let flash = reducedMotion ? 0 : M.interpolateKeyframes(ms,
      [0, 1120, 1250, 1340, 1560, 1950, 2330], [0, 0, 0.65, 1, 0.48, 0.35, 0])
    return EpicPose(
      yaw: yaw,
      pitch: reducedMotion ? 0 : sin(yaw) * 0.065,
      roll: reducedMotion ? 0 : sin(yaw) * 0.018,
      scale: reducedMotion ? 1 : 1 + sin(M.clamp((ms - 550) / 1100, 0, 1) * Double.pi) * 0.045,
      light: light,
      flash: flash,
      sweep: reducedMotion ? 0 : M.clamp((ms - 1120) / 450, 0, 1))
  }

  static let CARD_ARRIVAL_MS = 520.0

  /// A plain card arriving on top of the stack: a small pop and one glint.
  static func commonCardArrival(_ ms: Double, _ reducedMotion: Bool = false) -> (pop: Double, sweep: Double, flash: Double) {
    if reducedMotion || ms >= CARD_ARRIVAL_MS { return (0, 0, 0) }
    let t = M.clamp(ms / 360, 0, 1)
    return (
      // Peaks early, then settles without crossing below the resting scale.
      sin(t * Double.pi) * (1 - t * 0.55),
      M.clamp((ms - 60) / 420, 0, 1),
      M.interpolateKeyframes(ms, [0, 60, 200, 380, CARD_ARRIVAL_MS], [0, 0, 0.85, 0.6, 0]))
  }

  /// True perspective projection of a rigid card, in local centered coordinates, as the nine
  /// values of a row-major homography. Reverse the back's U direction so its artwork is
  /// never mirrored.
  static func cardProjection(_ pose: BoosterPackCardPose, _ cardWidth: Double, _ back: Bool = false) -> [Double] {
    let cy = cos(pose.yaw)
    let sy = sin(pose.yaw)
    let cx = cos(pose.pitch)
    let sx = sin(pose.pitch)
    let cz = cos(pose.roll)
    let sz = sin(pose.roll)
    let flip: Double = back ? -1 : 1
    let scale = pose.scale
    let r00 = cy * cz * scale * flip
    let r01 = (sx * sy * cz - cx * sz) * scale
    let r10 = cy * sz * scale * flip
    let r11 = (sx * sy * sz + cx * cz) * scale
    let p0 = sy * scale * flip / (cardWidth * 2.7)
    let p1 = -cy * sx * scale / (cardWidth * 2.7)
    return [
      r00 + pose.x * p0, r01 + pose.x * p1, pose.x,
      r10 + pose.y * p0, r11 + pose.y * p1, pose.y,
      p0, p1, 1,
    ]
  }
}
