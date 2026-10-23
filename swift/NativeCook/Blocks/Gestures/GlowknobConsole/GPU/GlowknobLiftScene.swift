import Metal
import QuartzCore
import SceneKit
import simd
import UIKit

struct GlowknobBox {
  var x: Double
  var y: Double
  var width: Double
  var height: Double
}

// Drawing units, as DeviceArt (housing 1000 × 284).
private let ART_W = 1000.0
private let ART_H = 284.0
// Body thickness, edge chamfer and knob height, in drawing units.
private let BODY_DEPTH = 62.0
private let BEVEL = 7.0
private let KNOB = 22.0
private let FOV = 30.0
// How far the console comes toward the viewer, as a fraction of the camera
// distance (about +12% apparent size), and its presenting tilt: the top edge
// turns toward the viewer.
private let LIFT_Z = 0.11
private let LIFT_Y = 14.0
private let REST_PITCH = 0.14
// Peak intensity of the track's light inside the scene (candela-ish, pt).
private let LAMP = 3.2e4

// A dark studio over a white table: the floor bounce below gives the lower
// chamfer its rim, a soft box above and two strips draw the moving
// highlights across the glass and the metal.
private let STUDIO = GlowknobStudioOptions(
  floor: SIMD3(0.36, 0.355, 0.35),
  sky: SIMD3(0.03, 0.03, 0.035),
  boxes: [
    GlowknobSoftBox(size: SIMD2(16, 5), at: SIMD3(0, 12, 10), power: 5),
    GlowknobSoftBox(size: SIMD2(2.4, 13), at: SIMD3(-14, 3, 9), power: 3.2),
    GlowknobSoftBox(size: SIMD2(2.4, 15), at: SIMD3(13, 2, -7), power: 2.4),
  ]
)

// three.js MeshPhysicalMaterial's direct lighting for one point light
// (BRDF_Lambert + BRDF_GGX with V_GGX_SmithCorrelated, D_GGX, F_Schlick and
// getDistanceAttenuation), added in the fragment stage: SceneKit's own omni
// light has no inverse-square falloff with a cutoff, so the lamp is evaluated
// with three's formulas instead. Positions are in view space (points).
private let lampShader = """
#pragma arguments
float3 lampPos;
float3 lampColor;
float lampCutoff;
float lampMetal;
float lampRough;
float lampUseTexture;
float3 lampBase;
#pragma body
{
  float3 L = lampPos - _surface.position;
  float d = length(L);
  L = L / max(d, 1e-4);
  float3 N = normalize(_surface.normal);
  float3 V = normalize(_surface.view);
  float dotNL = saturate(dot(N, L));
  if (dotNL > 0.0 && d < lampCutoff) {
    float falloff = 1.0 / max(d * d, 0.01);
    float k = saturate(1.0 - pow(d / lampCutoff, 4.0));
    falloff *= k * k;
    float3 base = lampUseTexture > 0.5 ? _surface.diffuse.rgb : lampBase;
    float3 diffuseColor = base * (1.0 - lampMetal);
    float3 specColor = mix(float3(0.04), base, lampMetal);
    float roughness = min(max(lampRough, 0.0525), 1.0);
    float alpha = roughness * roughness;
    float3 H = normalize(L + V);
    float dotNV = saturate(dot(N, V));
    float dotNH = saturate(dot(N, H));
    float dotVH = saturate(dot(V, H));
    float fresnel = exp2((-5.55473 * dotVH - 6.98316) * dotVH);
    float3 F = specColor * (1.0 - fresnel) + fresnel;
    float a2 = alpha * alpha;
    float gv = dotNL * sqrt(a2 + (1.0 - a2) * dotNV * dotNV);
    float gl = dotNV * sqrt(a2 + (1.0 - a2) * dotNL * dotNL);
    float Vis = 0.5 / max(gv + gl, 1e-6);
    float denom = dotNH * dotNH * (a2 - 1.0) + 1.0;
    float D = 0.31830988618 * a2 / (denom * denom);
    float3 irradiance = dotNL * lampColor * falloff;
    _output.color.rgb += irradiance * (diffuseColor * 0.31830988618 + F * (Vis * D));
  }
}
"""

