import SwiftUI

struct CityEventHero: View {
    static let loop: Double = CityEventHeroMotion.loop
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityVoiceOverEnabled) private var screenReader
    @Environment(\.scenePhase) private var scenePhase
    @State private var controller = CityEventHeroController()

    var body: some View {
        TimelineView(.animation(paused: !controller.isTicking || scenePhase != .active)) { timeline in
            CityEventHeroSurface(
                frame: controller.frame(at: timeline.date.timeIntervalSinceReferenceDate),
                controller: controller,
                reduceMotion: reduceMotion
            )
        }
        .background(Color.black.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .ignoresSafeArea()
        .onAppear {
            // Decode and pre-render before the clock starts, so the first visible frame is t = 0.
            CityEventHeroAssets.prepare()
            controller.configure(
                autoPlay: autoPlay,
                reduceMotion: reduceMotion,
                screenReader: screenReader,
                at: clock
            )
            controller.setActive(scenePhase == .active, at: clock)
        }
        .onDisappear { controller.setActive(false, at: clock) }
        .onChange(of: autoPlay) { _, value in
            controller.configure(autoPlay: value, reduceMotion: reduceMotion, screenReader: screenReader, at: clock)
        }
        .onChange(of: reduceMotion) { _, value in
            controller.configure(autoPlay: autoPlay, reduceMotion: value, screenReader: screenReader, at: clock)
        }
        .onChange(of: screenReader) { _, value in
            controller.configure(autoPlay: autoPlay, reduceMotion: reduceMotion, screenReader: value, at: clock)
        }
        .onChange(of: scenePhase) { _, value in
            controller.setActive(value == .active, at: clock)
        }
        .sensoryFeedback(.impact(weight: .light), trigger: controller.lightHaptic)
        .sensoryFeedback(.selection, trigger: controller.selectionHaptic)
        .sensoryFeedback(.impact(weight: .medium), trigger: controller.burstHaptic)
    }

    private var clock: Double { Date.timeIntervalSinceReferenceDate }
}
