import CoreGraphics
import Foundation

/// The pose the pack body is drawn with; reflections read the same values.
struct BoosterPackLightingPose {
  var originX: Double
  var originY: Double
  var translateX: Double
  var translateY: Double
  var scale: Double
  /// Rotation in radians, around origin before translation.
  var rotation: Double
}

/// Uniforms shared by the inner wall and the rim of the open mouth.
struct BoosterPackMouthLight {
  var center = 0.5
  var secondCenter = 0.5
  var strength = 0.0
  var opening = 0.0
  var travel = 0.0
  var liningCenter = 0.5
  var liningLight = 0.0
  var sideLight = 0.0
  var liningTilt = 0.0
  var liningTint: [Double] = [1, 1, 1]
  var liningSurface = [Double](repeating: 0, count: BoosterPackMouthLighting.MOUTH_LIGHT_SAMPLES * 2)
}

/// Reflections of the open mouth, derived from the wall's geometry and the body's pose.
enum BoosterPackMouthLighting {
  static let MOUTH_LIGHT_SAMPLES = 9

  struct Input {
    var vertices: [CGPoint]
    var textures: [CGPoint]
    var pose: BoosterPackLightingPose
    var opening: Double
    var width: Double
    var height: Double
    var reducedMotion = false
  }

  private static func finite(_ value: Double, _ fallback: Double = 0) -> Double {
    value.isFinite ? value : fallback
  }

  private static func bounded(_ value: Double, _ low: Double, _ high: Double) -> Double {
    max(low, min(high, finite(value, low)))
  }

  private static func smooth(_ low: Double, _ high: Double, _ value: Double) -> Double {
    let t = bounded((value - low) / (high - low), 0, 1)
    return t * t * (3 - 2 * t)
  }

  private static func unit3(_ x: Double, _ y: Double, _ z: Double) -> [Double] {
    let length = max(sqrt(x * x + y * y + z * z), 0.000001)
    return [x / length, y / length, z / length]
  }

  private static func transform(_ point: CGPoint, _ pose: BoosterPackLightingPose) -> CGPoint {
    let ox = finite(pose.originX)
    let oy = finite(pose.originY)
    let x = finite(point.x, ox) - ox
    let y = finite(point.y, oy) - oy
    let rotation = finite(pose.rotation)
    let scale = bounded(pose.scale, 0, 100)
    let c = cos(rotation)
    let s = sin(rotation)
    return CGPoint(
      x: ox + scale * (c * x - s * y) + finite(pose.translateX),
      y: oy + scale * (s * x + c * y) + finite(pose.translateY))
  }

  // Columns are not evenly spaced: the geometry reserves many columns for
  // folded ends. Interpolate using the supplied UVs, including reversed tears.
  private static func sampleRow(_ input: Input, _ u: Double, _ row: Int) -> CGPoint {
    let count = min(input.vertices.count, input.textures.count) / 5
    let fallback = CGPoint(x: finite(input.pose.originX), y: finite(input.pose.originY))
    if count == 0 { return fallback }
    var nearest = 0
    var nearestDistance = Double.infinity
    for column in 0..<count {
      let uv = finite(input.textures[column * 5].x)
      let distance = abs(u - uv)
      if distance < nearestDistance {
        nearest = column
        nearestDistance = distance
      }
      if column + 1 == count { continue }
      let nextUV = finite(input.textures[(column + 1) * 5].x)
      if abs(nextUV - uv) < 0.000001 || u < min(uv, nextUV) || u > max(uv, nextUV) { continue }
      let t = (u - uv) / (nextUV - uv)
      let a = input.vertices[column * 5 + row]
      let b = input.vertices[(column + 1) * 5 + row]
      return CGPoint(
        x: finite(a.x, fallback.x) + (finite(b.x, fallback.x) - finite(a.x, fallback.x)) * t,
        y: finite(a.y, fallback.y) + (finite(b.y, fallback.y) - finite(a.y, fallback.y)) * t)
    }
    let point = input.vertices[nearest * 5 + row]
    return CGPoint(x: finite(point.x, fallback.x), y: finite(point.y, fallback.y))
  }

