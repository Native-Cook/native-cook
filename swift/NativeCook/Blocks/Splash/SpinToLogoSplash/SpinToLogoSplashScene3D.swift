import Foundation
import SceneKit
import simd

/// The 3D stage, drawn with SceneKit: the Arca sculpture (three nested arches and a terracotta pearl) from the React
/// Native block's arca.glb, converted once to SpinToLogoSplash-arca.usdz with nc-port/glb2usdz.sh (glTF space, node
/// names and PBR materials kept), lit like Filament: the same studio IBL (the cmgen KTX as SpinToLogoSplash-studio.hdr),
/// a warm key and a cool fill, and ACES (legacy) tone mapping with +0.15 stops in a fragment modifier.
///
/// It never runs its own clock: `apply(t:yaw:)` poses it, called from the SCNView's render delegate on SceneKit's own
/// render loop, so no SwiftUI state changes per frame. The floor shadows and the track are drawn in SwiftUI under it.
final class SpinToLogoSplashScene3D {
  typealias M = SpinToLogoSplashMotion

  let scene = SCNScene()
  let cameraNode = SCNNode()
  private var rig: SCNNode?
  private var arches: [SCNNode?] = [nil, nil, nil]
  private var pearl: SCNNode?

  /// Filament's default camera exposure (f/16, 1/125 s, ISO 100).
  static let exposure = 1 / (1.2 * pow(2, log2(16.0 * 16.0 * 125.0)))

  init(modelURL: URL?, environmentURL: URL?) {
    let cam = SCNCamera()
    cam.fieldOfView = CGFloat(M.hfovDeg)
    cam.projectionDirection = .horizontal
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
    addLight(direction: M.fillDirection, lux: M.fillLux, kelvin: 7200)

    guard let modelURL, let model = try? SCNScene(url: modelURL, options: [.checkConsistency: false]) else { return }
    for child in model.rootNode.childNodes { scene.rootNode.addChildNode(child) }
    rig = scene.rootNode.childNode(withName: "ArcaRig", recursively: true)
    arches = M.arches.map { scene.rootNode.childNode(withName: $0.name, recursively: true) }
    pearl = scene.rootNode.childNode(withName: "Pearl", recursively: true)
    scene.rootNode.enumerateHierarchy { node, _ in
      node.skinner = nil
      guard let g = node.geometry else { return }
      node.castsShadow = false
      for m in g.materials { Self.fix(m) }
    }
  }

  /// Filament's Color::cct(K) (what react-native-filament's colorKelvin sets): the Planckian locus point in linear
  /// sRGB, divided by its largest channel. SceneKit's own `temperature` is far less warm for the same K.
  static func kelvinColor(_ k: Double) -> CGColor {
    let k2 = k * k
    let u = (0.860117757 + 1.54118254e-4 * k + 1.28641212e-7 * k2) / (1 + 8.42420235e-4 * k + 7.08145163e-7 * k2)
    let v = (0.317398726 + 4.22806245e-5 * k + 4.20481691e-8 * k2) / (1 - 2.89741816e-5 * k + 1.61456053e-7 * k2)
    let d = 1 / (2 * u - 8 * v + 4)
    let x = 3 * u * d, y = 2 * v * d
    let X = x / y, Z = (1 - x - y) / y
    let r = 3.2404542 * X - 1.5371385 - 0.4985314 * Z
    let g = -0.9692660 * X + 1.8760108 + 0.0415560 * Z
    let b = 0.0556434 * X - 0.2040259 + 1.0572252 * Z
    let m = max(r, g, b)
    let space = CGColorSpace(name: CGColorSpace.extendedLinearSRGB)!
    return CGColor(colorSpace: space, components: [CGFloat(r / m), CGFloat(g / m), CGFloat(b / m), 1])!
  }

  private func addLight(direction: M.V3, lux: Double, kelvin: CGFloat) {
    let l = SCNLight()
    l.type = .directional
    l.color = Self.kelvinColor(Double(kelvin))
    l.intensity = CGFloat(lux * Self.exposure / Double.pi * 1000)
    l.castsShadow = false
    let n = SCNNode()
    n.light = l
    scene.rootNode.addChildNode(n)
    n.simdLook(at: SIMD3<Float>(direction), up: SIMD3<Float>(0, 1, 0), localFront: SIMD3<Float>(0, 0, -1))
  }

