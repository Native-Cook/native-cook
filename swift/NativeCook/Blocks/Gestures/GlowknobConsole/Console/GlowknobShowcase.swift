import Observation
import SwiftUI
import UIKit

private typealias G = GlowknobReferenceGeometry

// The specimen keeps phone proportions on tablets: past this width it would
// only grow, not gain detail.
private let MAX_WIDTH = 520.0
// The renderer is built once the power-on has played, off its critical path.
private let WARM_UP = 1400.0
// Height of the lift layer around the housing, in housing heights.
private let LIFT_ROOM = 3.4

/// Owns the page's long-lived state (the RN hooks of index.tsx).
///
/// There is one for the life of the app. Its reactions and frame callbacks sit
/// on the shared frame loop and point back at their owners unowned, which was
/// safe in the original app because its one page was never torn down. Here the
/// screen can close and open again, so the screen keeps this model and pauses
/// it (`update(focused: false, …)`) instead of destroying it.
@MainActor
@Observable
final class GlowknobShowcaseModel {
  static let shared = GlowknobShowcaseModel()

  let control: GlowknobTactileControl
  let lift: GlowknobConsoleLift
  @ObservationIgnored let tilt: GlowknobSharedValue<GlowknobTilt>
  @ObservationIgnored private var tiltSource: GlowknobTiltSource!
  private(set) var renderer: GlowknobLiftRenderer?
  @ObservationIgnored private var rendererKey = ""
  @ObservationIgnored private var warmTask: Task<Void, Never>?
  @ObservationIgnored private var environment: URL?
  private(set) var warm = false
  private(set) var fx: GlowknobEffects = .all
  @ObservationIgnored var highContrast = false
  @ObservationIgnored private var displayScale = 3.0

  private init() {
    let reduced = UIAccessibility.isReduceMotionEnabled
    let tilt = GlowknobSharedValue(GlowknobTilt(0, 0))
    self.tilt = tilt
    control = GlowknobTactileControl(fx: .all, reduced: reduced)
    lift = GlowknobConsoleLift(control: control, fx: .all, tilt: tilt)
    lift.recordFace = { [unowned self] in self.recordFace() }
    tiltSource = GlowknobTiltSource(tilt: tilt)
    // The lift layer renders and composites after the frame's mappers.
    GlowknobUIRuntime.shared.onAfterFrame { [weak self] _ in self?.afterFrame() }
    if GlowknobPerfBench.requested {
      let (control, lift) = (control, lift)
      Task { await GlowknobPerfBench.run(control: control, lift: lift) }
    }
    Task { [weak self] in
      let url = await GlowknobStudioEnvironment.make()
      self?.environment = url
      if let url { self?.renderer?.setEnvironment(url) }
    }
  }

  /// Increase Contrast keeps the object and its states but drops decorative
  /// light: spill, glass, sheen, reflection and the page ambience.
  func setHighContrast(_ on: Bool) {
    highContrast = on
    var next = GlowknobEffects.all
    if on {
      next.spill = false
      next.glass = false
      next.sheen = false
      next.reflection = false
      next.ambient = false
    }
    fx = next
    control.setEffects(next)
    lift.setEffects(next)
  }

  func liftable(largeText: Bool) -> Bool { fx.lift && !control.reduced && !largeText }

  /// Focus, foreground, reduce motion and large text changed.
  func update(focused: Bool, foreground: Bool, reduced: Bool, largeText: Bool) {
    GlowknobUIRuntime.shared.isReduceMotion = reduced
    control.setEnvironment(enabled: focused && foreground, reduced: reduced)
    let liftable = self.liftable(largeText: largeText)
    if liftable && focused {
      if warmTask == nil && !warm {
        warmTask = Task { [weak self] in
          try? await Task.sleep(for: .milliseconds(Int(WARM_UP)))
          guard !Task.isCancelled else { return }
          self?.warm = true
          self?.warmTask = nil
          self?.syncLift(liftable: true, focused: true)
        }
      }
    } else {
      warmTask?.cancel()
      warmTask = nil
    }
    syncLift(liftable: liftable, focused: focused)
    if fx.tilt && focused && !reduced { tiltSource.start() } else { tiltSource.stop() }
    if !focused || !foreground { if lift.stage.get() != GLOWKNOB_REST { lift.reset() } }
  }

