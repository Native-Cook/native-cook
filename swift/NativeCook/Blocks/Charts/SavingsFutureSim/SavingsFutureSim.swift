import SwiftUI
import UIKit

struct SavingsFutureSim: View {
  static let loop = SavingsFutureSimMotion.LOOP
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var clock = SavingsFutureSimClock()
  private var pinned: Double? {
    #if DEBUG
      if let value = UserDefaults.standard.object(forKey: "savingsSimTime") {
        return (value as? NSNumber)?.doubleValue ?? Double("\(value)")
      }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { proxy in
      TimelineView(
        .animation(paused: scenePhase != .active || pinned != nil || (!autoPlay && !clock.running))
      ) { timeline in
        let frame = clock.sample(
          timeline.date, auto: autoPlay && !reduced, reduced: reduced, pinned: pinned)
        Canvas(opaque: true, colorMode: .nonLinear) { context, size in
          let image = SavingsFutureSimArt.shared.image(size, frame)
          context.draw(Image(uiImage: image), in: CGRect(origin: .zero, size: size))
        }
        .overlay {
          SavingsFutureSimTouchView { phase, location in
            guard pinned == nil else { return }
            let p = CGPoint(
              x: location.x / (proxy.size.width / 736), y: location.y / (proxy.size.width / 736))
            clock.touch(phase, p, reduced: reduced)
          }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel(
          "Joined waitlist. Your savings rate: 6 percent. See your savings grow. Simulate my future."
        )
        .accessibilityHint("Opens the forecast.")
        .accessibilityAction { clock.toggle() }
      }
      .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
    }
    .ignoresSafeArea()
    .preferredColorScheme(.dark)
    .onChange(of: scenePhase) { _, _ in clock.resetDate() }
    .onChange(of: autoPlay) { _, _ in clock.reset() }
    .onChange(of: reduced) { _, _ in clock.reset() }
    .onDisappear {
      clock.resetDate()
      clock.audio.stop()
    }
  }
}
