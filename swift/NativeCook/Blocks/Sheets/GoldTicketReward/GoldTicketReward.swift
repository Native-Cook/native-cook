import SwiftUI

struct GoldTicketReward: View {
  static let loop = GoldTicketRewardMotion.LOOP
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var clock = GoldTicketRewardClock()
  private var pinned: Double? {
    #if DEBUG
      if let value = UserDefaults.standard.object(forKey: "goldTicketTime") {
        return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
      }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { proxy in
      TimelineView(
        .animation(
          paused: pinned != nil || scenePhase != .active || (reduced && !clock.fading)
            || (!reduced && !autoPlay && !clock.running))
      ) { timeline in
        let sample = clock.sample(timeline.date, autoPlay && !reduced, reduced, pinned)
        Canvas(opaque: false, colorMode: .nonLinear, rendersAsynchronously: false) {
          context, size in
          let art = GoldTicketRewardArt(size: size)
          if reduced && pinned == nil {
            if sample.fade < 0.999 {
              art.drawScene(context, GoldTicketRewardMotion.frameAt(0), .init())
            }
            if sample.fade > 0.001 {
              var reward = context
              reward.opacity = sample.fade
              reward.drawLayer { layer in
                art.drawScene(
                  layer, GoldTicketRewardMotion.frameAt(GoldTicketRewardMotion.PEAK), .init())
              }
            }
          } else {
            art.drawScene(context, sample.frame, sample.live)
          }
        }
        .overlay {
          if pinned == nil {
            GoldTicketRewardTouchView { state, point, translation, velocity in
              clock.touch(state, point, translation, velocity, proxy.size, reduced)
            }
          }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel(
          "Thanks for your interest. Book a test drive to win a 1 gram gold coin."
        )
        .accessibilityHint("Reveals the gold reward, or closes it when it is open.")
        .accessibilityAction { clock.accessibilityToggle(reduced) }
      }
      .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
    }
    .ignoresSafeArea()
    .background(GoldTicketRewardArt.color(GoldTicketRewardTheme.C.page))
    .preferredColorScheme(.light)
    .onAppear {
      clock.reset(reduced)
      clock.active(scenePhase == .active, reduced, pinned != nil)
    }
    .onDisappear { clock.active(false, reduced, pinned != nil) }
    .onChange(of: scenePhase) { _, phase in clock.active(phase == .active, reduced, pinned != nil) }
    .onChange(of: reduced) { _, value in
      clock.reset(value)
      clock.active(scenePhase == .active, value, pinned != nil)
    }
    .onChange(of: autoPlay) { _, _ in clock.reset(reduced) }
  }
}
