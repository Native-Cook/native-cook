import QuartzCore
import SceneKit
import SwiftUI
import UIKit

/// A jeweller's proposal planner told as an 11.5-second product tour around a real 3D ring box: the navy velvet box
/// on a soft paper floor with a gentle reflection; the lid snaps open, the solitaire throws glints and fire, the camera
/// comes round to the ring and goes in to the stone while every option is tried in the native pickers and slider,
/// then "Book a private viewing".
///
/// Ported from the React Native block (react-native-filament + Skia): the same ring.glb as USDZ in SceneKit
/// (RingBoxRevealScene3D), the same film (RingBoxRevealMotion), the Skia layers in Canvas (RingBoxRevealLight).
/// Touch: tap the shut box to snap it open, tap the open box to go in to the stone and back; drag the lid up or down;
/// drag sideways once open to turn the box (rubber band, spring back). 2 s after the last touch the camera pulls back,
/// the lid closes and the film restarts from the buyer's choices. Reduce Motion: open on the ring shot, no film.
struct RingBoxReveal: View {
  typealias M = RingBoxRevealMotion
  typealias Lt = RingBoxRevealLight
  var autoPlay: Bool = true

  static let loop: Double = M.loop

  @Environment(\.accessibilityReduceMotion) private var reduceMotion
  @State private var controller: RingBoxRevealController?

  private static let panelH: CGFloat = 262
  private static let headerH: CGFloat = 42
  private static let fade: CGFloat = 40
  private static let topFade: CGFloat = 14
  static let gutter: CGFloat = 20

  init(autoPlay: Bool = true) { self.autoPlay = autoPlay }

  var body: some View {
    GeometryReader { geo in
      let insets = geo.safeAreaInsets
      let W = geo.size.width
      let H = geo.size.height + insets.top + insets.bottom
      let panelTop = H - (insets.bottom + 12 + Self.panelH)
      let stageTop = insets.top + Self.headerH
      let stageH = max(120, panelTop - 8 - stageTop)
      ZStack(alignment: .topLeading) {
        LinearGradient(colors: [Lt.rgb(M.bgTop), Lt.rgb(M.bgBottom)], startPoint: .top, endPoint: .bottom)
        if let controller {
          stage(controller, width: W, height: stageH, screenH: H, top: stageTop)
            .frame(width: W, height: stageH)
            .clipped()
            .offset(y: stageTop)
          Color.clear
            .contentShape(Rectangle())
            .frame(width: W, height: stageH)
            .offset(y: stageTop)
            .gesture(drag(controller))
            .onTapGesture { controller.tap(CACurrentMediaTime()) }
            .accessibilityElement()
            .accessibilityLabel("Ring box")
            .accessibilityHint("Tap or flick up to open the box, tap again to look at the stone, drag down to close it, drag sideways to turn it")
          header(top: insets.top)
          VStack(spacing: 0) {
            Spacer(minLength: 0)
            RingBoxRevealPanel(controller: controller, width: min(W, 560) - Self.gutter * 2)
          }
          .frame(width: min(W, 560) - Self.gutter * 2, height: H - insets.bottom - 12, alignment: .bottomLeading)
          .offset(x: Self.gutter)
        }
      }
      .frame(width: W, height: H, alignment: .topLeading)
      .ignoresSafeArea()
    }
    .background(Lt.rgb(M.bgBottom).ignoresSafeArea())
    .preferredColorScheme(.light)
    .statusBarHidden(false)
    .onAppear {
      if controller == nil { controller = RingBoxRevealController(autoPlay: autoPlay, reduced: reduceMotion) }
    }
  }

  private func stage(_ c: RingBoxRevealController, width: CGFloat, height: CGFloat, screenH: CGFloat, top: CGFloat) -> some View {
    let size = CGSize(width: width, height: height)
    let paperTop = -Double(top), paperH = Double(screenH)
    return ZStack(alignment: .topLeading) {
      TimelineView(.animation) { _ in
        let p = c.frame(at: CACurrentMediaTime()).pose
        Canvas { ctx, s in Lt.backdrop(&ctx, size: s, pose: p, paperTop: paperTop, paperH: paperH) }
      }
      RingBoxRevealStage3D(controller: c, size: size)
        .frame(width: width, height: height)
      TimelineView(.animation) { _ in
        let p = c.frame(at: CACurrentMediaTime()).pose
        Canvas { ctx, s in
          Lt.floorFade(&ctx, size: s, pose: p, paperTop: paperTop, paperH: paperH)
          Lt.sparkle(&ctx, size: s, pose: p)
        }
      }
      LinearGradient(colors: [Self.paper(top, screenH, 1), Self.paper(top + Self.topFade, screenH, 0)],
                     startPoint: .top, endPoint: .bottom)
        .frame(width: width, height: Self.topFade)
      LinearGradient(colors: [Self.paper(top + height - Self.fade, screenH, 0), Self.paper(top + height, screenH, 1)],
                     startPoint: .top, endPoint: .bottom)
        .frame(width: width, height: Self.fade)
        .offset(y: height - Self.fade)
    }
    .frame(width: width, height: height, alignment: .topLeading)
    .allowsHitTesting(false)
  }

