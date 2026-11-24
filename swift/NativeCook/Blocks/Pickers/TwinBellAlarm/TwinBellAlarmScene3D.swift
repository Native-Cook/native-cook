import Foundation
import SceneKit
import simd

/// What the stage shows at one instant.
struct TwinBellAlarmPose {
  /// The hands (deg clockwise from 12, not wrapped).
  var hour = 210.0
  var minute = 0.0
  /// Seconds into a ring (negative: not ringing), the camera's push (0..1) and a finger's orbit (deg).
  var ring = -1.0
  var push = 0.0
  var orbit = 0.0
}

/// The 3D stage, drawn with SceneKit: the clock and its studio sweep from the React Native block's alarm.glb (converted
/// once to TwinBellAlarm-alarm.usdz with glb2usdz.sh, which keeps glTF space and node names), lit like Filament: the
/// same IBL (the cmgen KTX as TwinBellAlarm-studio.hdr), a key and a fill, ACES (legacy) tone mapping in a fragment
/// modifier. `apply` is called from the render delegate.
final class TwinBellAlarmScene3D {
  typealias M = TwinBellAlarmMotion

  let scene = SCNScene()
  let cameraNode = SCNNode()
  private(set) var ready = false
  private var root: SCNNode?
  private var hourHand: SCNNode?
  private var minuteHand: SCNNode?
  private var hammer: SCNNode?
  private var bellL: SCNNode?
  private var bellR: SCNNode?
  private var aspect = 0.0

  /// Filament's default camera exposure (f/16, 1/125 s, ISO 100).
  static let exposure = 1 / (1.2 * pow(2, log2(16.0 * 16.0 * 125.0)))

  init(modelURL: URL?, environmentURL: URL?) {
    let cam = SCNCamera()
    cam.projectionDirection = .vertical
    cam.zNear = M.near
    cam.zFar = M.far
    cam.wantsHDR = false
    cameraNode.camera = cam
    scene.rootNode.addChildNode(cameraNode)
    scene.background.contents = CGColor(red: 0, green: 0, blue: 0, alpha: 0)

    if let environmentURL {
      scene.lightingEnvironment.contents = environmentURL
      scene.lightingEnvironment.intensity = CGFloat(M.iblIntensity * Self.exposure)
    }
    addLight(direction: M.keyDirection, lux: M.keyLux, kelvin: 5400)
    addLight(direction: M.fillDirection, lux: M.fillLux, kelvin: 7000)

    guard let modelURL, let model = try? SCNScene(url: modelURL, options: [.checkConsistency: false]) else { return }
    for child in model.rootNode.childNodes { scene.rootNode.addChildNode(child) }
    var seen = Set<ObjectIdentifier>()
    scene.rootNode.enumerateHierarchy { node, _ in
      // nothing plays from the file: every part is set from the film's time
      node.removeAllAnimations()
      guard let g = node.geometry else { return }
      node.castsShadow = false
      for m in g.materials where seen.insert(ObjectIdentifier(m)).inserted { Self.fix(m) }
      if g.materials.contains(where: { $0.name == "ClockShadowMat" }) { node.renderingOrder = 10 }
      if g.materials.contains(where: { $0.name == "Glass" }) { node.renderingOrder = 20 }
    }
    let find = { [scene] (name: String) in scene.rootNode.childNode(withName: name, recursively: true) }
    root = find("Clock")
    hourHand = find("HourHand")
    minuteHand = find("MinuteHand")
    hammer = find("Hammer")
    bellL = find("BellL")
    bellR = find("BellR")
    ready = root != nil && hourHand != nil && minuteHand != nil && hammer != nil && bellL != nil && bellR != nil
  }

  private func addLight(direction: M.V3, lux: Double, kelvin: CGFloat) {
    let l = SCNLight()
    l.type = .directional
    l.temperature = kelvin
    l.intensity = CGFloat(lux * Self.exposure / Double.pi * 1000)
    l.castsShadow = false
    let n = SCNNode()
    n.light = l
    scene.rootNode.addChildNode(n)
    n.simdLook(at: SIMD3<Float>(direction), up: SIMD3<Float>(0, 1, 0), localFront: SIMD3<Float>(0, 0, -1))
  }

  // MARK: materials

  /// From alarm.glb's JSON: plain colours in linear Rec.709, metallic and roughness factors.
  private static let materials: [String: (color: SIMD3<Double>?, metal: Double, rough: Double)] = [
    "Chrome": (SIMD3(0.86, 0.86, 0.88), 1, 0.12),
    "Enamel": (SIMD3(0.8879231, 0.4677838, 0.0318960), 0, 0.22),
    "DialCream": (SIMD3(0.9215819, 0.8713671, 0.7605245), 0, 0.7),
    "Steel": (SIMD3(0.55, 0.55, 0.57), 1, 0.3),
    "HandBlack": (SIMD3(0.0080232, 0.0074990, 0.0069954), 0, 0.35),
    "DialInk": (SIMD3(0.0168074, 0.0152085, 0.0129830), 0, 0.6),
    "SweepMat": (nil, 0, 0.95),
  ]
  private static let shadowColor = SIMD3<Double>(0.0409152, 0.0528606, 0.1022417)

