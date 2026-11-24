import Foundation
import SceneKit
import simd

/// What the stage shows at one instant.
struct CombinationSafeDialPose {
  /// The dial (unwrapped numbers, + is counter-clockwise), the unlock timeline (s) and the wrong code's shake (m).
  var dial = 0.0
  var unlock = 0.0
  var shake = 0.0
}

/// The 3D stage, drawn with SceneKit: the safe and its studio sweep from the React Native block's safe.glb (converted
/// once to CombinationSafeDial-safe.usdz with glb2usdz.sh, which keeps glTF space and node names), lit like Filament:
/// the same IBL (the cmgen KTX as CombinationSafeDial-studio.hdr), a key and a fill, ACES (legacy) tone mapping in a
/// fragment modifier. Nothing plays from the file; `apply` poses every part from the controller's pose, on the render
/// thread.
final class CombinationSafeDialScene3D {
  typealias M = CombinationSafeDialMotion

  let scene = SCNScene()
  let cameraNode = SCNNode()
  private(set) var ready = false
  private var root: SCNNode?
  private var door: SCNNode?
  private var dial: SCNNode?
  private var handle: SCNNode?
  private var bolts: [SCNNode?] = []
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
    addLight(direction: M.keyDirection, lux: M.keyLux, kelvin: 5200)
    addLight(direction: M.fillDirection, lux: M.fillLux, kelvin: 7000)

    guard let modelURL, let model = try? SCNScene(url: modelURL, options: [.checkConsistency: false]) else { return }
    for child in model.rootNode.childNodes { scene.rootNode.addChildNode(child) }
    var seen = Set<ObjectIdentifier>()
    scene.rootNode.enumerateHierarchy { node, _ in
      // nothing plays from the file: every part is set from the film's time
      node.removeAllAnimations()
      node.skinner = nil
      guard let g = node.geometry else { return }
      node.castsShadow = false
      for m in g.materials where seen.insert(ObjectIdentifier(m)).inserted { Self.fix(m) }
      if g.materials.contains(where: { $0.name == "SafeShadowMat" }) { node.renderingOrder = 10 }
    }
    let find = { [scene] (name: String) in scene.rootNode.childNode(withName: name, recursively: true) }
    root = find("Safe")
    door = find("Door")
    dial = find("Dial")
    handle = find("Handle")
    bolts = (0..<M.boltCount).map { find("Bolt\($0)") }
    ready = root != nil && door != nil && dial != nil && handle != nil && !bolts.contains(where: { $0 == nil })
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

  /// From safe.glb's JSON: plain colours in linear Rec.709, metallic and roughness factors, normal-map scale.
  private static let materials: [String: (color: SIMD3<Double>?, metal: Double?, rough: Double?, normal: Double?)] = [
    "WarmBack": (nil, 0, 0.8, nil),
    "DeedBox": (SIMD3(0.1946178, 0.0497066, 0.0159963), 0, 0.42, nil),
    "Chrome": (SIMD3(0.88, 0.87, 0.85), 1, 0.13, nil),
    "DarkSteel": (SIMD3(0.32, 0.32, 0.33), 1, 0.38, nil),
    "Brass": (SIMD3(0.5711249, 0.3231432, 0.0595112), 1, 0.26, nil),
    "DialInk": (SIMD3(0.9046612, 0.8631572, 0.7605245), 0, 0.5, nil),
    "DialBlack": (SIMD3(0.0097212, 0.0103298, 0.0109601), 0, 0.34, nil),
    "BrushedSteel": (nil, nil, nil, 0.32),
    "GreenEnamel": (SIMD3(0.0137021, 0.0544803, 0.0395462), 0, 0.3, nil),
    "WarmLiner": (SIMD3(0.4851499, 0.1682694, 0.0273209), 0, 0.7, nil),
    "WarmShelf": (SIMD3(1, 0.6307572, 0.2541521), 0, 0.5, nil),
    "SweepMat": (nil, 0, 0.95, nil),
  ]
  private static let shadowColor = SIMD3<Double>(0.0273209, 0.0231534, 0.0159963)

  static func linearColor(_ c: SIMD3<Double>) -> CGColor {
    let space = CGColorSpace(name: CGColorSpace.extendedLinearSRGB)!
    return CGColor(colorSpace: space, components: [CGFloat(c.x), CGFloat(c.y), CGFloat(c.z), 1])!
  }