  private func header(top: CGFloat) -> some View {
    HStack(alignment: .firstTextBaseline) {
      Text(M.atelier).font(.system(size: 20, weight: .bold)).kerning(-0.2).foregroundStyle(Lt.rgb(M.ink))
      Spacer(minLength: 0)
      Text("Proposal planner").font(.system(size: 13)).foregroundStyle(Lt.rgb(M.mute))
    }
    .padding(.horizontal, Self.gutter)
    .offset(y: top + 8)
    .allowsHitTesting(false)
  }

  private func drag(_ c: RingBoxRevealController) -> some Gesture {
    DragGesture(minimumDistance: 6)
      .onChanged { v in
        let now = CACurrentMediaTime()
        if !c.dragging { c.dragBegan(now) }
        c.dragChanged(now, dx: v.translation.width, dy: v.translation.height)
      }
      .onEnded { v in
        c.dragEnded(CACurrentMediaTime(), vx: v.velocity.width, vy: v.velocity.height)
      }
  }

  /// The paper's colour at screen y (the screen gradient runs bgTop to bgBottom), with an alpha.
  static func paper(_ y: CGFloat, _ h: CGFloat, _ a: Double) -> Color {
    let k = Double(min(1, max(0, y / h)))
    func ch(_ s: UInt32) -> Double {
      let t = Double(M.bgTop >> s & 255), b = Double(M.bgBottom >> s & 255)
      return (t + (b - t) * k).rounded() / 255
    }
    return Color(.sRGB, red: ch(16), green: ch(8), blue: ch(0), opacity: a)
  }
}

/// The ring's name, price and line; metal and stone as native segmented pickers, carat on a native slider, and the
/// booking button. The film drives them by state, never with lookalikes.
struct RingBoxRevealPanel: View {
  typealias M = RingBoxRevealMotion
  typealias Lt = RingBoxRevealLight
  let controller: RingBoxRevealController
  let width: CGFloat

  var body: some View {
    TimelineView(.animation) { _ in
      let f = controller.frame(at: CACurrentMediaTime())
      VStack(alignment: .leading, spacing: 0) {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
          Text(M.ringName).font(.system(size: 30, weight: .bold)).kerning(-0.5).lineLimit(1).minimumScaleFactor(0.8)
          Spacer(minLength: 0)
          Text(M.priceOf(f.pickerMetal, f.pickerStone, f.sliderCarat)).font(.system(size: 19, weight: .semibold)).monospacedDigit()
        }
        .foregroundStyle(Lt.rgb(M.ink))
        Text("\(String(format: "%.2f", f.sliderCarat)) ct \(M.stones[f.pickerStone].name.lowercased()) in \(M.metals[f.pickerMetal].name.lowercased())")
          .font(.system(size: 15)).foregroundStyle(Lt.rgb(M.mute)).padding(.top, 2)
        VStack(spacing: 12) {
          HStack(spacing: 8) {
            label("Metal")
            RingBoxRevealSegments(title: "Metal", labels: M.metals.map(\.short), selection: f.pickerMetal) { i in
              UISelectionFeedbackGenerator().selectionChanged()
              controller.pickMetal(i, now: CACurrentMediaTime())
            }
            .equatable()
          }
          HStack(spacing: 8) {
            label("Stone")
            RingBoxRevealSegments(title: "Stone", labels: M.stones.map(\.name), selection: f.pickerStone) { i in
              UISelectionFeedbackGenerator().selectionChanged()
              controller.pickStone(i, now: CACurrentMediaTime())
            }
            .equatable()
          }
          HStack(spacing: 8) {
            label("Carat")
            RingBoxRevealCaratSlider(value: f.sliderCarat) { r in
              UISelectionFeedbackGenerator().selectionChanged()
              controller.pickCarat(r, now: CACurrentMediaTime())
            }
            .equatable()
            Text("\(String(format: "%.2f", f.sliderCarat)) ct").font(.system(size: 15, weight: .semibold)).monospacedDigit()
              .frame(width: 64, alignment: .trailing)
          }
          Button {
            controller.book(CACurrentMediaTime())
            UINotificationFeedbackGenerator().notificationOccurred(.success)
          } label: {
            Label(f.booked ? "Viewing requested" : "Book a private viewing", systemImage: f.booked ? "checkmark" : "calendar")
          }
          .buttonStyle(.borderedProminent)
          .buttonBorderShape(.capsule)
          .controlSize(.large)
          .tint(Lt.rgb(M.navy))
          .frame(maxWidth: .infinity)
        }
        .padding(.top, 16)
      }
      .frame(width: width, alignment: .leading)
    }
  }

  private func label(_ s: String) -> some View {
    Text(s).font(.system(size: 13, weight: .medium)).foregroundStyle(.secondary).frame(width: 56, alignment: .leading)
  }
}

