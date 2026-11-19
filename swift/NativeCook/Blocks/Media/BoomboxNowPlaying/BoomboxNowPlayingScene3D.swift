import Foundation
import SceneKit
import simd

/// The 3D stage, drawn with SceneKit: the record and its sleeve from the React Native block's vinyl.glb (converted
/// once to BoomboxNowPlaying-vinyl.usdz with nc-port/glb2usdz.sh, which keeps glTF space, node names and the PBR
/// materials), lit like Filament: the same studio IBL (the cmgen KTX turned into BoomboxNowPlaying-studio.hdr), a
/// warm key from the front left, a cool rim from behind, and ACES (legacy) tone mapping at +0.25 stops in a fragment
/// modifier. The groove highlight is the glb's two sheen discs, turned each frame toward the light.
///
/// It never runs its own clock: `apply(_:)` poses it, called from the SCNView's render delegate on SceneKit's own
/// render loop, so no SwiftUI state changes per frame.
final class BoomboxNowPlayingScene3D {
  typealias M = BoomboxNowPlayingMotion

  let scene = SCNScene()
  let cameraNode = SCNNode()
  private(set) var ready = false
  private var record: SCNNode?
  private var spin: SCNNode?
  private var sheen: SCNNode?
  private var sleeve: SCNNode?
  private var size = CGSize(width: 393, height: 537)

  /// Filament's default camera exposure (f/16, 1/125 s, ISO 100).
  static let exposure = 1 / (1.2 * pow(2, log2(16.0 * 16.0 * 125.0)))