  private func syncLift(liftable: Bool, focused: Bool) {
    lift.enabled = liftable && warm
    renderer?.setActive(warm && focused && liftable)
  }

  /// The lift layer's box changed (layout): rebuild the renderer.
  func layoutLift(frame: GlowknobBox, housing: GlowknobBox, displayScale: Double) {
    self.displayScale = displayScale
    let key = "\(frame) \(housing) \(displayScale)"
    guard key != rendererKey else { return }
    rendererKey = key
    guard let r = GlowknobLiftRenderer(frame: frame, housing: housing, pixelRatio: displayScale, link: lift.link) else { return }
    renderer = r
    // The face's grain, ready before the first pick-up.
    GlowknobDeviceArtResources.shared.prewarm(pixelsPerUnit: r.scene.faceSize.width / G.width)
    if let environment { r.setEnvironment(environment) }
    r.setActive(warm && lift.enabled)
    GlowknobUIRuntime.shared.wake()
  }

  private func afterFrame() {
    guard let renderer, renderer.active else { return }
    // While the object is up the layer polls every frame (like three's rAF).
    let up = lift.stage.get() != GLOWKNOB_REST || lift.shown.get() > 0 || lift.link.face != nil
    GlowknobUIRuntime.shared.keepAlive = up || !lift.link.ready ? 1 : 0
    renderer.update { [unowned self] in
      GlowknobLiftRenderer.Composite(shown: self.lift.shown.get(), lift: self.lift.lift.get(), tick: self.lift.tick.get(),
                             fogTone: glowknobPageTone(self.control, fx: self.fx), glow: self.fx.liftLight)
    }
  }

  /// DeviceArt baked, with still tilt, recorded at the face texture's size.
  private func recordFace() -> CGImage? {
    GlowknobPerf.measure("recordFace", \.face) { recordFaceNow() }
  }

  private func recordFaceNow() -> CGImage? {
    guard let renderer else { return nil }
    let size = renderer.scene.faceSize
    let pixelsPerUnit = size.width / G.width
    let art = GlowknobDeviceArtFrame(control, tilt: GlowknobTilt(0, 0))
    let fx = self.fx
    let noise = GlowknobDeviceArtResources.shared.noise(pixelsPerUnit: pixelsPerUnit)
    let view = Canvas(opaque: false, colorMode: .nonLinear) { ctx, _ in
      GlowknobBlurCalibration.pixelsPerUnit = pixelsPerUnit
      glowknobDrawDeviceArt(ctx, art, fx: fx, baked: true, noise: noise)
    }
    .frame(width: G.width, height: G.height)
    let r = ImageRenderer(content: view)
    r.scale = pixelsPerUnit
    r.isOpaque = false
    return r.cgImage
  }
}

/// The page is the specimen alone: the tactile Volume / Brightness console
/// standing on a white glass stage.
struct GlowknobShowcaseScreen: View {
  private let model = GlowknobShowcaseModel.shared
  @Environment(\.colorSchemeContrast) private var contrast
  @Environment(\.accessibilityReduceMotion) private var reduceMotion
  @Environment(\.dynamicTypeSize) private var dynamicType
  @Environment(\.scenePhase) private var scenePhase
  @Environment(\.displayScale) private var displayScale

  var body: some View {
    GeometryReader { outer in
      let insets = outer.safeAreaInsets
      let _ = GlowknobPerf.count("GlowknobShowcaseScreen")
      page(insets: insets)
        .ignoresSafeArea()
    }
    .onAppear {
      model.setHighContrast(contrast == .increased)
      model.control.mount()
    }
    // The screen closed: the same pause as losing focus in the original app.
    .onDisappear {
      model.update(focused: false, foreground: scenePhase == .active, reduced: reduceMotion, largeText: false)
      GlowknobUIRuntime.shared.keepAlive = 0
    }
    .onChange(of: contrast) { model.setHighContrast(contrast == .increased) }
    .onChange(of: reduceMotion) { refresh(largeText: glowknobRnFontScale(UIContentSizeCategory(dynamicType)) >= 1.35) }
    .onChange(of: scenePhase) { refresh(largeText: glowknobRnFontScale(UIContentSizeCategory(dynamicType)) >= 1.35) }
  }

