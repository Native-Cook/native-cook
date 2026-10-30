import SwiftUI

struct CareerWebOnboarding: View {
    static let loop = CareerWebOnboardingMotion.LOOP
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var clock = CareerWebOnboardingClock()
    private var pinned: Double? {
        #if DEBUG
        if let value = UserDefaults.standard.object(forKey: "careerWebTime") {
            return max(0, (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0)
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { proxy in
            TimelineView(.animation(paused: scenePhase != .active)) { timeline in
                let sample = clock.sample(timeline.date, auto: autoPlay, reduced: reduced, pinned: pinned)
                Canvas(opaque: true, colorMode: .nonLinear, rendersAsynchronously: false) { context, size in
                    CareerWebOnboardingArt.drawScene(context, size, sample.0, sample.1)
                }
                .overlay {
                    CareerWebOnboardingTouchView(
                        onBegin: { clock.begin($0, size: proxy.size, reduced: reduced) },
                        onChange: { clock.change($0, translation: $1, size: proxy.size, reduced: reduced) },
                        onEnd: { clock.end(velocity: $0, size: proxy.size, reduced: reduced) }
                    ).allowsHitTesting(pinned == nil)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityAddTraits(.isButton)
                .accessibilityLabel("Build your future, build your dream. Three people linked to the roles that fit them: UX/UI designer, product designer, software engineer, Flutter developer. Get started.")
                .accessibilityHint("Shows or hides the links between people and roles.")
                .accessibilityAction { clock.accessibilityGrow() }
            }
            .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
            .onAppear { clock.prepare() }
            .onDisappear { clock.stop() }
            .onChange(of: scenePhase) { _, _ in clock.resetDate() }
            .onChange(of: autoPlay) { _, _ in clock.reset() }
            .onChange(of: reduced) { _, _ in clock.reset() }
        }
        .ignoresSafeArea()
        .background(CareerWebOnboardingArt.color(CareerWebOnboardingTheme.bg))
        .preferredColorScheme(.dark)
    }
}
