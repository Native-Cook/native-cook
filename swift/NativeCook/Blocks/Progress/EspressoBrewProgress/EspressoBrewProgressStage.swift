import SceneKit
import SwiftUI
import UIKit

/// The SceneKit layer. SceneKit runs the render loop; its delegate asks the controller for this frame's pose and sets
/// it on the render thread, before the frame is drawn.
struct EspressoBrewProgressStage3D: UIViewRepresentable {
  typealias M = EspressoBrewProgressMotion
  let controller: EspressoBrewProgressController
  let size: CGSize
  var onReady: () -> Void
  var onShotReady: (() -> Void)?

  final class Coordinator: NSObject, SCNSceneRendererDelegate {
    let controller: EspressoBrewProgressController
    let stage: EspressoBrewProgressScene3D
    var aspect = 1.0
    var onShotReady: (() -> Void)?
    private var status: Int?
    private let done = UINotificationFeedbackGenerator()

    init(controller: EspressoBrewProgressController) {
      self.controller = controller
      stage = EspressoBrewProgressScene3D(modelURL: Bundle.main.url(forResource: "EspressoBrewProgress-espresso", withExtension: "usdz"),
                                          environmentURL: Bundle.main.url(forResource: "EspressoBrewProgress-studio", withExtension: "hdr"))
      super.init()
    }

    func renderer(_ renderer: SCNSceneRenderer, updateAtTime time: TimeInterval) {
      let f = controller.frame(at: CACurrentMediaTime())
      stage.apply(f.pose, aspect: aspect)
      // a finger's shot is done: one success tap, and the host hears of it
      let s = M.statusAt(f.pose.t)
      defer { status = s }
      guard let prev = status, prev != s, s == 3, !f.filming else { return }
      let report = onShotReady
      DispatchQueue.main.async { [done] in
        done.notificationOccurred(.success)
        report?()
      }
    }
  }

  func makeCoordinator() -> Coordinator { Coordinator(controller: controller) }

  func makeUIView(context: Context) -> SCNView {
    let v = SCNView(frame: CGRect(origin: .zero, size: size),
                    options: [SCNView.Option.preferredRenderingAPI.rawValue: SCNRenderingAPI.metal.rawValue])
    let c = context.coordinator
    c.aspect = Double(size.width / max(1, size.height))
    c.onShotReady = onShotReady
    c.stage.apply(controller.frame(at: CACurrentMediaTime()).pose, aspect: c.aspect)
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
    // compile the shaders and upload the IBL and the textures first, so the film's first frames do not hitch
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
    context.coordinator.onShotReady = onShotReady
  }

  static func dismantleUIView(_ v: SCNView, coordinator: Coordinator) {
    v.isPlaying = false
    v.delegate = nil
  }
}