  init(modelURL: URL?, environmentURL: URL?) {
    let cam = SCNCamera()
    cam.fieldOfView = CGFloat(M.hfovFor(393, 537))
    cam.projectionDirection = .horizontal
    cam.zNear = M.near
    cam.zFar = M.far
    cam.wantsHDR = false
    cameraNode.camera = cam
    cameraNode.name = "VinylCamera"
    scene.rootNode.addChildNode(cameraNode)
    scene.background.contents = CGColor(red: 0, green: 0, blue: 0, alpha: 0)

    if let environmentURL {
      scene.lightingEnvironment.contents = environmentURL
      scene.lightingEnvironment.intensity = CGFloat(M.iblIntensity * Self.exposure)
    }
    addLight(direction: M.keyDirection, lux: M.keyLux, kelvin: 5200)
    addLight(direction: M.rimDirection, lux: M.rimLux, kelvin: 7500)

    guard let modelURL, let model = try? SCNScene(url: modelURL, options: [.checkConsistency: false]) else { return }
    for child in model.rootNode.childNodes { scene.rootNode.addChildNode(child) }
    record = scene.rootNode.childNode(withName: "Record", recursively: true)
    spin = scene.rootNode.childNode(withName: "Spin", recursively: true)
    sheen = scene.rootNode.childNode(withName: "SheenSpin", recursively: true)
    sleeve = scene.rootNode.childNode(withName: "Sleeve", recursively: true)
    scene.rootNode.enumerateHierarchy { node, _ in
      node.skinner = nil
      guard let g = node.geometry else { return }
      node.castsShadow = false
      for m in g.materials { Self.fix(m) }
      if node.parent?.parent?.name == "SheenSpin" || node.parent?.name == "SheenSpin" { node.renderingOrder = 10 }
    }
    ready = record != nil && sleeve != nil
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

  /// What glb2usdz.sh cannot carry over, put back from vinyl.glb's JSON: plain colours in linear Rec.709, the
  /// roughness and metalness factors. The Vinyl material keeps its metallic-roughness texture.
  private static let materials: [String: (color: SIMD3<Double>?, metal: Double?, rough: Double?)] = [
    "LabelA": (nil, 0, 0.6),
    "LabelB": (nil, 0, 0.6),
    "Vinyl": (nil, nil, nil),
    "SleeveEdge": (SIMD3(0.9058823585510254, 0.8627451062202454, 0.7843137383460999), 0, 0.75),
    "SleeveBack": (nil, 0, 0.62),
    "SleeveInside": (SIMD3(0.16862745583057404, 0.15294118225574493, 0.13725490868091583), 0, 0.9),
    "SleeveFront": (nil, 0, 0.42),
  ]

  static func linearColor(_ c: SIMD3<Double>, alpha: Double = 1) -> CGColor {
    let space = CGColorSpace(name: CGColorSpace.extendedLinearSRGB)!
    return CGColor(colorSpace: space, components: [CGFloat(c.x), CGFloat(c.y), CGFloat(c.z), CGFloat(alpha)])!
  }

  /// Filament's ACES legacy tone mapping (ACES with a 1/0.6 brightness boost) after the colour grading's exposure
  /// (+0.25 stops), as Stephen Hill's fitted ACES; the drawable's sRGB encoding does the rest.
  static let toneMapBody = """
    v = max(v, float3(0.0)) * (1.18920712 / 0.6);
    float3 a = float3(dot(float3(0.59719, 0.35458, 0.04823), v),
                      dot(float3(0.07600, 0.90834, 0.01566), v),
                      dot(float3(0.02840, 0.13383, 0.83777), v));
    a = (a * (a + 0.0245786) - 0.000090537) / (a * (0.983729 * a + 0.4329510) + 0.238081);
    v = clamp(float3(dot(float3(1.60475, -0.53108, -0.07367), a),
                     dot(float3(-0.10208, 1.10813, -0.00605), a),
                     dot(float3(-0.00327, -0.07276, 1.07602), a)), 0.0, 1.0);
    """
  static let toneMap = "float3 v = _output.color.rgb;\n" + toneMapBody + "\n_output.color.rgb = v;"

  /// The groove highlight: glTF BLEND (gltfio fades every term by alpha) with an emissive of 0.9 that Filament adds
  /// after exposure. Unlit here: the glow through the ACES curve, faded by the streak texture's alpha.
  static let sheenFragment = "float al = _output.color.a;\nfloat3 v = float3(\(M.sheenGlow) + 0.06);\n" + toneMapBody
    + "\n_output.color = float4(v * al, al);"

  private static func fix(_ m: SCNMaterial) {
    m.isDoubleSided = true
    if m.name == "Sheen" {
      m.lightingModel = .constant
      if let tex = m.transparent.contents ?? m.diffuse.contents { m.diffuse.contents = tex }
      m.transparent.contents = nil
      m.emission.contents = nil
      m.blendMode = .alpha
      m.writesToDepthBuffer = false
      m.shaderModifiers = [.fragment: sheenFragment]
      return
    }
    m.lightingModel = .physicallyBased
    if let spec = materials[m.name ?? ""] {
      if let c = spec.color { m.diffuse.contents = linearColor(c) }
      if let v = spec.metal { m.metalness.contents = NSNumber(value: v) }
      if let v = spec.rough { m.roughness.contents = NSNumber(value: v) }
    }
    m.shaderModifiers = [.surface: backFace, .fragment: toneMap]
  }

  /// glTF doubleSided: Filament lights a back face with its normal flipped toward the viewer (the record's B side is
  /// dark under the key light); SceneKit keeps the front face's normal, which lit the B side like the A side.
  static let backFace = """
    if (dot(_surface.geometryNormal, _surface.view) < 0.0) { _surface.normal = -_surface.normal; }
    """

  // MARK: per frame

  func setSize(_ s: CGSize) {
    guard s.width > 0, s.height > 0, s != size else { return }
    size = s
    cameraNode.camera?.fieldOfView = CGFloat(M.hfovFor(Double(s.width), Double(s.height)))
  }

  /// Poses the set for stage time `stage`, the ease toward the close `ret`, and the record's spin `platter`.
  func apply(stage: Double, ret: Double, platter: Double) {
    let sc = M.sceneAt(stage, ret)
    cameraNode.simdPosition = SIMD3<Float>(sc.shot.eye)
    cameraNode.simdLook(at: SIMD3<Float>(sc.shot.target), up: SIMD3<Float>(0, 1, 0), localFront: SIMD3<Float>(0, 0, -1))
    if let record {
      record.simdPosition = SIMD3<Float>(sc.pose.record)
      record.simdOrientation = simd_quatf(angle: Float(sc.pose.recordYaw), axis: SIMD3<Float>(0, 1, 0))
    }
    // the spin on its own axis, clockwise as the viewer sees side A: a negative angle about +z
    spin?.simdOrientation = simd_quatf(angle: Float(-platter.truncatingRemainder(dividingBy: 2 * Double.pi)), axis: SIMD3<Float>(0, 0, 1))
    // the groove highlight stays with the light and the eye, not with the record's spin
    sheen?.simdOrientation = simd_quatf(angle: Float(M.sheenAngle(sc)), axis: SIMD3<Float>(0, 0, 1))
    if let sleeve {
      sleeve.simdPosition = SIMD3<Float>(sc.pose.sleeve)
      sleeve.simdOrientation = simd_quatf(angle: Float(sc.pose.sleeveYaw), axis: SIMD3<Float>(0, 1, 0))
    }
  }
}
