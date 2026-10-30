import SwiftUI

struct SunUvBuddy: View {
  static let loop = 9.0
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reducedMotion
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var clock = SunUvBuddyClock()

  private var pinned: Double? {
    #if DEBUG
      if let value = UserDefaults.standard.object(forKey: "sunUvTime") {
        return max(0, (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0)
      }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { proxy in
      TimelineView(.animation(paused: scenePhase != .active || pinned != nil)) { timeline in
        let frame = clock.sample(
          timeline.date, auto: autoPlay && !reducedMotion,
          reduced: reducedMotion, pinned: pinned)
        Canvas(opaque: true, colorMode: .nonLinear, rendersAsynchronously: false) { context, size in
          SunUvBuddyArt().drawScene(context, size, frame)
        }
      }
      .overlay {
        SunUvBuddyTouchView { point, phase in
          guard pinned == nil else { return }
          let scale = proxy.size.width / SunUvBuddyTheme.REF_W
          clock.touch(
            CGPoint(x: point.x / scale, y: point.y / scale), phase: phase, reduced: reducedMotion)
        }
      }
      .accessibilityElement(children: .ignore)
      .accessibilityAddTraits(.isButton)
      .accessibilityLabel(
        "UV index, Thursday. From 12:00 it will be moderate, 3. Sunscreen and a cap won't hurt."
      )
      .accessibilityHint("Double tap for the next day.")
      .accessibilityAction { clock.nextDay() }
      .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
      .onChange(of: scenePhase) { _, _ in clock.resetDate() }
      .onChange(of: autoPlay) { _, _ in clock.reset() }
      .onChange(of: reducedMotion) { _, _ in clock.reset() }
    }
    .ignoresSafeArea()
    .preferredColorScheme(.light)
  }
}
