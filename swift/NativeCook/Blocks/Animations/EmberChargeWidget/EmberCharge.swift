import SwiftUI

/// The self-contained instrument. The page supplies autoplay; touch stays on an untransformed wrapper.
struct EmberCharge: View {
  private typealias E = EmberTheme.E
  private typealias TypeStyle = EmberTheme.TypeStyle
  private typealias Optical = EmberTheme.Optical
  private typealias M = EmberMotionConstants

  /// The demo enables the loop; the reusable component defaults to manual interaction.
  var autoPlay = false
  /// Daily target, clamped above rest so the charge interval remains defined.
  var goal = E.goal
  /// Explicit tiers bypass the automatic frame-budget decision.
  var quality: EmberQuality = .auto
  /// Responsive scale is outside every motion transform and also maps touch coordinates.
  var scale = 1.0
  /// Manual completion callback; autoplay never calls it.
  var onIgnite: (() -> Void)?

  init(
    autoPlay: Bool = false, goal: Int = E.goal, quality: EmberQuality = .auto, scale: Double = 1,
    onIgnite: (() -> Void)? = nil
  ) {
    EmberFonts.register()
    self.autoPlay = autoPlay
    self.goal = max(E.restSteps + 1, goal)
    self.quality = quality
    self.scale = scale
    self.onIgnite = onIgnite
  }

  @Environment(\.accessibilityReduceMotion) private var reduced

  @Environment(\.scenePhase) private var scenePhase

  @State private var motion = EmberMotion()

  var body: some View {
    let frame = motion.frame
    ZStack(alignment: .topLeading) {
      ZStack(alignment: .topLeading) {
        EmberSurface(frame: frame).frame(width: EmberTheme.canvas, height: EmberTheme.canvas)
          .offset(
            x: -E.bleed, y: -E.bleed)
        EmberSurface(frame: frame, isTube: true).frame(
          width: EmberTheme.tube.w, height: EmberTheme.tube.h
        ).offset(x: EmberTheme.tube.x, y: EmberTheme.tube.y)
        EmberDrawing(frame: frame).offset(x: -E.bleed, y: -E.bleed)
        copy(frame)
        EmberDrawing(frame: frame, airborne: true).offset(x: -E.bleed, y: -E.bleed)
      }
      .frame(width: E.size, height: E.size, alignment: .topLeading)
      .scaleEffect(frame.cardScale).offset(frame.shake).opacity(frame.enter)
      .scaleEffect(scale, anchor: .topLeading)
      .allowsHitTesting(false).accessibilityHidden(true)
      EmberTouch(motion: motion, scale: scale)
        .frame(width: E.size * scale, height: E.size * scale)
    }
    .frame(width: E.size * scale, height: E.size * scale, alignment: .topLeading)
    .onAppear {
      EmberFonts.register()
      motion.autoPlay = autoPlay
      motion.frame.goal = max(E.restSteps + 1, goal)
      motion.requestedQuality = quality
      motion.onIgnite = onIgnite
      motion.setReduced(reduced)
      motion.start()
    }
    .onDisappear { motion.stop() }
    .onChange(of: reduced) { _, v in motion.setReduced(v) }
    .onChange(of: quality) { _, v in motion.requestedQuality = v }
    .onChange(of: autoPlay) { _, v in motion.autoPlay = v }
    .onChange(of: goal) { _, v in motion.frame.goal = max(E.restSteps + 1, v) }
    .onChange(of: scenePhase) { _, v in if v == .active { motion.start() } else { motion.stop() } }
  }

  private func copy(_ frame: EmberFrame) -> some View {
    // Optical offsets align Manrope's SwiftUI baseline with RN's explicit
    // line boxes and NumericText's bottom-biased native 60 pt view.
    ZStack(alignment: .topLeading) {
      label(
        EmberL10n.t("title"), size: TypeStyle.title.size, height: E.line.title, bold: true,
        tracking: TypeStyle.title.tracking
      ).offset(
        x: E.title.x, y: E.title.top + Optical.titleRise)
      label(
        EmberL10n.t("subtitle"), size: TypeStyle.subtitle.size, height: E.line.subtitle,
        tracking: TypeStyle.subtitle.tracking, secondary: true
      ).offset(
        x: E.title.x, y: E.title.sub + Optical.subtitleRise)
      label("4:24 AM", size: TypeStyle.time, height: E.line.time, secondary: true).monospacedDigit()
        .frame(width: E.size - E.time.right, alignment: .trailing).offset(
          y: E.time.top + Optical.smallTextDrop)
      metric("5.21", "km").offset(x: E.metrics.x[0] + E.metrics.text, y: E.metrics.valueTop)
      metric("413", EmberL10n.t("minutes")).offset(
        x: E.metrics.x[1] + E.metrics.text, y: E.metrics.valueTop)
      metric("1343", "kcal").offset(x: E.metrics.x[2] + E.metrics.text, y: E.metrics.valueTop)
      HStack(alignment: .bottom, spacing: E.count.unitGap) {
        EmberCount(
          value: motion.shown, from: motion.previousShown, goal: goal, reduced: frame.reduced
        )
        .offset(y: Optical.countDrop).scaleEffect(frame.slam, anchor: .bottomLeading)
        label(EmberL10n.t("steps"), size: TypeStyle.unit, height: E.line.unit)
          .foregroundStyle(EmberTone.onEmber).offset(
            y: Optical.smallTextDrop
          ).padding(.bottom, E.count.unitDrop)
      }.offset(x: E.count.x, y: E.count.top)
      Text(String(motion.reached ? E.goalBpm : E.restBpm))
        .font(EmberFonts.font(TypeStyle.bpm.size, semibold: true)).tracking(TypeStyle.bpm.tracking)
        .monospacedDigit()
        .contentTransition(.numericText(value: Double(motion.reached ? E.goalBpm : E.restBpm)))
        .animation(
          frame.reduced ? nil : .spring(duration: M.bpm.duration, bounce: M.bpm.bounce),
          value: motion.reached
        )
        .foregroundStyle(EmberHeartTone.ink).frame(width: E.heart.w, height: E.line.bpm).offset(
          x: E.heart.x, y: E.heart.y + E.heart.rateOffset + Optical.rateDrop)
    }
    .frame(width: E.size, height: E.size, alignment: .topLeading).environment(
      \.dynamicTypeSize, .medium
    )
    .accessibilityHidden(true)
  }

  private func label(
    _ text: String, size: Double, height: Double, bold: Bool = false, tracking: Double = 0,
    secondary: Bool = false
  ) -> some View {
    Text(text).font(EmberFonts.font(size, semibold: bold)).tracking(tracking)
      .foregroundStyle(secondary ? EmberTone.sub : EmberTone.ink)
      .fixedSize().frame(height: height)
  }

  private func metric(_ value: String, _ unit: String) -> some View {
    VStack(alignment: .leading, spacing: 0) {
      label(
        value, size: TypeStyle.value.size, height: E.line.value, bold: true,
        tracking: TypeStyle.value.tracking
      ).offset(y: Optical.valueRise)
      label(unit, size: TypeStyle.unit, height: E.line.unit, secondary: true).offset(
        y: Optical.smallTextDrop)
    }
  }
}
