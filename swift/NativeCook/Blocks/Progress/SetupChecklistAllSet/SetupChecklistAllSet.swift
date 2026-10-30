import SwiftUI

struct SetupChecklistAllSet: View {
  static let loop = 9.0
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var clock = SetupChecklistAllSetClock()

  private var pinned: Double? {
    #if DEBUG
      // a launch argument arrives as a string, a stored default as a number
      if let value = UserDefaults.standard.object(forKey: "setupListTime") {
        return max(0, (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0)
      }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { geometry in
      TimelineView(.animation(paused: pinned != nil || scenePhase != .active)) { timeline in
        let live = clock.sample(
          timeline.date, auto: autoPlay && !reduced, reduced: reduced, pinned: pinned)
        Canvas(opaque: false, colorMode: .nonLinear, rendersAsynchronously: false) {
          context, size in
          SetupChecklistAllSetArt().draw(&context, size, live)
        }
        .overlay {
          SetupChecklistAllSetTouchView(
            scale: geometry.size.width / 402, enabled: pinned == nil,
            began: { point in clock.begin(point) },
            ended: { point in clock.end(point, reduced: reduced) })
        }
        .accessibilityElement(children: .ignore)
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel("Your setup checklist, 1 of 6 done. Add your name and bio.")
        .accessibilityHint("Ticks the next item.")
        .accessibilityAction { clock.tickNext() }
      }
      .frame(width: geometry.size.width, height: geometry.size.height, alignment: .topLeading)
    }
    .ignoresSafeArea()
    .background(Color(red: 243 / 255, green: 243 / 255, blue: 241 / 255))
    .preferredColorScheme(.light)
    .onChange(of: scenePhase) { _, _ in clock.resetDate() }
    .onChange(of: autoPlay) { _, _ in clock.reset() }
    .onChange(of: reduced) { _, _ in clock.reset() }
    .onDisappear { clock.stop() }
  }
}