  /// Deterministic lighting from the raw 56×5 wall and its rendered body pose.
  /// The mesh is a 2D projection: opening supplies a calibrated virtual sheet
  /// depth to estimate incidence. This is a 2.5D material, not recovered 3D.
  static func deriveMouthLighting(_ input: Input) -> BoosterPackMouthLight {
    let width = max(finite(input.width, 1), 0.001)
    let height = max(finite(input.height, width), 0.001)
    let aspect = height / width
    let aperture = bounded(finite(input.opening) / width, 0, 0.25)
    var centers: [CGPoint] = []
    var depths: [CGPoint] = []
    var localCenter = CGPoint.zero
    for index in 0..<MOUTH_LIGHT_SAMPLES {
      let u = Double(index) / Double(MOUTH_LIGHT_SAMPLES - 1)
      let middle = sampleRow(input, u, 2)
      let rear = transform(sampleRow(input, u, 0), input.pose)
      let front = transform(sampleRow(input, u, 4), input.pose)
      centers.append(transform(middle, input.pose))
      depths.append(CGPoint(x: front.x - rear.x, y: front.y - rear.y))
      localCenter = CGPoint(x: localCenter.x + middle.x / 9, y: localCenter.y + middle.y / 9)
    }
    let localStart = sampleRow(input, 0.125, 2)
    let localEnd = sampleRow(input, 0.875, 2)
    let direction: Double = localEnd.x >= localStart.x ? 1 : -1
    let dx = (centers[7].x - centers[1].x) * direction
    let dy = (centers[7].y - centers[1].y) * direction
    let chordLength = hypot(dx, dy)
    let roll = chordLength > width * 0.000001 ? atan2(dy, dx) : finite(input.pose.rotation)
    let tx = cos(roll)
    let ty = sin(roll)
    let projectedWidth = max(chordLength / 0.75, width * 0.000001)
    var normalDepths: [Double] = []
    var meanDepth = 0.0
    var meanShear = 0.0
    var worldX = 0.0
    var worldY = 0.0
    for index in 0..<MOUTH_LIGHT_SAMPLES {
      let depth = depths[index]
      let h = bounded((-depth.x * ty + depth.y * tx) / projectedWidth, -0.25, 0.25)
      normalDepths.append(h)
      meanDepth += h / 9
      meanShear += (depth.x * tx + depth.y * ty) / projectedWidth / 9
      worldX += centers[index].x / width / 9
      worldY += centers[index].y / width / 9
    }
    let bowX = centers[4].x - (centers[1].x + centers[7].x) * 0.5
    let bowY = centers[4].y - (centers[1].y + centers[7].y) * 0.5
    let bow = bounded((-bowX * ty + bowY * tx) / projectedWidth, -0.2, 0.2)
    let response = input.reducedMotion ? 0.55 : 1
    var liningSurface: [Double] = []
    for index in 0..<MOUTH_LIGHT_SAMPLES {
      let a = centers[max(0, index - 1)]
      let b = centers[min(MOUTH_LIGHT_SAMPLES - 1, index + 1)]
      let x = (b.x - a.x) * direction
      let y = (b.y - a.y) * direction
      let angle = hypot(x, y) > width * 0.000001 ? atan2(tx * y - ty * x, tx * x + ty * y) : 0
      // Angular displacement is signed in material-U coordinates. Depth is a
      // dimensionless departure from the mean, so uniform scaling cancels out.
      liningSurface.append(bounded(angle, -0.6, 0.6) * direction * response)
      liningSurface.append(bounded(normalDepths[index] - meanDepth, -0.12, 0.12) * response)
    }

    // Fixed finite camera and softbox positions, in viewport-width units.
    // Translation changes their incident directions; no lamp follows the pack.
    let lightX = 0.50 - worldX
    let lightY = aspect * 0.38 - worldY
    let lightZ = 1.45
    let light = unit3(lightX, lightY, lightZ)
    let view = unit3(0.50 - worldX, aspect * 0.46 - worldY, 2.8)
    let half = unit3(light[0] + view[0], light[1] + view[1], light[2] + view[2])
    let lightPitch = atan2(light[1], light[2])
    let viewPitch = atan2(view[1], view[2])
    let referenceLightPitch = atan2(aspect * (0.38 - 0.58), lightZ)
    let referenceViewPitch = atan2(aspect * (0.46 - 0.58), 2.8)
    let openingAngle = atan2(aperture, 0.058)
    let incidence = openingAngle + 0.22 * (lightPitch - referenceLightPitch)
      + 0.26 * (viewPitch - referenceViewPitch) + 0.10 * roll + 0.15 * bow
    let normal = unit3(-ty * cos(openingAngle), tx * cos(openingAngle), sin(openingAngle))
    let facing = bounded(normal[0] * half[0] + normal[1] * half[1] + normal[2] * half[2], 0, 1)
    let referenceDistance = lightZ * lightZ + pow(aspect * (0.38 - 0.58), 2)
    let attenuation = bounded(referenceDistance / (lightX * lightX + lightY * lightY + lightZ * lightZ), 0.22, 1.12)
    let exposed = smooth(0.008, 0.031, aperture)
    let exposure = exposed * bounded(0.75 + facing * 0.50, 0, 1) * attenuation
    let physicalCenter = bounded(0.5 + 0.45 * atan2(half[0], half[2]) - 0.18 * roll - 0.25 * bow, 0.12, 0.88)
    let liningCenter = direction == 1 ? physicalCenter : 1 - physicalCenter
    let white = smooth(atan2(0.036, 0.058), atan2(0.049, 0.058), incidence)
    let violet = smooth(atan2(0.048, 0.058), atan2(0.055, 0.058), incidence)
    let tint = [0.86 + 0.14 * white - 0.08 * violet, 1 - 0.40 * violet, 1]
    let muted = [0.82, 0.80, 0.93]
    let liningTint = tint.enumerated().map { index, value in
      input.reducedMotion ? value * 0.75 + muted[index] * 0.25 : value
    }
    let sideAngle = (incidence - atan2(0.040, 0.058)) / 0.068
    let sideLight = exp(-sideAngle * sideAngle) * exposed * attenuation * response
    let secondPhysical = bounded(0.79 + 0.35 * atan2(light[0], light[2]) - 0.10 * roll, 0.08, 0.92)
    return BoosterPackMouthLight(
      center: liningCenter,
      secondCenter: direction == 1 ? secondPhysical : 1 - secondPhysical,
      strength: bounded(exposure * (input.reducedMotion ? 0.65 : 1), 0, 1),
      opening: aperture,
      travel: bounded((worldY * width - localCenter.y) / height, -2, 2),
      liningCenter: liningCenter,
      liningLight: bounded(exposure * (input.reducedMotion ? 0.65 : 1), 0, 1),
      sideLight: bounded(sideLight, 0, 1),
      liningTilt: bounded((0.025 + 0.25 * roll + 0.35 * meanShear
        + 0.035 * (viewPitch - referenceViewPitch)) * direction * response, -0.12, 0.12),
      liningTint: liningTint,
      liningSurface: liningSurface)
  }
}
