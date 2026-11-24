import Foundation
import SceneKit
import simd

/// What the stage shows at one instant.
struct DeskFanSpeedPose {
  /// Blade angle (turns), blade speed (turns a second), head angle (deg), and how far each key is down (0..1).
  var turns = 0.0
  var speed = 0.0
  var yaw = DeskFanSpeedMotion.yawRest
  var keys: [Double] = [1, 0, 0, 0]
}

/// The 3D stage, drawn with SceneKit: the fan and its studio sweep from the React Native block's fan.glb (converted
/// once to DeskFanSpeed-fan.usdz with glb2usdz.sh, which keeps glTF space and node names), lit like Filament: the same
/// IBL (the cmgen KTX as DeskFanSpeed-studio.hdr), a key and a fill, ACES (legacy) tone mapping in a fragment modifier.
/// Nothing plays from the file; `apply` poses every part from the controller's pose, on the render thread.
final class DeskFanSpeedScene3D {
  typealias M = DeskFanSpeedMotion

  let scene = SCNScene()
  let cameraNode = SCNNode()
  private(set) var ready = false
  private var head: SCNNode?
  private var blades: SCNNode?
  private var blur: SCNMaterial?
  private var keys: [SCNNode?] = []
  private var aspect = 0.0
  private var blurShown = -1.0