  // MARK: materials

  /// From arca.glb's JSON: plain colours in linear Rec.709 (SceneKit reads Blender's as Display P3 linear), and the
  /// clear coats SceneKit's USD importer drops.
  private static let materials: [String: (color: SIMD3<Double>, metal: Double?, rough: Double?, coat: Double, coatRough: Double)] = [
    "ArcaSatinBlack": (SIMD3(0.018, 0.018, 0.02), 0, 0.48, 0, 0.06),
    "ArcaChampagne": (SIMD3(0.86, 0.73, 0.56), nil, nil, 0, 0),
    "ArcaGlazedBlack": (SIMD3(0.018, 0.018, 0.02), 0, 0.22, 0.6, 0.06),
    "ArcaTerracotta": (SIMD3(0.8, 0.16, 0.05), 0, 0.3, 0.5, 0.08),
  ]

  static func linearColor(_ c: SIMD3<Double>) -> CGColor {
    let space = CGColorSpace(name: CGColorSpace.extendedLinearSRGB)!
    return CGColor(colorSpace: space, components: [CGFloat(c.x), CGFloat(c.y), CGFloat(c.z), 1])!
  }

  /// Filament's ACES legacy tone mapping (ACES with a 1/0.6 brightness boost) after the colour grading's exposure
  /// (+0.15 stops), as Stephen Hill's fitted ACES.
  static let toneMap = """
    // Filament's ACES (ToneMapper.cpp: glow, red modifier, RRT/ODT fit, dim surround, desaturation) on ACEScg
    float3 c = max(_output.color.rgb, float3(0.0)) * 1.1095694;
    const float3x3 sRGB_to_AP1 = float3x3(float3(0.61319, 0.07021, 0.02062), float3(0.33951, 0.91634, 0.10957), float3(0.04737, 0.01345, 0.86961));
    const float3x3 AP1_to_sRGB = float3x3(float3(1.70505, -0.13026, -0.02400), float3(-0.62179, 1.14080, -0.12897), float3(-0.08326, -0.01055, 1.15297));
    const float3x3 AP1_to_AP0 = float3x3(float3(0.6954522414, 0.0447945634, -0.0055258826), float3(0.1406786965, 0.8596711185, 0.0040252103), float3(0.1638690622, 0.0955343182, 1.0015006723));
    const float3x3 AP0_to_AP1 = float3x3(float3(1.4514393161, -0.0765537734, 0.0083161484), float3(-0.2365107469, 1.1762296998, -0.0060324498), float3(-0.2149285693, -0.0996759264, 0.9977163014));
    const float3x3 AP1_to_XYZ = float3x3(float3(0.6624541811, 0.2722287168, -0.0055746495), float3(0.1340042065, 0.6740817658, 0.0040607335), float3(0.1561876870, 0.0536895174, 1.0103391003));
    const float3x3 XYZ_to_AP1 = float3x3(float3(1.6410233797, -0.6636628587, 0.0117218943), float3(-0.3248032942, 1.6153315917, -0.0082844420), float3(-0.2364246952, 0.0167563477, 0.9883948585));
    const float3 lumAP1 = float3(0.272229, 0.674082, 0.0536895);
    float3 ap0 = AP1_to_AP0 * (sRGB_to_AP1 * c);
    float mi = min(ap0.r, min(ap0.g, ap0.b)), ma = max(ap0.r, max(ap0.g, ap0.b));
    float sat = (max(ma, 1e-5) - max(mi, 1e-5)) / max(ma, 1e-2);
    float chroma = sqrt(max(ap0.b * (ap0.b - ap0.g) + ap0.g * (ap0.g - ap0.r) + ap0.r * (ap0.r - ap0.b), 0.0));
    float yc = (ap0.b + ap0.g + ap0.r + 1.75 * chroma) / 3.0;
    float sx = (sat - 0.4) / 0.2;
    float st = max(1.0 - abs(sx / 2.0), 0.0);
    float s = (1.0 + sign(sx) * (1.0 - st * st)) / 2.0;
    float gg = 0.05 * s;
    float glow = yc <= 2.0 / 3.0 * 0.08 ? gg : (yc >= 2.0 * 0.08 ? 0.0 : gg * (0.08 / yc - 0.5));
    ap0 *= 1.0 + glow;
    float hue = (ap0.r == ap0.g && ap0.g == ap0.b) ? 0.0 : 57.2957795 * atan2(sqrt(3.0) * (ap0.g - ap0.b), 2.0 * ap0.r - ap0.g - ap0.b);
    if (hue < 0.0) hue += 360.0;
    float ch = hue; if (ch < -180.0) ch += 360.0; else if (ch > 180.0) ch -= 360.0;
    float hw = smoothstep(0.0, 1.0, 1.0 - abs(2.0 * ch / 135.0)); hw *= hw;
    ap0.r += hw * sat * (0.03 - ap0.r) * (1.0 - 0.82);
    float3 ap1 = clamp(AP0_to_AP1 * ap0, 0.0, 65504.0);
    ap1 = mix(float3(dot(ap1, lumAP1)), ap1, 0.96);
    ap1 *= 1.0 / 0.6;
    float3 rp = (ap1 * (2.785085 * ap1 + 0.107772)) / (ap1 * (2.936045 * ap1 + 0.887122) + 0.806889);
    float3 xyz = AP1_to_XYZ * rp;
    float sum = max(xyz.x + xyz.y + xyz.z, 1e-10);
    float2 xy = xyz.xy / sum;
    float Y = pow(clamp(xyz.y, 0.0, 65504.0), 0.9811);
    float3 xyz2 = float3(xy.x * Y / max(xy.y, 1e-10), Y, (1.0 - xy.x - xy.y) * Y / max(xy.y, 1e-10));
    float3 cv = XYZ_to_AP1 * xyz2;
    cv = mix(float3(dot(cv, lumAP1)), cv, 0.93);
    // Filament's iOS layer shows its sRGB numbers as Display P3 (measured on the reference): P3 -> sRGB
    const float3x3 P3_to_sRGB = float3x3(float3(1.2249, -0.0420, -0.0197), float3(-0.2247, 1.0419, -0.0786), float3(0.0, 0.0, 1.0979));
    _output.color.rgb = clamp(P3_to_sRGB * clamp(AP1_to_sRGB * cv, 0.0, 1.0), 0.0, 1.0);
    """

