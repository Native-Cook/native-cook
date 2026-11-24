import Foundation
import SceneKit
import simd

/// The 3D layer, drawn with SceneKit: the sheet of paper from the React Native block's plane.glb (sixteen rigid
/// panels hinged on a dart's real creases; converted once to PaperPlaneSend-plane.usdz with glb2usdz.sh, which keeps
/// glTF space and node names), lit like Filament: the same loft IBL (the cmgen KTX as PaperPlaneSend-loft.hdr), a
/// key and a fill, ACES (legacy) tone mapping in a fragment modifier. See-through over the chat.
///
/// The fold does not rely on the glTF animation: every panel is turned about its hinge from the hinge table, as a
/// function of the film's time. `apply(t:geo:)` is called from the SCNView's render delegate.
final class PaperPlaneSendScene3D {
  typealias M = PaperPlaneSendMotion

  let scene = SCNScene()
  let cameraNode = SCNNode()
  private(set) var ready = false
  private var root: SCNNode?
  private var panels: [SCNNode?] = []
  private var lastFold = -1.0
  private var parked = true
  private var cameraKey = M.Geo(w: 0, h: 0)

  /// Filament's default camera exposure (f/16, 1/125 s, ISO 100).
  static let exposure = 1 / (1.2 * pow(2, log2(16.0 * 16.0 * 125.0)))

  init(modelURL: URL?, environmentURL: URL?) {
    let cam = SCNCamera()
    cam.fieldOfView = CGFloat(M.vfovDeg)
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
    addLight(direction: M.keyDirection, lux: M.keyLux, kelvin: 5600)
    addLight(direction: M.fillDirection, lux: M.fillLux, kelvin: 7500)

    guard let modelURL, let model = try? SCNScene(url: modelURL, options: [.checkConsistency: false]) else { return }
    for child in model.rootNode.childNodes { scene.rootNode.addChildNode(child) }
    scene.rootNode.enumerateHierarchy { node, _ in
      // the baked fold (if the conversion kept it) is not used: the hinges are set from the table
      node.removeAllAnimations()
      guard let g = node.geometry else { return }
      node.castsShadow = false
      for m in g.materials { Self.fix(m) }
    }
    root = scene.rootNode.childNode(withName: "Plane", recursively: true)
    panels = M.hinges.map { scene.rootNode.childNode(withName: $0.node, recursively: true) }
    ready = root != nil && !panels.contains { $0 == nil }
    root?.simdScale = SIMD3<Float>(repeating: 0.0001)
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

  /// From plane.glb's JSON: the three paper tints in linear Rec.709 and their roughness.
  private static let materials: [String: (color: SIMD3<Double>, rough: Double)] = [
    "Paper": (SIMD3(0.9215819, 0.8962694, 0.8227857), 0.82),
    "PaperKeel": (SIMD3(0.8631572, 0.8307699, 0.7379104), 0.84),
    "PaperFlap": (SIMD3(0.8148466, 0.7758222, 0.6724432), 0.86),
  ]

  /// SceneKit's light temperatures come out a little cooler on white paper than Filament's (5600 K key, 7500 K
  /// fill): measured on the flat sheet against the React Native recording, (225, 224, 221) vs (225, 221, 211).
  /// The paper's tints carry the difference.
  static let warmth = SIMD3<Double>(0.97, 0.97 * 0.925, 0.97 * 0.78)

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

  private static func fix(_ m: SCNMaterial) {
    m.lightingModel = .physicallyBased
    if let spec = materials[m.name ?? ""] {
      let space = CGColorSpace(name: CGColorSpace.extendedLinearSRGB)!
      let c = spec.color * warmth
      m.diffuse.contents = CGColor(colorSpace: space, components: [CGFloat(c.x), CGFloat(c.y), CGFloat(c.z), 1])!
      m.metalness.contents = NSNumber(value: 0)
      m.roughness.contents = NSNumber(value: spec.rough)
    }
    m.shaderModifiers = [.fragment: toneMap]
  }

  // MARK: per frame

  func apply(t: Double, geo g: M.Geo?) {
    guard let root, let g else { return }
    // metres per point: the sheet is g.sheetPt wide on screen
    let m = M.sheetL / g.sheetPt
    if cameraKey.h != g.h || cameraKey.sheetPt != g.sheetPt {
      // straight at the plane z = 0 from the distance where the screen's height is g.h points
      cameraKey = g
      cameraNode.simdPosition = SIMD3<Float>(0, 0, Float(g.h * m / 2 / tan(M.vfovDeg * Double.pi / 360)))
      cameraNode.simdOrientation = simd_quatf(angle: 0, axis: SIMD3<Float>(0, 1, 0))
    }
    // outside the 3D part of the film the plane is parked (the layer is transparent then)
    // (at no size rather than hidden, so SceneKit has compiled its shaders before the hand-off's first frame)
    guard M.stageOn(t) else {
      if !parked {
        parked = true
        root.simdScale = SIMD3<Float>(repeating: 0.0001)
      }
      return
    }
    parked = false

    // the fold: written only when its time changes
    let ft = M.foldTime(t)
    if ft != lastFold {
      lastFold = ft
      for (i, h) in M.hinges.enumerated() {
        guard let n = panels[i] else { continue }
        let k = M.easeInOutCubic(M.seg(ft, h.step.span.0, h.step.span.1))
        n.simdPosition = SIMD3<Float>(h.pivot)
        n.simdOrientation = simd_quatf(angle: Float(h.deg * Double.pi / 180 * k), axis: simd_normalize(SIMD3<Float>(h.axis)))
      }
    }

    // the pose: T(place on the plane z = 0) · Rx(-tilt) · Rz(heading) · Rx(90° + roll) · S
    let p = M.poseAt(t, g)
    let r = Float.pi / 180
    root.simdScale = SIMD3<Float>(repeating: Float(p.scale))
    root.simdOrientation = simd_quatf(angle: Float(-p.tilt) * r, axis: SIMD3<Float>(1, 0, 0))
      * simd_quatf(angle: Float(p.heading) * r, axis: SIMD3<Float>(0, 0, 1))
      * simd_quatf(angle: Float(90 + p.roll) * r, axis: SIMD3<Float>(1, 0, 0))
    root.simdPosition = SIMD3<Float>(Float((p.x - g.w / 2) * m), Float((g.h / 2 - p.y) * m), 0)
  }
}
