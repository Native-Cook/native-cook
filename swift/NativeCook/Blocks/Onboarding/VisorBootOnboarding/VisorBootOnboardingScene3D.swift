import Foundation
import SceneKit
import simd

/// The 3D stage, drawn with SceneKit: the helmet on its graphite plinth from the React Native block's visor.glb
/// (converted once to VisorBootOnboarding-visor.usdz with nc-port/glb2usdz.sh, which keeps glTF space, node names
/// and the PBR materials), lit like Filament: the same studio IBL (the cmgen KTX turned into
/// VisorBootOnboarding-studio.hdr), a warm key, a cool rim, the Get Started sweep light and ACES (legacy) tone
/// mapping in a fragment modifier.
///
/// It never runs its own clock: `apply(_:)` poses it from a VisorBootOnboardingState, called from the SCNView's
/// render delegate on SceneKit's own render loop, so no SwiftUI state changes per frame.
final class VisorBootOnboardingScene3D {
  typealias M = VisorBootOnboardingMotion

  let scene = SCNScene()
  let cameraNode = SCNNode()
  private(set) var ready = false
  private var rig: SCNNode?
  private var shell: [SCNMaterial] = []
  private let sweepNode = SCNNode()
  private let sweepLight = SCNLight()
  private var lastLamp = -1
  private var lastSweep = -1.0
  private var glow: SCNNode?
  private var glowMaterial: SCNMaterial?

  /// The panel lamp (helmet space; found from the emissive texture's lamp in the mesh's UVs) and its outward normal.
  static let lamp = SIMD3<Double>(-0.714, 0.19, 0.555)
  static let lampNormal = simd_normalize(SIMD3<Double>(-0.8, 0.05, 0.6))

  /// Filament's default camera exposure (f/16, 1/125 s, ISO 100): every light below is multiplied by it.
  static let exposure = 1 / (1.2 * pow(2, log2(16.0 * 16.0 * 125.0)))

  init(modelURL: URL?, environmentURL: URL?) {
    let cam = SCNCamera()
    cam.fieldOfView = CGFloat(M.hfovDeg)
    cam.projectionDirection = .horizontal
    cam.zNear = M.near
    cam.zFar = M.far
    cam.wantsHDR = false
    cameraNode.camera = cam
    cameraNode.name = "VisorCamera"
    scene.rootNode.addChildNode(cameraNode)
    scene.background.contents = CGColor(red: 0, green: 0, blue: 0, alpha: 0)

    if let environmentURL {
      scene.lightingEnvironment.contents = environmentURL
      scene.lightingEnvironment.intensity = CGFloat(M.iblIntensity * Self.exposure * Self.iblScale)
    }
    // SceneKit's colour temperature is much warmer than Filament's at the same kelvin, and its direct light lands
    // brighter on this metal shell: both settled against the React Native recording (cheek, chin, plinth patches).
    addLight(direction: M.keyDirection, lux: M.keyLux * Self.directScale, kelvin: 5200)
    addLight(direction: M.rimDirection, lux: M.rimLux * Self.directScale, kelvin: 9000)
    sweepLight.type = .directional
    sweepLight.temperature = 6500
    sweepLight.intensity = 0
    sweepLight.castsShadow = false
    sweepNode.light = sweepLight
    scene.rootNode.addChildNode(sweepNode)

    guard let modelURL, let model = try? SCNScene(url: modelURL, options: [.checkConsistency: false]) else { return }
    for child in model.rootNode.childNodes { scene.rootNode.addChildNode(child) }
    rig = scene.rootNode.childNode(withName: "HelmetRig", recursively: true)
    scene.rootNode.enumerateHierarchy { node, _ in
      node.skinner = nil
      guard let g = node.geometry else { return }
      node.castsShadow = false
      for m in g.materials {
        Self.fix(m)
        if m.name == "HelmetShell" { shell.append(m) }
      }
    }
    if let rig { addGlow(to: rig) }
    ready = rig != nil
  }

  // MARK: the lamp's bloom (Filament's bloom, strength 0.14 with a threshold: only the lamp blows past it)

