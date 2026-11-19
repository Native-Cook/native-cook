import SceneKit
import SwiftUI
import UIKit

/// The SceneKit view: see-through over the paper and the contact shadows. SceneKit runs the render loop; its
/// delegate asks the controller for this frame's values and poses the scene, on the render thread, before the frame is
/// drawn. The film's clock starts once the record has been drawn for three frames (React Native's READY_FRAMES).
struct BoomboxNowPlayingStage3D: UIViewRepresentable {
  let controller: BoomboxNowPlayingController
  let size: CGSize

  final class Coordinator: NSObject, SCNSceneRendererDelegate {
    let controller: BoomboxNowPlayingController
    let stage: BoomboxNowPlayingScene3D
    private var drawn = 0

    init(controller: BoomboxNowPlayingController) {
      self.controller = controller
      let model = Bundle.main.url(forResource: "BoomboxNowPlaying-vinyl", withExtension: "usdz")
      let env = Bundle.main.url(forResource: "BoomboxNowPlaying-studio", withExtension: "hdr")
      stage = BoomboxNowPlayingScene3D(modelURL: model, environmentURL: env)
      super.init()
    }

    func renderer(_ renderer: SCNSceneRenderer, updateAtTime time: TimeInterval) {
      let now = CACurrentMediaTime()
      let f = controller.frame(at: now)
      stage.apply(stage: f.stage, ret: f.ret, platter: f.platter)
    }

    func renderer(_ renderer: SCNSceneRenderer, didRenderScene scene: SCNScene, atTime time: TimeInterval) {
      guard stage.ready, drawn < 3 else { return }
      drawn += 1
      if drawn == 3 { controller.markReady(CACurrentMediaTime()) }
    }
  }

  func makeCoordinator() -> Coordinator { Coordinator(controller: controller) }

  func makeUIView(context: Context) -> SCNView {
    let v = SCNView(frame: CGRect(origin: .zero, size: size),
                    options: [SCNView.Option.preferredRenderingAPI.rawValue: SCNRenderingAPI.metal.rawValue])
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
    context.coordinator.stage.setSize(size)
    let f = controller.frame(at: CACurrentMediaTime())
    context.coordinator.stage.apply(stage: f.stage, ret: f.ret, platter: f.platter)
    return v
  }

  func updateUIView(_ v: SCNView, context: Context) {
    context.coordinator.stage.setSize(size)
  }

  static func dismantleUIView(_ v: SCNView, coordinator: Coordinator) {
    v.isPlaying = false
    v.delegate = nil
  }
}

/// The soft contact shadows of the floating record and sleeve on the paper (VinylShadows.tsx): each a radial falloff
/// stretched to its footprint as the stage's camera projects it, so it slides, narrows and swings with the turn.
struct BoomboxNowPlayingShadows: View {
  typealias M = BoomboxNowPlayingMotion
  let controller: BoomboxNowPlayingController

  var body: some View {
    TimelineView(.animation) { _ in
      // the frame is read here, outside the Canvas: a Canvas whose inputs do not change is not redrawn
      let f = controller.frame(at: CACurrentMediaTime())
      let sc = M.sceneAt(f.stage, f.ret)
      Canvas { ctx, size in
        let shade = { (a: Double) in Color(.sRGB, red: 58 / 255, green: 44 / 255, blue: 30 / 255, opacity: a) }
        let gradient = Gradient(stops: [.init(color: shade(1), location: 0), .init(color: shade(0.55), location: 0.45),
                                        .init(color: shade(0), location: 1)])
        for k in [1, 0] {
          guard let s = M.shadowAt(sc, k, Double(size.width), Double(size.height)) else { continue }
          var g = ctx
          g.opacity = s.alpha
          g.translateBy(x: s.x, y: s.y)
          g.rotate(by: .radians(s.angle))
          g.scaleBy(x: s.rw, y: s.rd)
          g.fill(Path(ellipseIn: CGRect(x: -1, y: -1, width: 2, height: 2)),
                 with: .radialGradient(gradient, center: .zero, startRadius: 0, endRadius: 1))
        }
      }
    }
    .allowsHitTesting(false)
  }
}
