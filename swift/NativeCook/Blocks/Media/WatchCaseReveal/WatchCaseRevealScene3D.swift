import Foundation
import SceneKit
import simd

/// The 3D stage, drawn with SceneKit: the walnut case, its pillow and the watch from the React Native block's
/// watch.glb (converted once to WatchCaseReveal-watch.usdz with nc-port/glb2usdz.sh, which keeps glTF space, node
/// names and the PBR materials), lit like Filament: the same studio IBL (the cmgen KTX turned into
/// WatchCaseReveal-studio.hdr), a warm key and a cool fill, and ACES (legacy) tone mapping in a fragment modifier.
///
/// It never runs its own clock: `apply(_:)` poses it from a WatchCaseRevealPose, called from the SCNView's render
/// delegate on SceneKit's own render loop, so no SwiftUI state changes per frame.
final class WatchCaseRevealScene3D {
  typealias M = WatchCaseRevealMotion

  let scene = SCNScene()
  let cameraNode = SCNNode()
  private(set) var ready = false
  private var lid: SCNNode?
  private var cushion: SCNNode?
  private var second: SCNNode?
  private var dial: SCNMaterial?
  private var glint: SCNNode?
  private var glintMaterial: SCNMaterial?
  private var lastDialKey = ""
  private var size = CGSize(width: 402, height: 478)

  /// Filament's default camera exposure (f/16, 1/125 s, ISO 100): every light below is multiplied by it.
  static let exposure = 1 / (1.2 * pow(2, log2(16.0 * 16.0 * 125.0)))
  // SceneKit's units for the same light: a directional light of 1000 "lumens" lights a white Lambert surface to
  // albedo x N.L, Filament's to albedo / pi x lux x exposure; the IBL intensity is a plain multiplier. Measured
  // against the React Native recording: front of the case (121, 66, 39) vs (116, 66, 41), clasp (120, 60, 34) vs
  // (112, 59, 35) in sRGB with no extra factor.