  private func page(insets: EdgeInsets) -> some View {
    GeometryReader { geo in
      let root = geo.size
      let fontScale = glowknobRnFontScale(UIContentSizeCategory(dynamicType))
      let largeText = fontScale >= 1.35
      let width = min(MAX_WIDTH, root.width - 2 * GlowknobSpace.screenEdge(root.width))
      let scale = width / G.width
      let control = model.control
      let fx = model.fx
      let liftable = model.liftable(largeText: largeText)
      let largeRow = largeText ? 19 * fontScale + GlowknobSpace.md : 0
      let columnHeight = largeRow + GLOWKNOB_SPECIMEN_HEIGHT * scale
      let origin = CGPoint(x: (root.width - width) / 2, y: (root.height - columnHeight) / 2)
      let layer = liftLayerBox(root: root, origin: CGPoint(x: origin.x, y: origin.y + largeRow), scale: scale)
      ZStack(alignment: .topLeading) {
        GlowknobTactile.page.color
        GlowknobStageAmbient(control: control, fx: fx)
        GlowknobTactileConsole(control: control, fx: fx, tilt: model.tilt, highContrast: model.highContrast,
                       width: width, fontScale: fontScale, lift: liftable ? model.lift : nil,
                       specimenOrigin: CGPoint(x: origin.x, y: origin.y + largeRow))
          .background(alignment: .top) {
            if !largeText && (fx.spill || fx.reflection) {
              StageFloorCanvas(control: control, fx: fx, tilt: model.tilt, scale: scale, lift: model.lift)
                .frame(width: width, height: (GLOWKNOB_SPECIMEN_HEIGHT + GLOWKNOB_FLOOR) * scale)
            }
          }
          .offset(x: origin.x, y: origin.y)
        if liftable {
          LiftLayerView(model: model, frame: layer.frame, housing: layer.housing)
            .frame(width: root.width, height: root.height)
          LiftHint(lift: model.lift, fontScale: fontScale, bottom: insets.bottom)
            .frame(width: root.width, height: root.height, alignment: .bottom)
        }
      }
      .frame(width: root.width, height: root.height)
      .onAppear { refresh(largeText: largeText) }
      .onChange(of: largeText) { refresh(largeText: largeText) }
      .task(id: layer.key) { model.layoutLift(frame: layer.frame, housing: layer.housing, displayScale: displayScale) }
    }
  }

  private func refresh(largeText: Bool) {
    model.update(focused: true, foreground: scenePhase == .active, reduced: reduceMotion, largeText: largeText)
  }

  /// The lift layer spans the page's width and LIFT_ROOM housing heights
  /// around the housing, so the vanishing point sits on the object.
  private func liftLayerBox(root: CGSize, origin: CGPoint, scale: Double) -> (frame: GlowknobBox, housing: GlowknobBox, key: String) {
    let housing = GlowknobBox(x: origin.x, y: origin.y + GLOWKNOB_SPECIMEN_TOP * scale, width: G.width * scale, height: G.height * scale)
    let height = min(root.height, housing.height * LIFT_ROOM)
    let top = housing.y + housing.height / 2 - height / 2
    let frame = GlowknobBox(x: 0, y: top, width: root.width, height: height)
    let inner = GlowknobBox(x: housing.x, y: housing.y - top, width: housing.width, height: housing.height)
    return (frame, inner, "\(frame) \(inner)")
  }
}

/// The floor canvas (StageFloor), fading out as the lifted object takes over
/// the glass.
private struct StageFloorCanvas: View {
  let control: GlowknobTactileControl
  let fx: GlowknobEffects
  let tilt: GlowknobSharedValue<GlowknobTilt>
  let scale: Double
  let lift: GlowknobConsoleLift
  @Environment(\.displayScale) private var displayScale

