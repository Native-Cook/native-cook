import SwiftUI

struct SmokedClocheReveal: View {
  static let loop=SmokedClocheRevealMotion.loop
  var autoPlay=false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scene
  @StateObject private var clock=SmokedClocheRevealClock()
  private var pinned: Double? {
    #if DEBUG
    if let value=UserDefaults.standard.object(forKey:"smokedClocheRevealTime") {
      return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
    }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { proxy in
      TimelineView(.animation(paused:pinned != nil || scene != .active || clock.sleeping || (reduced && !clock.moving))) { timeline in
        SmokedClocheRevealSurface(frame:clock.sample(timeline.date,pinned:pinned),clock:clock,pinned:pinned != nil,reduced:reduced)
      }
      .frame(width:proxy.size.width,height:proxy.size.height,alignment:.topLeading)
    }
    .ignoresSafeArea()
    .preferredColorScheme(.light)
    .onAppear { clock.reset(autoPlay,reduced) }
    .onChange(of:scene) { _, _ in clock.active() }
    .onChange(of:reduced) { _, value in clock.reset(autoPlay,value) }
    .onChange(of:autoPlay) { _, _ in clock.reset(autoPlay,reduced) }
  }
}