// The glass-coated face emits the 2D artwork as is (it carries its own
// painted light); the clear coat and specular grow with `gloss`. three.js
// scales clearcoat (0.8·gloss) and specularIntensity (gloss) — both terms are
// linear in gloss, so the lit result is blended from the emission alone at
// gloss 0 to the full coat at gloss 1. At rest the face is the 2D drawing
// pixel for pixel.
private let faceShader = """
#pragma arguments
float gloss;
#pragma body
_output.color.rgb = mix(_surface.emission.rgb, _output.color.rgb, gloss);
"""

/// The 3D console (lift-scene.ts makeLiftScene): an extruded beveled capsule
/// with a knob standing proud of the face, lit by a procedural studio and by
/// the track's light as a point lamp, seen by a camera whose z = 0 plane maps
/// 1:1 to points, so the object at rest lands on the 2D drawing.
@MainActor
final class GlowknobLiftScene {
  let scene = SCNScene()
  let cameraNode = SCNNode()
  let console3d = SCNNode()
  let distance: Double
  private let home: SIMD3<Double>
  private let s: Double
  private let w: Double
  private let h: Double
  private let front: Double
  private let bevel: Double
  let faceMat = SCNMaterial()
  private let bodyMat = SCNMaterial()
  private let backMat = SCNMaterial()
  private let knurlMat = SCNMaterial()
  private var lampLocal = SIMD3<Double>(0, 0, 0)
  private var lampOn = false
  let faceSize: CGSize

