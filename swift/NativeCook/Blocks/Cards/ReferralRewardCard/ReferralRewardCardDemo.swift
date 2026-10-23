import SwiftUI

/// A standalone SwiftUI showcase. The only endlessly redrawn surface is the glass.
struct ReferralRewardCardDemo: View {
  @State private var model = ReferralRewardModel()
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.accessibilityReduceTransparency) private var opaque
  @ScaledMetric(relativeTo: .body) private var fontScale: Double = 1
  var body: some View {
    GeometryReader { proxy in
      let l = ReferralRewardLayout(
        width: 2 * floor(min(proxy.size.width - 32, 370) / 2), fontScale: fontScale)
      let hintHeight = l.textHeight(
        ReferralRewardL10n.t("hint"), size: 13, weight: .regular, line: 18,
        width: proxy.size.width - 48)
      let y = round((proxy.size.height - 48 - l.height - 24 - hintHeight) / 2)
      ZStack(alignment: .topLeading) {
        ReferralRewardCard(model: model, layout: l)
          .frame(width: l.width, height: l.height)
          .offset(x: floor((proxy.size.width - l.width) / 2), y: y)
        Text(ReferralRewardL10n.t("hint"))
          .font(.system(size: 13 * fontScale)).lineSpacing(2.5 * fontScale).foregroundStyle(
            .white.opacity(0.6)
          )
          .multilineTextAlignment(.center).frame(width: proxy.size.width - 48, height: hintHeight)
          .offset(x: 24, y: y + l.height + 24)
          .accessibilitySortPriority(20)
      }
      .onChange(of: l, initial: true) { _, l in model.configure(l) }
    }
    .background(Color.black.ignoresSafeArea()).preferredColorScheme(.dark)
    .contentShape(Rectangle())
    .simultaneousGesture(DragGesture(minimumDistance: 0).onChanged { _ in model.touch() })
    .onChange(of: reduced, initial: true) { _, _ in
      model.preferences(reduced: reduced, opaque: opaque)
    }
    .onChange(of: opaque, initial: true) { _, _ in
      model.preferences(reduced: reduced, opaque: opaque)
    }
    .onAppear {
      model.start(autoPlay: true)
    }
    .onDisappear { model.stop() }
  }
}

struct ReferralRewardCard: View {
  let model: ReferralRewardModel
  let layout: ReferralRewardLayout
  var body: some View {
    ZStack(alignment: .topLeading) {
      if ReferralRewardRenderer.shared.available {
        ReferralRewardSurface(model: model)
          .frame(width: layout.width + 192, height: layout.height + 192).offset(x: -96, y: -96)
      } else {
        ReferralRewardPlainSurface(model: model)
      }
      ReferralRewardHeader(model: model, layout: layout)
        .modifier(ReferralRewardArrival(model: model, index: 0)).offset(x: 24, y: 24)
      ReferralRewardRail(model: model)
        .frame(width: layout.width, height: 212)
        .modifier(ReferralRewardArrival(model: model, index: 1)).offset(y: layout.railTop)
      ReferralRewardFooter(model: model)
        .modifier(ReferralRewardArrival(model: model, index: 2))
        .frame(width: layout.width, height: layout.buttonHeight).offset(y: layout.footerTop)
      ReferralRewardFlight(model: model).allowsHitTesting(false)
    }
    .frame(width: layout.width, height: layout.height, alignment: .topLeading)
  }
}

struct ReferralRewardArrival: ViewModifier {
  let model: ReferralRewardModel
  let index: Double
  func body(content: Content) -> some View {
    let _ = model.arrivalClock.frame
    let p = ReferralRewardMath.out((model.enter.value - index * 80) / 380)
    content.opacity(index == 1 && p == 0 ? 0.012 : p).offset(y: model.reduced ? 0 : (1 - p) * 8)
  }
}