  private func addGlow(to rig: SCNNode) {
    let px = 64
    guard let ctx = CGContext(data: nil, width: px, height: px, bitsPerComponent: 8, bytesPerRow: px * 4,
                              space: CGColorSpace(name: CGColorSpace.sRGB)!,
                              bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return }
    let colors = [CGColor(red: 1, green: 1, blue: 1, alpha: 1), CGColor(red: 1, green: 1, blue: 1, alpha: 0.25),
                  CGColor(red: 1, green: 1, blue: 1, alpha: 0)] as CFArray
    let grad = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB)!, colors: colors, locations: [0, 0.22, 1])!
    let c = CGPoint(x: px / 2, y: px / 2)
    ctx.drawRadialGradient(grad, startCenter: c, startRadius: 0, endCenter: c, endRadius: CGFloat(px / 2), options: [])
    guard let img = ctx.makeImage() else { return }
    let plane = SCNPlane(width: 0.22, height: 0.22)
    let m = SCNMaterial()
    m.lightingModel = .constant
    m.diffuse.contents = img
    m.multiply.contents = CGColor(red: 1, green: 0.76, blue: 0.48, alpha: 1)
    m.blendMode = .add
    m.writesToDepthBuffer = false
    m.readsFromDepthBuffer = true
    m.isDoubleSided = true
    m.transparency = 0
    plane.materials = [m]
    let n = SCNNode(geometry: plane)
    n.name = "VisorLampGlow"
    n.simdPosition = SIMD3<Float>(Self.lamp + Self.lampNormal * 0.06)
    n.constraints = [SCNBillboardConstraint()]
    n.renderingOrder = 100
    n.castsShadow = false
    rig.addChildNode(n)
    glow = n
    glowMaterial = m
  }

  private func updateGlow(lamp: Double, cam: M.Shot, lift: Double) {
    guard let glowMaterial else { return }
    let p = SIMD3<Double>(Self.lamp.x, Self.lamp.y + M.helmetY + lift, Self.lamp.z)
    let toCam = simd_normalize(cam.eye - p)
    let facing = M.clamp01((simd_dot(toCam, Self.lampNormal) - 0.05) / 0.35)
    let k = 0.08 * max(0, M.lampStrength * lamp - 1) * facing
    glowMaterial.transparency = CGFloat(min(1, k))
    glow?.isHidden = k < 0.002
  }

  private func addLight(direction: M.V3, lux: Double, kelvin: CGFloat) {
    let l = SCNLight()
    l.type = .directional
    l.temperature = kelvin
    l.intensity = CGFloat(Self.lightIntensity(lux))
    l.castsShadow = false
    let n = SCNNode()
    n.light = l
    scene.rootNode.addChildNode(n)
    n.simdLook(at: SIMD3<Float>(direction), up: SIMD3<Float>(0, 1, 0), localFront: SIMD3<Float>(0, 0, -1))
  }

  static let directScale = 0.4
  static let iblScale = 1.2

  static func lightIntensity(_ lux: Double) -> Double { lux * exposure / Double.pi * 1000 }

  // MARK: materials

  static func linearColor(_ c: SIMD3<Double>, alpha: Double = 1) -> CGColor {
    let space = CGColorSpace(name: CGColorSpace.extendedLinearSRGB)!
    return CGColor(colorSpace: space, components: [CGFloat(c.x), CGFloat(c.y), CGFloat(c.z), CGFloat(alpha)])!
  }

  /// Filament's ACES legacy tone mapping (ACES with a 1/0.6 brightness boost), as Stephen Hill's fitted ACES.
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

  /// What glb2usdz.sh cannot carry over, put back from visor.glb's JSON: the shell's occlusion map (the red channel
  /// of the packed AO/metal/rough texture; SceneKit's importer drops it), the ring's colour in linear Rec.709, and
  /// the emissive lamps, driven per frame (emissiveFactor x LAMP_STRENGTH x level, added unexposed like gltfio).
  private static func fix(_ m: SCNMaterial) {
    m.lightingModel = .physicallyBased
    switch m.name ?? "" {
    case "HelmetShell":
      if let packed = m.metalness.contents {
        m.ambientOcclusion.contents = packed
        m.ambientOcclusion.textureComponents = .red
      }
      m.emission.intensity = CGFloat(M.lampStrength * 0.15)
    case "PlinthGraphite":
      m.metalness.contents = NSNumber(value: 0)
      m.roughness.contents = NSNumber(value: 0.38)
      // Filament's IBL specular on the graphite top (a cool sheen of the studio's ceiling) is stronger than
      // SceneKit's prefiltered one: put the difference back, measured on the top's front.
      m.shaderModifiers = [.fragment: "_output.color.rgb += float3(0.0045, 0.0052, 0.0068);\n" + toneMap]
      return
    case "PlinthRing":
      m.diffuse.contents = linearColor(SIMD3(0.62, 0.6, 0.57))
      m.metalness.contents = NSNumber(value: 1)
      m.roughness.contents = NSNumber(value: 0.24)
    default: break
    }
    m.shaderModifiers = [.fragment: toneMap]
  }

  // MARK: per frame

  func apply(_ s: VisorBootOnboardingState) {
    let cam = M.cameraAt(s)
    cameraNode.simdPosition = SIMD3<Float>(cam.eye)
    cameraNode.simdLook(at: SIMD3<Float>(cam.target), up: SIMD3<Float>(0, 1, 0), localFront: SIMD3<Float>(0, 0, -1))

    // the light sweep of the Get Started beat swings across the front; only the film has it
    let sw = M.filmSweep(s.t)
    let level = sw.0 * (1 - M.clamp01(s.m))
    if level != lastSweep {
      lastSweep = level
      let az = M.lerp(M.sweepFromDeg, M.sweepToDeg, sw.1) * Double.pi / 180
      let dir = SIMD3<Float>(Float(-sin(az)), -0.35, Float(-cos(az)))
      sweepNode.simdPosition = .zero
      sweepNode.simdLook(at: dir, up: SIMD3<Float>(0, 1, 0), localFront: SIMD3<Float>(0, 0, -1))
      sweepLight.intensity = CGFloat(Self.lightIntensity(M.sweepLux * level * Self.directScale))
    }

    rig?.simdPosition = SIMD3<Float>(0, Float(M.helmetY + s.lift), 0)

    let lamp = s.lamps
    updateGlow(lamp: lamp, cam: cam, lift: s.lift)
    let q = Int((lamp * 400).rounded())
    if q != lastLamp {
      lastLamp = q
      for m in shell { m.emission.intensity = CGFloat(M.lampStrength * lamp) }
    }
  }
}
