import SwiftUI

struct CabinClimateCar: View {
  static let loop = CabinClimateCarMotion.LOOP
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var clock = CabinClimateCarClock()
  private var pinned: Double? {
    #if DEBUG
      if let value = UserDefaults.standard.object(forKey: "cabinClimateTime") {
        return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
      }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { proxy in
      TimelineView(.animation(paused: pinned != nil || scenePhase != .active)) { timeline in
        let frame = clock.sample(timeline.date, auto: autoPlay, reduced: reduced, pinned: pinned)
        Canvas(opaque: false, colorMode: .nonLinear, rendersAsynchronously: false) {
          context, size in
          CabinClimateCarArt(context).draw(size, frame)
        }
      }
      .overlay {
        if pinned == nil {
          CabinClimateCarTouchView(
            begin: { clock.begin($0, proxy.size.width) },
            move: { clock.move($0, proxy.size.width, reduced) },
            end: { clock.end(reduced) })
        }
      }
      .accessibilityElement(children: .ignore)
      .accessibilityAddTraits(.isButton)
      .accessibilityLabel("Cabin controls. Temperature 70 degrees. Distributed legroom. Fan auto.")
      .accessibilityHint("Double tap to warm the cabin a degree.")
      .accessibilityAction { clock.warmer() }
      .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
    }
    .ignoresSafeArea()
    .background(CabinClimateCarTheme.color(CabinClimateCarTheme.bg))
    .preferredColorScheme(.light)
    .onChange(of: autoPlay) { _, _ in clock.reset() }
    .onChange(of: reduced) { _, _ in clock.reset() }
    .onChange(of: scenePhase) { _, phase in
      clock.resetDate()
      if phase != .active { clock.stop() }
    }
    .onDisappear { clock.stop() }
  }
}
