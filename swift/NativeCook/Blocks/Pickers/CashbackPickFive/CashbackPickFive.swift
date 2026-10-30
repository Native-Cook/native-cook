import SwiftUI

struct CashbackPickFive: View {
  static let loop = 9.0
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reducedMotion
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var clock = CashbackPickFiveClock()

  private var pinned: Double? {
    #if DEBUG
      if let value = UserDefaults.standard.object(forKey: "pickFiveTime") {
        return max(0, (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0)
      }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { proxy in
      let scale = proxy.size.width / CashbackPickFiveTheme.REF_W * CashbackPickFiveTheme.S
      TimelineView(.animation(paused: pinned != nil || scenePhase != .active)) { timeline in
        let frame = clock.sample(timeline.date, auto: autoPlay && !reducedMotion, pinned: pinned)
        Canvas(opaque: true, colorMode: .nonLinear, rendersAsynchronously: false) { context, size in
          CashbackPickFiveArt.drawScene(context, size, frame)
        }
        .overlay {
          if pinned == nil {
            CashbackPickFiveTouchView(
              onBegan: { clock.begin() },
              onChanged: { clock.change($0, scale: scale) },
              onEnded: { clock.end($0, velocityY: $1, scale: scale, reduced: reducedMotion) })
          }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel(
          "Choose 5 of 9 cashback categories for June. 2% all purchases, 10% cafés, bars and restaurants, 3% supermarkets, 10% clothes and shoes, 10% taxi."
        )
        .accessibilityHint("Picks the next category.")
        .accessibilityAction { if pinned == nil { clock.pickNext() } }
      }
      .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
    }
    .ignoresSafeArea()
    .background(CashbackPickFiveArt.color(CashbackPickFiveTheme.C.bg))
    .preferredColorScheme(.light)
    .onChange(of: scenePhase) { _, _ in clock.resetDate() }
    .onChange(of: autoPlay) { _, _ in clock.reset() }
    .onChange(of: reducedMotion) { _, _ in clock.reset() }
  }
}
