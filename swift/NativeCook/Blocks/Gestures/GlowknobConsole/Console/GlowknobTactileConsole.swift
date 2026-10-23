import SwiftUI
import UIKit

private typealias G = GlowknobReferenceGeometry

/// The specimen drawing (ControlDrawing): DeviceArt in a Canvas scaled from
/// drawing units, redrawn on every frame in which a shared value changed.
/// The Canvas renders through its own RenderBox layer, asynchronously: on the
/// simulator a `.drawingGroup()` here made the main thread wait for Metal
/// (`waitUntilScheduled`) on every frame of a knob drag (docs/perf.md). The
/// pixels are the same either way (the floor canvas keeps its drawing group:
/// without it the blurred reflection changes).
struct GlowknobSpecimenCanvas: View {
  let control: GlowknobTactileControl
  let fx: GlowknobEffects
  let tilt: GlowknobSharedValue<GlowknobTilt>
  let scale: Double
  @Environment(\.displayScale) private var displayScale

  var body: some View {
    let _ = GlowknobUIRuntime.shared.clock.frame
    let _ = GlowknobPerf.count("GlowknobSpecimenCanvas")
    let frame = GlowknobDeviceArtFrame(control, tilt: fx.tilt ? tilt.get() : GlowknobTilt(0, 0))
    let noise = GlowknobDeviceArtResources.shared.noise(pixelsPerUnit: scale * displayScale)
    let ppu = scale * displayScale
    Canvas(opaque: false, colorMode: .nonLinear, rendersAsynchronously: true) { ctx, _ in
      GlowknobPerf.canvas("specimen") {
        GlowknobBlurCalibration.pixelsPerUnit = ppu
        ctx.scaleBy(x: scale, y: scale)
        ctx.translateBy(x: 0, y: GLOWKNOB_SPECIMEN_TOP)
        glowknobDrawDeviceArt(ctx, frame, fx: fx, baked: false, noise: noise)
      }
    }
    .allowsHitTesting(false)
    .accessibilityHidden(true)
  }
}

/// The console (tactile-console.tsx): drawing, native text overlays (title,
/// readout, FINE ×¼), selector glyphs, hit areas and accessibility.
struct GlowknobTactileConsole: View {
  let control: GlowknobTactileControl
  let fx: GlowknobEffects
  let tilt: GlowknobSharedValue<GlowknobTilt>
  let highContrast: Bool
  let width: Double
  let fontScale: Double
  /// Hold-to-lift on the bare housing (behind the controls).
  let lift: GlowknobConsoleLift?
  /// The specimen's unrounded absolute origin (for GlowknobYoga pixel rounding).
  var specimenOrigin: CGPoint = .zero
  @Environment(\.displayScale) private var displayScale

  var body: some View {
    let _ = GlowknobPerf.count("GlowknobTactileConsole")
    let scale = width / G.width
    let largeText = fontScale >= 1.35
    VStack(alignment: .leading, spacing: GlowknobSpace.md) {
      if largeText {
        HStack {
          GlowknobPageType.label(title, scale: fontScale, color: GlowknobTactile.pageText)
          Spacer(minLength: GlowknobSpace.sm)
          GlowknobPageType.label("\(control.snapshot[control.mode])%", scale: fontScale, color: GlowknobTactile.pageText)
        }
      }
      ZStack(alignment: .topLeading) {
        GlowknobSpecimenCanvas(control: control, fx: fx, tilt: tilt, scale: scale)
        if !largeText {
          TextOverlays(control: control, fx: fx, scale: scale, fontScale: fontScale, highContrast: highContrast,
                       yoga: GlowknobYoga(pointScale: displayScale), origin: specimenOrigin)
        }
        SelectorGlyphs(control: control, scale: scale, highContrast: highContrast,
                       yoga: GlowknobYoga(pointScale: displayScale), origin: specimenOrigin)
        GlowknobConsoleHitAreas(control: control, lift: lift, scale: scale, title: title)
      }
      .frame(width: width, height: GLOWKNOB_SPECIMEN_HEIGHT * scale, alignment: .topLeading)
      .modifier(StandIn(lift: lift))
    }
    .frame(width: width)
  }

  private var title: String {
    control.mode == 0 ? GlowknobL10n.t("Brightness", "Độ sáng") : GlowknobL10n.t("Volume", "Âm lượng")
  }
}