  private static func fix(_ m: SCNMaterial) {
    m.lightingModel = .physicallyBased
    if let spec = materials[m.name ?? ""] {
      m.diffuse.contents = linearColor(spec.color)
      if let metal = spec.metal { m.metalness.contents = NSNumber(value: metal) } else { m.metalness.textureComponents = .blue }
      if let rough = spec.rough { m.roughness.contents = NSNumber(value: rough) } else { m.roughness.textureComponents = .green }
      m.clearCoat.contents = NSNumber(value: spec.coat)
      m.clearCoatRoughness.contents = NSNumber(value: spec.coatRough)
    }
    m.shaderModifiers = [.fragment: toneMap]
  }

  // MARK: per frame

  func apply(t: Double, yaw: Double) {
    let cam = M.cameraAt(t)
    cameraNode.simdPosition = SIMD3<Float>(cam.eye)
    cameraNode.simdLook(at: SIMD3<Float>(cam.target), up: SIMD3<Float>(0, 1, 0), localFront: SIMD3<Float>(0, 0, -1))
    rig?.simdOrientation = simd_quatf(angle: Float(yaw * Double.pi / 180), axis: SIMD3<Float>(0, 1, 0))
    for i in 1..<3 {
      arches[i]?.simdOrientation = simd_quatf(angle: Float(M.archYaw(t, i) * Double.pi / 180), axis: SIMD3<Float>(0, 1, 0))
    }
    pearl?.simdPosition = SIMD3<Float>(0, Float(M.pearlY(t)), 0)
  }
}
