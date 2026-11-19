import SceneKit
import SwiftUI
import UIKit
import simd

/// The SceneKit view: see-through over the studio backdrop. SceneKit runs the render loop; its delegate asks the
/// controller for this frame's state and poses the scene, on the render thread, before the frame is drawn.
struct VisorBootOnboardingStage3D: UIViewRepresentable {
  let controller: VisorBootOnboardingController
  let size: CGSize

  final class Coordinator: NSObject, SCNSceneRendererDelegate {
    let controller: VisorBootOnboardingController
    let stage: VisorBootOnboardingScene3D
    private var lastPage: Int?
    private var lastDetent: Int?
    private let light = UIImpactFeedbackGenerator(style: .light)

    init(controller: VisorBootOnboardingController) {
      self.controller = controller
      let model = Bundle.main.url(forResource: "VisorBootOnboarding-visor", withExtension: "usdz")
      let env = Bundle.main.url(forResource: "VisorBootOnboarding-studio", withExtension: "hdr")
      stage = VisorBootOnboardingScene3D(modelURL: model, environmentURL: env)
      super.init()
    }

    func renderer(_ renderer: SCNSceneRenderer, updateAtTime time: TimeInterval) {
      let s = controller.state(at: CACurrentMediaTime())
      stage.apply(s)
      haptics(s)
    }

    /// A light tick as a page lands under the finger, and every 45 deg of orbit (never during the film).
    private func haptics(_ s: VisorBootOnboardingState) {
      var fire = false
      let page = controller.isManual ? Int(s.page.rounded()) : -1
      if let p = lastPage, p >= 0, page >= 0, p != page { fire = true }
      lastPage = page
      let detent = Int(floor(s.yaw / 45))
      if let d = lastDetent, d != detent, controller.isOrbiting { fire = true }
      lastDetent = detent
      if fire { DispatchQueue.main.async { [light] in light.impactOccurred() } }
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
    context.coordinator.stage.apply(controller.state(at: CACurrentMediaTime()))
    return v
  }

  func updateUIView(_ v: SCNView, context: Context) {}

  static func dismantleUIView(_ v: SCNView, coordinator: Coordinator) {
    v.isPlaying = false
    v.delegate = nil
  }
}

/// The studio behind the 3D view (React Native's VisorBackdrop): the shader in VisorBootOnboarding.metal, fed the
/// plinth's base as the stage camera sees it, so its contact shadow stays under the plinth.
struct VisorBootOnboardingBackdrop: View {
  typealias M = VisorBootOnboardingMotion
  let state: VisorBootOnboardingState
  let size: CGSize

  var body: some View {
    let w = Double(size.width), h = Double(size.height)
    let c = M.cameraAt(state)
    let b = M.basis(c, width: w)
    let o = M.project(c, b, w, h, M.V3(0, 0, 0))
    let rx = o.map { M.plinthR * b.focal / $0.z } ?? 0
    let dist = max(simd_length(c.eye), 1e-6)
    let ry = rx * max(0.08, abs(c.eye.y) / dist)
    let sweep = M.clamp01(M.blend(M.filmSweep(state.t).0, 0, state.m))
    return Rectangle()
      .fill(Color.black)
      .colorEffect(ShaderLibrary.visorBootStudio(.float2(Float(w), Float(h)),
                                                 .float2(Float(o?.x ?? 0), Float(o?.y ?? 0)),
                                                 .float2(Float(rx), Float(ry)),
                                                 .float(Float(sweep))))
      .allowsHitTesting(false)
  }
}
