import SwiftUI

struct LanternWishSend: View {
  static let loop = LanternWishSendMotion.loop
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.displayScale) private var displayScale
  @Environment(\.scenePhase) private var scene
  @StateObject private var clock = LanternWishSendClock()
  private let art = LanternWishSendResources.shared
  private var pinned: Double? {
    #if DEBUG
    if let value = UserDefaults.standard.object(forKey: "lanternWishSendTime") {
      return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
    }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { proxy in
      let glowAtlas = art.prepareGlows(scale: displayScale)
      TimelineView(.animation(paused: pinned != nil || scene != .active || (reduced && !clock.running))) { timeline in
        let f = clock.sample(timeline.date, auto: autoPlay && !reduced, pinned: pinned)
        Canvas(opaque: false, colorMode: .nonLinear, rendersAsynchronously: false) { context, size in
          art.draw(context, size, f, reduced, glowAtlas)
        }
        .overlay { LanternWishSendControls(frame: f, pinned: pinned != nil, clock: clock) }
      }
      .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
    }
    .ignoresSafeArea()
    .background(Color(uiColor: LanternWishSendResources.color(0x2A1A4A)))
    .preferredColorScheme(.dark)
    .onChange(of: scene) { _, _ in clock.resume() }
  }
}
