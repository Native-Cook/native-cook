import Foundation
import SceneKit
import simd

/// What the stage shows at one instant.
struct RockerLightSwitchPose {
  /// The film's time (the camera's drift), and how much of the drift shows (it eases out on the way home).
  var t = 0.0
  var drift = 1.0
  /// The rocker, 0 (off) .. 1 (on).
  var press = 0.0
  /// The filament, 0..1, and the brightness, 0..1.
  var level = 0.0
  var bright = 1.0
  /// The camera's way round the wall, 0..1.
  var orbit = 0.0

  var light: Double { RockerLightSwitchMotion.lightOut(level, bright) }
  var camera: RockerLightSwitchMotion.Shot {
    let M = RockerLightSwitchMotion.self
    var c = M.cameraAt(orbit, t)
    let still = M.cameraAt(orbit, 0)
    c.eye = still.eye + (c.eye - still.eye) * drift
    return c
  }
}

/// The 3D stage, drawn with SceneKit: the painted wall, the rocker switch and the brass sconce from the React Native
/// block's switch.glb (converted once to RockerLightSwitch-switch.usdz with glb2usdz.sh, which keeps glTF space and
/// node names), lit like Filament: the same dusk IBL (the cmgen KTX as RockerLightSwitch-loft.hdr), the bulb as a
/// point light with Filament's own maths in a fragment modifier (lumens, inverse square, its falloff window, GGX),
/// the room's warm bounce as a directional light, the globe's emissive glow, and ACES (legacy) tone mapping.
///
/// It never runs its own clock: `apply(_:)` poses it, called from the SCNView's render delegate.
final class RockerLightSwitchScene3D {
  typealias M = RockerLightSwitchMotion

  let scene = SCNScene()
  let cameraNode = SCNNode()
  private(set) var ready = false
  private var rocker: SCNNode?
  private var lit: [SCNMaterial] = []
  private var globe: SCNMaterial?
  private var shadow: SCNMaterial?
  private var bounce: SCNLight?
  private var lastLight = -1
  private var aspect = 0.0

  static let glowKnee = 4.4
  static let glowTint = SIMD3<Double>(1, 0.92, 0.76)

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
    let l = SCNLight()
    l.type = .directional
    l.temperature = 3000
    l.intensity = 0
    l.castsShadow = false
    let n = SCNNode()
    n.light = l
    scene.rootNode.addChildNode(n)
    n.simdLook(at: SIMD3<Float>(M.bounceDirection), up: SIMD3<Float>(0, 1, 0), localFront: SIMD3<Float>(0, 0, -1))
    bounce = l

