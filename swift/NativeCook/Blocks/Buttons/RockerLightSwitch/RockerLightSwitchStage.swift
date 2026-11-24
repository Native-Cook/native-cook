import SceneKit
import SwiftUI
import UIKit

/// The SceneKit view. SceneKit runs the render loop; its delegate asks the controller for this frame's pose and
/// poses the scene, on the render thread, before the frame is drawn.
struct RockerLightSwitchStage3D: UIViewRepresentable {
  let controller: RockerLightSwitchController
  let size: CGSize

  final class Coordinator: NSObject, SCNSceneRendererDelegate {
    let controller: RockerLightSwitchController
    let stage: RockerLightSwitchScene3D
    var size = CGSize(width: 393, height: 600)
    private var clicks: Int?
    private let light = UIImpactFeedbackGenerator(style: .light)

    init(controller: RockerLightSwitchController) {
      self.controller = controller
      stage = RockerLightSwitchScene3D(modelURL: Bundle.main.url(forResource: "RockerLightSwitch-switch", withExtension: "usdz"),
                                       environmentURL: Bundle.main.url(forResource: "RockerLightSwitch-loft", withExtension: "hdr"))
      super.init()
    }

    func renderer(_ renderer: SCNSceneRenderer, updateAtTime time: TimeInterval) {
      let f = controller.frame(at: CACurrentMediaTime())
      stage.apply(f.pose, size: size)
      // the click as the rocker tips past the middle
      if let c = clicks, c != f.clicks { DispatchQueue.main.async { [light] in light.impactOccurred() } }
      clicks = f.clicks
    }
  }

  func makeCoordinator() -> Coordinator { Coordinator(controller: controller) }

  func makeUIView(context: Context) -> SCNView {
    let v = SCNView(frame: CGRect(origin: .zero, size: size),
                    options: [SCNView.Option.preferredRenderingAPI.rawValue: SCNRenderingAPI.metal.rawValue])
    let c = context.coordinator
    c.size = size
    v.scene = c.stage.scene
    v.pointOfView = c.stage.cameraNode
    v.delegate = c
    v.backgroundColor = .clear
    v.isOpaque = false
    v.antialiasingMode = .multisampling4X
    v.rendersContinuously = true
    v.isPlaying = true
    v.isUserInteractionEnabled = false
    v.preferredFramesPerSecond = 120
    c.stage.apply(controller.frame(at: CACurrentMediaTime()).pose, size: size)
    return v
  }

  func updateUIView(_ v: SCNView, context: Context) {
    context.coordinator.size = size
  }

  static func dismantleUIView(_ v: SCNView, coordinator: Coordinator) {
    v.isPlaying = false
    v.delegate = nil
  }
}

/// Over the 3D view: the dusk (a blue veil, deepest away from the lamp, that lifts as the light comes on) and the
/// globe's halo in the room's air, pinned to the globe with the stage's own camera maths. Two gradient fills.
struct RockerLightSwitchLight: View {
  typealias M = RockerLightSwitchMotion
  let pose: RockerLightSwitchPose

  /// The halo reaches this many globe radii.
  private static let halo = 4.2

  var body: some View {
    Canvas { ctx, size in
      let w = Double(size.width), h = Double(size.height)
      let k = pose.light
      let all = Path(CGRect(origin: .zero, size: size))
      // the dusk falls away from the lamp: clear round the globe (at rest), deepest in the far corner
      var d = ctx
      d.opacity = 1 - 0.92 * k
      d.fill(all, with: .color(RockerLightSwitch.color(M.dusk, 0.3)))
      let lamp = M.project(M.cameraAt(0, 0), w, h, M.globe)
      let far = hypot(max(lamp.x, w - lamp.x), max(lamp.y, h - lamp.y))
      let night = { (a: Double) in Color(.sRGB, red: 8 / 255, green: 14 / 255, blue: 34 / 255, opacity: a) }
      d.fill(all, with: .radialGradient(Gradient(stops: [.init(color: night(0), location: 0.08), .init(color: night(0.16), location: 0.4),
                                                         .init(color: night(0.4), location: 0.75), .init(color: night(0.6), location: 1)]),
                                        center: CGPoint(x: lamp.x, y: lamp.y), startRadius: 0, endRadius: far))
      guard k > 0.002 else { return }
      let o = M.project(pose.camera, w, h, M.globe)
      let r = M.globeR * o.ppm * Self.halo
      var g = ctx
      g.opacity = 0.55 * k
      let warm = { (gr: Double, b: Double, a: Double) in Color(.sRGB, red: 1, green: gr / 255, blue: b / 255, opacity: a) }
      g.fill(Path(ellipseIn: CGRect(x: o.x - r, y: o.y - r, width: 2 * r, height: 2 * r)),
             with: .radialGradient(Gradient(stops: [.init(color: warm(214, 150, 0.5), location: 0), .init(color: warm(196, 120, 0.2), location: 0.3),
                                                    .init(color: warm(188, 110, 0.06), location: 0.62), .init(color: warm(188, 110, 0), location: 1)]),
                                   center: CGPoint(x: o.x, y: o.y), startRadius: 0, endRadius: r))
    }
    .allowsHitTesting(false)
  }
}