  var body: some View {
    let _ = GlowknobUIRuntime.shared.clock.frame
    let _ = GlowknobPerf.count("StageFloorCanvas")
    let art = GlowknobDeviceArtFrame(control, tilt: fx.tilt ? tilt.get() : GlowknobTilt(0, 0))
    let tone = glowknobPageTone(control, fx: fx)
    let noise = GlowknobDeviceArtResources.shared.noise(pixelsPerUnit: scale * displayScale)
    let ppu = scale * displayScale
    Canvas(opaque: false, colorMode: .nonLinear, rendersAsynchronously: false) { ctx, _ in
      GlowknobPerf.canvas("floor") {
        GlowknobBlurCalibration.pixelsPerUnit = ppu
        ctx.scaleBy(x: scale, y: scale)
        glowknobDrawStageFloor(ctx, art, tone: tone, fx: fx, noise: noise)
      }
    }
    .drawingGroup()
    .opacity(1 - min(1, lift.lift.get() * 1.6))
    .allowsHitTesting(false)
    .accessibilityHidden(true)
  }
}

/// The instruction under the console. Before the first lift: how to pick it
/// up; while lifted: how to turn it and set it down; afterwards: blank.
private struct LiftHint: View {
  let lift: GlowknobConsoleLift
  let fontScale: Double
  let bottom: Double

  var body: some View {
    let _ = GlowknobUIRuntime.shared.clock.frame
    let _ = GlowknobPerf.count("LiftHint")
    let text = lift.lifted
      ? GlowknobL10n.t("Drag to turn it over · Tap to set it down", "Kéo để lật · Chạm để đặt xuống")
      : lift.used ? " " : GlowknobL10n.t("Hold the console to pick it up", "Giữ console để nhấc lên")
    GlowknobPageType.caption(text, scale: fontScale, color: GlowknobTactile.hint)
      .opacity(lift.lifted ? 1 : 1 - min(1, lift.lift.get() * 2))
      .frame(maxWidth: .infinity)
      .padding(.bottom, bottom + 28)
      .allowsHitTesting(false)
  }
}

/// The lift layer: the composited 3D render at its frame, and the held
/// gestures over the whole page while the console is up.
private struct LiftLayerView: UIViewRepresentable {
  let model: GlowknobShowcaseModel
  let frame: GlowknobBox
  let housing: GlowknobBox

  func makeUIView(context: Context) -> GlowknobLiftHostView { GlowknobLiftHostView(lift: model.lift) }

  func updateUIView(_ view: GlowknobLiftHostView, context: Context) {
    GlowknobPerf.count("LiftLayer.update")
    view.attach(model.renderer)
    view.isUserInteractionEnabled = model.lift.lifted
  }
}

@MainActor
final class GlowknobLiftHostView: UIView {
  private let lift: GlowknobConsoleLift
  private let turn = GlowknobRNPanRecognizer()
  private let putDown = GlowknobRNTapRecognizer()
  private let race = GlowknobRaceDelegate()
  private weak var attached: GlowknobLiftRenderer?

  init(lift: GlowknobConsoleLift) {
    self.lift = lift
    super.init(frame: .zero)
    backgroundColor = .clear
    isUserInteractionEnabled = false
    turn.onStart = { [unowned lift] _ in lift.turnStarted() }
    turn.onUpdate = { [unowned lift] g in lift.turnChanged(translation: g.windowTranslation) }
    turn.onEnd = { [unowned lift] g, _ in lift.turnEnded(velocity: g.windowVelocity) }
    putDown.onEnd = { [unowned lift] _, _ in lift.putDown() }
    turn.delegate = race
    putDown.delegate = race
    addGestureRecognizer(turn)
    addGestureRecognizer(putDown)
    isAccessibilityElement = false
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) { fatalError() }

  func attach(_ renderer: GlowknobLiftRenderer?) {
    guard renderer !== attached else { return }
    attached?.layer.removeFromSuperlayer()
    attached = renderer
    if let renderer { layer.addSublayer(renderer.layer) }
  }
}