    guard let modelURL, let model = try? SCNScene(url: modelURL, options: [.checkConsistency: false]) else { return }
    for child in model.rootNode.childNodes { scene.rootNode.addChildNode(child) }
    rocker = scene.rootNode.childNode(withName: "Rocker", recursively: true)
    var seen = Set<ObjectIdentifier>()
    scene.rootNode.enumerateHierarchy { node, _ in
      guard let g = node.geometry else { return }
      node.castsShadow = false
      for m in g.materials where seen.insert(ObjectIdentifier(m)).inserted { fix(m, node: node) }
    }
    ready = rocker != nil
    apply(RockerLightSwitchPose(), size: CGSize(width: 393, height: 600))
  }

  // MARK: materials

  /// From switch.glb's JSON: plain colours in linear Rec.709 (SceneKit reads Blender's as Display P3 linear),
  /// metallic and roughness factors, the wall's normal scale.
  private static let materials: [String: (color: SIMD3<Double>?, metal: Double, rough: Double, normal: Double)] = [
    "GlobeOpal": (SIMD3(0.8631572, 0.8148466, 0.7230551), 0, 0.38, 1),
    "PlatePlastic": (SIMD3(0.8549926, 0.8307699, 0.7835378), 0, 0.42, 1),
    "RockerPlastic": (SIMD3(0.9046612, 0.8879231, 0.8468732), 0, 0.3, 1),
    "SconceBrass": (SIMD3(0.4793202, 0.2917706, 0.1022417), 1, 0.36, 1),
    "ScrewNickel": (SIMD3(0.62, 0.6, 0.56), 1, 0.34, 1),
    "WallPaint": (nil, 0, 0.92, 0.4),
    "WellDark": (SIMD3(0.0097212, 0.0091341, 0.0085681), 0, 0.8, 1),
  ]
  /// The three soft decals (glTF BLEND, a dark baseColorFactor x an alpha texture): their colour.
  private static let decals: [String: SIMD3<Double>] = [
    "BackAO": SIMD3(0.0231534, 0.0185002, 0.0144438),
    "PlateAO": SIMD3(0.0231534, 0.0185002, 0.0144438),
    "PlateShadow": SIMD3(0.0241576, 0.0152085, 0.0103298),
  ]

  static func linearColor(_ c: SIMD3<Double>) -> CGColor {
    let space = CGColorSpace(name: CGColorSpace.extendedLinearSRGB)!
    return CGColor(colorSpace: space, components: [CGFloat(c.x), CGFloat(c.y), CGFloat(c.z), 1])!
  }

  /// After SceneKit's own lighting (the IBL and the bounce): the bulb, the globe's glow, then the tone curve.
  ///
  /// The bulb is Filament's point light: I = lumens / 4π candela, times the camera's exposure (both folded into
  /// rockerBulb.rgb with the colour), inverse square with Filament's window (1 - (d/r)^4)^2, Lambert / π and its GGX
  /// specular (D GGX, Smith-GGX correlated visibility, Schlick). No shadows, as in the React Native stage.
  /// Then Filament's ACES legacy tone mapping (Stephen Hill's fit with a 1/0.6 boost).
  static let fragment = """
    #pragma arguments
    float4 rockerBulb;
    float4 rockerMat;
    float4 rockerEmit;
    #pragma body
    float3 rkP = (scn_frame.inverseViewTransform * float4(_surface.position, 1.0)).xyz;
    float3 rkN = normalize((scn_frame.inverseViewTransform * float4(_surface.normal, 0.0)).xyz);
    float3 rkV = normalize((scn_frame.inverseViewTransform * float4(-_surface.position, 0.0)).xyz);
    float3 rkL = float3(-0.066, 0.062, 0.1) - rkP;
    float rkD2 = max(dot(rkL, rkL), 1e-4);
    rkL = rkL / sqrt(rkD2);
    float rkF = rkD2 / (2.5 * 2.5);
    float rkS = clamp(1.0 - rkF * rkF, 0.0, 1.0);
    float rkNoL = clamp(dot(rkN, rkL), 0.0, 1.0);
    float3 rkBase = _surface.diffuse.rgb;
    float3 rkF0 = mix(float3(0.04), rkBase, rockerMat.x);
    float rkA = max(rockerMat.y * rockerMat.y, 0.002);
    float3 rkH = normalize(rkV + rkL);
    float rkNoH = clamp(dot(rkN, rkH), 0.0, 1.0);
    float rkNoV = max(dot(rkN, rkV), 1e-4);
    float rkLoH = clamp(dot(rkL, rkH), 0.0, 1.0);
    float rkA2 = rkA * rkA;
    float rkDen = rkNoH * rkNoH * (rkA2 - 1.0) + 1.0;
    float rkD = rkA2 / (3.14159265 * rkDen * rkDen);
    float rkVis = 0.5 / max(rkNoL * sqrt(rkNoV * rkNoV * (1.0 - rkA2) + rkA2) + rkNoV * sqrt(rkNoL * rkNoL * (1.0 - rkA2) + rkA2), 1e-4);
    float3 rkFr = rkF0 + (float3(1.0) - rkF0) * pow(1.0 - rkLoH, 5.0);
    float3 rkLit = (rkBase * (1.0 - rockerMat.x) / 3.14159265 + rkD * rkVis * rkFr) * rockerBulb.rgb * (rkS * rkS / rkD2 * rkNoL);
    float3 v = max(_output.color.rgb + rkLit + rockerEmit.rgb, float3(0.0)) * (1.0 / 0.6);
    float3 a = float3(dot(float3(0.59719, 0.35458, 0.04823), v),
                      dot(float3(0.07600, 0.90834, 0.01566), v),
                      dot(float3(0.02840, 0.13383, 0.83777), v));
    a = (a * (a + 0.0245786) - 0.000090537) / (a * (0.983729 * a + 0.4329510) + 0.238081);
    float3 o = float3(dot(float3(1.60475, -0.53108, -0.07367), a),
                      dot(float3(-0.10208, 1.10813, -0.00605), a),
                      dot(float3(-0.00327, -0.07276, 1.07602), a));
    _output.color.rgb = clamp(o, 0.0, 1.0);
    """

  /// A decal: unlit, its texture's alpha times a colour and an alpha set from code (premultiplied).
  static let decalFragment = """
    #pragma arguments
    float4 rockerDecal;
    #pragma body
    float rkA = _output.color.a * rockerDecal.a;
    _output.color = float4(rockerDecal.rgb * rkA, rkA);
    """

  private func fix(_ m: SCNMaterial, node: SCNNode) {
    let name = m.name ?? ""
    if let c = Self.decals[name] {
      // glTF BLEND fades every term by alpha; SceneKit's lit transparency would still add specular where alpha is
      // 0. These are near-black shades: unlit, the alpha texture as the colour.
      m.lightingModel = .constant
      if let alpha = m.transparent.contents { m.diffuse.contents = alpha }
      m.transparent.contents = nil
      m.writesToDepthBuffer = false
      m.blendMode = .alpha
      m.isDoubleSided = true
      m.shaderModifiers = [.fragment: Self.decalFragment]
      m.setValue(NSValue(scnVector4: SCNVector4(Float(c.x), Float(c.y), Float(c.z), name == "PlateShadow" ? 0 : 1)), forKey: "rockerDecal")
      node.renderingOrder = name == "PlateShadow" ? 20 : 10
      if name == "PlateShadow" { shadow = m }
      return
    }
    m.lightingModel = .physicallyBased
    var metal = 0.0, rough = 0.5
    if let spec = Self.materials[name] {
      if let c = spec.color { m.diffuse.contents = Self.linearColor(c) }
      m.metalness.contents = NSNumber(value: spec.metal)
      m.roughness.contents = NSNumber(value: spec.rough)
      m.normal.intensity = CGFloat(spec.normal)
      metal = spec.metal
      rough = spec.rough
    }
    m.shaderModifiers = [.fragment: Self.fragment]
    m.setValue(NSValue(scnVector4: SCNVector4(0, 0, 0, 0)), forKey: "rockerBulb")
    m.setValue(NSValue(scnVector4: SCNVector4(Float(metal), Float(rough), 0, 0)), forKey: "rockerMat")
    m.setValue(NSValue(scnVector4: SCNVector4(0, 0, 0, 0)), forKey: "rockerEmit")
    lit.append(m)
    if name == "GlobeOpal" { globe = m }
  }

  // MARK: per frame

  func apply(_ p: RockerLightSwitchPose, size: CGSize) {
    let a = Double(size.width / max(1, size.height))
    if a != aspect, a > 0 {
      aspect = a
      cameraNode.camera?.fieldOfView = CGFloat(2 * atan(M.tanHalfFovY(a)) * 180 / Double.pi)
    }
    let cam = p.camera
    cameraNode.simdPosition = SIMD3<Float>(cam.eye)
    cameraNode.simdLook(at: SIMD3<Float>(cam.target), up: SIMD3<Float>(0, 1, 0), localFront: SIMD3<Float>(0, 0, -1))

    if let rocker {
      // off = +rock (the bottom half in), on = -rock (the top half in), about x through the pivot
      rocker.simdPosition = SIMD3<Float>(M.rockerPivot)
      rocker.simdOrientation = simd_quatf(angle: Float(M.lerp(M.rock, -M.rock, p.press)), axis: SIMD3<Float>(1, 0, 0))
    }
    // the light: written only when it changes
    let k = p.light
    let q = Int((k * 1000).rounded())
    guard q != lastLight else { return }
    lastLight = q
    let col = M.warmColor(k)
    let i = M.bulbLumens * k / (4 * Double.pi) * Self.exposure
    let bulb = NSValue(scnVector4: SCNVector4(Float(col.x * i), Float(col.y * i), Float(col.z * i), 0))
    for m in lit { m.setValue(bulb, forKey: "rockerBulb") }
    bounce?.intensity = CGFloat(M.bounceLux * k * Self.exposure / Double.pi * 1000)
    // The globe's glow. Filament's bloom and its ACES keep a blown-out highlight's hue; the fitted curve here runs
    // to white, so the glow is given a soft knee and a warmer tint (measured against the React Native recording:
    // the globe at 100 % and at 40 % within a few sRGB levels).
    let e = Self.glowKnee * (1 - exp(-M.globeEmissive * k / Self.glowKnee))
    globe?.setValue(NSValue(scnVector4: SCNVector4(Float(col.x * e * Self.glowTint.x), Float(col.y * e * Self.glowTint.y), Float(col.z * e * Self.glowTint.z), 0)), forKey: "rockerEmit")
    shadow?.setValue(NSValue(scnVector4: SCNVector4(Float(M.shadowRGB.x), Float(M.shadowRGB.y), Float(M.shadowRGB.z), Float(k))), forKey: "rockerDecal")
  }
}
