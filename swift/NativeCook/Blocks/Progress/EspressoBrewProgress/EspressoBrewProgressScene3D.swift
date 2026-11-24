import Foundation
import SceneKit
import simd

/// What the 3D stage shows in one frame: the film's time, the liquid's dose (0 single .. 1 double) and a finger's orbit.
struct EspressoBrewProgressPose: Equatable {
  var t: Double
  var dose: Double
  var orbit: Double
}

/// The 3D stage, drawn with SceneKit: the machine, the cup and the studio sweep from the React Native block's
/// espresso.glb (converted once to EspressoBrewProgress-espresso.usdz with glb2usdz.sh, which keeps glTF space and node
/// names), lit like Filament: the same IBL (the cmgen KTX as EspressoBrewProgress-studio.hdr), a key and a fill, ACES
/// (legacy) tone mapping in a fragment modifier. Every moving part is posed from the film's time; `apply` is called from
/// the render delegate. Nothing plays from the file.
final class EspressoBrewProgressScene3D {
  typealias M = EspressoBrewProgressMotion

  let scene = SCNScene()
  let cameraNode = SCNNode()
  private(set) var ready = false
  private var needle: SCNNode?
  private var cup: SCNNode?
  private var coffee: SCNNode?
  private var crema: SCNNode?
  private var streams: [SCNNode?] = []
  private var light: SCNMaterial?
  private var aspect = 0.0
  private var last: EspressoBrewProgressPose?
  private var lightShown = -1.0

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
      if g.materials.contains(where: { $0.name == "MachineShadowMat" }) { node.renderingOrder = 5 }
      // the glass is drawn after everything it holds
      if g.materials.contains(where: { $0.name == "CupGlass" }) { node.renderingOrder = 20 }
    }
    let root = scene.rootNode
    var glassNodes: [SCNNode] = []
    root.enumerateHierarchy { n, _ in
      if n.geometry?.materials.contains(where: { $0.name == "CupGlass" }) == true { glassNodes.append(n) }
    }
    for g in glassNodes {
      // the darkening pass, drawn just before the glass itself
      let shade = SCNNode(geometry: g.geometry?.copy() as? SCNGeometry)
      shade.geometry?.materials = [Self.glassShade()]
      shade.renderingOrder = 19
      shade.castsShadow = false
      shade.simdTransform = g.simdTransform
      g.parent?.addChildNode(shade)
    }
    needle = root.childNode(withName: "Needle", recursively: true)
    cup = root.childNode(withName: "Cup", recursively: true)
    coffee = cup?.childNode(withName: "Coffee", recursively: true)
    crema = cup?.childNode(withName: "Crema", recursively: true)
    streams = ["Stream0", "Stream1"].map { root.childNode(withName: $0, recursively: true) }
    if let lightNode = root.childNode(withName: "GroupLight", recursively: true) {
      var found: SCNMaterial?
      lightNode.enumerateHierarchy { n, stop in
        if let m = n.geometry?.materials.first(where: { $0.name == "GroupLightMat" }) {
          found = m
          stop.pointee = true
        }
      }
      light = found
    }
    ready = needle != nil && cup != nil && coffee != nil && crema != nil && !streams.contains { $0 == nil } && light != nil
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
    var metal: Double = 0
    var rough: Double = 1
    var emissive: SIMD3<Double>? = nil
    /// A factor over a texture (USD keeps the texture only).
    var tint: SIMD3<Double>? = nil
  }

  /// The sweep's baked texture reads a little cooler through USD than through gltfio (measured on the floor and the
  /// back of the sweep against the React Native recording).
  private static let sweepWarmth = SIMD3<Double>(1.04, 1.0, 0.98)

  /// From espresso.glb's JSON: plain baseColorFactors in linear Rec.709 (USD tags them Display P3 linear), metallic and
  /// roughness factors (glTF's defaults are 1).
  private static let materials: [String: Spec] = [
    "SlateEnamel": Spec(color: SIMD3(0.04817183, 0.09084171, 0.13286832), rough: 0.34),
    "CoffeeBody": Spec(color: SIMD3(0.00604883, 0.00212469, 0.00091058), rough: 0.32),
    "CremaTop": Spec(rough: 0.55),
    "CremaSide": Spec(color: SIMD3(0.43415365, 0.20155625, 0.05951124), rough: 0.5),
    "Chrome": Spec(color: SIMD3(0.92, 0.91, 0.89), metal: 1, rough: 0.1),
    "Rubber": Spec(color: SIMD3(0.01298303, 0.01228649, 0.01161224), rough: 0.85),
    "BrushedSteel": Spec(color: SIMD3(0.8, 0.8, 0.79), metal: 1, rough: 0.3),
    "GaugeBand": Spec(color: SIMD3(0.69387174, 0.25015828, 0.04373503), rough: 0.45),
    "GaugeFace": Spec(color: SIMD3(0.89626938, 0.86315721, 0.79129791), rough: 0.4),
    "GaugeInk": Spec(color: SIMD3(0.01599629, 0.01938236, 0.02315337), rough: 0.5),
    "GroupLightMat": Spec(color: SIMD3(0.81484658, 0.76815116, 0.68668532), rough: 0.3, emissive: SIMD3(0.002, 0.0014, 0.0008)),
    "NeedleRed": Spec(color: SIMD3(0.64447969, 0.05951124, 0.02518686), rough: 0.35),
    "Walnut": Spec(color: SIMD3(0.10224173, 0.03954624, 0.01764195), rough: 0.42),
    "ReadyLamp": Spec(color: SIMD3(0.90466118, 0.58407843, 0.18782078), rough: 0.3, emissive: SIMD3(1, 0.46778381, 0.1070231)),
    "SlateDeep": Spec(color: SIMD3(0.02315337, 0.04373503, 0.06480327), rough: 0.42),
    "CoffeeStream": Spec(color: SIMD3(0.04231141, 0.01096009, 0.00303527), rough: 0.2),
    "GrateDark": Spec(color: SIMD3(0.01298303, 0.01599629, 0.01938236), rough: 0.6),
    "SweepMat": Spec(rough: 0.95, tint: sweepWarmth),
  ]
  private static let glass = (color: SIMD3<Double>(0.8, 0.84, 0.86), alpha: 0.09, rough: 0.05)
  private static let shadowColor = SIMD3<Double>(0.04373503, 0.03310477, 0.02121901)

  static func linearColor(_ c: SIMD3<Double>) -> CGColor {
    let space = CGColorSpace(name: CGColorSpace.extendedLinearSRGB)!
    return CGColor(colorSpace: space, components: [CGFloat(c.x), CGFloat(c.y), CGFloat(c.z), 1])!
  }

  /// Filament's ACES legacy tone mapping (Stephen Hill's fit with a 1/0.6 boost).
  static let toneMapBody = """
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


  /// glTF BLEND as gltfio draws it (Filament's TRANSPARENT blending): the diffuse is faded by alpha and the
  /// reflections are kept (this pass, added), and what is behind is dimmed by 1 - alpha (`glassShade`, multiplied).
  /// SceneKit's own alpha blending does not keep the view's alpha, which would let the backdrop show through the cup.
  private static let tintSurface = """
    #pragma arguments
    float4 espressoTint;
    #pragma body
    _surface.diffuse.rgb *= espressoTint.rgb;
    """

  private static let glassSurface = """
    _surface.diffuse.rgb *= 0.09;
    """
  private static let glassShadeFragment = """
    _output.color = float4(0.91, 0.91, 0.91, 1.0);
    """

  private static func glassShade() -> SCNMaterial {
    let m = SCNMaterial()
    m.name = "CupGlassShade"
    m.lightingModel = .constant
    m.blendMode = .multiply
    m.writesToDepthBuffer = false
    m.shaderModifiers = [.fragment: glassShadeFragment]
    return m
  }

  private static let decalFragment = """
    #pragma arguments
    float4 espressoDecal;
    #pragma body
    float esA = _output.color.a;
    _output.color = float4(espressoDecal.rgb * esA, esA);
    """

  private static func vec(_ c: SIMD3<Double>) -> NSValue {
    NSValue(scnVector4: SCNVector4(Float(c.x), Float(c.y), Float(c.z), 1))
  }

  private static func fix(_ m: SCNMaterial) {
    let name = m.name ?? ""
    if name == "MachineShadowMat" {
      // glTF BLEND fades every term by alpha: unlit, the alpha texture as the colour
      m.lightingModel = .constant
      if let alpha = m.transparent.contents { m.diffuse.contents = alpha }
      m.transparent.contents = nil
      m.emission.contents = nil
      m.writesToDepthBuffer = false
      m.blendMode = .alpha
      m.isDoubleSided = true
      m.shaderModifiers = [.fragment: decalFragment]
      m.setValue(vec(shadowColor), forKey: "espressoDecal")
      return
    }
    m.lightingModel = .physicallyBased
    if name == "CupGlass" {
      m.diffuse.contents = linearColor(glass.color)
      m.metalness.contents = NSNumber(value: 0)
      m.roughness.contents = NSNumber(value: glass.rough)
      m.transparent.contents = nil
      m.emission.contents = nil
      m.blendMode = .add
      m.writesToDepthBuffer = false
      m.shaderModifiers = [.surface: glassSurface, .fragment: toneMapBody]
      return
    }
    if let s = materials[name] {
      if let c = s.color { m.diffuse.contents = linearColor(c) }
      m.metalness.contents = NSNumber(value: s.metal)
      m.roughness.contents = NSNumber(value: s.rough)
      if name == "GroupLightMat" {
        // the light band: its colour once, its strength per frame (SceneKit adds emission x intensity unclamped)
        m.emission.contents = linearColor(M.groupLightRGB)
        m.emission.intensity = 0.002
      } else if let e = s.emissive {
        m.emission.contents = linearColor(e)
        m.emission.intensity = 1
      } else {
        m.emission.contents = nil
      }
    }
    var mods: [SCNShaderModifierEntryPoint: String] = [.fragment: toneMapBody]
    if let t = materials[name]?.tint {
      mods[.surface] = tintSurface
      m.setValue(vec(t), forKey: "espressoTint")
    }
    m.shaderModifiers = mods
  }

  // MARK: per frame

  func apply(_ p: EspressoBrewProgressPose, aspect a: Double) {
    guard ready else { return }
    if a != aspect, a > 0 {
      aspect = a
      cameraNode.camera?.fieldOfView = CGFloat(2 * atan(M.tanHalfFovY(a)) * 180 / Double.pi)
      last = nil
    }
    // a still scene writes nothing
    if p == last { return }
    last = p
    let t = p.t

    let cam = M.cameraAt(M.camAt(t), orbit: p.orbit)
    cameraNode.simdPosition = SIMD3<Float>(cam.eye)
    cameraNode.simdLook(at: SIMD3<Float>(cam.target), up: SIMD3<Float>(0, 1, 0), localFront: SIMD3<Float>(0, 0, -1))

    if let needle {
      needle.simdOrientation = simd_quatf(angle: Float(M.needleDeg(M.barAt(t)) * Double.pi / 180), axis: SIMD3<Float>(0, 0, 1))
      needle.simdPosition = SIMD3<Float>(M.needlePivot)
    }
    if let light {
      let k = M.lightAt(t)
      if abs(k - lightShown) > 0.004 || (k == 0 && lightShown != 0) {
        lightShown = k
        let e = M.groupLightOn * k
        // the colour is set once at load; the glb's faint resting glow stays when the light is off
        light.emission.intensity = CGFloat(max(0.002, e))
      }
    }

    // the cup and what is in it
    let slide = M.cupAt(t)
    cup?.simdPosition = SIMD3<Float>(Float(M.cupRest.x + slide.x), Float(M.cupRest.y + slide.lift), Float(M.cupRest.z))
    let liquid = M.liquid(t, dose: p.dose)
    if let coffee {
      if liquid.body < M.minH {
        coffee.isHidden = true
      } else {
        coffee.isHidden = false
        coffee.simdScale = SIMD3<Float>(1, Float(liquid.body / M.coffeeRef), 1)
        coffee.simdPosition = SIMD3<Float>(0, Float(M.coffeeBase), 0)
      }
    }
    if let crema {
      if liquid.crema < M.minH {
        crema.isHidden = true
      } else {
        crema.isHidden = false
        crema.simdOrientation = simd_quatf(angle: Float(M.swirlAt(t)), axis: SIMD3<Float>(0, 1, 0))
        crema.simdScale = SIMD3<Float>(1, Float(liquid.crema / M.cremaRef), 1)
        crema.simdPosition = SIMD3<Float>(0, Float(M.coffeeBase + liquid.body), 0)
      }
    }
    // the streams run from the spout tips to the surface; only while the cup stands under them
    let surface = M.cupRest.y + slide.lift + M.coffeeBase + liquid.total
    let gap = M.spoutTipY - surface
    for (i, s) in streams.enumerated() {
      guard let s else { continue }
      let st = M.streamAt(t, i)
      let top = M.spoutTipY - st.tail * gap
      let len = (st.head - st.tail) * gap
      if len < M.minH || st.width <= 0 || slide.x > 0.001 {
        s.isHidden = true
        continue
      }
      s.isHidden = false
      let tip = M.streamTips[i]
      s.simdScale = SIMD3<Float>(Float(st.width), Float(len / M.streamRef), Float(st.width))
      s.simdPosition = SIMD3<Float>(Float(tip.x), Float(top), Float(tip.z))
    }
  }
}
