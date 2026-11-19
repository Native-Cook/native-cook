import SceneKit
import SwiftUI
import UIKit

/// The SceneKit view: see-through over the SwiftUI floor. SceneKit runs the render loop; its delegate asks the
/// controller for this frame's film time and turn and poses the scene, on the render thread, before the frame is drawn.
struct SpinToLogoSplashStage3D: UIViewRepresentable {
  let controller: SpinToLogoSplashController

  final class Coordinator: NSObject, SCNSceneRendererDelegate {
    let controller: SpinToLogoSplashController
    let stage: SpinToLogoSplashScene3D
    private var lastDetent: Int?
    private var lastUnlock = -1.0
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let note = UINotificationFeedbackGenerator()

    init(controller: SpinToLogoSplashController) {
      self.controller = controller
      let model = Bundle.main.url(forResource: "SpinToLogoSplash-arca", withExtension: "usdz")
      let env = Bundle.main.url(forResource: "SpinToLogoSplash-studio", withExtension: "hdr")
      stage = SpinToLogoSplashScene3D(modelURL: model, environmentURL: env)
      super.init()
    }

    func renderer(_ renderer: SCNSceneRenderer, updateAtTime time: TimeInterval) {
      let now = CACurrentMediaTime()
      let f = controller.frame(at: now)
      stage.apply(t: f.t, yaw: f.yaw)
      haptics(f, now: now)
    }

    /// A light detent every 45 deg while a finger turns the sculpture (never during the film); success once a finger
    /// (or Skip) completes the turn.
    private func haptics(_ f: SpinToLogoSplashController.Frame, now: Double) {
      guard !controller.reduced else { return }
      var fire: [() -> Void] = []
      let detent = Int(floor(f.yaw / 45))
      if let d = lastDetent, d != detent, controller.isTurning(now) { fire.append { [light] in light.impactOccurred() } }
      lastDetent = detent
      let u = controller.unlockTime
      if u != lastUnlock {
        if u > 0 { fire.append { [note] in note.notificationOccurred(.success) } }
        lastUnlock = u
      }
      if !fire.isEmpty { DispatchQueue.main.async { fire.forEach { $0() } } }
    }
  }

  func makeCoordinator() -> Coordinator { Coordinator(controller: controller) }

  func makeUIView(context: Context) -> SCNView {
    let v = SCNView(frame: .zero, options: [SCNView.Option.preferredRenderingAPI.rawValue: SCNRenderingAPI.metal.rawValue])
    v.scene = context.coordinator.stage.scene
    v.pointOfView = context.coordinator.stage.cameraNode
    v.delegate = context.coordinator
    v.backgroundColor = .clear
    v.isOpaque = false
    v.antialiasingMode = .multisampling4X
    v.rendersContinuously = true
    v.isPlaying = true
    v.isUserInteractionEnabled = false
    v.preferredFramesPerSecond = 120
    let f = controller.frame(at: CACurrentMediaTime())
    context.coordinator.stage.apply(t: f.t, yaw: f.yaw)
    return v
  }

  func updateUIView(_ v: SCNView, context: Context) {}

  static func dismantleUIView(_ v: SCNView, coordinator: Coordinator) {
    v.isPlaying = false
    v.delegate = nil
  }
}
