import SwiftUI
import UIKit

struct VitalsChipMorph: View {
    static let loop: Double = 9.6
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    @Environment(\.scenePhase) private var scenePhase
    @State private var playback = VitalsChipMorphPlayback()
    private typealias T = VitalsChipMorphTheme
    // Debug-only deterministic stills use the production sampler, including the seam.
    private var snapshotTime: Double? {
        #if DEBUG
        ProcessInfo.processInfo.environment["VCM_SNAPSHOT_TIME"].flatMap(Double.init)
        #else
        nil
        #endif
    }

    private static let names = ["Readiness", "Fitness", "Heart"]
    private static let values = [
        "7 out of 10, ready",
        "375 of 500 calories, 19 of 30 exercise minutes, 4 of 12 stand hours",
        "62 beats per minute",
    ]
    private static let pageCopy = [
        "Ready. You had a good night’s sleep last night and all your other metrics are typical. It looks like you’re rested and ready for today.",
        "Your Exercise Ring Looks Good. Your Exercise ring is ahead of where it usually is by now. Steps 5,253, Cardio Fitness 38 VO2 max.",
        "Cardio Fitness Trending Up. On average, your cardio fitness went up over the last month. Resting heart rate 55 BPM, recovery HRV 54 milliseconds.",
    ]

    var body: some View {
        GeometryReader { geometry in
            let k = geometry.size.width / T.REF_W
            let height = geometry.size.height + geometry.safeAreaInsets.top + geometry.safeAreaInsets.bottom
            // GeometryReader's content origin already lies below the top safe area.
            // Cancelling the reference inset reproduces RN's inset - REF_INSET*k.
            ZStack(alignment: .topLeading) {
                T.colors.page.ignoresSafeArea()
                TimelineView(.animation(paused: snapshotTime != nil || !playback.moving)) { timeline in
                    let b = snapshotTime.map { VitalsChipMorphMotion.loopBlend($0.truncatingRemainder(dividingBy: Self.loop)) }
                        ?? playback.sample(at: timeline.date)
                    ZStack(alignment: .topLeading) {
                        VitalsChipMorphChips(blend: b, quiet: playback.quiet, k: k) { playback.select(chip: $0) }
                        ZStack(alignment: .topLeading) {
                            VitalsChipMorphHero(blend: b, quiet: playback.quiet, k: k)
                                .offset(y: T.layout.heroTop * k)
                            VitalsChipMorphNumbers(blend: b, quiet: playback.quiet, k: k)
                        }
                        .frame(width: geometry.size.width, height: height, alignment: .topLeading)
                        .offset(x: playback.heroShift(at: timeline.date))
                        VitalsChipMorphTouch(playback: playback, k: k,
                                             label: Self.names[playback.current], value: Self.values[playback.current])
                            .frame(width: geometry.size.width, height: T.layout.heroTouchH * k)
                            .offset(y: T.layout.heroTop * k)
                        VitalsChipMorphPages(lo: b.lo, hi: b.hi, u: b.u, quiet: playback.quiet, k: k)
                            .allowsHitTesting(false).accessibilityHidden(true)
                        VitalsChipMorphTabBar(k: k)
                    }
                    .frame(width: geometry.size.width, height: height, alignment: .topLeading)
                    .offset(y: -T.REF_INSET * k)
                }
                Color.clear.frame(width: 1, height: 1)
                    .accessibilityElement()
                    .accessibilityLabel(Self.pageCopy[playback.current])
            }
            .frame(width: geometry.size.width, height: geometry.size.height, alignment: .topLeading)
        }
        .preferredColorScheme(.light)
        .onAppear { playback.configure(autoPlay: autoPlay, quiet: reducedMotion || voiceOver) }
        .onChange(of: reducedMotion || voiceOver) { _, quiet in playback.configure(autoPlay: autoPlay, quiet: quiet) }
        .onChange(of: autoPlay) { _, value in playback.configure(autoPlay: value, quiet: reducedMotion || voiceOver) }
        .onChange(of: scenePhase) { _, phase in playback.setForeground(phase == .active) }
        .onDisappear { playback.stop() }
    }
}
