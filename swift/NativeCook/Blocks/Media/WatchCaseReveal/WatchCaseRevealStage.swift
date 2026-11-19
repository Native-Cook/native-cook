import SceneKit
import SwiftUI
import UIKit

/// The SceneKit view: see-through over the SwiftUI backdrop. SceneKit runs the render loop; its delegate asks the
/// controller for this frame's pose and poses the scene, on the render thread, before the frame is drawn.
struct WatchCaseRevealStage3D: UIViewRepresentable {
  let controller: WatchCaseRevealController
  let size: CGSize

  final class Coordinator: NSObject, SCNSceneRendererDelegate {
    let controller: WatchCaseRevealController
    let stage: WatchCaseRevealScene3D
    private var lastOpen: Double?
    private var lastDetent: Int?
    private var lastReserved = false
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let soft = UIImpactFeedbackGenerator(style: .soft)
    private let tick = UISelectionFeedbackGenerator()
    private let note = UINotificationFeedbackGenerator()

    init(controller: WatchCaseRevealController) {
      self.controller = controller
      let model = Bundle.main.url(forResource: "WatchCaseReveal-watch", withExtension: "usdz")
      let env = Bundle.main.url(forResource: "WatchCaseReveal-studio", withExtension: "hdr")
      stage = WatchCaseRevealScene3D(modelURL: model, environmentURL: env)
      super.init()
    }

    func renderer(_ renderer: SCNSceneRenderer, updateAtTime time: TimeInterval) {
      let f = controller.frame(at: CACurrentMediaTime())
      stage.apply(f.pose)
      haptics(f)
    }

    /// The clasp, the lid at rest, the pillow at the top, the lid landing shut; a detent every 12 deg while a finger
    /// turns the watch; the film's press of the reserve button.
    private func haptics(_ f: WatchCaseRevealController.Frame) {
      guard !controller.reduced else { return }
      let o = f.pose.open
      var fire: [() -> Void] = []
      if let prev = lastOpen {
        if prev < 0.015 && o >= 0.015 { fire.append { [light] in light.impactOccurred() } }
        if prev < 0.97 && o >= 0.97 { fire.append { [soft] in soft.impactOccurred() } }
        if prev < 1.95 && o >= 1.95 { fire.append { [light] in light.impactOccurred() } }
        if prev > 0.02 && o <= 0.02 { fire.append { [soft] in soft.impactOccurred() } }
      }
      lastOpen = o
      let detent = Int(floor(f.pose.yaw / (12 * Double.pi / 180)))
      if let d = lastDetent, d != detent, controller.isTurning { fire.append { [tick] in tick.selectionChanged() } }
      lastDetent = detent
      if f.filmPlaying && f.reserved && !lastReserved { fire.append { [note] in note.notificationOccurred(.success) } }
      lastReserved = f.reserved
      if !fire.isEmpty { DispatchQueue.main.async { fire.forEach { $0() } } }
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
    context.coordinator.stage.apply(controller.frame(at: CACurrentMediaTime()).pose)
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

/// Behind the 3D view, inside the stage window: the studio light and the case's broad soft shadow on the table
/// (WatchLight.tsx's WatchBackdrop). The paper itself is the screen's gradient, so the window has no seam.
struct WatchCaseRevealBackdrop: View {
  typealias M = WatchCaseRevealMotion
  let pose: WatchCaseRevealPose

  var body: some View {
    Canvas { ctx, size in
      let w = size.width, h = size.height
      let c = CGPoint(x: w * 0.4, y: h * 0.26), r = w * 0.9
      ctx.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)),
               with: .radialGradient(Gradient(colors: [Color(.sRGB, red: 1, green: 253 / 255, blue: 248 / 255, opacity: 0.8),
                                                       Color(.sRGB, red: 1, green: 253 / 255, blue: 248 / 255, opacity: 0)]),
                                     center: c, startRadius: 0, endRadius: r))
      let cam = M.cameraAt(pose.open, pose.macro, pose.t)
      let b = M.basis(cam, width: Double(w))
      guard let o = M.project(cam, b, Double(w), Double(h), M.V3(0, 0, 0)) else { return }
      let s = M.caseW * b.focal / o.z
      var g = ctx
      g.translateBy(x: o.x, y: o.y + s * 0.05)
      g.scaleBy(x: s * 1.05, y: s * 0.36)
      let shade = { (a: Double) in Color(.sRGB, red: 52 / 255, green: 40 / 255, blue: 30 / 255, opacity: a) }
      g.fill(Path(ellipseIn: CGRect(x: -1, y: -1, width: 2, height: 2)),
             with: .radialGradient(Gradient(stops: [.init(color: shade(0.28), location: 0), .init(color: shade(0.1), location: 0.5),
                                                    .init(color: shade(0), location: 1)]),
                                   center: .zero, startRadius: 0, endRadius: 1))
    }
    .allowsHitTesting(false)
  }
}
