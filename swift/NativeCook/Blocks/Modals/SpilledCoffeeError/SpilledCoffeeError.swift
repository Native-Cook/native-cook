import SwiftUI

struct SpilledCoffeeError: View {
  static let loop = 9.0
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var clock = SpilledCoffeeErrorClock()
  private var pinned: Double? {
    #if DEBUG
      if let value = UserDefaults.standard.object(forKey: "spilledCoffeeTime") {
        return max(0, (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0)
      }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { proxy in
      TimelineView(.animation(paused: pinned != nil || scenePhase != .active)) { timeline in
        let pose = clock.sample(timeline.date, auto: autoPlay, reduced: reduced, pinned: pinned)
        Canvas(opaque: false, colorMode: .nonLinear, rendersAsynchronously: false) {
          context, size in
          SpilledCoffeeErrorArt(size: size).drawScene(context, pose)
        }
        .overlay {
          SpilledCoffeeErrorTouchView { phase, point in
            guard pinned == nil else { return }
            clock.touch(phase, point, size: proxy.size, reduced: reduced)
          }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel("Error! Something went wrong, please try again later. Retry.")
        .accessibilityHint("Tries again.")
        .accessibilityAction { clock.accessibilityRetry() }
      }
      .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
      .onChange(of: scenePhase) { _, phase in
        clock.resetDate()
        if phase != .active { clock.audio.cancelScript() }
      }
      .onChange(of: autoPlay) { _, _ in clock.reset() }
      .onChange(of: reduced) { _, _ in clock.reset() }
      .onDisappear {
        clock.audio.cancelScript()
        clock.resetDate()
      }
    }
    .ignoresSafeArea()
    .background(SpilledCoffeeErrorTheme.color(SpilledCoffeeErrorTheme.C.floor))
    .preferredColorScheme(.light)
  }
}