  /// Filament's ACES legacy tone mapping (Stephen Hill's fit with a 1/0.6 boost); measured against the React Native
  /// recording, this scene needs no warmth correction.
  static let toneMap = """
    float3 v = max(_output.color.rgb, float3(0.0)) * (1.0 / 0.6);
    float3 a = float3(dot(float3(0.59719, 0.35458, 0.04823), v),
                      dot(float3(0.07600, 0.90834, 0.01566), v),
                      dot(float3(0.02840, 0.13383, 0.83777), v));
    a = (a * (a + 0.0245786) - 0.000090537) / (a * (0.983729 * a + 0.4329510) + 0.238081);
    float3 o = float3(dot(float3(1.60475, -0.53108, -0.07367), a),
                      dot(float3(-0.10208, 1.10813, -0.00605), a),
                      dot(float3(-0.00327, -0.07276, 1.07602), a));
    _output.color.rgb = clamp(o, 0.0, 1.0);
    """

  /// The baked contact shadow: gltfio fades every term of a BLEND surface by its alpha, so it is drawn unlit, in its
  /// colour, with the texture's alpha.
  private static let decalFragment = """
    #pragma arguments
    float4 safeDecal;
    #pragma body
    float sdA = _output.color.a;
    _output.color = float4(safeDecal.rgb * sdA, sdA);
    """

  private static func fix(_ m: SCNMaterial) {
    let name = m.name ?? ""
    if name == "SafeShadowMat" {
      m.lightingModel = .constant
      if let alpha = m.transparent.contents { m.diffuse.contents = alpha }
      m.transparent.contents = nil
      m.writesToDepthBuffer = false
      m.blendMode = .alpha
      m.isDoubleSided = true
      m.shaderModifiers = [.fragment: decalFragment]
      let c = shadowColor
      m.setValue(NSValue(scnVector4: SCNVector4(Float(c.x), Float(c.y), Float(c.z), 1)), forKey: "safeDecal")
      return
    }
    m.lightingModel = .physicallyBased
    if let s = materials[name] {
      if let c = s.color { m.diffuse.contents = linearColor(c) }
      if let v = s.metal { m.metalness.contents = NSNumber(value: v) }
      if let v = s.rough { m.roughness.contents = NSNumber(value: v) }
      if let v = s.normal { m.normal.intensity = CGFloat(v) }
    }
    m.shaderModifiers = [.fragment: toneMap]
  }

  // MARK: per frame

  func apply(_ p: CombinationSafeDialPose, aspect a: Double) {
    if a != aspect, a > 0 {
      aspect = a
      cameraNode.camera?.fieldOfView = CGFloat(2 * atan(M.tanHalfFovY(a)) * 180 / Double.pi)
    }
    let open = M.doorAt(p.unlock)
    cameraNode.simdPosition = SIMD3<Float>(M.eye(open: open))
    cameraNode.simdLook(at: SIMD3<Float>(M.target), up: SIMD3<Float>(0, 1, 0), localFront: SIMD3<Float>(0, 0, -1))
    guard ready else { return }

    let y = SIMD3<Float>(0, 1, 0), z = SIMD3<Float>(0, 0, 1)
    root?.simdPosition = SIMD3<Float>(Float(p.shake), 0, 0)
    // opening towards the viewer is a negative turn about y
    Self.pose(door, Float(-open * M.doorDeg * Double.pi / 180), M.doorHinge, y)
    Self.pose(dial, Float(p.dial * M.degPerNumber * Double.pi / 180), M.dialPivot, z)
    Self.pose(handle, Float(-M.handleAt(p.unlock) * M.handleDeg * Double.pi / 180), M.handlePivot, z)
    for i in 0..<M.boltCount {
      let d = M.boltDir(i)
      let pull = M.boltAt(p.unlock, i) * M.boltTravel
      bolts[i]?.simdPosition = SIMD3<Float>(Float(M.doorCentre.x - d.x * pull), Float(M.doorCentre.y - d.y * pull), Float(M.doorCentre.z))
    }
  }

  private static func pose(_ n: SCNNode?, _ angle: Float, _ at: M.V3, _ axis: SIMD3<Float>) {
    n?.simdOrientation = simd_quatf(angle: angle, axis: axis)
    n?.simdPosition = SIMD3<Float>(at)
  }
}