struct ReferralRewardHeader: View {
  let model: ReferralRewardModel
  let layout: ReferralRewardLayout
  var body: some View {
    let l = layout
    ZStack(alignment: .topLeading) {
      VStack(alignment: .leading, spacing: 4) {
        ReferralRewardText(
          text: ReferralRewardL10n.t("title"), size: 26 * l.fontScale, weight: .bold,
          line: 32 * l.fontScale, tracking: -0.5
        )
        .frame(width: l.copyWidth, height: l.titleHeight, alignment: .topLeading)
        .accessibilityAddTraits(.isHeader)
        .accessibilitySortPriority(90)
        ReferralRewardText(
          text: ReferralRewardL10n.t("subtitle"), size: 16 * l.fontScale, weight: .regular,
          line: 24 * l.fontScale
        )
        .foregroundStyle(.white.opacity(0.72)).frame(
          width: l.copyWidth, height: l.subtitleHeight, alignment: .topLeading)
        .accessibilitySortPriority(80)
      }
      ReferralRewardTiers(model: model).frame(width: 132, height: 36)
        .offset(x: l.markX - 24 - 3, y: l.markY - 24 - 18)
      Text(ReferralRewardL10n.format("rewardNote", model.shownTier))
        .font(.system(size: 12 * l.fontScale).monospacedDigit()).foregroundStyle(
          .white.opacity(0.9)
        )
        .frame(
          width: l.stacked ? l.width - 48 - 138 : 126, height: 16 * l.fontScale,
          alignment: .trailing
        )
        .offset(
          x: l.stacked ? 138 : l.width - 48 - 126,
          y: l.stacked ? l.markY - 24 - 8 * l.fontScale : 39
        )
        .accessibilityRepresentation { EmptyView() }
        .accessibilityHidden(true)
    }.foregroundStyle(.white).frame(
      width: l.width - 48, height: l.headerHeight, alignment: .topLeading)
  }
}

/// Explicit line boxes match RN's lineHeight without scaling the fixed canvas geometry.
struct ReferralRewardText: View {
  let text: String
  let size: Double
  let weight: Font.Weight
  let line: Double
  var tracking: Double = 0
  var body: some View {
    Text(text).font(.system(size: size, weight: weight)).tracking(tracking)
      .lineSpacing(max(0, line - size * 1.193)).fixedSize(horizontal: false, vertical: true)
      .padding(.vertical, max(0, (line - size * 1.193) / 2))
  }
}

struct ReferralRewardFooter: View {
  let model: ReferralRewardModel
  @GestureState private var touching = false
  var body: some View {
    let _ = model.footerClock.frame
    let b = ReferralRewardButtonBody(model: model)
    let l = model.layout
    let shape = model.reduced ? 0 : model.shape.value
    let fit =
      model.reduced
      ? 1 : (1 - model.label.value) * (1 - ReferralRewardMath.clamp((shape - 0.1) / 0.3))
    ZStack {
      RoundedRectangle(cornerRadius: max(b.radius, 0), style: .circular).fill(.white)
        .frame(width: max(b.width, 0), height: max(b.height, 0)).scaleEffect(b.scale)
        .opacity(b.hidden ? 0 : pow(ReferralRewardMath.clamp(shape), 4))
      ReferralRewardText(
        text: ReferralRewardL10n.t("refer"), size: 16 * l.fontScale, weight: .medium,
        line: 22 * l.fontScale, tracking: -0.2
      )
      .frame(width: l.buttonWidth - 48).multilineTextAlignment(.center).opacity(
        (1 - model.swap.value) * fit
      )
      .offset(y: model.reduced ? 0 : -4 * model.swap.value)
      .blur(radius: model.reduced ? 0 : 4 * model.label.value)
      ReferralRewardText(
        text: ReferralRewardL10n.t("reset"), size: 16 * l.fontScale, weight: .medium,
        line: 22 * l.fontScale, tracking: -0.2
      )
      .frame(width: l.buttonWidth - 48).multilineTextAlignment(.center).opacity(
        model.swap.value * fit
      )
      .offset(y: model.reduced ? 0 : 4 * (1 - model.swap.value))
      .blur(radius: model.reduced ? 0 : 4 * model.label.value)
    }
    .font(.system(size: 16 * l.fontScale, weight: .medium)).tracking(-0.2).foregroundStyle(.white)
    .frame(width: l.buttonWidth, height: l.buttonHeight)
    .contentShape(Rectangle().inset(by: -8))
    .gesture(
      DragGesture(minimumDistance: 0).updating($touching) { _, state, _ in state = true }
        .onChanged { _ in if !touching { model.buttonDown(true) } }
        .onEnded { value in
          model.buttonDown(false)
          if CGRect(x: -24, y: -24, width: l.buttonWidth + 48, height: l.buttonHeight + 48)
            .contains(value.location)
          {
            model.touch()
            model.activate()
          }
        }
    )
    .onChange(of: touching) { _, value in model.buttonDown(value) }
    // Crossfading labels stay visual; the semantic button has only its current label.
    .accessibilityRepresentation {
      Button(ReferralRewardL10n.t(model.done ? "reset" : "refer")) {
        model.touch()
        model.activate()
      }
    }
    .accessibilitySortPriority(30)
  }
}

#Preview { ReferralRewardCardDemo() }
