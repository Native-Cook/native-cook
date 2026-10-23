import SwiftUI

/// Skia `BlurMask blur={sigma}` → SwiftUI blur radius. Skia's value is the
/// Gaussian's sigma; SwiftUI's blur radius is close to a sigma too, slightly
/// tighter for large blurs. This is the calibration the Glowknob SwiftUI port
/// measured against RN Skia renders (`BlurCalibration` there): radius =
/// sigma / k, k = 1 up to 4 px of sigma, 0.92 from 6 px, linear between.
/// Sigma 10 pt at 3× (30 px) gives radius 10 / 0.92 ≈ 10.87 pt.
func flareSkiaBlurRadius(sigma: Double, pixelsPerPoint: Double) -> Double {
  let px = sigma * pixelsPerPoint
  let k = px <= 4 ? 1 : px >= 6 ? 0.92 : 1 - 0.08 * (px - 4) / 2
  return sigma / k
}

/// The cast shadow (drawn under the pill, no rim/stroke anywhere): a capsule
/// the size of the pill inset 6 on the left and right, moved down 12, filled
/// with the shadow color and blurred with sigma 10. Drawn into a canvas `pad`
/// larger than the pill on every side, as the RN Skia canvas is.
struct FlarePillShadow: View {
  let width: Double
  let color: FlareRGBA
  @Environment(\.displayScale) private var displayScale

  var body: some View {
    let pad = FlareBeats.pad
    Canvas { context, _ in
      context.drawLayer { layer in
        layer.addFilter(.blur(radius: flareSkiaBlurRadius(sigma: FlareMotion.shadowBlur, pixelsPerPoint: displayScale)))
        let rect = CGRect(x: pad + FlareMotion.shadowInset, y: pad + FlareMotion.shadowDrop,
                          width: width - FlareMotion.shadowInset * 2, height: FlareMotion.height)
        layer.fill(Path(roundedRect: rect, cornerRadius: FlareMotion.height / 2, style: .circular),
                   with: .color(color.color))
      }
    }
    .frame(width: width + pad * 2, height: FlareMotion.height + pad * 2)
    .allowsHitTesting(false)
    .accessibilityHidden(true)
  }
}

/// One note: shadow, flame surface and copy, with its gesture and its
/// accessibility element.
struct FlarePillView: View {
  let pill: FlarePillModel
  let width: Double
  let round: Int
  let onToggle: () -> Void

  @Environment(\.displayScale) private var displayScale
  @State private var titleW = 0.0
  @State private var lineW = 0.0
  @GestureState private var touching = false

  private var flare: Flare { pill.flare }

  var body: some View {
    let v = pill.visual
    let pad = FlareBeats.pad
    let read = pill.isRead(round: round)
    let line = FlareL10n.t(flare.lineKey)
    let seen = FlareL10n.seen

    ZStack(alignment: .topLeading) {
      FlarePillShadow(width: width, color: flare.shadow)
      fill
        .frame(width: width, height: FlareMotion.height)
        .overlay(alignment: .leading) { copy(line: line, seen: seen, v: v) }
        .offset(x: pad, y: pad)
    }
    .frame(width: width + pad * 2, height: FlareMotion.height + pad * 2, alignment: .topLeading)
    // RN view opacity is group opacity: the shadow never shows through a
    // half-faded pill.
    .compositingGroup()
    .opacity(v.opacity)
    // Back to the pill's own frame; the canvas overflows it by `pad`.
    .padding(-pad)
    .contentShape(Rectangle())
    // transform: [{ translateY }, { scale }] about the pill's center.
    .scaleEffect(v.scale)
    .offset(y: v.translateY)
    .gesture(touch)
    .onChange(of: touching) { _, down in
      // A cancelled touch (no onEnded) still finalizes, like RNGH onFinalize.
      if !down && pill.tracking {
        pill.tracking = false
        pill.touchEnded(translation: pill.lastTranslation, round: round)
      }
    }
    .accessibilityElement(children: .ignore)
    .accessibilityLabel("\(flare.title.replacingOccurrences(of: ".", with: "").replacingOccurrences(of: "*", with: "")), \(read ? seen : line)")
    .accessibilityHint(FlareL10n.pillHint)
    .accessibilityAddTraits(read ? [.isButton, .isSelected] : .isButton)
    .accessibilityAction { onToggle() }
    .onChange(of: titleW) { updateCopyWidth() }
    .onChange(of: lineW) { updateCopyWidth() }
    .onAppear { pill.width = width }
    .onChange(of: width) { pill.width = width }
    .onChange(of: displayScale) { updateCopyWidth() }
  }

  /// The flame fill: a CAMetalLayer surface.
  private var fill: some View { FlareSurface(pill: pill) }

  /// RNGH `Pan().minDistance(0)`: onBegin at touch-down, onFinalize at
  /// release with the translation since touch-down.
  private var touch: some Gesture {
    DragGesture(minimumDistance: 0, coordinateSpace: .global)
      .updating($touching) { _, state, _ in state = true }
      .onChanged { value in
        pill.lastTranslation = value.translation
        if !pill.tracking {
          pill.tracking = true
          pill.touchBegan()
        }
      }
      .onEnded { value in
        guard pill.tracking else { return }
        pill.tracking = false
        pill.touchEnded(translation: value.translation, round: round)
      }
  }

  /// The copy block: left inset 29, vertically centered, gap 3 between title
  /// and line; the read line sits on the unread line and they crossfade.
  private func copy(line: String, seen: String, v: FlarePillVisual) -> some View {
    VStack(alignment: .leading, spacing: 3) {
      FlareRNText(text: flare.title, style: Self.titleStyle, color: flare.ink.color)
        .lineLimit(1)
        .fixedSize()
        .onGeometryChange(for: Double.self) { $0.size.width } action: { titleW = $0 }
      ZStack(alignment: .topLeading) {
        FlareRNText(text: line.uppercased(), style: Self.lineStyle, color: flare.sub.color)
          .lineLimit(1)
          .fixedSize()
          .onGeometryChange(for: Double.self) { $0.size.width } action: { lineW = $0 }
          .opacity(v.lineOpacity)
          .offset(y: v.lineY)
        FlareRNText(text: seen.uppercased(), style: Self.lineStyle, color: flare.sub.color)
          .lineLimit(1)
          .fixedSize()
          .onGeometryChange(for: Double.self) { $0.size.width } action: { pill.seenW = flarePixelCeil($0, scale: displayScale) }
          .opacity(v.seenOpacity)
          .offset(y: v.seenY)
      }
    }
    .padding(.leading, FlareMotion.inset)
    .allowsHitTesting(false)
  }

  /// The copy View's width is its widest in-flow child: the title or the
  /// unread line.
  private func updateCopyWidth() {
    pill.copyW = flarePixelCeil(max(titleW, lineW), scale: displayScale)
  }

  // The reference sets the name at a 0.185-height cap (22pt here). Its tag
  // line is ~7pt at this size; 10pt bold keeps the look and stays readable.
  static let titleStyle = FlareRNTextStyle(size: 22, weight: .bold, lineHeight: 26, letterSpacing: -0.3)
  static let lineStyle = FlareRNTextStyle(size: 10, weight: .bold, lineHeight: 13, letterSpacing: 0.4)
}
