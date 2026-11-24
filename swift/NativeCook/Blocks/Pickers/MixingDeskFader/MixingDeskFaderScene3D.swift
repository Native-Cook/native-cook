import Foundation
import SceneKit
import simd

/// What the 3D stage shows in one frame: the pose (faders, knobs, mutes), the camera's lean and the meters' time.
struct MixingDeskFaderPose: Equatable {
  var p: [Double]
  var lean: Double
  var signal: Double
}

/// The 3D stage, drawn with SceneKit: the desk and its studio sweep from the React Native block's mixer.glb (converted
/// once to MixingDeskFader-mixer.usdz with glb2usdz.sh, which keeps glTF space, the Deck's tilt and node names), lit like
/// Filament: the same IBL (the cmgen KTX as MixingDeskFader-studio.hdr), a key and a fill, ACES (legacy) tone mapping in
/// a fragment modifier. Faders, knobs and mutes are the Deck's children, posed in its frame; LEDs and mute lamps are
/// emissive strengths. `apply` is called from the render delegate; nothing plays from the file.
final class MixingDeskFaderScene3D {
  typealias M = MixingDeskFaderMotion

  let scene = SCNScene()
  let cameraNode = SCNNode()
  private(set) var ready = false
  private var faders: [SCNNode?] = []
  private var knobs: [SCNNode?] = []
  private var mutes: [SCNNode?] = []
  private var lamps: [SCNMaterial?] = []
  /// channels x leds, channel-major.
  private var leds: [SCNMaterial?] = []
  /// What each LED and lamp shows now, in 1/48 steps, so a frame writes only what changed.
  private var glow: [Int] = []
  /// Filament's bloom round the LEDs and lamps, as soft additive quads under them (same order as `leds`, then lamps).
  private var halos: [SCNMaterial] = []
  private static let glowSteps = 48.0
  private var aspect = 0.0
  private var last: MixingDeskFaderPose?

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
    addLight(direction: M.keyDirection, lux: M.keyLux, kelvin: 5000)
    addLight(direction: M.fillDirection, lux: M.fillLux, kelvin: 7200)

