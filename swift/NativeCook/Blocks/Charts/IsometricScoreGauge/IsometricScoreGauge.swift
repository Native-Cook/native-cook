import SwiftUI

struct IsometricScoreGauge: View {
    static let loop = IsometricScoreGaugeMotion.loop
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var clock = IsometricScoreGaugeClock()
    private var pinned: Double? {
        #if DEBUG
        if let value = UserDefaults.standard.object(forKey: "scoreGaugeTime") {
            return max(0, (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0)
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { proxy in
            let scale = proxy.size.width / IsometricScoreGaugeTheme.width
            let dy = (proxy.size.height - IsometricScoreGaugeTheme.height * scale) / 2
            // Explicit reduced-motion dates stop scheduling after the 200 ms crossfade.
            if reduced {
                TimelineView(.explicit((0...13).map { clock.fadeEnd.addingTimeInterval(Double($0 - 13) / 60) })) { timeline in
                    scene(frame: .init(), ambient: 0, size: proxy.size)
                        .overlay {
                            scene(frame: clock.reducedFrame(), ambient: 0, size: proxy.size)
                                .opacity(clock.fade(at: timeline.date))
                        }
                }
                .id(clock.revision)
                .overlay { touch(scale: scale, dy: dy) }
            } else {
                TimelineView(.animation(paused: pinned != nil || scenePhase != .active)) { timeline in
                    let frame = clock.sample(timeline.date, auto: autoPlay, reduced: false, pinned: pinned)
                    scene(frame: frame, ambient: clock.driver.amb, size: proxy.size)
                }
                .overlay { touch(scale: scale, dy: dy) }
            }
        }
        .ignoresSafeArea()
        .preferredColorScheme(.dark)
        .accessibilityElement(children: .ignore)
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel("Credit score 765 out of 900. Make a choice, see where it takes you.")
        .accessibilityHint("Previews paying off your credit card.")
        .accessibilityAction { clock.accessibilityChoose() }
        .onAppear { if !reduced { clock.prepareAudio() } }
        .onChange(of: scenePhase) { _, _ in clock.resetDate() }
        .onChange(of: autoPlay) { _, _ in clock.reset() }
        .onChange(of: reduced) { _, value in
            clock.reset()
            if value { clock.stop() } else { clock.prepareAudio() }
        }
        .onDisappear { clock.stop() }
    }
    private func scene(frame: IsometricScoreGaugeMotion.Frame, ambient: Double, size: CGSize) -> some View {
        ZStack(alignment: .topLeading) {
            Canvas(opaque: false, colorMode: .nonLinear) { context, size in
                IsometricScoreGaugeArt.drawScene(context, size: size, frame: frame, ambient: ambient)
            }
            IsometricScoreGaugeCards(ambient: ambient, frame: frame)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }
    private func touch(scale: Double, dy: Double) -> some View {
        IsometricScoreGaugeTouchView(scale: scale, dy: dy,
            began: { point in if pinned == nil { clock.begin(point, reduced: reduced) } },
            changed: { dx, screenDX in if pinned == nil { clock.change(dx: dx, screenDX: screenDX, reduced: reduced) } },
            ended: { velocity in if pinned == nil { clock.end(velocity: velocity, reduced: reduced) } })
    }
}
