import Foundation
import SceneKit
import simd

/// The pose the 3D stage shows: the lid, the camera's shot, the film time (camera drift), the box's turn, the metal
/// and stone blends and the stone's weight.
struct RingBoxRevealPose {
  var t: Double = 0
  var open: Double = 0
  var shot: Double = 0
  var yaw: Double = 0
  var burst: Double = 0
  var mFrom = 0, mTo = 0
  var mProg: Double = 0
  var sFrom = 0, sTo = 0
  var sProg: Double = 0
  var carat: Double = RingBoxRevealMotion.caratModel
}

/// The 3D stage, drawn with SceneKit: the velvet box, the solitaire and the box's reflection under the floor from the
/// React Native block's ring.glb (converted once to RingBoxReveal-ring.usdz with nc-port/glb2usdz.sh), lit like
/// Filament: the studio IBL (RingBoxReveal-studio.hdr), a warm key and a cool fill, ACES (legacy) in a fragment
/// modifier. `apply` is called from the SCNView's render delegate, so no SwiftUI state changes per frame.
final class RingBoxRevealScene3D {
  typealias M = RingBoxRevealMotion

  let scene = SCNScene()
  let cameraNode = SCNNode()
  private(set) var ready = false
  private var base: SCNNode?, baseM: SCNNode?, lid: SCNNode?, lidM: SCNNode?, ring: SCNNode?
  private var setting: [SCNNode] = []
  private var metal: [SCNMaterial] = []
  private var stone: [SCNMaterial] = []
  private var lastMat = ""
  private var lastCarat = -1.0

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
    addLight(direction: M.keyDirection, lux: M.keyLux, kelvin: 5600)
    addLight(direction: M.fillDirection, lux: M.fillLux, kelvin: 7000)

    guard let modelURL, let model = try? SCNScene(url: modelURL, options: [.checkConsistency: false]) else { return }
    for child in model.rootNode.childNodes { scene.rootNode.addChildNode(child) }
    let root = scene.rootNode
    base = root.childNode(withName: "Base", recursively: true)
    baseM = root.childNode(withName: "BaseM", recursively: true)
    lid = root.childNode(withName: "Lid", recursively: true)
    lidM = root.childNode(withName: "LidM", recursively: true)
    ring = root.childNode(withName: "Ring", recursively: true)
    for name in ["Stone", "Head", "Gallery"] {
      if let n = ring?.childNode(withName: name, recursively: false) { setting.append(n) }
    }
    root.enumerateHierarchy { node, _ in
      guard let g = node.geometry else { return }
      node.castsShadow = false
      for m in g.materials {
        Self.fix(m)
        if m.name == "RingMetal" { metal.append(m) }
        if m.name == "Stone" { stone.append(m) }
      }
    }
    ready = base != nil && lid != nil && ring != nil
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

  /// From ring.glb's JSON: plain colours in linear Rec.709, metalness, roughness, normal scale.
  private static let materials: [String: (color: SIMD3<Double>?, metal: Double, rough: Double, normal: Double)] = [
    "Velvet": (nil, 0, 0.62, 0.55),
    "Seam": (SIMD3(0.5840784, 0.5457245, 0.4793202), 1, 0.28, 1),
    "LidSatin": (SIMD3(0.8468732, 0.7681512, 0.6444797), 0, 0.34, 1),
    "RingMetal": (SIMD3(0.7835378, 0.7758222, 0.7605245), 1, 0.08, 1),
    "Stone": (SIMD3(0.9215819, 0.9386857, 1), 1, 0.02, 1),
  ]

  static func linearColor(_ c: SIMD3<Double>) -> CGColor {
    let space = CGColorSpace(name: CGColorSpace.extendedLinearSRGB)!
    return CGColor(colorSpace: space, components: [CGFloat(c.x), CGFloat(c.y), CGFloat(c.z), 1])!
  }

  /// Filament's ACES legacy tone mapping (exposure 0), as Stephen Hill's fitted ACES.
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

