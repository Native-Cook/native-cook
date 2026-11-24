import SceneKit
import SwiftUI
import UIKit

/// The SceneKit layer. SceneKit runs the render loop; its delegate asks the controller for this frame's time and poses
/// the carton on the render thread, before the frame is drawn.
struct ParcelPackProgressStage3D: UIViewRepresentable {
  typealias M = ParcelPackProgressMotion
  let controller: ParcelPackProgressController
  let size: CGSize
  var onReady: () -> Void

  final class Coordinator: NSObject, SCNSceneRendererDelegate {
    let controller: ParcelPackProgressController
    let stage: ParcelPackProgressScene3D
    var aspect = 1.0
    private var lastT: Double?
    private let tick = UIImpactFeedbackGenerator(style: .light)
    private let thud = UIImpactFeedbackGenerator(style: .soft)

    init(controller: ParcelPackProgressController) {
      self.controller = controller
      stage = ParcelPackProgressScene3D(modelURL: Bundle.main.url(forResource: "ParcelPackProgress-parcel", withExtension: "usdz"),
                                        environmentURL: Bundle.main.url(forResource: "ParcelPackProgress-studio", withExtension: "hdr"))
      super.init()
    }

    func renderer(_ renderer: SCNSceneRenderer, updateAtTime time: TimeInterval) {
      let f = controller.frame(at: CACurrentMediaTime())
      stage.apply(t: f.t, orbit: f.orbit, aspect: aspect)
      defer { lastT = f.t }
      guard !controller.reduced, let prev = lastT, f.t > prev, f.t - prev <= 1 else { return }
      // a light tick as each step is done, a soft one as the label lands
      if prev < M.labelLand, f.t >= M.labelLand {
        DispatchQueue.main.async { [thud] in thud.impactOccurred() }
      } else if M.done.contains(where: { prev < $0 && f.t >= $0 }) {
        DispatchQueue.main.async { [tick] in tick.impactOccurred() }
      }
    }
  }

  func makeCoordinator() -> Coordinator { Coordinator(controller: controller) }

  func makeUIView(context: Context) -> SCNView {
    let v = SCNView(frame: CGRect(origin: .zero, size: size),
                    options: [SCNView.Option.preferredRenderingAPI.rawValue: SCNRenderingAPI.metal.rawValue])
    let c = context.coordinator
    c.aspect = Double(size.width / max(1, size.height))
    c.stage.apply(t: controller.frame(at: CACurrentMediaTime()).t, orbit: 0, aspect: c.aspect)
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
    // compile the shaders and upload the IBL and textures first, so the film's first frames do not hitch
    let controller = controller, onReady = onReady
    var fired = false
    let go = {
      guard !fired else { return }
      fired = true
      controller.markReady()
      onReady()
    }
    v.prepare([c.stage.scene]) { _ in DispatchQueue.main.async(execute: go) }
    // nothing may hold the film back for long
    DispatchQueue.main.asyncAfter(deadline: .now() + 3, execute: go)
    return v
  }

  func updateUIView(_ v: SCNView, context: Context) {
    context.coordinator.aspect = Double(size.width / max(1, size.height))
  }

  static func dismantleUIView(_ v: SCNView, coordinator: Coordinator) {
    v.isPlaying = false
    v.delegate = nil
  }
}