/// 1 while the lifted 3D object stands in for this drawing.
private struct StandIn: ViewModifier {
  let lift: GlowknobConsoleLift?
  func body(content: Content) -> some View {
    let _ = GlowknobUIRuntime.shared.clock.frame
    let _ = GlowknobPerf.count("StandIn")
    content.opacity(lift.map { 1 - $0.covered.get() } ?? 1)
  }
}

/// Title, readout and the FINE ×¼ hint, in native text over the drawing.
private struct TextOverlays: View {
  let control: GlowknobTactileControl
  let fx: GlowknobEffects
  let scale: Double
  let fontScale: Double
  let highContrast: Bool
  let yoga: GlowknobYoga
  let origin: CGPoint

  var body: some View {
    let _ = GlowknobUIRuntime.shared.clock.frame
    let _ = GlowknobPerf.count("TextOverlays")
    let titleBox = yoga.node(CGRect(x: 118 * scale, y: (GLOWKNOB_SPECIMEN_TOP + 36) * scale, width: 360 * scale,
                                    height: 40 * scale), parent: origin)
    let titleText = yoga.node(CGRect(x: 0, y: 0, width: 360 * scale, height: 40 * scale), parent: titleBox.absolute)
    ZStack(alignment: .topLeading) {
      // One title whose glyphs swap natively, in the direction of the
      // selector that was pressed (Volume sits below Brightness). Its
      // left-to-right glyph stagger ends inside the 380ms light handoff.
      GlowknobNumericText(props: GlowknobNumericTextProps(
        value: control.mode == 0 ? "Brightness" : "Volume",
        alignment: "start",
        direction: control.mode == 1 ? "up" : "down",
        animated: !control.reduced,
        duration: 300,
        bounce: 0.18,
        blur: nil,
        maxBlurRadius: 3,
        travel: nil,
        enterScale: nil,
        fontSize: 31 * scale,
        fontWeight: "400",
        fontFamily: GlowknobArtText.systemFace,
        monospacedDigits: false,
        color: GlowknobTactile.text
      ))
      .frame(width: titleText.frame.width, height: titleText.frame.height)
      .opacity(control.labelAlpha)
      .offset(x: titleBox.frame.minX, y: titleBox.frame.minY)
      GlowknobReadout(control: control, scale: scale, highContrast: highContrast, yoga: yoga, origin: origin)
      if fx.fineScrub {
        Text("FINE ×¼")
          .font(.system(size: 20 * scale * fontScale, weight: .regular))
          .foregroundStyle(GlowknobTactile.inactive.color)
          .fixedSize()
          .opacity(control.fineShown.get())
          .offset(x: yoga.round(300 * scale), y: yoga.round((GLOWKNOB_SPECIMEN_TOP + 44) * scale))
      }
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    .allowsHitTesting(false)
    .accessibilityHidden(true)
  }
}

/// The selector glyphs (native in RN): inactive tint underneath, the lit glyph
/// crossfading with the selector handoff; the pressed glyph sinks with its well.
fileprivate struct SelectorGlyphs: View {
  let control: GlowknobTactileControl
  let scale: Double
  let highContrast: Bool
  let yoga: GlowknobYoga
  let origin: CGPoint

  var body: some View {
    let _ = GlowknobUIRuntime.shared.clock.frame
    let _ = GlowknobPerf.count("SelectorGlyphs")
    let inactive = highContrast ? GlowknobTactile.inactiveHigh : GlowknobTactile.inactive
    ZStack(alignment: .topLeading) {
      glyph(0, inactive)
      glyph(1, inactive)
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    .allowsHitTesting(false)
    .accessibilityHidden(true)
  }
}

extension SelectorGlyphs {
  /// One selector's glyph pair. (Not a ForEach: its rows would not be
  /// re-evaluated on every frame.)
  fileprivate func glyph(_ mode: GlowknobChannel, _ inactive: GlowknobRGBA) -> some View {
    let c = mode == 0 ? (x: G.brightness.x, y: G.brightness.y, r: G.brightness.radius)
      : (x: G.volume.x, y: G.volume.y, r: G.volume.radius)
    let size = (mode == 0 ? 50.0 : 46.0) * scale
    let sink = 1 - (mode == 0 ? control.brightPress.get() : control.volumePress.get()) * 0.08
    // Pressable (alignItems/justifyContent center) → the sinking glyph box.
    let well = yoga.node(CGRect(x: (c.x - c.r) * scale, y: (GLOWKNOB_SPECIMEN_TOP + c.y - c.r) * scale,
                                width: c.r * 2 * scale, height: c.r * 2 * scale), parent: origin)
    let box = yoga.node(CGRect(x: (c.r * 2 * scale - size) / 2, y: (c.r * 2 * scale - size) / 2,
                               width: size, height: size), parent: well.absolute)
    return ZStack {
      GlowknobReferenceGlyph(mode: mode, size: size, tint: inactive)
      GlowknobReferenceGlyph(mode: mode, size: size, tint: GlowknobTactile.text)
        .opacity(mode == 0 ? 1 - control.selectorMix : control.selectorMix)
    }
    .frame(width: box.frame.width, height: box.frame.height, alignment: .topLeading)
    .scaleEffect(sink)
    .offset(x: well.frame.minX + box.frame.minX, y: well.frame.minY + box.frame.minY)
  }
}

// MARK: - Hit areas (UIKit: gesture recognizers with RNGH semantics, a11y)

struct GlowknobConsoleHitAreas: UIViewRepresentable {
  let control: GlowknobTactileControl
  let lift: GlowknobConsoleLift?
  let scale: Double
  let title: String

  func makeUIView(context: Context) -> GlowknobHitAreasView { GlowknobHitAreasView(control: control) }

  func updateUIView(_ view: GlowknobHitAreasView, context: Context) {
    GlowknobPerf.count("HitAreas.update")
    view.update(scale: scale, title: title, lift: lift)
  }
}

@MainActor
final class GlowknobHitAreasView: UIView {
  private let control: GlowknobTactileControl
  private let pickUpArea = UIView()
  private let track = GlowknobAdjustableView()
  private let dial = GlowknobAdjustableView()
  private let selectors = [GlowknobPressableView(), GlowknobPressableView()]
  private let trackPan = GlowknobRNPanRecognizer()
  private let trackTap = GlowknobRNTapRecognizer()
  private let dialPan = GlowknobRNPanRecognizer()
  private let pickUp = GlowknobRNPanRecognizer()
  private let race = GlowknobRaceDelegate()
  private weak var lift: GlowknobConsoleLift?
  private var scale = 0.335

  init(control: GlowknobTactileControl) {
    self.control = control
    super.init(frame: .zero)
    backgroundColor = .clear
    addSubview(pickUpArea)
    addSubview(track)
    addSubview(dial)
    selectors.forEach(addSubview)
    pickUpArea.isAccessibilityElement = false

    // Track: Race(Pan, Tap).
    trackPan.maxPointers = 1
    trackPan.activeOffsetX = (-4, 4)
    // failOffsetY is set from fx in update (fine scrub needs room to travel down).
    trackPan.onBegin = { [unowned control] in control.trackPanBegan() }
    trackPan.onStart = { [unowned control] g in
      control.trackPanStarted(x: g.locationInView.x, translationY: g.windowTranslation.y)
    }
    trackPan.onUpdate = { [unowned control] g in
      control.trackPanChanged(x: g.locationInView.x, translationY: g.windowTranslation.y)
    }
    trackPan.onFinalize = { [unowned control] in control.trackPanFinalized() }
    trackTap.maxDist = 10
    trackTap.onEnd = { [unowned control] g, success in control.trackTapEnded(x: g.locationInView.x, success: success) }
    trackPan.delegate = race
    trackTap.delegate = race
    track.addGestureRecognizer(trackPan)
    track.addGestureRecognizer(trackTap)

    // Dial: Pan.
    dialPan.maxPointers = 1
    dialPan.activeOffsetX = (-3, 3)
    dialPan.failOffsetY = (-12, 12)
    dialPan.onBegin = { [unowned control] in control.dialPanBegan() }
    dialPan.onStart = { [unowned control] _ in control.dialPanStarted() }
    dialPan.onUpdate = { [unowned control] g in control.dialPanChanged(translationX: g.windowTranslation.x) }
    dialPan.onEnd = { [unowned control] g, _ in control.dialPanEnded(velocityX: g.windowVelocity.x) }
    dialPan.onFinalize = { [unowned control] in control.dialPanFinalized() }
    dial.addGestureRecognizer(dialPan)

    // Pick-up: Pan activated by a 320 ms hold on the bare housing.
    pickUp.activateAfterLongPress = GlowknobConsoleLift.HOLD / 1000
    pickUp.onStart = { [weak self] _ in self?.lift?.pickUpStarted() }
    pickUp.onUpdate = { [weak self] g in
      self?.lift?.pickUpChanged(translation: g.windowTranslation)
    }
    pickUp.onEnd = { [weak self] g, _ in self?.lift?.pickUpEnded(velocity: g.windowVelocity) }
    pickUpArea.addGestureRecognizer(pickUp)

    for (mode, view) in selectors.enumerated() {
      view.isAccessibilityElement = true
      view.onPressIn = { [unowned control] in control.pressSelector(mode, true) }
      view.onPressOut = { [unowned control] in control.pressSelector(mode, false) }
      view.onPress = { [unowned control] in
        if control.haptics { GlowknobHaptics.selection() }
        control.select(mode)
      }
    }
    track.onIncrement = { [unowned control] in control.adjust(5) }
    track.onDecrement = { [unowned control] in control.adjust(-5) }
    dial.onIncrement = { [unowned control] in control.adjust(5) }
    dial.onDecrement = { [unowned control] in control.adjust(-5) }
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) { fatalError() }

  func update(scale: Double, title: String, lift: GlowknobConsoleLift?) {
    self.scale = scale
    self.lift = lift
    let enabled = control.enabled
    trackPan.isEnabled = enabled
    trackTap.isEnabled = enabled
    dialPan.isEnabled = enabled
    trackPan.failOffsetY = control.fx.fineScrub ? (-12, 400) : (-12, 12)
    pickUp.isEnabled = lift?.enabled ?? false
    pickUpArea.isHidden = lift == nil

    let value = control.snapshot[control.mode]
    track.accessibilityLabel = title
    track.accessibilityHint = GlowknobL10n.t("Drag to adjust, or use increment and decrement.",
                                     "Kéo để điều chỉnh, hoặc dùng thao tác tăng và giảm.")
    dial.accessibilityLabel = GlowknobL10n.t("\(title) dial", "Núm \(title.lowercased())")
    dial.accessibilityHint = GlowknobL10n.t("Drag left or right to adjust.", "Kéo sang trái hoặc phải để điều chỉnh.")
    for v in [track, dial] {
      v.accessibilityValue = "\(value)%"
      v.accessibilityTraits = enabled ? .adjustable : [.adjustable, .notEnabled]
    }
    for (mode, v) in selectors.enumerated() {
      v.accessibilityLabel = mode == 0 ? GlowknobL10n.t("Brightness", "Độ sáng") : GlowknobL10n.t("Volume", "Âm lượng")
      var traits: UIAccessibilityTraits = .button
      if control.mode == mode { traits.insert(.selected) }
      if !enabled { traits.insert(.notEnabled) }
      v.accessibilityTraits = traits
      v.enabled = enabled
    }
    setNeedsLayout()
  }

  override func layoutSubviews() {
    super.layoutSubviews()
    GlowknobPerf.count("HitAreas.layout")
    let s = scale
    pickUpArea.frame = CGRect(x: 0, y: GLOWKNOB_SPECIMEN_TOP * s, width: bounds.width, height: G.height * s)
    let trackH = max(48, 104 * s)
    track.frame = CGRect(x: G.track.x * s, y: (GLOWKNOB_SPECIMEN_TOP + G.track.y + 52) * s - trackH / 2,
                         width: G.track.width * s, height: trackH)
    dial.frame = CGRect(x: (G.dial.x - 100) * s, y: (GLOWKNOB_SPECIMEN_TOP + G.dial.y - 100) * s, width: 200 * s, height: 200 * s)
    for (mode, v) in selectors.enumerated() {
      let c = mode == 0 ? (x: G.brightness.x, y: G.brightness.y, r: G.brightness.radius)
        : (x: G.volume.x, y: G.volume.y, r: G.volume.radius)
      v.frame = CGRect(x: (c.x - c.r) * s, y: (GLOWKNOB_SPECIMEN_TOP + c.y - c.r) * s, width: c.r * 2 * s, height: c.r * 2 * s)
    }
    control.setTrackWidth(G.track.width * bounds.width / G.width)
  }

  override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
    subviews.contains { !$0.isHidden && $0.point(inside: convert(point, to: $0), with: event) }
  }
}

/// An adjustable accessibility element (RN accessibilityRole "adjustable"
/// with increment/decrement actions).
final class GlowknobAdjustableView: UIView {
  var onIncrement: (() -> Void)?
  var onDecrement: (() -> Void)?

  override init(frame: CGRect) {
    super.init(frame: frame)
    isAccessibilityElement = true
    accessibilityTraits = .adjustable
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) { fatalError() }

  override func accessibilityIncrement() { onIncrement?() }
  override func accessibilityDecrement() { onDecrement?() }
}