  init(canvas: CGSize, housing: GlowknobBox, pixelRatio: Double) {
    // World units are points; the z = 0 plane maps 1:1 to the canvas.
    distance = canvas.height / 2 / tan(FOV / 2 * Double.pi / 180)
    let camera = SCNCamera()
    camera.fieldOfView = FOV
    camera.projectionDirection = .vertical
    camera.zNear = distance * 0.2
    camera.zFar = distance * 3
    camera.wantsHDR = false
    cameraNode.camera = camera
    cameraNode.simdPosition = SIMD3<Float>(0, 0, Float(distance))
    scene.rootNode.addChildNode(cameraNode)
    scene.background.contents = UIColor.clear

    s = housing.width / ART_W
    w = housing.width
    h = ART_H * s
    let depth = BODY_DEPTH * s
    bevel = BEVEL * s
    front = depth / 2 + bevel

    // Face textures sized to their largest on-screen footprint (lifted) so
    // they are sampled about 1:1; no mipmaps, as in RN.
    let faceW = min(2048, (w * pixelRatio * 1.2).rounded())
    faceSize = CGSize(width: faceW, height: (faceW * ART_H / ART_W).rounded())

    for m in [faceMat, bodyMat, backMat, knurlMat] {
      m.lightingModel = .physicallyBased
      m.isDoubleSided = false
      for p in [m.diffuse, m.emission] {
        p.minificationFilter = .linear
        p.magnificationFilter = .linear
        p.mipFilter = .none
      }
    }
    faceMat.diffuse.contents = UIColor.black
    faceMat.metalness.contents = 0.0
    faceMat.roughness.contents = 0.45
    faceMat.clearCoat.contents = 0.8
    faceMat.clearCoatRoughness.contents = 0.07
    faceMat.shaderModifiers = [.fragment: faceShader]
    faceMat.setValue(0.0, forKey: "gloss")
    // Anodized aluminium chamfer and walls.
    bodyMat.diffuse.contents = GlowknobRGBA("#55555A").uiColor
    bodyMat.metalness.contents = 1.0
    bodyMat.roughness.contents = 0.3
    // The back is seen after a half turn about the vertical axis: mirror u.
    backMat.metalness.contents = 0.35
    backMat.roughness.contents = 0.4
    backMat.diffuse.contentsTransform = SCNMatrix4Mult(SCNMatrix4MakeScale(-1, 1, 1), SCNMatrix4MakeTranslation(1, 0, 0))
    backMat.diffuse.wrapS = .repeat
    // Brushed rather than polished: the light's warmth spreads across the
    // flank instead of glinting off a few ridges.
    knurlMat.metalness.contents = 0.55
    knurlMat.roughness.contents = 0.7
    knurlMat.diffuse.contentsTransform = SCNMatrix4MakeScale(3, 1, 1)
    knurlMat.diffuse.wrapS = .repeat
    for (m, metal, rough, texture) in [(bodyMat, 1.0, 0.3, false), (knurlMat, 0.55, 0.7, true)] {
      m.shaderModifiers = [.fragment: lampShader]
      m.setValue(metal, forKey: "lampMetal")
      m.setValue(rough, forKey: "lampRough")
      m.setValue(texture ? 1.0 : 0.0, forKey: "lampUseTexture")
      m.setValue(SCNVector3(Self.linear(0x55), Self.linear(0x55), Self.linear(0x5A)), forKey: "lampBase")
      m.setValue(SCNVector3(0, 0, 0), forKey: "lampColor")
      m.setValue(SCNVector3(0, 0, 1e6), forKey: "lampPos")
      m.setValue(w * 0.5, forKey: "lampCutoff")
    }

    let artScale = CGSize(width: faceSize.width, height: faceSize.height)
    backMat.diffuse.contents = glowknobMakeTexture(pixels: artScale, units: CGSize(width: ART_W, height: ART_H)) { c in
      glowknobPaintConsoleBack(c)
    }
    knurlMat.diffuse.contents = glowknobMakeTexture(pixels: CGSize(width: 256, height: 64), units: CGSize(width: 256, height: 64)) {
      glowknobPaintKnurl($0, width: 256, height: 64)
    }

    // Body: ExtrudeGeometry of a capsule w − 2·bevel × h − 2·bevel.
    var body = glowknobExtrudeGeometry(contour: glowknobCapsulePoints(width: w - 2 * bevel, height: h - 2 * bevel, curveSegments: 64),
                               depth: depth, bevelThickness: bevel, bevelSize: bevel, bevelSegments: 6)
    body.translate(0, 0, -depth / 2)
    body.faceUV(width: w, height: h)
    // ExtrudeGeometry puts both lids in group 0 (back lid first, then front)
    // and the walls in group 1. Split the lids: 0 face, 1 walls, 2 back.
    let lids = body.groups[0], walls = body.groups[1]
    let lid = lids.count / 2
    body.groups = [(lids.start, lid, 2), (lids.start + lid, lid, 0), (walls.start, walls.count, 1)]

    // The knob stands proud of the face; its top shows the knob artwork.
    let knobR = 100 * s, knobH = KNOB * s
    var knob = glowknobCylinderGeometry(radius: knobR, height: knobH, radialSegments: 96)
    knob.rotateX(Double.pi / 2)
    knob.translate((GlowknobReferenceGeometry.dial.x - ART_W / 2) * s, (ART_H / 2 - GlowknobReferenceGeometry.dial.y) * s,
                   front + knobH / 2)
    knob.faceUV(width: w, height: h, groups: [1, 2])

    let mats = [faceMat, bodyMat, backMat]
    console3d.addChildNode(SCNNode(geometry: Self.scnGeometry(body, materials: mats)))
    console3d.addChildNode(SCNNode(geometry: Self.scnGeometry(knob, materials: [knurlMat, faceMat, bodyMat])))
    scene.rootNode.addChildNode(console3d)
    home = SIMD3(housing.x + w / 2 - canvas.width / 2, canvas.height / 2 - (housing.y + h / 2), -front)
    console3d.simdPosition = SIMD3<Float>(home)
  }

