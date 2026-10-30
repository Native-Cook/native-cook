import SwiftUI

struct ListeningBuddyPaywall: View {
  static let loop = 9.0
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reducedMotion
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var clock = ListeningBuddyPaywallClock()

  private var pinned: Double? {
    #if DEBUG
      if let value = UserDefaults.standard.object(forKey: "buddyPaywallTime") {
        return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
      }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { proxy in
      TimelineView(.animation(paused: pinned != nil || scenePhase != .active)) { timeline in
        let frame = clock.sample(
          timeline.date, auto: autoPlay, reduced: reducedMotion, pinned: pinned)
        Canvas(opaque: true, colorMode: .nonLinear, rendersAsynchronously: false) { context, size in
          let geo = ListeningBuddyPaywallArt.Geo(w: size.width, h: size.height, s: size.width / 402)
          ListeningBuddyPaywallArt().drawScene(
            ListeningBuddyPaywallCanvas(context),
            ListeningBuddyPaywallArt.resources, geo, frame)
        }
        .overlay {
          ListeningBuddyPaywallTouchView(
            onBegin: { if pinned == nil { clock.begin($0, width: proxy.size.width) } },
            onEnd: { if pinned == nil { clock.end($0, width: proxy.size.width) } })
        }
        .accessibilityElement(children: .ignore)
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel(
          "Subscribe now. Monthly 12,99 euros per month. Annual 94,99 euros per year, most popular, selected. Lifetime 449,99 euros, one payment. Continue."
        )
        .accessibilityHint("Chooses the next plan.")
        .accessibilityAction { clock.nextPlan() }
      }
      .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
    }
    .ignoresSafeArea()
    .background(Color(hex: 0xFAF0E4))
    .preferredColorScheme(.light)
    .onChange(of: scenePhase) { _, phase in
      clock.resetDate()
      if phase != .active { clock.stop() }
    }
    .onChange(of: autoPlay) { _, _ in clock.reset() }
    .onChange(of: reducedMotion) { _, _ in clock.reset() }
    .onDisappear { clock.stop() }
  }
}