/// The SceneKit view, see-through over the floor. SceneKit runs the render loop; its delegate asks the controller for
/// this frame's pose and poses the scene on the render thread before the frame is drawn.
struct RingBoxRevealStage3D: UIViewRepresentable {
  let controller: RingBoxRevealController
  let size: CGSize

  final class Coordinator: NSObject, SCNSceneRendererDelegate {
    let controller: RingBoxRevealController
    let stage: RingBoxRevealScene3D
    private var lastOpen: Double?
    private var lastBooked = false
    private let rigid = UIImpactFeedbackGenerator(style: .rigid)
    private let soft = UIImpactFeedbackGenerator(style: .soft)
    private let note = UINotificationFeedbackGenerator()

    init(controller: RingBoxRevealController) {
      self.controller = controller
      let model = Bundle.main.url(forResource: "RingBoxReveal-ring", withExtension: "usdz")
      let env = Bundle.main.url(forResource: "RingBoxReveal-studio", withExtension: "hdr")
      stage = RingBoxRevealScene3D(modelURL: model, environmentURL: env)
      super.init()
    }

    func renderer(_ renderer: SCNSceneRenderer, updateAtTime time: TimeInterval) {
      let now = CACurrentMediaTime()
      controller.begin(now)
      let f = controller.frame(at: now)
      stage.apply(f.pose)
      haptics(f)
    }

    /// The snap (rigid, half way up), the lid landing shut (soft), the film's press of the booking button.
    private func haptics(_ f: RingBoxRevealController.Frame) {
      guard !controller.reduced else { return }
      let o = f.pose.open
      var fire: [() -> Void] = []
      if let prev = lastOpen {
        if prev < 0.5 && o >= 0.5 { fire.append { [rigid] in rigid.impactOccurred() } }
        if prev > 0.01 && o <= 0.01 { fire.append { [soft] in soft.impactOccurred() } }
      }
      lastOpen = o
      if f.filmPlaying && f.booked && !lastBooked { fire.append { [note] in note.notificationOccurred(.success) } }
      lastBooked = f.booked
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
    context.coordinator.stage.apply(controller.frame(at: CACurrentMediaTime()).pose)
    return v
  }

  func updateUIView(_ v: SCNView, context: Context) {}

  static func dismantleUIView(_ v: SCNView, coordinator: Coordinator) {
    v.isPlaying = false
    v.delegate = nil
  }
}

/// The native controls live inside the panel's TimelineView, which rebuilds them every frame; a UISegmentedControl or
/// UISlider updated mid-touch drops the touch, so they were dead to the finger. As Equatable views SwiftUI only
/// updates them when their value changes.
private struct RingBoxRevealSegments: View, Equatable {
  let title: String
  let labels: [String]
  let selection: Int
  let pick: (Int) -> Void

  static func == (a: Self, b: Self) -> Bool { a.selection == b.selection && a.labels == b.labels }

  var body: some View {
    Picker(title, selection: Binding(get: { selection }, set: { i in
      guard i != selection else { return }
      pick(i)
    })) {
      ForEach(labels.indices, id: \.self) { i in Text(labels[i]).tag(i) }
    }
    .pickerStyle(.segmented)
  }
}

private struct RingBoxRevealCaratSlider: View, Equatable {
  typealias M = RingBoxRevealMotion
  let value: Double
  let pick: (Double) -> Void

  static func == (a: Self, b: Self) -> Bool { a.value == b.value }

  var body: some View {
    Slider(value: Binding(get: { value }, set: { v in
      let r = (v * 4).rounded() / 4
      guard r != value else { return }
      pick(r)
    }), in: M.caratMin...M.caratMax, step: M.caratStep)
    .tint(RingBoxRevealLight.rgb(M.navy))
  }
}
