import SwiftUI

struct WavyFrameAuction: View {
  static let loop = WavyFrameAuctionMotion.loop
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var clock = WavyFrameAuctionClock()
  private var pinned: Double? {
    #if DEBUG
    if let value = UserDefaults.standard.object(forKey: "wavyFrameAuctionTime") { return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0 }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { safe in
      GeometryReader { full in
        TimelineView(.animation(paused: pinned != nil || scenePhase != .active)) { timeline in
          let sample = clock.sample(timeline.date, autoPlay && !reduced, reduced, pinned)
          Canvas(opaque: false, colorMode: .nonLinear) { context, size in
            WavyFrameAuctionArt(size: size, top: safe.safeAreaInsets.top, bottom: safe.safeAreaInsets.bottom).draw(context, sample.s, sample.phase, sample.lift, sample.seconds)
          }
          .overlay {
            if pinned == nil { WavyFrameAuctionTouchView { state, dy, vy in clock.touch(state, dy, vy, reduced) } }
          }
          .accessibilityElement(children: .ignore)
          .accessibilityAddTraits(.isButton)
          .accessibilityLabel(clock.open ? "Lumen Tide number 42, current bid 0.34 ETH. Swipe down to close." : "Swipe up to open the auction")
          .accessibilityAction { if pinned == nil { clock.toggle(reduced) } }
        }
        .frame(width: full.size.width, height: full.size.height, alignment: .topLeading)
      }.ignoresSafeArea()
    }
    .background(WavyFrameAuctionArt.color(0x1a1d18).ignoresSafeArea())
    .preferredColorScheme(.dark)
    .onAppear { _ = WavyFrameAuctionResources.shared; clock.active(scenePhase == .active) }
    .onDisappear { clock.active(false) }
    .onChange(of: scenePhase) { _, phase in clock.active(phase == .active) }
  }
}