  /// Filament's default camera exposure (f/16, 1/125 s, ISO 100).
  static let exposure = 1 / (1.2 * pow(2, log2(16.0 * 16.0 * 125.0)))
  /// The blur's alpha is only sent when it moved this much.
  private static let blurStep = 0.004

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
      node.skinner = nil
      guard let g = node.geometry else { return }
      node.castsShadow = false
      for m in g.materials where seen.insert(ObjectIdentifier(m)).inserted { Self.fix(m) }
      if g.materials.contains(where: { $0.name == "FanShadowMat" }) { node.renderingOrder = 10 }
      if g.materials.contains(where: { $0.name == "BladeBlurMat" }) { node.renderingOrder = 20 }
    }
    let find = { [scene] (name: String) in scene.rootNode.childNode(withName: name, recursively: true) }
    head = find("Head")
    blades = find("Blades")
    keys = (0..<M.keyPositions.count).map { find("Key\($0)") }
    var found: SCNMaterial?
    find("BladeBlur")?.enumerateHierarchy { n, stop in
      if let m = n.geometry?.materials.first(where: { $0.name == "BladeBlurMat" }) {
        found = m
        stop.pointee = true
      }
    }
    blur = found
    setBlur(0)
    ready = head != nil && blades != nil && blur != nil && !keys.contains(where: { $0 == nil })
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

  /// From fan.glb's JSON: plain colours in linear Rec.709, metallic and roughness factors.
  private static let materials: [String: (color: SIMD3<Double>?, metal: Double, rough: Double)] = [
    "SeafoamEnamel": (SIMD3(0.2122308, 0.4620770, 0.3662526), 0, 0.24),
    "Rubber": (SIMD3(0.0168074, 0.0152085, 0.0129830), 0, 0.8),
    "Chrome": (SIMD3(0.9, 0.89, 0.87), 1, 0.11),
    "BladeBrass": (SIMD3(0.5840784, 0.3564001, 0.1022417), 1, 0.38),
    "DeepEnamel": (SIMD3(0.0284260, 0.1328683, 0.0953075), 0, 0.3),
    "CageWire": (SIMD3(0.86, 0.86, 0.85), 1, 0.2),
    "KeyInk": (SIMD3(0.0193824, 0.0331048, 0.0273209), 0, 0.5),
    "KeyCream": (SIMD3(0.8879231, 0.8307699, 0.7083758), 0, 0.36),
    "SweepMat": (nil, 0, 0.95),
  ]
  private static let shadowColor = SIMD3<Double>(0.0684782, 0.0512695, 0.0295568)

  static func linearColor(_ c: SIMD3<Double>) -> CGColor {
    let space = CGColorSpace(name: CGColorSpace.extendedLinearSRGB)!
    return CGColor(colorSpace: space, components: [CGFloat(c.x), CGFloat(c.y), CGFloat(c.z), 1])!
  }

  /// Filament's ACES legacy tone mapping (Stephen Hill's fit with a 1/0.6 boost).
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

  /// The blur disc: glTF BLEND with baseColorFactor x texture. The texture is white with the blur in its alpha, so the
  /// colour is the factor's (USD keeps only the texture, and SceneKit hands it over premultiplied, which would darken
  /// the disc by its alpha twice); gltfio fades every term by the alpha, so it is premultiplied after the tone curve.
  private static let blurSurface = """
    #pragma arguments
    float4 fanBlur;
    #pragma body
    _surface.diffuse.rgb = fanBlur.rgb;
    """

  /// (`fanBlur` is declared once, in the surface modifier; the fragment modifier shares it.)
  private static let blurFragment = toneMap + """

    float fbA = _surface.diffuse.a * fanBlur.a;
    _output.color = float4(_output.color.rgb * fbA, fbA);
    """

  /// The baked contact shadow: drawn unlit, in its colour, with the texture's alpha.
  private static let decalFragment = """
    #pragma arguments
    float4 fanDecal;
    #pragma body
    float fdA = _output.color.a;
    _output.color = float4(fanDecal.rgb * fdA, fdA);
    """

  private static func fix(_ m: SCNMaterial) {
    let name = m.name ?? ""
    if name == "FanShadowMat" {
      m.lightingModel = .constant
      if let alpha = m.transparent.contents { m.diffuse.contents = alpha }
      m.transparent.contents = nil
      m.writesToDepthBuffer = false
      m.blendMode = .alpha
      m.isDoubleSided = true
      m.shaderModifiers = [.fragment: decalFragment]
      let c = shadowColor
      m.setValue(NSValue(scnVector4: SCNVector4(Float(c.x), Float(c.y), Float(c.z), 1)), forKey: "fanDecal")
      return
    }
    m.lightingModel = .physicallyBased
    if name == "BladeBlurMat" {
      m.metalness.contents = NSNumber(value: 0)
      m.roughness.contents = NSNumber(value: 0.42)
      // just under 1 so SceneKit draws it in the blended pass; the fragment modifier sets the real alpha
      m.transparent.contents = NSNumber(value: 1)
      m.transparency = 0.99
      m.blendMode = .alpha
      m.writesToDepthBuffer = false
      m.isDoubleSided = true
      m.shaderModifiers = [.surface: blurSurface, .fragment: blurFragment]
      return
    }
    if let s = materials[name] {
      if let c = s.color { m.diffuse.contents = linearColor(c) }
      m.metalness.contents = NSNumber(value: s.metal)
      m.roughness.contents = NSNumber(value: s.rough)
    }
    m.shaderModifiers = [.fragment: toneMap]
  }

  private func setBlur(_ a: Double) {
    let c = M.blurRGB
    blur?.setValue(NSValue(scnVector4: SCNVector4(Float(c.x), Float(c.y), Float(c.z), Float(a))), forKey: "fanBlur")
    blurShown = a
  }

  // MARK: per frame

  func apply(_ p: DeskFanSpeedPose, aspect a: Double) {
    if a != aspect, a > 0 {
      aspect = a
      cameraNode.camera?.fieldOfView = CGFloat(2 * atan(M.tanHalfFovY(a)) * 180 / Double.pi)
    }
    cameraNode.simdPosition = SIMD3<Float>(M.eye(speed: p.speed))
    cameraNode.simdLook(at: SIMD3<Float>(M.target), up: SIMD3<Float>(0, 1, 0), localFront: SIMD3<Float>(0, 0, -1))
    guard ready else { return }

    head?.simdOrientation = simd_quatf(angle: Float(p.yaw * Double.pi / 180), axis: SIMD3<Float>(0, 1, 0))
    head?.simdPosition = SIMD3<Float>(M.headPivot)
    // clockwise for the viewer is a negative turn about z; only the fraction matters
    let frac = p.turns - floor(p.turns)
    blades?.simdOrientation = simd_quatf(angle: Float(-frac * 2 * Double.pi), axis: SIMD3<Float>(0, 0, 1))
    blades?.simdPosition = SIMD3<Float>(M.bladesPivot)
    let b = M.blurAt(p.speed)
    if abs(b - blurShown) > Self.blurStep || (b == 0 && blurShown != 0) { setBlur(b) }
    for i in 0..<keys.count {
      let k = M.keyPositions[i]
      keys[i]?.simdPosition = SIMD3<Float>(Float(k.x), Float(k.y - p.keys[i] * M.keyTravel), Float(k.z))
    }
  }
}
