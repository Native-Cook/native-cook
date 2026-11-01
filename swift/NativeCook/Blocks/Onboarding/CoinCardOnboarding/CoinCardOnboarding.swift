import SwiftUI

struct CoinCardOnboarding: View {
  static let loop = CoinCardOnboardingMotion.loop
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var phase
  @Environment(\.displayScale) private var displayScale
  @StateObject private var clock = CoinCardOnboardingClock()
  private let resources = CoinCardOnboardingResources.shared
  private var pinned: Double? {
    #if DEBUG
    if let value = UserDefaults.standard.object(forKey: "coinCardOnboardingTime") {
      return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
    }
    #endif
    return nil
  }
  private func configure() { clock.configure(autoPlay, reduced, phase == .active, pinned) }
  var body: some View {
    GeometryReader { safe in
      GeometryReader { full in
        TimelineView(.animation(paused: !clock.running || pinned != nil || phase != .active)) { timeline in
          let p = clock.sample(timeline.date)
          let w = full.size.width, h = full.size.height
          let top = safe.safeAreaInsets.top, bottom = safe.safeAreaInsets.bottom
          let cur = Int(min(2, max(0, floor(p + 0.5))))
          ZStack(alignment: .topLeading) {
            CoinCardOnboardingBackdrop(p: p, top: top)
              .overlay {
                if pinned == nil { CoinCardOnboardingTouchView(clock: clock) }
              }
              .accessibilityElement(children: .ignore)
              .accessibilityLabel("\(CoinCardOnboardingMotion.titles[cur].replacingOccurrences(of: "\n", with: " ")). \(CoinCardOnboardingMotion.bodies[cur].replacingOccurrences(of: "\n", with: " "))")
              .accessibilityValue("Page \(cur + 1) of 3")
              .accessibilityAdjustableAction { direction in
                if direction == .increment { clock.goTo(cur + 1) }
                else if direction == .decrement { clock.goTo(cur - 1) }
              }
            ZStack(alignment: .topLeading) {
              Image(uiImage: resources.shadow).resizable()
                .frame(width: 416, height: 202)
                .opacity(CoinCardOnboardingMotion.mix(0.16, 0.26, CoinCardOnboardingMotion.clamp(p - 1)))
                .offset(x: w / 2 - 208, y: 236 + 92 - 78)
              CoinCardOnboardingCard(front: false, p: p, cx: w / 2, cy: 236)
              CoinCardOnboardingCard(front: true, p: p, cx: w / 2, cy: 236)
              CoinCardOnboardingCoin(euro: false, p: p, cx: w / 2, cy: 236, reduced: reduced)
              CoinCardOnboardingCoin(euro: true, p: p, cx: w / 2, cy: 236, reduced: reduced)
            }
            .frame(width: w, height: 520, alignment: .topLeading).clipped().offset(y: top)
            .allowsHitTesting(false).accessibilityHidden(true)
            HStack {
              HStack(spacing: 8) {
                ZStack {
                  Circle().fill(CoinCardOnboardingResources.color(0xe0479a))
                  ForEach(0..<4) { i in
                    Capsule(style: .circular).fill(.white).frame(width: 16, height: 3).rotationEffect(.degrees(Double(i) * 45))
                  }
                }.frame(width: 32, height: 32)
                Text("Sorel").font(resources.font("Outfit-Medium", 17)).offset(y: -1.0 / 3).foregroundStyle(CoinCardOnboardingResources.color(0x1b1b1f))
              }
              Spacer()
              Button { clock.goTo(2) } label: {
                Text("Skip").font(resources.font("Inter-Medium", 14)).offset(y: -1.0 / 6)
                  .foregroundStyle(CoinCardOnboardingResources.color(0x1b1b1f))
                  .padding(.horizontal, 16).frame(height: 34).background(.white, in: Capsule(style: .circular))
              }
              .buttonStyle(CoinCardOnboardingButtonStyle()).accessibilityLabel("Skip to the last page")
            }.frame(width: w - 40, height: 40).offset(x: 20, y: top + 8)
            ZStack(alignment: .top) {
              ForEach(0..<3) { i in
                VStack(spacing: 14) {
                  Image(uiImage: resources.copy(CoinCardOnboardingMotion.titles[i], title: true, width: w - 48))
                    .resizable().frame(width: w - 48, height: 74)
                    .accessibilityLabel(CoinCardOnboardingMotion.titles[i])
                    .accessibilityAddTraits(.isStaticText).accessibilityRemoveTraits(.isImage)
                  Image(uiImage: resources.copy(CoinCardOnboardingMotion.bodies[i], title: false, width: w - 48))
                    .resizable().frame(width: w - 48, height: 42)
                    .accessibilityLabel(CoinCardOnboardingMotion.bodies[i])
                    .accessibilityAddTraits(.isStaticText).accessibilityRemoveTraits(.isImage)
                }
                .fixedSize(horizontal: true, vertical: true)
                .opacity(CoinCardOnboardingMotion.clamp(1 - abs(p - Double(i)) * 1.8))
                .offset(x: -(p - Double(i)) * 44).accessibilityHidden(i != cur)
              }
            }.frame(width: w - 48, height: 150, alignment: .top).offset(x: 24, y: top + 420)
              .allowsHitTesting(false)
            HStack(spacing: 5) {
              ForEach(0..<3) { i in
                let k = CoinCardOnboardingMotion.clamp(1 - abs(p - Double(i)))
                Capsule(style: .circular).fill(CoinCardOnboardingResources.color(0x141416))
                  .frame(width: 6 + 16 * k, height: 4).opacity(0.3 + 0.7 * k)
              }
            }.frame(width: w).offset(y: h - bottom - 154).allowsHitTesting(false).accessibilityHidden(true)
            VStack(spacing: 10) {
              Button { clock.buzz(1) } label: {
                Text("Get Started").font(resources.font("Inter-Medium", 16)).offset(y: -1.0 / 3).foregroundStyle(.white)
                  .frame(maxWidth: .infinity).frame(height: 54)
                  .background(CoinCardOnboardingResources.color(0x0d0d0f), in: Capsule(style: .circular))
              }.accessibilityLabel("Get started")
              Button { clock.buzz(0) } label: {
                Text("Sign In").font(resources.font("Inter-Medium", 16)).offset(x: -1.0 / 3, y: -1.0 / 3)
                  .foregroundStyle(CoinCardOnboardingResources.color(0x141416))
                  .frame(maxWidth: .infinity).frame(height: 54)
                  .overlay(Capsule(style: .circular).strokeBorder(CoinCardOnboardingResources.color(0x141416), lineWidth: (1.5 * displayScale).rounded() / displayScale))
                  .contentShape(Rectangle())
              }.accessibilityLabel("Sign in")
            }.buttonStyle(CoinCardOnboardingButtonStyle())
              .frame(width: w - 40).offset(x: 20, y: h - bottom - 136)
          }.frame(width: w, height: h, alignment: .topLeading)
            .allowsHitTesting(pinned == nil)
        }
      }.ignoresSafeArea()
    }
    .background(CoinCardOnboardingResources.color(0xfdf7fb)).preferredColorScheme(.light)
    .persistentSystemOverlays(.visible)
    .onAppear { configure() }
    .onDisappear { clock.configure(autoPlay, reduced, false, pinned) }
    .onChange(of: phase) { _, _ in configure() }
    .onChange(of: reduced) { _, _ in configure() }
    .onChange(of: autoPlay) { _, _ in configure() }
  }
}

struct CoinCardOnboardingButtonStyle: ButtonStyle {
  func makeBody(configuration: Configuration) -> some View {
    configuration.label.contentShape(Rectangle()).scaleEffect(configuration.isPressed ? 0.97 : 1)
  }
}

struct CoinCardOnboardingBackdrop: View {
  let p: Double
  let top: Double
  var body: some View {
    GeometryReader { geometry in
      Image(uiImage: CoinCardOnboardingResources.shared.backdrop(p, geometry.size, top))
        .resizable().frame(width: geometry.size.width, height: geometry.size.height)
    }
  }
}
