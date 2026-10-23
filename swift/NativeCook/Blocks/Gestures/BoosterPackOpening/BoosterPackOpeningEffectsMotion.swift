import CoreGraphics
import Foundation

/// A spark thrown backward from the tear frontier.
struct BoosterPackTearSpark {
  var x: Double, y: Double, vx: Double, vy: Double, age: Double, life: Double, size: Double
}

/// Accents around the measured opening. Each is a pure function of the shared clocks,
/// so a held gesture or a paused session holds them too.
enum BoosterPackOpeningEffectsMotion {
  private typealias M = BoosterPackModel

  static let IDLE_CYCLE_MS = 4_600.0
  static let BURST_GLINT_COUNT = 26
  static let BURST_DURATION_MS = 980.0
  static let MAX_TEAR_SPARKS = 34

  private static let TWO_PI = Double.pi * 2

  /// Deterministic 0..1 value, so particles need no stored random state.
  static func hash01(_ index: Double, _ salt: Double) -> Double {
    let value = sin(index * 127.1 + salt * 311.7) * 43758.5453
    return value - floor(value)
  }

  private static func smooth(_ value: Double) -> Double {
    let t = M.clamp(value, 0, 1)
    return t * t * (3 - 2 * t)
  }

  /// Slow breath of the glow behind an untouched pack; 0 at the cycle boundary.
  static func idleAuraBreath(_ phase: Double) -> Double {
    sin(phase * TWO_PI)
  }

  /// A diagonal sheen crosses the intact pack once per idle cycle.
  static func idleSheen(_ phase: Double, _ pack: BoosterPackRect) -> (start: CGPoint, end: CGPoint, opacity: Double) {
    let travel = M.clamp((phase - 0.04) / 0.3, 0, 1)
    let centerX = pack.x + pack.width * (-0.3 + 1.6 * smooth(travel))
    let centerY = pack.y + pack.height * 0.5
    let band = pack.width * 0.2
    return (
      CGPoint(x: centerX - band, y: centerY - band * 0.55),
      CGPoint(x: centerX + band, y: centerY + band * 0.55),
      sin(travel * Double.pi))
  }

  /// A light runs along the tear line between sheens, showing where to swipe.
  static func idleSeamHint(_ phase: Double, _ pack: BoosterPackRect) -> (head: Double, tail: Double, opacity: Double) {
    let travel = M.clamp((phase - 0.46) / 0.36, 0, 1)
    let strength = sin(travel * Double.pi)
    let head = pack.x + pack.width * (0.03 + 0.94 * smooth(travel))
    let tail = max(pack.x, head - pack.width * 0.26 * strength)
    return (head, tail, pow(strength, 0.7))
  }

  static func spawnTearSpark(_ origin: CGPoint, _ seed: Double, _ direction: Double, _ width: Double) -> BoosterPackTearSpark {
    let speed = width * (0.22 + hash01(seed, 1) * 0.78)
    let spread = (hash01(seed, 2) - 0.5) * 2.5
    let angle = (direction == 1 ? Double.pi : 0) + direction * spread
    return BoosterPackTearSpark(
      x: origin.x, y: origin.y,
      vx: cos(angle) * speed,
      vy: sin(angle) * speed - width * 0.14,
      age: 0,
      life: 230 + hash01(seed, 3) * 330,
      size: width * (0.006 + hash01(seed, 4) * 0.008))
  }

  static func stepTearSpark(_ spark: BoosterPackTearSpark, _ seconds: Double, _ width: Double) -> BoosterPackTearSpark? {
    let age = spark.age + seconds * 1000
    if age >= spark.life { return nil }
    let drag = max(0, 1 - 2.4 * seconds)
    return BoosterPackTearSpark(
      x: spark.x + spark.vx * seconds,
      y: spark.y + spark.vy * seconds,
      vx: spark.vx * drag,
      vy: spark.vy * drag + width * 1.5 * seconds,
      age: age, life: spark.life, size: spark.size)
  }

  /// Glints thrown out of the cut. Time is relative to the commit flash.
  static func burstGlintAt(
    _ index: Int, _ ms: Double, _ pack: BoosterPackRect, _ cutY: Double, _ width: Double, _ height: Double
  ) -> (x: Double, y: Double, size: Double, rotation: Double)? {
    let i = Double(index)
    let age = ms - hash01(i, 1) * 80
    let life = 430 + hash01(i, 2) * 520
    if age <= 0 || age >= life { return nil }

    let u = 0.06 + 0.88 * hash01(i, 3)
    // Glints near either side lean outward, giving the burst a fan silhouette.
    let angle = -Double.pi / 2 + (u - 0.5) * 1.7 + (hash01(i, 4) - 0.5) * 0.8
    let speed = width * (0.6 + hash01(i, 5) * 1.25)
    let seconds = age / 1000
    let drag = 4.4
    let travel = speed * (1 - exp(-drag * seconds)) / drag
    let progress = age / life
    let twinkle = 0.72 + 0.28 * sin(age / 38 + i * 1.7)
    return (
      pack.x + pack.width * u + cos(angle) * travel,
      cutY + sin(angle) * travel + 0.5 * height * 0.34 * seconds * seconds,
      width * (0.011 + hash01(i, 6) * 0.021) * min(1, progress * 9) * pow(1 - progress, 0.75) * twinkle,
      hash01(i, 7) * Double.pi + seconds * (hash01(i, 8) - 0.5) * 7)
  }

  /// Soft ring that keeps expanding after the single-frame flash ring is gone.
  static func shockwaveAt(_ ms: Double, _ width: Double, _ height: Double)
    -> (radiusX: Double, radiusY: Double, strokeWidth: Double, opacity: Double) {
    let eased = 1 - pow(1 - M.clamp(ms / 480, 0, 1), 3)
    return (
      width * (0.4 + 0.52 * eased),
      height * (0.05 + 0.105 * eased),
      width * (0.034 - 0.027 * eased),
      M.interpolateKeyframes(ms, [0, 24, 130, 480], [0, 0.62, 0.36, 0]))
  }

  /// Brief zoom of the whole scene on the commit frame.
  static func commitPunchScale(_ ms: Double) -> Double {
    if ms <= 0 || ms >= 340 { return 1 }
    return 1 + 0.03 * M.clamp(ms / 40, 0, 1) * exp(-ms / 78)
  }

  /// Light spilling from the mouth while the card rises and centers.
  static func cardRiseLightAt(_ ms: Double) -> Double {
    M.interpolateKeyframes(ms, [780, 880, 1040, 1380, 1640], [0, 0.9, 0.78, 0.34, 0])
  }
}
