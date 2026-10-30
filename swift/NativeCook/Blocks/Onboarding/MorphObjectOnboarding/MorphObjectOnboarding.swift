import SwiftUI

struct MorphObjectOnboarding: View {
    static let loop = 9.0
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var clock = MorphObjectOnboardingClock()
    private let resources = MorphObjectOnboardingResources.shared
    private var pinned: Double? {
        #if DEBUG
        if let value = UserDefaults.standard.object(forKey: "morphObjectTime") {
            return max(0, (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0)
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { proxy in
            let scale = proxy.size.width / MorphObjectOnboardingTheme.width
            let dy = proxy.size.height - MorphObjectOnboardingTheme.height * scale
            TimelineView(.animation(paused: pinned != nil || scenePhase != .active || (reduced && !clock.fading))) { timeline in
                let live = clock.sample(timeline.date, auto: autoPlay && !reduced, reduced: reduced, pinned: pinned)
                Canvas(opaque: false, colorMode: .nonLinear, rendersAsynchronously: false) { context, size in
                    let art = MorphObjectOnboardingArt(resources: resources, size: size)
                    if reduced && pinned == nil {
                        let lo = floor(live.page)
                        let blend = live.page - lo
                        art.draw(context, .init(page: lo))
                        if blend > 0.001 {
                            var overlay = context
                            overlay.opacity = blend
                            overlay.drawLayer { layer in art.draw(layer, .init(page: min(2, lo + 1))) }
                        }
                    } else { art.draw(context, live) }
                }
            }
            .overlay {
                MorphObjectOnboardingTouchView(scale: scale, dy: dy, enabled: pinned == nil,
                    onBegan: { clock.begin($0, reduced: reduced) },
                    onChanged: { clock.change(translation: $0, screenTranslation: $1, reduced: reduced) },
                    onEnded: { clock.end($0, translation: $1, velocity: $2, reduced: reduced) })
            }
            .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
            .accessibilityElement(children: .ignore)
            .accessibilityAddTraits(.isButton)
            .accessibilityLabel("Onboarding, three pages: We really value your time. You will learn online. You will learn correctly.")
            .accessibilityHint("Goes to the next page.")
            .accessibilityAction { clock.accessibilityNext(reduced: reduced) }
        }
        .ignoresSafeArea()
        .preferredColorScheme(.dark)
        .onAppear { clock.prepareAudio(reduced: reduced) }
        .onDisappear { clock.disappear() }
        .onChange(of: scenePhase) { _, phase in
            clock.resetDate()
            if phase != .active { clock.disappear() } else { clock.prepareAudio(reduced: reduced) }
        }
        .onChange(of: autoPlay) { _, _ in clock.reset() }
        .onChange(of: reduced) { _, _ in clock.reset(); clock.prepareAudio(reduced: reduced) }
    }
}