    guard let modelURL, let model = try? SCNScene(url: modelURL, options: [.checkConsistency: false]) else { return }
    for child in model.rootNode.childNodes { scene.rootNode.addChildNode(child) }
    var seen = Set<ObjectIdentifier>()
    var byName: [String: SCNMaterial] = [:]
    scene.rootNode.enumerateHierarchy { node, _ in
      // nothing plays from the file: every part is set from the film's time
      node.removeAllAnimations()
      node.skinner = nil
      guard let g = node.geometry else { return }
      node.castsShadow = false
      for m in g.materials where seen.insert(ObjectIdentifier(m)).inserted {
        Self.fix(m)
        if let n = m.name { byName[n] = m }
      }
      if g.materials.contains(where: { $0.name == "MixerShadowMat" }) { node.renderingOrder = 5 }
    }
    let root = scene.rootNode
    for c in 0..<M.channels {
      faders.append(root.childNode(withName: "Fader\(c)", recursively: true))
      knobs.append(root.childNode(withName: "Knob\(c)", recursively: true))
      mutes.append(root.childNode(withName: "Mute\(c)", recursively: true))
      lamps.append(byName["MuteLamp\(c)"])
      for i in 0..<M.leds { leds.append(byName["Led\(c)_\(i)"]) }
    }
    glow = Array(repeating: -1, count: M.channels * (M.leds + 1))
    // emissive colours are set once; their strengths (intensity) per frame
    for c in 0..<M.channels {
      if let l = lamps[c] { l.emission.contents = Self.linearColor(M.lampRGB) }
      for i in 0..<M.leds {
        leds[c * M.leds + i]?.emission.contents = Self.linearColor(Self.ledColour(i))
      }
    }
    // the bloom: one soft quad per LED (on the deck) and per lamp (on its button, so it sinks with it)
    if let deck = root.childNode(withName: "Deck", recursively: true) {
      let ledImage = Self.haloImage(inner: SIMD2(4.8 / 16.8, 5.8 / 17.8))
      for c in 0..<M.channels {
        for i in 0..<M.leds {
          guard let n = root.childNode(withName: "Led\(c)_\(i)", recursively: true) else { continue }
          let (lo, hi) = n.boundingBox
          let ctr = SIMD3<Float>(Float(lo.x + hi.x) / 2, Float(hi.y) + 0.0003, Float(lo.z + hi.z) / 2)
          halos.append(addHalo(to: deck, at: n.simdConvertPosition(ctr, to: deck), size: SIMD2(0.0168, 0.0178), image: ledImage))
        }
      }
      let lampImage = Self.haloImage(inner: SIMD2(13.2 / 25.2, 8.6 / 20.6))
      for c in 0..<M.channels {
        guard let mute = mutes[c], let lens = root.childNode(withName: "MuteLens\(c)", recursively: true) else { continue }
        let ctr = lens.simdConvertPosition(SIMD3<Float>(0, 0.0063, 0), to: mute)
        halos.append(addHalo(to: mute, at: ctr, size: SIMD2(0.0252, 0.0206), image: lampImage))
      }
    }
    ready = halos.count == M.channels * (M.leds + 1) && !faders.contains { $0 == nil } && !knobs.contains { $0 == nil } && !mutes.contains { $0 == nil }
      && !lamps.contains { $0 == nil } && !leds.contains { $0 == nil }
  }

  /// A quad lying on the deck (its normal up the parent's y), drawn added over what is under it.
  private func addHalo(to parent: SCNNode, at p: SIMD3<Float>, size: SIMD2<Float>, image: CGImage) -> SCNMaterial {
    let plane = SCNPlane(width: CGFloat(size.x), height: CGFloat(size.y))
    let m = SCNMaterial()
    m.lightingModel = .constant
    m.diffuse.contents = image
    m.blendMode = .add
    m.writesToDepthBuffer = false
    m.shaderModifiers = [.fragment: Self.haloFragment]
    m.setValue(Self.vec(.zero), forKey: "mixerHalo")
    plane.materials = [m]
    let n = SCNNode(geometry: plane)
    n.castsShadow = false
    n.renderingOrder = 30
    n.simdOrientation = simd_quatf(angle: -Float.pi / 2, axis: SIMD3<Float>(1, 0, 0))
    n.simdPosition = p
    parent.addChildNode(n)
    return m
  }

  /// A glow outside a centred rectangle (`inner`, as fractions of the quad) falling off like a narrow gaussian, as
  /// grey levels (the colour comes from the uniform).
  private static func haloImage(inner: SIMD2<Double>) -> CGImage {
    let w = 96, h = 96
    var px = [UInt8](repeating: 255, count: w * h * 4)
    for y in 0..<h {
      for x in 0..<w {
        let u = abs((Double(x) + 0.5) / Double(w) - 0.5), v = abs((Double(y) + 0.5) / Double(h) - 0.5)
        // distance outside the rectangle, in units of the quad's half-margin
        let dx = max(0, u - inner.x / 2) / ((1 - inner.x) / 2), dy = max(0, v - inner.y / 2) / ((1 - inner.y) / 2)
        let d = (dx * dx + dy * dy).squareRoot()
        let g = exp(-d * d * 4) * (1 - min(1, d))
        let q = UInt8(max(0, min(255, g * 255)))
        px[(y * w + x) * 4] = q
        px[(y * w + x) * 4 + 1] = q
        px[(y * w + x) * 4 + 2] = q
      }
    }
    // RGBA: a one-channel image becomes a texture format the simulator's Metal refuses
    let ctx = CGContext(data: &px, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
                        space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
    return ctx.makeImage()!
  }

  /// The bloom's strength next to a light, per unit of its emissive strength (measured on the React Native recording).
  private static let haloGain = 0.03

  private static let haloFragment = """
    #pragma arguments
    float4 mixerHalo;
    #pragma body
    _output.color = float4(mixerHalo.rgb * _output.color.r, 0.0);
    """

  private static func ledColour(_ i: Int) -> SIMD3<Double> {
    i == M.leds - 1 ? M.ledRed : i == M.leds - 2 ? M.ledAmber : M.ledGreen
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
  }

  /// From mixer.glb's JSON: plain baseColorFactors in linear Rec.709 (USD tags them Display P3 linear), metallic and
  /// roughness factors (glTF's defaults are 1). LEDs and lamps share one look each.
  private static let materials: [String: Spec] = [
    "Anodized": Spec(color: SIMD3(0.02315337, 0.02518686, 0.02955684), metal: 0.6, rough: 0.46),
    "Walnut": Spec(color: SIMD3(0.10461649, 0.04231141, 0.01938236), rough: 0.44),
    "PrintInk": Spec(color: SIMD3(0.01161224, 0.01298303, 0.01599629), rough: 0.5),
    "SoftCap": Spec(color: SIMD3(0.02732089, 0.02955684, 0.03433981), rough: 0.62),
    "Index0": Spec(color: SIMD3(1, 0.19461784, 0.09989873), rough: 0.4),
    "SlotDark": Spec(color: SIMD3(0.00477695, 0.00518152, 0.00604883), rough: 0.7),
    "Index1": Spec(color: SIMD3(0.91309863, 0.52711511, 0.06847817), rough: 0.4),
    "Index2": Spec(color: SIMD3(0.07818742, 0.54572445, 0.43415365), rough: 0.4),
    "Index3": Spec(color: SIMD3(0.38132602, 0.25818285, 1), rough: 0.4),
    "KnobRubber": Spec(color: SIMD3(0.01680738, 0.01764195, 0.02121901), rough: 0.55),
    "KnobTop": Spec(color: SIMD3(0.82, 0.82, 0.81), metal: 1, rough: 0.26),
    "PrintWhite": Spec(color: SIMD3(0.88792312, 0.8713671, 0.82278574), rough: 0.4),
    "ButtonRubber": Spec(color: SIMD3(0.02955684, 0.03189603, 0.03688945), rough: 0.6),
    "Feet": Spec(color: SIMD3(0.00749903, 0.00749903, 0.00749903), rough: 0.85),
    "SweepMat": Spec(rough: 0.95),
  ]
  private static let ledGreenBase = SIMD3<Double>(0.01298303, 0.03433981, 0.01938236)
  private static let ledAmberBase = SIMD3<Double>(0.04231141, 0.02842604, 0.01228649)
  private static let ledRedBase = SIMD3<Double>(0.04231141, 0.01370208, 0.01161224)
  private static let lampBase = SIMD3<Double>(0.69387174, 0.73046076, 0.65140563)
  private static let shadowColor = SIMD3<Double>(0.00367651, 0.00334654, 0.00303527)

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

  private static let decalFragment = """
    #pragma arguments
    float4 mixerDecal;
    #pragma body
    float mxA = _output.color.a;
    _output.color = float4(mixerDecal.rgb * mxA, mxA);
    """

  private static func vec(_ c: SIMD3<Double>) -> NSValue {
    NSValue(scnVector4: SCNVector4(Float(c.x), Float(c.y), Float(c.z), 1))
  }

  private static func fix(_ m: SCNMaterial) {
    let name = m.name ?? ""
    if name == "MixerShadowMat" {
      // glTF BLEND fades every term by alpha: unlit, the alpha texture as the colour
      m.lightingModel = .constant
      if let alpha = m.transparent.contents { m.diffuse.contents = alpha }
      m.transparent.contents = nil
      m.emission.contents = nil
      m.writesToDepthBuffer = false
      m.blendMode = .alpha
      m.isDoubleSided = true
      m.shaderModifiers = [.fragment: decalFragment]
      m.setValue(vec(shadowColor), forKey: "mixerDecal")
      return
    }
    m.lightingModel = .physicallyBased
    if name == "BrushedAlu" {
      // glTF's metallicRoughness texture: metal in blue, roughness in green
      m.metalness.textureComponents = .blue
      m.roughness.textureComponents = .green
      m.emission.contents = nil
    } else if name.hasPrefix("Led") {
      let i = Int(name.split(separator: "_").last ?? "0") ?? 0
      m.diffuse.contents = linearColor(i == M.leds - 1 ? ledRedBase : i == M.leds - 2 ? ledAmberBase : ledGreenBase)
      m.metalness.contents = NSNumber(value: 0)
      m.roughness.contents = NSNumber(value: 0.28)
      m.emission.intensity = 0.0005
    } else if name.hasPrefix("MuteLamp") {
      m.diffuse.contents = linearColor(lampBase)
      m.metalness.contents = NSNumber(value: 0)
      m.roughness.contents = NSNumber(value: 0.3)
      m.emission.intensity = 0.0005
    } else if let s = materials[name] {
      if let c = s.color { m.diffuse.contents = linearColor(c) }
      m.metalness.contents = NSNumber(value: s.metal)
      m.roughness.contents = NSNumber(value: s.rough)
      m.emission.contents = nil
    }
    m.shaderModifiers = [.fragment: toneMap]
  }

  // MARK: per frame

  func apply(_ pose: MixingDeskFaderPose, aspect a: Double) {
    guard ready else { return }
    if a != aspect, a > 0 {
      aspect = a
      cameraNode.camera?.fieldOfView = CGFloat(2 * atan(M.tanHalfFovY(a)) * 180 / Double.pi)
      last = nil
    }
    // a still scene writes nothing
    if pose == last { return }
    last = pose

    cameraNode.simdPosition = SIMD3<Float>(M.eye(pose.lean))
    cameraNode.simdLook(at: SIMD3<Float>(M.target), up: SIMD3<Float>(0, 1, 0), localFront: SIMD3<Float>(0, 0, -1))

    let p = pose.p
    for c in 0..<M.channels {
      let b = M.faderBase[c]
      faders[c]?.simdPosition = SIMD3<Float>(Float(b.x), Float(b.y), Float(b.z - p[c] * M.travel))
      knobs[c]?.simdOrientation = simd_quatf(angle: Float(M.knobRad(p[4 + c])), axis: SIMD3<Float>(0, 1, 0))
      knobs[c]?.simdPosition = SIMD3<Float>(M.knobPos[c])
      let mute = p[8 + c]
      let mp = M.mutePos[c]
      mutes[c]?.simdPosition = SIMD3<Float>(Float(mp.x), Float(mp.y - mute * M.muteTravel), Float(mp.z))

      let lampLevel = Int(((1 - mute) * Self.glowSteps).rounded())
      let li = M.channels * M.leds + c
      if glow[li] != lampLevel, let lamp = lamps[c] {
        glow[li] = lampLevel
        let e = M.lampOn * Double(lampLevel) / Self.glowSteps
        lamp.emission.intensity = CGFloat(max(0.0005, e))
        halos[M.channels * M.leds + c].setValue(Self.vec(M.lampRGB * e * Self.haloGain), forKey: "mixerHalo")
      }
      let level = M.meterAt(c, pose.signal, fader: p[c], knob: p[4 + c], mute: mute)
      for i in 0..<M.leds {
        let idx = c * M.leds + i
        let q = Int((M.ledAt(level, i) * Self.glowSteps).rounded())
        if glow[idx] == q { continue }
        glow[idx] = q
        let e = M.ledOn * Double(q) / Self.glowSteps
        leds[idx]?.emission.intensity = CGFloat(max(0.0005, e))
        halos[idx].setValue(Self.vec(Self.ledColour(i) * e * Self.haloGain), forKey: "mixerHalo")
      }
    }
  }
}
