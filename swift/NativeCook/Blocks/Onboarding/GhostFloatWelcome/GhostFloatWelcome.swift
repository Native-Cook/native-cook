import SwiftUI

struct GhostFloatWelcome: View {
  static let loop = 9.0
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var clock = GhostFloatWelcomeClock()
  private var pinned: Double? {
    #if DEBUG
      if let value = UserDefaults.standard.object(forKey: "ghostFloatTime") {
        return max(0, (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0)
      }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { proxy in
      let scale = proxy.size.width / 402
      let dy = (proxy.size.height - 874 * scale) / 2
      TimelineView(
        .animation(paused: pinned != nil || scenePhase != .active || (reduced && !clock.fading))
      ) { timeline in
        let sample = clock.sample(
          timeline.date, auto: autoPlay && !reduced, reduced: reduced, pinned: pinned)
        Canvas(opaque: true, colorMode: .nonLinear, rendersAsynchronously: false) { context, size in
          GhostFloatWelcomeArt.drawScene(context, size, sample.0, sample.1)
        }
        .overlay {
          GhostFloatWelcomeTouchView(
            scale: scale, dy: dy, enabled: pinned == nil,
            began: { clock.begin($0, reduced: reduced) },
            changed: { clock.change($0, velocity: $1, reduced: reduced) },
            ended: { clock.end($0, velocity: $1, reduced: reduced) })
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(
          "Welcome to Wisp. To get started, create a new wallet or import an existing one. I agree to the Terms of Service."
        )
        .accessibilityHint("Ticks or unticks the agreement.")
        .accessibilityAddTraits(.isButton)
        .accessibilityAction { clock.accessibilityToggle(reduced: reduced) }
      }
      .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
    }
    .ignoresSafeArea()
    .background(GhostFloatWelcomeArt.color(GhostFloatWelcomeTheme.C.bg))
    .preferredColorScheme(.dark)
    .onAppear { clock.lifecycle(active: scenePhase == .active, reduced: reduced) }
    .onDisappear { clock.lifecycle(active: false, reduced: reduced) }
    .onChange(of: scenePhase) { _, phase in
      clock.lifecycle(active: phase == .active, reduced: reduced)
    }
    .onChange(of: autoPlay) { _, _ in clock.reset() }
    .onChange(of: reduced) { _, value in
      clock.reset()
      clock.lifecycle(active: scenePhase == .active, reduced: value)
    }
  }
}