  static func linearColor(_ c: SIMD3<Double>) -> CGColor {
    let space = CGColorSpace(name: CGColorSpace.extendedLinearSRGB)!
    return CGColor(colorSpace: space, components: [CGFloat(c.x), CGFloat(c.y), CGFloat(c.z), 1])!
  }

  /// Filament's ACES legacy tone mapping (Stephen Hill's fit with a 1/0.6 boost). SceneKit's light temperatures come
  /// out a little cooler than Filament's, so the light's warmth is put back first (measured against the RN recording).
  static let toneMap = """
    float3 v = max(_output.color.rgb, float3(0.0)) * float3(1.03, 0.97, 0.90) * (1.0 / 0.6);
    float3 a = float3(dot(float3(0.59719, 0.35458, 0.04823), v),
                      dot(float3(0.07600, 0.90834, 0.01566), v),
                      dot(float3(0.02840, 0.13383, 0.83777), v));
    a = (a * (a + 0.0245786) - 0.000090537) / (a * (0.983729 * a + 0.4329510) + 0.238081);
    float3 o = float3(dot(float3(1.60475, -0.53108, -0.07367), a),
                      dot(float3(-0.10208, 1.10813, -0.00605), a),
                      dot(float3(-0.00327, -0.07276, 1.07602), a));
    _output.color.rgb = clamp(o, 0.0, 1.0);
    """

  /// gltfio fades every term of a BLEND surface by its alpha (0.1 for the glass): premultiplied after the tone curve.
  private static let glassFragment = toneMap + """

    _output.color = float4(_output.color.rgb * 0.1, 0.1);
    """

  private static let decalFragment = """
    #pragma arguments
    float4 alarmDecal;
    #pragma body
    float alA = _output.color.a;
    _output.color = float4(alarmDecal.rgb * alA, alA);
    """

  private static func fix(_ m: SCNMaterial) {
    let name = m.name ?? ""
    if name == "ClockShadowMat" {
      m.lightingModel = .constant
      if let alpha = m.transparent.contents { m.diffuse.contents = alpha }
      m.transparent.contents = nil
      m.writesToDepthBuffer = false
      m.blendMode = .alpha
      m.isDoubleSided = true
      m.shaderModifiers = [.fragment: decalFragment]
      let c = shadowColor
      m.setValue(NSValue(scnVector4: SCNVector4(Float(c.x), Float(c.y), Float(c.z), 1)), forKey: "alarmDecal")
      return
    }
    m.lightingModel = .physicallyBased
    if name == "Glass" {
      m.diffuse.contents = linearColor(SIMD3(1, 1, 1))
      m.metalness.contents = NSNumber(value: 0)
      m.roughness.contents = NSNumber(value: 0.04)
      // just under 1 so SceneKit draws it in the blended pass; the fragment modifier sets the real alpha
      m.transparent.contents = NSNumber(value: 1)
      m.transparency = 0.99
      m.blendMode = .alpha
      m.writesToDepthBuffer = false
      m.shaderModifiers = [.fragment: glassFragment]
      return
    }
    if let s = materials[name] {
      if let c = s.color { m.diffuse.contents = linearColor(c) }
      m.metalness.contents = NSNumber(value: s.metal)
      m.roughness.contents = NSNumber(value: s.rough)
    }
    m.shaderModifiers = [.fragment: toneMap]
  }

  // MARK: per frame

  func apply(_ p: TwinBellAlarmPose, aspect a: Double) {
    guard ready else { return }
    if a != aspect, a > 0 {
      aspect = a
      cameraNode.camera?.fieldOfView = CGFloat(2 * atan(M.tanHalfFovY(a)) * 180 / Double.pi)
    }
    cameraNode.simdPosition = SIMD3<Float>(M.eye(push: p.push, orbit: p.orbit))
    cameraNode.simdLook(at: SIMD3<Float>(M.target), up: SIMD3<Float>(0, 1, 0), localFront: SIMD3<Float>(0, 0, -1))

    let z = SIMD3<Float>(0, 0, 1)
    let r = M.ringPose(p.ring)
    Self.pose(hourHand, Float(-p.hour * Double.pi / 180), M.hourPivot, z)
    Self.pose(minuteHand, Float(-p.minute * Double.pi / 180), M.minutePivot, z)
    Self.pose(hammer, Float(r.hammer), M.hammerPivot, z)
    Self.pose(bellL, Float(r.bellL), M.bellLPivot, z)
    Self.pose(bellR, Float(r.bellR), M.bellRPivot, z)
    Self.pose(root, Float(r.rock), M.V3(r.shuffle, r.lift, 0), z)
  }

  private static func pose(_ n: SCNNode?, _ angle: Float, _ at: M.V3, _ axis: SIMD3<Float>) {
    n?.simdOrientation = simd_quatf(angle: angle, axis: axis)
    n?.simdPosition = SIMD3<Float>(at)
  }
}