  /// The stone's baked facet pattern times its emissive colour (glTF's emissiveFactor, set per stone).
  static let stoneSurface = """
    #pragma arguments
    float4 stoneGlow;
    #pragma body
    _surface.emission.rgb *= stoneGlow.rgb;
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
    if m.name == "Stone" {
      m.isDoubleSided = true
      mods[.surface] = stoneSurface
      let c = M.linear(M.stones[0].color) * M.stones[0].glow
      m.setValue(NSValue(scnVector4: SCNVector4(Float(c.x), Float(c.y), Float(c.z), 1)), forKey: "stoneGlow")
    }
    m.shaderModifiers = mods
  }

  // MARK: per frame

  func apply(_ p: RingBoxRevealPose) {
    let cam = M.cameraAt(p.open, p.shot, p.t)
    cameraNode.simdPosition = SIMD3<Float>(cam.eye)
    cameraNode.simdLook(at: SIMD3<Float>(cam.target), up: SIMD3<Float>(0, 1, 0), localFront: SIMD3<Float>(0, 0, -1))

    let ry = simd_quatf(angle: Float(p.yaw), axis: SIMD3<Float>(0, 1, 0))
    base?.simdOrientation = ry
    baseM?.simdOrientation = ry
    if let lid {
      lid.simdPosition = ry.act(SIMD3<Float>(M.lidHinge))
      lid.simdOrientation = ry * simd_quatf(angle: Float(-M.lidOpen * p.open), axis: SIMD3<Float>(1, 0, 0))
    }
    if let lidM {
      let h = SIMD3<Float>(Float(M.lidHinge.x), Float(-M.lidHinge.y), Float(M.lidHinge.z))
      lidM.simdPosition = ry.act(h)
      lidM.simdOrientation = ry * simd_quatf(angle: Float(M.lidOpen * p.open), axis: SIMD3<Float>(1, 0, 0))
    }
    if let ring {
      ring.simdPosition = SIMD3<Float>(0, Float(M.ringY), 0)
      ring.simdOrientation = ry
    }
    // carat: the stone and its prongs scale about the girdle's centre (cube root of the weight)
    if abs(p.carat - lastCarat) > 0.0005 {
      lastCarat = p.carat
      let k = Float(cbrt(p.carat / M.caratModel))
      for n in setting {
        n.simdScale = SIMD3<Float>(repeating: k)
        n.simdPosition = SIMD3<Float>(0, Float(M.stoneLocalY) * (1 - k), 0)
      }
    }
    // metal and stone colours, blended (written only when they change)
    let mk = M.easeInOutSine(M.clamp01(p.mProg)), sk = M.easeInOutSine(M.clamp01(p.sProg))
    let key = "\(p.mFrom),\(p.mTo),\(Int((mk * 300).rounded())),\(p.sFrom),\(p.sTo),\(Int((sk * 300).rounded()))"
    if key != lastMat {
      lastMat = key
      let ma = M.metals[p.mFrom], mb = M.metals[p.mTo]
      let mc = M.linear(ma.color) + (M.linear(mb.color) - M.linear(ma.color)) * mk
      let mr = M.lerp(ma.rough, mb.rough, mk)
      for m in metal {
        m.diffuse.contents = Self.linearColor(mc)
        m.roughness.contents = NSNumber(value: mr)
      }
      let sa = M.stones[p.sFrom], sb = M.stones[p.sTo]
      let sc = M.linear(sa.color) + (M.linear(sb.color) - M.linear(sa.color)) * sk
      let glow = M.lerp(sa.glow, sb.glow, sk)
      for m in stone {
        m.diffuse.contents = Self.linearColor(sc)
        m.setValue(NSValue(scnVector4: SCNVector4(Float(sc.x * glow), Float(sc.y * glow), Float(sc.z * glow), 1)), forKey: "stoneGlow")
      }
    }
  }
}
