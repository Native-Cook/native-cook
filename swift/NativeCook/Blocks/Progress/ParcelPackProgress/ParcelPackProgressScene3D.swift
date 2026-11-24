import Foundation
import SceneKit
import simd

/// The 3D stage, drawn with SceneKit: the carton and its studio floor from the React Native block's parcel.glb
/// (converted once to ParcelPackProgress-parcel.usdz with glb2usdz.sh, which keeps glTF space and node names), lit like
/// Filament: the same IBL (the cmgen KTX as ParcelPackProgress-studio.hdr), a key and a fill, ACES (legacy) tone mapping
/// in a fragment modifier. Every part is posed from the film's time; `apply` is called from the render delegate.
final class ParcelPackProgressScene3D {
  typealias M = ParcelPackProgressMotion

  let scene = SCNScene()
  let cameraNode = SCNNode()
  private(set) var ready = false
  private var root: SCNNode?
  private var flaps: [SCNNode?] = []
  private var tapeA: SCNNode?
  private var tapeTop: SCNNode?
  private var tapeB: SCNNode?
  private var label: SCNNode?
  private var aspect = 0.0
  private var lastT = -1.0
  private var lastOrbit = 0.0

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
      if g.materials.contains(where: { $0.name == "BoxShadowMat" }) { node.renderingOrder = 10 }
    }
    root = scene.rootNode.childNode(withName: "Box", recursively: true)
    flaps = M.flaps.map { scene.rootNode.childNode(withName: $0.node, recursively: true) }
    tapeA = scene.rootNode.childNode(withName: "TapeA", recursively: true)
    tapeTop = scene.rootNode.childNode(withName: "TapeTop", recursively: true)
    tapeB = scene.rootNode.childNode(withName: "TapeB", recursively: true)
    label = scene.rootNode.childNode(withName: "Label", recursively: true)
    ready = root != nil && !flaps.contains { $0 == nil } && tapeA != nil && tapeTop != nil && tapeB != nil && label != nil
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

  private struct Spec {
    var color: SIMD3<Double>? = nil
    var tint: SIMD3<Double>? = nil
    var rough: Double
    var normal: Double = 1
  }

  /// The kraft texture reads a little more saturated through USD than through gltfio (measured on the front wall).
  private static let kraftWarmth = SIMD3<Double>(1.0, 1.065, 1.19)

  /// From parcel.glb's JSON. `color`: a plain baseColorFactor in linear Rec.709; `tint`: a factor over a texture
  /// (USD drops it).
  private static let materials: [String: Spec] = [
    "Kraft": Spec(tint: kraftWarmth, rough: 0.84, normal: 0.55),
    "KraftIn": Spec(tint: SIMD3(0.82, 0.8, 0.78) * kraftWarmth, rough: 0.9, normal: 0.4),
    "Rim": Spec(color: SIMD3(0.2541521, 0.1470273, 0.0648033), rough: 0.92),
    "Flute": Spec(rough: 0.92),
    "ItemBand": Spec(color: SIMD3(0.8227857, 0.6938717, 0.4793202), rough: 0.5),
    "ItemBox": Spec(color: SIMD3(0.0176420, 0.1195384, 0.0781874), rough: 0.42),
    "LabelPaper": Spec(rough: 0.6),
    "Tape": Spec(color: SIMD3(0.0137021, 0.1070231, 0.0684782), rough: 0.3),
    "Tissue": Spec(color: SIMD3(0.8962694, 0.8631572, 0.7912979), rough: 0.85),
    "FloorMat": Spec(rough: 0.95),
  ]
  private static let inkColor = SIMD3<Double>(0.0684782, 0.0331048, 0.0144438)
  private static let shadowColor = SIMD3<Double>(0.0684782, 0.0497066, 0.0331048)

  static func linearColor(_ c: SIMD3<Double>) -> CGColor {
    let space = CGColorSpace(name: CGColorSpace.extendedLinearSRGB)!
    return CGColor(colorSpace: space, components: [CGFloat(c.x), CGFloat(c.y), CGFloat(c.z), 1])!
  }

  /// Filament's ACES legacy tone mapping (Stephen Hill's fit with a 1/0.6 boost). SceneKit's light temperatures come
  /// out a little cooler than Filament's, so the light's warmth is put back first (measured against the RN recording).
  static let toneMap = """
    float3 v = max(_output.color.rgb, float3(0.0)) * float3(1.01, 0.96, 0.91) * (1.0 / 0.6);
    float3 a = float3(dot(float3(0.59719, 0.35458, 0.04823), v),
                      dot(float3(0.07600, 0.90834, 0.01566), v),
                      dot(float3(0.02840, 0.13383, 0.83777), v));
    a = (a * (a + 0.0245786) - 0.000090537) / (a * (0.983729 * a + 0.4329510) + 0.238081);
    float3 o = float3(dot(float3(1.60475, -0.53108, -0.07367), a),
                      dot(float3(-0.10208, 1.10813, -0.00605), a),
                      dot(float3(-0.00327, -0.07276, 1.07602), a));
    _output.color.rgb = clamp(o, 0.0, 1.0);
    """

  private static let tintSurface = """
    #pragma arguments
    float4 parcelTint;
    #pragma body
    _surface.diffuse.rgb *= parcelTint.rgb;
    """

  /// glTF MASK: the print's alpha is cut at 0.5.
  private static let inkSurface = """
    #pragma arguments
    float4 parcelTint;
    #pragma body
    if (_surface.diffuse.a < 0.5) { discard_fragment(); }
    _surface.diffuse = float4(_surface.diffuse.rgb * parcelTint.rgb, 1.0);
    """

  private static let decalFragment = """
    #pragma arguments
    float4 parcelDecal;
    #pragma body
    float pcA = _output.color.a;
    _output.color = float4(parcelDecal.rgb * pcA, pcA);
    """

  private static func vec(_ c: SIMD3<Double>) -> NSValue {
    NSValue(scnVector4: SCNVector4(Float(c.x), Float(c.y), Float(c.z), 1))
  }

  private static func fix(_ m: SCNMaterial) {
    let name = m.name ?? ""
    if name == "BoxShadowMat" {
      // glTF BLEND fades every term by alpha: unlit, the alpha texture as the colour
      m.lightingModel = .constant
      if let alpha = m.transparent.contents { m.diffuse.contents = alpha }
      m.transparent.contents = nil
      m.writesToDepthBuffer = false
      m.blendMode = .alpha
      m.isDoubleSided = true
      m.shaderModifiers = [.fragment: decalFragment]
      m.setValue(vec(shadowColor), forKey: "parcelDecal")
      return
    }
    m.lightingModel = .physicallyBased
    m.metalness.contents = NSNumber(value: 0)
    if name == "Ink" {
      if let alpha = m.transparent.contents { m.diffuse.contents = alpha }
      m.transparent.contents = nil
      m.roughness.contents = NSNumber(value: 0.9)
      m.shaderModifiers = [.surface: inkSurface, .fragment: toneMap]
      m.setValue(vec(inkColor), forKey: "parcelTint")
      return
    }
    var mods: [SCNShaderModifierEntryPoint: String] = [.fragment: toneMap]
    if let s = materials[name] {
      if let c = s.color { m.diffuse.contents = linearColor(c) }
      m.roughness.contents = NSNumber(value: s.rough)
      m.normal.intensity = CGFloat(s.normal)
      if let t = s.tint {
        mods[.surface] = tintSurface
        m.setValue(vec(t), forKey: "parcelTint")
      }
    }
    m.shaderModifiers = mods
  }

  // MARK: per frame

  func apply(t: Double, orbit: Double, aspect a: Double) {
    guard ready, let root else { return }
    if a != aspect, a > 0 {
      aspect = a
      cameraNode.camera?.fieldOfView = CGFloat(2 * atan(M.tanHalfFovY(a)) * 180 / Double.pi)
      lastT = -1
    }
    // a still scene writes nothing
    if t == lastT, orbit == lastOrbit { return }
    lastT = t
    lastOrbit = orbit

    cameraNode.simdPosition = SIMD3<Float>(M.eye(t, orbit: orbit))
    cameraNode.simdLook(at: SIMD3<Float>(M.target), up: SIMD3<Float>(0, 1, 0), localFront: SIMD3<Float>(0, 0, -1))

    let rad = Float.pi / 180
    root.simdScale = SIMD3<Float>(1, Float(M.boxSquash(t)), 1)
    root.simdOrientation = simd_quatf(angle: Float(M.yawAt(t)) * rad, axis: SIMD3<Float>(0, 1, 0))
    root.simdPosition = SIMD3<Float>(Float(M.slideX(t)), 0, 0)

    for (i, f) in M.flaps.enumerated() {
      guard let n = flaps[i] else { continue }
      let open = M.flapOpen(i, t)
      n.simdOrientation = simd_quatf(angle: Float(f.open * open) * rad, axis: SIMD3<Float>(f.axis))
      n.simdPosition = SIMD3<Float>(Float(f.pivot.x), Float(f.pivot.y - f.sink * (1 - open)), Float(f.pivot.z))
    }

    let tape = M.tapeAt(t)
    tapeA?.simdScale = SIMD3<Float>(1, Float(max(M.none, tape.0)), 1)
    tapeA?.simdPosition = SIMD3<Float>(M.tapeA)
    tapeTop?.simdScale = SIMD3<Float>(Float(max(M.none, tape.1)), 1, 1)
    tapeTop?.simdPosition = SIMD3<Float>(M.tapeTop)
    tapeB?.simdScale = SIMD3<Float>(1, Float(max(M.none, tape.2)), 1)
    tapeB?.simdPosition = SIMD3<Float>(M.tapeB)

    if let label {
      let l = M.labelAt(t)
      let s = max(M.none, l.size)
      label.simdScale = SIMD3<Float>(Float(s * (1 + l.squash)), Float(s), Float(s * (1 + l.squash)))
      label.simdOrientation = simd_quatf(angle: Float(l.tilt) * rad, axis: simd_normalize(SIMD3<Float>(M.tiltAxis)))
        * simd_quatf(angle: Float(M.labelYaw) * rad, axis: SIMD3<Float>(0, 1, 0))
      label.simdPosition = SIMD3<Float>(Float(M.label.x), Float(M.label.y + l.height), Float(M.label.z))
    }
  }
}
