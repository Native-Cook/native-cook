import SceneKit
import SwiftUI
import UIKit

/// The SceneKit layer: see-through over the whole chat. SceneKit runs the render loop; its delegate asks the
/// controller for this frame's time and geometry and poses the sheet, on the render thread, before the frame is
/// drawn.
struct PaperPlaneSendStage3D: UIViewRepresentable {
  let controller: PaperPlaneSendController
  let geo: PaperPlaneSendMotion.Geo

  final class Coordinator: NSObject, SCNSceneRendererDelegate {
    typealias M = PaperPlaneSendMotion
    let controller: PaperPlaneSendController
    let stage: PaperPlaneSendScene3D
    private var lastT: Double?
    private var sends: Int?
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let soft = UIImpactFeedbackGenerator(style: .soft)

    init(controller: PaperPlaneSendController) {
      self.controller = controller
      stage = PaperPlaneSendScene3D(modelURL: Bundle.main.url(forResource: "PaperPlaneSend-plane", withExtension: "usdz"),
                                    environmentURL: Bundle.main.url(forResource: "PaperPlaneSend-loft", withExtension: "hdr"))
      super.init()
    }

    func renderer(_ renderer: SCNSceneRenderer, updateAtTime time: TimeInterval) {
      let f = controller.frame(at: CACurrentMediaTime())
      stage.apply(t: f.t, geo: f.geo)
      // haptics: the send (the film's finger or a real one), and the message landing
      guard !controller.reduced else { return }
      var tapped = false, landed = false
      if let prev = lastT {
        tapped = prev < M.tap && f.t >= M.tap && f.t < M.lift
        landed = prev < M.pop && f.t >= M.pop
      }
      if let s = sends, s != f.sends { tapped = true }
      lastT = f.t
      sends = f.sends
      if tapped { DispatchQueue.main.async { [light] in light.impactOccurred() } }
      if landed { DispatchQueue.main.async { [soft] in soft.impactOccurred() } }
    }
  }

  func makeCoordinator() -> Coordinator { Coordinator(controller: controller) }

  func makeUIView(context: Context) -> SCNView {
    let v = SCNView(frame: CGRect(x: 0, y: 0, width: geo.w, height: geo.h),
                    options: [SCNView.Option.preferredRenderingAPI.rawValue: SCNRenderingAPI.metal.rawValue])
    let c = context.coordinator
    controller.setGeometry(geo)
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
    // the layer is transparent until the hand-off: have SceneKit compile its shaders and upload the IBL now, so the
    // first fold does not start with a hitch
    v.prepare([c.stage.scene], completionHandler: nil)
    return v
  }

  func updateUIView(_ v: SCNView, context: Context) {
    controller.setGeometry(geo)
  }

  static func dismantleUIView(_ v: SCNView, coordinator: Coordinator) {
    v.isPlaying = false
    v.delegate = nil
  }
}
