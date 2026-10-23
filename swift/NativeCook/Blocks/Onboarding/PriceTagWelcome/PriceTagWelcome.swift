import SwiftUI

struct PriceTagWelcome: View {
    static let loop = PriceTagWelcomeMotion.loop
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var systemReduceMotion
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    @Environment(\.scenePhase) private var scenePhase
    @State private var playback = PriceTagWelcomePlayback()
    @State private var ticking = true
    @State private var started = false
    @State private var freezeToken = 0
    @State private var thawToken = 0
    @State private var buttonToken = 0

    /// Reduce Motion or a screen reader, both followed live: the loop stops and the freeze cross-fades.
    private var reduceMotion: Bool { systemReduceMotion || voiceOver }

    var body: some View {
        GeometryReader { proxy in
            TimelineView(.animation(minimumInterval: 1 / 120, paused: !ticking || scenePhase != .active)) { timeline in
                let now = timeline.date.timeIntervalSinceReferenceDate
                let frame = playback.sample(at: now)
                PriceTagWelcomeContent(
                    size: proxy.size,
                    frame: frame,
                    reduced: reduceMotion,
                    onWake: wake,
                    onDown: { x, y, time in
                        playback.touchDown(x: x, y: y, at: time)
                    },
                    onBeginDrag: { time in playback.beginDrag(at: time) },
                    onMoveDrag: { translation, time in
                        playback.moveDrag(translation: translation, at: time)
                    },
                    onEndDrag: { velocity, time in
                        playback.endDrag(velocity: velocity, at: time)
                    },
                    onTap: { x, y, time in
                        if playback.tap(x: x, y: y, at: time) >= 0 { bump() }
                    },
                    onCancel: { time in playback.cancelTouch(at: time) },
                    onAccessibilityToggle: { index in
                        playback.accessibilityToggle(index: index, at: clock)
                        bump()
                        wake()
                    },
                    onButton: { buttonToken += 1 }
                )
                .onChange(of: frame.moving) { _, moving in
                    if !moving { ticking = false }
                }
            }
        }
        .background(PriceTagWelcomeTheme.Colors.page.ignoresSafeArea())
        .ignoresSafeArea()
        .preferredColorScheme(.light)
        .onAppear {
            guard !started else { return }
            started = true
            playback.start(autoPlay: autoPlay, reduced: reduceMotion, at: clock)
            ticking = autoPlay && !reduceMotion
        }
        .onChange(of: autoPlay) { _, enabled in
            playback.configure(autoPlay: enabled, reduced: reduceMotion, at: clock)
            ticking = !reduceMotion && (enabled || playback.latestFrame.moving)
        }
        .onChange(of: reduceMotion) { _, reduced in
            playback.configure(autoPlay: autoPlay, reduced: reduced, at: clock)
            ticking = !reduced && (autoPlay || playback.latestFrame.moving)
        }
        .onChange(of: scenePhase) { _, phase in
            guard phase == .active else { return }
            playback.foreground(at: clock)
            ticking = !reduceMotion && (autoPlay || playback.latestFrame.moving)
        }
        .sensoryFeedback(.impact(flexibility: .rigid), trigger: freezeToken)
        .sensoryFeedback(.impact(flexibility: .soft), trigger: thawToken)
        .sensoryFeedback(.impact(weight: .light), trigger: buttonToken)
    }

    private var clock: Double { Date().timeIntervalSinceReferenceDate }

    /// Freezing lands hard and crisp; a thaw is soft.
    private func bump() {
        if playback.lastFroze { freezeToken += 1 } else { thawToken += 1 }
    }

    private func wake() {
        ticking = true
    }
}
