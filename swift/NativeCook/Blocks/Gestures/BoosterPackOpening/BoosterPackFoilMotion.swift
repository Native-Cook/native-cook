import CoreGraphics
import Foundation

/// The flight of the strip after the cut, from keyframes measured in the reference video.
enum BoosterPackFoilMotion {
  private typealias M = BoosterPackModel
  private static let TIMES = M.FOIL_KEYFRAMES.map(\.ms)
  private static let X = M.FOIL_KEYFRAMES.map(\.x)
  private static let Y = M.FOIL_KEYFRAMES.map(\.y)
  private static let SCALE_X = M.FOIL_KEYFRAMES.map(\.scaleX)
  private static let SCALE_Y = M.FOIL_KEYFRAMES.map(\.scaleY)
  private static let ROTATION = M.FOIL_KEYFRAMES.map(\.rotation)

  /// Deform the actual cut mesh, so a live swipe keeps its own torn edge.
  static func deformFlyingFoil(
    _ base: [CGPoint], _ pack: BoosterPackRect, _ width: Double, _ height: Double, _ time: Double
  ) -> [CGPoint] {
    if base.isEmpty { return [] }

    let targetX = M.interpolateKeyframes(time, TIMES, X) * width
    let targetY = M.interpolateKeyframes(time, TIMES, Y) * height
    let scaleX = M.interpolateKeyframes(time, TIMES, SCALE_X)
    let scaleY = M.interpolateKeyframes(time, TIMES, SCALE_Y)
    let rotation = M.interpolateKeyframes(time, TIMES, ROTATION) * Double.pi / 180
    let cosine = cos(rotation)
    let sine = sin(rotation)
    let originX = width * 0.5
    let originY = height * 0.522
    let flight = M.clamp((time - 58) / 575, 0, 1)
    let curlStrength = M.clamp((flight - 0.18) / 0.55, 0, 1)
    let rollProgress = M.clamp((time - 433.333) / 200, 0, 1)
    let lateRoll = rollProgress * rollProgress * (3 - 2 * rollProgress)
    let edgeOnPose = M.interpolateKeyframes(time, [433.333, 633.333, 733.333, 1000], [0, 1, 0, 0])

    // At f133 the leading top corner is 17 px farther right than an affine
    // ribbon; by f150 it is 25 px farther right. The torn lower corner stays put.
    let leadingShear = width * M.interpolateKeyframes(time,
      [0, 66.667, 116.667, 150, 333.333, 433.333, 533.333, 633.333, 1000],
      [0, 0, 0.045, 0.045, 0.06, 0.07, 0.04, 0, 0])
    let leadingLift = height * M.interpolateKeyframes(time,
      [0, 150, 333.333, 433.333, 533.333, 633.333, 1000],
      [0, 0, 0.007, 0.012, 0.008, 0, 0])

    return base.enumerated().map { index, point in
      let u = M.clamp((point.x - pack.x) / pack.width, 0, 1)
      let isBottom = index % 2 == 1
      let column = index / 2
      let top = base[column * 2]
      let bottom = base[column * 2 + 1]
      let centerY = (top.y + bottom.y) * 0.5
      let halfHeight = max((bottom.y - top.y) * 0.5, 0)
      let edgeSign: Double = isBottom ? 1 : -1
      let wave = sin(u * Double.pi) * height * 0.013 * flight
      let curlPhase = Double.pi * (u - 0.04)
      let earlyCurl = 1 + curlStrength * (cos(curlPhase) - 1)
      // f162 has a thin leading edge and a broad trailing face. A cosine across
      // the full width puts the broad face in the middle and leaves a hooked tip.
      let trailingCurl = -(0.035 + 0.965 * sin(Double.pi * 0.5 * u * u)) * (1 + edgeOnPose * 0.3)
      let curlScale = earlyCurl + (trailingCurl - earlyCurl) * lateRoll
      let lateTailFan = 1 + M.clamp((time - 650) / 150, 0, 1) * pow(u, 8) * 1.55
      let ribbonWave = sin(u * Double.pi * 2 + flight * Double.pi * 0.35) * height * 0.0055 * curlStrength
      let crossShear = edgeSign * halfHeight * scaleY * sin(curlPhase) * curlStrength * 0.28 * (1 - lateRoll)
      let twist = (u - 0.5) * height * 0.018 * flight * (isBottom ? 1 : -0.25) * (1 - lateRoll)
      let leadingInfluence = pow(1 - u, 2)
      let localX = (point.x - originX) * scaleX + crossShear + (isBottom ? 0 : leadingShear * leadingInfluence)
      let localY = (centerY - originY) * scaleY
        + edgeSign * halfHeight * scaleY * curlScale * lateTailFan
        + wave + ribbonWave + twist
      let leadingRollU = M.clamp(u / 0.6, 0, 1)
      let leadingRollTilt = 1 - leadingRollU * leadingRollU * (3 - 2 * leadingRollU)
      // The nearly edge-on f162 pose slopes down from the raised leading tip;
      // lifting this section separately retains the lower sag of the broad tail.
      let edgeOnLift = height * edgeOnPose * (0.0023 + 0.0082 * leadingRollTilt)
      return CGPoint(
        x: targetX + localX * cosine - localY * sine,
        y: targetY + localX * sine + localY * cosine - leadingLift * leadingInfluence - edgeOnLift)
    }
  }
}