  /// SceneKit expects sRGB texture images; the shader works in linear.
  private static func linear(_ v: Int) -> Double {
    let c = Double(v) / 255
    return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4)
  }

  func setEnvironment(_ url: URL) {
    scene.lightingEnvironment.contents = url
    scene.lightingEnvironment.intensity = 1
  }

  /// A freshly recorded face, and the light as it was at pick-up.
  func paintFace(_ image: CGImage, glow: GlowknobGlow?) {
    faceMat.emission.contents = image
    lampOn = glow != nil
    let intensity = glow.map { $0.power * LAMP } ?? 0
    let color = glow?.color ?? .zero
    for m in [bodyMat, knurlMat] {
      m.setValue(SCNVector3(color.x * intensity, color.y * intensity, color.z * intensity), forKey: "lampColor")
    }
    if let glow {
      lampLocal = SIMD3((glow.tip - 42 - ART_W / 2) * s,
                        (ART_H / 2 - (GlowknobReferenceGeometry.track.y + GlowknobReferenceGeometry.track.height / 2)) * s,
                        front - bevel * 0.5)
    }
  }

  func setPose(lift: Double, yaw: Double, pitch: Double, tiltX: Double, tiltY: Double) {
    faceMat.setValue(min(1, lift * 1.4), forKey: "gloss")
    console3d.simdPosition = SIMD3<Float>(SIMD3(home.x, home.y + LIFT_Y * lift, home.z + distance * LIFT_Z * lift))
    // three.js Euler XYZ: R = Rx · Ry · Rz.
    let rx = Float(pitch + (REST_PITCH + tiltY * 0.12) * lift)
    let ry = Float(yaw + tiltX * 0.16 * lift)
    console3d.simdOrientation = simd_quatf(angle: rx, axis: SIMD3(1, 0, 0)) * simd_quatf(angle: ry, axis: SIMD3(0, 1, 0))
    updateLamp()
  }

  private func updateLamp() {
    let world = console3d.simdWorldTransform * SIMD4<Float>(SIMD3<Float>(lampLocal), 1)
    let view = simd_inverse(cameraNode.simdWorldTransform) * world
    let p = SCNVector3(view.x, view.y, view.z)
    for m in [bodyMat, knurlMat] { m.setValue(p, forKey: "lampPos") }
  }

  private static func scnGeometry(_ mesh: GlowknobMeshData, materials: [SCNMaterial]) -> SCNGeometry {
    let sources = [
      SCNGeometrySource(vertices: mesh.positions.map { SCNVector3($0.x, $0.y, $0.z) }),
      SCNGeometrySource(normals: mesh.normals.map { SCNVector3($0.x, $0.y, $0.z) }),
      SCNGeometrySource(textureCoordinates: mesh.uvs.map { CGPoint(x: CGFloat($0.x), y: CGFloat($0.y)) }),
    ]
    var elements: [SCNGeometryElement] = []
    var elementMaterials: [SCNMaterial] = []
    for g in mesh.groups {
      let idx: [UInt32] = mesh.indices.map { Array($0[g.start..<(g.start + g.count)]) }
        ?? (g.start..<(g.start + g.count)).map(UInt32.init)
      elements.append(SCNGeometryElement(indices: idx, primitiveType: .triangles))
      elementMaterials.append(materials[g.material])
    }
    let geometry = SCNGeometry(sources: sources, elements: elements)
    geometry.materials = elementMaterials
    return geometry
  }
}

/// Builds the studio environment once per launch (off the main thread).
enum GlowknobStudioEnvironment {
  static func make() async -> URL? {
    await Task.detached(priority: .userInitiated) {
      let width = 512
      let pixels = GlowknobStudio.equirect(STUDIO, width: width)
      let url = FileManager.default.temporaryDirectory.appendingPathComponent("glowknob-studio.hdr")
      do {
        try GlowknobStudio.writeHDR(pixels, width: width, height: width / 2, to: url)
        return url
      } catch {
        return nil
      }
    }.value
  }
}