  init(modelURL: URL?, environmentURL: URL?) {
    let cam = SCNCamera()
    cam.fieldOfView = CGFloat(M.hfovDeg)
    cam.projectionDirection = .horizontal
    cam.zNear = M.near
    cam.zFar = M.far
    cam.wantsHDR = false
    cameraNode.camera = cam
    cameraNode.name = "WatchCamera"
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
    lid = scene.rootNode.childNode(withName: "Lid", recursively: true)
    cushion = scene.rootNode.childNode(withName: "Cushion", recursively: true)
    second = scene.rootNode.childNode(withName: "HandSecond", recursively: true)
    scene.rootNode.enumerateHierarchy { node, _ in
      guard let g = node.geometry else { return }
      node.castsShadow = false
      for m in g.materials { Self.fix(m) }
      if node.parent?.name == "Dial", let m = g.materials.first { dial = m }
    }
    if let cushion { addGlint(to: cushion) }
    ready = lid != nil && cushion != nil
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

  /// What glb2usdz.sh cannot carry over, put back from the .glb's JSON (values copied from watch.glb): plain colours
  /// in linear Rec.709 (SceneKit reads Blender's as Display P3 linear), baseColorFactor x texture (USD drops the
  /// factor), and the normal maps' scale.
  private static let materials: [String: (color: SIMD3<Double>?, metal: Double, rough: Double, normal: Double)] = [
    "Walnut": (nil, 0, 0.24, 0.4),
    "Brass": (SIMD3(0.6938717, 0.4677838, 0.1844750), 1, 0.22, 1),
    "Suede": (nil, 0, 0.92, 0.8),
    "CaseShadow": (SIMD3(0, 0, 0), 0, 1, 1),
    "Leather": (SIMD3(0.0423114, 0.0176420, 0.0091341), 0, 0.5, 0.7),
    "Steel": (SIMD3(0.6866853, 0.7011019, 0.7230551), 1, 0.13, 1),
    "Dial": (nil, 0.55, 0.3, 1),
    "DialPrint": (SIMD3(0.8549926, 0.8796224, 0.9130986), 0, 0.5, 1),
    "BrushedSteel": (SIMD3(0.5840784, 0.6038274, 0.6307572), 1, 0.32, 1),
    "Lining": (SIMD3(0.6866853, 0.5906188, 0.4507858), 0, 0.6, 1),
  ]

  static func linearColor(_ c: SIMD3<Double>, alpha: Double = 1) -> CGColor {
    let space = CGColorSpace(name: CGColorSpace.extendedLinearSRGB)!
    return CGColor(colorSpace: space, components: [CGFloat(c.x), CGFloat(c.y), CGFloat(c.z), CGFloat(alpha)])!
  }

  /// Filament's ACES legacy tone mapping (ACES with a 1/0.6 brightness boost), as Stephen Hill's fitted ACES
  /// (sRGB -> AP1 with the RRT's desaturation, the RRT+ODT curve, AP1 -> sRGB with the ODT's), applied to the lit
  /// colour of every model material; the drawable's sRGB encoding does the rest. A per-channel curve without the
  /// matrices (Narkowicz) leaves the walnut too orange.
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

  /// The dial: the greyscale sunburst texture times a tint (glTF's baseColorFactor), set per frame.
  static let tintSurface = """
    #pragma arguments
    float4 watchTint;
    #pragma body
    _surface.diffuse.rgb *= watchTint.rgb;
    """

  private static func fix(_ m: SCNMaterial) {
    m.lightingModel = .physicallyBased
    if let spec = materials[m.name ?? ""] {
      if let c = spec.color { m.diffuse.contents = linearColor(c) }
      m.metalness.contents = NSNumber(value: spec.metal)
      m.roughness.contents = NSNumber(value: spec.rough)
      m.normal.intensity = CGFloat(spec.normal)
    }
    var mods: [SCNShaderModifierEntryPoint: String] = [.fragment: toneMap]
    if m.name == "Dial" { mods[.surface] = tintSurface }
    if m.name == "CaseShadow" {
      // glTF BLEND fades every term by alpha (gltfio's "fade"); SceneKit's lit transparency would still add the
      // plane's specular where alpha is 0 (a pale halo round the case). The shadow is black x alpha: unlit.
      m.lightingModel = .constant
      if let alpha = m.transparent.contents { m.diffuse.contents = alpha }
      m.transparent.contents = nil
      m.writesToDepthBuffer = false
      m.blendMode = .alpha
      m.shaderModifiers = [.fragment: "_output.color = float4(0.0, 0.0, 0.0, _output.color.a);"]
      return
    }
    m.shaderModifiers = mods
  }

  // MARK: the crystal's glint (WatchLight.tsx's WatchGlint, in the scene so it never lags the watch)

  /// A soft band of light across the crystal: a disc on the dial's rim (cushion space) drawn additively over
  /// everything, its band a screen-space linear gradient whose ends are set per frame (in pt, y down).
  static let glintSurface = """
    #pragma arguments
    float4 glintEnds;
    float4 glintView;
    #pragma body
    float4 clip = scn_frame.projectionTransform * float4(_surface.position, 1.0);
    float2 ndc = clip.xy / clip.w;
    float2 p = float2((ndc.x * 0.5 + 0.5) * glintView.x, (0.5 - ndc.y * 0.5) * glintView.y);
    float2 a = glintEnds.xy;
    float2 d = glintEnds.zw - a;
    float s = clamp(dot(p - a, d) / max(dot(d, d), 1e-4), 0.0, 1.0);
    float al = s < 0.38 ? mix(0.0, 0.18, s / 0.38)
             : s < 0.5 ? mix(0.18, 0.55, (s - 0.38) / 0.12)
             : s < 0.62 ? mix(0.55, 0.18, (s - 0.5) / 0.12)
             : mix(0.18, 0.0, (s - 0.62) / 0.38);
    // Skia adds in the sRGB-encoded canvas; over the dark dial that is about this much light in linear space
    float k = pow(al * glintView.z, 2.2);
    _surface.diffuse = float4(k, k, k, 1.0);
    """

  private func addGlint(to cushion: SCNNode) {
    let n = 28
    var verts = [SCNVector3(Float(M.dialC.x), Float(M.dialC.y), Float(M.dialC.z))]
    for i in 0..<n {
      let a = 2 * Double.pi * Double(i) / Double(n)
      verts.append(SCNVector3(Float(M.dialC.x + cos(a) * M.dialR), Float(M.dialC.y), Float(M.dialC.z + sin(a) * M.dialR)))
    }
    var idx: [UInt16] = []
    for i in 0..<n { idx += [0, UInt16(1 + (i + 1) % n), UInt16(1 + i)] }
    let geo = SCNGeometry(sources: [SCNGeometrySource(vertices: verts)],
                          elements: [SCNGeometryElement(indices: idx, primitiveType: .triangles)])
    let m = SCNMaterial()
    m.lightingModel = .constant
    m.isDoubleSided = true
    m.blendMode = .add
    m.readsFromDepthBuffer = false
    m.writesToDepthBuffer = false
    m.shaderModifiers = [.surface: Self.glintSurface]
    m.setValue(NSValue(scnVector4: SCNVector4(0, 0, 1, 1)), forKey: "glintEnds")
    m.setValue(NSValue(scnVector4: SCNVector4(402, 478, 0, 0)), forKey: "glintView")
    geo.materials = [m]
    let node = SCNNode(geometry: geo)
    node.name = "WatchGlint"
    node.renderingOrder = 100
    node.castsShadow = false
    cushion.addChildNode(node)
    glint = node
    glintMaterial = m
  }

  // MARK: per frame

  func setSize(_ s: CGSize) { if s.width > 0, s.height > 0 { size = s } }

  func apply(_ p: WatchCaseRevealPose) {
    let cam = M.cameraAt(p.open, p.macro, p.t)
    cameraNode.simdPosition = SIMD3<Float>(cam.eye)
    cameraNode.simdLook(at: SIMD3<Float>(cam.target), up: SIMD3<Float>(0, 1, 0), localFront: SIMD3<Float>(0, 0, -1))

    if let lid {
      lid.simdPosition = SIMD3<Float>(M.lidHinge)
      lid.simdOrientation = simd_quatf(angle: Float(-M.lidOpen * M.lidOf(p.open)), axis: SIMD3<Float>(1, 0, 0))
    }
    let tilt = M.tilt * M.tipOf(p.open)
    let lift = M.liftOf(p.open)
    if let cushion {
      cushion.simdPosition = SIMD3<Float>(0, Float(M.cushionY + M.lift * lift), 0)
      cushion.simdOrientation = simd_quatf(angle: Float(p.yaw), axis: SIMD3<Float>(0, 1, 0))
        * simd_quatf(angle: Float(tilt), axis: SIMD3<Float>(1, 0, 0))
    }
    if let second {
      second.simdOrientation = simd_quatf(angle: Float(-M.secondAngle(p.secs)), axis: SIMD3<Float>(0, 1, 0))
    }
    if let dial {
      let k = M.easeInOutSine(M.clamp01(p.dialK))
      let key = "\(p.dialFrom),\(p.dialTo),\(Int((k * 400).rounded()))"
      if key != lastDialKey {
        lastDialKey = key
        let a = M.dials[p.dialFrom], b = M.dials[p.dialTo]
        let ca = M.linear(a.color), cb = M.linear(b.color)
        let c = ca + (cb - ca) * k
        dial.setValue(NSValue(scnVector4: SCNVector4(Float(c.x), Float(c.y), Float(c.z), 1)), forKey: "watchTint")
        dial.metalness.contents = NSNumber(value: M.lerp(a.metal, b.metal, k))
        dial.roughness.contents = NSNumber(value: M.lerp(a.rough, b.rough, k))
      }
    }
    updateGlint(p, cam: cam, tilt: tilt, lift: lift)
  }

  private func updateGlint(_ p: WatchCaseRevealPose, cam: M.Shot, tilt: Double, lift: Double) {
    guard let glint, let m = glintMaterial else { return }
    let k = M.clamp01((lift - 0.2) / 0.8)
    glint.isHidden = k <= 0.001
    if glint.isHidden { return }
    let w = Double(size.width), h = Double(size.height)
    let b = M.basis(cam, width: w)
    let y = M.cushionY + M.lift * lift
    var minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9
    for i in 0..<28 {
      let a = 2 * Double.pi * Double(i) / 28
      let q = M.V3(M.dialC.x + cos(a) * M.dialR, M.dialC.y, M.dialC.z + sin(a) * M.dialR)
      guard let s = M.project(cam, b, w, h, M.cushionToWorld(q, tilt: tilt, yaw: p.yaw, y: y)) else { continue }
      minX = min(minX, s.x); maxX = max(maxX, s.x); minY = min(minY, s.y); maxY = max(maxY, s.y)
    }
    let s = M.clamp01(0.5 + sin(p.yaw * 2.4) * 0.75 + (M.tipOf(p.open) - 1) * 0.3)
    let cx = minX + (maxX - minX) * s
    let cy = minY + (maxY - minY) * (1 - s * 0.6)
    let r = (maxX - minX) * 0.35
    m.setValue(NSValue(scnVector4: SCNVector4(Float(cx - r), Float(cy - r * 1.4), Float(cx + r), Float(cy + r * 1.4))), forKey: "glintEnds")
    m.setValue(NSValue(scnVector4: SCNVector4(Float(w), Float(h), Float(0.5 * k), 0)), forKey: "glintView")
  }
}
