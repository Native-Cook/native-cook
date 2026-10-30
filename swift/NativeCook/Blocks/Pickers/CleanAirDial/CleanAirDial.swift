import SwiftUI

struct CleanAirDial: View {
  static let loop = CleanAirDialMotion.LOOP
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var clock = CleanAirDialClock()
  private var pinned: Double? {
    #if DEBUG
      if let value = UserDefaults.standard.object(forKey: "airDialTime") {
        return max(0, (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0)
      }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { proxy in
      TimelineView(
        .animation(minimumInterval: 1.0 / 60, paused: pinned != nil || scenePhase != .active)
      ) { timeline in
        let frame = clock.sample(timeline.date, auto: autoPlay, reduced: reduced, pinned: pinned)
        Canvas(opaque: true, colorMode: .nonLinear, rendersAsynchronously: false) { context, size in
          CleanAirDialArt(context).drawScene(size, frame)
        }
        .overlay {
          CleanAirDialTouchView { point, phase in
            guard pinned == nil else { return }
            clock.touch(point, phase: phase, width: proxy.size.width, reduced: reduced)
          }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel("Air purifier. PM 2.5: 12, moderate. Speed: medium.")
        .accessibilityHint("Double tap to change the fan speed.")
        .accessibilityAction { clock.stepSpeed() }
      }
      .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
    }
    .ignoresSafeArea()
    .background(CleanAirDialTheme.color(CleanAirDialTheme.C.bg))
    .preferredColorScheme(.dark)
    .onChange(of: scenePhase) { _, _ in clock.resetDate() }
    .onChange(of: autoPlay) { _, _ in clock.reset() }
    .onAppear { clock.prepareAudio(!reduced) }
    .onChange(of: reduced) { _, _ in
      clock.reset()
      clock.prepareAudio(!reduced)
    }
    .onDisappear {
      clock.stopAudio()
      clock.resetDate()
    }
  }
}
