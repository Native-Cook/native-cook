import SceneKit
import SwiftUI
import UIKit

/// The SceneKit layer. SceneKit runs the render loop; its delegate asks the controller for this frame's pose and sets
/// it on the render thread, before the frame is drawn.
struct TwinBellAlarmStage3D: UIViewRepresentable {
  let controller: TwinBellAlarmController
  let size: CGSize
  var onReady: () -> Void

  final class Coordinator: NSObject, SCNSceneRendererDelegate {
    let controller: TwinBellAlarmController
    let stage: TwinBellAlarmScene3D
    var aspect = 1.0
    private var beat = -1
    private let buzz = UIImpactFeedbackGenerator(style: .medium)
    private let tick = UIImpactFeedbackGenerator(style: .light)

    init(controller: TwinBellAlarmController) {
      self.controller = controller
      stage = TwinBellAlarmScene3D(modelURL: Bundle.main.url(forResource: "TwinBellAlarm-alarm", withExtension: "usdz"),
                                   environmentURL: Bundle.main.url(forResource: "TwinBellAlarm-studio", withExtension: "hdr"))
      super.init()
    }

    func renderer(_ renderer: SCNSceneRenderer, updateAtTime time: TimeInterval) {
      let f = controller.frame(at: CACurrentMediaTime())
      stage.apply(f.pose, aspect: aspect)
      // the ring's haptic: a firm tap as it starts, then a light one every 0.3 s
      let cur = f.pose.ring < 0 ? -1 : Int(f.pose.ring / 0.3)
      if cur != beat {
        beat = cur
        if cur >= 0, !controller.reduced {
          let g = cur == 0 ? buzz : tick
          DispatchQueue.main.async { g.impactOccurred() }
        }
      }
    }
  }

  func makeCoordinator() -> Coordinator { Coordinator(controller: controller) }

  func makeUIView(context: Context) -> SCNView {
    let v = SCNView(frame: CGRect(origin: .zero, size: size),
                    options: [SCNView.Option.preferredRenderingAPI.rawValue: SCNRenderingAPI.metal.rawValue])
    let c = context.coordinator
    c.aspect = Double(size.width / max(1, size.height))
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
    // compile the shaders and upload the IBL first, so the film's first frames do not hitch
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
