import SwiftUI

struct BlacklightFlightPassport: View {
    static let loop = BlacklightFlightPassportMotion.loop
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var clock = BlacklightFlightPassportClock()
    private var pinned: Double? {
        #if DEBUG
        if let value = UserDefaults.standard.object(forKey: "passportTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { proxy in
            TimelineView(.animation(paused: scenePhase != .active || pinned != nil || (reduced && !clock.fading))) { timeline in
                let sample = clock.sample(timeline.date, auto: autoPlay && !reduced, reduced: reduced, pinned: pinned)
                Canvas(opaque: false, colorMode: .nonLinear) { context, size in
                    BlacklightFlightPassportArt.scene(context, size, sample.0, sample.1)
                    if reduced && sample.2 > 0.001 {
                        var crossfade = context
                        crossfade.opacity = sample.2
                        crossfade.drawLayer { uv in
                            uv.opacity = 1
                            BlacklightFlightPassportArt.scene(uv, size, BlacklightFlightPassportMotion.frameAt(5.2), BlacklightFlightPassportMotion.Driver())
                        }
                    }
                }
                .overlay {
                    BlacklightFlightPassportTouchView { point, phase, translation in
                        guard pinned == nil else { return }
                        clock.touch(point, phase: phase, translation: translation, size: proxy.size, reduced: reduced)
                    }
                }
                .accessibilityElement(children: .ignore)
                .accessibilityAddTraits(.isButton)
                .accessibilityLabel("Your all-time passport: 27 flights, 128,714 kilometres, 7 days 1 hour in the air, 21 airports, 14 airlines.")
                .accessibilityHint("Switches the blacklight on or off.")
                .accessibilityAction { clock.accessibilityToggle(reduced: reduced) }
            }
            .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
        }
        .ignoresSafeArea()
        .background(BlacklightFlightPassportTheme.light["bg"])
        .preferredColorScheme(clock.dark ? .dark : .light)
        .onAppear {
            clock.prepareAudio(enabled: !reduced)
            clock.active(scenePhase == .active && !reduced && pinned == nil)
        }
        .onDisappear { clock.active(false) }
        .onChange(of: scenePhase) { _, phase in
            clock.active(phase == .active && !reduced && pinned == nil)
        }
        .onChange(of: autoPlay) { _, _ in clock.reset() }
        .onChange(of: reduced) { _, _ in
            clock.reset()
            clock.prepareAudio(enabled: !reduced)
            clock.active(scenePhase == .active && !reduced && pinned == nil)
        }
    }
}
