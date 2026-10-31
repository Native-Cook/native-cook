import SwiftUI

struct SwipeToOrderOnboarding: View {
  static let loop = SwipeToOrderOnboardingMotion.loop
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var clock = SwipeToOrderOnboardingClock()
  private var pinned: Double? {
    #if DEBUG
    if let value = UserDefaults.standard.object(forKey: "swipeToOrderOnboardingTime") {
      return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
    }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { safe in
      GeometryReader { proxy in
        TimelineView(.animation(paused: pinned != nil || scenePhase != .active || (reduced && !clock.awake))) { timeline in
          let f = clock.sample(timeline.date, autoPlay && !reduced, reduced, pinned)
          let top = safe.safeAreaInsets.top+56
          let art = SwipeToOrderOnboardingArt(size: proxy.size, top: top, bottom: safe.safeAreaInsets.bottom)
          ZStack(alignment: .topLeading) {
            SwipeToOrderOnboardingBackdrop(top: top)
              .frame(width: proxy.size.width, height: proxy.size.height)
              .allowsHitTesting(false).accessibilityHidden(true)
            Canvas { c, _ in art.background(c, f) }
              .accessibilityHidden(true)
            SwipeToOrderOnboardingGlass()
              .frame(width: 300, height: 86)
              .rotationEffect(.degrees(f.cards[0].p[2]))
              .scaleEffect(f.cards[0].p[3])
              .position(x: proxy.size.width/2+f.cards[0].p[0], y: top+f.cards[0].p[1])
            Canvas { c, _ in art.foreground(c, f, reduced) }
              .allowsHitTesting(false).accessibilityHidden(true)
            Color.clear.frame(width: proxy.size.width-48, height: 176)
              .position(x: proxy.size.width/2, y: top+456)
              .accessibilityElement().accessibilityLabel("Take control of your schedule").accessibilityAddTraits(.isHeader)
            Color.clear.frame(width: 110, height: 36).position(x: 77, y: top-30)
              .accessibilityElement().accessibilityLabel("Taskloop")
            Color.clear.frame(width: 70, height: 36).position(x: proxy.size.width-57, y: top-30)
              .accessibilityElement().accessibilityLabel("Skip →").accessibilityAddTraits(.isButton)
            ForEach([1, 2], id: \.self) { i in
              Color.clear.frame(width: 300, height: 86)
                .rotationEffect(.degrees(f.cards[i].p[2]))
                .scaleEffect(f.cards[i].p[3])
                .position(x: proxy.size.width/2+f.cards[i].p[0], y: top+f.cards[i].p[1])
                .accessibilityElement().accessibilityLabel(i == 1 ? "Focus Block · Code Review" : "Leisure Reading, 2 Hours Needed")
            }
            if pinned == nil {
              SwipeToOrderOnboardingTouchView(done: clock.done, onEvent: { state, dx, vx in
                clock.touch(state, dx, vx, proxy.size.width-102, reduced)
              }, onActivate: { clock.accessibilityTap() })
                .frame(width: proxy.size.width-40, height: 62)
                .position(x: proxy.size.width/2, y: proxy.size.height-safe.safeAreaInsets.bottom-53)
            }
          }
          .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
          .clipped()
        }
      }
      .ignoresSafeArea()
    }
    .background(SwipeToOrderOnboardingArt.color(0x6a37ef))
    .preferredColorScheme(.dark)
    .onAppear { _ = SwipeToOrderOnboardingResources.shared; clock.reset() }
    .onDisappear { clock.active(false) }
    .onChange(of: scenePhase) { _, phase in clock.active(phase == .active) }
    .onChange(of: reduced) { _, _ in clock.reset() }
  }
}
