import CoreGraphics
import Foundation

/// The pack's rectangle in scene points. Field names follow the layout maths.
struct BoosterPackRect: Equatable {
  var x: Double
  var y: Double
  var width: Double
  var height: Double

  static let zero = BoosterPackRect(x: 0, y: 0, width: 0, height: 0)
  var cgRect: CGRect { CGRect(x: x, y: y, width: width, height: height) }
}

/// Rest or deformed positions with the texture coordinates they sample, both in scene points.
struct BoosterPackMesh {
  var vertices: [CGPoint]
  var textures: [CGPoint]
}

/// Layout, keyframes measured from the reference video, and the two helpers every module uses.
enum BoosterPackModel {
  static let VIDEO_FPS = 60.0
  static let FLASH_FRAME = 124.0
  static let FINAL_REFERENCE_FRAME = 184.0
  static let POST_CUT_DURATION_MS = ((FINAL_REFERENCE_FRAME - FLASH_FRAME) / VIDEO_FPS) * 1000
  // A session continues beyond the reference video through card centering.
  static let OPENING_DURATION_MS = 2000.0

  /// Measurements are normalized to the visible phone viewport in the reference video.
  enum REFERENCE_LAYOUT {
    static let width = 750.0
    static let height = 1660.0
    enum pack {
      static let centerX = 0.5
      static let top = 0.472
      static let width = 0.737
      static let sourceAspect = 938.0 / 496.0
      static let viewportHeightScale = 0.9834
      static let settledBodyWidth = 0.735
    }
    static let seamY = 0.572
    enum card {
      static let centerX = 0.5
      static let width = 0.736
      static let height = 0.482
      static let finalTop = 0.448
    }
    enum trace {
      static let startX = 0.125
      static let startY = 0.568
      static let endX = 0.765
      static let endY = 0.544
      static let touchRadius = 0.067
    }
  }

  static let TRACE_PROGRESS_KEYFRAMES: [(Double, Double)] = [
    (13, 0), (28, 0.06), (36, 0.29), (42, 0.44), (48, 0.52), (54, 0.62), (60, 0.72),
    (66, 0.81), (72, 0.88), (78, 0.95), (84, 0.98), (90, 1), (123, 1),
  ]

  struct FoilKeyframe {
    let ms: Double, x: Double, y: Double, scaleX: Double, scaleY: Double, rotation: Double
  }

  // Values measured from the video. Time is relative to the frame-124 cut flash.
  static let FOIL_KEYFRAMES: [FoilKeyframe] = [
    .init(ms: 0, x: 0.5, y: 0.522, scaleX: 1, scaleY: 1, rotation: 0),
    .init(ms: 66.667, x: 0.518, y: 0.508, scaleX: 0.98, scaleY: 0.92, rotation: 7.6),
    .init(ms: 116.667, x: 0.63, y: 0.46, scaleX: 0.846, scaleY: 0.821, rotation: 23.3),
    .init(ms: 233.333, x: 0.697, y: 0.443, scaleX: 0.797, scaleY: 0.686, rotation: 23.1),
    .init(ms: 333.333, x: 0.712, y: 0.444, scaleX: 0.8, scaleY: 0.57, rotation: 26.7),
    .init(ms: 433.333, x: 0.732, y: 0.463, scaleX: 0.77, scaleY: 0.42, rotation: 25.5),
    .init(ms: 533.333, x: 0.752, y: 0.482, scaleX: 0.73, scaleY: 0.36, rotation: 24.5),
    .init(ms: 633.333, x: 0.779, y: 0.515, scaleX: 0.67, scaleY: 0.31, rotation: 9.2),
    .init(ms: 733.333, x: 0.82, y: 0.61, scaleX: 0.46, scaleY: 0.46, rotation: -5),
    .init(ms: 766.667, x: 0.84, y: 0.64, scaleX: 0.44, scaleY: 0.5, rotation: -5),
    .init(ms: 833.333, x: 0.84, y: 0.69, scaleX: 0.4, scaleY: 0.52, rotation: -12),
    .init(ms: 900, x: 0.765, y: 0.729, scaleX: 0.36, scaleY: 0.82, rotation: -24),
    .init(ms: 1000, x: 1.06, y: 1.16, scaleX: 0.18, scaleY: 0.05, rotation: -35),
  ]

  static let BODY_KEYFRAMES: [(ms: Double, seamY: Double, scale: Double)] = [
    (0, 0.572, 1), (133.333, 0.573, 1.002), (333.333, 0.575, 1.019), (533.333, 0.582, 1.047),
    (733.333, 0.595, 1.081), (833.333, 0.642, 1.1), (900, 0.728, 1.115), (933.333, 0.8, 1.122),
    (966.667, 0.892, 1.136), (1000, 1, 1.14),
  ]

  static let CARD_KEYFRAMES: [(ms: Double, top: Double, opacity: Double)] = [
    (833.333, 0.65, 0), (850, 0.624, 1), (866.667, 0.603, 1), (883.333, 0.581, 1),
    (900, 0.556, 1), (916.667, 0.535, 1), (933.333, 0.514, 1), (950, 0.494, 1),
    (966.667, 0.477, 1), (983.333, 0.462, 1), (1000, 0.448, 1),
  ]

  static func clamp(_ value: Double, _ min: Double, _ max: Double) -> Double {
    Swift.min(max, Swift.max(min, value))
  }

  static func interpolateKeyframes(_ value: Double, _ input: [Double], _ output: [Double]) -> Double {
    if value <= input[0] { return output[0] }
    let last = input.count - 1
    if value >= input[last] { return output[last] }
    for index in 1..<input.count where value <= input[index] {
      let segment = (value - input[index - 1]) / (input[index] - input[index - 1])
      return output[index - 1] + segment * (output[index] - output[index - 1])
    }
    return output[last]
  }

  static func traceProgressAtFrame(_ frame: Double) -> Double {
    interpolateKeyframes(frame, TRACE_PROGRESS_KEYFRAMES.map(\.0), TRACE_PROGRESS_KEYFRAMES.map(\.1))
  }
}
