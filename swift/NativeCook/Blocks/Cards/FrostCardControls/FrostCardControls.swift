import SwiftUI

struct FrostCardControls: View {
  static let loop = 9.0
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var clock = FrostCardControlsClock()
  private let art = FrostCardControlsArt()
  private var pinned: Double? {
    #if DEBUG
      if let value = UserDefaults.standard.object(forKey: "frostCardTime") {
        return max(0, (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0)
      }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { proxy in
      TimelineView(
        .animation(paused: pinned != nil || scenePhase != .active || (reduced && !clock.fading))
      ) { timeline in
        let sample = clock.sample(
          timeline.date, auto: autoPlay && !reduced, reduced: reduced, pinned: pinned)
        ZStack(alignment: .topLeading) {
          scene(sample.frame, sample.driver, proxy.size)
          if reduced && sample.iced > 0.001 {
            scene(FrostCardControlsMotion.frameAt(3), FrostCardControlsMotion.Driver(), proxy.size)
              .opacity(sample.iced)
          }
          if pinned == nil {
            FrostCardControlsTouchView(
              began: { clock.begin($0, scale: proxy.size.width / 402, reduced: reduced) },
              changed: { clock.change($0, reduced: reduced) },
              ended: { clock.end(reduced: reduced) })
          }
        }
        .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
        .clipped()
        .accessibilityElement(children: .ignore)
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel(
          "Physical card ending 5371. Balance minus 3,116 dollars 58. No spending limit."
        )
        .accessibilityHint("Freezes the card, or unfreezes it when it is frozen.")
        .accessibilityAction { clock.accessibilityFreeze(reduced: reduced) }
      }
    }
    .ignoresSafeArea()
    .background(FrostCardControlsTheme.color(FrostCardControlsTheme.C.bg))
    .preferredColorScheme(.light)
    .onAppear {
      clock.prepareAudio(!reduced)
      clock.resetDate()
    }
    .onDisappear {
      clock.prepareAudio(false)
      clock.resetDate()
    }
    .onChange(of: scenePhase) { _, _ in clock.resetDate() }
    .onChange(of: autoPlay) { _, _ in clock.reset() }
    .onChange(of: reduced) { _, value in
      clock.reset()
      clock.prepareAudio(!value)
    }
  }
  private func scene(
    _ f: FrostCardControlsMotion.Frame, _ d: FrostCardControlsMotion.Driver, _ size: CGSize
  ) -> some View {
    ZStack(alignment: .topLeading) {
      Canvas(opaque: false, colorMode: .nonLinear) { context, canvasSize in
        art.drawPage(context, f, canvasSize)
      }
      Canvas(opaque: false, colorMode: .nonLinear) { context, canvasSize in
        art.drawCard(context, f, d, canvasSize)
      }
      .projectionEffect(art.projection(f, d, size.width / 402))
    }
    .frame(width: size.width, height: size.height, alignment: .topLeading)
    .allowsHitTesting(false)
  }
}
